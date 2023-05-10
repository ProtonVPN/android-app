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

// Enumerate iOS network interfaces

#ifndef OPENVPN_NETCONF_ENUMIFACE_H
#define OPENVPN_NETCONF_ENUMIFACE_H

#include <sys/types.h>
#include <sys/socket.h>
#include <ifaddrs.h>

#ifdef OPENVPN_PLATFORM_IPHONE
#include <openvpn/netconf/ios/net-route.h>
#else
#include <net/route.h>
#endif

#include <cstring>
#include <string>
#include <sstream>
#include <memory>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/addr/ip.hpp>

namespace openvpn {
class EnumIface
{
  public:
    OPENVPN_EXCEPTION(enum_iface_error);

    EnumIface()
        : ifinfo(alloc_if_addrs(), free_if_addrs)
    {
    }

    std::string to_string() const
    {
        std::ostringstream os;
        os << "INTERFACES:" << std::endl;
        for (const ifaddrs *i = ifinfo.get(); i->ifa_next; i = i->ifa_next)
            render(i, os);
        return os.str();
    }

    bool iface_up(const char *iface) const
    {
        for (const ifaddrs *i = ifinfo.get(); i->ifa_next; i = i->ifa_next)
        {
            if (!std::strcmp(iface, i->ifa_name)
                && (i->ifa_flags & RTF_UP)
                && IP::Addr::sockaddr_defined(i->ifa_addr))
                return true;
        }
        return false;
    }

  protected:
    static void render(const ifaddrs *i, std::ostream &os)
    {
        try
        {
            os << i->ifa_name;
            os << ' ' << render_flags(i->ifa_flags);
            if (i->ifa_addr)
            {
                const IP::Addr a = IP::Addr::from_sockaddr(i->ifa_addr);
                if (a.defined())
                    os << ' ' << a;
            }
            if (i->ifa_netmask)
            {
                const IP::Addr nm = IP::Addr::from_sockaddr(i->ifa_netmask);
                if (nm.defined())
                {
                    try
                    {
                        unsigned int pl = nm.prefix_len();
                        os << '/' << pl;
                    }
                    catch (const std::exception &)
                    {
                    }
                }
            }
            if (i->ifa_dstaddr)
            {
                const IP::Addr dst = IP::Addr::from_sockaddr(i->ifa_dstaddr);
                if (dst.defined())
                    os << " (" << dst << ')';
            }
        }
        catch (const std::exception &)
        {
            os << " ERROR";
        }
        os << std::endl;
    }

    static std::string render_flags(const u_int flags)
    {
        struct flag_info
        {
            u_int flag;
            char c;
        };
        static const struct flag_info flag_info[] = {
#ifdef RTF_UP
            {RTF_UP, 'U'}, // Route usable
#endif
#ifdef RTF_GATEWAY
            {RTF_GATEWAY, 'G'}, // Destination requires forwarding by intermediary
#endif
#ifdef RTF_HOST
            {RTF_HOST, 'H'}, // Host entry (net otherwise)
#endif
#ifdef RTF_REJECT
            {RTF_REJECT, 'R'}, // Host or net unreachable
#endif
#ifdef RTF_DYNAMIC
            {RTF_DYNAMIC, 'D'}, // Created dynamically (by redirect)
#endif
#ifdef RTF_MODIFIED
            {RTF_MODIFIED, 'M'}, // Modified dynamically (by redirect)
#endif
#ifdef RTF_CLONING
            {RTF_CLONING, 'C'}, // Generate new routes on use
#endif
#ifdef RTF_XRESOLVE
            {RTF_XRESOLVE, 'X'}, // External daemon translates proto to link address
#endif
#ifdef RTF_LLINFO
            {RTF_LLINFO, 'L'}, // Valid protocol to link address translation
#endif
#ifdef RTF_STATIC
            {RTF_STATIC, 'S'}, // Manually added
#endif
#ifdef RTF_BLACKHOLE
            {RTF_BLACKHOLE, 'B'}, // Just discard packets (during updates)
#endif
#ifdef RTF_PROTO2
            {RTF_PROTO2, '2'}, // Protocol specific routing flag #2
#endif
#ifdef RTF_PROTO1
            {RTF_PROTO1, '1'}, // Protocol specific routing flag #1
#endif
#ifdef RTF_PRCLONING
            {RTF_PRCLONING, 'c'}, // Protocol-specified generate new routes on use
#endif
#ifdef RTF_WASCLONED
            {RTF_WASCLONED, 'W'}, // Route was generated as a result of cloning
#endif
#ifdef RTF_PROTO3
            {RTF_PROTO3, '3'}, // Protocol specific routing flag #3
#endif
#ifdef RTF_BROADCAST
            {RTF_BROADCAST, 'b'}, // The route represents a broadcast address
#endif
#ifdef RTF_MULTICAST
            {RTF_MULTICAST, 'm'}, // The route represents a multicast address
#endif
#ifdef RTF_IFSCOPE
            {RTF_IFSCOPE, 'I'}, // Route is associated with an interface scope
#endif
#ifdef RTF_IFREF
            {RTF_IFREF, 'i'}, // Route is holding a reference to the interface
#endif
#ifdef RTF_PROXY
            {RTF_PROXY, 'Y'}, // Proxying; cloned routes will not be scoped
#endif
#ifdef RTF_ROUTER
            {RTF_ROUTER, 'r'}, // Host is a default router
#endif
            {0, '\0'},
        };

        std::string ret;
        for (const struct flag_info *fi = flag_info; fi->flag; ++fi)
            if (flags & fi->flag)
                ret += fi->c;
        return ret;
    }

    static ifaddrs *alloc_if_addrs()
    {
        ifaddrs *ifa = nullptr;
        ::getifaddrs(&ifa);
        return ifa;
    }

    static void free_if_addrs(ifaddrs *p)
    {
        // delete method for pointer returned by getifaddrs
        freeifaddrs(p);
    }

    std::unique_ptr<ifaddrs, decltype(&free_if_addrs)> ifinfo;
};
} // namespace openvpn

#endif
