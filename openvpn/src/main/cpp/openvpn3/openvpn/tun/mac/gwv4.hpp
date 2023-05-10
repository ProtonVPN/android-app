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

// Get IPv4 gateway info on Mac OS X.

#ifndef OPENVPN_TUN_MAC_GWV4_H
#define OPENVPN_TUN_MAC_GWV4_H

#include <sys/types.h>
#include <sys/socket.h>
#include <sys/ioctl.h>
#include <netinet/in.h>
#include <net/route.h>
#include <net/if.h>
#include <net/if_dl.h>

#include <cstring>
#include <string>
#include <sstream>
#include <algorithm> // for std::max
#include <cstdint>   // for std::uint32_t
#include <memory>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/hexstr.hpp>
#include <openvpn/common/scoped_fd.hpp>
#include <openvpn/common/socktypes.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/addr/ip.hpp>
#include <openvpn/addr/addrpair.hpp>
#include <openvpn/addr/macaddr.hpp>

namespace openvpn {
class MacGatewayInfoV4
{
    struct rtmsg
    {
        struct rt_msghdr m_rtm;
        char m_space[512];
    };

#define OPENVPN_ROUNDUP(a) \
    ((a) > 0 ? (1 + (((a)-1) | (sizeof(std::uint32_t) - 1))) : sizeof(std::uint32_t))

#define OPENVPN_NEXTADDR(w, u)         \
    if (rtm_addrs & (w))               \
    {                                  \
        l = OPENVPN_ROUNDUP(u.sa_len); \
        std::memmove(cp, &(u), l);     \
        cp += l;                       \
    }

#define OPENVPN_ADVANCE(x, n) \
    (x += OPENVPN_ROUNDUP((n)->sa_len))

  public:
    OPENVPN_EXCEPTION(route_gateway_error);

    enum
    {
        ADDR_DEFINED = (1 << 0),    /* set if gateway.addr defined */
        NETMASK_DEFINED = (1 << 1), /* set if gateway.netmask defined */
        HWADDR_DEFINED = (1 << 2),  /* set if hwaddr is defined */
        IFACE_DEFINED = (1 << 3),   /* set if iface is defined */
    };

