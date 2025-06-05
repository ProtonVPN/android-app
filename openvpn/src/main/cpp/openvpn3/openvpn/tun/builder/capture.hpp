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

// An artificial TunBuilder object, used to log the tun builder settings,
// but doesn't actually configure anything.

#ifndef OPENVPN_TUN_BUILDER_CAPTURE_H
#define OPENVPN_TUN_BUILDER_CAPTURE_H

#include <string>
#include <sstream>
#include <vector>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/common/size.hpp>
#include <openvpn/common/hostport.hpp>
#include <openvpn/common/to_string.hpp>
#include <openvpn/common/jsonlib.hpp>
#include <openvpn/tun/builder/base.hpp>
#include <openvpn/client/rgopt.hpp>
#include <openvpn/client/dns.hpp>
#include <openvpn/addr/ip.hpp>
#include <openvpn/addr/route.hpp>
#include <openvpn/http/urlparse.hpp>
#include <openvpn/tun/layer.hpp>

#ifdef HAVE_JSON
#include <openvpn/common/jsonhelper.hpp>
#endif

namespace openvpn {
class TunBuilderCapture : public TunBuilderBase, public RC<thread_unsafe_refcount>
{
  public:
    using Ptr = RCPtr<TunBuilderCapture>;

    // builder data classes

    /**
     * @brief Represents a remote %IP address with IPv4/IPv6 designation.
     * @details This class encapsulates an %IP address string along with a flag indicating
     * whether it's an %IPv6 address. It provides methods for validation, serialization,
     * and checking if the address is defined.
     */
    class RemoteAddress
    {
      public:
        std::string address;
        bool ipv6 = false;

        /**
         * @brief Returns a string representation of the remote address.
         * @details Formats the address as a string, appending " [IPv6]" suffix if
         * the address is %IPv6.
         * @return Formatted string representation of the address.
         */
        std::string to_string() const
        {
            std::string ret = address;
            if (ipv6)
                ret += " [IPv6]";
            return ret;
        }

        /**
         * @brief Checks if the address is defined.
         * @details An address is considered defined if its string representation is not empty.
         * @return @c true if the address is defined, @c false otherwise.
         */
        bool defined() const
        {
            return !address.empty();
        }

        /**
         * @brief Validates the %IP address format.
         * @details Uses the @c IP::Addr class to validate that the address string
         * represents a valid %IPv4 or %IPv6 address based on the @c ipv6 flag.
         * @param title A context string used in error messages if validation fails.
         */
        void validate(const std::string &title) const
        {
            IP::Addr(address, title, ipv6 ? IP::Addr::V6 : IP::Addr::V4);
        }

#ifdef HAVE_JSON
        /**
         * @brief Serializes the object to a JSON value.
         * @details Creates a JSON object with "address" and "ipv6" fields.
         * @return A @c Json::Value object representing this remote address.
         */
        Json::Value to_json() const
        {
            Json::Value root(Json::objectValue);
            root["address"] = Json::Value(address);
            root["ipv6"] = Json::Value(ipv6);
            return root;
        }

        /**
         * @brief Deserializes the object from a JSON value.
         * @details Populates the object's fields from the given JSON object.
         * @warning If input is not a valid JSON dictionary, the method will return without processing and without raising any errors. This silent behavior may cause unexpected results.
         * @param root The JSON value to deserialize from.
         * @param title A context string used in error messages if deserialization fails.
         */
        void from_json(const Json::Value &root, const std::string &title)
        {
            if (!json::is_dict(root, title))
                return;
            json::to_string(root, address, "address", title);
            json::to_bool(root, ipv6, "ipv6", title);
        }
#endif
    };

    /**
     * @brief Class for handling gateway rerouting configuration.
     * @details This class encapsulates settings for redirecting gateway traffic in an OpenVPN connection,
     * supporting both %IPv4 and %IPv6 protocols with configurable flags. It provides string representation
     * and JSON serialization/deserialization capabilities (when JSON support is enabled).
     */
    class RerouteGW
    {
      public:
        bool ipv4 = false;
        bool ipv6 = false;
        unsigned int flags = 0;

        /**
         * @brief Converts the object to a human-readable string representation.
         * @details Creates a string describing the current state of the object, including IPv4/IPv6 status
         * and flags using the @c RedirectGatewayFlags helper class.
         * @return A string representation of the reroute gateway configuration.
         */
        std::string to_string() const
        {
            std::ostringstream os;
            const RedirectGatewayFlags rgf(flags);
            os << "IPv4=" << ipv4 << " IPv6=" << ipv6 << " flags=" << rgf.to_string();
            return os.str();
        }


#ifdef HAVE_JSON
        /**
         * @brief Serializes the object to a JSON value.
         * @details Creates a JSON object containing the current %IPv4, %IPv6, and flags values.
         * Only available when JSON support is enabled (HAVE_JSON defined).
         * @return A JSON representation of the reroute gateway configuration.
         */
        Json::Value to_json() const
        {
            Json::Value root(Json::objectValue);
            root["ipv4"] = Json::Value(ipv4);
            root["ipv6"] = Json::Value(ipv6);
            root["flags"] = Json::Value(flags);
            return root;
        }

