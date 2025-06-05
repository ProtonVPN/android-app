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


#include "test_common.hpp"

#include <openvpn/client/acc_certcheck.hpp>

using namespace openvpn;

const std::string pvt_key_txt = R"(-----BEGIN PRIVATE KEY-----
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
NP5tGsFi4iV5VR2DHQd+Z/5Cbe2Y
-----END PRIVATE KEY-----)";

const std::string fail_pvt_key_txt = R"(-----BEGIN PRIVATE KEY-----
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
1vEcBD3rxLEh4uavbzumhBFCGZuIWg==
-----END PRIVATE KEY-----)";

const std::string cert_txt = R"(-----BEGIN CERTIFICATE-----
MIIFwTCCA6mgAwIBAgIUcRBdLp7xIk5v+o4Akel9SMTXKHIwDQYJKoZIhvcNAQEL
BQAwcDELMAkGA1UEBhMCWFgxEjAQBgNVBAgMCVN0YXRlTmFtZTENMAsGA1UEBwwE
Q2l0eTEQMA4GA1UECgwHQ29tcGFueTEXMBUGA1UECwwOQ29tcGFueVNlY3Rpb24x
EzARBgNVBAMMCkNvbW1vbk5hbWUwHhcNMjMxMjIwMDYxNzI5WhcNMzMxMjE3MDYx
NzI5WjBwMQswCQYDVQQGEwJYWDESMBAGA1UECAwJU3RhdGVOYW1lMQ0wCwYDVQQH
DARDaXR5MRAwDgYDVQQKDAdDb21wYW55MRcwFQYDVQQLDA5Db21wYW55U2VjdGlv
bjETMBEGA1UEAwwKQ29tbW9uTmFtZTCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCC
AgoCggIBAMs/NuacorWP0wZQ2cvYehIBVJtU0VNalZaWhSdhck4QphdxGF4yjs9F
IYmSRuzyhjrJU0OSWu3o8VBVlPnY6PCIFNpRdA+FPQK0SkXXQdoqF77WGTX8/cRC
OSmmJiiE0eQ1bNQ4AOZ0gnlPaB7Iu3qH3go58rq1h73Wx83muXf1nCS286ryHBIp
IUeoCkI/2CdHTxh3FdjldvbWabDTuJbEGdDEa2Xmi+GyxZ7ci5VQd4AgYQnE1eJR
KbtJUHqgNIod/GIcj1RRcZeUMlRO5JXh6WViWpyz8f/r5bIzm3s0w+hEtMjDv5oW
8JcE8uQgj+68VZv6Kuw7UgJdJwgUQBE3Vbm+raMbUL2HlJF8yGkAuCtR+2JQJiyd
AWhpzVeisfughiRAYu/5RK9s4qv5P5MSo31ox0LigX989umzchcGovX2maGnXylr
ddfJ1K+tH64LJkTQ6XzDcl/n6babxmYNMLmKv8/LWVYXWf6q5gzH9/SFl24lSrp4
7/E1moRsbOrv9g2bj89RIKR1Ro7is7pWgZZdWGaHuo/+7f83Z97bK/uqoYQFegGg
OPCVUGJnALxkPF8KRmi+wKQAAC1RYz++Y14UfUaHQE/PxRgw8j0JaoGK7LQya2Ko
SuOtBlZAnNsnZpz813smrQw3XdHVT08jKIaNkuVU6xFZ++AayKz/AgMBAAGjUzBR
MB0GA1UdDgQWBBSPFAV7oBThf2t85aaxUs3vWLjk1jAfBgNVHSMEGDAWgBSPFAV7
oBThf2t85aaxUs3vWLjk1jAPBgNVHRMBAf8EBTADAQH/MA0GCSqGSIb3DQEBCwUA
A4ICAQBiXfCdKAZtNT1wYeuE1b5bOoWtDFbkSiZ/9iT8rpyjhxbkTviXPAY3okF+
PRRZT+6wEXQ1Wh0ymRW+nDvAewxSi8c7ry+xcHLI25tHxfJDafk5LwiNsDSGwc+T
wISXJ5LrHy9XqESJD3ZtLFal8zIYJmd7Rp8zUF7Qx44qzpKOsM3udwIGihO14RkQ
5wbUok/N1Z/wmswdcCji79aU69LrSWewld2gLuguogUVReC/S1hziSgud0EukZoo
btLKaVrsxZanXk/v7ijyZuD7MN32dbfPPYR3eCUf1/kNutNrgjPh0xRLi9pr/KC6
62FrN/WanYurrQYQj721XZgNeq8fyuCNc/SY6vEbimXq9Orx7oBI5PTxaSuDmDaX
DRIkmuf9o1OeWTPGjY4+/O/DYGDSbkHqbGqpUEMD1vP7Ui0wzJbAdhJ+ar7hW0DS
LoeD6wYEov9ArKnFnyHsD/eVwQGZNjYP9qjzXUv51Uzsyw8207njrGZFWUfWC0tl
oCCmFIz0tu4A63+ieiyUvDB9HzHx6QHBC60uDh+pKCK7TRg3yAZ+pg5W6Nkk8NTZ
v5DxppHxwZoZtdnQPKc13ax1UNpYibQYAh98B1tvHs95gd2oOdRFGeg5O9aVJcxl
da0X3BFsTH3RvZEyQcx8hiUsBqP9vh1SmC7TJvdCjuAZVnaT/A==
-----END CERTIFICATE-----)";

