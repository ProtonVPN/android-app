//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2022      OpenVPN Inc.
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

#pragma once

#include <openvpn/options/continuation.hpp>
#include <openvpn/common/hostport.hpp>
#include <openvpn/common/number.hpp>
#include <openvpn/addr/ip.hpp>

#include <map>
#include <vector>
#include <cstdint>
#include <algorithm>

namespace openvpn {

struct DnsServer
{
    enum class DomainType
    {
        Unset,
        Resolve,
        Exclude
    };
    enum class Security
    {
        Unset,
        No,
        Yes,
        Optional
    };
    enum class Transport
    {
        Unset,
        Plain,
        HTTPS,
        TLS
    };

    IPv4::Addr address4;
    IPv6::Addr address6;
    unsigned int port4 = 0;
    unsigned int port6 = 0;
    std::vector<std::string> domains;
    DomainType domain_type = DomainType::Unset;
    Security dnssec = Security::Unset;
    Transport transport = Transport::Unset;
    std::string sni;

    static std::int32_t parse_priority(const std::string &prio_str)
    {
        const auto min = std::numeric_limits<std::int8_t>::min();
        const auto max = std::numeric_limits<std::int8_t>::max();

        std::int32_t priority;
        if (!parse_number_validate<std::int32_t>(prio_str, 4, min, max, &priority))
            OPENVPN_THROW(option_error, "dns server priority '" << prio_str << "' invalid");
        return priority;
    }
};

struct DnsOptionsMerger : public PushOptionsMerger
{
    using PriorityList = std::vector<std::int8_t>;

    struct DnsFilter : public OptionList::FilterBase
    {
        DnsFilter(PriorityList &&pushed_prios)
            : pushed_prios_(std::forward<PriorityList>(pushed_prios))
        {
        }

        bool filter(const Option &opt) override
        {
            if (opt.empty()
                || opt.size() < 3
                || opt.ref(0) != "dns"
                || opt.ref(1) != "server")
            {
                return true;
            }
            const auto priority = DnsServer::parse_priority(opt.ref(2));
            const auto it = std::find(pushed_prios_.begin(), pushed_prios_.end(), priority);

            // Filter out server option if an option with this priority was pushed
            return it == pushed_prios_.end() ? true : false;
        }

      protected:
        const PriorityList pushed_prios_;
    };

