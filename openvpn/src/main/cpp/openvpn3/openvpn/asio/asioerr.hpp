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

#ifndef OPENVPN_ASIO_ASIOERR_H
#define OPENVPN_ASIO_ASIOERR_H

#include <string>

#include <openvpn/io/io.hpp> // was: #include <asio/error_code.hpp>

namespace openvpn {

// returns a string describing an i/o error code
template <typename ErrorCode>
inline std::string errinfo(ErrorCode err)
{
    openvpn_io::error_code e(err, openvpn_io::system_category());
    return e.message();
}

} // namespace openvpn

#endif
