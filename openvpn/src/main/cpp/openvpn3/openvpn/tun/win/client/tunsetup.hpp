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
//

// Client tun setup for Windows

#ifndef OPENVPN_TUN_WIN_CLIENT_TUNSETUP_H
#define OPENVPN_TUN_WIN_CLIENT_TUNSETUP_H

#include <string>
#include <sstream>
#include <ostream>
#include <memory>
#include <utility>
#include <thread>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/size.hpp>
#include <openvpn/common/arraysize.hpp>
#include <openvpn/error/excode.hpp>
#include <openvpn/time/time.hpp>
#include <openvpn/tun/proxy.hpp>
#include <openvpn/tun/win/tunutil.hpp>
#include <openvpn/tun/win/winproxy.hpp>
#include <openvpn/tun/win/tunutil.hpp>
#include <openvpn/tun/win/client/setupbase.hpp>
#include <openvpn/win/scoped_handle.hpp>
#include <openvpn/win/cmd.hpp>

#if _WIN32_WINNT >= 0x0600 // Vista+
#include <openvpn/tun/win/nrpt.hpp>
#include <openvpn/tun/win/wfp.hpp>
#endif

#include <versionhelpers.h>

// use IP Helper on Windows by default
#ifdef OPENVPN_USE_NETSH
#define TUNWINDOWS Util::TunNETSH
#else
#define TUNWINDOWS Util::TunIPHELPER
#endif

namespace openvpn {
namespace TunWin {
class Setup : public SetupBase
{
  public:
    typedef RCPtr<Setup> Ptr;

    Setup(openvpn_io::io_context &io_context_arg, const Type tun_type, bool allow_local_dns_resolvers_arg)
        : delete_route_timer(io_context_arg),
          tun_type_(tun_type),
          allow_local_dns_resolvers(allow_local_dns_resolvers_arg)
    {
    }

    Util::TapNameGuidPair get_adapter_state() override
    {
        return tap_;
    }

    void set_adapter_state(const Util::TapNameGuidPair &tap) override
    {
        tap_ = tap;
    }

    HANDLE get_handle(std::ostream &os) override
    {
        if (tap_.index_defined())
            // tap has already been opened
            return INVALID_HANDLE_VALUE;

        // enumerate available TAP adapters
        Util::TapNameGuidPairList guids(tun_type_);
        os << "TAP ADAPTERS:" << std::endl
           << guids.to_string() << std::endl;

        // open TAP device handle
        std::string path_opened;
        Win::ScopedHANDLE th(Util::tap_open(tun_type_, guids, path_opened, tap_));
        os << "Open TAP device \"" + tap_.name + "\" PATH=\"" + path_opened + '\"';
        if (!th.defined())
        {
            os << " FAILED" << std::endl;
            throw ErrorCode(Error::TUN_IFACE_CREATE, true, "cannot acquire TAP handle");
        }

        os << " SUCCEEDED" << std::endl;
        if (tun_type_ == TapWindows6)
        {
            Util::TAPDriverVersion version(th());
            os << version.to_string() << std::endl;
        }

        return th.release();
    }

    // Set up the TAP device
    virtual HANDLE establish(const TunBuilderCapture &pull,
                             const std::wstring &openvpn_app_path,
                             Stop *stop,
                             std::ostream &os,
                             RingBuffer::Ptr ring_buffer) override // defined by SetupBase
    {
        // close out old remove cmds, if they exist
        destroy(os);

        ScopedHANDLE adapter_handle;
        if (tun_type_ != OvpnDco)
            adapter_handle.replace(get_handle(os));
        vpn_interface_index_ = tap_.index;

        // create ActionLists for setting up and removing adapter properties
        ActionList::Ptr add_cmds(new ActionList());
        remove_cmds.reset(new ActionList());

        // populate add/remove lists with actions
        switch (pull.layer())
        {
        case Layer::OSI_LAYER_3:
            adapter_config(adapter_handle(), openvpn_app_path, tap_, pull, false, *add_cmds, *remove_cmds, os);
            break;
        case Layer::OSI_LAYER_2:
            adapter_config_l2(adapter_handle(), openvpn_app_path, tap_, pull, *add_cmds, *remove_cmds, os);
            break;
        default:
            throw tun_win_setup("layer undefined");
        }
        // execute the add actions
        add_cmds->execute(os);

        // now that the add actions have succeeded,
        // enable the remove actions
        remove_cmds->enable_destroy(true);

        // if layer 2, save state
        if (pull.layer() == Layer::OSI_LAYER_2)
            l2_state.reset(new L2State(tap_, openvpn_app_path));

        if (ring_buffer)
            register_rings(adapter_handle(), ring_buffer);

        if (tun_type_ == Type::TapWindows6 && tap_.index_defined())
            Util::flush_arp(tap_.index, os);

        return adapter_handle.release();
    }

