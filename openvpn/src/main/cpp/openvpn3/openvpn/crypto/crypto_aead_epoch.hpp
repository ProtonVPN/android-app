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

#ifndef OPENVPN_CRYPTO_CRYPTO_AEAD_EPOCH_H
#define OPENVPN_CRYPTO_CRYPTO_AEAD_EPOCH_H

#include <array>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/clamp_typerange.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/frame/frame.hpp>
#include <openvpn/crypto/static_key.hpp>
#include <openvpn/crypto/packet_id_data.hpp>
#include <openvpn/log/sessionstats.hpp>
#include <openvpn/crypto/cryptodc.hpp>
#include <openvpn/crypto/data_epoch.hpp>

namespace openvpn::AEADEpoch {

OPENVPN_EXCEPTION(aead_epoch_error);

template <typename CRYPTO_API>
class Crypto : public CryptoDCInstance
{

    BufferAllocated work_encrypt;
    BufferAllocated work_decrypt;

  public:
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
        if (buf.empty())
        {
            return false;
        }

        dce.check_send_iterate();

        auto &encrypt_ctx = dce.encrypt();

        /* header of the packet. op32 (opcode + peer-id) + 8 byte of epoch + epoch counter */
        BufferAllocated pkt_header{4 + 8};

        pkt_header.write(op32, 4);
        encrypt_ctx.pid.write_next(pkt_header);

        std::array<uint8_t, EpochDataChannelCryptoContext::IV_SIZE> calculated_iv{};
        encrypt_ctx.calculate_iv(pkt_header.data() + 4, calculated_iv);

        // encrypt to work buf
        frame->prepare(Frame::ENCRYPT_WORK, work_encrypt);
        if (work_encrypt.max_size() < buf.size())
            throw aead_epoch_error("encrypt work buffer too small");

        unsigned char *work_data = work_encrypt.write_alloc(buf.size());

        // alloc auth tag in buffer where it needs to be
        uint8_t *auth_tag = work_encrypt.write_alloc(CRYPTO_API::CipherContextAEAD::AUTH_TAG_LEN);

        // encrypt. Epoch data always uses full header for authenticated data
        encrypt_ctx.cipher.encrypt(buf.data(), work_data, buf.size(), calculated_iv.data(), auth_tag, pkt_header.data(), pkt_header.size());

        buf.swap(work_encrypt);

        // prepend additional data from the pkt_header but without the opcode and peer-id (first 4 bytes)
        buf.prepend(pkt_header.c_data() + 4, pkt_header.size() - 4);

        return dce.should_renegotiate();
    }

    Error::Type decrypt(BufferAllocated &buf, const std::time_t now, const unsigned char *op32) override
    {
        // only process non-null packets but report empty packets as success
        if (buf.empty())
        {
            return Error::SUCCESS;
        }



        if (buf.size() < PacketIDData::size(true))
        {
            /* Packet is too small to even have a packet id */
            return Error::DECRYPT_ERROR;
        }

        // Reconstruct header since we don't get the continuous memory that we received from wire but already
        // split into op32 and the rest of the packet.
        BufferAllocated pkt_header{4 + 8};
        pkt_header.write(op32, 4);
        auto *packet_id = pkt_header.write_alloc(8);
        buf.read(packet_id, 8);

        // Extract epoch from packet
        ConstBuffer packet_id_buf{packet_id, 8, true};
        PacketIDData pid{true};
        pid.read(packet_id_buf);

        auto *decrypt_ctx = dce.lookup_decrypt_key(pid.get_epoch());

        if (!decrypt_ctx)
        {
            // failed to look up the epoch. Report error.
            return Error::DECRYPT_ERROR;
        }

        // calculate IV from implicit IV and packet ID
        std::array<uint8_t, EpochDataChannelCryptoContext::IV_SIZE> calculated_iv{};
        decrypt_ctx->calculate_iv(packet_id, calculated_iv);

        // initialize work buffer.
        frame->prepare(Frame::DECRYPT_WORK, work_decrypt);
        if (work_decrypt.max_size() < buf.size())
            throw aead_epoch_error("decrypt work buffer too small");

        // decrypt from buf -> work
        if (!decrypt_ctx->cipher.decrypt(buf.c_data(), work_decrypt.data(), buf.size(), calculated_iv.data(), nullptr, pkt_header.data(), pkt_header.size()))
        {
            buf.reset_size();
            return Error::DECRYPT_ERROR;
        }

        work_decrypt.set_size(buf.size() - CRYPTO_API::CipherContextAEAD::AUTH_TAG_LEN);

        // verify packet ID
        if (!decrypt_ctx->pid.test_add(pid, now, stats))
        {
            buf.reset_size();
            return Error::REPLAY_ERROR;
        }

        // Check if the other side has moved its epoch send key further
        // and we need to adjust our active recv key and generate new future keys
        dce.replace_update_recv_key(decrypt_ctx->epoch, stats);

        // return cleartext result in buf
        buf.swap(work_decrypt);

        return Error::SUCCESS;
    }

    // Initialization

    void init_cipher(StaticKey &&encrypt_key, StaticKey &&decrypt_key) override
    {
        if (!dc_settings.useEpochKeys())
        {
            throw aead_epoch_error("AEAD Epoch requires epoch keys to be in use");
        }

        /* Initialise the epoch key management with the encrypt and decrypt key as epoch 1 keys */
        dce = {dc_settings.cipher(), std::move(encrypt_key), std::move(decrypt_key), libctx};
    }

    void init_hmac(StaticKey &&encrypt_key,
                   StaticKey &&decrypt_key) override
    {
        /* Implicit IVs are derived in DataChannelEpoch class and AEAD does not use
         * a separate HMAC, so this is just a dummy that does nothing.*/
    }

    void init_pid(const char *recv_name,
                  const int recv_unit,
                  const SessionStats::Ptr &recv_stats_arg) override
    {
    }

    unsigned int defined() const override
    {
        unsigned int ret = CRYPTO_DEFINED;

        if (CryptoAlgs::defined(dc_settings.cipher()))
            ret |= CIPHER_DEFINED;
        return ret;
    }

    bool consider_compression([[maybe_unused]] const CompressContext &comp_ctx) override
    {
        return true;
    }

    void rekey(const RekeyType type) override
    {
    }

    // Force using a new epoch on send. Currently mainly used for unit testing
    void increase_send_epoch()
    {
        dce.iterate_send_key();
    }

  private:
    CryptoDCSettingsData dc_settings;
    Frame::Ptr frame;
    SessionStats::Ptr stats;
    SSLLib::Ctx libctx;
    DataChannelEpoch dce;
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

    CryptoDCInstance::Ptr new_obj([[maybe_unused]] const unsigned int key_id) override
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
        /* encap_overhead does not really return the encapsulation overhead of this channel as it ignores
         * the packet-id and 4 byte opcode.
         *
         * We keep this in line with the normal AEAD data channel defined in crypto_aead_epoch.hpp, and the
         * keep the difference to that correct. The difference in overhead are the 4 bytes in the larger packet counter/epoch */
        return CRYPTO_API::CipherContextAEAD::AUTH_TAG_LEN + 4;
    }

  private:
    CryptoDCSettingsData dc_settings;
    Frame::Ptr frame;
    SessionStats::Ptr stats;
    SSLLib::Ctx libctx;
};
} // namespace openvpn::AEADEpoch

#endif
