//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2022 OpenVPN Inc.
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

#ifndef OPENVPN_MBEDTLS_UTIL_PKCS1_H
#define OPENVPN_MBEDTLS_UTIL_PKCS1_H

#include <openvpn/pki/pkcs1.hpp>

namespace openvpn {
namespace PKCS1 {
namespace DigestPrefix {
class MbedTLSParse : public Parse<mbedtls_md_type_t>
{
  public:
    MbedTLSParse()
        : Parse(MBEDTLS_MD_NONE,
                MBEDTLS_MD_MD2,
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
        case MBEDTLS_MD_MD2:
            return "MBEDTLS_MD_MD2";
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
} // namespace DigestPrefix
} // namespace PKCS1
} // namespace openvpn

#endif
