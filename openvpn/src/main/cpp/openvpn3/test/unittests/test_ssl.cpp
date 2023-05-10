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


using namespace openvpn;

#include <openvpn/ssl/sslchoose.hpp>
#include <openvpn/ssl/sslapi.hpp>

TEST(ssl, sslciphersuites)
{
    SSLFactoryAPI::Ptr sslfact;
    SSLLib::SSLAPI::Config::Ptr sslcfg(new SSLLib::SSLAPI::Config);
    sslcfg->set_local_cert_enabled(false);
    sslcfg->set_flags(SSLConst::NO_VERIFY_PEER);

    sslcfg->set_tls_ciphersuite_list("TLS_CHACHA20_POLY1305_SHA256:TLS_AES_128_CCM_SHA256");

    sslfact = sslcfg->new_factory();


    sslcfg->set_tls_ciphersuite_list("TLS_CHACHA2000");
#if defined(USE_MBEDTLS) || OPENSSL_VERSION_NUMBER < 0x10100000L
    /* Ignored on non TLS 1.3 implementations */
    sslfact = sslcfg->new_factory();
#else
    /* This is invalid and should throw an exception */
    EXPECT_THROW(sslcfg->new_factory(), SSLFactoryAPI::ssl_context_error);
#endif
}

TEST(ssl, sslciphers)
{
    RandomAPI::Ptr rng(new FakeSecureRand);

    bool previousLogOutput = testLog->isStdoutEnabled();
    testLog->setPrintOutput(false);
    SSLFactoryAPI::Ptr sslfact;
    SSLLib::SSLAPI::Config::Ptr sslcfg(new SSLLib::SSLAPI::Config);
    sslcfg->set_local_cert_enabled(false);
    sslcfg->set_flags(SSLConst::NO_VERIFY_PEER);
    sslcfg->set_rng(rng);

    /* This list mixes IANA and OpenSSL ciphers to see if ciphers are translated for mbed TLS and for OpenSSL */
    sslcfg->set_tls_cipher_list("TLS-ECDHE-RSA-WITH-AES-128-GCM-SHA256:TLS-ECDHE-RSA-WITH-AES-128-CBC-SHA256:AES256-SHA");

    sslfact = sslcfg->new_factory();
    sslfact->ssl();

    testLog->setPrintOutput(previousLogOutput);
}

TEST(ssl, tls_groups)
{
    RandomAPI::Ptr rng(new FakeSecureRand);

    SSLFactoryAPI::Ptr sslfact;

    SSLLib::SSLAPI::Config::Ptr sslcfg(new SSLLib::SSLAPI::Config);
    sslcfg->set_local_cert_enabled(false);
    sslcfg->set_flags(SSLConst::NO_VERIFY_PEER);
    sslcfg->set_rng(rng);

    sslcfg->set_tls_groups("secp521r1:secp384r1");

    /* Should not throw an error */
    auto f = sslcfg->new_factory();
    f->ssl();

    sslcfg->set_tls_groups("secp521r1:secp384r1:greenhell");


    testLog->startCollecting();
    f = sslcfg->new_factory();
    f->ssl();
#ifdef USE_OPENSSL
    EXPECT_EQ("OpenSSL -- warning ignoring unknown group 'greenhell' in tls-groups\n", testLog->stopCollecting());
#else
    EXPECT_EQ("mbed TLS -- warning ignoring unknown group 'greenhell' in tls-groups\n", testLog->stopCollecting());
#endif
}

#if defined(USE_OPENSSL)
TEST(ssl, translate_ciphers_openssl)
{
    bool previousLogOutput = testLog->isStdoutEnabled();
    testLog->setPrintOutput(false);
    EXPECT_EQ("ECDHE-RSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-SHA256:AES256-SHA",
              OpenSSLContext::translate_cipher_list("TLS-ECDHE-RSA-WITH-AES-128-GCM-SHA256:TLS-ECDHE-RSA-WITH-AES-128-CBC-SHA256:AES256-SHA"));
    EXPECT_EQ("DEFAULT", OpenSSLContext::translate_cipher_list("DEFAULT"));
    EXPECT_EQ("NONSENSE:AES256-SHA", OpenSSLContext::translate_cipher_list("NONSENSE:AES256-SHA"));

    testLog->setPrintOutput(previousLogOutput);
}
#endif

#if defined(USE_OPENSSL) && OPENSSL_VERSION_NUMBER >= 0x30000000L
TEST(ssl, enablelegacyProvider)
{
    RandomAPI::Ptr rng(new FakeSecureRand);

    SSLLib::SSLAPI::Config::Ptr sslcfg(new SSLLib::SSLAPI::Config);
    sslcfg->set_local_cert_enabled(false);
    sslcfg->set_flags(SSLConst::NO_VERIFY_PEER);
    sslcfg->set_rng(rng);

    auto f_nolegacy = sslcfg->new_factory();

    EXPECT_EQ(SSLLib::CryptoAPI::CipherContext::is_supported(f_nolegacy->libctx(), openvpn::CryptoAlgs::BF_CBC), false);

    SSLLib::SSLAPI::Config::Ptr sslcfg_legacy(new SSLLib::SSLAPI::Config);
    sslcfg_legacy->set_local_cert_enabled(false);
    sslcfg_legacy->set_flags(SSLConst::NO_VERIFY_PEER);
    sslcfg_legacy->set_rng(rng);
    sslcfg_legacy->enable_legacy_algorithms(true);

    /* Should not throw an error */
    auto f_legacy = sslcfg_legacy->new_factory();

    EXPECT_EQ(SSLLib::CryptoAPI::CipherContext::is_supported(f_legacy->libctx(), openvpn::CryptoAlgs::BF_CBC), true);
}
#endif