const std::string fail_cert_txt = R"(-----BEGIN CERTIFICATE-----
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
-----END CERTIFICATE-----)";

const std::string certcrl_txt = R"(-----BEGIN CERTIFICATE-----
MIIFwTCCA6mgAwIBAgIUcRBdLp7xIk5v+o4Akel9SMTXKHIwDQYJKoZIhvcNAQEL
BQAwcDELMAkGA1UEBhMCWFgxEjAQBgNVBAgMCVN0YXRlTmFtZTENMAsGA1UEBwwE
Q2l0eTEQMA4GA1UECgwHQ29tcGFueTEXMBUGA1UECwwOQ29tcGFueVNlY3Rpb24x
EzARBgNVBAMMCkNvbW1vbk5hbWUwHhcNMjMxMjIwMDYxNzI5WhcNMzMxMjE3MDYx
NzI5WjBwMQswCQYDVQQGEwJYWDESMBAGA1UECAwJU3RhdGVOYW1lMQ0wCwYDVQQH
DARDaXR5MRAwDgYDVQQKDAdDb21wYW55MRcwFQYDVQQLDA5Db21wYW55U2VjdGlv
bjETMBEGA1UEAwwKQ29tbW9uTmFtZTCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCC
AgoCggIBAMs/NuacorWP0wZQ2cvYehIBVJtU0VNalZaWhSdhck4QphdxGF4yjs9F
IYmSRuzyhjrJU0OSWu3o8VBVlPnY6PCIFNpRdA+FPQK0SkXXQdoqF77WGTX8/cRC
OSmmJiiE0eQ1bNQ4AOZ0gnlPaB7Iu3qH3go58rq1h73Wx83muXf1nCS286ryHBIp
IUeoCkI/2CdHTxh3FdjldvbWabDTuJbEGdDEa2Xmi+GyxZ7ci5VQd4AgYQnE1eJR
KbtJUHqgNIod/GIcj1RRcZeUMlRO5JXh6WViWpyz8f/r5bIzm3s0w+hEtMjDv5oW
8JcE8uQgj+68VZv6Kuw7UgJdJwgUQBE3Vbm+raMbUL2HlJF8yGkAuCtR+2JQJiyd
AWhpzVeisfughiRAYu/5RK9s4qv5P5MSo31ox0LigX989umzchcGovX2maGnXylr
ddfJ1K+tH64LJkTQ6XzDcl/n6babxmYNMLmKv8/LWVYXWf6q5gzH9/SFl24lSrp4
7/E1moRsbOrv9g2bj89RIKR1Ro7is7pWgZZdWGaHuo/+7f83Z97bK/uqoYQFegGg
OPCVUGJnALxkPF8KRmi+wKQAAC1RYz++Y14UfUaHQE/PxRgw8j0JaoGK7LQya2Ko
SuOtBlZAnNsnZpz813smrQw3XdHVT08jKIaNkuVU6xFZ++AayKz/AgMBAAGjUzBR
MB0GA1UdDgQWBBSPFAV7oBThf2t85aaxUs3vWLjk1jAfBgNVHSMEGDAWgBSPFAV7
oBThf2t85aaxUs3vWLjk1jAPBgNVHRMBAf8EBTADAQH/MA0GCSqGSIb3DQEBCwUA
A4ICAQBiXfCdKAZtNT1wYeuE1b5bOoWtDFbkSiZ/9iT8rpyjhxbkTviXPAY3okF+
PRRZT+6wEXQ1Wh0ymRW+nDvAewxSi8c7ry+xcHLI25tHxfJDafk5LwiNsDSGwc+T
wISXJ5LrHy9XqESJD3ZtLFal8zIYJmd7Rp8zUF7Qx44qzpKOsM3udwIGihO14RkQ
5wbUok/N1Z/wmswdcCji79aU69LrSWewld2gLuguogUVReC/S1hziSgud0EukZoo
btLKaVrsxZanXk/v7ijyZuD7MN32dbfPPYR3eCUf1/kNutNrgjPh0xRLi9pr/KC6
62FrN/WanYurrQYQj721XZgNeq8fyuCNc/SY6vEbimXq9Orx7oBI5PTxaSuDmDaX
DRIkmuf9o1OeWTPGjY4+/O/DYGDSbkHqbGqpUEMD1vP7Ui0wzJbAdhJ+ar7hW0DS
LoeD6wYEov9ArKnFnyHsD/eVwQGZNjYP9qjzXUv51Uzsyw8207njrGZFWUfWC0tl
oCCmFIz0tu4A63+ieiyUvDB9HzHx6QHBC60uDh+pKCK7TRg3yAZ+pg5W6Nkk8NTZ
v5DxppHxwZoZtdnQPKc13ax1UNpYibQYAh98B1tvHs95gd2oOdRFGeg5O9aVJcxl
da0X3BFsTH3RvZEyQcx8hiUsBqP9vh1SmC7TJvdCjuAZVnaT/A==
-----END CERTIFICATE-----)";

