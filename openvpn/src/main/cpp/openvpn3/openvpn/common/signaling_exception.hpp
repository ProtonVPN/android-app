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

#include <openvpn/common/exception.hpp>

namespace openvpn {

// When exceptions are used to trigger a daemon
// restart, use this exception to disable such
// behavior.
OPENVPN_EXCEPTION(signaling_exception);

} // namespace openvpn
