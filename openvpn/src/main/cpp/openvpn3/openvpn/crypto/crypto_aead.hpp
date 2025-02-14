//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012- OpenVPN Inc.
//
//    SPDX-License-Identifier: MPL-2.0 OR AGPL-3.0-only WITH openvpn3-openssl-exception
//

// OpenVPN AEAD data channel interface

#ifndef OPENVPN_CRYPTO_CRYPTO_AEAD_H
#define OPENVPN_CRYPTO_CRYPTO_AEAD_H

#include <cstring> // for std::memcpy, std::memset

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/clamp_typerange.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/frame/frame.hpp>
#include <openvpn/crypto/static_key.hpp>
#include <openvpn/crypto/packet_id_data.hpp>
#include <openvpn/log/sessionstats.hpp>
#include <openvpn/crypto/cryptodc.hpp>

// Sample AES-GCM head:
//   48000001 00000005 7e7046bd 444a7e28 cc6387b1 64a4d6c1 380275a...
//   [ OP32 ] [seq # ] [             auth tag            ] [ payload ... ]
//            [4-byte
//            IV head]

using openvpn::numeric_util::clamp_to_default;

namespace openvpn::AEAD {

OPENVPN_EXCEPTION(aead_error);

template <typename CRYPTO_API>
class Crypto : public CryptoDCInstance
{
    class Nonce
    {
      public:
        Nonce()
        {
            static_assert(4 + CRYPTO_API::CipherContextAEAD::IV_LEN == sizeof(data),
                          "AEAD IV_LEN inconsistency");
            ad_op32 = false;
            std::memset(data, 0, sizeof(data));
        }

        /**
         * Sets the IV tail for AEAD operations
         *
         * The IV for AEAD ciphers (both AES-GCM and Chacha20-Poly1305) consists of 96 bits/12 bytes
         *  (It then gets concatenated with internal 32 bits for block counter to form a 128 bit counter for the
         *  encryption).
         *
         *  Since we only use 4 bytes (32 bit packet ID) on the wire, we fill out the rest of the IV with
         *  pseudorandom bytes that come from the negotiated key for the HMAC key (this key is not used
         *  by AEAD ciphers, so we reuse it for this purpose in AEAD mode).
         */
        void set_tail(const StaticKey &sk)
        {
            constexpr size_t implicit_iv_len = 8;
            if (sk.size() < implicit_iv_len)
                throw aead_error("insufficient key material for nonce tail");

            /* 4 bytes opcode + 4 bytes on wire IV */
            constexpr size_t implicit_iv_offset = data_offset_pkt_id + (12 - implicit_iv_len);
            std::memcpy(data + implicit_iv_offset, sk.data(), implicit_iv_len);
        }

        // for encrypt
        Nonce(const Nonce &ref, PacketIDDataSend &pid_send, const unsigned char *op32)
        {
            /** Copy op code and tail of packet ID */
            std::memcpy(data, ref.data, sizeof(data));

            Buffer buf(data + data_offset_pkt_id, PacketIDData::long_id_size, false);
            pid_send.write_next(buf);
            if (op32)
            {
                ad_op32 = true;
                std::memcpy(data, op32, op32_size);
            }
            else
                ad_op32 = false;
        }

        // for encrypt
        void prepend_ad(Buffer &buf, const PacketIDDataSend &pid_send) const
        {
            buf.prepend(data + data_offset_pkt_id, pid_send.length());
        }

        // for decrypt
        Nonce(const Nonce &ref, const PacketIDDataReceive &recv_pid, Buffer &buf, const unsigned char *op32)
        {
            /* Copy opcode and tail of packet ID */
            std::memcpy(data, ref.data, sizeof(data));

            /* copy dynamic packet of IV into */
            buf.read(data + data_offset_pkt_id, recv_pid.length());
            if (op32)
            {
                ad_op32 = true;
                std::memcpy(data, op32, op32_size);
            }
            else
                ad_op32 = false;
        }

        // for decrypt
        bool verify_packet_id(PacketIDDataReceive &pid_recv, const PacketIDControl::time_t now, const SessionStats::Ptr &stats_arg)
        {
            Buffer buf(data + data_offset_pkt_id, PacketIDData::long_id_size, true);
            const PacketIDData pid = pid_recv.read_next(buf);
            return pid_recv.test_add(pid, now, stats_arg); // verify packet ID
        }

        const unsigned char *iv() const
        {
            return data + data_offset_pkt_id;
        }

        const unsigned char *ad() const
        {
            return ad_op32 ? data : data + data_offset_pkt_id;
        }

