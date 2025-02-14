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

// Low-level methods used to implement HTTP Digest authentication

#ifndef OPENVPN_PROXY_HTTPDIGEST_H
#define OPENVPN_PROXY_HTTPDIGEST_H

#include <cstring> // for std::strlen and others
#include <string>

#include <openvpn/crypto/hashstr.hpp>

namespace openvpn::HTTPProxy {

class Digest
{
  public:
    // calculate H(A1) as per spec
    static std::string calcHA1(DigestFactory &digest_factory,
                               const std::string &alg,
                               const std::string &username,
                               const std::string &realm,
                               const std::string &password,
                               const std::string &nonce,
                               const std::string &cnonce)
    {
        HashString h1(digest_factory, CryptoAlgs::MD5);
        h1.update(username);
        h1.update(':');
        h1.update(realm);
        h1.update(':');
        h1.update(password);
        BufferPtr result = h1.final();

        if (string::strcasecmp(alg, "md5-sess") == 0)
        {
            HashString h2(digest_factory, CryptoAlgs::MD5);
            h2.update(*result);
            h2.update(':');
            h2.update(nonce);
            h2.update(':');
            h2.update(cnonce);
            result = h2.final();
        }
        return render_hex_generic(*result);
    }

    // calculate request-digest/response-digest as per HTTP Digest spec
    static std::string calcResponse(DigestFactory &digest_factory,
                                    const std::string &hA1,         // H(A1)
                                    const std::string &nonce,       // nonce from server
                                    const std::string &nonce_count, // 8 hex digits
                                    const std::string &cnonce,      // client nonce
                                    const std::string &qop,         // qop-value: "", "auth", "auth-int"
                                    const std::string &method,      // method from the request
                                    const std::string &digestUri,   // requested URI
                                    const std::string &hEntity)     // H(entity body) if qop="auth-int"
    {
        // calculate H(A2)
        HashString h1(digest_factory, CryptoAlgs::MD5);
        h1.update(method);
        h1.update(':');
        h1.update(digestUri);
        if (string::strcasecmp(qop, "auth-int") == 0)
        {
            h1.update(':');
            h1.update(hEntity);
        }
        const std::string hA2 = h1.final_hex();

        // calculate response
        HashString h2(digest_factory, CryptoAlgs::MD5);
        h2.update(hA1);
        h2.update(':');
        h2.update(nonce);
        h2.update(':');
        if (!qop.empty())
        {
            h2.update(nonce_count);
            h2.update(':');
            h2.update(cnonce);
            h2.update(':');
            h2.update(qop);
            h2.update(':');
        }
        h2.update(hA2);
        return h2.final_hex();
    }
};
} // namespace openvpn::HTTPProxy

#endif
