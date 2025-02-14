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

#ifndef OPENVPN_SERVER_PEERADDR_H
#define OPENVPN_SERVER_PEERADDR_H

#include <cstdint> // for std::uint32_t, uint64_t, etc.

#include <openvpn/common/rc.hpp>
#include <openvpn/common/to_string.hpp>
#include <openvpn/common/jsonlib.hpp>
#include <openvpn/addr/ip.hpp>

namespace openvpn {
struct AddrPort
{
    AddrPort()
        : port(0)
    {
    }

    std::string to_string() const
    {
        return addr.to_string_bracket_ipv6() + ':' + openvpn::to_string(port);
    }

#ifdef HAVE_JSON
    Json::Value to_json(bool convert_mapped_addresses = false) const
    {
        Json::Value jret(Json::objectValue);
        if (convert_mapped_addresses && addr.is_mapped_address())
        {
            auto v4addr = addr.to_v4_addr();
            jret["addr"] = Json::Value(v4addr.to_string());
        }
        else
        {
            jret["addr"] = Json::Value(addr.to_string());
        }
        jret["port"] = Json::Value(port);
        return jret;
    }
#endif

    IP::Addr addr;
    std::uint16_t port;
};

struct PeerAddr : public RCCopyable<thread_unsafe_refcount>
{
    typedef RCPtr<PeerAddr> Ptr;

    PeerAddr()
        : tcp(false)
    {
    }

    std::string to_string() const
    {
        std::string proto;
        if (tcp)
            proto = "TCP ";
        else
            proto = "UDP ";
        return proto + remote.to_string() + " -> " + local.to_string();
    }

#ifdef HAVE_JSON
    Json::Value to_json(bool convert_mapped_addresses = false) const
    {
        Json::Value jret(Json::objectValue);
        jret["tcp"] = Json::Value(tcp);
        jret["local"] = local.to_json(convert_mapped_addresses);
        jret["remote"] = remote.to_json(convert_mapped_addresses);
        return jret;
    }
#endif

    AddrPort remote;
    AddrPort local;
    bool tcp;
};
} // namespace openvpn

#endif