        size_t ad_len(const PacketIDDataSend &pid_send) const
        {
            return (ad_op32 ? op32_size : 0) + pid_send.length();
        }

        size_t ad_len(const PacketIDDataReceive &pid_recv) const
        {
            return (ad_op32 ? op32_size : 0) + pid_recv.length();
        }


      private:
        bool ad_op32; // true if AD (authenticated data) includes op32 opcode

        // Sample data:
        //   [ OP32 (optional) ] [  pkt ID     ] [     nonce tail          ]
        //   [ 48 00 00 01     ] [ 00 00 00 05 ] [ 7f 45 64 db 33 5b 6c 29 ]
        unsigned char data[16];
        static constexpr std::size_t data_offset_pkt_id = 4;
        static constexpr std::size_t op32_size = 4;
    };

    struct Encrypt
    {
        typename CRYPTO_API::CipherContextAEAD impl;
        Nonce nonce;
        PacketIDDataSend pid_send{};
        BufferAllocated work;
    };

    struct Decrypt
    {
        typename CRYPTO_API::CipherContextAEAD impl;
        Nonce nonce;
        PacketIDDataReceive pid_recv{};
        BufferAllocated work;
    };

  public:
    typedef CryptoDCInstance Base;

    Crypto(SSLLib::Ctx libctx_arg,
           CryptoDCSettingsData dc_settings_data,
           const Frame::Ptr &frame_arg,
           const SessionStats::Ptr &stats_arg)
        : dc_settings(dc_settings_data),
          frame(frame_arg),
          stats(stats_arg),
          libctx(libctx_arg)
    {
    }

    // Encrypt/Decrypt

    // returns true if packet ID is close to wrapping
    bool encrypt(BufferAllocated &buf, const unsigned char *op32) override
    {
        // only process non-null packets
        if (buf.size())
        {
            // build nonce/IV/AD
            Nonce nonce(e.nonce, e.pid_send, op32);

            // encrypt to work buf
            frame->prepare(Frame::ENCRYPT_WORK, e.work);
            if (e.work.max_size() < buf.size())
                throw aead_error("encrypt work buffer too small");


            unsigned char *work_data = e.work.write_alloc(buf.size());

            unsigned char *auth_tag_tmp = nullptr;
            // alloc auth tag in buffer at the start of the packet
            // Create a temporary auth tag at the end if the implementation and mode require it

            unsigned char *auth_tag = e.work.prepend_alloc(CRYPTO_API::CipherContextAEAD::AUTH_TAG_LEN);
            if (e.impl.requires_authtag_at_end())
            {
                auth_tag_tmp = e.work.write_alloc(CRYPTO_API::CipherContextAEAD::AUTH_TAG_LEN);
            }

            // encrypt
            e.impl.encrypt(buf.data(), work_data, buf.size(), nonce.iv(), auth_tag, nonce.ad(), nonce.ad_len(e.pid_send));

            if (auth_tag_tmp)
            {
                /* move the auth tag to the front */
                std::memcpy(auth_tag, auth_tag_tmp, CRYPTO_API::CipherContextAEAD::AUTH_TAG_LEN);
                /* Ignore the auth tag at the end */
                e.work.inc_size(-CRYPTO_API::CipherContextAEAD::AUTH_TAG_LEN);
            }

            buf.swap(e.work);

            // prepend additional data
            nonce.prepend_ad(buf, e.pid_send);
        }
        return e.pid_send.wrap_warning() || e.impl.get_usage_limit().usage_limit_warn();
    }

    Error::Type decrypt(BufferAllocated &buf, const std::time_t now, const unsigned char *op32) override
    {
        // only process non-null packets
        if (buf.size())
        {
            // get nonce/IV/AD
            Nonce nonce(d.nonce, d.pid_recv, buf, op32);

            // get auth tag if it is at the front. If the auth tag is at the end
            // the decrypt function will just treat it as part of the input
            unsigned char *auth_tag = nullptr;

            auth_tag = buf.read_alloc(CRYPTO_API::CipherContextAEAD::AUTH_TAG_LEN);

            // initialize work buffer.
            frame->prepare(Frame::DECRYPT_WORK, d.work);
            if (d.work.max_size() < buf.size())
                throw aead_error("decrypt work buffer too small");

            if (auth_tag && e.impl.requires_authtag_at_end())
            {
                unsigned char *auth_tag_end = buf.write_alloc(CRYPTO_API::CipherContextAEAD::AUTH_TAG_LEN);
                std::memcpy(auth_tag_end, auth_tag, CRYPTO_API::CipherContextAEAD::AUTH_TAG_LEN);
                auth_tag = nullptr;
            }

            // decrypt from buf -> work
            if (!d.impl.decrypt(buf.c_data(), d.work.data(), buf.size(), nonce.iv(), auth_tag, nonce.ad(), nonce.ad_len(d.pid_recv)))
            {
                buf.reset_size();
                return Error::DECRYPT_ERROR;
            }

            if (e.impl.requires_authtag_at_end())
            {
                d.work.set_size(buf.size() - CRYPTO_API::CipherContextAEAD::AUTH_TAG_LEN);
            }
            else
            {
                d.work.set_size(buf.size());
            }

            // verify packet ID
            if (!nonce.verify_packet_id(d.pid_recv, now, stats))
            {
                buf.reset_size();
                return Error::REPLAY_ERROR;
            }

            // return cleartext result in buf
            buf.swap(d.work);
        }
        return Error::SUCCESS;
    }

