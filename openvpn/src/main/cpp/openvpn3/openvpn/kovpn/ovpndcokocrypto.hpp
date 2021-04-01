//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
//    Copyright (C) 2020-2020 Lev Stipakov <lev@openvpn.net>
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
class OvpnDcoKey : public Key {
public:
  OvpnDcoKey(const CryptoDCInstance::RekeyType rktype, const Info &rkinfo) {
    std::memset(&kc, 0, sizeof(kc));

    kc.remote_peer_id = rkinfo.remote_peer_id;

    const CryptoDCContext::Info ci = rkinfo.dc_context_delegate->crypto_info();
    const CryptoAlgs::Alg &calg = CryptoAlgs::get(ci.cipher_alg);
    switch (ci.cipher_alg) {
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
    case CryptoAlgs::AES_128_CBC:
      kc.cipher_alg = OVPN_CIPHER_ALG_AES_CBC;
      kc.encrypt.cipher_key_size = 128 / 8;
      break;
    case CryptoAlgs::AES_192_CBC:
      kc.cipher_alg = OVPN_CIPHER_ALG_AES_CBC;
      kc.encrypt.cipher_key_size = 192 / 8;
      break;
    case CryptoAlgs::AES_256_CBC:
      kc.cipher_alg = OVPN_CIPHER_ALG_AES_CBC;
      kc.encrypt.cipher_key_size = 256 / 8;
      break;
    default:
      OPENVPN_THROW(korekey_error,
                    "cipher alg " << calg.name()
                                  << " is not currently supported by ovpn-dco");
      break;
    }
    kc.decrypt.cipher_key_size = kc.encrypt.cipher_key_size;

    kc.encrypt.cipher_key = verify_key("cipher encrypt", rkinfo.encrypt_cipher,
                                       kc.encrypt.cipher_key_size);
    kc.decrypt.cipher_key = verify_key("cipher decrypt", rkinfo.decrypt_cipher,
                                       kc.decrypt.cipher_key_size);

    switch (calg.mode()) {
    case CryptoAlgs::CBC_HMAC:
      // if CBC mode, process HMAC digest
      {
        const CryptoAlgs::Alg &halg = CryptoAlgs::get(ci.hmac_alg);
        switch (ci.hmac_alg) {
        case CryptoAlgs::SHA256:
          kc.hmac_alg = OVPN_HMAC_ALG_SHA256;
          break;
        case CryptoAlgs::SHA512:
          kc.hmac_alg = OVPN_HMAC_ALG_SHA512;
          break;
        default:
          OPENVPN_THROW(korekey_error,
                        "HMAC alg "
                            << halg.name()
                            << " is not currently supported by ovpn-dco");
        }
        kc.encrypt.hmac_key_size = halg.size();
        kc.decrypt.hmac_key_size = kc.encrypt.hmac_key_size;

        // set hmac keys
        kc.encrypt.hmac_key = verify_key("hmac encrypt", rkinfo.encrypt_hmac,
                                         kc.encrypt.hmac_key_size);
        kc.decrypt.hmac_key = verify_key("hmac decrypt", rkinfo.decrypt_hmac,
                                         kc.decrypt.hmac_key_size);
      }
      break;

    case CryptoAlgs::AEAD:
      set_nonce_tail("AEAD nonce tail encrypt", kc.encrypt.nonce_tail,
                     sizeof(kc.encrypt.nonce_tail), rkinfo.encrypt_hmac);
      set_nonce_tail("AEAD nonce tail decrypt", kc.decrypt.nonce_tail,
                     sizeof(kc.decrypt.nonce_tail), rkinfo.decrypt_hmac);

      break;

    default: {
      // should have been caught above
      throw korekey_error("internal error");
    }
    }

    kc.key_id = rkinfo.key_id;
  }

  const struct KeyConfig *operator()() const { return &kc; }

private:
  struct KeyConfig kc;
};

} // namespace KoRekey
} // namespace openvpn