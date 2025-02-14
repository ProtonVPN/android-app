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

#ifndef OPENVPN_TUN_BUILDER_BASE_H
#define OPENVPN_TUN_BUILDER_BASE_H

#include <string>

#ifdef ENABLE_OVPNDCO
#include <openvpn/dco/key.hpp>
#endif

#include <openvpn/addr/ip.hpp>
#include <openvpn/client/dns_options.hpp>

namespace openvpn {

/**
 * @brief TunBuilder methods, loosely based on the Android VpnService.Builder abstraction.
 *
 * These methods comprise an abstraction layer that allows the OpenVPN C++ core to call
 * out to external methods for establishing the tunnel, adding routes, etc.
 *
 * All methods returning bool use the return value to indicate success (true) or failure (false).
 *
 * `tun_builder_new()` should be called first, then arbitrary setter methods, and finally
 * `tun_builder_establish()` to return the socket descriptor for the session.
 *
 * IP addresses are pre-validated before being passed to these methods.
 *
 * This interface is based on Android's VpnService.Builder.
 */
class TunBuilderBase
{
  public:
    /**
     * @brief Callback to construct a new TunBuilder.
     * This function should be called first.
     *
     * @return `true` if the TunBuilder was successfully created, `false` otherwise
     */
    virtual bool tun_builder_new()
    {
        return false;
    }

    /**
     * @brief Optional callback that indicates OSI layer to be used.
     *
     * @details This function sets the OSI layer to be used.
     * Possible values are 2 (TAP), 3 (TUN) or 0.
     * Currently, we only support 3 (TUN).
     *
     * @param layer The OSI layer to set, should be 2 (TAP), 3 (TUN) or 0.
     * Currently only 3 (TUN) is supported.
     *
     * @return `true` if the layer was successfully set, `false` otherwise
     */
    virtual bool tun_builder_set_layer(int layer)
    {
        return true;
    }

    /**
     * @brief Callback to set the address of the remote server.
     *
     * This function is invoked to set the remote server's address. It will not
     * be called more than once in a single TunBuilder session.
     *
     * @param address Specifies the address of the remote server.
     * @param ipv6 Boolean indicating whether the given address is an IPv6 address.
     *
     * @return `true` if the address was successfully set, `false` otherwise
     */
    virtual bool tun_builder_set_remote_address(const std::string &address, bool ipv6)
    {
        return false;
    }

    /**
     * @brief Callback to add a network address to the VPN interface.
     *
     * This method may be called multiple times within a single TunBuilder session.
     *
     * @param address The network address to add.
     * @param prefix_length The prefix length of the network address.
     * @param gateway An optional gateway address.
     * @param ipv6 A boolean indicating whether the address is IPv6.
     * @param net30 A boolean indicating whether to use a net30 topology.
     *
     * @return `true` if the address was successfully added, `false` otherwise
     */
    virtual bool tun_builder_add_address(const std::string &address,
                                         int prefix_length,
                                         const std::string &gateway, // optional
                                         bool ipv6,
                                         bool net30)
    {
        return false;
    }

    /**
     * @brief Optional callback to set default value for route metric.
     *
     * This method is guaranteed to be called before other methods
     * that deal with routes, such as `tun_builder_add_route()` and
     * `tun_builder_reroute_gw()`. The route metric is ignored if its
     * value is less than 0.
     *
     * @param metric The metric value to set for the route.
     *
     * @return `true` if the route metric was successfully set
     */
    virtual bool tun_builder_set_route_metric_default(int metric)
    {
        return true;
    }

    /**
     * @brief Callback to reroute the default gateway to the VPN interface.
     *
     * This function is used to add the default route for either IPv4, IPv6, or both.
     * It is called only once per TunBuilder session.
     *
     * @param ipv4 Set to `true` if the default route to be added should be IPv4.
     * @param ipv6 Set to `true` if the default route to be added should be IPv6.
     * @param flags Additional flags defined in RGWFlags (see rgwflags.hpp for details).
     *
     * @return `true` if the rerouting was successful, `false` otherwise
     */
    virtual bool tun_builder_reroute_gw(bool ipv4,
                                        bool ipv6,
                                        unsigned int flags)
    {
        return false;
    }

