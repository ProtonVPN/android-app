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

// This is a general-purpose logging framework that allows for OPENVPN_LOG and
// OPENVPN_LOG_NTNL macros to dispatch logging data to a thread-local handler.

#pragma once

// the following define was part of the header guard that was replaced by the
// "#pragma once".  Apparently, some logging consumers check that this macro is
// defined and their logging breaks if it is not.  Adding it back here as the
// path of least resistance to fix; but IMO, the macro name should be better.
#define OPENVPN_LOG_LOGTHREAD_H

#include "openvpn/log/logthread_macros.hpp"
#include "openvpn/log/logthread_class.hpp"
