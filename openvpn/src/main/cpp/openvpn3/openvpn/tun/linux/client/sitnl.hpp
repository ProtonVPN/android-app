//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
//    Copyright (C) 2018-2020 Antonio Quartulli <antonio@openvpn.net>
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

#pragma once

#include <errno.h>
#include <string.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <linux/netlink.h>
#include <linux/rtnetlink.h>

#include <openvpn/addr/ip.hpp>
#include <openvpn/addr/ipv4.hpp>
#include <openvpn/addr/ipv6.hpp>
#include <openvpn/addr/route.hpp>


#ifdef DEBUG_RTNL
#define OPENVPN_LOG_RTNL(_x) OPENVPN_LOG(_x)
#else
#define OPENVPN_LOG_RTNL(_x)
#endif

namespace openvpn {
  namespace TunNetlink {

#define SNDBUF_SIZE (1024 * 2)
#define RCVBUF_SIZE (1024 * 4)

#define SITNL_ADDATTR(_msg, _max_size, _attr, _data, _size)         \
    {                                                               \
        if (sitnl_addattr(_msg, _max_size, _attr, _data, _size) < 0)\
        {                                                           \
            goto err;                                               \
        }                                                           \
    }

#define NLMSG_TAIL(nmsg) \
    ((struct rtattr *)(((uint8_t *)(nmsg)) + NLMSG_ALIGN((nmsg)->nlmsg_len)))

    /* this class contains only static members */
    class SITNL
    {
    private:
      /**
       * Link state request message
       */
      struct sitnl_link_req
      {
	struct nlmsghdr n;
	struct ifinfomsg i;
	char buf[256];
      };

      /**
       * Address request message
       */
      struct sitnl_addr_req
      {
	struct nlmsghdr n;
	struct ifaddrmsg i;
	char buf[256];
      };

      /**
       * Route request message
       */
      struct sitnl_route_req
      {
	struct nlmsghdr n;
	struct rtmsg r;
	char buf[256];
      };

      typedef int (*sitnl_parse_reply_cb)(struct nlmsghdr *msg, void *arg);

      /**
       * Helper function used to easily add attributes to a rtnl message
       */
      static int
      sitnl_addattr(struct nlmsghdr *n, int maxlen, int type, const void *data,
		    int alen)
      {
	int len = RTA_LENGTH(alen);
	struct rtattr *rta;

	if ((int)(NLMSG_ALIGN(n->nlmsg_len) + RTA_ALIGN(len)) > maxlen)
	{
	  OPENVPN_LOG(__func__ << ": rtnl: message exceeded bound of " << maxlen);
	  return -EMSGSIZE;
	}

	rta = NLMSG_TAIL(n);
	rta->rta_type = type;
	rta->rta_len = len;

	if (!data)
	{
	  memset(RTA_DATA(rta), 0, alen);
	}
	else
	{
	  memcpy(RTA_DATA(rta), data, alen);
	}

	n->nlmsg_len = NLMSG_ALIGN(n->nlmsg_len) + RTA_ALIGN(len);

	return 0;
      }

      /**
       * Open RTNL socket
       */
      static int
      sitnl_socket(void)
      {
	int sndbuf = SNDBUF_SIZE;
	int rcvbuf = RCVBUF_SIZE;
	int fd;

	fd = socket(AF_NETLINK, SOCK_RAW, NETLINK_ROUTE);
	if (fd < 0)
	{
	  OPENVPN_LOG(__func__ << ": cannot open netlink socket");
	  return fd;
	}

	if (setsockopt(fd, SOL_SOCKET, SO_SNDBUF, &sndbuf, sizeof(sndbuf)) < 0)
	{
	  OPENVPN_LOG(__func__ << ": SO_SNDBUF");
	  close(fd);
	  return -1;
	}

	if (setsockopt(fd, SOL_SOCKET, SO_RCVBUF, &rcvbuf, sizeof(rcvbuf)) < 0)
	{
	  OPENVPN_LOG(__func__ << ": SO_RCVBUF");
	  close(fd);
	  return -1;
	}

	return fd;
      }

