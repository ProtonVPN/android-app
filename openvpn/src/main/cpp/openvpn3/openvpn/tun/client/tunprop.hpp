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

// Process tun interface properties.

#ifndef OPENVPN_TUN_CLIENT_TUNPROP_H
#define OPENVPN_TUN_CLIENT_TUNPROP_H

#include <string>

#include <openvpn/common/size.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/common/options.hpp>
#include <openvpn/common/split.hpp>
#include <openvpn/common/hostport.hpp>
#include <openvpn/tun/builder/base.hpp>
#include <openvpn/addr/addrpair.hpp>
#include <openvpn/client/remotelist.hpp>
#include <openvpn/client/ipverflags.hpp>
#include <openvpn/client/dns.hpp>
#include <openvpn/tun/client/emuexr.hpp>
#include <openvpn/tun/layer.hpp>

namespace openvpn {
class TunProp
{
    // render option flags
    enum
    {
        OPT_RENDER_FLAGS = Option::RENDER_TRUNC_64 | Option::RENDER_BRACKET
    };

    // maximum route metric
    static constexpr int MAX_ROUTE_METRIC = 1000000;

  public:
    OPENVPN_EXCEPTION(tun_prop_error);
    OPENVPN_EXCEPTION(tun_prop_route_error);
    OPENVPN_UNTAGGED_EXCEPTION_INHERIT(option_error, tun_prop_dns_option_error);
    OPENVPN_UNTAGGED_EXCEPTION_INHERIT(option_error, tun_prop_dhcp_option_error);

    struct Config
    {
        std::string session_name;
        int mtu = 0;
        int mtu_max = 0;
        bool google_dns_fallback = false;
#if defined(OPENVPN_PLATFORM_WIN) || defined(OPENVPN_PLATFORM_MAC) || defined(OPENVPN_PLATFORM_LINUX) || defined(OPENVPN_PLATFORM_IPHONE)
        bool dhcp_search_domains_as_split_domains = true;
#else
        bool dhcp_search_domains_as_split_domains = false;
#endif
        bool allow_local_lan_access = false;
        Layer layer{Layer::OSI_LAYER_3};

        // If remote_bypass is true, obtain cached remote IPs from
        // remote_list, and preconfigure exclude route rules for them.
        // Note that the primary remote IP is not included in the
        // exclusion list because existing pathways already exist
        // (i.e. redirect-gateway) for routing this particular address.
        // This feature is intended to work with tun_persist, so that
        // the client is not locked out of contacting subsequent
        // servers in the remote list after the routing configuration
        // for the initial connection has taken effect.
        RemoteList::Ptr remote_list;
        bool remote_bypass = false;
    };

    struct State : public RC<thread_unsafe_refcount>
    {
        typedef RCPtr<State> Ptr;

        std::string iface_name;
        IP::Addr vpn_ip4_addr;
        IP::Addr vpn_ip6_addr;
        IP::Addr vpn_ip4_gw;
        IP::Addr vpn_ip6_gw;
        int mtu = 0;
        bool tun_prefix = false;
    };

