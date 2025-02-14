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

#pragma once

#include <string>
#include <atomic>

namespace openvpn::crypto {

// Compare strings in a way that is more resistant to timing attacks.
// s1 should be the string provided by the user, while s2 is the
// "secret" string that we are comparing s1 against.
// Our goal is to prevent timing data from leaking info about the
// length or content of s2.
//  https://nachtimwald.com/2017/04/02/constant-time-string-comparison-in-c/
inline bool str_neq(const char *s1, const char *s2)
{
    unsigned int neq = 0;
    size_t i = 0;
    size_t j = 0;

    while (true)
    {
        neq |= s1[i] ^ s2[j];

        if (s1[i] == '\0')
            break;
        i++;

        atomic_thread_fence(std::memory_order_acq_rel);
        if (s2[j] != '\0')
            j++;
        atomic_thread_fence(std::memory_order_acq_rel);
    }
    atomic_thread_fence(std::memory_order_acq_rel);
    return bool(neq);
}

inline bool str_neq(const std::string &s1, const std::string &s2)
{
    return str_neq(s1.c_str(), s2.c_str());
}
} // namespace openvpn::crypto
