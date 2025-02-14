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

#include <openvpn/common/rc.hpp>

#define OPENVPN_LOG_CLASS openvpn::LogBase

namespace openvpn {

/**
 * @brief The logging interface, simple, logs a string
 */
struct LogBase : RC<thread_safe_refcount>
{
    // As demonstrated here by the comment out of Ptr, objects of type LogBase are
    // never used in the intrusive pointer mode.  However, removing the base class
    // exposes other types derived from LogBase (e.g., RunContextBase) which are reliant
    // upon the RC base class here.  FIXME!

    // typedef RCPtr<LogBase> Ptr;

    virtual void log(const std::string &str) = 0;
};

} // namespace openvpn