      /**
       * Bind socket to Netlink subsystem
       */
      static int
      sitnl_bind(int fd, uint32_t groups)
      {
	socklen_t addr_len;
	struct sockaddr_nl local = { };

	local.nl_family = AF_NETLINK;
	local.nl_groups = groups;

	if (bind(fd, (struct sockaddr *)&local, sizeof(local)) < 0)
	{
	  OPENVPN_LOG(__func__ << ": cannot bind netlink socket");
	  return -errno;
	}

	addr_len = sizeof(local);
	if (getsockname(fd, (struct sockaddr *)&local, &addr_len) < 0)
	{
	  OPENVPN_LOG(__func__ << ": cannot getsockname");
	  return -errno;
	}

	if (addr_len != sizeof(local))
	{
	  OPENVPN_LOG(__func__ << ": wrong address length " << addr_len);
	  return -EINVAL;
	}

	if (local.nl_family != AF_NETLINK)
	{
	  OPENVPN_LOG(__func__ << ": wrong address family " << local.nl_family);
	  return -EINVAL;
	}

	return 0;
      }

      /**
       * Send Netlink message and run callback on reply (if specified)
       */
      static int
      sitnl_send(struct nlmsghdr *payload, pid_t peer, unsigned int groups,
		 sitnl_parse_reply_cb cb, void *arg_cb)
      {
	int len, rem_len, fd, ret, rcv_len;
	struct sockaddr_nl nladdr = { };
	struct nlmsgerr *err;
	struct nlmsghdr *h;
	unsigned int seq;
	char buf[1024 * 16];
	struct iovec iov =
	{
	  .iov_base = payload,
	  .iov_len = payload->nlmsg_len,
	};
	struct msghdr nlmsg =
	{
	  .msg_name = &nladdr,
	  .msg_namelen = sizeof(nladdr),
	  .msg_iov = &iov,
	  .msg_iovlen = 1,
	};

	nladdr.nl_family = AF_NETLINK;
	nladdr.nl_pid = peer;
	nladdr.nl_groups = groups;

	payload->nlmsg_seq = seq = time(NULL);

	/* no need to send reply */
	if (!cb)
	{
	  payload->nlmsg_flags |= NLM_F_ACK;
	}

	fd = sitnl_socket();
	if (fd < 0)
	{
	  OPENVPN_LOG(__func__ << ": can't open rtnl socket");
	  return -errno;
	}

	ret = sitnl_bind(fd, 0);
	if (ret < 0)
	{
	  OPENVPN_LOG(__func__ << ": can't bind rtnl socket");
	  ret = -errno;
	  goto out;
	}

	ret = sendmsg(fd, &nlmsg, 0);
	if (ret < 0)
	{
	  OPENVPN_LOG(__func__ << ": rtnl: error on sendmsg()");
	  ret = -errno;
	  goto out;
	}

	/* prepare buffer to store RTNL replies */
	memset(buf, 0, sizeof(buf));
	iov.iov_base = buf;

	while (1)
	{
	  /*
	   * iov_len is modified by recvmsg(), therefore has to be initialized before
	   * using it again
	   */
	  OPENVPN_LOG_RTNL(__func__ << ": checking for received messages");
	  iov.iov_len = sizeof(buf);
	  rcv_len = recvmsg(fd, &nlmsg, 0);
	  OPENVPN_LOG_RTNL(__func__ << ": rtnl: received " << rcv_len << " bytes");
	  if (rcv_len < 0)
	  {
	    if ((errno == EINTR) || (errno == EAGAIN))
	    {
	      OPENVPN_LOG(__func__ << ": interrupted call");
	      continue;
	    }
	    OPENVPN_LOG(__func__ << ": rtnl: error on recvmsg()");
	    ret = -errno;
	    goto out;
	  }

	  if (rcv_len == 0)
	  {
	    OPENVPN_LOG(__func__ << ": rtnl: socket reached unexpected EOF");
	    ret = -EIO;
	    goto out;
	  }

	  if (nlmsg.msg_namelen != sizeof(nladdr))
	  {
	    OPENVPN_LOG(__func__ << ": sender address length: "
			<< nlmsg.msg_namelen << " (expected " << sizeof(nladdr)
			<< ")");
	    ret = -EIO;
	    goto out;
	  }

	  h = (struct nlmsghdr *)buf;
	  while (rcv_len >= (int)sizeof(*h))
	  {
	    len = h->nlmsg_len;
	    rem_len = len - sizeof(*h);

	    if ((rem_len < 0) || (len > rcv_len))
	    {
	      if (nlmsg.msg_flags & MSG_TRUNC)
	      {
		OPENVPN_LOG(__func__ << ": truncated message");
		ret = -EIO;
		goto out;
	      }
	      OPENVPN_LOG(__func__ << ": malformed message: len=" << len);
	      ret = -EIO;
	      goto out;
	    }

	    if (h->nlmsg_type == NLMSG_DONE)
	    {
	      goto out;
	    }

	    if (h->nlmsg_type == NLMSG_ERROR)
	    {
	      err = (struct nlmsgerr *)NLMSG_DATA(h);
	      if (rem_len < (int)sizeof(struct nlmsgerr))
	      {
		OPENVPN_LOG(__func__ << ": ERROR truncated");
		ret = -EIO;
	      }
	      else
	      {
		if (!err->error)
		{
		  ret = 0;
		  if (cb)
		  {
		    ret = cb(h, arg_cb);
		    if (ret < 0)
		      goto out;
		  }
		}
		else
		{
		  OPENVPN_LOG(__func__ << ": rtnl: generic error: "
			      << strerror(-err->error)
			      << " (" << err->error << ")");
		  ret = err->error;
		}
	      }
	      goto out;
	    }

	    if (cb)
	    {
	      ret = cb(h, arg_cb);
	    }
	    else
	    {
	      OPENVPN_LOG(__func__ << ": RTNL: unexpected reply");
	    }

	    rcv_len -= NLMSG_ALIGN(len);
	    h = (struct nlmsghdr *)((char *)h + NLMSG_ALIGN(len));
	  }

	  if (nlmsg.msg_flags & MSG_TRUNC)
	  {
	    OPENVPN_LOG(__func__ << ": message truncated");
	    continue;
	  }

	  if (rcv_len)
	  {
	    OPENVPN_LOG(__func__ << ": rtnl: " << rcv_len
			<< " not parsed bytes");
	    ret = -1;
	    goto out;
	  }

	  // continue reading multipart message
	  if (!(h->nlmsg_flags & NLM_F_MULTI))
	    goto out;
	}
out:
	close(fd);

	return ret;
      }

