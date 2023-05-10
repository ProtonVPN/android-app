//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2022 OpenVPN Inc.
//    Copyright (C) 2020-2022 Lev Stipakov <lev@openvpn.net>
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU Affero General Public License Version 3
//    as published by the Free Software Foundation.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU Affero General Public License for more details.
//
//    You should have received a copy of the GNU Affero General Public License
//    along with this program in the COPYING file.
//    If not, see <http://www.gnu.org/licenses/>.

// ovpn-dco crypto wrappers

#pragma once

namespace openvpn {
namespace KoRekey {

/**
 * @brief Parses key information into format consumed by ovpn-dco.
 *
 */
class OvpnDcoKey : public Key
{
  public:
    OvpnDcoKey(const CryptoDCInstance::RekeyType rktype, const Info &rkinfo)
    {
        std::memset(&kc, 0, sizeof(kc));

        kc.remote_peer_id = rkinfo.remote_peer_id;

        const CryptoDCContext::Info ci = rkinfo.dc_context_delegate->crypto_info();
        const CryptoAlgs::Alg &calg = CryptoAlgs::get(ci.cipher_alg);
        switch (ci.cipher_alg)
        {
        case CryptoAlgs::NONE:
            kc.cipher_alg = OVPN_CIPHER_ALG_NONE;
            kc.encrypt.cipher_key_size = 0;
            break;
        case CryptoAlgs::AES_128_GCM:
            kc.cipher_alg = OVPN_CIPHER_ALG_AES_GCM;
            kc.encrypt.cipher_key_size = 128 / 8;
            break;
        case CryptoAlgs::AES_192_GCM:
            kc.cipher_alg = OVPN_CIPHER_ALG_AES_GCM;
            kc.encrypt.cipher_key_size = 192 / 8;
            break;
        case CryptoAlgs::AES_256_GCM:
            kc.cipher_alg = OVPN_CIPHER_ALG_AES_GCM;
            kc.encrypt.cipher_key_size = 256 / 8;
            break;
        case CryptoAlgs::CHACHA20_POLY1305:
            kc.cipher_alg = OVPN_CIPHER_ALG_CHACHA20_POLY1305;
            kc.encrypt.cipher_key_size = 256 / 8;
            break;
        default:
            OPENVPN_THROW(korekey_error,
                          "cipher alg " << calg.name()
                                        << " is not currently supported by ovpn-dco");
            break;
        }
        kc.decrypt.cipher_key_size = kc.encrypt.cipher_key_size;

        if (calg.mode() == CryptoAlgs::AEAD)
        {
            kc.encrypt.cipher_key = verify_key("cipher encrypt",
                                               rkinfo.encrypt_cipher,
                                               kc.encrypt.cipher_key_size);
            kc.decrypt.cipher_key = verify_key("cipher decrypt",
                                               rkinfo.decrypt_cipher,
                                               kc.decrypt.cipher_key_size);

            set_nonce_tail("AEAD nonce tail encrypt",
                           kc.encrypt.nonce_tail,
                           sizeof(kc.encrypt.nonce_tail),
                           rkinfo.encrypt_hmac);
            set_nonce_tail("AEAD nonce tail decrypt",
                           kc.decrypt.nonce_tail,
                           sizeof(kc.decrypt.nonce_tail),
                           rkinfo.decrypt_hmac);
        }

        kc.key_id = rkinfo.key_id;
    }

    const struct KeyConfig *operator()() const
    {
        return &kc;
    }

  private:
    struct KeyConfig kc;
};

} // namespace KoRekey
} // namespace openvpn
