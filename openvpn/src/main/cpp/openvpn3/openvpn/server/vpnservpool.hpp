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

#ifndef OPENVPN_SERVER_VPNSERVPOOL_H
#define OPENVPN_SERVER_VPNSERVPOOL_H

#include <sstream>
#include <vector>
#include <memory>
#include <mutex>
#include <thread>
#include <cstdint> // for std::uint32_t

#include <openvpn/common/exception.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/common/arraysize.hpp>
#include <openvpn/server/vpnservnetblock.hpp>
#include <openvpn/addr/ip.hpp>
#include <openvpn/addr/route.hpp>
#include <openvpn/addr/pool.hpp>

namespace openvpn {
  namespace VPNServerPool {

    OPENVPN_EXCEPTION(vpn_serv_pool_error);

    struct IP46
    {
      void add_routes(std::vector<IP::Route>& rtvec)
      {
	if (ip4.defined())
	  rtvec.emplace_back(ip4, ip4.size());
	if (ip6.defined())
	  rtvec.emplace_back(ip6, ip6.size());
      }

      std::string to_string() const
      {
	std::ostringstream os;
	os << '[' << ip4 << ' ' << ip6 << ']';
	return os.str();
      }

      bool defined() const
      {
	return ip4.defined() || ip6.defined();
      }

      IP::Addr ip4;
      IP::Addr ip6;
    };

    class Pool : public VPNServerNetblock
    {
    public:
      enum Flags {
	IPv4_DEPLETION=(1<<0),
	IPv6_DEPLETION=(1<<1),
      };

      Pool(const OptionList& opt)
	: VPNServerNetblock(init_snb_from_opt(opt))
      {
	if (configured(opt, "server"))
	  {
	    pool4.add_range(netblock4().clients);
	    pool6.add_range(netblock6().clients);
	  }
      }

      // returns flags
      unsigned int acquire(IP46& addr_pair, const bool request_ipv6)
      {
	std::lock_guard<std::mutex> lock(mutex);
	unsigned int flags = 0;
	if (!pool4.acquire_addr(addr_pair.ip4))
	  flags |= IPv4_DEPLETION;
	if (request_ipv6 && netblock6().defined())
	  {
	    if (!pool6.acquire_addr(addr_pair.ip6))
	      flags |= IPv6_DEPLETION;
	  }
	return flags;
      }

      void release(IP46& addr_pair)
      {
	std::lock_guard<std::mutex> lock(mutex);
	if (addr_pair.ip4.defined())
	  pool4.release_addr(addr_pair.ip4);
	if (addr_pair.ip6.defined())
	  pool6.release_addr(addr_pair.ip6);
      }

    private:
      static VPNServerNetblock init_snb_from_opt(const OptionList& opt)
      {
	if (configured(opt, "server"))
	  return VPNServerNetblock(opt, "server", false, 0);
	else if (configured(opt, "ifconfig"))
	  return VPNServerNetblock(opt, "ifconfig", false, 0);
	else
	  return VPNServerNetblock();
      }

      static bool configured(const OptionList& opt,
			     const std::string& opt_name)
      {
	return opt.exists(opt_name) || opt.exists(opt_name + "-ipv6");
      }

      std::mutex mutex;

      IP::Pool pool4;
      IP::Pool pool6;
    };

    class IP46AutoRelease : public IP46, public RC<thread_safe_refcount>
    {
    public:
      typedef RCPtr<IP46AutoRelease> Ptr;

      IP46AutoRelease(Pool* pool_arg)
	: pool(pool_arg)
      {
      }

      ~IP46AutoRelease()
      {
	if (pool)
	  pool->release(*this);
      }

    private:
      Pool* pool;
    };

  }
}

#endif