      /* store the route entry resulting from the query */
      typedef struct
      {
	sa_family_t family;
	IP::Addr gw;
	std::string iface;
	std::string iface_to_ignore;
	int metric;
	IP::Route dst;
	int prefix_len;
      } route_res_t;

      static int
      sitnl_route_save(struct nlmsghdr *n, void *arg)
      {
	route_res_t *res = (route_res_t *)arg;
	struct rtmsg *r = (struct rtmsg *)NLMSG_DATA(n);
	struct rtattr *rta = RTM_RTA(r);
	int len = n->nlmsg_len - NLMSG_LENGTH(sizeof(*r));
	int ifindex = 0;
	int metric = 0;

	IP::Addr gw;

	IP::Route route;
	switch (res->family)
	{
	  case AF_INET:
	    route = IP::Route("0.0.0.0/0");
	    break;
	  case AF_INET6:
	    route = IP::Route("::/0");
	    break;
	}

	while (RTA_OK(rta, len))
	{
	  switch (rta->rta_type)
	  {
	  case RTA_OIF:
	    /* route interface */
	    ifindex = *(unsigned int *)RTA_DATA(rta);
	    break;
	  case RTA_DST:
	    /* route prefix */
	    {
	      const unsigned char *bytestr = (unsigned char *)RTA_DATA(rta);
	      switch (res->family)
	      {
		case AF_INET:
		  route = IP::Route(IPv4::Addr::from_bytes_net(bytestr).to_string() + "/" + std::to_string(r->rtm_dst_len));
		  break;
		case AF_INET6:
		  route = IP::Route(IPv6::Addr::from_byte_string(bytestr).to_string() + "/" + std::to_string(r->rtm_dst_len));
		  break;
	      }
	    }
	    break;
	  case RTA_PRIORITY:
	    metric = *(unsigned int *)RTA_DATA(rta);
	    break;
	  case RTA_GATEWAY:
	    /* GW for the route */
	    {
	      const unsigned char *bytestr = (unsigned char *)RTA_DATA(rta);
	      switch (res->family)
	      {
	      case AF_INET:
		gw = IP::Addr::from_ipv4(IPv4::Addr::from_bytes_net(bytestr));
		break;
	      case AF_INET6:
		gw = IP::Addr::from_ipv6(IPv6::Addr::from_byte_string(bytestr));
		break;
	      }
	    }
	    break;
	  }

	  rta = RTA_NEXT(rta, len);
	}

	if (!gw.defined() || ifindex <= 0)
	{
	  return 0;
	}
	else
	{
	  OPENVPN_LOG_RTNL(__func__ << ": RTA_GATEWAY " << gw.to_string());
	}

	if (!route.contains(res->dst))
	{
	  OPENVPN_LOG_RTNL(__func__ << ": Ignore gw for unmatched route " << route.to_string());
	  return 0;
	}

	char iface[IFNAMSIZ];
	if (!if_indextoname(ifindex, iface))
	{
	  OPENVPN_LOG(__func__ << ": rtnl: can't get ifname for index "
		      << ifindex);
	  return -1;
	}

	if (res->iface_to_ignore == iface)
	{
	  OPENVPN_LOG_RTNL(__func__ << ": Ignore gw " << gw.to_string() << " on " << iface);
	  return 0;
	}

	// skip if gw's route prefix is shorter
	if (r->rtm_dst_len < res->prefix_len)
	{
	  OPENVPN_LOG_RTNL(__func__ << ": Ignore gw " << gw.to_string() << " with shorter route prefix " << route.to_string());
	  return 0;
	}

	// skip if gw's route metric is higher
	if ((metric > res->metric) && (res->metric != -1))
	{
	  OPENVPN_LOG_RTNL(__func__ << ": Ignore gw " << gw.to_string() << " with higher metrics " << metric);
	  return 0;
	}

	res->iface = iface;
	res->gw = gw;
	res->metric = metric;
	res->prefix_len = res->prefix_len;

	OPENVPN_LOG_RTNL(__func__ << ": Use gw " << gw.to_string() << " route " << route.to_string() << " metric " << metric);

	return 0;
      }

