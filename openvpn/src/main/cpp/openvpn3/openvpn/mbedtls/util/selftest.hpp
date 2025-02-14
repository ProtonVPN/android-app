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

// Call various mbed TLS self-test functions

#ifndef OPENVPN_MBEDTLS_UTIL_SELFTEST_H
#define OPENVPN_MBEDTLS_UTIL_SELFTEST_H

#include <sstream>

#include <mbedtls/bignum.h>
#if MBEDTLS_VERSION_NUMBER < 0x03000000
#include <mbedtls/config.h>
#endif
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
