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

// Demangle a C++ name (GCC only)

#ifndef OPENVPN_COMMON_DEMANGLE_H
#define OPENVPN_COMMON_DEMANGLE_H

#include <cxxabi.h>

#include <string>
#include <memory>

namespace openvpn {

inline std::string cxx_demangle(const char *mangled_name)
{
    int status;
    std::unique_ptr<char, decltype(&free)> realname{abi::__cxa_demangle(mangled_name, 0, 0, &status), &free};

    return status ? "DEMANGLE_ERROR" : realname.get();
}

} // namespace openvpn

#endif