    static void configure_builder(TunBuilderBase *tb,
                                  State *state,
                                  SessionStats *stats,
                                  const IP::Addr &server_addr,
                                  const Config &config,
                                  const OptionList &opt,
                                  const EmulateExcludeRouteFactory *eer_factory,
                                  const bool quiet)
    {
        // if eer_factory is defined, we must emulate exclude routes
        EmulateExcludeRoute::Ptr eer;
        if (eer_factory)
            eer = eer_factory->new_obj();

        // do ifconfig
        IP::Addr::VersionMask ip_ver_flags = tun_ifconfig(tb, state, opt);

        // with layer 2, either IPv4 or IPv6 might be supported
        if (config.layer() == Layer::OSI_LAYER_2)
            ip_ver_flags |= (IP::Addr::V4_MASK | IP::Addr::V6_MASK);

        // verify IPv4/IPv6
        if (!ip_ver_flags)
            throw tun_prop_error("one of ifconfig or ifconfig-ipv6 must be specified");

        // get IP version and redirect-gateway flags
        IPVerFlags ipv(opt, ip_ver_flags);

        // add default route-metric
        add_route_metric_default(tb, opt, quiet);

        // add remote bypass routes
        if (config.remote_list && config.remote_bypass && server_addr.defined())
            add_remote_bypass_routes(tb, *config.remote_list, server_addr, eer.get(), quiet);

        // add routes
        if (config.allow_local_lan_access)
        {
            // query local lan exclude routes and then
            // copy option list to construct a copy with the excluded routes as route options
            OptionList excludedRoutesOptions = opt;
            for (const auto &exRoute : tb->tun_builder_get_local_networks(false))
            {
                /* We abuse here the fact that OpenVPN3 core parses "route", "192.168.0.0/24", "", "net_gateway"
                 * in the same way as the correct "route 192.168.0.0.0 255.255.255.0 net_gateway statement */
                excludedRoutesOptions.add_item(Option{"route", exRoute, "", "net_gateway"});
            }

            for (const auto &exRoute : tb->tun_builder_get_local_networks(true))
            {
                excludedRoutesOptions.add_item(Option{"route-ipv6", exRoute, "net_gateway"});
            }

            add_routes(tb, excludedRoutesOptions, ipv, eer.get(), quiet);
        }
        else
        {
            add_routes(tb, opt, ipv, eer.get(), quiet);
        }


        if (eer && server_addr.defined())
        {
            // Route emulation needs to know if default routes are included
            // from redirect-gateway
            eer->add_default_routes(ipv.rgv4(), ipv.rgv6());
            // emulate exclude routes
            eer->emulate(tb, ipv, server_addr);
        }
        else
        {
            // configure redirect-gateway
            if (!tb->tun_builder_reroute_gw(ipv.rgv4(), ipv.rgv6(), ipv.api_flags()))
                throw tun_prop_route_error("tun_builder_reroute_gw for redirect-gateway failed");
        }

        // add DNS servers and domain prefixes
        bool dns_option_added = add_dns_options(tb, opt, quiet, config.dhcp_search_domains_as_split_domains);

        // add DHCP options
        add_dhcp_options(tb, opt, quiet);

        // Allow protocols unless explicitly blocked
        tb->tun_builder_set_allow_family(AF_INET, !opt.exists("block-ipv4"));
        tb->tun_builder_set_allow_family(AF_INET6, !opt.exists("block-ipv6"));

        // Allow access to local port 53 with --dns options unless explicitly blocked
        tb->tun_builder_set_allow_local_dns(!opt.exists("block-outside-dns"));

        // DNS fallback
        if (ipv.rgv4() && !dns_option_added)
        {
            if (config.google_dns_fallback)
            {
                if (!quiet)
                    OPENVPN_LOG("Google DNS fallback enabled");
                add_google_dns(tb);
            }
            else if (stats && (config.layer() != Layer::OSI_LAYER_2))
                stats->error(Error::REROUTE_GW_NO_DNS);
        }

        // set remote server address
        if (server_addr.defined()
            && !tb->tun_builder_set_remote_address(server_addr.to_string(),
                                                   server_addr.version() == IP::Addr::V6))
            throw tun_prop_error("tun_builder_set_remote_address failed");

        // set layer
        if (!tb->tun_builder_set_layer(config.layer.value()))
            throw tun_prop_error("tun_builder_set_layer failed");

        // configure MTU
        tun_mtu(tb, state, opt, config.mtu, config.mtu_max);

        // set session name
        if (!config.session_name.empty())
        {
            if (!tb->tun_builder_set_session_name(config.session_name))
                throw tun_prop_error("tun_builder_set_session_name failed");
        }
    }

  private:
    static void add_route_metric_default(TunBuilderBase *tb,
                                         const OptionList &opt,
                                         const bool quiet)
    {
        try
        {
            const Option *o = opt.get_ptr("route-metric"); // DIRECTIVE
            if (o)
            {
                const int metric = o->get_num<int>(1);
                if (metric < 0 || metric > MAX_ROUTE_METRIC)
                    throw tun_prop_error("route-metric is out of range");
                if (!tb->tun_builder_set_route_metric_default(metric))
                    throw tun_prop_error("tun_builder_set_route_metric_default failed");
            }
        }
        catch (const std::exception &e)
        {
            if (!quiet)
                OPENVPN_LOG("exception processing route-metric: " << e.what());
        }
    }

    static IP::Addr route_gateway(const OptionList &opt)
    {
        IP::Addr gateway;
        const Option *o = opt.get_ptr("route-gateway"); // DIRECTIVE
        if (o)
        {
            gateway = IP::Addr::from_string(o->get(1, 256), "route-gateway");
            if (gateway.version() != IP::Addr::V4)
                throw tun_prop_error("route-gateway is not IPv4 (IPv6 route-gateway is passed with ifconfig-ipv6 directive)");
        }
        return gateway;
    }

