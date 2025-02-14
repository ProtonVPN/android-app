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

// the following define was part of the header guard that was replaced by the
// "#pragma once".  Apparently, some logging consumers check that this macro is
// defined and their logging breaks if it is not.  Adding it back here as the
// path of least resistance to fix; but IMO, the macro name should be better.
#define OPENVPN_LOG_LOGBASE_H

#include "openvpn/log/logbase_class.hpp"

#define OPENVPN_LOG_INFO(x) x

#include <openvpn/log/logthread.hpp>
