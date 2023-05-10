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

#pragma once

#include <openvpn/common/arch.hpp>
#include <openvpn/common/size.hpp>
#include <atomic>

#if defined(USE_OPENSSL)
#include <openssl/crypto.h>
#endif

// Does this architecture allow efficient unaligned access?

#if defined(OPENVPN_ARCH_x86_64) || defined(OPENVPN_ARCH_i386)
#define OPENVPN_HAVE_EFFICIENT_UNALIGNED_ACCESS
#endif

// Define a portable compiler memory access fence (from Boost).

#if defined(__INTEL_COMPILER)

#define OPENVPN_COMPILER_FENCE __memory_barrier();

#elif defined(_MSC_VER) && _MSC_VER >= 1310

extern "C" void _ReadWriteBarrier();
#pragma intrinsic(_ReadWriteBarrier)

#define OPENVPN_COMPILER_FENCE _ReadWriteBarrier();

#elif defined(__GNUC__)

#define OPENVPN_COMPILER_FENCE __asm__ __volatile__("" \
                                                    :  \
                                                    :  \
                                                    : "memory");

#else

#error need memory fence definition for this compiler

#endif

// C++ doesn't allow increment of void *

#define OPENVPN_INCR_VOID_PTR(var, incr) (var) = static_cast<const unsigned char *>(var) + (incr)

namespace openvpn {
namespace crypto {
/**
 * memneq - Compare two areas of memory in constant time
 *
 * @a: first area of memory
 * @b: second area of memory
 * @size: The length of the memory area to compare
 *
 * Returns false when data is equal, true otherwise
 */
inline bool memneq(const void *a, const void *b, size_t size);

#if defined(USE_OPENSSL)
inline bool memneq(const void *a, const void *b, size_t size)
{
    // memcmp does return 0 (=false) when the memory is equal. It normally
    // returns the position of first mismatch otherwise but the crypto
    // variants only promise to return something != 0 (=true)
    return (bool)(CRYPTO_memcmp(a, b, size));
}
#else
inline bool memneq(const void *a, const void *b, size_t size)
{
    // This is inspired by  mbedtls' internal safer_memcmp function:
    const unsigned char *x = (const unsigned char *)a;
    const unsigned char *y = (const unsigned char *)b;
    unsigned char diff = 0;

    for (size_t i = 0; i < size; i++)
    {
        unsigned char u = x[i], v = y[i];
        diff |= u ^ v;
    }
    atomic_thread_fence(std::memory_order_release);
    return bool(diff);
}
#endif
} // namespace crypto
} // namespace openvpn