        /**
         * @brief Deserializes the object from a JSON value.
         * @details Populates the object's fields from a JSON object.
         * Only available when JSON support is enabled (HAVE_JSON defined).
         * @param root The JSON object to extract values from.
         * @param title A title/context string used for error reporting.
         */
        void from_json(const Json::Value &root, const std::string &title)
        {
            json::assert_dict(root, title);
            json::to_bool(root, ipv4, "ipv4", title);
            json::to_bool(root, ipv6, "ipv6", title);
            json::to_uint(root, flags, "flags", title);
        }
#endif
    };

    /**
     * @brief Base class for route-related functionality representing a network route.
     * @details This class serves as a base for route classes, providing common validation functionality.
     *          It encapsulates the components of a network route, including
     *          address, prefix length, gateway, and various options. It provides methods
     *          for string representation, JSON serialization/deserialization, and validation.
     *          The class handles both %IPv4 and %IPv6 routes, with special support for net30
     *          routes commonly used in certain VPN configurations.
     */
    class RouteBase
    {
      public:
        std::string address;
        unsigned char prefix_length = 0;
        int metric = -1;     // optional
        std::string gateway; // optional
        bool ipv6 = false;
        bool net30 = false;

        /**
         * @brief Converts the route to a human-readable string.
         * @details Formats the route in CIDR notation (address/prefix_length),
         *          including gateway, metric, %IPv6 flag, and net30 flag if applicable.
         * @return A string representation of the route.
         */
        std::string to_string() const
        {
            std::ostringstream os;
            os << address << '/' << static_cast<uint16_t>(prefix_length);
            if (!gateway.empty())
                os << " -> " << gateway;
            if (metric >= 0)
                os << " [METRIC=" << metric << ']';
            if (ipv6)
                os << " [IPv6]";
            if (net30)
                os << " [net30]";
            return os.str();
        }

#ifdef HAVE_JSON
        /**
         * @brief Serializes the route to a JSON object.
         * @details Creates a JSON object with keys for each route property.
         * @return A JSON object representing the route.
         */
        Json::Value to_json() const
        {
            Json::Value root(Json::objectValue);
            root["address"] = Json::Value(address);
            root["prefix_length"] = Json::Value(prefix_length);
            root["metric"] = Json::Value(metric);
            root["gateway"] = Json::Value(gateway);
            root["ipv6"] = Json::Value(ipv6);
            root["net30"] = Json::Value(net30);
            return root;
        }

        /**
         * @brief Deserializes the route from a JSON object.
         * @details Populates the route properties from a JSON object.
         *          Throws exceptions if required fields are missing or have invalid types.
         * @param root The JSON object to parse.
         * @param title A string identifier used in error messages.
         */
        void from_json(const Json::Value &root, const std::string &title)
        {
            json::assert_dict(root, title);
            json::to_string(root, address, "address", title);
            json::to_uchar(root, prefix_length, "prefix_length", title);
            json::to_int(root, metric, "metric", title);
            json::to_string(root, gateway, "gateway", title);
            json::to_bool(root, ipv6, "ipv6", title);
            json::to_bool(root, net30, "net30", title);
        }
#endif

      protected:
        static constexpr int net30_prefix_length = 30;

        /**
         * @brief Protected validation method used by derived classes.
         * @details Performs validation with different requirements based on the canonical flag.
         *          Checks if the route is valid and properly formatted.
         *          Validates address format, prefix length, gateway format if specified,
         *          and net30 compliance if required.
         * @param title A string identifier used in error messages.
         * @param require_canonical If @c true, verifies the route is in canonical form.
         * @throws Exception if validation fails.
         */
        void validate_(const std::string &title, const bool require_canonical) const
        {
            const IP::Addr::Version ver = ipv6 ? IP::Addr::V6 : IP::Addr::V4;
            const IP::Route route = IP::route_from_string_prefix(address, prefix_length, title, ver);
            if (require_canonical && !route.is_canonical())
                OPENVPN_THROW_EXCEPTION(title << " : not a canonical route: " << route);
            if (!gateway.empty())
                IP::Addr(gateway, title + ".gateway", ver);
            if (net30 && route.prefix_len != net30_prefix_length)
                OPENVPN_THROW_EXCEPTION(title << " : not a net30 route: " << route);
        }
    };

    /**
     * @brief %Route address class that may use non-canonical form.
     * @details Extends RouteBase to represent a route address that doesn't necessarily need to be in canonical form.
     */
    class RouteAddress : public RouteBase // may be non-canonical
    {
      public:
        /**
         * @brief Validates the route address.
         * @details Performs validation using @c false for the canonical parameter, allowing non-canonical forms.
         * @param title A string identifier used in validation messages.
         */
        void validate(const std::string &title) const
        {
            validate_(title, false);
        }
    };

    /**
     * @brief %Route class that must use canonical form.
     * @details Extends RouteBase to represent a route that must be in canonical form.
     */
    class Route : public RouteBase // must be canonical
    {
      public:
        /**
         * @brief Validates the route.
         * @details Performs validation using @c true for the canonical parameter, requiring canonical form.
         * @param title A string identifier used in validation messages.
         */
        void validate(const std::string &title) const
        {
            validate_(title, true);
        }
    };

    /**
     * @brief Class for managing proxy bypass host configurations.
     * @details The ProxyBypass class provides functionality to store, validate, and
     *          serialize/deserialize information about a host that should bypass proxy settings.
     */
    class ProxyBypass
    {
      public:
        std::string bypass_host;

