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

#ifndef OPENVPN_TUN_CLIENT_DHCP_CAPTURE_H
#define OPENVPN_TUN_CLIENT_DHCP_CAPTURE_H

#include "openvpn/client/dns.hpp"
#include <cstring>

#include <openvpn/common/socktypes.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/ip/ipcommon.hpp>
#include <openvpn/ip/dhcp.hpp>
#include <openvpn/tun/builder/capture.hpp>

namespace openvpn {
class DHCPCapture
{
  public:
    // We take a TunBuilderCapture object with previously pushed
    // options and augment it with additional options sniffed
    // from the DHCP reply.
    DHCPCapture(const TunBuilderCapture::Ptr &props_arg)
        : props(props_arg)
    {
        if (props->vpn_ipv4() || props->vpn_ipv4())
            OPENVPN_LOG("NOTE: pushed ifconfig directive is ignored in layer 2 mode");
        if (!props->dns_options.servers.empty())
            OPENVPN_LOG("NOTE: pushed DNS servers are ignored in layer 2 mode");
        reset();
    }

    // returns true when router addr and DNS servers are captured
    bool mod_reply(Buffer &buf)
    {
        if (buf.size() < sizeof(DHCPPacket))
            return false;
        if (!is_safe_conversion<unsigned int>(buf.size()))
            return false;

        DHCPPacket *dhcp = (DHCPPacket *)buf.data();
        if (dhcp->ip.protocol == IPCommon::UDP
            && dhcp->udp.source == htons(DHCP::BOOTPS_PORT)
            && dhcp->udp.dest == htons(DHCP::BOOTPC_PORT)
            && dhcp->dhcp.op == DHCP::BOOTREPLY)
        {
            const unsigned int optlen = static_cast<unsigned int>(buf.size() - sizeof(DHCPPacket));
            const int message_type = dhcp_message_type(dhcp, optlen);
            if (message_type == DHCP::DHCPACK || message_type == DHCP::DHCPOFFER)
            {
                /* get host IP address/netmask */
                const IPv4::Addr host = IPv4::Addr::from_uint32_net(dhcp->dhcp.yiaddr);
                const IPv4::Addr netmask = get_netmask(dhcp, optlen);
                const int prefix_len = netmask.prefix_len_nothrow();

                /* get the router IP address while padding out all DHCP router options */
                const IPv4::Addr router = extract_router(dhcp, optlen);

                /* get DNS server addresses */
                const std::vector<DnsAddress> dns_addresses = get_dns(dhcp, optlen);

                /* recompute the UDP checksum */
                dhcp->udp.check = 0;
                dhcp->udp.check = htons(udp_checksum((uint8_t *)&dhcp->udp,
                                                     sizeof(UDPHeader) + sizeof(DHCP) + optlen,
                                                     (uint8_t *)&dhcp->ip.saddr,
                                                     (uint8_t *)&dhcp->ip.daddr));

                /* only capture the extracted Router address if DHCPACK */
                if (message_type == DHCP::DHCPACK && !configured)
                {
                    bool complete = true;
                    if (host.unspecified())
                    {
                        OPENVPN_LOG("NOTE: failed to obtain host address via DHCP");
                        complete = false;
                    }
                    if (netmask.unspecified())
                    {
                        OPENVPN_LOG("NOTE: failed to obtain netmask via DHCP");
                        complete = false;
                    }
                    if (prefix_len < 0)
                    {
                        OPENVPN_LOG("NOTE: bad netmask obtained via DHCP: " << netmask);
                        complete = false;
                    }
                    if (router.unspecified())
                    {
                        OPENVPN_LOG("NOTE: failed to obtain router via DHCP");
                        complete = false;
                    }
                    if (complete)
                    {
                        reset();
                        props->tun_builder_add_address(host.to_string(), prefix_len, router.to_string(), false, false);
                        if (dns_addresses.empty())
                            OPENVPN_LOG("NOTE: failed to obtain DNS servers via DHCP");
                        else
                        {
                            DnsServer server;
                            server.addresses = dns_addresses;
                            DnsOptions dns_options;
                            dns_options.servers[0] = server;
                            props->tun_builder_set_dns_options(dns_options);
                        }
                    }
                    return configured = complete;
                }
            }
        }
        return false;
    }

    const TunBuilderCapture &get_props() const
    {
        return *props;
    }

  private:
    void reset()
    {
        props->reset_tunnel_addresses();
        props->reset_dns_options();
    }

