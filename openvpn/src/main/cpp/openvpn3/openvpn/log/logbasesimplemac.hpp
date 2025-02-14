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

#ifndef OPENVPN_LOG_LOGBASESIMPLEMAC_H
#define OPENVPN_LOG_LOGBASESIMPLEMAC_H

#include <iostream>
#include <mutex>

#include <openvpn/log/logbase.hpp>
#include <openvpn/time/timestr.hpp>

#include <os/log.h>

namespace openvpn {
class LogBaseSimpleMac : public LogBase
{
  public:
    typedef RCPtr<LogBaseSimpleMac> Ptr;

    LogBaseSimpleMac()
        : log_context(this)
    {
        os_log_with_type(OS_LOG_DEFAULT,
                         OS_LOG_TYPE_DEFAULT,
                         "LogBaseSimple for macOS/iOS initialized");
    }

    virtual void log(const std::string &str) override
    {
        std::lock_guard<std::mutex> lock(mutex);
        os_log_with_type(OS_LOG_DEFAULT,
                         OS_LOG_TYPE_DEFAULT,
                         "OVPN-CORE: %{public}s",
                         str.c_str());
    }

  private:
    std::mutex mutex;
    Log::Context log_context;
};
} // namespace openvpn

#endif
