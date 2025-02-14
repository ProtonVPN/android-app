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

#include <openvpn/common/path.hpp>
#include <openvpn/common/hexstr.hpp>
#include <openvpn/random/randapi.hpp>

namespace openvpn {

// Generate a temporary filename that is a variant
// of an existing filename.
inline std::string tmp_filename(const std::string &fn,
                                const std::string &tmpdir,
                                StrongRandomAPI &rng)
{
    unsigned char data[16];
    rng.rand_fill(data);
    return path::join(tmpdir, '.' + path::basename(fn).substr(0, 64) + '.' + render_hex(data, sizeof(data)));
}

} // namespace openvpn
