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

// General-purpose OpenVPN protocol encrypt method (CBC/HMAC) that is independent of the underlying CRYPTO_API

#ifndef OPENVPN_CRYPTO_ENCRYPT_CHM_H
#define OPENVPN_CRYPTO_ENCRYPT_CHM_H

#include <cstring>
#include <utility>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/random/randapi.hpp>
#include <openvpn/frame/frame.hpp>
#include <openvpn/crypto/cipher.hpp>
#include <openvpn/crypto/ovpnhmac.hpp>
#include <openvpn/crypto/static_key.hpp>
#include <openvpn/crypto/packet_id_data.hpp>

namespace openvpn {
template <typename CRYPTO_API>
class EncryptCHM
{
  public:
    OPENVPN_SIMPLE_EXCEPTION(chm_unsupported_cipher_mode);

    void encrypt(BufferAllocated &buf)
    {
        // skip null packets
        if (!buf.size())
            return;

        if (cipher.defined())
        {
            // workspace for generating IV
            unsigned char iv_buf[CRYPTO_API::CipherContext::MAX_IV_LENGTH];
            const size_t iv_length = cipher.iv_length();

            // IV and packet ID are generated differently depending on cipher mode
            const int cipher_mode = cipher.cipher_mode();
            if (cipher_mode == CRYPTO_API::CipherContext::CIPH_CBC_MODE)
            {
                // in CBC mode, use an explicit, random IV
                rng->rand_bytes(iv_buf, iv_length);

                // generate fresh outgoing packet ID and prepend to cleartext buffer
                pid_send.prepend_next(buf);
            }
            else
            {
                throw chm_unsupported_cipher_mode();
            }

            // initialize work buffer
            frame->prepare(Frame::ENCRYPT_WORK, work);

            // encrypt from buf -> work
            const size_t encrypt_bytes = cipher.encrypt(iv_buf, work.data(), work.max_size(), buf.c_data(), buf.size());
            if (!encrypt_bytes)
            {
                buf.reset_size();
                return;
            }
            work.set_size(encrypt_bytes);

            // prepend the IV to the ciphertext
            work.prepend(iv_buf, iv_length);

            // HMAC the ciphertext
            prepend_hmac(work);

            // return ciphertext result in buf
            buf.swap(work);
        }
        else // no encryption
        {
            // generate fresh outgoing packet ID and prepend to cleartext buffer
            pid_send.prepend_next(buf);

            // HMAC the cleartext
            prepend_hmac(buf);
        }
    }

    void set_rng(StrongRandomAPI::Ptr rng_arg)
    {
        rng = std::move(rng_arg);
    }

    Frame::Ptr frame;
    CipherContext<CRYPTO_API> cipher;
    OvpnHMAC<CRYPTO_API> hmac;
    PacketIDDataSend pid_send{};

  private:
    // compute HMAC signature of data buffer,
    // then prepend the signature to the buffer.
    void prepend_hmac(BufferAllocated &buf)
    {
        if (hmac.defined())
        {
            const unsigned char *content = buf.data();
            const size_t content_size = buf.size();
            const size_t hmac_size = hmac.output_size();
            unsigned char *hmac_buf = buf.prepend_alloc(hmac_size);
            hmac.hmac(hmac_buf, hmac_size, content, content_size);
        }
    }

    BufferAllocated work;
    StrongRandomAPI::Ptr rng;
};

} // namespace openvpn

#endif // OPENVPN_CRYPTO_ENCRYPT_H