    // Initialization

    // TODO: clamp_to_default probably will cause an error further along if triggered, investigate
    void init_cipher(StaticKey &&encrypt_key, StaticKey &&decrypt_key) override
    {
        e.impl.init(libctx,
                    dc_settings.cipher(),
                    encrypt_key.data(),
                    clamp_to_default<unsigned int>(encrypt_key.size(), 0),
                    CRYPTO_API::CipherContextAEAD::ENCRYPT);
        d.impl.init(libctx,
                    dc_settings.cipher(),
                    decrypt_key.data(),
                    clamp_to_default<unsigned int>(decrypt_key.size(), 0),
                    CRYPTO_API::CipherContextAEAD::DECRYPT);
    }

    void init_hmac(StaticKey &&encrypt_key,
                   StaticKey &&decrypt_key) override
    {
        e.nonce.set_tail(encrypt_key);
        d.nonce.set_tail(decrypt_key);
    }

    void init_pid(const char *recv_name,
                  const int recv_unit,
                  const SessionStats::Ptr &recv_stats_arg) override
    {
        e.pid_send = PacketIDDataSend{};
        d.pid_recv.init(recv_name, recv_unit, false);
        stats = recv_stats_arg;
    }

    // Indicate whether or not cipher/digest is defined

    unsigned int defined() const override
    {
        unsigned int ret = CRYPTO_DEFINED;

        // AEAD mode doesn't use HMAC, but we still indicate HMAC_DEFINED
        // because we want to use the HMAC keying material for the AEAD nonce tail.
        if (CryptoAlgs::defined(dc_settings.cipher()))
            ret |= (CIPHER_DEFINED | HMAC_DEFINED);
        return ret;
    }

    bool consider_compression(const CompressContext &comp_ctx) override
    {
        return true;
    }

    // Rekeying
    void rekey(const typename Base::RekeyType type) override
    {
    }

  private:
    CryptoDCSettingsData dc_settings;
    Frame::Ptr frame;
    SessionStats::Ptr stats;
    SSLLib::Ctx libctx;
    Encrypt e;
    Decrypt d;
};

template <typename CRYPTO_API>
class CryptoContext : public CryptoDCContext
{
  public:
    typedef RCPtr<CryptoContext> Ptr;

    CryptoContext(SSLLib::Ctx libctx_arg,
                  CryptoDCSettingsData dc_settings_data,
                  const Frame::Ptr &frame_arg,
                  const SessionStats::Ptr &stats_arg)
        : CryptoDCContext(dc_settings_data.key_derivation()),
          dc_settings(std::move(dc_settings_data)),
          frame(frame_arg),
          stats(stats_arg),
          libctx(libctx_arg)
    {
        /* Check if the cipher is legal for AEAD and otherwise throw */
        legal_dc_cipher(dc_settings.cipher());
        dc_settings.set_digest(CryptoAlgs::NONE);
    }

    CryptoDCInstance::Ptr new_obj(const unsigned int key_id) override
    {
        return new Crypto<CRYPTO_API>(libctx, dc_settings, frame, stats);
    }

    // cipher/HMAC/key info
    CryptoDCSettingsData crypto_info() override
    {
        return dc_settings;
    }

    // Info for ProtoContext::link_mtu_adjust

    size_t encap_overhead() const override
    {
        return CRYPTO_API::CipherContextAEAD::AUTH_TAG_LEN;
    }

  private:
    CryptoDCSettingsData dc_settings;
    Frame::Ptr frame;
    SessionStats::Ptr stats;
    SSLLib::Ctx libctx;
};
} // namespace openvpn::AEAD

#endif
