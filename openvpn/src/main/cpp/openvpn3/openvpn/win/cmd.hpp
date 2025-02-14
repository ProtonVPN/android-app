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

    WinCmd(const std::string &command)
        : cmd(command)
    {
    }

    virtual void execute(std::ostream &os) override
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

} // namespace openvpn
#endif
