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

#pragma once

#include <string>
#include <cstring>
#include <utility>

#include <openvpn/common/numeric_cast.hpp>
#include <openvpn/common/size.hpp>
#include <openvpn/common/socktypes.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/addr/ipv6.hpp>
#include <openvpn/ip/ipcommon.hpp>
#include <openvpn/ip/icmp6.hpp>
#include <openvpn/ip/csum.hpp>

using namespace openvpn::numeric_util;

namespace openvpn::Ping6 {

inline static const std::uint16_t *get_addr16(const struct in6_addr *addr)
{
#if defined(_WIN32)
    return addr->u.Word;
#elif defined(__APPLE__)
    return addr->__u6_addr.__u6_addr16;
#else
    return addr->s6_addr16;
#endif
}

inline std::uint16_t csum_ipv6_pseudo(const struct in6_addr *saddr,
                                      const struct in6_addr *daddr,
                                      const std::uint32_t len,
                                      const std::uint16_t proto,
                                      std::uint32_t sum)
{
    int carry = 0;
    std::uint32_t val = 0;

    const std::uint16_t *addr = get_addr16(saddr);
    for (int i = 0; i < 4; ++i)
    {
        val = (std::uint32_t)(addr[i * 2] << 16) + addr[i * 2 + 1];
        sum += val;
        carry = (sum < val);
        sum += carry;
    }

    addr = get_addr16(daddr);
    for (int i = 0; i < 4; ++i)
    {
        val = (std::uint32_t)(addr[i * 2] << 16) + addr[i * 2 + 1];
        sum += val;
        carry = (sum < val);
        sum += carry;
    }

    const std::uint32_t ulen = (std::uint32_t)htonl((std::uint32_t)len);
    sum += ulen;
    carry = (sum < ulen);
    sum += carry;

    const std::uint32_t uproto = (std::uint32_t)htonl(proto);
    sum += uproto;
    carry = (sum < uproto);
    sum += carry;

    return IPChecksum::cfold(sum);
}

// len must be >= sizeof(ICMPv6)
inline std::uint16_t csum_icmp(const ICMPv6 *icmp, const size_t len)
{
    if (len < sizeof(IPv6Header))
        throw std::range_error("Argument 'len' too small");

    auto lenArg = numeric_cast<uint32_t>(len - sizeof(IPv6Header));
    return csum_ipv6_pseudo(&icmp->head.saddr,
                            &icmp->head.daddr,
                            lenArg,
                            IPCommon::ICMPv6,
                            IPChecksum::compute((std::uint8_t *)icmp + sizeof(IPv6Header), len - sizeof(IPv6Header)));
}

inline void generate_echo_request(Buffer &buf,
                                  const IPv6::Addr &src,
                                  const IPv6::Addr &dest,
                                  const void *extra_data,
                                  const size_t extra_data_size,
                                  const uint16_t id,
                                  const uint16_t seq_num,
                                  const size_t total_size,
                                  std::string *log_info)
{
    const unsigned int data_size = std::max(int(extra_data_size), int(total_size) - int(sizeof(ICMPv6)));

    if (log_info)
        *log_info = "PING6 " + src.to_string() + " -> " + dest.to_string() + " id=" + std::to_string(id) + " seq_num=" + std::to_string(seq_num) + " data_size=" + std::to_string(data_size);

    std::uint8_t *b = buf.write_alloc(sizeof(ICMPv6) + data_size);
    ICMPv6 *icmp = (ICMPv6 *)b;

    // IP Header
    icmp->head.version_prio = (6 << 4);
    icmp->head.flow_lbl[0] = 0;
    icmp->head.flow_lbl[1] = 0;
    icmp->head.flow_lbl[2] = 0;
    icmp->head.payload_len = htons(numeric_cast<uint16_t>(sizeof(ICMPv6) - sizeof(IPv6Header) + data_size));
    icmp->head.nexthdr = IPCommon::ICMPv6;
    icmp->head.hop_limit = 64;
    icmp->head.saddr = src.to_in6_addr();
    icmp->head.daddr = dest.to_in6_addr();

    // ICMP header
    icmp->type = ICMPv6::ECHO_REQUEST;
    icmp->code = 0;
    icmp->checksum = 0;
    icmp->id = ntohs(id);
    icmp->seq_num = ntohs(seq_num);

    // Data
    std::uint8_t *data = b + sizeof(ICMPv6);
    for (size_t i = 0; i < data_size; ++i)
        data[i] = (std::uint8_t)i;

    // Extra data
    std::memcpy(data, extra_data, extra_data_size);

    // ICMP checksum
    icmp->checksum = csum_icmp(icmp, sizeof(ICMPv6) + data_size);

    // std::cout << dump_hex(buf);
}

// assumes that buf is a validated ECHO_REQUEST
inline void generate_echo_reply(Buffer &buf,
                                std::string *log_info)
{
    if (buf.size() < sizeof(ICMPv6))
    {
        if (log_info)
            *log_info = "Invalid ECHO6_REQUEST";
        return;
    }

    ICMPv6 *icmp = (ICMPv6 *)buf.c_data();
    std::swap(icmp->head.saddr, icmp->head.daddr);
    const std::uint16_t old_type_code = icmp->type_code;
    icmp->type = ICMPv6::ECHO_REPLY;
    icmp->checksum = IPChecksum::cfold(IPChecksum::diff2(old_type_code, icmp->type_code, IPChecksum::cunfold(icmp->checksum)));

    if (log_info)
        *log_info = "ECHO6_REPLY size=" + std::to_string(buf.size()) + ' ' + IPv6::Addr::from_in6_addr(&icmp->head.saddr).to_string() + " -> " + IPv6::Addr::from_in6_addr(&icmp->head.daddr).to_string();
}
} // namespace openvpn::Ping6