    /**
     * @brief Callback to add a route to the VPN interface.
     *
     * This method may be called multiple times per TunBuilder session.
     *
     * @param address The address to add the route for.
     * @param prefix_length The prefix length associated with the route.
     * @param metric The metric for the route. It is optional and should be ignored if it is less than 0.
     * @param ipv6 Boolean indicating whether the address is IPv6.
     *
     * @return `true` if the route was successfully added, `false` otherwise
     */
    virtual bool tun_builder_add_route(const std::string &address,
                                       int prefix_length,
                                       int metric,
                                       bool ipv6)
    {
        return false;
    }

    /**
     * @brief Callback to exclude route from VPN interface.
     *
     * This method may be called more than once per TunBuilder session.
     *
     * @param address The IP address for the route to be excluded.
     * @param prefix_length The prefix length for the IP address.
     * @param metric The route metric. This parameter should be ignored if it is less than 0.
     * @param ipv6 A boolean flag indicating whether the IP address is IPv6 or not.
     *
     * @return `true` if the route was successfully excluded, `false` otherwise
     */
    virtual bool tun_builder_exclude_route(const std::string &address,
                                           int prefix_length,
                                           int metric,
                                           bool ipv6)
    {
        return false;
    }

    /**
     * @brief Callback to set DNS related options to VPN interface.
     *
     * Unlike others, this function is called only once and overrides when called multiple times.
     *
     * @param dns A reference to the DnsOptions object containing the DNS options.
     *
     * @return `true` if the DNS options were successfully added, `false` otherwise
     */
    virtual bool tun_builder_set_dns_options(const DnsOptions &dns)
    {
        return false;
    }

    /**
     * @brief Callback to set the MTU of the VPN interface.
     *
     * This function sets the Maximum Transmission Unit (MTU) of the virtual
     * private network (VPN) interface. It's designed to be called not more
     * than once per TunBuilder session.
     *
     * @param mtu The MTU size to set.
     *
     * @return Returns `true` if the MTU was successfully set, `false` otherwise
     */
    virtual bool tun_builder_set_mtu(int mtu)
    {
        return false;
    }

    /**
     * @brief Sets the session name for the TunBuilder.
     *
     * This function is a callback that sets the session name. It is guaranteed to
     * be called no more than once per TunBuilder session.
     *
     * @param name A string representing the session name.
     *
     * @return Returns `true` if the session name was successfully set, `false` otherwise
     */
    virtual bool tun_builder_set_session_name(const std::string &name)
    {
        return false;
    }

    /**
     * @brief Callback to add a host which should bypass the proxy.
     *
     * This method can be called multiple times within the same TunBuilder session.
     *
     * @param bypass_host A string representing the host that should bypass the proxy.
     *
     * @return Returns `true` if the bypass host was successfully added, `false` otherwise
     */
    virtual bool tun_builder_add_proxy_bypass(const std::string &bypass_host)
    {
        return false;
    }

    /**
     * @brief Callback to set the proxy "Auto Config URL".
     *
     * This function is a callback used to set the proxy "Auto Config URL".
     * It is never called more than once per TunBuilder session.
     *
     * @param url The URL string for the proxy autoconfiguration.
     *
     * @return Returns `true` if the proxy auto config URL was successfully set, `false` otherwise
     */
    virtual bool tun_builder_set_proxy_auto_config_url(const std::string &url)
    {
        return false;
    }

    /**
     * @brief Callback to set the HTTP proxy.
     *
     * This function acts as a callback to configure the HTTP proxy settings.
     * It is never called more than once per TunBuilder session.
     *
     * @param host The hostname or IP address of the HTTP proxy.
     * @param port The port number of the HTTP proxy.
     *
     * @return Returns `true` if the HTTP proxy was successfully set, `false` otherwise
     */
    virtual bool tun_builder_set_proxy_http(const std::string &host, int port)
    {
        return false;
    }

