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

#include <openvpn/log/logbase.hpp>
#include <openvpn/win/logutil.hpp>

namespace openvpn::Win {

class LogFile : public LogBase
{
  public:
    typedef RCPtr<LogFile> Ptr;

    LogFile(const std::string &fn,
            const std::string &sddl_string,
            bool append)
        : log_handle(LogUtil::create_file(fn, sddl_string, append)),
          log_context(this)
    {
    }

    virtual void log(const std::string &str) override
    {
        LogUtil::log(log_handle(), str);
    }

  private:
    ScopedHANDLE log_handle;
    Log::Context log_context; // must be initialized last
};

} // namespace openvpn::Win
