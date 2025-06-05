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

#ifndef ALIGNMENT_HEADER_PARSED
#define ALIGNMENT_HEADER_PARSED

#include <cstring>
#include <type_traits>

namespace openvpn {

/**
 * @brief Converts a byte buffer to the desired type, avoiding undefined behavior due to alignment.
 * @details Replaces a simple cast with an alignment safe alternative. Useful when scraping data
 * out of wire oriented buffers and so on.
 * @note It is assumed that the source pointed to by the passed pointer is large enough to hold
 * the type T as source, and that the type T is trivially copyable. Due to the newer RVO rules and
 * typical compiler optimizations, this should boil down to a straight memcpy, and on some
 * platforms that copy might even be optimized away.
 * @note The reason for using a pointer to void is that using a pointer to T to allow
 * inference of T would cause the compiler to require a cast to T at the call site in most
 * cases which seems like a lot more work for the same thing.
 * @tparam T type to convert to
 * @param toAlign starting address of the bytes to be converted
 * @return T output value and type
 */
template <typename T>
    requires ::std::is_trivially_copyable_v<T>
T alignment_safe_extract(const void *toAlign) noexcept
{
    T ret;
    std::memcpy(&ret, toAlign, sizeof(T));
    return ret;
}

} // namespace openvpn

#endif