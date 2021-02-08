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

#ifndef OPENVPN_LINUX_DAEMON_ALIVE_H
#define OPENVPN_LINUX_DAEMON_ALIVE_H

#include <openvpn/common/file.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/number.hpp>

namespace openvpn {
  inline int daemon_pid(const std::string& cmd,
			const std::string& pidfile)
  {
    try {
      std::string pidstr = read_text(pidfile);
      string::trim_crlf(pidstr);
      const std::string cmdline_fn = "/proc/" + pidstr + "/cmdline";
      BufferPtr cmdbuf = read_binary_linear(cmdline_fn);
      const size_t len = ::strnlen((const char *)cmdbuf->c_data(), cmdbuf->size());
      if (cmd == std::string((const char *)cmdbuf->c_data(), len))
	{
	  int ret;
	  if (parse_number(pidstr, ret))
	    return ret;
	}
    }
    catch (const std::exception& e)
      {
      }
    return -1;
  }

  inline bool is_daemon_alive(const std::string& cmd,
			      const std::string& pidfile)
  {
    return daemon_pid(cmd, pidfile) >= 0;
  }
}

#endif