    static void tun_mtu(TunBuilderBase *tb,
                        State *state,
                        const OptionList &opt,
                        int config_mtu,
                        int config_mtu_max)
    {
        // MTU
        int tun_mtu = config_mtu;
        const Option *o = opt.get_ptr("tun-mtu");
        if (o)
        {
            bool status = parse_number_validate<decltype(tun_mtu)>(o->get(1, 16),
                                                                   16,
                                                                   68,
                                                                   65535,
                                                                   &tun_mtu);
            tun_mtu = std::min(tun_mtu, config_mtu_max);

            if (!status)
                throw option_error(ERR_INVALID_OPTION_VAL, "tun-mtu parse/range issue");

            if (state)
                state->mtu = tun_mtu;
        }

        if (tun_mtu != 0)
        {
            if (!tb->tun_builder_set_mtu(tun_mtu))
                throw tun_prop_error("tun_builder_set_mtu failed");
        }
    }

    static IP::Addr::VersionMask tun_ifconfig(TunBuilderBase *tb,
                                              State *state,
                                              const OptionList &opt)
    {
        enum Topology
        {
            NET30,
            SUBNET,
        };

        IP::Addr::VersionMask ip_ver_flags = 0;

        // get topology
        Topology top = NET30;
        {
            const Option *o = opt.get_ptr("topology"); // DIRECTIVE
            if (o)
            {
                const std::string &topstr = o->get(1, 16);
                if (topstr == "subnet")
                    top = SUBNET;
                else if (topstr == "net30")
                    top = NET30;
                else
                    throw option_error(ERR_INVALID_OPTION_VAL, "only topology 'subnet' and 'net30' supported");
            }
        }

        // configure tun interface
        {
            const Option *o;
            o = opt.get_ptr("ifconfig"); // DIRECTIVE
            if (o)
            {
                if (top == SUBNET)
                {
                    const IP::AddrMaskPair pair = IP::AddrMaskPair::from_string(o->get(1, 256), o->get_optional(2, 256), "ifconfig");
                    const IP::Addr gateway = route_gateway(opt);
                    if (pair.version() != IP::Addr::V4)
                        throw tun_prop_error("ifconfig address is not IPv4 (topology subnet)");
                    if (!tb->tun_builder_add_address(pair.addr.to_string(),
                                                     pair.netmask.prefix_len(),
                                                     gateway.to_string(),
                                                     false,  // IPv6
                                                     false)) // net30
                        throw tun_prop_error("tun_builder_add_address IPv4 failed (topology subnet)");
                    if (state)
                    {
                        state->vpn_ip4_addr = pair.addr;
                        state->vpn_ip4_gw = gateway;
                    }
                    ip_ver_flags |= IP::Addr::V4_MASK;
                }
                else if (top == NET30)
                {
                    const IP::Addr remote = IP::Addr::from_string(o->get(2, 256));
                    const IP::Addr local = IP::Addr::from_string(o->get(1, 256));
                    const IP::Addr netmask = IP::Addr::from_string("255.255.255.252");
                    if (local.version() != IP::Addr::V4 || remote.version() != IP::Addr::V4)
                        throw tun_prop_error("ifconfig address is not IPv4 (topology net30)");
                    if ((local & netmask) != (remote & netmask))
                        throw tun_prop_error("ifconfig addresses are not in the same /30 subnet (topology net30)");
                    if (!tb->tun_builder_add_address(local.to_string(),
                                                     netmask.prefix_len(),
                                                     remote.to_string(),
                                                     false, // IPv6
                                                     true)) // net30
                        throw tun_prop_error("tun_builder_add_address IPv4 failed (topology net30)");
                    if (state)
                    {
                        state->vpn_ip4_addr = local;
                        state->vpn_ip4_gw = remote;
                    }
                    ip_ver_flags |= IP::Addr::V4_MASK;
                }
                else
                    throw option_error(ERR_INVALID_OPTION_VAL, "internal topology error");
            }

            o = opt.get_ptr("ifconfig-ipv6"); // DIRECTIVE
            if (o)
            {
                // We don't check topology setting here since it doesn't really affect IPv6
                const IP::AddrMaskPair pair = IP::AddrMaskPair::from_string(o->get(1, 256), "ifconfig-ipv6");
                if (pair.version() != IP::Addr::V6)
                    throw tun_prop_error("ifconfig-ipv6 address is not IPv6");
                std::string gateway_str;
                if (o->size() >= 3)
                {
                    const IP::Addr gateway = IP::Addr::from_string(o->get(2, 256), "ifconfig-ipv6");
                    if (gateway.version() != IP::Addr::V6)
                        throw tun_prop_error("ifconfig-ipv6 gateway is not IPv6");
                    gateway_str = gateway.to_string();
                    if (state)
                        state->vpn_ip6_gw = gateway;
                }
                if (!tb->tun_builder_add_address(pair.addr.to_string(),
                                                 pair.netmask.prefix_len(),
                                                 gateway_str,
                                                 true,   // IPv6
                                                 false)) // net30
                    throw tun_prop_error("tun_builder_add_address IPv6 failed");
                if (state)
                    state->vpn_ip6_addr = pair.addr;
                ip_ver_flags |= IP::Addr::V6_MASK;
            }

            return ip_ver_flags;
        }
    }