    MacGatewayInfoV4()
        : flags_(0)
    {
        struct rtmsg m_rtmsg;
        ScopedFD sockfd;
        int seq, l, pid, rtm_addrs;
        struct sockaddr so_dst, so_mask;
        char *cp = m_rtmsg.m_space;
        struct sockaddr *gate = nullptr, *ifp = nullptr, *sa;
        struct rt_msghdr *rtm_aux;

        /* setup data to send to routing socket */
        pid = ::getpid();
        seq = 0;
        rtm_addrs = RTA_DST | RTA_NETMASK | RTA_IFP;

        std::memset(&m_rtmsg, 0, sizeof(m_rtmsg));
        std::memset(&so_dst, 0, sizeof(so_dst));
        std::memset(&so_mask, 0, sizeof(so_mask));
        std::memset(&m_rtmsg.m_rtm, 0, sizeof(struct rt_msghdr));

        m_rtmsg.m_rtm.rtm_type = RTM_GET;
        m_rtmsg.m_rtm.rtm_flags = RTF_UP | RTF_GATEWAY;
        m_rtmsg.m_rtm.rtm_version = RTM_VERSION;
        m_rtmsg.m_rtm.rtm_seq = ++seq;
        m_rtmsg.m_rtm.rtm_addrs = rtm_addrs;

        so_dst.sa_family = AF_INET;
        so_dst.sa_len = sizeof(struct sockaddr_in);
        so_mask.sa_family = AF_INET;
        so_mask.sa_len = sizeof(struct sockaddr_in);

        OPENVPN_NEXTADDR(RTA_DST, so_dst);
        OPENVPN_NEXTADDR(RTA_NETMASK, so_mask);

        m_rtmsg.m_rtm.rtm_msglen = l = cp - (char *)&m_rtmsg;

        /* transact with routing socket */
        sockfd.reset(socket(PF_ROUTE, SOCK_RAW, 0));
        if (!sockfd.defined())
            throw route_gateway_error("GDG: socket #1 failed");
        if (::write(sockfd(), (char *)&m_rtmsg, l) < 0)
            throw route_gateway_error("GDG: problem writing to routing socket");
        do
        {
            l = ::read(sockfd(), (char *)&m_rtmsg, sizeof(m_rtmsg));
        } while (l > 0 && (m_rtmsg.m_rtm.rtm_seq != seq || m_rtmsg.m_rtm.rtm_pid != pid));
        sockfd.close();

        /* extract return data from routing socket */
        rtm_aux = &m_rtmsg.m_rtm;
        cp = ((char *)(rtm_aux + 1));
        if (rtm_aux->rtm_addrs)
        {
            for (unsigned int i = 1; i; i <<= 1u)
            {
                if (i & rtm_aux->rtm_addrs)
                {
                    sa = (struct sockaddr *)cp;
                    if (i == RTA_GATEWAY)
                        gate = sa;
                    else if (i == RTA_IFP)
                        ifp = sa;
                    OPENVPN_ADVANCE(cp, sa);
                }
            }
        }
        else
            return;

        /* get gateway addr and interface name */
        if (gate != nullptr)
        {
            /* get default gateway addr */
            gateway_.addr.reset_ipv4_from_uint32(ntohl(((struct sockaddr_in *)gate)->sin_addr.s_addr));
            if (!gateway_.addr.unspecified())
                flags_ |= ADDR_DEFINED;

            if (ifp)
            {
                /* get interface name */
                const struct sockaddr_dl *adl = (struct sockaddr_dl *)ifp;
                const size_t len = adl->sdl_nlen;
                if (len && len < sizeof(iface_))
                {
                    std::memcpy(iface_, adl->sdl_data, len);
                    iface_[len] = '\0';
                    flags_ |= IFACE_DEFINED;
                }
            }
        }

        /* get netmask of interface that owns default gateway */
        if (flags_ & IFACE_DEFINED)
        {
            struct ifreq ifr;

            sockfd.reset(socket(AF_INET, SOCK_DGRAM, 0));
            if (!sockfd.defined())
                throw route_gateway_error("GDG: socket #2 failed");

            std::memset(&ifr, 0, sizeof(ifr));
            ifr.ifr_addr.sa_family = AF_INET;
            string::strncpynt(ifr.ifr_name, iface_, IFNAMSIZ);

            if (::ioctl(sockfd(), SIOCGIFNETMASK, (char *)&ifr) < 0)
                throw route_gateway_error("GDG: ioctl #1 failed");
            sockfd.close();

            gateway_.netmask.reset_ipv4_from_uint32(ntohl(((struct sockaddr_in *)&ifr.ifr_addr)->sin_addr.s_addr));
            flags_ |= NETMASK_DEFINED;
        }

        /* try to read MAC addr associated with interface that owns default gateway */
        if (flags_ & IFACE_DEFINED)
        {
            struct ifconf ifc;
            const int bufsize = 4096;

            std::unique_ptr<char[]> buffer(new char[bufsize]);
            std::memset(buffer.get(), 0, bufsize);
            sockfd.reset(socket(AF_INET, SOCK_DGRAM, 0));
            if (!sockfd.defined())
                throw route_gateway_error("GDG: socket #3 failed");

            ifc.ifc_len = bufsize;
            ifc.ifc_buf = buffer.get();

            if (::ioctl(sockfd(), SIOCGIFCONF, (char *)&ifc) < 0)
                throw route_gateway_error("GDG: ioctl #2 failed");
            sockfd.close();

            for (cp = buffer.get(); cp <= buffer.get() + ifc.ifc_len - sizeof(struct ifreq);)
            {
                ifreq ifr = {};
                std::memcpy(&ifr, cp, sizeof(ifr));
                const size_t len = sizeof(ifr.ifr_name) + std::max(sizeof(ifr.ifr_addr), size_t(ifr.ifr_addr.sa_len));
                if (!ifr.ifr_addr.sa_family)
                    break;
                if (!::strncmp(ifr.ifr_name, iface_, IFNAMSIZ))
                {
                    if (ifr.ifr_addr.sa_family == AF_LINK)
                    {
                        /* This is a broken member access. struct sockaddr_dl has
                         * 20 bytes while if_addr has only 16 bytes. But sockaddr_dl
                         * has 12 bytes space for the hw address and Ethernet only uses
                         * 6 bytes. So the last 4 that are truncated can be ignored here
                         *
                         * So we use a memcpy here to avoid the warnings with ASAN that we
                         * are doing a very nasty cast here
                         */
                        static_assert(sizeof(ifr.ifr_addr) >= 12, "size of if_addr too small to contain MAC");
                        static_assert(sizeof(sockaddr_dl) >= sizeof(ifr.ifr_addr), "dest struct needs to be larger than source struct");
                        sockaddr_dl sdl{};
                        std::memcpy(&sdl, &ifr.ifr_addr, sizeof(ifr.ifr_addr));
                        hwaddr_.reset((const unsigned char *)LLADDR(&sdl));
                        flags_ |= HWADDR_DEFINED;
                    }
                }
                cp += len;
            }
        }
    }
#undef OPENVPN_ROUNDUP
#undef OPENVPN_NEXTADDR
#undef OPENVPN_ADVANCE

    std::string info() const
    {
        std::ostringstream os;
        os << "GATEWAY";
        if (flags_ & ADDR_DEFINED)
        {
            os << " ADDR=" << gateway_.addr;
            if (flags_ & NETMASK_DEFINED)
            {
                os << '/' << gateway_.netmask;
            }
        }
        if (flags_ & IFACE_DEFINED)
            os << " IFACE=" << iface_;
        if (flags_ & HWADDR_DEFINED)
            os << " HWADDR=" << hwaddr_;
        return os.str();
    }

    unsigned int flags() const
    {
        return flags_;
    }
    const IP::Addr &gateway_addr() const
    {
        return gateway_.addr;
    }
    std::string gateway_addr_str() const
    {
        return gateway_addr().to_string();
    }
    const IP::Addr &gateway_netmask() const
    {
        return gateway_.netmask;
    }
    std::string gateway_netmask_str() const
    {
        return gateway_netmask().to_string();
    }
    std::string iface() const
    {
        return iface_;
    }
    const MACAddr &hwaddr() const
    {
        return hwaddr_;
    }

    bool iface_addr_defined() const
    {
        return (flags_ & (ADDR_DEFINED | IFACE_DEFINED)) == (ADDR_DEFINED | IFACE_DEFINED);
    }

    bool hwaddr_defined() const
    {
        return flags_ & HWADDR_DEFINED;
    }

  private:
    unsigned int flags_;
    IP::AddrMaskPair gateway_;
    char iface_[16];
    MACAddr hwaddr_;
};

} // namespace openvpn

#endif