    static int dhcp_message_type(const DHCPPacket *dhcp, const unsigned int optlen)
    {
        const std::uint8_t *p = dhcp->options;
        for (unsigned int i = 0; i < optlen; ++i)
        {
            const std::uint8_t type = p[i];
            const unsigned int room = optlen - i;

            if (type == DHCP::DHCP_END) /* didn't find what we were looking for */
                return -1;
            else if (type == DHCP::DHCP_PAD) /* no-operation */
                ;
            else if (type == DHCP::DHCP_MSG_TYPE) /* what we are looking for */
            {
                if (room >= 3)
                {
                    if (p[i + 1] == 1)   /* option length should be 1 */
                        return p[i + 2]; /* return message type */
                }
                return -1;
            }
            else /* some other option */
            {
                if (room >= 2)
                {
                    const unsigned int len = p[i + 1]; /* get option length */
                    i += (len + 1);                    /* advance to next option */
                }
            }
        }
        return -1;
    }

    static IPv4::Addr extract_router(DHCPPacket *dhcp, const unsigned int optlen)
    {
        std::uint8_t *p = dhcp->options;
        IPv4::Addr ret = IPv4::Addr::from_zero();

        for (unsigned int i = 0; i < optlen;)
        {
            const std::uint8_t type = p[i];
            const unsigned int room = optlen - i;

            if (type == DHCP::DHCP_END)
                break;
            else if (type == DHCP::DHCP_PAD)
                ++i;
            else if (type == DHCP::DHCP_ROUTER)
            {
                if (room >= 2)
                {
                    const unsigned int len = p[i + 1]; /* get option length */
                    if (len <= (room - 2))
                    {
                        /* get router IP address */
                        if (ret.unspecified() && len >= 4 && (len & 3) == 0)
                            ret = IPv4::Addr::from_bytes_net(p + i + 2);

                        /* delete the router option */
                        std::uint8_t *dest = p + i;
                        const unsigned int owlen = len + 2; /* len of data to overwrite */
                        std::uint8_t *src = dest + owlen;
                        std::uint8_t *end = p + optlen;
                        const ssize_t movlen = end - src;
                        if (movlen > 0)
                            std::memmove(dest, src, static_cast<size_t>(movlen)); /* overwrite router option */
                        std::memset(end - owlen, DHCP::DHCP_PAD, owlen);          /* pad tail */
                    }
                    else
                        break;
                }
                else
                    break;
            }
            else /* some other option */
            {
                if (room >= 2)
                {
                    const unsigned int len = p[i + 1]; /* get option length */
                    i += (len + 2);                    /* advance to next option */
                }
                else
                    break;
            }
        }
        return ret;
    }

    static IPv4::Addr get_netmask(const DHCPPacket *dhcp, const unsigned int optlen)
    {
        const std::uint8_t *p = dhcp->options;
        IPv4::Addr ret = IPv4::Addr::from_zero();

        for (unsigned int i = 0; i < optlen;)
        {
            const std::uint8_t type = p[i];
            const unsigned int room = optlen - i;

            if (type == DHCP::DHCP_END)
                break;
            else if (type == DHCP::DHCP_PAD)
                ++i;
            else if (type == DHCP::DHCP_NETMASK)
            {
                if (room >= 2)
                {
                    const unsigned int len = p[i + 1]; /* get option length */
                    if (len <= (room - 2) && len == 4)
                        return IPv4::Addr::from_bytes_net(p + i + 2);
                    else
                        break;
                }
                else
                    break;
            }
            else /* some other option */
            {
                if (room >= 2)
                {
                    const unsigned int len = p[i + 1]; /* get option length */
                    i += (len + 2);                    /* advance to next option */
                }
                else
                    break;
            }
        }
        return ret;
    }

    static std::vector<DnsAddress> get_dns(const DHCPPacket *dhcp, const unsigned int optlen)
    {
        const std::uint8_t *p = dhcp->options;
        std::vector<DnsAddress> ret;

        for (unsigned int i = 0; i < optlen;)
        {
            const std::uint8_t type = p[i];
            const unsigned int room = optlen - i;

            if (type == DHCP::DHCP_END)
                break;
            else if (type == DHCP::DHCP_PAD)
                ++i;
            else if (type == DHCP::DHCP_DNS)
            {
                if (room >= 2)
                {
                    const unsigned int len = p[i + 1]; /* get option length */
                    if (len <= (room - 2) && (len & 3) == 0)
                    {
                        /* get DNS addresses */
                        for (unsigned int j = 0; j < len; j += 4)
                            ret.push_back({IPv4::Addr::from_bytes_net(p + i + j + 2).to_string(), 0});

                        i += (len + 2); /* advance to next option */
                    }
                    else
                        break;
                }
                else
                    break;
            }
            else /* some other option */
            {
                if (room >= 2)
                {
                    const unsigned int len = p[i + 1]; /* get option length */
                    i += (len + 2);                    /* advance to next option */
                }
                else
                    break;
            }
        }
        return ret;
    }

    TunBuilderCapture::Ptr props;
    bool configured = false;
};
} // namespace openvpn

#endif