    // In layer 2 mode, return true route_delay seconds after
    // the adapter properties matches the data given in pull.
    // This method is usually called once per second until it
    // returns true.
    virtual bool l2_ready(const TunBuilderCapture &pull) override
    {
        const unsigned int route_delay = 5;
        if (l2_state)
        {
            if (l2_state->props_ready.defined())
            {
                if (Time::now() >= l2_state->props_ready)
                    return true;
            }
            else
            {
                const Util::IPNetmask4 vpn_addr(pull, "VPN IP");
                const Util::IPAdaptersInfo ai;
                if (ai.is_up(l2_state->tap.index, vpn_addr))
                    l2_state->props_ready = Time::now() + Time::Duration::seconds(route_delay);
            }
        }
        return false;
    }

    // Finish the layer 2 configuration, should be called
    // after l2_ready() returns true.
    virtual void l2_finish(const TunBuilderCapture &pull,
                           Stop *stop,
                           std::ostream &os) override
    {
        std::unique_ptr<L2State> l2s(std::move(l2_state));
        if (l2s)
        {
            Win::ScopedHANDLE nh;
            ActionList::Ptr add_cmds(new ActionList());
            adapter_config(nh(), l2s->openvpn_app_path, l2s->tap, pull, true, *add_cmds, *remove_cmds, os);
            add_cmds->execute(os);
        }
    }

    virtual void destroy(std::ostream &os) override // defined by DestructorBase
    {
        // l2_state
        l2_state.reset();

        // l2_thread
        if (l2_thread)
        {
            try
            {
                l2_thread->join();
            }
            catch (...)
            {
            }
            l2_thread.reset();
        }

        // remove_cmds
        if (remove_cmds)
        {
            remove_cmds->destroy(os);
            remove_cmds.reset();
        }

        delete_route_timer.cancel();

        vpn_interface_index_ = DWORD(-1);
    }

    virtual ~Setup()
    {
        std::ostringstream os;
        destroy(os);
    }

    DWORD vpn_interface_index() const
    {
        return vpn_interface_index_;
    }

    static void add_bypass_route(const Util::BestGateway &gw,
                                 const std::string &route,
                                 bool ipv6,
                                 ActionList &add_cmds,
                                 ActionList &remove_cmds_bypass_gw)
    {
        if (!ipv6)
        {
            if (!gw.local_route())
            {
                add_cmds.add(new WinCmd("netsh interface ip add route " + route + "/32 " + to_string(gw.interface_index()) + ' ' + gw.gateway_address() + " store=active"));
                remove_cmds_bypass_gw.add(new WinCmd("netsh interface ip delete route " + route + "/32 " + to_string(gw.interface_index()) + ' ' + gw.gateway_address() + " store=active"));
            }
            else
            {
                OPENVPN_LOG("Skip bypass route to " << route << ", route is local");
            }
        }
    }

  private:
    struct L2State
    {
        L2State(const Util::TapNameGuidPair &tap_arg,
                const std::wstring &openvpn_app_path_arg)
            : tap(tap_arg),
              openvpn_app_path(openvpn_app_path_arg)
        {
        }

        Util::TapNameGuidPair tap;
        std::wstring openvpn_app_path;
        Time props_ready;
    };

    class UseDNS
    {
      public:
        UseDNS()
        {
        }

        UseDNS(const TunBuilderCapture &pull)
        {
            for (auto &ds : pull.dns_servers)
                add(ds, pull);
        }

        static bool enabled(const TunBuilderCapture::DNSServer &ds,
                            const TunBuilderCapture &pull)
        {
            if (ds.ipv6 && pull.block_ipv6)
                return false;
            return true;
        }

        int add(const TunBuilderCapture::DNSServer &ds,
                const TunBuilderCapture &pull)
        {
            if (enabled(ds, pull))
                return indices[ds.ipv6 ? 1 : 0]++;
            else
                return -1;
        }

        int ipv4() const
        {
            return indices[0];
        }
        int ipv6() const
        {
            return indices[1];
        }

      private:
        int indices[2] = {0, 0};
    };

