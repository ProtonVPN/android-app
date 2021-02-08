//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU Affero General Public License Version 3
//    as published by the Free Software Foundation.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU Affero General Public License for more details.
//
//    You should have received a copy of the GNU Affero General Public License
//    along with this program in the COPYING file.
//    If not, see <http://www.gnu.org/licenses/>.

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
      os_log_with_type(OS_LOG_DEFAULT, OS_LOG_TYPE_DEFAULT,
		       "LogBaseSimple for macOS/iOS initialized");
    }

    virtual void log(const std::string& str) override
    {
	std::lock_guard<std::mutex> lock(mutex);
	os_log_with_type(OS_LOG_DEFAULT, OS_LOG_TYPE_DEFAULT,
                         "OVPN-CORE: %{public}s", str.c_str());
    }

  private:
    std::mutex mutex;
    Log::Context log_context;
  };
}

#endif
