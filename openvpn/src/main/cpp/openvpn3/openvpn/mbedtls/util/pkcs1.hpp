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

#ifndef OPENVPN_MBEDTLS_UTIL_PKCS1_H
#define OPENVPN_MBEDTLS_UTIL_PKCS1_H

#include <openvpn/pki/pkcs1.hpp>

namespace openvpn::PKCS1::DigestPrefix {

class MbedTLSParse : public Parse<mbedtls_md_type_t>
{
  public:
    MbedTLSParse()
        : Parse(MBEDTLS_MD_NONE,
                MBEDTLS_MD_MD5,
                MBEDTLS_MD_SHA1,
                MBEDTLS_MD_SHA256,
                MBEDTLS_MD_SHA384,
                MBEDTLS_MD_SHA512)
    {
    }

    static const char *to_string(const mbedtls_md_type_t t)
    {
        switch (t)
        {
        case MBEDTLS_MD_NONE:
            return "MBEDTLS_MD_NONE";
        case MBEDTLS_MD_MD5:
            return "MBEDTLS_MD_MD5";
        case MBEDTLS_MD_SHA1:
            return "MBEDTLS_MD_SHA1";
        case MBEDTLS_MD_SHA256:
            return "MBEDTLS_MD_SHA256";
        case MBEDTLS_MD_SHA384:
            return "MBEDTLS_MD_SHA384";
        case MBEDTLS_MD_SHA512:
            return "MBEDTLS_MD_SHA512";
        default:
            return "MBEDTLS_MD_???";
        }
    }
};
} // namespace openvpn::PKCS1::DigestPrefix

#endif
