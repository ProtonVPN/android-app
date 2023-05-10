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

// Generates ICMP "packet too big" response

#pragma once

#include <openvpn/common/socktypes.hpp>
#include <openvpn/ip/csum.hpp>
#include <openvpn/ip/ip4.hpp>
#include <openvpn/ip/ip6.hpp>
#include <openvpn/ip/icmp4.hpp>
#include <openvpn/ip/icmp6.hpp>
#include <openvpn/ip/ping6.hpp>
#include <openvpn/ip/ipcommon.hpp>
#include <openvpn/buffer/buffer.hpp>

namespace openvpn {
class Ptb
{
  public:
    static void generate_icmp_ptb(BufferAllocated &buf, std::uint16_t nexthop_mtu)
    {
        if (buf.empty())
            return;

        switch (IPCommon::version(buf[0]))
        {
        case IPCommon::IPv4:
            if (buf.length() <= sizeof(struct IPv4Header))
                break;

            generate_icmp4_ptb(buf, nexthop_mtu);
            break;

        case IPCommon::IPv6:
            if (buf.length() <= sizeof(struct IPv6Header))
                break;

            generate_icmp6_ptb(buf, nexthop_mtu);
            break;
        }
    }

  private:
    static void generate_icmp6_ptb(BufferAllocated &buf, std::uint16_t nexthop_mtu)
    {
        // ICMPv6 data includes original IPv6 header and as many bytes of payload as possible
        int data_size = std::min(buf.length(), (size_t)(nexthop_mtu - sizeof(ICMPv6)));

        // sanity check
        // we use headroom for adding IPv6 + ICMPv6 headers
        if ((buf.offset() < sizeof(ICMPv6)) || (buf.capacity() < (sizeof(ICMPv6) + data_size)))
            return;

        IPv6Header *ipv6 = (IPv6Header *)buf.c_data();

        uint8_t *b = buf.prepend_alloc(sizeof(ICMPv6));
        ICMPv6 *icmp = (ICMPv6 *)b;

        // IPv6 header
        icmp->head.version_prio = (6 << 4);
        icmp->head.flow_lbl[0] = 0;
        icmp->head.flow_lbl[1] = 0;
        icmp->head.flow_lbl[2] = 0;
        icmp->head.payload_len = htons(sizeof(ICMPv6) - sizeof(IPv6Header) + data_size);
        icmp->head.nexthdr = IPCommon::ICMPv6;
        icmp->head.hop_limit = 64;
        icmp->head.saddr = ipv6->daddr;
        icmp->head.daddr = ipv6->saddr;

        // ICMP header
        icmp->type = ICMPv6::PACKET_TOO_BIG;
        icmp->code = 0;
        icmp->mtu = htonl(nexthop_mtu);
        icmp->checksum = 0;
        icmp->checksum = Ping6::csum_icmp(icmp, sizeof(ICMPv6) + data_size);

        buf.set_size(sizeof(ICMPv6) + data_size);
    }

    static void generate_icmp4_ptb(BufferAllocated &buf, std::uint16_t nexthop_mtu)
    {
        // ICMP data includes original IP header and first 8 bytes of payload
        int data_size = sizeof(IPv4Header) + ICMPv4::MIN_DATA_SIZE;

        // sanity check
        // we use headroom for adding IPv4 + ICMPv4 headers
        if ((buf.offset() < sizeof(ICMPv4)) || (buf.capacity() < (sizeof(ICMPv4) + data_size)))
            return;

        IPv4Header *ipv4 = (IPv4Header *)buf.c_data();

        uint8_t *b = buf.prepend_alloc(sizeof(ICMPv4));
        ICMPv4 *icmp = (ICMPv4 *)b;

        icmp->head.saddr = ipv4->daddr;
        icmp->head.daddr = ipv4->saddr;
        icmp->head.version_len = IPv4Header::ver_len(IPCommon::IPv4, sizeof(IPv4Header));
        icmp->head.tos = 0;
        icmp->head.tot_len = htons(sizeof(ICMPv4) + data_size);
        icmp->head.id = 0;
        icmp->head.frag_off = 0;
        icmp->head.ttl = 64;
        icmp->head.protocol = IPCommon::ICMPv4;
        icmp->head.check = 0;
        icmp->head.check = IPChecksum::checksum(b, sizeof(IPv4Header));

        icmp->type = ICMPv4::DEST_UNREACH;
        icmp->code = ICMPv4::FRAG_NEEDED;
        icmp->unused = 0;
        icmp->nexthop_mtu = htons(nexthop_mtu);
        icmp->checksum = 0;
        icmp->checksum = IPChecksum::checksum(b + sizeof(IPv4Header), sizeof(ICMPv4) - sizeof(IPv4Header) + data_size);

        buf.set_size(sizeof(ICMPv4) + data_size);
    }
};
} // namespace openvpn