      /**
       * Searches for best gateway for a given route
       * @param iface_to_ignore this allows to exclude certain interface
       * from discovered gateways. Used when we want to exclude VPN interface
       * when there is active VPN connection with redirected default gateway
       * @param route route for which we search gw
       * @param [out] best_gw found gw
       * @param [out] best_iface network interface on which gw was found
       * @return
       */
      static int
      sitnl_route_best_gw(const std::string& iface_to_ignore,
			  const IP::Route& route,
			  IP::Addr& best_gw,
			  std::string& best_iface)
      {
	struct sitnl_route_req req = { };
	req.n.nlmsg_len = NLMSG_LENGTH(sizeof(req.r));
	req.n.nlmsg_type = RTM_GETROUTE;
	req.n.nlmsg_flags = NLM_F_REQUEST;

	route_res_t res;
	res.metric = -1;
	res.prefix_len = -1;

	int ret = -EINVAL;

	res.family = req.r.rtm_family = route.addr.family();
	req.r.rtm_dst_len = route.prefix_len;

	if (route.addr.family() == AF_INET)
	{
	  req.n.nlmsg_flags |= NLM_F_DUMP;
	}

	res.iface_to_ignore = iface_to_ignore;
	res.dst = route;

	{
	  unsigned char bytestr[IP::Addr::V6_SIZE / 8];
	  route.addr.to_byte_string_variable(bytestr);

	  SITNL_ADDATTR(&req.n, sizeof(req), RTA_DST, bytestr,
			route.addr.size_bytes());
	}

	ret = sitnl_send(&req.n, 0, 0, sitnl_route_save, &res);
	if (ret >= 0)
	{
	  /* save result in output variables */
	  best_gw = std::move(res.gw);
	  best_iface = std::move(res.iface);

	  OPENVPN_LOG(__func__ << " result: via " << best_gw << " dev " << best_iface);
	}
	else
	{
	  OPENVPN_LOG(__func__ << ": failed to retrieve route, err=" << ret);
	}

err:
	return ret;
      }

