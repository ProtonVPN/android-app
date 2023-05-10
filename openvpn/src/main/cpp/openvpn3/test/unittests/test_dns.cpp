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

#include "test_common.h"

#include <openvpn/client/dns.hpp>

using namespace openvpn;

TEST(Dns, Options)
{
    OptionList config;

    config.parse_from_config(
        "dns server 1 address 1.1.1.1\n"
        "dns search-domains domain0 domain1\n"
        "dns server -2 address [2.2.2.2]:5353\n"
        "dns server -2 resolve-domains rdom0\n"
        "dns server 1 address [1::1]:5353\n"
        "dns server 1 exclude-domains xdom0\n"
        "dns search-domains domain2\n"
        "dns server -2 resolve-domains rdom1\n"
        "dns server -2 dnssec optional\n"
        "dns server -2 transport DoT\n"
        "dns server -2 sni hostname\n"
        "dns server 3 address 3::3 3.2.1.0:4242\n"
        "dns server 3 dnssec no\n"
        "dns server 3 transport DoH\n",
        nullptr);
    config.update_map();

    DnsOptions dns(config);

    ASSERT_EQ(dns.search_domains.size(), 3u);
    ASSERT_EQ(dns.search_domains[0], "domain0");
    ASSERT_EQ(dns.search_domains[1], "domain1");
    ASSERT_EQ(dns.search_domains[2], "domain2");

    ASSERT_EQ(dns.servers.size(), 3u);

    int i = 1;
    for (const auto &keyval : dns.servers)
    {
        auto priority = keyval.first;
        auto &server = keyval.second;

        if (priority == -2)
        {
            ASSERT_EQ(i, 1);

            ASSERT_TRUE(server.address4.specified());
            ASSERT_EQ(server.address4.to_string(), "2.2.2.2");
            ASSERT_EQ(server.port4, 5353u);

            ASSERT_TRUE(server.address6.unspecified());
            ASSERT_EQ(server.port6, 0u);

            ASSERT_EQ(server.domains.size(), 2u);
            ASSERT_EQ(server.domain_type, DnsServer::DomainType::Resolve);
            ASSERT_EQ(server.domains[0], "rdom0");
            ASSERT_EQ(server.domains[1], "rdom1");

            ASSERT_EQ(server.dnssec, DnsServer::Security::Optional);

            ASSERT_EQ(server.transport, DnsServer::Transport::TLS);
            ASSERT_EQ(server.sni, "hostname");
        }
        else if (priority == 1)
        {
            ASSERT_EQ(i, 2);

            ASSERT_TRUE(server.address4.specified());
            ASSERT_EQ(server.address4.to_string(), "1.1.1.1");
            ASSERT_EQ(server.port4, 0u);

            ASSERT_TRUE(server.address6.specified());
            ASSERT_EQ(server.address6.to_string(), "1::1");
            ASSERT_EQ(server.port6, 5353u);

            ASSERT_EQ(server.domains.size(), 1u);
            ASSERT_EQ(server.domain_type, DnsServer::DomainType::Exclude);
            ASSERT_EQ(server.domains[0], "xdom0");

            ASSERT_EQ(server.dnssec, DnsServer::Security::Unset);

            ASSERT_EQ(server.transport, DnsServer::Transport::Unset);
            ASSERT_TRUE(server.sni.empty());
        }
        else if (priority == 3)
        {
            ASSERT_EQ(i, 3);

            ASSERT_TRUE(server.address4.specified());
            ASSERT_EQ(server.address4.to_string(), "3.2.1.0");
            ASSERT_EQ(server.port4, 4242u);

            ASSERT_TRUE(server.address6.specified());
            ASSERT_EQ(server.address6.to_string(), "3::3");
            ASSERT_EQ(server.port6, 0u);

            ASSERT_EQ(server.domains.size(), 0u);
            ASSERT_EQ(server.domain_type, DnsServer::DomainType::Unset);

            ASSERT_EQ(server.dnssec, DnsServer::Security::No);

            ASSERT_EQ(server.transport, DnsServer::Transport::HTTPS);
            ASSERT_TRUE(server.sni.empty());
        }

        i++;
    }
}

TEST(Dns, OptionsMerger)
{
    OptionList pushed;
    OptionList config;
    DnsOptionsMerger merger;

    pushed.parse_from_config("dns server 1 address ::1", nullptr);
    config.parse_from_config("dns server 1 address 1.1.1.1\n"
                             "dns server -2 address 2.2.2.2\n",
                             nullptr);
    pushed.update_map();
    config.update_map();

    merger.merge(pushed, config);
    ASSERT_EQ(config.size(), 2u);
    ASSERT_EQ(pushed.size(), 2u);
    ASSERT_EQ(pushed[0].ref(4), "::1");
    ASSERT_EQ(pushed[1].ref(4), "2.2.2.2");
}

TEST(Dns, ServerNoAddress)
{
    OptionList config;
    config.parse_from_config("dns server 0 exclude-domains xdom0\n", nullptr);
    config.update_map();
    JY_EXPECT_THROW(DnsOptions dns(config),
                    option_error,
                    "dns server 0 does not have an address assigned");
}

TEST(Dns, ServerInvalidAddress)
{
    OptionList config;
    config.parse_from_config("dns server 0 address 1.1.1.1 foobar\n", nullptr);
    config.update_map();
    JY_EXPECT_THROW(DnsOptions dns(config),
                    option_error,
                    "dns server 0 invalid address: foobar");
}

TEST(Dns, ServerInvalidDnssec)
{
    {
        OptionList config;
        config.parse_from_config("dns server 0 dnssec foo\n", nullptr);
        config.update_map();
        JY_EXPECT_THROW(DnsOptions dns(config),
                        option_error,
                        "dns server 0 dnssec setting 'foo' invalid");
    }
    {
        OptionList config;
        config.parse_from_config("dns server 0 dnssec yes no\n", nullptr);
        config.update_map();
        JY_EXPECT_THROW(DnsOptions dns(config),
                        option_error,
                        "dns server 0 option 'dnssec' unknown or too many parameters");
    }
}

TEST(Dns, ServerInvalidTransport)
{
    {
        OptionList config;
        config.parse_from_config("dns server 0 transport avian-carrier\n", nullptr);
        config.update_map();
        JY_EXPECT_THROW(DnsOptions dns(config),
                        option_error,
                        "dns server 0 transport 'avian-carrier' invalid");
    }
    {
        OptionList config;
        config.parse_from_config("dns server 0 transport DoT D'oh\n", nullptr);
        config.update_map();
        JY_EXPECT_THROW(DnsOptions dns(config),
                        option_error,
                        "dns server 0 option 'transport' unknown or too many parameters");
    }
}

TEST(Dns, ServerMixedDomainType)
{
    {
        OptionList config;
        config.parse_from_config(
            "dns server 0 resolve-domains this that\n"
            "dns server 0 exclude-domains foo bar baz\n",
            nullptr);
        config.update_map();
        JY_EXPECT_THROW(DnsOptions dns(config),
                        option_error,
                        "dns server 0 cannot use exclude-domains and resolve-domains together");
    }
    {
        OptionList config;
        config.parse_from_config(
            "dns server 0 exclude-domains foo bar baz\n"
            "dns server 0 resolve-domains this that\n",
            nullptr);
        config.update_map();
        JY_EXPECT_THROW(DnsOptions dns(config),
                        option_error,
                        "dns server 0 cannot use resolve-domains and exclude-domains together");
    }
}
