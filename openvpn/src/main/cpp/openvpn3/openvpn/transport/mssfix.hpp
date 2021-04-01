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

#pragma once

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
  class MSSFix {
  public:
    static void mssfix(BufferAllocated& buf, int mss_inter)
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

	  if (iphdr->protocol == IPCommon::TCP &&
	      ntohs(iphdr->tot_len) == buf.length() &&
	      (ntohs(iphdr->frag_off) & IPv4Header::OFFMASK) == 0 &&
	      ipv4hlen <= buf.length() &&
	      buf.length() - ipv4hlen >= sizeof(struct TCPHeader))
	    {
	      TCPHeader* tcphdr = (TCPHeader*)(buf.data() + ipv4hlen);
	      int ip_payload_len = buf.length() - ipv4hlen;

	      do_mssfix(tcphdr, mss_inter - (sizeof(struct IPv4Header) + sizeof(struct TCPHeader)), ip_payload_len);
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
	  int payload_len = buf.length() - sizeof(struct IPv6Header);
	  if (payload_len >= (int) sizeof(struct TCPHeader))
	    {
	      TCPHeader *tcphdr = (TCPHeader *)(buf.data() + sizeof(struct IPv6Header));
	      do_mssfix(tcphdr, mss_inter - (sizeof(struct IPv6Header) + sizeof(struct TCPHeader)),
			payload_len);
	    }
	}
	break;
      }
    }

  private:
    static void do_mssfix(TCPHeader *tcphdr, int max_mss, int ip_payload_len)
    {
      if ((tcphdr->flags & TCPHeader::FLAG_SYN) == 0)
	return;

      int tcphlen = TCPHeader::length(tcphdr->doff_res);
      if (tcphlen <= (int) sizeof(struct TCPHeader) || tcphlen > ip_payload_len)
	return;

      int olen, optlen; // length of options field and Option-Length
      uint8_t *opt; // option type

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
		uint16_t mssval = (opt[2] << 8) + opt[3];
		if (mssval > max_mss)
		  {
		    OPENVPN_LOG_MSSFIX("MTU MSS " << mssval << " -> " << max_mss);
		    int accumulate = htons(mssval);
		    opt[2] = (max_mss >> 8) & 0xff;
		    opt[3] = max_mss & 0xff;
		    accumulate -= htons(max_mss);
		    tcp_adjust_checksum(accumulate, tcphdr->check);
		  }
	      }
	  }
      }
    }
  };
}