    static void add_route_tunbuilder(TunBuilderBase *tb,
                                     bool add,
                                     const IP::Addr &addr,
                                     int prefix_length,
                                     int metric,
                                     bool ipv6,
                                     EmulateExcludeRoute *eer)
    {
        const std::string addr_str = addr.to_string();
        if (eer)
            eer->add_route(add, addr, prefix_length);
        else if (add)
        {
            if (!tb->tun_builder_add_route(addr_str, prefix_length, metric, ipv6))
                throw tun_prop_route_error("tun_builder_add_route failed");
        }
        else if (!eer)
        {
            if (!tb->tun_builder_exclude_route(addr_str, prefix_length, metric, ipv6))
                throw tun_prop_route_error("tun_builder_exclude_route failed");
        }
    }

    // Check the target of a route.
    // Return true if route should be added or false if route should be excluded.
    static bool route_target(const Option &o, const size_t target_index)
    {
        if (o.size() >= (target_index + 1))
        {
            const std::string &target = o.ref(target_index);
            if (target == "vpn_gateway")
                return true;
            else if (target == "net_gateway")
                return false;
            else
                throw tun_prop_route_error("route destinations other than vpn_gateway or net_gateway are not supported");
        }
        else
            return true;
    }

    static void add_routes(TunBuilderBase *tb,
                           const OptionList &opt,
                           const IPVerFlags &ipv,
                           EmulateExcludeRoute *eer,
                           const bool quiet)
    {
        // add IPv4 routes
        if (ipv.v4())
        {
            OptionList::IndexMap::const_iterator dopt = opt.map().find("route"); // DIRECTIVE
            if (dopt != opt.map().end())
            {
                for (OptionList::IndexList::const_iterator i = dopt->second.begin(); i != dopt->second.end(); ++i)
                {
                    const Option &o = opt[*i];
                    try
                    {
                        const IP::AddrMaskPair pair = IP::AddrMaskPair::from_string(o.get(1, 256), o.get_optional(2, 256), "route");
                        const int metric = o.get_num<int>(4, -1, 0, MAX_ROUTE_METRIC);
                        if (!pair.is_canonical())
                            throw tun_prop_error("route is not canonical");
                        if (pair.version() != IP::Addr::V4)
                            throw tun_prop_error("route is not IPv4");
                        const bool add = route_target(o, 3);
                        add_route_tunbuilder(tb, add, pair.addr, pair.netmask.prefix_len(), metric, false, eer);
                    }
                    catch (const std::exception &e)
                    {
                        if (!quiet)
                            OPENVPN_LOG("exception parsing IPv4 route: " << o.render(OPT_RENDER_FLAGS) << " : " << e.what());
                    }
                }
            }
        }

        // add IPv6 routes
        if (ipv.v6())
        {
            OptionList::IndexMap::const_iterator dopt = opt.map().find("route-ipv6"); // DIRECTIVE
            if (dopt != opt.map().end())
            {
                for (OptionList::IndexList::const_iterator i = dopt->second.begin(); i != dopt->second.end(); ++i)
                {
                    const Option &o = opt[*i];
                    try
                    {
                        const IP::AddrMaskPair pair = IP::AddrMaskPair::from_string(o.get(1, 256), "route-ipv6");
                        const int metric = o.get_num<int>(3, -1, 0, MAX_ROUTE_METRIC);
                        if (!pair.is_canonical())
                            throw tun_prop_error("route is not canonical");
                        if (pair.version() != IP::Addr::V6)
                            throw tun_prop_error("route is not IPv6");
                        const bool add = route_target(o, 2);
                        add_route_tunbuilder(tb, add, pair.addr, pair.netmask.prefix_len(), metric, true, eer);
                    }
                    catch (const std::exception &e)
                    {
                        if (!quiet)
                            OPENVPN_LOG("exception parsing IPv6 route: " << o.render(OPT_RENDER_FLAGS) << " : " << e.what());
                    }
                }
            }
        }
    }