    /**
     * @brief Set the HTTPS proxy for the TunBuilder session.
     *
     * This method sets the HTTPS proxy using the given host and port.
     * It is called at most once during a TunBuilder session.
     *
     * @param host The hostname of the HTTPS proxy.
     * @param port The port number of the HTTPS proxy.
     *
     * @return `true` if the HTTPS proxy was successfully set, `false` otherwise
     */
    virtual bool tun_builder_set_proxy_https(const std::string &host, int port)
    {
        return false;
    }

    /**
     * @brief Callback to add a Windows WINS server to the VPN interface.
     *
     * This function is called to add a WINS server address to the VPN interface.
     * WINS server addresses are always IPv4.
     *
     * @note This function may be called more than once per TunBuilder session.
     * It is guaranteed to be called after `tun_builder_reroute_gw()`.
     *
     * @param address The IPv4 address of the WINS server to be added.
     *
     * @return `true` if the WINS server was successfully added, `false` otherwise
     */
    virtual bool tun_builder_add_wins_server(const std::string &address)
    {
        return false;
    }

    /**
     * @brief Indicates whether traffic of a certain address family
     * (AF_INET or AF_INET6) should be blocked or allowed.
     *
     * This is used to prevent unencrypted packet leakage when the tunnel
     * is IPv4-only or IPv6-only, but the local machine has connectivity
     * with the other protocol to the internet.
     *
     * This setting is controlled by the "block-ipv6" and "block-ipv6"
     * configuration variables. If addresses are added for a family,
     * this setting should be ignored for that family.
     *
     * @remark See also Android's VPNService.Builder::allowFamily method.
     *
     * @param af The address family (AF_INET or AF_INET6).
     * @param allow A boolean indicating whether the address family should be allowed.
     *
     * @return `true` if it was successfully set, `false` otherwise
     */
    virtual bool tun_builder_set_allow_family(int af, bool allow)
    {
        return true;
    }

    /**
     * @brief Optional callback that indicates whether local DNS traffic
     * should be blocked or allowed to prevent DNS queries from leaking
     * while the tunnel is connected.
     *
     * Note that this option is only relevant on Windows when the
     * `--dns` option is used. If DNS is set via `--dhcp-option`, port 53
     * is always blocked for backwards compatibility reasons.
     *
     * @param allow Determines whether to allow (`true`) or block (`false`) local DNS traffic.
     *
     * @return `true` if it was successfully set, `false` otherwise
     */
    virtual bool tun_builder_set_allow_local_dns(bool allow)
    {
        return true;
    }

    /**
     * @brief Callback to establish the VPN tunnel.
     *
     * This method returns a file descriptor to the tunnel, which the caller will henceforth own.
     * It returns -1 if the tunnel could not be established. This function is always called last
     * after the TunBuilder session has been configured.
     *
     * @return File descriptor to the tunnel, or -1 if the tunnel could not be established
     */
    virtual int tun_builder_establish()
    {
        return -1;
    }

    /**
     * @brief Determines if the TUN interface can be persisted.
     *
     * This method returns `true` if the TUN interface may be persisted –
     * rolled into a new session with properties unchanged. This method
     * is invoked only after all other tests for persistence allowability succeed;
     * therefore, it has the ability to veto the persistence.
     *
     * If persistence is ultimately enabled, `tun_builder_establish_lite()` will
     * be called. Otherwise, `tun_builder_establish()` will be called.
     *
     * @return true if the tun interface can be persisted, `false` otherwise
     */
    virtual bool tun_builder_persist()
    {
        return true;
    }

    /**
     * @brief Retrieves a list of local networks to exclude from the VPN network.
     *
     * When the exclude local network option is enabled, this function is called
     * to get a list of local networks. Routes are then generated to exclude these
     * networks from the VPN network.
     *
     * @param ipv6 Indicates whether to retrieve IPv6 networks (`true`) or IPv4 networks (`false`).
     * @return A vector containing CIDR representations of the local networks
     * (e.g., "192.168.0.0/24")
     */
    virtual std::vector<std::string> tun_builder_get_local_networks(bool ipv6)
    {
        return {};
    }