        /**
         * @brief Converts the bypass host to a string representation.
         * @return The bypass host string.
         */
        std::string to_string() const
        {
            return bypass_host;
        }

        /**
         * @brief Checks if a bypass host is defined.
         * @details Returns @c true if a bypass host is specified (non-empty string),
         *          @c false otherwise.
         * @return @c true if bypass host is defined, @c false otherwise.
         */
        bool defined() const
        {
            return !bypass_host.empty();
        }

        /**
         * @brief Validates the bypass host value.
         * @details Validates the bypass host if defined using the HostPort validator.
         *          This ensures the host is properly formatted.
         * @warning If bypass host is not defined, the method will return without processing and without raising any errors. This silent behavior may cause unexpected results.
         * @param title A descriptive title used in error messages if validation fails.
         */
        void validate(const std::string &title) const
        {
            if (defined())
                HostPort::validate_host(bypass_host, title);
        }

#ifdef HAVE_JSON
        /**
         * @brief Serializes the object to JSON.
         * @details Creates a JSON object with the bypass_host value.
         * @return JSON value representing the ProxyBypass object.
         */
        Json::Value to_json() const
        {
            Json::Value root(Json::objectValue);
            root["bypass_host"] = Json::Value(bypass_host);
            return root;
        }

        /**
         * @brief Deserializes the object from JSON.
         * @details Extracts bypass_host value from the provided JSON object.
         * @param root The JSON value to parse.
         * @param title A descriptive title used in error messages if parsing fails.
         */
        void from_json(const Json::Value &root, const std::string &title)
        {
            json::assert_dict(root, title);
            json::to_string(root, bypass_host, "bypass_host", title);
        }
#endif
    };

    /**
     * @brief Class for handling Proxy Auto-Configuration (PAC) URLs.
     * @details This class provides functionality to store, validate, and convert
     *          a Proxy Auto-Configuration %URL. PAC files are JavaScript files that
     *          determine which proxy server (if any) to use for a given %URL.
     */
    class ProxyAutoConfigURL
    {
      public:
        std::string url;

        /**
         * @brief Returns the %URL as a string.
         * @return The %URL string.
         */
        std::string to_string() const
        {
            return url;
        }

        /**
         * @brief Checks if the %URL is defined.
         * @details A %URL is considered defined if it is not empty.
         * @return @c true if the %URL is defined, @c false otherwise.
         */
        bool defined() const
        {
            return !url.empty();
        }

        /**
         * @brief Validates the %URL format.
         * @details Attempts to parse the %URL using the URL::Parse function to verify
         *          it has a valid format. Throws an exception if parsing fails.
         * @warning If url is not defined, the method will return without processing and without raising any errors. This silent behavior may cause unexpected results.
         * @param title A descriptive string to include in the exception message if validation fails.
         * @throw Exception Throws an exception if the %URL is invalid.
         */
        void validate(const std::string &title) const
        {
            try
            {
                if (defined())
                    (URL::Parse(url));
            }
            catch (const std::exception &e)
            {
                OPENVPN_THROW_EXCEPTION(title << " : error parsing ProxyAutoConfigURL: " << e.what());
            }
        }

#ifdef HAVE_JSON
        /**
         * @brief Converts the %URL to a JSON object.
         * @details Creates a JSON object with a single "url" field containing the %URL string.
         * @return A JSON object representation of the %URL.
         */
        Json::Value to_json() const
        {
            Json::Value root(Json::objectValue);
            root["url"] = Json::Value(url);
            return root;
        }

        /**
         * @brief Populates the %URL from a JSON object.
         * @details Extracts the "url" field from the provided JSON object.
         *          Does nothing if the JSON is not an object.
         * @warning If input is not a valid JSON dictionary, the method will return without processing and without raising any errors. This silent behavior may cause unexpected results.
         * @param root The JSON object to extract the %URL from.
         * @param title A descriptive string to include in error messages.
         */
        void from_json(const Json::Value &root, const std::string &title)
        {
            if (!json::is_dict(root, title))
                return;
            json::to_string(root, url, "url", title);
        }
#endif
    };

    /**
     * @brief Host and port configuration for proxy connections.
     * @details This class stores and validates a host:port combination that represents
     *          a proxy server. It provides methods to check validity, convert to string
     *          representation, and serialize/deserialize from JSON (when available).
     */
    class ProxyHostPort
    {
      public:
        std::string host;
        int port = 0;

        /**
         * @brief Converts the host and port to a string representation.
         * @details Creates a string in the format "host port".
         * @return A string containing the host and port separated by a space.
         */
        std::string to_string() const
        {
            std::ostringstream os;
            os << host << ' ' << port;
            return os.str();
        }

        /**
         * @brief Checks if the proxy configuration is defined.
         * @details A proxy is considered defined if the host is not empty.
         * @return @c true if the host is not empty, @c false otherwise.
         */
        bool defined() const
        {
            return !host.empty();
        }