    void register_rings(HANDLE handle, RingBuffer::Ptr ring_buffer)
    {
        TUN_REGISTER_RINGS rings;

        ZeroMemory(&rings, sizeof(rings));

        rings.receive.ring = ring_buffer->receive_ring();
        rings.receive.tail_moved = ring_buffer->receive_ring_tail_moved();
        rings.receive.ring_size = sizeof(rings.receive.ring->data);

        rings.send.ring = ring_buffer->send_ring();
        rings.send.tail_moved = ring_buffer->send_ring_tail_moved();
        rings.send.ring_size = sizeof(rings.send.ring->data);

        {
            Win::Impersonate imp(true);

            DWORD len;
            if (!DeviceIoControl(handle, TUN_IOCTL_REGISTER_RINGS, &rings, sizeof(rings), NULL, 0, &len, NULL))
            {
                const Win::LastError err;
                throw ErrorCode(Error::TUN_REGISTER_RINGS_ERROR, true, "Error registering ring buffers: " + err.message());
            }
        }
    }

#if _WIN32_WINNT >= 0x0600
    // Configure TAP adapter on Vista and higher
    void adapter_config(HANDLE th,
                        const std::wstring &openvpn_app_path,
                        const Util::TapNameGuidPair &tap,
                        const TunBuilderCapture &pull,
                        const bool l2_post,
                        ActionList &create,
                        ActionList &destroy,
                        std::ostream &os)
    {
        // Windows interface index
        const std::string tap_index_name = tap.index_or_name();

        // special IPv6 next-hop recognized by TAP driver (magic)
        const std::string ipv6_next_hop = "fe80::8";

        // set local4 and local6 to point to IPv4/6 route configurations
        const TunBuilderCapture::RouteAddress *local4 = pull.vpn_ipv4();
        const TunBuilderCapture::RouteAddress *local6 = pull.vpn_ipv6();

        if (!l2_post)
        {
            // set TAP media status to CONNECTED
            if (tun_type_ == TapWindows6)
                Util::tap_set_media_status(th, true);

            // try to delete any stale routes on interface left over from previous session
            create.add(new Util::ActionDeleteAllRoutesOnInterface(tap.index));
        }

        // Set IPv4 Interface
        //
        // Usage: set address [name=]<string>
        //  [[source=]dhcp|static]
        //  [[address=]<IPv4 address>[/<integer>] [[mask=]<IPv4 mask>]
        //  [[gateway=]<IPv4 address>|none [gwmetric=]<integer>]
        //  [[type=]unicast|anycast]
        //  [[subinterface=]<string>]
        //  [[store=]active|persistent]
        // Usage: delete address [name=]<string> [[address=]<IPv4 address>]
        //  [[gateway=]<IPv4 address>|all]
        //  [[store=]active|persistent]
        if (local4)
        {
            // Process ifconfig and topology
            if (!l2_post)
            {
                // set lowest interface metric to make Windows use pushed DNS search domain
                create.add(new WinCmd("netsh interface ip set interface " + tap_index_name + " metric=1"));

                const std::string metric = route_metric_opt(pull, *local4, MT_IFACE);
                const std::string netmask = IPv4::Addr::netmask_from_prefix_len(local4->prefix_length).to_string();
                const IP::Addr localaddr = IP::Addr::from_string(local4->address);
                const IP::Addr remoteaddr = IP::Addr::from_string(local4->gateway);
                if (tun_type_ == TapWindows6)
                {
                    if (local4->net30)
                        Util::tap_configure_topology_net30(th, localaddr, remoteaddr);
                    else
                        Util::tap_configure_topology_subnet(th, localaddr, local4->prefix_length);
                }
                create.add(new WinCmd("netsh interface ip set address " + tap_index_name + " static " + local4->address + ' ' + netmask + " gateway=" + local4->gateway + metric + " store=active"));
                destroy.add(new WinCmd("netsh interface ip delete address " + tap_index_name + ' ' + local4->address + " gateway=all store=active"));

                // specifying 'gateway' when setting ip address makes Windows add unnecessary route 0.0.0.0/0,
                // which might cause routing conflicts, so we have to delete it after a small delay.
                // If route is deleted before profile is created, then profile won't be created at all (OVPN-135)
                WinCmd::Ptr cmd = new WinCmd("netsh interface ip delete route 0.0.0.0/0 " + tap_index_name + ' ' + local4->gateway + " store=active");
                delete_route_timer.expires_after(Time::Duration::seconds(5));
                delete_route_timer.async_wait([self = Ptr(this), cmd = std::move(cmd)](const openvpn_io::error_code &error)
                                              {
						if (!error)
						  {
						    std::ostringstream os;
						    cmd->execute(os);
						  } });
            }
        }

        // Should we block IPv6?
        if (pull.block_ipv6)
        {
            static const char *const block_ipv6_net[] = {
                "2000::/4",
                "3000::/4",
                "fc00::/7",
            };
            for (size_t i = 0; i < array_size(block_ipv6_net); ++i)
            {
                create.add(new WinCmd("netsh interface ipv6 add route " + std::string(block_ipv6_net[i]) + " interface=1 store=active"));
                destroy.add(new WinCmd("netsh interface ipv6 delete route " + std::string(block_ipv6_net[i]) + " interface=1 store=active"));
            }
        }

        // Set IPv6 Interface
        //
        // Usage: set address [interface=]<string> [address=]<IPv6 address>
        //  [[type=]unicast|anycast]
        //  [[validlifetime=]<integer>|infinite]
        //  [[preferredlifetime=]<integer>|infinite]
        //  [[store=]active|persistent]
        // Usage: delete address [interface=]<string> [address=]<IPv6 address>
        //  [[store=]active|persistent]
        if (local6 && !pull.block_ipv6 && !l2_post)
        {
            create.add(new WinCmd("netsh interface ipv6 set address " + tap_index_name + ' ' + local6->address + " store=active"));
            destroy.add(new WinCmd("netsh interface ipv6 delete address " + tap_index_name + ' ' + local6->address + " store=active"));

            create.add(new WinCmd("netsh interface ipv6 add route " + local6->gateway + '/' + to_string(local6->prefix_length) + ' ' + tap_index_name + ' ' + ipv6_next_hop + " store=active"));
            destroy.add(new WinCmd("netsh interface ipv6 delete route " + local6->gateway + '/' + to_string(local6->prefix_length) + ' ' + tap_index_name + ' ' + ipv6_next_hop + " store=active"));
        }

        // Process Routes
        //
        // Usage: add route [prefix=]<IPv4 address>/<integer> [interface=]<string>
        //  [[nexthop=]<IPv4 address>] [[siteprefixlength=]<integer>]
        //  [[metric=]<integer>] [[publish=]no|age|yes]
        //  [[validlifetime=]<integer>|infinite]
        //  [[preferredlifetime=]<integer>|infinite]
        //  [[store=]active|persistent]
        // Usage: delete route [prefix=]<IPv4 address>/<integer> [interface=]<string>
        //  [[nexthop=]<IPv4 address>]
        //  [[store=]active|persistent]
        //
        // Usage: add route [prefix=]<IPv6 address>/<integer> [interface=]<string>
        //  [[nexthop=]<IPv6 address>] [[siteprefixlength=]<integer>]
        //  [[metric=]<integer>] [[publish=]no|age|yes]
        //  [[validlifetime=]<integer>|infinite]
        //  [[preferredlifetime=]<integer>|infinite]
        //  [[store=]active|persistent]
        // Usage: delete route [prefix=]<IPv6 address>/<integer> [interface=]<string>
        //  [[nexthop=]<IPv6 address>]
        //  [[store=]active|persistent]
        {
            for (auto &route : pull.add_routes)
            {
                if (route.ipv6)
                {
                    if (!pull.block_ipv6)
                    {
                        const std::string metric = route_metric_opt(pull, route, MT_NETSH);
                        create.add(new WinCmd("netsh interface ipv6 add route " + route.address + '/' + to_string(route.prefix_length) + ' ' + tap_index_name + ' ' + ipv6_next_hop + metric + " store=active"));
                        destroy.add(new WinCmd("netsh interface ipv6 delete route " + route.address + '/' + to_string(route.prefix_length) + ' ' + tap_index_name + ' ' + ipv6_next_hop + " store=active"));
                    }
                }
                else
                {
                    if (local4)
                    {
                        int metric = pull.route_metric_default;
                        if (route.metric >= 0)
                            metric = route.metric;
                        create.add(new TUNWINDOWS::AddRoute4Cmd(route.address, route.prefix_length, tap.index, tap.name, local4->gateway, metric, true));
                        destroy.add(new TUNWINDOWS::AddRoute4Cmd(route.address, route.prefix_length, tap.index, tap.name, local4->gateway, metric, false));
                    }
                    else
                        throw tun_win_setup("IPv4 routes pushed without IPv4 ifconfig");
                }
            }
        }

        // Process exclude routes
        if (!pull.exclude_routes.empty())
        {
            const Util::BestGateway gw;
            if (gw.defined())
            {
                bool ipv6_error = false;
                for (auto &route : pull.exclude_routes)
                {
                    int metric = pull.route_metric_default;
                    if (route.metric >= 0)
                        metric = route.metric;

                    if (route.ipv6)
                    {
                        ipv6_error = true;
                    }
                    else
                    {
                        create.add(new TUNWINDOWS::AddRoute4Cmd(route.address, route.prefix_length, gw.interface_index(), "", gw.gateway_address(), metric, true));
                        destroy.add(new TUNWINDOWS::AddRoute4Cmd(route.address, route.prefix_length, gw.interface_index(), "", gw.gateway_address(), metric, false));
                    }
                }
                if (ipv6_error)
                    os << "NOTE: exclude IPv6 routes not currently supported" << std::endl;
            }
            else
                os << "NOTE: exclude routes error: cannot detect default gateway" << std::endl;
        }

        // Process IPv4 redirect-gateway
        if (pull.reroute_gw.ipv4)
        {
            // get default gateway
            const Util::BestGateway gw{pull.remote_address.address, tap.index};

            if (!gw.local_route())
            {
                // add server bypass route
                if (gw.defined())
                {
                    if (!pull.remote_address.ipv6 && !(pull.reroute_gw.flags & RedirectGatewayFlags::RG_LOCAL))
                        add_bypass_route(gw, pull.remote_address.address, false, create, destroy);
                }
                else
                    throw tun_win_setup("redirect-gateway error: cannot find gateway for bypass route");
            }

            create.add(new WinCmd("netsh interface ip add route 0.0.0.0/1 " + tap_index_name + ' ' + local4->gateway + " store=active"));
            create.add(new WinCmd("netsh interface ip add route 128.0.0.0/1 " + tap_index_name + ' ' + local4->gateway + " store=active"));
            destroy.add(new WinCmd("netsh interface ip delete route 0.0.0.0/1 " + tap_index_name + ' ' + local4->gateway + " store=active"));
            destroy.add(new WinCmd("netsh interface ip delete route 128.0.0.0/1 " + tap_index_name + ' ' + local4->gateway + " store=active"));
        }

        // Process IPv6 redirect-gateway
        if (pull.reroute_gw.ipv6 && !pull.block_ipv6)
        {
            create.add(new WinCmd("netsh interface ipv6 add route 0::/1 " + tap_index_name + ' ' + ipv6_next_hop + " store=active"));
            create.add(new WinCmd("netsh interface ipv6 add route 8000::/1 " + tap_index_name + ' ' + ipv6_next_hop + " store=active"));
            destroy.add(new WinCmd("netsh interface ipv6 delete route 0::/1 " + tap_index_name + ' ' + ipv6_next_hop + " store=active"));
            destroy.add(new WinCmd("netsh interface ipv6 delete route 8000::/1 " + tap_index_name + ' ' + ipv6_next_hop + " store=active"));
        }

        // Process DNS Servers
        //
        // Usage: set dnsservers [name=]<string> [source=]dhcp|static
        //  [[address=]<IP address>|none]
        //  [[register=]none|primary|both]
        //  [[validate=]yes|no]
        // Usage: add dnsservers [name=]<string> [address=]<IPv4 address>
        //  [[index=]<integer>] [[validate=]yes|no]
        // Usage: delete dnsservers [name=]<string> [[address=]<IP address>|all] [[validate=]yes|no]
        //
        // Usage: set dnsservers [name=]<string> [source=]dhcp|static
        //  [[address=]<IPv6 address>|none]
        //  [[register=]none|primary|both]
        //  [[validate=]yes|no]
        // Usage: add dnsservers [name=]<string> [address=]<IPv6 address>
        //  [[index=]<integer>] [[validate=]yes|no]
        // Usage: delete dnsservers [name=]<string> [[address=]<IPv6 address>|all] [[validate=]yes|no]
        {
            // fix for vista and dnsserver vs win7+ dnsservers
            std::string dns_servers_cmd = "dnsservers";
            std::string validate_cmd = " validate=no";
            if (IsWindowsVistaOrGreater() && !IsWindows7OrGreater())
            {
                dns_servers_cmd = "dnsserver";
                validate_cmd = "";
            }

#if 1
            // normal production setting
            const bool use_nrpt = IsWindows8OrGreater();
            const bool use_wfp = IsWindows8OrGreater();
            const bool add_netsh_rules = true;
#else
            // test NRPT registry settings on pre-Win8
            const bool use_nrpt = true;
            const bool use_wfp = true;
            const bool add_netsh_rules = true;
#endif
            // determine IPv4/IPv6 DNS redirection
            const UseDNS dns(pull);

            // will DNS requests be split between VPN DNS server and local?
            const bool split_dns = (!pull.search_domains.empty()
                                    && !(pull.reroute_gw.ipv4 && dns.ipv4())
                                    && !(pull.reroute_gw.ipv6 && dns.ipv6()));

            // add DNS servers via netsh
            if (add_netsh_rules && !(use_nrpt && split_dns) && !l2_post)
            {
                UseDNS dc;
                for (auto &ds : pull.dns_servers)
                {
                    // 0-based index for specific IPv4/IPv6 protocol, or -1 if disabled
                    const int count = dc.add(ds, pull);
                    if (count >= 0)
                    {
                        const std::string proto = ds.ipv6 ? "ipv6" : "ip";
                        if (count)
                            create.add(new WinCmd("netsh interface " + proto + " add " + dns_servers_cmd + " " + tap_index_name + ' ' + ds.address + " " + to_string(count + 1) + validate_cmd));
                        else
                        {
                            create.add(new WinCmd("netsh interface " + proto + " set " + dns_servers_cmd + " " + tap_index_name + " static " + ds.address + " register=primary" + validate_cmd));
                            destroy.add(new WinCmd("netsh interface " + proto + " delete " + dns_servers_cmd + " " + tap_index_name + " all" + validate_cmd));
                        }
                    }
                }
            }

            // If NRPT enabled and at least one IPv4 or IPv6 DNS
            // server was added, add NRPT registry entries to
            // route DNS through the tunnel.
            // Also consider selective DNS routing using domain
            // suffix list from pull.search_domains as set by
            // "dhcp-option DOMAIN ..." directives.
            if (use_nrpt && (dns.ipv4() || dns.ipv6()))
            {
                // domain suffix list
                std::vector<std::string> dsfx;

                // Only add DNS routing suffixes if not rerouting gateway.
                // Otherwise, route all DNS requests with wildcard (".").
                if (split_dns)
                {
                    for (const auto &sd : pull.search_domains)
                    {
                        std::string dom = sd.domain;
                        if (!dom.empty())
                        {
                            // each DNS suffix must begin with '.'
                            if (dom[0] != '.')
                                dom = "." + dom;
                            dsfx.push_back(std::move(dom));
                        }
                    }
                }
                if (dsfx.empty() && !allow_local_dns_resolvers)
                    dsfx.emplace_back(".");

                // DNS server list
                std::vector<std::string> dserv;
                for (const auto &ds : pull.dns_servers)
                    dserv.push_back(ds.address);

                create.add(new NRPT::ActionCreate(dsfx, dserv));
                destroy.add(new NRPT::ActionDelete);
            }

            // Use WFP for DNS leak protection.
            // If we added DNS servers, block DNS on all interfaces except
            // the TAP adapter.
            if (use_wfp && !split_dns && !openvpn_app_path.empty() && (dns.ipv4() || dns.ipv6()))
            {
                create.add(new ActionWFP(openvpn_app_path, tap.index, true, allow_local_dns_resolvers, wfp));
                destroy.add(new ActionWFP(openvpn_app_path, tap.index, false, allow_local_dns_resolvers, wfp));
            }
        }

        // Set a default TAP-adapter domain suffix using
        // "dhcp-option ADAPTER_DOMAIN_SUFFIX mycompany.com" directive.
        if (!pull.adapter_domain_suffix.empty())
        {
            // Only the first search domain is used
            create.add(new Util::ActionSetAdapterDomainSuffix(pull.adapter_domain_suffix, tap.guid));
            destroy.add(new Util::ActionSetAdapterDomainSuffix("", tap.guid));
        }

        // Process WINS Servers
        //
        // Usage: set winsservers [name=]<string> [source=]dhcp|static
        //  [[address=]<IP address>|none]
        // Usage: add winsservers [name=]<string> [address=]<IP address> [[index=]<integer>]
        // Usage: delete winsservers [name=]<string> [[address=]<IP address>|all]
        {
            for (size_t i = 0; i < pull.wins_servers.size(); ++i)
            {
                const TunBuilderCapture::WINSServer &ws = pull.wins_servers[i];
                if (i)
                    create.add(new WinCmd("netsh interface ip add winsservers " + tap_index_name + ' ' + ws.address + ' ' + to_string(i + 1)));
                else
                {
                    create.add(new WinCmd("netsh interface ip set winsservers " + tap_index_name + " static " + ws.address));
                    destroy.add(new WinCmd("netsh interface ip delete winsservers " + tap_index_name + " all"));
                }
            }
        }

        OPENVPN_LOG("proxy_auto_config_url " << pull.proxy_auto_config_url.url);
        if (pull.proxy_auto_config_url.defined())
            ProxySettings::add_actions<WinProxySettings>(pull, create, destroy);

        // flush DNS cache
        create.add(new WinCmd("ipconfig /flushdns"));
        destroy.add(new WinCmd("ipconfig /flushdns"));
    }
#else
    // Configure TAP adapter for pre-Vista
    // Currently we don't support IPv6 on pre-Vista
    void adapter_config(HANDLE th,
                        const std::wstring &openvpn_app_path,
                        const Util::TapNameGuidPair &tap,
                        const TunBuilderCapture &pull,
                        const bool l2_post,
                        ActionList &create,
                        ActionList &destroy,
                        std::ostream &os)
    {
        // Windows interface index
        const std::string tap_index_name = tap.index_or_name();

        // get default gateway
        const Util::DefaultGateway gw;

        // set local4 to point to IPv4 route configurations
        const TunBuilderCapture::RouteAddress *local4 = pull.vpn_ipv4();

        // This section skipped on layer 2 post-config
        if (!l2_post)
        {
            // Make sure the TAP adapter is set for DHCP
            {
                const Util::IPAdaptersInfo ai;
                if (!ai.is_dhcp_enabled(tap.index))
                {
                    os << "TAP: DHCP is disabled, attempting to enable" << std::endl;
                    ActionList::Ptr cmds(new ActionList());
                    cmds->add(new Util::ActionEnableDHCP(tap));
                    cmds->execute(os);
                }
            }

            // Set IPv4 Interface
            if (local4)
            {
                // Process ifconfig and topology
                const std::string netmask = IPv4::Addr::netmask_from_prefix_len(local4->prefix_length).to_string();
                const IP::Addr localaddr = IP::Addr::from_string(local4->address);
                if (local4->net30)
                    Util::tap_configure_topology_net30(th, localaddr, local4->prefix_length);
                else
                    Util::tap_configure_topology_subnet(th, localaddr, local4->prefix_length);
            }

            // On pre-Vista, set up TAP adapter DHCP masquerade for
            // configuring adapter properties.
            {
                os << "TAP: configure DHCP masquerade" << std::endl;
                Util::TAPDHCPMasquerade dhmasq;
                dhmasq.init_from_capture(pull);
                dhmasq.ioctl(th);
            }

            // set TAP media status to CONNECTED
            if (tun_type_ == TapWindows6)
                Util::tap_set_media_status(th, true);

            // ARP
            Util::flush_arp(tap.index, os);

            // DHCP release/renew
            {
                const Util::InterfaceInfoList ii;
                Util::dhcp_release(ii, tap.index, os);
                Util::dhcp_renew(ii, tap.index, os);
            }

            // Wait for TAP adapter to come up
            {
                bool succeed = false;
                const Util::IPNetmask4 vpn_addr(pull, "VPN IP");
                for (int i = 1; i <= 30; ++i)
                {
                    os << '[' << i << "] waiting for TAP adapter to receive DHCP settings..." << std::endl;
                    const Util::IPAdaptersInfo ai;
                    if (ai.is_up(tap.index, vpn_addr))
                    {
                        succeed = true;
                        break;
                    }
                    ::Sleep(1000);
                }
                if (!succeed)
                    throw tun_win_setup("TAP adapter DHCP handshake failed");
            }

            // Pre route-add sleep
            os << "Sleeping 5 seconds prior to adding routes..." << std::endl;
            ::Sleep(5000);
        }

        // Process routes
        for (auto &route : pull.add_routes)
        {
            const std::string metric = route_metric_opt(pull, route, MT_ROUTE);
            if (!route.ipv6)
            {
                if (local4)
                {
                    const std::string netmask = IPv4::Addr::netmask_from_prefix_len(route.prefix_length).to_string();
                    create.add(new WinCmd("route ADD " + route.address + " MASK " + netmask + ' ' + local4->gateway + metric));
                    destroy.add(new WinCmd("route DELETE " + route.address + " MASK " + netmask + ' ' + local4->gateway));
                }
                else
                    throw tun_win_setup("IPv4 routes pushed without IPv4 ifconfig");
            }
        }

        // Process exclude routes
        if (!pull.exclude_routes.empty())
        {
            if (gw.defined())
            {
                for (auto &route : pull.exclude_routes)
                {
                    const std::string metric = route_metric_opt(pull, route, MT_ROUTE);
                    if (!route.ipv6)
                    {
                        const std::string netmask = IPv4::Addr::netmask_from_prefix_len(route.prefix_length).to_string();
                        create.add(new WinCmd("route ADD " + route.address + " MASK " + netmask + ' ' + gw.gateway_address() + metric));
                        destroy.add(new WinCmd("route DELETE " + route.address + " MASK " + netmask + ' ' + gw.gateway_address()));
                    }
                }
            }
            else
                os << "NOTE: exclude routes error: cannot detect default gateway" << std::endl;
        }

        // Process IPv4 redirect-gateway
        if (pull.reroute_gw.ipv4)
        {
            // add server bypass route
            if (gw.defined())
            {
                if (!pull.remote_address.ipv6)
                {
                    create.add(new WinCmd("route ADD " + pull.remote_address.address + " MASK 255.255.255.255 " + gw.gateway_address()));
                    destroy.add(new WinCmd("route DELETE " + pull.remote_address.address + " MASK 255.255.255.255 " + gw.gateway_address()));
                }
            }
            else
                throw tun_win_setup("redirect-gateway error: cannot detect default gateway");

            create.add(new WinCmd("route ADD 0.0.0.0 MASK 128.0.0.0 " + local4->gateway));
            create.add(new WinCmd("route ADD 128.0.0.0 MASK 128.0.0.0 " + local4->gateway));
            destroy.add(new WinCmd("route DELETE 0.0.0.0 MASK 128.0.0.0 " + local4->gateway));
            destroy.add(new WinCmd("route DELETE 128.0.0.0 MASK 128.0.0.0 " + local4->gateway));
        }

        // flush DNS cache
        // create.add(new WinCmd("net stop dnscache"));
        // create.add(new WinCmd("net start dnscache"));
        create.add(new WinCmd("ipconfig /flushdns"));
        // create.add(new WinCmd("ipconfig /registerdns"));
        destroy.add(new WinCmd("ipconfig /flushdns"));
    }
#endif