      static int
      sitnl_addr_set(const int cmd, const uint32_t flags, const std::string& iface,
		     const IP::Addr& local, const IP::Addr& remote, int prefixlen,
		     const IP::Addr& broadcast)
      {
	struct sitnl_addr_req req = { };
	int ret = -EINVAL;

	if (iface.empty())
	{
	  OPENVPN_LOG(__func__ << ": passed empty interface");
	  return -EINVAL;
	}

	if (local.unspecified())
	{
	  OPENVPN_LOG(__func__ << ": passed zero IP address");
	  return -EINVAL;
	}

	req.n.nlmsg_len = NLMSG_LENGTH(sizeof(req.i));
	req.n.nlmsg_type = cmd;
	req.n.nlmsg_flags = NLM_F_REQUEST | flags;

	req.i.ifa_family = local.family();
	req.i.ifa_index = if_nametoindex(iface.c_str());
	if (req.i.ifa_index == 0)
	{
	  OPENVPN_LOG(__func__ << ": cannot get ifindex for " << iface << " "
		      << strerror(errno));
	  return -ENOENT;
	}

	/* if no prefixlen has been specified, assume host address */
	if (prefixlen == 0)
	{
	  prefixlen = local.size();
	}
	req.i.ifa_prefixlen = prefixlen;

	{
	  unsigned char bytestr[IP::Addr::V6_SIZE / 8];

	  local.to_byte_string_variable(bytestr);
	  SITNL_ADDATTR(&req.n, sizeof(req), IFA_LOCAL, bytestr, local.size_bytes());

	  if (remote.specified())
	  {
	    remote.to_byte_string_variable(bytestr);
	    SITNL_ADDATTR(&req.n, sizeof(req), IFA_ADDRESS, bytestr, remote.size_bytes());
	  }

	  if (broadcast.specified())
	  {
	    broadcast.to_byte_string_variable(bytestr);
	    SITNL_ADDATTR(&req.n, sizeof(req), IFA_BROADCAST, bytestr, broadcast.size_bytes());
	  }
	}

	ret = sitnl_send(&req.n, 0, 0, NULL, NULL);
	if ((ret < 0) && (errno == EEXIST))
	{
	  ret = 0;
	}

err:
	return ret;
      }

      static int
      sitnl_addr_ptp_add(const std::string& iface, const IP::Addr& local,
			 const IP::Addr& remote)
      {
	return sitnl_addr_set(RTM_NEWADDR, NLM_F_CREATE | NLM_F_REPLACE, iface,
			      local, remote, 0,
			      IP::Addr::from_zero(local.version()));
      }

      static int
      sitnl_addr_ptp_del(const std::string& iface, const IP::Addr& local)
      {
	return sitnl_addr_set(RTM_DELADDR, 0, iface, local,
			      IP::Addr::from_zero(local.version()),
			      0, IP::Addr::from_zero(local.version()));
      }

      static int
      sitnl_route_set(const int cmd, const uint32_t flags,
		      const std::string& iface, const IP::Route& route,
		      const IP::Addr& gw, const enum rt_class_t table,
		      const int metric, const enum rt_scope_t scope,
		      const int protocol, const int type)
      {
	struct sitnl_route_req req = { };
	int ret = -1;

	req.n.nlmsg_len = NLMSG_LENGTH(sizeof(req.r));
	req.n.nlmsg_type = cmd;
	req.n.nlmsg_flags = NLM_F_REQUEST | flags;

	req.r.rtm_family = route.addr.family();
	req.r.rtm_scope = scope;
	req.r.rtm_protocol = protocol;
	req.r.rtm_type = type;
	req.r.rtm_dst_len = route.prefix_len;

	if (table < 256)
	{
	  req.r.rtm_table = table;
	}
	else
	{
	  req.r.rtm_table = RT_TABLE_UNSPEC;
	  SITNL_ADDATTR(&req.n, sizeof(req), RTA_TABLE, &table, 4);
	}

	{
	  unsigned char bytestr[IP::Addr::V6_SIZE / 8];

	  route.addr.to_byte_string_variable(bytestr);
	  SITNL_ADDATTR(&req.n, sizeof(req), RTA_DST, bytestr, route.addr.size_bytes());

	  if (gw.specified())
	  {
	    gw.to_byte_string_variable(bytestr);
	    SITNL_ADDATTR(&req.n, sizeof(req), RTA_GATEWAY, bytestr, gw.size_bytes());
	  }
	}

	if (!iface.empty())
	{
	  int ifindex = if_nametoindex(iface.c_str());
	  if (ifindex == 0)
	  {
	    OPENVPN_LOG(__func__ << ": rtnl: cannot get ifindex for " << iface);
	    return -ENOENT;
	  }

	  SITNL_ADDATTR(&req.n, sizeof(req), RTA_OIF, &ifindex, 4);
	}

	if (metric > 0)
	{
	  SITNL_ADDATTR(&req.n, sizeof(req), RTA_PRIORITY, &metric, 4);
	}

	ret = sitnl_send(&req.n, 0, 0, NULL, NULL);
	if ((ret < 0) && (errno == EEXIST))
	{
	  ret = 0;
	}

err:
	return ret;
      }

