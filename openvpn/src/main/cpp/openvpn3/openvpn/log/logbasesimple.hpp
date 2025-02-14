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

#ifndef OPENVPN_LOG_LOGBASESIMPLE_H
#define OPENVPN_LOG_LOGBASESIMPLE_H

#include <iostream>
#include <mutex>

#include <openvpn/log/logbase.hpp>
#include <openvpn/time/timestr.hpp>

namespace openvpn {
class LogBaseSimple : public LogBase
{
  public:
    typedef RCPtr<LogBaseSimple> Ptr;

    LogBaseSimple()
        : log_context(this)
    {
    }

    virtual void log(const std::string &str) override
    {
        const std::string ts = date_time();
        {
            std::lock_guard<std::mutex> lock(mutex);
            std::cout << ts << ' ' << str << std::flush;
        }
    }

  private:
    std::mutex mutex;
    Log::Context log_context;
};
} // namespace openvpn

#endif
