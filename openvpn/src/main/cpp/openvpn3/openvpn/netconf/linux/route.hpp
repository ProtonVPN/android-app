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

// Add routes on Linux using AF_NETLINK socket

#ifndef OPENVPN_NETCONF_LINUX_ROUTE_H
#define OPENVPN_NETCONF_LINUX_ROUTE_H

#include <cstring>
#include <unistd.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <linux/netlink.h>
#include <linux/rtnetlink.h>
#include <net/if.h>
#include <errno.h>

#include <string>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/scoped_fd.hpp>
#include <openvpn/common/strerror.hpp>
#include <openvpn/addr/route.hpp>

namespace openvpn {

class LinuxRoute
{
  public:
    OPENVPN_EXCEPTION(linux_route_error);

    LinuxRoute()
    {
        fd.reset(::socket(AF_NETLINK, SOCK_RAW, NETLINK_ROUTE));
        if (!fd.defined())
            throw linux_route_error("creating AF_NETLINK socket");

        struct sockaddr_nl local;
        ::memset(&local, 0, sizeof(local));
        local.nl_family = AF_NETLINK;
        local.nl_pad = 0;
        local.nl_pid = 0; // only use getpid() if unique instantiation per process
        local.nl_groups = 0;
        if (::bind(fd(), (struct sockaddr *)&local, sizeof(local)) < 0)
            throw linux_route_error("binding to AF_NETLINK socket");
    }

    void add_delete(const bool add,
                    const IP::Route &route,
                    const int if_index,
                    const int table = RT_TABLE_MAIN)
    {
        typedef struct
        {
            struct nlmsghdr nlmsg_info;
            struct rtmsg rtmsg_info;
            char buffer[64]; // must be large enough to contain request
        } netlink_req_t;

        struct rtattr *rtattr_ptr;
        int rtmsg_len;
        struct sockaddr_nl peer;
        struct msghdr msg_info;
        struct iovec iov_info;
        netlink_req_t netlink_req;

        ::memset(&peer, 0, sizeof(peer));
        peer.nl_family = AF_NETLINK;
        peer.nl_pad = 0;
        peer.nl_pid = 0;
        peer.nl_groups = 0;

        ::memset(&msg_info, 0, sizeof(msg_info));
        msg_info.msg_name = (void *)&peer;
        msg_info.msg_namelen = sizeof(peer);

        ::memset(&netlink_req, 0, sizeof(netlink_req));

        rtmsg_len = sizeof(struct rtmsg);

        // add destination addr
        rtattr_ptr = (struct rtattr *)netlink_req.buffer;
        rtattr_ptr->rta_type = RTA_DST;
        rtattr_ptr->rta_len = sizeof(struct rtattr) + route.addr.size_bytes();
        route.addr.to_byte_string_variable(((unsigned char *)rtattr_ptr) + sizeof(struct rtattr));
        rtmsg_len += rtattr_ptr->rta_len;

        // add if_index
        rtattr_ptr = (struct rtattr *)(((unsigned char *)rtattr_ptr) + rtattr_ptr->rta_len);
        rtattr_ptr->rta_type = RTA_OIF;
        rtattr_ptr->rta_len = sizeof(struct rtattr) + 4;
        ::memcpy(((unsigned char *)rtattr_ptr) + sizeof(struct rtattr), &if_index, 4);
        rtmsg_len += rtattr_ptr->rta_len;

        netlink_req.nlmsg_info.nlmsg_len = NLMSG_LENGTH(rtmsg_len);

        if (add)
        {
            netlink_req.nlmsg_info.nlmsg_flags = NLM_F_REQUEST | NLM_F_CREATE;
            netlink_req.nlmsg_info.nlmsg_type = RTM_NEWROUTE;
        }
        else // delete
        {
            netlink_req.nlmsg_info.nlmsg_flags = NLM_F_REQUEST;
            netlink_req.nlmsg_info.nlmsg_type = RTM_DELROUTE;
        }

        netlink_req.rtmsg_info.rtm_family = route.addr.family();
        netlink_req.rtmsg_info.rtm_table = table;
        netlink_req.rtmsg_info.rtm_dst_len = route.prefix_len; // add prefix

        netlink_req.rtmsg_info.rtm_protocol = RTPROT_STATIC;
        netlink_req.rtmsg_info.rtm_scope = RT_SCOPE_UNIVERSE;
        netlink_req.rtmsg_info.rtm_type = RTN_UNICAST;

        iov_info.iov_base = (void *)&netlink_req.nlmsg_info;
        iov_info.iov_len = netlink_req.nlmsg_info.nlmsg_len;
        msg_info.msg_iov = &iov_info;
        msg_info.msg_iovlen = 1;

        const ssize_t status = ::sendmsg(fd(), &msg_info, 0);
        if (status < 0)
        {
            const int eno = errno;
            OPENVPN_THROW(linux_route_error, "add_delete: sendmsg failed: " << strerror_str(eno));
        }
    }

    static int if_index(const std::string &iface)
    {
        const unsigned int ret = ::if_nametoindex(iface.c_str());
        if (!ret)
            OPENVPN_THROW(linux_route_error, "if_index: no such interface: " << iface);
        return ret;
    }

  private:
    ScopedFD fd;
};

} // namespace openvpn

#endif