      static int
      sitnl_addr_add(const std::string& iface, const IP::Addr& addr,
		     int prefixlen, const IP::Addr& broadcast)
      {
	return sitnl_addr_set(RTM_NEWADDR, NLM_F_CREATE | NLM_F_REPLACE, iface,
			      addr, IP::Addr::from_zero(addr.version()),
			      prefixlen, broadcast);
      }

      static int
      sitnl_addr_del(const std::string& iface, const IP::Addr& addr, int prefixlen)
      {
	return sitnl_addr_set(RTM_DELADDR, 0, iface, addr,
			      IP::Addr::from_zero(addr.version()), prefixlen,
			      IP::Addr::from_zero(addr.version()));
      }

      static int
      sitnl_route_add(const IP::Route& route, const IP::Addr& gw,
		      const std::string& iface, const uint32_t table,
		      const int metric)
      {
	return sitnl_route_set(RTM_NEWROUTE, NLM_F_CREATE, iface,
			       route, gw,
			       (enum rt_class_t)(!table ? RT_TABLE_MAIN : table),
			       metric, RT_SCOPE_UNIVERSE, RTPROT_BOOT, RTN_UNICAST);
      }

      static int
      sitnl_route_del(const IP::Route& route, const IP::Addr& gw,
		      const std::string& iface, const uint32_t table,
		      const int metric)
      {
	return sitnl_route_set(RTM_DELROUTE, 0, iface, route, gw,
			       (enum rt_class_t)(!table ? RT_TABLE_MAIN : table),
			       metric, RT_SCOPE_NOWHERE,
			       0, 0);
      }

    public:

      static int
      net_route_best_gw(const IP::Route6& route, IPv6::Addr& best_gw6,
			std::string& best_iface, const std::string& iface_to_ignore = "")
      {
	IP::Addr best_gw;
	int ret;

	OPENVPN_LOG(__func__ << " query IPv6: " << route);

	ret = sitnl_route_best_gw(iface_to_ignore, IP::Route(IP::Addr::from_ipv6(route.addr), route.prefix_len),
				  best_gw, best_iface);
	if (ret >= 0)
	{
	  best_gw6 = best_gw.to_ipv6();
	}

	return ret;
      }

      static int
      net_route_best_gw(const IP::Route4& route, IPv4::Addr &best_gw4,
			std::string& best_iface, const std::string& iface_to_ignore = "")
      {
	IP::Addr best_gw;
	int ret;

	OPENVPN_LOG(__func__ << " query IPv4: " << route);

	ret = sitnl_route_best_gw(iface_to_ignore, IP::Route(IP::Addr::from_ipv4(route.addr), route.prefix_len),
				  best_gw, best_iface);
	if (ret >= 0)
	{
	  best_gw4 = best_gw.to_ipv4();
	}

	return ret;
      }