    void adapter_config_l2(HANDLE th,
                           const std::wstring &openvpn_app_path,
                           const Util::TapNameGuidPair &tap,
                           const TunBuilderCapture &pull,
                           ActionList &create,
                           ActionList &destroy,
                           std::ostream &os)
    {
        // Make sure the TAP adapter is set for DHCP
        {
            const Util::IPAdaptersInfo ai;
            if (!ai.is_dhcp_enabled(tap.index))
            {
                os << "TAP: DHCP is disabled, attempting to enable" << std::endl;
                ActionList::Ptr cmds(new ActionList());
                cmds->add(new Util::ActionEnableDHCP(tap));
                cmds->execute(os);
            }
        }

        // set TAP media status to CONNECTED
        Util::tap_set_media_status(th, true);

        // ARP
        Util::flush_arp(tap.index, os);

        // We must do DHCP release/renew in a background thread
        // so the foreground can forward the DHCP negotiation packets
        // over the tunnel.
        l2_thread.reset(new std::thread([tap, logwrap = Log::Context::Wrapper()]()
                                        {
	      Log::Context logctx(logwrap);
	      ::Sleep(250);
	      const Util::InterfaceInfoList ii;
	      {
		std::ostringstream os;
		Util::dhcp_release(ii, tap.index, os);
		OPENVPN_LOG_STRING(os.str());
	      }
	      ::Sleep(250);
	      {
		std::ostringstream os;
		Util::dhcp_renew(ii, tap.index, os);
		OPENVPN_LOG_STRING(os.str());
	      } }));
    }