        /**
         * @brief Validates the host and port.
         * @details If the proxy is defined, validates both the host name and port number
         *          using the HostPort validation methods.
         * @warning If host is not defined, the method will return without processing and without raising any errors. This silent behavior may cause unexpected results.
         * @param title A context string used in error messages for validation.
         */
        void validate(const std::string &title) const
        {
            if (defined())
            {
                HostPort::validate_host(host, title);
                HostPort::validate_port(port, title);
            }
        }

#ifdef HAVE_JSON
        /**
         * @brief Converts the object to a JSON representation.
         * @details Creates a JSON object with "host" and "port" fields.
         * @return A JSON object containing the host and port values.
         */
        Json::Value to_json() const
        {
            Json::Value root(Json::objectValue);
            root["host"] = Json::Value(host);
            root["port"] = Json::Value(port);
            return root;
        }

        /**
         * @brief Populates the object from a JSON representation.
         * @details Extracts "host" and "port" fields from a JSON object.
         * @warning If input is not a valid JSON dictionary, the method will return without processing and without raising any errors. This silent behavior may cause unexpected results.
         * @param root The JSON object to parse.
         * @param title A context string used in error messages during parsing.
         */
        void from_json(const Json::Value &root, const std::string &title)
        {
            if (!json::is_dict(root, title))
                return;
            json::to_string(root, host, "host", title);
            json::to_int(root, port, "port", title);
        }
#endif
    };

    /**
     * @brief Windows Internet Name Service (WINS) server configuration.
     * @details Represents a WINS server with its %IPv4 address. WINS provides name
     *          resolution services in Microsoft Windows networks, allowing NetBIOS
     *          names to be mapped to %IP addresses.
     */
    class WINSServer
    {
      public:
        std::string address;

        /**
         * @brief Converts the WINS server to a string representation.
         * @return The WINS server's %IP address as a string.
         */
        std::string to_string() const
        {
            std::string ret = address;
            return ret;
        }

        /**
         * @brief Validates the WINS server address.
         * @details Checks if the address is a valid %IPv4 address. Throws an exception
         *          if the address is invalid.
         * @param title The context name used in error messages.
         */
        void validate(const std::string &title) const
        {
            IP::Addr(address, title, IP::Addr::V4);
        }

#ifdef HAVE_JSON
        /**
         * @brief Serializes the WINS server to a JSON object.
         * @details Creates a JSON object with the "address" field containing the
         *          server's %IP address.
         * @return A JSON object representing the WINS server.
         */
        Json::Value to_json() const
        {
            Json::Value root(Json::objectValue);
            root["address"] = Json::Value(address);
            return root;
        }

        /**
         * @brief Deserializes a WINS server from a JSON object.
         * @details Extracts the "address" field from the JSON object and assigns it
         *          to the server address.
         * @param root The JSON object to deserialize from.
         * @param title The context name used in error messages.
         */
        void from_json(const Json::Value &root, const std::string &title)
        {
            json::assert_dict(root, title);
            json::to_string(root, address, "address", title);
        }
#endif
    };

    /**
     * @brief Sets the remote address for the TUN interface.
     * @details Stores the remote endpoint address for the VPN tunnel connection.
     * @param address The remote address string to set.
     * @param ipv6 If @c true, indicates this is an %IPv6 address; if @c false, it's an %IPv4 address.
     * @return Always returns @c true to indicate successful operation.
     */
    bool tun_builder_set_remote_address(const std::string &address, bool ipv6) override
    {
        remote_address.address = address;
        remote_address.ipv6 = ipv6;
        return true;
    }

    /**
     * @brief Adds a local address to the TUN interface.
     * @details Configures a local %IP address for the virtual network interface with specified
     *          prefix length and gateway. Maintains separate indices for %IPv4 and %IPv6 addresses.
     * @param address The local %IP address to assign to the TUN interface.
     * @param prefix_length The subnet prefix length (e.g., 24 for a /24 subnet).
     * @param gateway The gateway address for this network.
     * @param ipv6 If @c true, indicates this is an %IPv6 address; if @c false, it's an %IPv4 address.
     * @param net30 If @c true, indicates this is a net30 topology (point-to-point with 4 addresses).
     * @return Always returns @c true to indicate successful operation.
     */
    bool tun_builder_add_address(const std::string &address, int prefix_length, const std::string &gateway, bool ipv6, bool net30) override
    {
        RouteAddress r;
        r.address = address;
        r.prefix_length = static_cast<unsigned char>(prefix_length);
        r.gateway = gateway;
        r.ipv6 = ipv6;
        r.net30 = net30;
        if (ipv6)
            tunnel_address_index_ipv6 = static_cast<int>(tunnel_addresses.size());
        else
            tunnel_address_index_ipv4 = static_cast<int>(tunnel_addresses.size());
        tunnel_addresses.push_back(std::move(r));
        return true;
    }

    /**
     * @brief Configures global gateway rerouting through the VPN tunnel.
     * @details Sets up redirection of default traffic routes through the VPN tunnel
     *          for %IPv4 and/or %IPv6 traffic according to the specified flags.
     * @param ipv4 If @c true, reroute %IPv4 default gateway.
     * @param ipv6 If @c true, reroute %IPv6 default gateway.
     * @param flags Special routing flags that modify the routing behavior.
     * @return Always returns @c true to indicate successful operation.
     */
    bool tun_builder_reroute_gw(bool ipv4, bool ipv6, unsigned int flags) override
    {
        reroute_gw.ipv4 = ipv4;
        reroute_gw.ipv6 = ipv6;
        reroute_gw.flags = flags;
        return true;
    }

