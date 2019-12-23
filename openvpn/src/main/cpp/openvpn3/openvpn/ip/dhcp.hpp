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

#ifndef OPENVPN_IP_DHCP_H
#define OPENVPN_IP_DHCP_H

#include <openvpn/ip/eth.hpp>
#include <openvpn/ip/ip4.hpp>
#include <openvpn/ip/udp.hpp>

#pragma pack(push)
#pragma pack(1)

namespace openvpn {
  struct DHCP {
    enum {
      /* DHCP Option types */
      DHCP_PAD      =  0,
      DHCP_NETMASK  =  1,
      DHCP_ROUTER   =  3,
      DHCP_DNS      =  6,
      DHCP_MSG_TYPE =  53  /* message type (u8) */,
      DHCP_END      =  255,

      /* DHCP Messages types */
      DHCPDISCOVER =  1,
      DHCPOFFER    =  2,
      DHCPREQUEST  =  3,
      DHCPDECLINE  =  4,
      DHCPACK      =  5,
      DHCPNAK      =  6,
      DHCPRELEASE  =  7,
      DHCPINFORM   =  8,

      /* DHCP UDP port numbers */
      BOOTPS_PORT = 67,
      BOOTPC_PORT = 68,

      /* DHCP message op */
      BOOTREQUEST = 1,
      BOOTREPLY   = 2,
    };

    std::uint8_t  op;         /* message op */
    std::uint8_t  htype;      /* hardware address type (e.g. '1' = 10Mb Ethernet) */
    std::uint8_t  hlen;       /* hardware address length (e.g. '6' for 10Mb Ethernet) */
    std::uint8_t  hops;       /* client sets to 0, may be used by relay agents */
    std::uint32_t xid;        /* transaction ID, chosen by client */
    std::uint16_t secs;       /* seconds since request process began, set by client */
    std::uint16_t flags;
    std::uint32_t ciaddr;     /* client IP address, client sets if known */
    std::uint32_t yiaddr;     /* 'your' IP address -- server's response to client */
    std::uint32_t siaddr;     /* server IP address */
    std::uint32_t giaddr;     /* relay agent IP address */
    std::uint8_t  chaddr[16]; /* client hardware address */
    std::uint8_t  sname[64];  /* optional server host name */
    std::uint8_t  file[128];  /* boot file name */
    std::uint32_t magic;      /* must be 0x63825363 (network order) */
  };

  struct DHCPPacket {
    EthHeader eth;
    IPv4Header ip;
    UDPHeader udp;
    DHCP dhcp;
    std::uint8_t options[];
  };
}

#pragma pack(pop)

#endif