    enum MetricType
    {
        MT_ROUTE,
        MT_NETSH,
        MT_IFACE,
    };

    static std::string route_metric_opt(const TunBuilderCapture &pull,
                                        const TunBuilderCapture::RouteBase &route,
                                        const MetricType mt)
    {
        int metric = pull.route_metric_default;
        if (route.metric >= 0)
            metric = route.metric;
        if (metric >= 0)
        {
            switch (mt)
            {
            case MT_ROUTE:
                return " METRIC " + std::to_string(metric); // route command form
            case MT_NETSH:
                return " metric=" + std::to_string(metric); // "netsh interface ip[v6] add route" form
            case MT_IFACE:
                return " gwmetric=" + std::to_string(metric); // "netsh interface ip set address" form
            }
        }
        return "";
    }

#if _WIN32_WINNT >= 0x0600 // Vista+
    TunWin::WFPContext::Ptr wfp{new TunWin::WFPContext};
#endif

    std::unique_ptr<std::thread> l2_thread;
    std::unique_ptr<L2State> l2_state;

    DWORD vpn_interface_index_ = DWORD(-1);
    ActionList::Ptr remove_cmds;

    AsioTimer delete_route_timer;

    const Type tun_type_;
    Util::TapNameGuidPair tap_;
    bool allow_local_dns_resolvers = false;
};
} // namespace TunWin
} // namespace openvpn

#endif
