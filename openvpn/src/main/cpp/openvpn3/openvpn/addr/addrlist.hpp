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

#ifndef OPENVPN_ADDR_ADDRLIST_H
#define OPENVPN_ADDR_ADDRLIST_H

#include <openvpn/common/rc.hpp>
#include <openvpn/addr/ip.hpp>

namespace openvpn::IP {

// A list of unique IP addresses
class AddrList : public std::vector<IP::Addr>, public RC<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<AddrList> Ptr;

    void add(const IP::Addr &a)
    {
        if (!exists(a))
            push_back(a);
    }

    bool exists(const IP::Addr &a) const
    {
        return std::find(begin(), end(), a) != end();
    }

#if 0
      void dump() const
      {
          OPENVPN_LOG("******* AddrList::dump");
          for (const auto& i : *this)
          {
              OPENVPN_LOG(i.to_string());
          }
      }
#endif
};
} // namespace openvpn::IP

#endif
