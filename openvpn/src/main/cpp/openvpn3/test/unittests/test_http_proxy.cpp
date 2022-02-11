//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2021 OpenVPN Inc.
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

#include <openvpn/transport/client/httpcli.hpp>

using namespace openvpn;

TEST(HttpProxyClient, Options)
{
  OptionList cfg;
  cfg.parse_from_config(
    "http-proxy proxyhost 3128 auto\n"
    "http-proxy-option VERSION 1.1\n"
    "http-proxy-option AGENT Mosaic/0.9\n"
    "http-proxy-option CUSTOM-HEADER X-Greeting \"Hi mom!\"\n"
    "<http-proxy-user-pass>\n"
    "uzername\n"
    "pazzword\n"
    "</http-proxy-user-pass>\n"
    , nullptr);
  cfg.update_map();
  auto po = HTTPProxyTransport::Options::parse(cfg);
  ASSERT_EQ(po->allow_cleartext_auth, true);
  ASSERT_EQ(po->auth_method, HTTPProxyTransport::AuthMethod::Any);
  ASSERT_EQ(po->username, "uzername");
  ASSERT_EQ(po->password, "pazzword");
  ASSERT_EQ(po->http_version, "1.1");
  ASSERT_EQ(po->user_agent, "Mosaic/0.9");
  ASSERT_EQ(po->headers.size(), 1);
  ASSERT_EQ(po->headers.at(0)->p1, "X-Greeting");
  ASSERT_EQ(po->headers.at(0)->p2, "Hi mom!");

  cfg.parse_from_config("http-proxy proxyhost 3128 none\n", nullptr);
  cfg.update_map();
  po = HTTPProxyTransport::Options::parse(cfg);
  ASSERT_EQ(po->auth_method, HTTPProxyTransport::AuthMethod::None);

  cfg.parse_from_config("http-proxy proxyhost 3128 basic\n", nullptr);
  cfg.update_map();
  po = HTTPProxyTransport::Options::parse(cfg);
  ASSERT_EQ(po->allow_cleartext_auth, true);
  ASSERT_EQ(po->auth_method, HTTPProxyTransport::AuthMethod::Basic);

  cfg.parse_from_config("http-proxy proxyhost 3128 digest\n", nullptr);
  cfg.update_map();
  po = HTTPProxyTransport::Options::parse(cfg);
  ASSERT_EQ(po->allow_cleartext_auth, false);
  ASSERT_EQ(po->auth_method, HTTPProxyTransport::AuthMethod::Digest);

  cfg.parse_from_config("http-proxy proxyhost 3128 ntlm\n", nullptr);
  cfg.update_map();
  po = HTTPProxyTransport::Options::parse(cfg);
  ASSERT_EQ(po->allow_cleartext_auth, false);
  ASSERT_EQ(po->auth_method, HTTPProxyTransport::AuthMethod::Ntlm);

  cfg.parse_from_config("http-proxy proxyhost 3128 auto-nct\n", nullptr);
  cfg.update_map();
  po = HTTPProxyTransport::Options::parse(cfg);
  ASSERT_EQ(po->allow_cleartext_auth, false);
  ASSERT_EQ(po->auth_method, HTTPProxyTransport::AuthMethod::Any);
}