    /**
     * @brief Indicates a reconnection with persisted TUN state.
     *
     * This function is responsible for handling reconnections
     * while maintaining the persistent TUN state.
     */
    virtual void tun_builder_establish_lite()
    {
    }

    /**
     * @brief Indicates that tunnel is being torn down.
     *
     * This function is called to indicate that the tunnel is being torn down.
     *
     * @param disconnect If `true`, the teardown is occurring prior to the final disconnect.
     */
    virtual void tun_builder_teardown(bool disconnect)
    {
    }
    /**
     * @brief Virtual destructor for the TunBuilderBase class.
     *
     * Ensures derived classes can clean up resources properly.
     */
    virtual ~TunBuilderBase() = default;

#ifdef ENABLE_OVPNDCO
    /**
     * Check if ovpn-dco kernel module is available
     *
     * @return bool indicating whether the ovpn-dco module is loaded
     */
    virtual bool tun_builder_dco_available()
    {
        return false;
    }

    /**
     * Enable ovpn-dco support
     *
     * @param dev_name name of ovpn-dco net device, which should be created by client
     * @return int file descriptor of socket used to direct communication with ovpn-dco kernel module
     */
    virtual int tun_builder_dco_enable(const std::string &dev_name)
    {
        return -1;
    }

    /**
     * Add peer information to kernel module
     *
     * @param peer_id Peer ID of the peer being created
     * @param transport_fd socket to be used to communicate with the peer
     * @param sa sockaddr object representing the remote endpoint
     * @param salen length of sa (either sizeof(sockaddr_in) or sizeof(sockaddr_in6)
     * @vpn4 IPv4 address associated with this peer in the tunnel
     * @vpn6 IPv6 address associated with this peer in the tunnel
     */
    virtual void tun_builder_dco_new_peer(uint32_t peer_id,
                                          uint32_t transport_fd,
                                          struct sockaddr *sa,
                                          socklen_t salen,
                                          IPv4::Addr &vpn4,
                                          IPv6::Addr &vpn6)
    {
    }

    /**
     * Set peer properties. Currently used for keepalive settings.
     *
     * @param peer_id ID of the peer whose properties have to be modified
     * @param keepalive_interval how often to send ping packet in absence of traffic
     * @param keepalive_timeout when to trigger keepalive_timeout in absence of traffic
     */
    virtual void tun_builder_dco_set_peer(uint32_t peer_id, int keepalive_interval, int keepalive_timeout)
    {
    }

    /**
     * Delete an existing peer.
     *
     * @param peer_id the ID of the peer to delete
     * @throws netlink_error thrown if error occurs during sending netlink message
     */
    virtual void tun_builder_dco_del_peer(uint32_t peer_id)
    {
    }

    /**
     * Retrieve the status of an existing peer.
     *
     * @param peer_id the ID of the peer to query
     * @param sync if true the netlink invocation will be synchronous
     * @throws netlink_error thrown if error occurs while sending netlink message
     */
    virtual void tun_builder_dco_get_peer(uint32_t peer_id, bool sync)
    {
    }

    /**
     * Inject new key into kernel module
     *
     * @param key_slot \c OVPN_KEY_SLOT_PRIMARY or \c OVPN_KEY_SLOT_SECONDARY
     * @param kc pointer to \c KeyConfig struct which contains key data
     */
    virtual void tun_builder_dco_new_key(unsigned int key_slot, const KoRekey::KeyConfig *kc)
    {
    }

    /**
     * Swap keys between primary and secondary slot. Called
     * by client as part of rekeying logic to promote and demote keys.
     *
     * @param peer_id the ID of the peer whose keys have to be swapped
     */
    virtual void tun_builder_dco_swap_keys(uint32_t peer_id)
    {
    }

    /**
     * Remove key from key slot.
     *
     * @param peer_id the ID of the peer whose keys has to be deleted
     * @param key_slot OVPN_KEY_SLOT_PRIMARY or OVPN_KEY_SLOT_SECONDARY
     */
    virtual void tun_builder_dco_del_key(uint32_t peer_id, unsigned int key_slot)
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
#endif // ENABLE_OVPNDCO
};
} // namespace openvpn

#endif