    static void add_remote_bypass_routes(TunBuilderBase *tb,
                                         const RemoteList &remote_list,
                                         const IP::Addr &server_addr,
                                         EmulateExcludeRoute *eer,
                                         const bool quiet)
    {
        IP::AddrList addrlist;
        remote_list.cached_ip_address_list(addrlist);
        for (IP::AddrList::const_iterator i = addrlist.begin(); i != addrlist.end(); ++i)
        {
            const IP::Addr &addr = *i;
            if (addr != server_addr)
            {
                try
                {
                    const IP::Addr::Version ver = addr.version();
                    add_route_tunbuilder(tb, false, addr, IP::Addr::version_size(ver), -1, ver == IP::Addr::V6, eer);
                }
                catch (const std::exception &e)
                {
                    if (!quiet)
                        OPENVPN_LOG("exception adding remote bypass route: " << addr.to_string() << " : " << e.what());
                }
            }
        }
    }

    /**
     * @brief Configure tun builder to use DNS related options if defined
     *
     * @param tb            pointer to the tun builder
     * @param opt           the --dns options object
     * @param quiet         boolean to silence log messages about issues processing the options
     * @param use_dhcp_search_domains_as_split_domains boolean to apply dhcp-option DNS search domains as split domains
     * @return bool flag when there were servers defined in the options
     */
    static bool add_dns_options(TunBuilderBase *tb, const OptionList &opt, bool quiet, bool use_dhcp_search_domains_as_split_domains)
    {
        try
        {
            DnsOptionsParser dns_options(opt, use_dhcp_search_domains_as_split_domains);
            if (dns_options.servers.empty())
                return false;

            if (!tb->tun_builder_set_dns_options(dns_options))
                throw tun_prop_dns_option_error("tun_builder_set_dns_options failed");

            return true;
        }
        catch (const std::exception &e)
        {
            if (!quiet)
            {
                OPENVPN_LOG("exception parsing DNS settings:"
                            << e.what());
            }
        }

        return false;
    }