      /**
       * @brief Add new interface (similar to ip link add)
       *
       * @param iface interface name
       * @param type interface link type (for example "ovpn-dco")
       * @return int 0 on success, negative error code on error
       */
      static int
      net_iface_new(const std::string& iface, const std::string& type)
      {
	struct sitnl_link_req req = { };
	struct rtattr *tail = NULL;
	int ret = -1;

	if (iface.empty())
	{
	  OPENVPN_LOG(__func__ << ": passed empty interface");
	  return -EINVAL;
	}

	req.n.nlmsg_len = NLMSG_LENGTH(sizeof(req.i));
	req.n.nlmsg_flags = NLM_F_REQUEST | NLM_F_CREATE | NLM_F_EXCL;
	req.n.nlmsg_type = RTM_NEWLINK;

	SITNL_ADDATTR(&req.n, sizeof(req), IFLA_IFNAME, iface.c_str(),
		      iface.length() + 1);
	tail = NLMSG_TAIL(&req.n);
	SITNL_ADDATTR(&req.n, sizeof(req), IFLA_LINKINFO, NULL, 0);
	SITNL_ADDATTR(&req.n, sizeof(req), IFLA_INFO_KIND, type.c_str(),
		      type.length() + 1);
	tail->rta_len = (uint8_t *)NLMSG_TAIL(&req.n) - (uint8_t *)tail;

	req.i.ifi_family = AF_PACKET;
	req.i.ifi_index = 0;

	OPENVPN_LOG(__func__ << ": add " << iface << " type " << type);

	ret = sitnl_send(&req.n, 0, 0, NULL, NULL);
err:
	return ret;
      }

      static int
      net_iface_del(const std::string& iface)
      {
	struct sitnl_link_req req = { };
	int ifindex;

	if (iface.empty())
	{
	  OPENVPN_LOG(__func__ << ": passed empty interface");
	  return -EINVAL;
	}

	ifindex = if_nametoindex(iface.c_str());
	if (ifindex == 0)
	{
	  OPENVPN_LOG(__func__ << ": rtnl: cannot get ifindex for " << iface
		      << ": " << strerror(errno));
	  return -ENOENT;
	}

	req.n.nlmsg_len = NLMSG_LENGTH(sizeof(req.i));
	req.n.nlmsg_flags = NLM_F_REQUEST;
	req.n.nlmsg_type = RTM_DELLINK;

	req.i.ifi_family = AF_PACKET;
	req.i.ifi_index = ifindex;

	OPENVPN_LOG(__func__ << ": idel " << iface);

	return sitnl_send(&req.n, 0, 0, NULL, NULL);
      }

      static int
      net_iface_up(std::string& iface, bool up)
      {
	struct sitnl_link_req req = { };
	int ifindex;

	if (iface.empty())
	{
	  OPENVPN_LOG(__func__ << ": passed empty interface");
	  return -EINVAL;
	}

	ifindex = if_nametoindex(iface.c_str());
	if (ifindex == 0)
	{
	  OPENVPN_LOG(__func__ << ": rtnl: cannot get ifindex for " << iface
		      << ": " << strerror(errno));
	  return -ENOENT;
	}

	req.n.nlmsg_len = NLMSG_LENGTH(sizeof(req.i));
	req.n.nlmsg_flags = NLM_F_REQUEST;
	req.n.nlmsg_type = RTM_NEWLINK;

	req.i.ifi_family = AF_PACKET;
	req.i.ifi_index = ifindex;
	req.i.ifi_change |= IFF_UP;
	if (up)
	{
	  req.i.ifi_flags |= IFF_UP;
	}
	else
	{
	  req.i.ifi_flags &= ~IFF_UP;
	}

	OPENVPN_LOG(__func__ << ": set " << iface << " " << (up ? "up" : "down"));

	return sitnl_send(&req.n, 0, 0, NULL, NULL);
      }

      static int
      net_iface_mtu_set(std::string& iface, uint32_t mtu)
      {
	struct sitnl_link_req req = { };
	int ifindex;

	if (iface.empty())
	{
	  OPENVPN_LOG(__func__ << ": passed empty interface");
	  return -EINVAL;
	}

	ifindex = if_nametoindex(iface.c_str());
	if (ifindex == 0)
	{
	  OPENVPN_LOG(__func__ << ": rtnl: cannot get ifindex for " << iface);
	  return -1;
	}

	req.n.nlmsg_len = NLMSG_LENGTH(sizeof(req.i));
	req.n.nlmsg_flags = NLM_F_REQUEST;
	req.n.nlmsg_type = RTM_NEWLINK;

	req.i.ifi_family = AF_PACKET;
	req.i.ifi_index = ifindex;

	SITNL_ADDATTR(&req.n, sizeof(req), IFLA_MTU, &mtu, 4);

	OPENVPN_LOG(__func__ << ": mtu " << mtu << " for " << iface);

err:
	return sitnl_send(&req.n, 0, 0, NULL, NULL);
      }