const std::string dhparam_txt = R"(-----BEGIN DH PARAMETERS-----
MIIBDAKCAQEA9WXZvihl5pltdS4C+Dc+Ki3KOuH0aBfSzmth/B3O0+oTN3CZXAak
WyK6dCCtXeBZ3ih3eNVO69BgoNVdbxuPIUWxo3EWFt6LteASgkjCEyTmrNd/aKAE
pS06RgsZcWs/Ps9iwcVFyE6kMi9Cbf3D2wwJT50kJmkEov+4cOH3NQVpS231I58x
rHMEMtNDFcJYCAEIG3cKqfX9unAgZUsDoLtyvfgvHV29koZxnmMt0+5f0qnezcnP
I/+4kAlXuAKdhsXohHeBhC2ijg/kTOMDxEbEVv+SkCIUyM+dB8UtlPKOH9HEL5Xi
+BpDSqO6Bha5+NAVUU7OdDsnzRwSWaD6lwIBAgICAOE=
-----END DH PARAMETERS-----)";

static constexpr int debug_output = 0;

static inline bool xfer_oneway(SslApiBuilder &sender, SslApiBuilder &recv, std::string out)
{
    if (sender.get().read_ciphertext_ready())
    {
        BufferPtr buf = sender.get().read_ciphertext();
        recv.get().write_ciphertext(buf);
        std::cout << out << buf->size() << " bytes" << std::endl;
        return true;
    }

    if (sender.get().read_cleartext_ready())
    {
        /* this can also indicate an error */
        uint8_t cleartext[1024];

        std::cout << out << " read ready?" << std::endl;
        auto ctsize = sender.get().read_cleartext(cleartext, sizeof(cleartext));
        std::cout << ctsize << std::endl;

        EXPECT_FALSE(ctsize > 0);
        /* TODO: capture output for tests with data */
        return true;
    }
    return false;
}

static inline void xfer(SslApiBuilder &cli, SslApiBuilder &serv)
{
    while (xfer_oneway(cli, serv, "CLIENT -> SERVER ") || xfer_oneway(serv, cli, "SERVER -> CLIENT"))
    {
        /* while we have done work, keep working */
    }
}

static inline void xfer(AccHandshaker &cli, AccHandshaker &serv)
{
    using data_t = std::optional<std::string>;
    data_t cdata;
    data_t sdata;

    do
    {
        if (sdata && debug_output)
            std::cout << "CLIENT <-- SERVER: " << sdata->size() << " bytes\n";
        cdata = cli.process_msg(sdata);
        try
        {
            if (cdata && debug_output)
                std::cout << "CLIENT --> SERVER: " << cdata->size() << " bytes\n";
            sdata = serv.process_msg(cdata);
        }
        catch (...)
        {
            std::cout << "Server side exception indicates failed handshake\n";
            throw;
        }
    } while (sdata || cdata);
}


