//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2017 OpenVPN Inc.
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

#ifndef OPENVPN_WIN_CMD_H
#define OPENVPN_WIN_CMD_H

#include <windows.h>

#include <string>
#include <regex>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/action.hpp>
#include <openvpn/common/unicode.hpp>
#include <openvpn/win/call.hpp>

namespace openvpn {

  class WinCmd : public Action
  {
  public:
    typedef RCPtr<WinCmd> Ptr;

    WinCmd(const std::string& command)
      : cmd(command)
    {
    }

    virtual void execute(std::ostream& os) override
    {
      os << cmd << std::endl;
      std::string out = Win::call(cmd);
      os << out;
    }

    virtual std::string to_string() const override
    {
      return cmd;
    }

  private:
    std::string cmd;
  };

}
#endif
