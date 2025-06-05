//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2022- OpenVPN Inc.
//
//    SPDX-License-Identifier: MPL-2.0 OR AGPL-3.0-only WITH openvpn3-openssl-exception
//


#pragma once

#include <map>
#include <vector>
#include <algorithm>
#include <sstream>

#include <openvpn/common/number.hpp>
#include <openvpn/common/jsonlib.hpp>
#include <openvpn/common/hostport.hpp>
#include <openvpn/addr/ip.hpp>

#ifdef HAVE_JSON
#include <openvpn/common/jsonhelper.hpp>
#endif

namespace openvpn {

/**
 * @class DnsAddress
 * @brief A name server address and optional port
 */
struct DnsAddress
{
    /**
     * @brief Return string representation of the DnsAddress object
     *
     * @return std::string  the string representation generated
     */
    std::string to_string() const
    {
        std::ostringstream os;
        os << address;
        if (port)
        {
            os << " " << port;
        }
        return os.str();
    }

    void validate(const std::string &title) const
    {
        IP::Addr::validate(address, title);
    }

#ifdef HAVE_JSON
    Json::Value to_json() const
    {
        Json::Value root(Json::objectValue);
        root["address"] = Json::Value(address);
        if (port)
        {
            root["port"] = Json::Value(port);
        }
        return root;
    }

    void from_json(const Json::Value &root, const std::string &title)
    {
        json::assert_dict(root, title);
        json::to_uint_optional(root, port, "port", 0u, title);

        std::string addr_str;
        json::to_string(root, addr_str, "address", title);
        address = IP::Addr::from_string(addr_str).to_string();
    }
#endif

    bool operator==(const DnsAddress &) const = default;

    std::string address;
    unsigned int port = 0;
};

/**
 * @class DnsDomain
 * @brief A DNS domain name
 */
struct DnsDomain
{
    /**
     * @brief Return string representation of the DnsDomain object
     *
     * @return std::string  the string representation generated
     */
    std::string to_string() const
    {
        return domain;
    }

    void validate(const std::string &title) const
    {
        HostPort::validate_host(domain, title);
    }

#ifdef HAVE_JSON
    Json::Value to_json() const
    {
        return Json::Value(domain);
    }

    void from_json(const Json::Value &value, const std::string &title)
    {
        if (!value.isString())
        {
            throw json::json_parse("string " + title + " is of incorrect type");
        }
        domain = value.asString();
    }
#endif

    std::string domain;

    bool operator==(const DnsDomain &) const = default;
};

/**
 * @class DnsServer
 * @brief DNS settings for a name server
 */
struct DnsServer
{
    enum class Security
    {
        Unset,
        No,
        Yes,
        Optional
    };

    std::string dnssec_string(const Security dnssec) const
    {
        switch (dnssec)
        {
        case Security::No:
            return "No";
        case Security::Yes:
            return "Yes";
        case Security::Optional:
            return "Optional";
        default:
            return "Unset";
        }
    }

    enum class Transport
    {
        Unset,
        Plain,
        HTTPS,
        TLS
    };

    std::string transport_string(const Transport transport) const
    {
        switch (transport)
        {
        case Transport::Plain:
            return "Plain";
        case Transport::HTTPS:
            return "HTTPS";
        case Transport::TLS:
            return "TLS";
        default:
            return "Unset";
        }
    }

    std::string to_string(const char *prefix = "") const
    {
        std::ostringstream os;
        os << prefix << "Addresses:\n";
        for (const auto &address : addresses)
        {
            os << prefix << "  " << address.to_string() << '\n';
        }
        if (!domains.empty())
        {
            os << prefix << "Domains:\n";
            for (const auto &domain : domains)
            {
                os << prefix << "  " << domain.to_string() << '\n';
            }
        }
        if (dnssec != Security::Unset)
        {
            os << prefix << "DNSSEC: " << dnssec_string(dnssec) << '\n';
        }
        if (transport != Transport::Unset)
        {
            os << prefix << "Transport: " << transport_string(transport) << '\n';
        }
        if (!sni.empty())
        {
            os << prefix << "SNI: " << sni << '\n';
        }
        return os.str();
    }

#ifdef HAVE_JSON
    Json::Value to_json() const
    {
        Json::Value server(Json::objectValue);
        json::from_vector(server, addresses, "addresses");
        if (!domains.empty())
        {
            json::from_vector(server, domains, "domains");
        }
        if (dnssec != Security::Unset)
        {
            server["dnssec"] = Json::Value(dnssec_string(dnssec));
        }
        if (transport != Transport::Unset)
        {
            server["transport"] = Json::Value(transport_string(transport));
        }
        if (!sni.empty())
        {
            server["sni"] = Json::Value(sni);
        }
        return server;
    }

