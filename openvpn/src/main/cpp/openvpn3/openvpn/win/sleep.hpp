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

#ifndef OPENVPN_WIN_SLEEP_H
#define OPENVPN_WIN_SLEEP_H

#include <windows.h>

#include <string>

#include <openvpn/common/action.hpp>
#include <openvpn/common/to_string.hpp>

namespace openvpn {

class WinSleep : public Action
{
  public:
    typedef RCPtr<WinSleep> Ptr;

    WinSleep(DWORD dwMilliseconds_arg)
        : dwMilliseconds(dwMilliseconds_arg)
    {
    }

    virtual void execute(std::ostream &os) override
    {
        os << to_string() << std::endl;
        ::Sleep(dwMilliseconds);
    }

    virtual std::string to_string() const override
    {
        return "Sleeping for " + openvpn::to_string(dwMilliseconds) + " milliseconds...";
    }

  private:
    DWORD dwMilliseconds;
};

} // namespace openvpn
#endif