SSLLib::SSLAPI::Config::Ptr CreateServerConfig(const std::string &pvtKey,
                                               const std::string &cert,
                                               const std::string &ca,
                                               Frame::Ptr &&frm)
{
    SSLLib::SSLAPI::Config::Ptr config = new SSLLib::SSLAPI::Config;
    config->enable_legacy_algorithms(false);

#ifdef USE_MBEDTLS
    StrongRandomAPI::Ptr rng(new SSLLib::RandomAPI());
    config->set_rng(rng);
#endif

    config->set_frame(frm);
    config->set_mode(Mode(Mode::SERVER));
    config->load_cert(cert);
    config->load_private_key(pvtKey);
    config->load_ca(ca, false);
    // Do not log extra data during unit test
    config->set_debug_level(debug_output);

    return config;
}

SSLLib::SSLAPI::Config::Ptr CreateClientConfig(const std::string &pvtKey,
                                               const std::string &cert,
                                               const std::string &ca,
                                               Frame::Ptr &&frm)
{
    SSLLib::SSLAPI::Config::Ptr config = new SSLLib::SSLAPI::Config;

#ifdef USE_MBEDTLS
    StrongRandomAPI::Ptr rng(new SSLLib::RandomAPI());
    config->set_rng(rng);
#endif

    config->set_frame(frm);
    config->set_mode(Mode(Mode::CLIENT));
    config->load_cert(cert);
    config->load_private_key(pvtKey);
    if (ca.empty())
        config->set_flags(SSLConfigAPI::LF_ALLOW_CLIENT_CERT_NOT_REQUIRED);
    else
        config->load_ca(ca, false);

    // Do not log extra data during unit test
    config->set_debug_level(debug_output);

    return config;
}

TEST(AccCertCheckSsl, SslApiBuilder_ssl_handshake)
{
    auto server = SslApiBuilder(CreateServerConfig(pvt_key_txt,
                                                   cert_txt,
                                                   cert_txt,
                                                   new Frame(Frame::Context(128, 4096, 4096 - 128, 0, 16, BufAllocFlags::NO_FLAGS))));
    auto client = SslApiBuilder(CreateClientConfig(pvt_key_txt,
                                                   cert_txt,
                                                   cert_txt,
                                                   new Frame(Frame::Context(128, 4096, 4096 - 128, 0, 16, BufAllocFlags::NO_FLAGS))));

    server.get().start_handshake();
    client.get().start_handshake();

    try
    {
        xfer(client, server);
    }
    catch (...)
    {
        FAIL();
    }
}

TEST(AccCertCheckSsl, AccHandshaker_ssl_handshake)
{
    auto server = AccHandshaker(CreateServerConfig(pvt_key_txt,
                                                   cert_txt,
                                                   cert_txt,
                                                   new Frame(Frame::Context(128, 4096, 4096 - 128, 0, 16, BufAllocFlags::NO_FLAGS))));
    auto client = AccHandshaker(CreateClientConfig(pvt_key_txt,
                                                   cert_txt,
                                                   cert_txt,
                                                   new Frame(Frame::Context(128, 4096, 4096 - 128, 0, 16, BufAllocFlags::NO_FLAGS))));

    try
    {
        xfer(client, server);
    }
    catch (...)
    {
        FAIL();
    }
}

TEST(AccCertCheckSsl, AccHandshaker_ssl_handshake_no_cli_ca)
{
    auto server = AccHandshaker(CreateServerConfig(pvt_key_txt,
                                                   cert_txt,
                                                   cert_txt,
                                                   new Frame(Frame::Context(128, 4096, 4096 - 128, 0, 16, BufAllocFlags::NO_FLAGS))));
    auto client = AccHandshaker(CreateClientConfig(pvt_key_txt,
                                                   cert_txt,
                                                   "",
                                                   new Frame(Frame::Context(128, 4096, 4096 - 128, 0, 16, BufAllocFlags::NO_FLAGS))));

    try
    {
        xfer(client, server);
    }
    catch (...)
    {
        FAIL();
    }
}

TEST(AccCertCheckSsl, AccHandshaker_ssl_handshake_fail)
{
    auto server = AccHandshaker(CreateServerConfig(fail_pvt_key_txt,
                                                   fail_cert_txt,
                                                   fail_cert_txt,
                                                   new Frame(Frame::Context(128, 4096, 4096 - 128, 0, 16, BufAllocFlags::NO_FLAGS))));
    auto client = AccHandshaker(CreateClientConfig(pvt_key_txt,
                                                   cert_txt,
                                                   cert_txt,
                                                   new Frame(Frame::Context(128, 4096, 4096 - 128, 0, 16, BufAllocFlags::NO_FLAGS))));

    try
    {
        xfer(client, server);
    }
    catch (...)
    {
        return;
    }
    FAIL();
}