    void from_json(const Json::Value &root, const std::string &title)
    {
        json::assert_dict(root, title);
        json::to_vector(root, addresses, "addresses", title);
        if (json::exists(root, "domains"))
        {
            json::to_vector(root, domains, "domains", title);
        }
        if (json::exists(root, "dnssec"))
        {
            std::string dnssec_str;
            json::to_string(root, dnssec_str, "dnssec", title);
            if (dnssec_str == "Optional")
            {
                dnssec = Security::Optional;
            }
            else if (dnssec_str == "Yes")
            {
                dnssec = Security::Yes;
            }
            else if (dnssec_str == "No")
            {
                dnssec = Security::No;
            }
            else
            {
                throw json::json_parse("dnssec value " + dnssec_str + "is unknown");
            }
        }
        if (json::exists(root, "transport"))
        {
            std::string transport_str;
            json::to_string(root, transport_str, "transport", title);
            if (transport_str == "Plain")
            {
                transport = Transport::Plain;
            }
            else if (transport_str == "HTTPS")
            {
                transport = Transport::HTTPS;
            }
            else if (transport_str == "TLS")
            {
                transport = Transport::TLS;
            }
            else
            {
                throw json::json_parse("transport value " + transport_str + "is unknown");
            }
        }
        json::to_string_optional(root, sni, "sni", "", title);
    }
#endif

    bool operator==(const DnsServer &at) const = default;

    std::vector<DnsAddress> addresses;
    std::vector<DnsDomain> domains;
    Security dnssec = Security::Unset;
    Transport transport = Transport::Unset;
    std::string sni;
};

/**
 * @class DnsOptions
 * @brief All DNS options set with the --dns or --dhcp-option directive
 */
struct DnsOptions
{
    std::string to_string() const
    {
        std::ostringstream os;
        if (!servers.empty())
        {
            os << "DNS Servers:\n";
            for (const auto &elem : servers)
            {
                os << "  Priority: " << elem.first << '\n';
                os << elem.second.to_string("  ");
            }
        }
        if (!search_domains.empty())
        {
            os << "DNS Search Domains:\n";
            for (const auto &domain : search_domains)
            {
                os << "  " << domain.to_string() << '\n';
            }
        }
        os << "Values from dhcp-options: " << (from_dhcp_options ? "true" : "false") << '\n';
        return os.str();
    }

#ifdef HAVE_JSON
    Json::Value to_json() const
    {
        Json::Value root(Json::objectValue);
        Json::Value servers_json(Json::objectValue);
        for (const auto &[prio, server] : servers)
        {
            servers_json[std::to_string(prio)] = server.to_json();
        }
        root["servers"] = std::move(servers_json);
        json::from_vector(root, search_domains, "search_domains");
        root["from_dhcp_options"] = Json::Value(from_dhcp_options);
        return root;
    }

    void from_json(const Json::Value &root, const std::string &title)
    {
        json::assert_dict(root, title);
        json::assert_dict(root["servers"], title);
        for (const auto &prio : root["servers"].getMemberNames())
        {
            DnsServer server;
            server.from_json(root["servers"][prio], title);
            servers[std::stoi(prio)] = std::move(server);
        }
        json::to_vector(root, search_domains, "search_domains", title);
        json::to_bool(root, from_dhcp_options, "from_dhcp_options", title);
    }
#endif

    bool operator==(const DnsOptions &at) const = default;

    bool from_dhcp_options = false;
    std::vector<DnsDomain> search_domains;
    std::map<int, DnsServer> servers;

  protected:
    DnsServer &get_server(const int priority)
    {
        auto it = servers.insert(std::make_pair(priority, DnsServer())).first;
        return (*it).second;
    }
};

} // namespace openvpn
