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

// Base classes for Emulate Excluded Routes

#ifndef OPENVPN_TUN_CLIENT_EMUEXR_H
#define OPENVPN_TUN_CLIENT_EMUEXR_H

#include <openvpn/common/rc.hpp>
#include <openvpn/client/ipverflags.hpp>
#include <openvpn/tun/builder/base.hpp>

namespace openvpn {
  struct EmulateExcludeRoute : public RC<thread_unsafe_refcount>
  {
    typedef RCPtr<EmulateExcludeRoute> Ptr;

    virtual void add_route(const bool add, const IP::Addr& addr, const int prefix_len) = 0;
    virtual bool enabled(const IPVerFlags& ipv) const = 0;
    virtual void emulate(TunBuilderBase* tb, IPVerFlags& ipv, const IP::Addr& server_addr) const = 0;
    virtual void add_default_routes(bool ipv4, bool ipv6) = 0;
  };

  struct EmulateExcludeRouteFactory : public RC<thread_unsafe_refcount>
  {
    typedef RCPtr<EmulateExcludeRouteFactory> Ptr;

    virtual EmulateExcludeRoute::Ptr new_obj() const = 0;
  };
}

#endif
