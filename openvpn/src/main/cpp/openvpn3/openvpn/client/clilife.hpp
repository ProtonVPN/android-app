//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2022 OpenVPN Inc.
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

#ifndef OPENVPN_CLIENT_CLILIFE_H
#define OPENVPN_CLIENT_CLILIFE_H

#include <string>

#include <openvpn/common/rc.hpp>

namespace openvpn {
// Base class for managing connection lifecycle notifications,
// such as sleep, wakeup, network-unavailable, network-available.
class ClientLifeCycle : public RC<thread_unsafe_refcount>
{
  public:
    struct NotifyCallback
    {
        virtual void cln_stop() = 0;
        virtual void cln_pause(const std::string &reason) = 0;
        virtual void cln_resume() = 0;
        virtual void cln_reconnect(int seconds) = 0;
    };

    typedef RCPtr<ClientLifeCycle> Ptr;

    virtual bool network_available() = 0;

    virtual void start(NotifyCallback *) = 0;
    virtual void stop() = 0;
};
} // namespace openvpn

#endif