    /**
     * @brief Sets the default route metric for VPN routes.
     * @details Configures the priority of routes added by the VPN, where lower
     *          metric values indicate higher priority routes.
     * @param metric The metric value to assign to routes.
     * @return Always returns @c true to indicate successful operation.
     */
    bool tun_builder_set_route_metric_default(int metric) override
    {
        route_metric_default = metric;
        return true;
    }

    /**
     * @brief Adds a route to the tunnel.
     * @details Configures a new route to be added to the routing table when the tunnel is established.
     * @param address The destination network address.
     * @param prefix_length The subnet prefix length (netmask).
     * @param metric The route metric/priority value. If negative, a default metric will be used.
     * @param ipv6 Whether this is an %IPv6 @c true or %IPv4 @c false route.
     * @return Always returns @c true to indicate successful operation.
     */
    bool tun_builder_add_route(const std::string &address, int prefix_length, int metric, bool ipv6) override
    {
        Route r;
        r.address = address;
        r.prefix_length = static_cast<unsigned char>(prefix_length);
        r.metric = (metric < 0 ? route_metric_default : metric);
        r.ipv6 = ipv6;
        add_routes.push_back(std::move(r));
        return true;
    }

    /**
     * @brief Excludes a route from the tunnel.
     * @details Configures a route to be excluded from the tunnel routing, allowing traffic to that destination
     *          to bypass the VPN tunnel.
     * @param address The destination network address to exclude.
     * @param prefix_length The subnet prefix length (netmask).
     * @param metric The route metric/priority value.
     * @param ipv6 Whether this is an %IPv6 (@c true ) or %IPv4 (@c false ) route.
     * @return Always returns @c true to indicate successful operation.
     */
    bool tun_builder_exclude_route(const std::string &address, int prefix_length, int metric, bool ipv6) override
    {
        Route r;
        r.address = address;
        r.prefix_length = static_cast<unsigned char>(prefix_length);
        r.metric = metric;
        r.ipv6 = ipv6;
        exclude_routes.push_back(std::move(r));
        return true;
    }

    /**
     * @brief Set DNS options for use with tun builder.
     * @details Calling this invalidates any DNS related @c --dhcp-options previously added.
     * @param dns The @c --dns options to be set.
     * @return Always returns @c true to indicate successful operation.
     */
    bool tun_builder_set_dns_options(const DnsOptions &dns) override
    {
        dns_options = dns;
        return true;
    }

    /**
     * @brief Sets the tunnel's network layer.
     * @details Configures which OSI layer the tunnel will operate at (typically layer 2 or 3).
     * @param layer The network layer value to set.
     * @return Always returns @c true to indicate successful operation.
     */
    bool tun_builder_set_layer(int layer) override
    {
        this->layer = Layer::from_value(layer);
        return true;
    }

    /**
     * @brief Sets the Maximum Transmission Unit (MTU) for the tunnel.
     * @details Configures the maximum packet size that can be transmitted through the tunnel.
     * @param mtu The MTU value in bytes.
     * @return Always returns @c true to indicate successful operation.
     */
    bool tun_builder_set_mtu(int mtu) override
    {
        this->mtu = mtu;
        return true;
    }

    /**
     * @brief Sets a descriptive name for the VPN session.
     * @details This name may be displayed in network connection UIs or logs.
     * @param name The session name to set.
     * @return Always returns @c true to indicate successful operation.
     */
    bool tun_builder_set_session_name(const std::string &name) override
    {
        session_name = name;
        return true;
    }

    /**
     * @brief Adds a host to bypass proxy settings.
     * @details Configures a host that should connect directly, bypassing any proxy settings
     *          when the VPN is active.
     * @param bypass_host The hostname or address that should bypass the proxy.
     * @return Always returns @c true to indicate successful operation.
     */
    bool tun_builder_add_proxy_bypass(const std::string &bypass_host) override
    {
        ProxyBypass b;
        b.bypass_host = bypass_host;
        proxy_bypass.push_back(std::move(b));
        return true;
    }

    /**
     * @brief Sets the %URL for a proxy auto-configuration (PAC) file.
     * @details Configures the VPN to use a PAC file at the specified %URL for determining
     *          proxy settings.
     * @param url The %URL where the PAC file is located.
     * @return Always returns @c true to indicate successful operation.
     */
    bool tun_builder_set_proxy_auto_config_url(const std::string &url) override
    {
        proxy_auto_config_url.url = url;
        return true;
    }

    /**
     * @brief Sets the %HTTP proxy for the tunnel.
     * @details Configures the %HTTP proxy with the specified host and port.
     * @param host The hostname or %IP address of the %HTTP proxy server.
     * @param port The port number of the %HTTP proxy server.
     * @return Always returns @c true to indicate successful configuration.
     */
    bool tun_builder_set_proxy_http(const std::string &host, int port) override
    {
        http_proxy.host = host;
        http_proxy.port = port;
        return true;
    }

    /**
     * @brief Sets the HTTPS proxy for the tunnel.
     * @details Configures the HTTPS proxy with the specified host and port.
     * @param host The hostname or %IP address of the HTTPS proxy server.
     * @param port The port number of the HTTPS proxy server.
     * @return Always returns @c true to indicate successful configuration.
     */
    bool tun_builder_set_proxy_https(const std::string &host, int port) override
    {
        https_proxy.host = host;
        https_proxy.port = port;
        return true;
    }

