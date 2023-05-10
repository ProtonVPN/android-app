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

// Call various mbed TLS self-test functions

#ifndef OPENVPN_MBEDTLS_UTIL_SELFTEST_H
#define OPENVPN_MBEDTLS_UTIL_SELFTEST_H

#include <sstream>

#include <mbedtls/bignum.h>
#include <mbedtls/config.h>
#include <mbedtls/cipher.h>
#include <mbedtls/aes.h>
#include <mbedtls/sha1.h>
#include <mbedtls/sha256.h>
#include <mbedtls/sha512.h>

namespace openvpn {
inline std::string crypto_self_test_mbedtls()
{
    std::ostringstream os;
#ifdef MBEDTLS_SELF_TEST
    const int verbose = 1;
    os << "mbed TLS self test (tests return 0 if successful):" << std::endl;
    os << "  mbedlts_aes_self_test status=" << mbedtls_aes_self_test(verbose) << std::endl;
    os << "  mbedtls_sha1_self_test status=" << mbedtls_sha1_self_test(verbose) << std::endl;
    os << "  mbedtls_sha256_self_test status=" << mbedtls_sha256_self_test(verbose) << std::endl;
    os << "  mbedtls_sha512_self_test status=" << mbedtls_sha512_self_test(verbose) << std::endl;
    os << "  mbedtls_mpi_self_test status=" << mbedtls_mpi_self_test(verbose) << std::endl;
#else
    os << "mbed TLS self test: not compiled" << std::endl;
#endif
    return os.str();
}
} // namespace openvpn

#endif
