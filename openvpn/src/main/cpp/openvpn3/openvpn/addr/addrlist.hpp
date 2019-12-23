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

#ifndef OPENVPN_ADDR_ADDRLIST_H
#define OPENVPN_ADDR_ADDRLIST_H

#include <openvpn/common/rc.hpp>
#include <openvpn/addr/ip.hpp>

namespace openvpn {
  namespace IP {

    // A list of unique IP addresses
    class AddrList : public std::vector<IP::Addr>, public RC<thread_unsafe_refcount>
    {
    public:
      typedef RCPtr<AddrList> Ptr;

      void add(const IP::Addr& a)
      {
	if (!exists(a))
	  push_back(a);
      }

      bool exists(const IP::Addr& a) const
      {
	for (const_iterator i = begin(); i != end(); ++i)
	  {
	    if (a == *i)
	      return true;
	  }
	return false;
      }

#if 0
      void dump() const
      {
	OPENVPN_LOG("******* AddrList::dump");
	for (const_iterator i = begin(); i != end(); ++i)
	  OPENVPN_LOG(i->to_string());
      }
#endif
    };
  }
}

#endif