      static int
      net_addr_add(const std::string& iface, const IPv4::Addr& addr,
		   const int prefixlen, const IPv4::Addr& broadcast)
      {
	OPENVPN_LOG(__func__ << ": " << addr << "/" << prefixlen << " brd "
		    << broadcast << " dev " << iface);

	return sitnl_addr_add(iface, IP::Addr::from_ipv4(addr), prefixlen,
			      IP::Addr::from_ipv4(broadcast));
      }

      static int
      net_addr_add(const std::string& iface, const IPv6::Addr& addr,
		   const int prefixlen)
      {
	OPENVPN_LOG(__func__ << ": " << addr << "/" << prefixlen << " dev " << iface);

	return sitnl_addr_add(iface, IP::Addr::from_ipv6(addr), prefixlen,
			      IP::Addr::from_zero(IP::Addr::V6));
      }

      static int
      net_addr_del(const std::string& iface, const IPv4::Addr& addr,
		   const int prefixlen)
      {
	OPENVPN_LOG(__func__ << ": " << addr << "/" << prefixlen << " dev " << iface);

	return sitnl_addr_del(iface, IP::Addr::from_ipv4(addr), prefixlen);
      }

      static int
      net_addr_del(const std::string& iface, const IPv6::Addr& addr,
		   const int prefixlen)
      {
	OPENVPN_LOG(__func__ << ": " << addr << "/" << prefixlen << " dev " << iface);

	return sitnl_addr_del(iface, IP::Addr::from_ipv6(addr), prefixlen);
      }

      static int
      net_addr_ptp_add(const std::string& iface, const IPv4::Addr& local,
		       const IPv4::Addr& remote)
      {
	OPENVPN_LOG(__func__ << ": " << local << " peer " << remote << " dev " << iface);

	return sitnl_addr_ptp_add(iface, IP::Addr::from_ipv4(local),
				  IP::Addr::from_ipv4(remote));
      }

      static int
      net_addr_ptp_del(const std::string& iface, const IPv4::Addr& local,
		       const IPv4::Addr& remote)
      {
	OPENVPN_LOG(__func__ << ": " << local << " dev " << iface);

	return sitnl_addr_ptp_del(iface, IP::Addr::from_ipv4(local));
      }

      static int
      net_route_add(const IP::Route4& route, const IPv4::Addr& gw,
		    const std::string& iface, const uint32_t table,
		    const int metric)
      {
	OPENVPN_LOG(__func__ << ": " << route << " via " << gw << " dev " << iface
		    << " table " << table << " metric " << metric);

	return sitnl_route_add(IP::Route(IP::Addr::from_ipv4(route.addr), route.prefix_len),
			       IP::Addr::from_ipv4(gw), iface, table, metric);
      }

      static int
      net_route_add(const IP::Route6& route, const IPv6::Addr& gw,
		    const std::string& iface, const uint32_t table,
		    const int metric)
      {
	OPENVPN_LOG(__func__ << ": " << route << " via " << gw << " dev " << iface
		    << " table " << table << " metric " << metric);

	return sitnl_route_add(IP::Route(IP::Addr::from_ipv6(route.addr), route.prefix_len),
			       IP::Addr::from_ipv6(gw), iface, table, metric);
      }

      static int
      net_route_del(const IP::Route4& route, const IPv4::Addr& gw,
		    const std::string& iface, const uint32_t table,
		    const int metric)
      {
	OPENVPN_LOG(__func__ << ": " << route << " via " << gw << " dev " << iface
		    << " table " << table << " metric " << metric);

	return sitnl_route_del(IP::Route(IP::Addr::from_ipv4(route.addr), route.prefix_len),
			       IP::Addr::from_ipv4(gw), iface, table, metric);
      }

      static int
      net_route_del(const IP::Route6& route, const IPv6::Addr& gw,
		    const std::string& iface, const uint32_t table,
		    const int metric)
      {
	OPENVPN_LOG(__func__ << ": " << route << " via " << gw << " dev " << iface
		    << " table " << table << " metric " << metric);

	return sitnl_route_del(IP::Route(IP::Addr::from_ipv6(route.addr), route.prefix_len),
			       IP::Addr::from_ipv6(gw), iface, table, metric);
      }
    };
  }
}
