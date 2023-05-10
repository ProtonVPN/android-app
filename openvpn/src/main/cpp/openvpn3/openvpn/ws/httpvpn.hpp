//
//  OpenVPN
//
//  Copyright (C) 2012-2022 OpenVPN Technologies, Inc.
//  All rights reserved.
//

#pragma once

#include <utility>
#include <memory>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/options.hpp>
#include <openvpn/common/jsonhelper.hpp>

namespace openvpn {
namespace WS {

// Helper class for HTTP client and server connections
// to strongly bind to a VPN client tunnel interface.
class ViaVPN : public RC<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<ViaVPN> Ptr;

    OPENVPN_EXCEPTION(via_vpn_error);

    enum GatewayType
    {
        NONE,
        GW,
        GW4,
        GW6,
    };

    static bool is_enabled(const OptionList &opt)
    {
        return opt.exists("vpn-connection-info");
    }

    ViaVPN(const OptionList &opt)
    {
        const Option &o = opt.get("vpn-connection-info");
        connection_info_fn = o.get(1, 256);
        gw_type = parse_gw_type(o.get_optional(2, 16));
    }

    ViaVPN(std::string conn_info_fn,
           const std::string &gw)
        : connection_info_fn(std::move(conn_info_fn)),
          gw_type(parse_gw_type(gw))
    {
    }

    static ViaVPN::Ptr client_new_if_enabled(const OptionList &opt)
    {
        if (is_enabled(opt))
            return ViaVPN::Ptr(new WS::ViaVPN(opt));
        else
            return ViaVPN::Ptr();
    }

    template <typename HOST>
    Json::Value client_update_host(HOST &host) const
    {
        Json::Value root = json::parse_from_file(connection_info_fn);
        set_host_field(host.local_addr, root, "vpn_ip4", connection_info_fn);
        set_host_field(host.local_addr_alt, root, "vpn_ip6", connection_info_fn);
        maybe_swap(host.local_addr, host.local_addr_alt);

        // use gw4/gw6 as host hint
        if (gw_type == GW || gw_type == GW4)
            set_host_field(host.hint, root, "gw4", connection_info_fn);
        if (gw_type == GW || gw_type == GW6)
            set_host_field(host.hint, root, "gw6", connection_info_fn);

        return root;
    }

    template <typename LISTEN_ITEM>
    static IP::Addr server_local_addr(const LISTEN_ITEM &listen_item,
                                      const GatewayType gw_type)
    {
        if (listen_item.addr.empty())
            throw via_vpn_error("listen_item is empty");

        // via-VPN processing enabled?
        if (listen_item.addr[0] == '@')
        {
            const Json::Value root = json::parse_from_file(listen_item.addr.substr(1));
            std::string ipstr;
            if (gw_type == GW || gw_type == GW4)
                set_host_field(ipstr, root, "vpn_ip4", listen_item.addr);
            if (gw_type == GW || gw_type == GW6)
                set_host_field(ipstr, root, "vpn_ip6", listen_item.addr);
            if (ipstr.empty())
                throw via_vpn_error("cannot find local address in " + listen_item.addr);
            const IP::Addr ret = IP::Addr(ipstr, listen_item.addr);
            OPENVPN_LOG("using local address " << ret.to_string() << " for " << listen_item.directive << ' ' << listen_item.addr);
            return ret;
        }
        else
            return IP::Addr(listen_item.addr, listen_item.directive);
    }

    // returns the "client-ip" directive pushed by the server
    IP::Addr client_ip() const
    {
        const Json::Value root = json::parse_from_file(connection_info_fn);
        return IP::Addr(json::get_string_ref(root, "client_ip", connection_info_fn), connection_info_fn);
    }

    std::string to_string() const
    {
        std::string ret;
        ret.reserve(128);
        ret += "[ViaVPN ";
        ret += connection_info_fn;
        if (gw_type != NONE)
        {
            ret += ' ';
            ret += gw_type_to_string(gw_type);
        }
        ret += ']';
        return ret;
    }

  private:
    static void set_host_field(std::string &dest,
                               const Json::Value &root,
                               const std::string &name,
                               const std::string &title)
    {
        std::string value = json::get_string_optional(root, name, std::string(), title);
        if (dest.empty() && !value.empty())
            dest = std::move(value);
    }

    // if only one of s1 and s2 are non-empty, make sure it is s1
    static void maybe_swap(std::string &s1, std::string &s2)
    {
        if (s1.empty() && !s2.empty())
            std::swap(s1, s2);
    }

    static GatewayType parse_gw_type(const std::string &gw)
    {
        if (gw.empty())
            return NONE;
        else if (gw == "gw")
            return GW;
        else if (gw == "gw4")
            return GW4;
        else if (gw == "gw6")
            return GW6;
        else
            throw via_vpn_error("ViaVPN: bad gw parameter");
    }

    static std::string gw_type_to_string(const GatewayType gw_type)
    {
        switch (gw_type)
        {
        case NONE:
            return "GW-NONE";
        case GW:
            return "GW";
        case GW4:
            return "GW4";
        case GW6:
            return "GW6";
        default:
            return "GW-?";
        }
    }

    std::string connection_info_fn;
    GatewayType gw_type;
};

} // namespace WS
} // namespace openvpn
