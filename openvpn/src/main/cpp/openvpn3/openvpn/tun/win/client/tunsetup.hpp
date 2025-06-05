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
//

// Client tun setup for Windows

#pragma once

#include <string>
#include <sstream>
#include <ostream>
#include <memory>
#include <utility>
#include <thread>
#include <algorithm>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/wstring.hpp>
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
#include <openvpn/tun/win/dns.hpp>
#include <openvpn/tun/win/wfp.hpp>
#endif

#include <versionhelpers.h>

// use IP Helper on Windows by default
#ifdef OPENVPN_USE_NETSH
#define TUNWINDOWS Util::TunNETSH
#else
#define TUNWINDOWS Util::TunIPHELPER
#endif

namespace openvpn::TunWin {
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

    /**
     * @brief Set the process id to be used with the NPRT rules
     *
     * The NRPT c'tor expects a process id parameter, which is used
     * internally. This function can be used if you want that pid to
     * be different from the current process id, e.g. if you are doing
     * the setup for a different process, like in the agent.
     *
     * @param process_id    The process id used with the NRPT class
     */
    void set_process_id(DWORD process_id)
    {
        process_id_ = process_id;
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
    HANDLE establish(const TunBuilderCapture &pull,
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
    bool l2_ready(const TunBuilderCapture &pull) override
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
    void l2_finish(const TunBuilderCapture &pull,
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

    void destroy(std::ostream &os) override // defined by DestructorBase
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

        vpn_interface_index_ = INVALID_ADAPTER_INDEX;
    }

    virtual ~Setup()
    {
        std::ostringstream os;
        destroy(os);
    }

    DWORD vpn_interface_index() const override
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
        UseDNS() = default;

        UseDNS(const TunBuilderCapture &pull)
        {
            if (pull.dns_options.servers.empty())
                return;
            for (const auto &ip : pull.dns_options.servers.begin()->second.addresses)
                add(ip.address, pull);
        }

        static bool enabled(const std::string &address,
                            const TunBuilderCapture &pull)
        {
            return IP::Addr(address).is_ipv6() && pull.block_ipv6 ? false : true;
        }

        int add(const std::string &address,
                const TunBuilderCapture &pull)
        {
            if (enabled(address, pull))
                return indices[IP::Addr(address).is_ipv6() ? 1 : 0]++;
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

    /**
     * @brief Set the DNS server addresses with the VPN adapter
     *
     * @param create            reference to create ActionList
     * @param destroy           reference to destroy ActionList
     * @param itf_index_name    VPN interface index or name string ref
     * @param addresses         vector of server address strings
     * @param pull              reference to tunbuilder capture
     */
    void set_adapter_dns(ActionList &create,
                         ActionList &destroy,
                         const std::string &itf_index_name,
                         const std::vector<std::string> &addresses,
                         const TunBuilderCapture &pull)
    {
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

        // fix for vista and dnsserver vs win7+ dnsservers
        std::string dns_servers_cmd = "dnsservers";
        std::string validate_cmd = " validate=no";
        if (IsWindowsVistaOrGreater() && !IsWindows7OrGreater())
        {
            dns_servers_cmd = "dnsserver";
            validate_cmd = "";
        }

        UseDNS dc;
        for (const auto &address : addresses)
        {
            // 0-based index for specific IPv4/IPv6 protocol, or -1 if disabled
            const int count = dc.add(address, pull);
            if (count >= 0)
            {
                const std::string proto = IP::Addr(address).is_ipv6() ? "ipv6" : "ip";
                if (count)
                    create.add(new WinCmd("netsh interface " + proto + " add " + dns_servers_cmd + " " + itf_index_name + ' ' + address + " " + to_string(count + 1) + validate_cmd));
                else
                {
                    create.add(new WinCmd("netsh interface " + proto + " set " + dns_servers_cmd + " " + itf_index_name + " static " + address + " register=primary" + validate_cmd));
                    destroy.add(new WinCmd("netsh interface " + proto + " delete " + dns_servers_cmd + " " + itf_index_name + " all" + validate_cmd));
                }
            }
        }
    }

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
                // set high metric on interface so that rogue route which Windows creates (0.0.0.0/0)
                // won't affect anything
                create.add(new WinCmd("netsh interface ip set interface " + tap_index_name + " metric=9000"));

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
                WinCmd::Ptr cmd_delroute = new WinCmd("netsh interface ip delete route 0.0.0.0/0 " + tap_index_name + ' ' + local4->gateway + " store=active");

                // set lowest interface metric to make Windows use pushed DNS search domain
                WinCmd::Ptr cmd_setmetric = new WinCmd("netsh interface ip set interface " + tap_index_name + " metric=1");

                delete_route_timer.expires_after(Time::Duration::seconds(5));
                delete_route_timer.async_wait([self = Ptr(this),
                                               cmd_delroute = std::move(cmd_delroute),
                                               cmd_setmetric = std::move(cmd_setmetric)](const openvpn_io::error_code &error)
                                              {
                        if (!error)
                        {
                            std::ostringstream os;
                            cmd_delroute->execute(os);
                            cmd_setmetric->execute(os);
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
            const Util::BestGateway gw{AF_INET};
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
            ADDRESS_FAMILY af = pull.remote_address.ipv6 ? AF_INET6 : AF_INET;
            const Util::BestGateway gw{af, pull.remote_address.address, tap.index};

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

        // Process redirect-gateway "block-local" flag:
        // Block traffic on all interfaces but VPN and loopback
        const bool use_wfp = IsWindows8OrGreater();
        const bool block_local_traffic = (pull.reroute_gw.flags & RedirectGatewayFlags::RG_BLOCK_LOCAL) != 0;
        if (use_wfp && block_local_traffic && !openvpn_app_path.empty())
        {
            WFP::Block block_type = (allow_local_dns_resolvers ? WFP::Block::AllButLocalDns : WFP::Block::All);
            create.add(new WFP::ActionBlock(openvpn_app_path, tap.index, block_type, wfp));
            destroy.add(new WFP::ActionUnblock(openvpn_app_path, tap.index, block_type, wfp));
        }

        // The process id for NRPT rules
        DWORD pid = process_id_ ? process_id_ : ::GetCurrentProcessId();

        // Process DNS related settings
        {
            if (!pull.dns_options.from_dhcp_options)
            {
                // apply DNS settings from --dns options
                std::vector<std::string> addresses;
                std::vector<std::string> split_domains;
                std::vector<std::wstring> wide_search_domains;
                std::string search_domains;
                bool dnssec = false;

                for (const auto &[priority, server] : pull.dns_options.servers)
                {
                    bool secure_transport = server.transport == DnsServer::Transport::HTTPS
                                            || server.transport == DnsServer::Transport::TLS;
                    bool custom_port = std::any_of(server.addresses.begin(),
                                                   server.addresses.end(),
                                                   [&](const DnsAddress &a)
                                                   { return a.port != 0 && a.port != 53; });
                    if (secure_transport || custom_port)
                    {
                        continue; // unsupported, try next server
                    }

                    // DNS server address(es)
                    for (const auto &addr : server.addresses)
                    {
                        addresses.push_back(addr.address);
                    }

                    // DNS server split domain(s)
                    for (const auto &dom : server.domains)
                    {
                        split_domains.push_back("." + dom.domain);
                    }

                    std::string delimiter;
                    for (const auto &domain : pull.dns_options.search_domains)
                    {
                        wide_search_domains.emplace_back(wstring::from_utf8(domain.to_string()));
                        search_domains.append(delimiter + domain.to_string());
                        delimiter = ",";
                    }

                    dnssec = server.dnssec == DnsServer::Security::Yes;
                    break;
                }

                // disconnect if we didn't find a compatible DNS server profile
                if (!pull.dns_options.servers.empty() && addresses.empty())
                {
                    throw tun_win_setup("no applicable DNS server config found");
                }

                if (!allow_local_dns_resolvers || !split_domains.empty())
                {
                    // To keep local resolvers working, only split rules must be created
                    create.add(new NRPT::ActionCreate(pid, split_domains, addresses, wide_search_domains, dnssec));
                    destroy.add(new NRPT::ActionDelete(pid));
                }
                else if (allow_local_dns_resolvers && pull.block_outside_dns)
                {
                    // Set pushed DNS servers with the adapter. In case the local resolver
                    // doesn't work the VPN DNS resolvers will serve as a fallback
                    set_adapter_dns(create, destroy, tap_index_name, addresses, pull);
                }

                create.add(new DNS::ActionCreate(tap.name, search_domains));
                destroy.add(new DNS::ActionDelete(tap.name, search_domains));

                // Apply changes to DNS settings
                create.add(new DNS::ActionApply());
                destroy.add(new DNS::ActionApply());

                // Use WFP for DNS leak protection unless local traffic is blocked already.
                // Block DNS on all interfaces except the TAP adapter.
                if (use_wfp && pull.block_outside_dns && !block_local_traffic && !openvpn_app_path.empty())
                {
                    WFP::Block block_type = (allow_local_dns_resolvers ? WFP::Block::DnsButAllowLocal : WFP::Block::Dns);
                    create.add(new WFP::ActionBlock(openvpn_app_path, tap.index, block_type, wfp));
                    destroy.add(new WFP::ActionUnblock(openvpn_app_path, tap.index, block_type, wfp));
                }
            }
            else
            {
                // apply DNS settings from --dhcp-options
                const bool use_nrpt = IsWindows8OrGreater();

                // count IPv4/IPv6 DNS servers
                const UseDNS dns(pull);
                const DnsServer &server = pull.dns_options.servers.begin()->second;

                // will DNS requests be split between VPN DNS server and local?
                const bool split_dns = (!server.domains.empty()
                                        && !(pull.reroute_gw.ipv4 && dns.ipv4())
                                        && !(pull.reroute_gw.ipv6 && dns.ipv6()));

                // add DNS servers via netsh
                if (!(use_nrpt && split_dns) && !l2_post)
                {
                    std::vector<std::string> addresses;
                    std::for_each(addresses.begin(), addresses.end(), [&addresses](const std::string &addr)
                                  { addresses.push_back(addr); });
                    set_adapter_dns(create, destroy, tap_index_name, addresses, pull);
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
                    std::vector<std::string> split_domains;

                    // Only add DNS routing suffixes if not rerouting gateway.
                    // Otherwise, route all DNS requests with wildcard (".").
                    if (split_dns)
                    {
                        for (const auto &sd : server.domains)
                        {
                            std::string dom = sd.domain;
                            if (!dom.empty())
                            {
                                // each DNS suffix must begin with '.'
                                if (dom[0] != '.')
                                    dom = "." + dom;
                                split_domains.push_back(std::move(dom));
                            }
                        }
                    }

                    // DNS server list
                    std::vector<std::string> dserv;
                    for (const auto &ip : server.addresses)
                        dserv.push_back(IP::Addr(ip.address).to_string());

                    // To keep local resolvers working, only split rules must be created
                    if (!allow_local_dns_resolvers || !split_domains.empty())
                    {
                        std::vector<std::wstring> wide_search_domains;
                        for (const auto &domain : pull.dns_options.search_domains)
                        {
                            wide_search_domains.emplace_back(wstring::from_utf8(domain.to_string()));
                        }
                        create.add(new NRPT::ActionCreate(pid, split_domains, dserv, wide_search_domains, false));
                        destroy.add(new NRPT::ActionDelete(pid));

                        // Apply changes to DNS settings
                        create.add(new DNS::ActionApply());
                        destroy.add(new DNS::ActionApply());
                    }
                }

                // Set a default TAP-adapter domain suffix using
                // "dhcp-option ADAPTER_DOMAIN_SUFFIX mycompany.com" directive.
                if (!pull.dns_options.search_domains.empty())
                {
                    // Only the first search domain is used
                    const std::string adapter_domain_suffix = pull.dns_options.search_domains[0].domain;
                    create.add(new Util::ActionSetAdapterDomainSuffix(adapter_domain_suffix, tap.guid));
                    destroy.add(new Util::ActionSetAdapterDomainSuffix("", tap.guid));
                }


                // Use WFP for DNS leak protection unless local traffic is blocked already.
                // Block DNS on all interfaces except the TAP adapter.
                if (use_wfp && !split_dns && !block_local_traffic
                    && !openvpn_app_path.empty() && (dns.ipv4() || dns.ipv6()))
                {
                    WFP::Block block_type = (allow_local_dns_resolvers ? WFP::Block::DnsButAllowLocal : WFP::Block::Dns);
                    create.add(new WFP::ActionBlock(openvpn_app_path, tap.index, block_type, wfp));
                    destroy.add(new WFP::ActionUnblock(openvpn_app_path, tap.index, block_type, wfp));
                }

                // flush DNS cache
                create.add(new WinCmd("ipconfig /flushdns"));
                destroy.add(new WinCmd("ipconfig /flushdns"));
            }
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
    }

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
    WFP::Context::Ptr wfp{new WFP::Context};
#endif

    std::unique_ptr<std::thread> l2_thread;
    std::unique_ptr<L2State> l2_state;

    DWORD vpn_interface_index_ = INVALID_ADAPTER_INDEX;
    ActionList::Ptr remove_cmds;

    AsioTimer delete_route_timer;

    const Type tun_type_;
    Util::TapNameGuidPair tap_;
    bool allow_local_dns_resolvers = false;
    DWORD process_id_ = 0;
};
} // namespace openvpn::TunWin