    /**
     * @brief Adds a WINS server to the tunnel configuration.
     * @details Creates a new WINS server entry with the provided address and adds it to the list of WINS servers.
     * @param address The %IP address of the WINS server.
     * @return Always returns @c true to indicate successful addition.
     */
    bool tun_builder_add_wins_server(const std::string &address) override
    {
        WINSServer wins;
        wins.address = address;
        wins_servers.push_back(std::move(wins));
        return true;
    }

    /**
     * @brief Sets whether to allow a specific address family in the tunnel.
     * @details Controls whether %IPv4 or %IPv6 traffic is allowed or blocked in the tunnel.
     * @param af The address family to configure (AF_INET for %IPv4 or AF_INET6 for %IPv6).
     * @param allow Whether to allow @c true or block @c false the specified address family.
     * @return Always returns @c true to indicate successful configuration.
     */
    bool tun_builder_set_allow_family(int af, bool allow) override
    {
        if (af == AF_INET)
            block_ipv4 = !allow;
        else if (af == AF_INET6)
            block_ipv6 = !allow;
        return true;
    }

    /**
     * @brief Sets whether to allow local DNS resolution.
     * @details Controls whether DNS requests can be resolved locally or must go through the VPN.
     * @param allow Whether to allow @c true or block @c false local DNS resolution.
     * @return Always returns @c true to indicate successful configuration.
     */
    bool tun_builder_set_allow_local_dns(bool allow) override
    {
        block_outside_dns = !allow;
        return true;
    }

    /**
     * @brief Resets all tunnel addresses.
     * @details Clears the list of tunnel addresses and resets %IPv4 and %IPv6 address indices to invalid values.
     */
    void reset_tunnel_addresses()
    {
        tunnel_addresses.clear();
        tunnel_address_index_ipv4 = -1;
        tunnel_address_index_ipv6 = -1;
    }

    /**
     * @brief Resets DNS options to default values.
     * @details Clears all DNS configuration options.
     */
    void reset_dns_options()
    {
        dns_options = {};
    }

    /**
     * @brief Gets the %IPv4 tunnel address.
     * @details Returns a pointer to the RouteAddress structure for the %IPv4 tunnel if configured.
     * @return Pointer to the %IPv4 tunnel address or @c nullptr if not configured.
     */
    const RouteAddress *vpn_ipv4() const
    {
        if (tunnel_address_index_ipv4 >= 0)
            return &tunnel_addresses[tunnel_address_index_ipv4];
        return nullptr;
    }

    /**
     * @brief Gets the %IPv6 tunnel address.
     * @details Returns a pointer to the RouteAddress structure for the %IPv6 tunnel if configured.
     * @return Pointer to the %IPv6 tunnel address or @c nullptr if not configured.
     */
    const RouteAddress *vpn_ipv6() const
    {
        if (tunnel_address_index_ipv6 >= 0)
            return &tunnel_addresses[tunnel_address_index_ipv6];
        return nullptr;
    }

    /**
     * @brief Gets the tunnel address for the specified %IP version.
     * @details Returns a pointer to the RouteAddress structure for the specified %IP version.
     * @param v The %IP address version (V4 or V6).
     * @return Pointer to the tunnel address for the specified version or @c nullptr if not configured.
     */
    const RouteAddress *vpn_ip(const IP::Addr::Version v) const
    {
        switch (v)
        {
        case IP::Addr::V4:
            return vpn_ipv4();
        case IP::Addr::V6:
            return vpn_ipv6();
        default:
            return nullptr;
        }
    }

    /**
     * @brief Validates the configuration of the tunnel.
     * @details Performs validation on all components of the tunnel configuration,
     *          including layer settings, MTU, addresses, routes, and proxy settings.
     *          Each component's validate method is called with an appropriate context string.
     */
    void validate() const
    {
        validate_layer("root");
        validate_mtu("root");
        remote_address.validate("remote_address");
        validate_list(tunnel_addresses, "tunnel_addresses");
        validate_tunnel_address_indices("root");
        validate_list(add_routes, "add_routes");
        validate_list(exclude_routes, "exclude_routes");
        validate_list(proxy_bypass, "proxy_bypass");
        proxy_auto_config_url.validate("proxy_auto_config_url");
        http_proxy.validate("http_proxy");
        https_proxy.validate("https_proxy");
    }

    /**
     * @brief Converts the tunnel configuration to a human-readable string representation.
     * @details Creates a formatted multi-line string containing all configured tunnel parameters
     *          including session name, layer, MTU, addresses, routing options, DNS settings,
     *          and proxy configurations. Only displays optional settings if they are defined.
     * @return A string representation of the tunnel configuration.
     */
    std::string to_string() const
    {
        std::ostringstream os;
        os << "Session Name: " << session_name << '\n';
        os << "Layer: " << layer.str() << '\n';
        if (mtu)
            os << "MTU: " << mtu << '\n';
        os << "Remote Address: " << remote_address.to_string() << '\n';
        render_list(os, "Tunnel Addresses", tunnel_addresses);
        os << "Reroute Gateway: " << reroute_gw.to_string() << '\n';
        os << "Block IPv4: " << (block_ipv4 ? "yes" : "no") << '\n';
        os << "Block IPv6: " << (block_ipv6 ? "yes" : "no") << '\n';
        os << "Block local DNS: " << (block_outside_dns ? "yes" : "no") << '\n';
        if (route_metric_default >= 0)
            os << "Route Metric Default: " << route_metric_default << '\n';
        render_list(os, "Add Routes", add_routes);
        render_list(os, "Exclude Routes", exclude_routes);
        if (!dns_options.servers.empty())
        {
            os << dns_options.to_string() << '\n';
        }
        if (!proxy_bypass.empty())
            render_list(os, "Proxy Bypass", proxy_bypass);
        if (proxy_auto_config_url.defined())
            os << "Proxy Auto Config URL: " << proxy_auto_config_url.to_string() << '\n';
        if (http_proxy.defined())
            os << "HTTP Proxy: " << http_proxy.to_string() << '\n';
        if (https_proxy.defined())
            os << "HTTPS Proxy: " << https_proxy.to_string() << '\n';
        if (!wins_servers.empty())
            render_list(os, "WINS Servers", wins_servers);
        return os.str();
    }

#ifdef HAVE_JSON

