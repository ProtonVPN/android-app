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

#ifndef OPENVPN_TUN_BUILDER_BASE_H
#define OPENVPN_TUN_BUILDER_BASE_H

#include <string>

#ifdef ENABLE_OVPNDCO
#include <openvpn/dco/key.hpp>
#endif

namespace openvpn {
  class TunBuilderBase
  {
  public:
    // Tun builder methods, loosely based on the Android VpnService.Builder
    // abstraction.  These methods comprise an abstraction layer that
    // allows the OpenVPN C++ core to call out to external methods for
    // establishing the tunnel, adding routes, etc.

    // All methods returning bool use the return
    // value to indicate success (true) or fail (false).
    // tun_builder_new() should be called first, then arbitrary setter methods,
    // and finally tun_builder_establish to return the socket descriptor
    // for the session.  IP addresses are pre-validated before being passed to
    // these methods.
    // This interface is based on Android's VpnService.Builder.

    // Callback to construct a new tun builder
    // Should be called first.
    virtual bool tun_builder_new()
    {
      return false;
    }

    // Optional callback that indicates OSI layer, should be 2 or 3.
    // Defaults to 3.
    virtual bool tun_builder_set_layer(int layer)
    {
      return true;
    }

    // Callback to set address of remote server
    // Never called more than once per tun_builder session.
    virtual bool tun_builder_set_remote_address(const std::string& address, bool ipv6)
    {
      return false;
    }

    // Callback to add network address to VPN interface
    // May be called more than once per tun_builder session
    virtual bool tun_builder_add_address(const std::string& address,
					 int prefix_length,
					 const std::string& gateway, // optional
					 bool ipv6,
					 bool net30)
    {
      return false;
    }

    // Optional callback to set default value for route metric.
    // Guaranteed to be called before other methods that deal
    // with routes such as tun_builder_add_route and
    // tun_builder_reroute_gw.  Route metric is ignored
    // if < 0.
    virtual bool tun_builder_set_route_metric_default(int metric)
    {
      return true;
    }

    // Callback to reroute default gateway to VPN interface.
    // ipv4 is true if the default route to be added should be IPv4.
    // ipv6 is true if the default route to be added should be IPv6.
    // flags are defined in RGWFlags (rgwflags.hpp).
    // Never called more than once per tun_builder session.
    virtual bool tun_builder_reroute_gw(bool ipv4,
					bool ipv6,
					unsigned int flags)
    {
      return false;
    }

    // Callback to add route to VPN interface
    // May be called more than once per tun_builder session
    // metric is optional and should be ignored if < 0
    virtual bool tun_builder_add_route(const std::string& address,
				       int prefix_length,
				       int metric,
				       bool ipv6)
    {
      return false;
    }

    // Callback to exclude route from VPN interface
    // May be called more than once per tun_builder session
    // metric is optional and should be ignored if < 0
    virtual bool tun_builder_exclude_route(const std::string& address,
					   int prefix_length,
					   int metric,
					   bool ipv6)
    {
      return false;
    }

    // Callback to add DNS server to VPN interface
    // May be called more than once per tun_builder session
    // If reroute_dns is true, all DNS traffic should be routed over the
    // tunnel, while if false, only DNS traffic that matches an added search
    // domain should be routed.
    // Guaranteed to be called after tun_builder_reroute_gw.
    virtual bool tun_builder_add_dns_server(const std::string& address, bool ipv6)
    {
      return false;
    }

    // Callback to add search domain to DNS resolver
    // May be called more than once per tun_builder session
    // See tun_builder_add_dns_server above for description of
    // reroute_dns parameter.
    // Guaranteed to be called after tun_builder_reroute_gw.
    virtual bool tun_builder_add_search_domain(const std::string& domain)
    {
      return false;
    }

    // Callback to set MTU of the VPN interface
    // Never called more than once per tun_builder session.
    virtual bool tun_builder_set_mtu(int mtu)
    {
      return false;
    }

    // Callback to set the session name
    // Never called more than once per tun_builder session.
    virtual bool tun_builder_set_session_name(const std::string& name)
    {
      return false;
    }

    // Callback to add a host which should bypass the proxy
    // May be called more than once per tun_builder session
    virtual bool tun_builder_add_proxy_bypass(const std::string& bypass_host)
    {
      return false;
    }

    // Callback to set the proxy "Auto Config URL"
    // Never called more than once per tun_builder session.
    virtual bool tun_builder_set_proxy_auto_config_url(const std::string& url)
    {
      return false;
    }

    // Callback to set the HTTP proxy
    // Never called more than once per tun_builder session.
    virtual bool tun_builder_set_proxy_http(const std::string& host, int port)
    {
      return false;
    }