    /**
     * @brief Parse options for WINS and HTTP Proxy --dhcp-option(s)
     *        and add them to the tunbuilder (tb) passed.
     *
     * @param tb                pointer to tunbuilder to add setting to
     * @param opt               reference to the (pushed) options
     * @param quiet             boolean to silence log messages about issues processing the options
     */
    static void add_dhcp_options(TunBuilderBase *tb, const OptionList &opt, const bool quiet)
    {
        // Example:
        //   [dhcp-option] [WINS] [172.16.0.23]
        //   [dhcp-option] [PROXY_HTTP] [foo.bar.gov] [1234]
        //   [dhcp-option] [PROXY_HTTPS] [foo.bar.gov] [1234]
        //   [dhcp-option] [PROXY_BYPASS] [server1] [server2] ...
        //   [dhcp-option] [PROXY_AUTO_CONFIG_URL] [http://...]

        OptionList::IndexMap::const_iterator dopt = opt.map().find("dhcp-option"); // DIRECTIVE
        if (dopt != opt.map().end())
        {
            std::string auto_config_url;
            std::string http_host;
            unsigned int http_port = 0;
            std::string https_host;
            unsigned int https_port = 0;
            for (OptionList::IndexList::const_iterator i = dopt->second.begin(); i != dopt->second.end(); ++i)
            {
                const Option &o = opt[*i];
                try
                {
                    const std::string &type = o.get(1, 64);
                    if (type == "DNS" || type == "DNS6" || type == "DOMAIN" || type == "DOMAIN-SEARCH" || type == "ADAPTER_DOMAIN_SUFFIX")
                    {
                        // Ignore DNS related options here
                        continue;
                    }
                    else if (type == "PROXY_BYPASS")
                    {
                        o.min_args(3);
                        for (size_t j = 2; j < o.size(); ++j)
                        {
                            typedef std::vector<std::string> strvec;
                            strvec v = Split::by_space<strvec, StandardLex, SpaceMatch, Split::NullLimit>(o.get(j, 256));
                            for (size_t k = 0; k < v.size(); ++k)
                            {
                                if (!tb->tun_builder_add_proxy_bypass(v[k]))
                                    throw tun_prop_dhcp_option_error(ERR_INVALID_OPTION_PUSHED, "tun_builder_add_proxy_bypass");
                            }
                        }
                    }
                    else if (type == "PROXY_AUTO_CONFIG_URL")
                    {
                        o.exact_args(3);
                        auto_config_url = o.get(2, 256);
                    }
                    else if (type == "PROXY_HTTP")
                    {
                        o.exact_args(4);
                        http_host = o.get(2, 256);
                        HostPort::validate_port(o.get(3, 256), "PROXY_HTTP", &http_port);
                    }
                    else if (type == "PROXY_HTTPS")
                    {
                        o.exact_args(4);
                        https_host = o.get(2, 256);
                        HostPort::validate_port(o.get(3, 256), "PROXY_HTTPS", &https_port);
                    }
                    else if (type == "WINS")
                    {
                        o.exact_args(3);
                        const IP::Addr ip = IP::Addr::from_string(o.get(2, 256), "wins-server-ip");
                        if (ip.version() != IP::Addr::V4)
                            throw tun_prop_dhcp_option_error(ERR_INVALID_OPTION_PUSHED, "WINS addresses must be IPv4");
                        if (!tb->tun_builder_add_wins_server(ip.to_string()))
                            throw tun_prop_dhcp_option_error(ERR_INVALID_OPTION_PUSHED, "tun_builder_add_wins_server failed");
                    }
                    else if (!quiet)
                        OPENVPN_LOG("Unknown pushed DHCP option: " << o.render(OPT_RENDER_FLAGS));
                }
                catch (const std::exception &e)
                {
                    if (!quiet)
                        OPENVPN_LOG("exception parsing dhcp-option: " << o.render(OPT_RENDER_FLAGS) << " : " << e.what());
                }
            }
            try
            {
                if (!http_host.empty())
                {
                    if (!tb->tun_builder_set_proxy_http(http_host, http_port))
                        throw tun_prop_dhcp_option_error(ERR_INVALID_OPTION_PUSHED, "tun_builder_set_proxy_http");
                }
                if (!https_host.empty())
                {
                    if (!tb->tun_builder_set_proxy_https(https_host, https_port))
                        throw tun_prop_dhcp_option_error(ERR_INVALID_OPTION_PUSHED, "tun_builder_set_proxy_https");
                }
                if (!auto_config_url.empty())
                {
                    if (!tb->tun_builder_set_proxy_auto_config_url(auto_config_url))
                        throw tun_prop_dhcp_option_error(ERR_INVALID_OPTION_PUSHED, "tun_builder_set_proxy_auto_config_url");
                }
            }
            catch (const std::exception &e)
            {
                if (!quiet)
                    OPENVPN_LOG("exception setting dhcp-option for proxy: " << e.what());
            }
        }
    }

    static bool search_domains_exist(const OptionList &opt, const bool quiet)
    {
        OptionList::IndexMap::const_iterator dopt = opt.map().find("dhcp-option"); // DIRECTIVE
        if (dopt != opt.map().end())
        {
            for (OptionList::IndexList::const_iterator i = dopt->second.begin(); i != dopt->second.end(); ++i)
            {
                const Option &o = opt[*i];
                try
                {
                    const std::string &type = o.get(1, 64);
                    if (type == "DOMAIN")
                        return true;
                }
                catch (const std::exception &e)
                {
                    if (!quiet)
                        OPENVPN_LOG("exception parsing dhcp-option: " << o.render(OPT_RENDER_FLAGS) << " : " << e.what());
                }
            }
        }
        return false;
    }

    static void add_google_dns(TunBuilderBase *tb)
    {
        DnsServer server;
        DnsOptions google_dns;
        server.addresses = {{{"8.8.8.8"}, 0}, {{"8.8.4.4"}, 0}};
        google_dns.servers[0] = std::move(server);
        if (!tb->tun_builder_set_dns_options(google_dns))
            throw tun_prop_dns_option_error(ERR_INVALID_OPTION_PUSHED, "tun_builder_set_dns_options failed for Google DNS");
    }
};
} // namespace openvpn

#endif // OPENVPN_TUN_CLIENT_TUNPROP_H