    /**
     * @brief Serializes the tunnel configuration to a JSON object.
     * @details Converts all tunnel parameters into a JSON representation, including
     *          session details, network configuration, routing options, and proxy settings.
     *          Optional settings are only included in the JSON if they are defined.
     * @return A @c Json::Value object containing the serialized tunnel configuration.
     */
    Json::Value to_json() const
    {
        Json::Value root(Json::objectValue);
        root["session_name"] = Json::Value(session_name);
        root["mtu"] = Json::Value(mtu);
        root["layer"] = Json::Value(layer.value());
        if (remote_address.defined())
            root["remote_address"] = remote_address.to_json();
        json::from_vector(root, tunnel_addresses, "tunnel_addresses");
        root["tunnel_address_index_ipv4"] = Json::Value(tunnel_address_index_ipv4);
        root["tunnel_address_index_ipv6"] = Json::Value(tunnel_address_index_ipv6);
        root["reroute_gw"] = reroute_gw.to_json();
        root["block_ipv6"] = Json::Value(block_ipv6);
        root["block_outside_dns"] = Json::Value(block_outside_dns);
        root["route_metric_default"] = Json::Value(route_metric_default);
        json::from_vector(root, add_routes, "add_routes");
        json::from_vector(root, exclude_routes, "exclude_routes");
        root["dns_options"] = dns_options.to_json();
        json::from_vector(root, wins_servers, "wins_servers");
        json::from_vector(root, proxy_bypass, "proxy_bypass");
        if (proxy_auto_config_url.defined())
            root["proxy_auto_config_url"] = proxy_auto_config_url.to_json();
        if (http_proxy.defined())
            root["http_proxy"] = http_proxy.to_json();
        if (https_proxy.defined())
            root["https_proxy"] = https_proxy.to_json();
        return root;
    }

    /**
     * @brief Creates a TunBuilderCapture instance from a JSON representation.
     * @details Parses a JSON object to reconstruct a complete tunnel configuration,
     *          validating required fields and populating all configuration parameters.
     *          Uses helper methods from the json namespace to ensure proper type conversion
     *          and validation.
     * @param root The JSON object containing the tunnel configuration.
     * @return A shared pointer to a newly created TunBuilderCapture instance.
     */
    static TunBuilderCapture::Ptr from_json(const Json::Value &root)
    {
        const std::string title = "root";
        TunBuilderCapture::Ptr tbc(new TunBuilderCapture);
        json::assert_dict(root, title);
        json::to_string(root, tbc->session_name, "session_name", title);
        tbc->layer = Layer::from_value(json::get_int(root, "layer", title));
        json::to_int(root, tbc->mtu, "mtu", title);
        tbc->remote_address.from_json(root["remote_address"], "remote_address");
        json::to_vector(root, tbc->tunnel_addresses, "tunnel_addresses", title);
        json::to_int(root, tbc->tunnel_address_index_ipv4, "tunnel_address_index_ipv4", title);
        json::to_int(root, tbc->tunnel_address_index_ipv6, "tunnel_address_index_ipv6", title);
        tbc->reroute_gw.from_json(root["reroute_gw"], "reroute_gw");
        json::to_bool(root, tbc->block_ipv6, "block_ipv6", title);
        json::to_bool(root, tbc->block_outside_dns, "block_outside_dns", title);
        json::to_int(root, tbc->route_metric_default, "route_metric_default", title);
        json::to_vector(root, tbc->add_routes, "add_routes", title);
        json::to_vector(root, tbc->exclude_routes, "exclude_routes", title);
        tbc->dns_options.from_json(root["dns_options"], "dns_options");
        json::to_vector(root, tbc->wins_servers, "wins_servers", title);
        json::to_vector(root, tbc->proxy_bypass, "proxy_bypass", title);
        tbc->proxy_auto_config_url.from_json(root["proxy_auto_config_url"], "proxy_auto_config_url");
        tbc->http_proxy.from_json(root["http_proxy"], "http_proxy");
        tbc->https_proxy.from_json(root["https_proxy"], "https_proxy");
        return tbc;
    }

#endif // HAVE_JSON