    // Callback to set the HTTPS proxy
    // Never called more than once per tun_builder session.
    virtual bool tun_builder_set_proxy_https(const std::string& host, int port)
    {
      return false;
    }

    // Callback to add Windows WINS server to VPN interface.
    // WINS server addresses are always IPv4.
    // May be called more than once per tun_builder session.
    // Guaranteed to be called after tun_builder_reroute_gw.
    virtual bool tun_builder_add_wins_server(const std::string& address)
    {
      return false;
    }

    // Optional callback that indicates whether IPv6 traffic should be
    // blocked, to prevent unencrypted IPv6 packet leakage when the
    // tunnel is IPv4-only, but the local machine has IPv6 connectivity
    // to the internet.  Enabled by "block-ipv6" config var.
    virtual bool tun_builder_set_block_ipv6(bool block_ipv6)
    {
      return true;
    }

    // Optional callback to set a DNS suffix on tun/tap adapter.
    // Currently only implemented on Windows, where it will
    // set the "Connection-specific DNS Suffix" property on
    // the TAP driver.
    virtual bool tun_builder_set_adapter_domain_suffix(const std::string& name)
    {
      return true;
    }

    // Callback to establish the VPN tunnel, returning a file descriptor
    // to the tunnel, which the caller will henceforth own.  Returns -1
    // if the tunnel could not be established.
    // Always called last after tun_builder session has been configured.
    virtual int tun_builder_establish()
    {
      return -1;
    }

    // Return true if tun interface may be persisted, i.e. rolled
    // into a new session with properties untouched.  This method
    // is only called after all other tests of persistence
    // allowability succeed, therefore it can veto persistence.
    // If persistence is ultimately enabled,
    // tun_builder_establish_lite() will be called.  Otherwise,
    // tun_builder_establish() will be called.
    virtual bool tun_builder_persist()
    {
      return true;
    }

    // When the exclude local network option is enabled this
    // function is called to get a list of local networks so routes
    // to exclude them from the VPN network are generated
    // This should be a list of CIDR networks (e.g. 192.168.0.0/24)
    virtual const std::vector<std::string> tun_builder_get_local_networks(bool ipv6)
    {
      return {};
    }

    // Indicates a reconnection with persisted tun state.
    virtual void tun_builder_establish_lite()
    {
    }

    // Indicates that tunnel is being torn down.
    // If disconnect == true, then the teardown is occurring
    // prior to final disconnect.
    virtual void tun_builder_teardown(bool disconnect) {}

    virtual ~TunBuilderBase() {}

#ifdef ENABLE_OVPNDCO
    /**
     * Enable ovpn-dco support
     *
     * @param transport_fd file descriptor of client-created transport socket
     * @param dev_name name of ovpn-dco net device, which should be created by client
     * @return int file descriptor of socket used to direct communication with ovpn-dco kernel module
     */
    virtual int tun_builder_dco_enable(int transport_fd, const std::string& dev_name)
    {
      return -1;
    }

    /**
     * Add peer information to kernel module
     *
     * @param local_ip local ip
     * @param local_port local port
     * @param remote_ip remote ip
     * @param remote_port remote port
     */
    virtual void tun_builder_dco_new_peer(const std::string& local_ip, unsigned int local_port,
                                          const std::string& remote_ip, unsigned int remote_port)
    {
    }

    /**
     * Set peer properties. Currently used for keepalive settings.
     *
     * @param keepalive_interval how often to send ping packet in absence of traffic
     * @param keepalive_timeout when to trigger keepalive_timeout in absence of traffic
     */
    virtual void tun_builder_dco_set_peer(int keepalive_interval, int keepalive_timeout)
    {
    }

    /**
     * Inject new key into kernel module
     *
     * @param key_slot \c OVPN_KEY_SLOT_PRIMARY or \c OVPN_KEY_SLOT_SECONDARY
     * @param kc pointer to \c KeyConfig struct which contains key data
     */
    virtual void tun_builder_dco_new_key(unsigned int key_slot, const KoRekey::KeyConfig* kc)
    {
    }

    /**
     * Swap keys between primary and secondary slot. Called
     * by client as part of rekeying logic to promote and demote keys.
     *
     */
    virtual void tun_builder_dco_swap_keys()
    {
    }

    /**
     * Remove key from key slot.
     *
     * @param key_slot OVPN_KEY_SLOT_PRIMARY or OVPN_KEY_SLOT_SECONDARY
     */
    virtual void tun_builder_dco_del_key(unsigned int key_slot)
    {
    }

    /**
     * Establishes VPN tunnel. Should be called last after tun_builder
     * session has been configured.
     *
     */
    virtual void tun_builder_dco_establish()
    {
    }
#endif // ENABLE_DCO

  };
}

#endif