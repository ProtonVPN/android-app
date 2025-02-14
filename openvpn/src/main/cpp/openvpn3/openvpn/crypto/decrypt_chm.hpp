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

// General-purpose OpenVPN protocol decrypt method (CBC/HMAC) that is independent of the underlying CRYPTO_API

#ifndef OPENVPN_CRYPTO_DECRYPT_CHM_H
#define OPENVPN_CRYPTO_DECRYPT_CHM_H

#include <cstring>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/memneq.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/frame/frame.hpp>
#include <openvpn/crypto/cipher.hpp>
#include <openvpn/crypto/ovpnhmac.hpp>
#include <openvpn/crypto/static_key.hpp>
#include <openvpn/crypto/packet_id_control.hpp>
#include <openvpn/log/sessionstats.hpp>

namespace openvpn {

template <typename CRYPTO_API>
class DecryptCHM
{
  public:
    OPENVPN_SIMPLE_EXCEPTION(chm_unsupported_cipher_mode);

    Error::Type decrypt(BufferAllocated &buf, const std::time_t now)
    {
        // skip null packets
        if (!buf.size())
            return Error::SUCCESS;

        // verify the HMAC
        if (hmac.defined())
        {
            unsigned char local_hmac[CRYPTO_API::HMACContext::MAX_HMAC_SIZE];
            const size_t hmac_size = hmac.output_size();
            const unsigned char *packet_hmac = buf.read_alloc(hmac_size);
            hmac.hmac(local_hmac, hmac_size, buf.c_data(), buf.size());
            if (crypto::memneq(local_hmac, packet_hmac, hmac_size))
            {
                buf.reset_size();
                return Error::HMAC_ERROR;
            }
        }

        // decrypt packet ID + payload
        if (cipher.defined())
        {
            unsigned char iv_buf[CRYPTO_API::CipherContext::MAX_IV_LENGTH];
            const size_t iv_length = cipher.iv_length();

            // extract IV from head of packet
            buf.read(iv_buf, iv_length);

            // initialize work buffer
            frame->prepare(Frame::DECRYPT_WORK, work);

            // decrypt from buf -> work
            const size_t decrypt_bytes = cipher.decrypt(iv_buf, work.data(), work.max_size(), buf.c_data(), buf.size());
            if (!decrypt_bytes)
            {
                buf.reset_size();
                return Error::DECRYPT_ERROR;
            }
            work.set_size(decrypt_bytes);

            // handle different cipher modes
            const int cipher_mode = cipher.cipher_mode();
            if (cipher_mode == CRYPTO_API::CipherContext::CIPH_CBC_MODE)
            {
                if (!verify_packet_id(work, now))
                {
                    buf.reset_size();
                    return Error::REPLAY_ERROR;
                }
            }
            else
            {
                throw chm_unsupported_cipher_mode();
            }

            // return cleartext result in buf
            buf.swap(work);
        }
        else // no encryption
        {
            if (!verify_packet_id(buf, now))
            {
                buf.reset_size();
                return Error::REPLAY_ERROR;
            }
        }
        return Error::SUCCESS;
    }

    Frame::Ptr frame;
    CipherContext<CRYPTO_API> cipher;
    OvpnHMAC<CRYPTO_API> hmac;
    PacketIDDataReceive pid_recv;
    SessionStats::Ptr stats;

  private:
    bool verify_packet_id(BufferAllocated &buf, const std::time_t now)
    {
        const PacketIDData pid = pid_recv.read_next(buf);
        return pid_recv.test_add(pid, now, stats);
    }

    BufferAllocated work;
};

} // namespace openvpn

#endif // OPENVPN_CRYPTO_DECRYPT_H
