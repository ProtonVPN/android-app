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

#pragma once

#include <openvpn/log/logbase.hpp>
#include <openvpn/win/logutil.hpp>

namespace openvpn {
  namespace Win {

    class LogFile : public LogBase
    {
    public:
      typedef RCPtr<LogFile> Ptr;

      LogFile(const std::string& fn,
	      const std::string& sddl_string,
	      bool append)
	: log_handle(LogUtil::create_file(fn, sddl_string, append)),
	  log_context(this)
      {
      }

      virtual void log(const std::string& str) override
      {
	LogUtil::log(log_handle(), str);
      }

    private:
      ScopedHANDLE log_handle;
      Log::Context log_context; // must be initialized last
    };

  }
}