    void merge(OptionList &pushed, const OptionList &config) const override
    {
        PriorityList pushed_prios;

        auto indices = pushed.get_index_ptr("dns");
        if (indices)
        {
            for (const auto i : *indices)
            {
                if (pushed[i].size() < 3 || pushed[i].ref(1) != "server")
                    continue;
                const auto priority = DnsServer::parse_priority(pushed[i].ref(2));
                pushed_prios.emplace_back(priority);
            }
        }

        DnsFilter filter(std::move(pushed_prios));
        pushed.extend(config, &filter);
    }
};

struct DnsOptions
{
    DnsOptions(const OptionList &opt)
    {
        auto indices = opt.get_index_ptr("dns");
        if (indices == nullptr)
            return;

        for (const auto i : *indices)
        {
            const auto &o = opt[i];
            if (o.size() >= 3 && o.ref(1) == "search-domains")
            {
                for (std::size_t j = 2; j < o.size(); j++)
                    search_domains.push_back(o.ref(j));
            }
            else if (o.size() >= 5 && o.ref(1) == "server")
            {
                auto priority = DnsServer::parse_priority(o.ref(2));
                auto &server = get_server(priority);

                if (o.ref(3) == "address" && o.size() <= 6)
                {
                    for (std::size_t j = 4; j < o.size(); j++)
                    {
                        IP::Addr addr;
                        unsigned int port = 0;
                        std::string addr_str = o.ref(j);

                        const bool v4_port_found = addr_str.find(':') != std::string::npos
                                                   && addr_str.find(':') == addr_str.rfind(':');

                        if (addr_str[0] == '[' || v4_port_found)
                        {
                            std::string port_str;
                            if (!HostPort::split_host_port(o.ref(j), addr_str, port_str, "", false, &port))
                                OPENVPN_THROW(option_error, "dns server " << priority << " invalid address: " << o.ref(j));
                        }

                        try
                        {
                            addr = IP::Addr(addr_str);
                        }
                        catch (const IP::ip_exception &)
                        {
                            OPENVPN_THROW(option_error, "dns server " << priority << " invalid address: " << o.ref(j));
                        }

                        if (addr.is_ipv6())
                        {
                            server.address6 = addr.to_ipv6();
                            server.port6 = port;
                        }
                        else
                        {
                            server.address4 = addr.to_ipv4();
                            server.port4 = port;
                        }
                    }
                }
                else if (o.ref(3) == "resolve-domains")
                {
                    if (server.domain_type == DnsServer::DomainType::Exclude)
                    {
                        OPENVPN_THROW(option_error,
                                      "dns server " << priority << " cannot use resolve-domains and exclude-domains together");
                    }

                    server.domain_type = DnsServer::DomainType::Resolve;
                    for (std::size_t j = 4; j < o.size(); j++)
                        server.domains.push_back(o.ref(j));
                }
                else if (o.ref(3) == "exclude-domains")
                {
                    if (server.domain_type == DnsServer::DomainType::Resolve)
                    {
                        OPENVPN_THROW(option_error,
                                      "dns server " << priority << " cannot use exclude-domains and resolve-domains together");
                    }

                    server.domain_type = DnsServer::DomainType::Exclude;
                    for (std::size_t j = 4; j < o.size(); j++)
                        server.domains.push_back(o.ref(j));
                }
                else if (o.ref(3) == "dnssec" && o.size() == 5)
                {
                    if (o.ref(4) == "yes")
                        server.dnssec = DnsServer::Security::Yes;
                    else if (o.ref(4) == "no")
                        server.dnssec = DnsServer::Security::No;
                    else if (o.ref(4) == "optional")
                        server.dnssec = DnsServer::Security::Optional;
                    else
                    {
                        OPENVPN_THROW(option_error,
                                      "dns server " << priority << " dnssec setting '" << o.ref(4) << "' invalid");
                    }
                }
                else if (o.ref(3) == "transport" && o.size() == 5)
                {
                    if (o.ref(4) == "plain")
                        server.transport = DnsServer::Transport::Plain;
                    else if (o.ref(4) == "DoH")
                        server.transport = DnsServer::Transport::HTTPS;
                    else if (o.ref(4) == "DoT")
                        server.transport = DnsServer::Transport::TLS;
                    else
                    {
                        OPENVPN_THROW(option_error,
                                      "dns server " << priority << " transport '" << o.ref(4) << "' invalid");
                    }
                }
                else if (o.ref(3) == "sni" && o.size() == 5)
                {
                    server.sni = o.ref(4);
                }
                else
                {
                    OPENVPN_THROW(option_error,
                                  "dns server " << priority << " option '" << o.ref(3) << "' unknown or too many parameters");
                }
            }
            else
            {
                OPENVPN_THROW(option_error,
                              "dns option unknown or invalid number of parameters "
                                  << o.render(Option::RENDER_TRUNC_64 | Option::RENDER_BRACKET));
            }
        }

        for (const auto &keyval : servers)
        {
            const auto priority = keyval.first;
            const auto &server = keyval.second;
            if (server.address4.unspecified() && server.address6.unspecified())
                OPENVPN_THROW(option_error,
                              "dns server " << priority << " does not have an address assigned");
        }
    }

    std::vector<std::string> search_domains;
    std::map<std::int32_t, DnsServer> servers;

  protected:
    DnsServer &get_server(const std::int32_t priority)
    {
        auto it = servers.insert(std::make_pair(priority, DnsServer())).first;
        return (*it).second;
    }
};

} // namespace openvpn