    // builder data
    std::string session_name;
    int mtu = 0;
    Layer layer{Layer::OSI_LAYER_3};            // OSI layer
    RemoteAddress remote_address;               // real address of server
    std::vector<RouteAddress> tunnel_addresses; // local tunnel addresses
    int tunnel_address_index_ipv4 = -1;         // index into tunnel_addresses for IPv4 entry (or -1 if undef)
    int tunnel_address_index_ipv6 = -1;         // index into tunnel_addresses for IPv6 entry (or -1 if undef)
    RerouteGW reroute_gw;                       // redirect-gateway info
    bool block_ipv4 = false;                    // block IPv4 traffic while VPN is active
    bool block_ipv6 = false;                    // block IPv6 traffic while VPN is active
    bool block_outside_dns = false;             // block traffic to port 53 locally while VPN is active
    int route_metric_default = -1;              // route-metric directive
    std::vector<Route> add_routes;              // routes that should be added to tunnel
    std::vector<Route> exclude_routes;          // routes that should be excluded from tunnel
    DnsOptions dns_options;                     // VPN DNS related settings from --dns option

    std::vector<ProxyBypass> proxy_bypass; // hosts that should bypass proxy
    ProxyAutoConfigURL proxy_auto_config_url;
    ProxyHostPort http_proxy;
    ProxyHostPort https_proxy;

    std::vector<WINSServer> wins_servers; // Windows WINS servers

    static constexpr int mtu_ipv4_maximum = 65'535;

  private:
    /**
     * @brief Renders a list of elements to an output stream with a title.
     * @details Outputs the title followed by each element in the list on a new line with indentation.
     * Each element is rendered using its to_string() method.
     * @param os The output stream to write to.
     * @param title The title to display before the list.
     * @param list The list of elements to render.
     * @tparam LIST The list type which must contain elements with a to_string() method.
     */
    template <typename LIST>
    static void render_list(std::ostream &os, const std::string &title, const LIST &list)
    {
        os << title << ':' << '\n';
        for (auto &e : list)
        {
            os << "  " << e.to_string() << '\n';
        }
    }

    /**
     * @brief Validates each element in a list.
     * @details Iterates through each element in the list and calls its validate() method
     * with a title argument that includes the element's index.
     * @param list The list of elements to validate.
     * @param title The base title to use for validation messages.
     * @tparam LIST The list type which must contain elements with a validate() method.
     */
    template <typename LIST>
    static void validate_list(const LIST &list, const std::string &title)
    {
        int i = 0;
        for (auto &e : list)
        {
            e.validate(title + '[' + openvpn::to_string(i) + ']');
            ++i;
        }
    }

    /**
     * @brief Checks if a tunnel index is valid.
     * @details An index is considered valid if it's -1 (special value) or if it's within
     * the range of available tunnel addresses (0 to @c tunnel_addresses.size() ).
     * @param index The tunnel index to validate.
     * @return @c true if the index is valid, @c false otherwise.
     */
    bool validate_tunnel_index(const int index) const
    {
        if (index == -1)
            return true;
        return index >= 0 && static_cast<unsigned int>(index) <= tunnel_addresses.size();
    }

    /**
     * @brief Validates tunnel address indices for both %IPv4 and %IPv6.
     * @details Checks that both tunnel_address_index_ipv4 and tunnel_address_index_ipv6 are valid,
     * and that they point to the correct address types (%IPv4 and %IPv6 respectively).
     * Throws an exception if any validation fails.
     * @param title The title to use in exception messages.
     * @throws Exception if any validation fails, with a descriptive error message.
     */
    void validate_tunnel_address_indices(const std::string &title) const
    {
        if (!validate_tunnel_index(tunnel_address_index_ipv4))
            OPENVPN_THROW_EXCEPTION(title << ".tunnel_address_index_ipv4 : IPv4 tunnel address index out of range: " << tunnel_address_index_ipv4);
        if (!validate_tunnel_index(tunnel_address_index_ipv6))
            OPENVPN_THROW_EXCEPTION(title << ".tunnel_address_index_ipv6 : IPv6 tunnel address index out of range: " << tunnel_address_index_ipv6);
        const RouteAddress *r4 = vpn_ipv4();
        if (r4 && r4->ipv6)
            OPENVPN_THROW_EXCEPTION(title << ".tunnel_address_index_ipv4 : IPv4 tunnel address index points to wrong address type: " << r4->to_string());
        const RouteAddress *r6 = vpn_ipv6();
        if (r6 && !r6->ipv6)
            OPENVPN_THROW_EXCEPTION(title << ".tunnel_address_index_ipv6 : IPv6 tunnel address index points to wrong address type: " << r6->to_string());
    }

    /**
     * @brief Validates that the MTU value is within an acceptable range.
     * @details Checks that the MTU is not negative and does not exceed mtu_ipv4_maximum.
     * Throws an exception if validation fails.
     * @param title The title to use in exception messages.
     * @throws Exception if the MTU is out of range.
     */
    void validate_mtu(const std::string &title) const
    {
        if (mtu < 0 || mtu > mtu_ipv4_maximum)
            OPENVPN_THROW_EXCEPTION(title << ".mtu : MTU out of range: " << mtu);
    }

    /**
     * @brief Validates that the network layer is defined.
     * @details Checks that the layer property has been properly initialized.
     * Throws an exception if the layer is undefined.
     * @param title The title to use in exception messages.
     * @throws Exception if the layer is undefined.
     */
    void validate_layer(const std::string &title) const
    {
        if (!layer.defined())
            OPENVPN_THROW_EXCEPTION(title << ": layer undefined");
    }
};
} // namespace openvpn

#endif