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

#pragma once

#include <openvpn/common/numeric_util.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/ip/ipcommon.hpp>
#include <openvpn/ip/ip4.hpp>
#include <openvpn/ip/ip6.hpp>
#include <openvpn/ip/tcp.hpp>

#if OPENVPN_DEBUG_PROTO >= 2
#define OPENVPN_LOG_MSSFIX(x) OPENVPN_LOG(x)
#else
#define OPENVPN_LOG_MSSFIX(x)
#endif

namespace openvpn {
class MSSFix
{
  public:
    static void mssfix(BufferAllocated &buf, uint16_t mss_fix)
    {
        if (buf.empty())
            return;

        switch (IPCommon::version(buf[0]))
        {
        case IPCommon::IPv4:
            {
                if (buf.length() <= sizeof(struct IPv4Header))
                    break;

                const IPv4Header *iphdr = (const IPv4Header *)buf.c_data();

                auto ipv4hlen = IPv4Header::length(iphdr->version_len);

                if (iphdr->protocol == IPCommon::TCP
                    && ntohs(iphdr->tot_len) == buf.length()
                    && (ntohs(iphdr->frag_off) & IPv4Header::OFFMASK) == 0
                    && ipv4hlen <= buf.length()
                    && buf.length() - ipv4hlen >= sizeof(struct TCPHeader))
                {
                    TCPHeader *tcphdr = reinterpret_cast<TCPHeader *>(buf.data() + ipv4hlen);
                    auto ip_payload_len = buf.length() - ipv4hlen;

                    do_mssfix(tcphdr, mss_fix, ip_payload_len);
                }
            }
            break;

        case IPCommon::IPv6:
            {
                if (buf.length() <= sizeof(struct IPv6Header))
                    break;

                const IPv6Header *iphdr = (const IPv6Header *)buf.c_data();

                if (buf.length() != ntohs(iphdr->payload_len) + sizeof(struct IPv6Header))
                    break;

                /* follow header chain until we reach final header, then check for TCP
                 *
                 * An IPv6 packet could, theoretically, have a chain of multiple headers
                 * before the final header (TCP, UDP, ...), so we'd need to walk that
                 * chain (see RFC 2460 and RFC 6564 for details).
                 *
                 * In practice, "most typically used" extention headers (AH, routing,
                 * fragment, mobility) are very unlikely to be seen inside an OpenVPN
                 * tun, so for now, we only handle the case of "single next header = TCP"
                 */
                if (iphdr->nexthdr != IPCommon::TCP)
                    break;

                /* skip IPv6 header (40 bytes),
                 * verify remainder is large enough to contain a full TCP header
                 */
                auto payload_len = buf.length() - sizeof(struct IPv6Header);
                if (payload_len >= sizeof(struct TCPHeader))
                {
                    TCPHeader *tcphdr = reinterpret_cast<TCPHeader *>(buf.data() + sizeof(struct IPv6Header));
                    // mssfix is calculated for IPv4, and since IPv6 header is 20 bytes larger we need to account for it
                    do_mssfix(tcphdr, mss_fix - 20, payload_len);
                }
            }
            break;
        }
    }

  private:
    static void do_mssfix(TCPHeader *tcphdr, uint16_t max_mss, size_t ip_payload_len)
    {
        if ((tcphdr->flags & TCPHeader::FLAG_SYN) == 0)
            return;

        auto tcphlen = TCPHeader::length(tcphdr->doff_res);
        if (tcphlen <= (int)sizeof(struct TCPHeader) || tcphlen > ip_payload_len)
            return;

        size_t olen, optlen; // length of options field and Option-Length
        uint8_t *opt;        // option type

        for (olen = tcphlen - sizeof(struct TCPHeader), opt = (uint8_t *)(tcphdr + 1);
             olen > 1;
             olen -= optlen, opt += optlen)
        {
            if (*opt == TCPHeader::OPT_EOL)
                break;
            else if (*opt == TCPHeader::OPT_NOP)
                optlen = 1;
            else
            {
                optlen = *(opt + 1);
                if (optlen <= 0 || optlen > olen)
                    break;
                if ((*opt == TCPHeader::OPT_MAXSEG) && (optlen == TCPHeader::OPTLEN_MAXSEG))
                {
                    auto mssRaw = (opt[2] << 8) + opt[3];
                    if (is_safe_conversion<uint16_t>(mssRaw))
                    {
                        uint16_t mssval = static_cast<uint16_t>(mssRaw);
                        if (mssval > max_mss)
                        {
                            OPENVPN_LOG_MSSFIX("MTU MSS " << mssval << " -> " << max_mss);
                            int accumulate = htons(mssval);
                            opt[2] = static_cast<uint8_t>((max_mss >> 8) & 0xff);
                            opt[3] = static_cast<uint8_t>(max_mss & 0xff);
                            accumulate -= htons(max_mss);
                            tcp_adjust_checksum(accumulate, tcphdr->check);
                        }
                    }
                    else
                    {
                        OPENVPN_LOG_MSSFIX("Rejecting MSS fix: value out of bounds for type " << ((opt[2] << 8) + opt[3]));
                        break;
                    }
                }
            }
        }
    }
};
} // namespace openvpn
