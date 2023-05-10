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
//    If not, see <http://www.gnu.org/licenses/>.

// OpenVPN 3 test client

#include <stdlib.h>

#include <string>
#include <iostream>
#include <thread>
#include <memory>
#include <mutex>

#include <openvpn/common/platform.hpp>

#ifdef OPENVPN_PLATFORM_MAC
#include <CoreFoundation/CFBundle.h>
#include <ApplicationServices/ApplicationServices.h>
#endif

// If enabled, don't direct ovpn3 core logging to
// ClientAPI::OpenVPNClient::log() virtual method.
// Instead, logging will go to LogBaseSimple::log().
// In this case, make sure to define:
//   LogBaseSimple log;
// at the top of your main() function to receive
// log messages from all threads.
// Also, note that the OPENVPN_LOG_GLOBAL setting
// MUST be consistent across all compilation units.
#ifdef OPENVPN_USE_LOG_BASE_SIMPLE
#define OPENVPN_LOG_GLOBAL // use global rather than thread-local log object pointer
#include <openvpn/log/logbasesimple.hpp>
#endif

// don't export core symbols
#define OPENVPN_CORE_API_VISIBILITY_HIDDEN

// use SITNL on Linux by default
#if defined(OPENVPN_PLATFORM_LINUX) && !defined(OPENVPN_USE_IPROUTE2) && !defined(OPENVPN_USE_SITNL)
#define OPENVPN_USE_SITNL
#endif

// should be included before other openvpn includes,
// with the exception of openvpn/log includes
#include <client/ovpncli.cpp>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/signal.hpp>
#include <openvpn/common/file.hpp>
#include <openvpn/common/getopt.hpp>
#include <openvpn/common/getpw.hpp>
#include <openvpn/common/cleanup.hpp>
#include <openvpn/time/timestr.hpp>
#include <openvpn/ssl/peerinfo.hpp>
#include <openvpn/ssl/sslchoose.hpp>

#ifdef OPENVPN_REMOTE_OVERRIDE
#include <openvpn/common/process.hpp>
#endif

#if defined(USE_MBEDTLS)
#include <openvpn/mbedtls/util/pkcs1.hpp>
#elif defined(USE_OPENSSL)
#include <openssl/evp.h>
#if (OPENSSL_VERSION_NUMBER >= 0x30000000L)
#include <openssl/core_names.h>
#endif
#endif

#if defined(OPENVPN_PLATFORM_WIN)
#include <openvpn/win/console.hpp>
#include <shellapi.h>
#endif

#ifdef USE_NETCFG
#include "client/core-client-netcfg.hpp"
#endif

#if defined(OPENVPN_PLATFORM_LINUX)

#include <openvpn/tun/linux/client/tuncli.hpp>

// we use a static polymorphism and define a
// platform-specific TunSetup class, responsible
// for setting up tun device
#define TUN_CLASS_SETUP TunLinuxSetup::Setup<TUN_LINUX>
#include <openvpn/tun/linux/client/tuncli.hpp>
#elif defined(OPENVPN_PLATFORM_MAC)
#include <openvpn/tun/mac/client/tuncli.hpp>
#define TUN_CLASS_SETUP TunMac::Setup
#endif

using namespace openvpn;

namespace {
OPENVPN_SIMPLE_EXCEPTION(usage);
}

#ifdef USE_TUN_BUILDER
class ClientBase : public ClientAPI::OpenVPNClient
{
  public:
    bool tun_builder_new() override
    {
        tbc.tun_builder_set_mtu(1500);
        return true;
    }

    int tun_builder_establish() override
    {
        if (!tun)
        {
            tun.reset(new TUN_CLASS_SETUP());
        }

        TUN_CLASS_SETUP::Config config;
        config.layer = Layer(Layer::Type::OSI_LAYER_3);
        // no need to add bypass routes on establish since we do it on socket_protect
        config.add_bypass_routes_on_establish = false;
        return tun->establish(tbc, &config, nullptr, std::cout);
    }

    bool tun_builder_add_address(const std::string &address,
                                 int prefix_length,
                                 const std::string &gateway, // optional
                                 bool ipv6,
                                 bool net30) override
    {
        return tbc.tun_builder_add_address(address, prefix_length, gateway, ipv6, net30);
    }

    bool tun_builder_add_route(const std::string &address,
                               int prefix_length,
                               int metric,
                               bool ipv6) override
    {
        return tbc.tun_builder_add_route(address, prefix_length, metric, ipv6);
    }

    bool tun_builder_reroute_gw(bool ipv4,
                                bool ipv6,
                                unsigned int flags) override
    {
        return tbc.tun_builder_reroute_gw(ipv4, ipv6, flags);
    }

    bool tun_builder_set_remote_address(const std::string &address,
                                        bool ipv6) override
    {
        return tbc.tun_builder_set_remote_address(address, ipv6);
    }

    bool tun_builder_set_session_name(const std::string &name) override
    {
        return tbc.tun_builder_set_session_name(name);
    }

    bool tun_builder_add_dns_server(const std::string &address, bool ipv6) override
    {
        return tbc.tun_builder_add_dns_server(address, ipv6);
    }

    void tun_builder_teardown(bool disconnect) override
    {
        std::ostringstream os;
        auto os_print = Cleanup([&os]()
                                { OPENVPN_LOG_STRING(os.str()); });
        tun->destroy(os);
    }

    bool socket_protect(int socket, std::string remote, bool ipv6) override
    {
        (void)socket;
        std::ostringstream os;
        auto os_print = Cleanup([&os]()
                                { OPENVPN_LOG_STRING(os.str()); });
        return tun->add_bypass_route(remote, ipv6, os);
    }

  private:
    TUN_CLASS_SETUP::Ptr tun = new TUN_CLASS_SETUP();
    TunBuilderCapture tbc;
};
#else // USE_TUN_BUILDER
class ClientBase : public ClientAPI::OpenVPNClient
{
  public:
    bool socket_protect(int socket, std::string remote, bool ipv6) override
    {
        std::cout << "NOT IMPLEMENTED: *** socket_protect " << socket << " " << remote << std::endl;
        return true;
    }
};
#endif

class Client : public ClientBase
{
  public:
    enum ClockTickAction
    {
        CT_UNDEF,
        CT_STOP,
        CT_RECONNECT,
        CT_PAUSE,
        CT_RESUME,
        CT_STATS,
    };

    bool is_dynamic_challenge() const
    {
        return !dc_cookie.empty();
    }

    std::string dynamic_challenge_cookie()
    {
        return dc_cookie;
    }

    std::string epki_ca;
    std::string epki_cert;
#if defined(USE_MBEDTLS)
    MbedTLSPKI::PKContext epki_ctx; // external PKI context
#elif defined(USE_OPENSSL)
    openvpn::OpenSSLPKI::PKey epki_pkey;
#endif

    void set_clock_tick_action(const ClockTickAction action)
    {
        clock_tick_action = action;
    }

    void print_stats()
    {
        const int n = stats_n();
        std::vector<long long> stats = stats_bundle();

        std::cout << "STATS:" << std::endl;
        for (int i = 0; i < n; ++i)
        {
            const long long value = stats[i];
            if (value)
                std::cout << "  " << stats_name(i) << " : " << value << std::endl;
        }
    }

#ifdef OPENVPN_REMOTE_OVERRIDE
    void set_remote_override_cmd(const std::string &cmd)
    {
        remote_override_cmd = cmd;
    }
#endif

    void set_write_url_fn(const std::string &fn)
    {
        write_url_fn = fn;
    }

  private:
    virtual void event(const ClientAPI::Event &ev) override
    {
        std::cout << date_time() << " EVENT: " << ev.name;
        if (!ev.info.empty())
            std::cout << ' ' << ev.info;
        if (ev.fatal)
            std::cout << " [FATAL-ERR]";
        else if (ev.error)
            std::cout << " [ERR]";
        std::cout << std::endl;
        if (ev.name == "DYNAMIC_CHALLENGE")
        {
            dc_cookie = ev.info;

            ClientAPI::DynamicChallenge dc;
            if (ClientAPI::OpenVPNClientHelper::parse_dynamic_challenge(ev.info, dc))
            {
                std::cout << "DYNAMIC CHALLENGE" << std::endl;
                std::cout << "challenge: " << dc.challenge << std::endl;
                std::cout << "echo: " << dc.echo << std::endl;
                std::cout << "responseRequired: " << dc.responseRequired << std::endl;
                std::cout << "stateID: " << dc.stateID << std::endl;
            }
        }
        else if (ev.name == "PROXY_NEED_CREDS")
        {
            std::cout << "PROXY_NEED_CREDS " << ev.info << std::endl;
        }
        else if (ev.name == "INFO")
        {
            if (string::starts_with(ev.info, "OPEN_URL:"))
            {
                open_url(ev.info.substr(9), "");
            }
            else if (string::starts_with(ev.info, "WEB_AUTH:"))
            {
                auto extra = ev.info.substr(9);
                size_t flagsend = extra.find(':');
                if (flagsend != std::string::npos)
                {

                    auto flags = extra.substr(0, flagsend);
                    auto url = extra.substr(flagsend + 1);
                    open_url(url, flags);
                }
            }
            else if (string::starts_with(ev.info, "CR_TEXT:"))
            {
                std::string cr_response;
                std::cout << "\n\n"
                          << ev.info.substr(8) << ": ";
                std::getline(std::cin, cr_response);
                post_cc_msg("CR_RESPONSE," + base64->encode(cr_response));
            }
        }
    }

    void open_url(std::string url_str, std::string flags)
    {
        if (string::starts_with(url_str, "http://")
            || string::starts_with(url_str, "https://"))
        {
            if (!write_url_fn.empty())
            {
                write_string(write_url_fn, url_str + '\n');
                return;
            }
#ifdef OPENVPN_PLATFORM_MAC
            std::thread thr([url_str]()
                            {
			    CFURLRef url = CFURLCreateWithBytes(
			      NULL,                        // allocator
			      (UInt8*) url_str.c_str(),     // URLBytes
			      url_str.length(),            // length
			      kCFStringEncodingUTF8,       // encoding
			      NULL                         // baseURL
			    );
			    LSOpenCFURLRef(url, 0);
			    CFRelease(url); });
            thr.detach();
#elif defined(OPENVPN_PLATFORM_TYPE_UNIX)
            Argv argv;
            if (::getuid() == 0 && ::getenv("SUDO_USER"))
            {
                argv.emplace_back("/usr/sbin/runuser");
                argv.emplace_back("-u");
                argv.emplace_back(::getenv("SUDO_USER"));
            }
            argv.emplace_back("/usr/bin/xdg-open");
            argv.emplace_back(url_str);
            system_cmd(argv);
#else
            std::cout << "No implementation to launch " << url_str << std::endl;
#endif
        }
    }

    virtual void log(const ClientAPI::LogInfo &log) override
    {
        std::lock_guard<std::mutex> lock(log_mutex);
        std::cout << date_time() << ' ' << log.text << std::flush;
    }

    virtual void clock_tick() override
    {
        const ClockTickAction action = clock_tick_action;
        clock_tick_action = CT_UNDEF;

        switch (action)
        {
        case CT_STOP:
            std::cout << "signal: CT_STOP" << std::endl;
            stop();
            break;
        case CT_RECONNECT:
            std::cout << "signal: CT_RECONNECT" << std::endl;
            reconnect(0);
            break;
        case CT_PAUSE:
            std::cout << "signal: CT_PAUSE" << std::endl;
            pause("clock-tick pause");
            break;
        case CT_RESUME:
            std::cout << "signal: CT_RESUME" << std::endl;
            resume();
            break;
        case CT_STATS:
            std::cout << "signal: CT_STATS" << std::endl;
            print_stats();
            break;
        default:
            break;
        }
    }

    virtual void external_pki_cert_request(ClientAPI::ExternalPKICertRequest &certreq) override
    {
        if (!epki_cert.empty())
        {
            certreq.cert = epki_cert;
            certreq.supportingChain = epki_ca;
        }
        else
        {
            certreq.error = true;
            certreq.errorText = "external_pki_cert_request not implemented";
        }
    }

#ifdef USE_OPENSSL
    void doOpenSSLSignature(ClientAPI::ExternalPKISignRequest &signreq) const
    {
        using PKEY_CTX_unique_ptr = std::unique_ptr<::EVP_PKEY_CTX, decltype(&::EVP_PKEY_CTX_free)>;

        BufferAllocated signdata(256, BufferAllocated::GROW);
        base64->decode(signdata, signreq.data);

        EVP_PKEY *pkey = epki_pkey.obj();


        PKEY_CTX_unique_ptr pkey_ctx(EVP_PKEY_CTX_new(pkey, nullptr), EVP_PKEY_CTX_free);

        if (!(pkey_ctx))
            throw Exception("epki_sign failed, error creating PKEY ctx");


        if ((EVP_PKEY_sign_init(pkey_ctx.get()) < 0))
        {
            throw Exception("epki_sign failed, error in EVP_PKEY_sign_init: " + openssl_error());
        }

        if (signreq.algorithm == "RSA_PKCS1_PSS_PADDING")
        {
            EVP_PKEY_CTX_set_rsa_padding(pkey_ctx.get(), RSA_PKCS1_PSS_PADDING);
        }
        else if (signreq.algorithm == "RSA_PKCS1_PADDING")
        {
            EVP_PKEY_CTX_set_rsa_padding(pkey_ctx.get(), RSA_PKCS1_PADDING);
        }
        else if (signreq.algorithm == "RSA_NO_PADDING")
        {
            EVP_PKEY_CTX_set_rsa_padding(pkey_ctx.get(), RSA_NO_PADDING);
        }

        /* determine the output length */
        size_t outlen;

        if ((EVP_PKEY_sign(pkey_ctx.get(), nullptr, &outlen, signdata.c_data(), signdata.size())) < 0)
        {
            throw Exception("epki_sign failed, error signing data: " + openssl_error());
        }

        BufferAllocated sig(outlen, BufferAllocated::ARRAY);

        if ((EVP_PKEY_sign(pkey_ctx.get(), sig.data(), &outlen, signdata.c_data(), signdata.size())) < 0)
        {
            throw Exception("epki_sign failed, error signing data: " + openssl_error());
        }

        sig.set_size(outlen);
        signreq.sig = base64->encode(sig);
        OPENVPN_LOG("SIGNATURE[" << outlen << "]: " << signreq.sig);
    }

#if (OPENSSL_VERSION_NUMBER < 0x30000000L)
    void doOpenSSLDigestSignature(ClientAPI::ExternalPKISignRequest &signreq)
    {
        /* technically implementing this without OpenSSL 3.0 is possible but
         * only the xkey_provider implementation for OpenSSL 3.0 requires this,
         * so in the cli.cpp, which is only a test cient, we skip this extra
         * effort and just use only the modern APIs in doOpenSSLDigestSignature
         */
        throw Exception("epki_sign failed, digest sign only implemented in OpenSSL 3.0");
    }
#else
    void doOpenSSLDigestSignature(ClientAPI::ExternalPKISignRequest &signreq)
    {
        EVP_PKEY_CTX *pkey_ctx = nullptr;
        BufferAllocated signdata(256, BufferAllocated::GROW);
        base64->decode(signdata, signreq.data);

        using MD_unique_ptr = std::unique_ptr<::EVP_MD_CTX, decltype(&::EVP_MD_CTX_free)>;

        MD_unique_ptr md(EVP_MD_CTX_new(), EVP_MD_CTX_free);

        if (!md)
            throw Exception("epki_sign failed, error creating MD ctx");

        if (!signreq.saltlen.empty() && signreq.saltlen != "digest")
        {
            throw Exception("epki_sign failed, only padding=digest supported" + openssl_error());
        }

        const char *padding = "none";

        if (signreq.algorithm == "RSA_PKCS1_PSS_PADDING")
        {
            padding = "pss";
        }
        else if (signreq.algorithm == "RSA_PKCS1_PADDING")
        {
            padding = "pkcs1";
        }
        else if (signreq.algorithm == "RSA_NO_PADDING")
        {
            padding = "none";
        }

        EVP_PKEY *pkey = epki_pkey.obj();
        OSSL_PARAM params[6] = {OSSL_PARAM_END};

        char *hashalg = const_cast<char *>(signreq.hashalg.c_str());
        if (signreq.hashalg == "none")
            hashalg = nullptr;

        params[0] = OSSL_PARAM_construct_utf8_string(OSSL_SIGNATURE_PARAM_DIGEST, hashalg, 0);
        params[1] = OSSL_PARAM_construct_utf8_string(OSSL_SIGNATURE_PARAM_PAD_MODE, const_cast<char *>(padding), 0);

        if (EVP_PKEY_get_id(pkey) == EVP_PKEY_RSA && !signreq.saltlen.empty())
        {
            /* The strings are used const in OpenSSL but the API definition has char * */
            char *saltlen = const_cast<char *>(signreq.saltlen.c_str());
            params[2] = OSSL_PARAM_construct_utf8_string(OSSL_SIGNATURE_PARAM_PSS_SALTLEN, saltlen, 0);
            params[3] = OSSL_PARAM_construct_utf8_string(OSSL_SIGNATURE_PARAM_MGF1_DIGEST, hashalg, 0);
            params[4] = OSSL_PARAM_construct_end();
        }

        EVP_DigestSignInit_ex(md.get(), &pkey_ctx, hashalg, nullptr, nullptr, pkey, params);

        /* determine the output length */
        size_t outlen;

        if (EVP_DigestSign(md.get(), nullptr, &outlen, signdata.data(), signdata.size()) < 0)
        {
            throw Exception("epki_sign failed, error signing data: " + openssl_error());
        }

        BufferAllocated sig(outlen, BufferAllocated::ARRAY);

        if (EVP_DigestSign(md.get(), sig.data(), &outlen, signdata.data(), signdata.size()) < 0)
        {
            throw Exception("epki_sign failed, error signing data: " + openssl_error());
        }

        sig.set_size(outlen);
        signreq.sig = base64->encode(sig);
        OPENVPN_LOG("SIGNATURE[" << outlen << "]: " << signreq.sig);
    }
#endif

    void doOpenSSLSignRequest(ClientAPI::ExternalPKISignRequest &signreq)
    {
        if (signreq.hashalg.empty())
        {
            doOpenSSLSignature(signreq);
        }
        else
        {
            doOpenSSLDigestSignature(signreq);
        }
    }
#endif

    virtual void external_pki_sign_request(ClientAPI::ExternalPKISignRequest &signreq) override
    {
#if defined(USE_MBEDTLS)
        if (epki_ctx.defined())
        {
            try
            {
                // decode base64 sign request
                BufferAllocated signdata(256, BufferAllocated::GROW);
                base64->decode(signdata, signreq.data);

                // get MD alg
                const mbedtls_md_type_t md_alg = PKCS1::DigestPrefix::MbedTLSParse().alg_from_prefix(signdata);

                // log info
                OPENVPN_LOG("SIGN[" << PKCS1::DigestPrefix::MbedTLSParse::to_string(md_alg) << ',' << signdata.size() << "]: " << render_hex_generic(signdata));

                // allocate buffer for signature
                BufferAllocated sig(mbedtls_pk_get_len(epki_ctx.get()), BufferAllocated::ARRAY);

                // sign it
                size_t sig_size = 0;
                const int status = mbedtls_pk_sign(epki_ctx.get(),
                                                   md_alg,
                                                   signdata.c_data(),
                                                   signdata.size(),
                                                   sig.data(),
                                                   &sig_size,
                                                   rng_callback,
                                                   this);
                if (status != 0)
                    throw Exception("mbedtls_pk_sign failed, err=" + openvpn::to_string(status));
                if (sig.size() != sig_size)
                    throw Exception("unexpected signature size");

                // encode base64 signature
                signreq.sig = base64->encode(sig);
                OPENVPN_LOG("SIGNATURE[" << sig_size << "]: " << signreq.sig);
            }
            catch (const std::exception &e)
            {
                signreq.error = true;
                signreq.errorText = std::string("external_pki_sign_request: ") + e.what();
            }
        }
        else
#elif defined(USE_OPENSSL)
        if (epki_pkey.defined())
        {
            try
            {
                doOpenSSLSignRequest(signreq);
            }
            catch (const std::exception &e)
            {
                signreq.error = true;
                signreq.errorText = std::string("external_pki_sign_request: ") + e.what();
            }
        }
        else
#endif
        {
            signreq.error = true;
            signreq.errorText = "external_pki_sign_request not implemented";
        }
    }

    // RNG callback
    static int rng_callback(void *arg, unsigned char *data, size_t len)
    {
        Client *self = (Client *)arg;
        if (!self->rng)
        {
            self->rng.reset(new SSLLib::RandomAPI(false));
            self->rng->assert_crypto();
        }
        return self->rng->rand_bytes_noexcept(data, len) ? 0 : -1; // using -1 as a general-purpose mbed TLS error code
    }

    virtual bool pause_on_connection_timeout() override
    {
        return false;
    }

#ifdef OPENVPN_REMOTE_OVERRIDE
    virtual bool remote_override_enabled() override
    {
        return !remote_override_cmd.empty();
    }

    virtual void remote_override(ClientAPI::RemoteOverride &ro) override
    {
        RedirectPipe::InOut pio;
        Argv argv;
        argv.emplace_back(remote_override_cmd);
        OPENVPN_LOG(argv.to_string());
        const int status = system_cmd(remote_override_cmd,
                                      argv,
                                      nullptr,
                                      pio,
                                      RedirectPipe::IGNORE_ERR,
                                      nullptr);
        if (!status)
        {
            const std::string out = string::first_line(pio.out);
            OPENVPN_LOG("REMOTE OVERRIDE: " << out);
            auto svec = string::split(out, ',');
            if (svec.size() == 4)
            {
                ro.host = svec[0];
                ro.ip = svec[1];
                ro.port = svec[2];
                ro.proto = svec[3];
            }
            else
                ro.error = "cannot parse remote-override, expecting host,ip,port,proto (at least one or both of host and ip must be defined)";
        }
        else
            ro.error = "status=" + std::to_string(status);
    }
#endif

    std::mutex log_mutex;
    std::string dc_cookie;
    RandomAPI::Ptr rng; // random data source for epki
    volatile ClockTickAction clock_tick_action = CT_UNDEF;

#ifdef OPENVPN_REMOTE_OVERRIDE
    std::string remote_override_cmd;
#endif

    std::string write_url_fn;
};

static Client *the_client = nullptr; // GLOBAL

static void worker_thread()
{
#if !defined(OPENVPN_OVPNCLI_SINGLE_THREAD)
    openvpn_io::detail::signal_blocker signal_blocker; // signals should be handled by parent thread
#endif
    try
    {
        std::cout << "Thread starting..." << std::endl;
        ClientAPI::Status connect_status = the_client->connect();
        if (connect_status.error)
        {
            std::cout << "connect error: ";
            if (!connect_status.status.empty())
                std::cout << connect_status.status << ": ";
            std::cout << connect_status.message << std::endl;
        }
    }
    catch (const std::exception &e)
    {
        std::cout << "Connect thread exception: " << e.what() << std::endl;
    }
    std::cout << "Thread finished" << std::endl;
}

static std::string read_profile(const char *fn, const std::string *profile_content)
{
    if (!string::strcasecmp(fn, "http") && profile_content && !profile_content->empty())
        return *profile_content;
    else
    {
        ProfileMerge pm(fn,
                        "ovpn",
                        "",
                        ProfileMerge::FOLLOW_FULL,
                        ProfileParseLimits::MAX_LINE_SIZE,
                        ProfileParseLimits::MAX_PROFILE_SIZE);
        if (pm.status() != ProfileMerge::MERGE_SUCCESS)
            OPENVPN_THROW_EXCEPTION("merge config error: " << pm.status_string() << " : " << pm.error());
        return pm.profile_content();
    }
}

#if defined(OPENVPN_PLATFORM_WIN)

static void start_thread(Client &client)
{
    // Set Windows title bar
    const std::string title_text = "F2:Stats F3:Reconnect F4:Stop F5:Pause";
    Win::Console::Title title(ClientAPI::OpenVPNClientHelper::platform() + "     " + title_text);
    Win::Console::Input console;

    // start connect thread
    std::unique_ptr<std::thread> thread;
    volatile bool thread_exit = false;
    the_client = &client;
    thread.reset(new std::thread([&thread_exit]()
                                 {
	worker_thread();
	thread_exit = true; }));

    // wait for connect thread to exit, also check for keypresses
    while (!thread_exit)
    {
        while (true)
        {
            const unsigned int c = console.get();
            if (!c)
                break;
            else if (c == 0x3C) // F2
                the_client->print_stats();
            else if (c == 0x3D) // F3
                the_client->reconnect(0);
            else if (c == 0x3E) // F4
                the_client->stop();
            else if (c == 0x3F) // F5
                the_client->pause("user-pause");
        }
        Sleep(1000);
    }

    // wait for connect thread to exit
    thread->join();

    the_client = nullptr;
}

#elif defined(OPENVPN_OVPNCLI_SINGLE_THREAD)

static void handler(int signum)
{
    switch (signum)
    {
    case SIGTERM:
    case SIGINT:
        if (the_client)
            the_client->set_clock_tick_action(Client::CT_STOP);
        break;
    case SIGHUP:
        if (the_client)
            the_client->set_clock_tick_action(Client::CT_RECONNECT);
        break;
    case SIGUSR1:
        if (the_client)
            the_client->set_clock_tick_action(Client::CT_STATS);
        break;
    case SIGUSR2:
        {
            // toggle pause/resume
            static bool hup = false;
            if (the_client)
            {
                if (hup)
                    the_client->set_clock_tick_action(Client::CT_RESUME);
                else
                    the_client->set_clock_tick_action(Client::CT_PAUSE);
                hup = !hup;
            }
        }
        break;
    default:
        break;
    }
}

static void start_thread(Client &client)
{
    the_client = &client;

    // capture signals that might occur while we're in worker_thread
    Signal signal(handler, Signal::F_SIGINT | Signal::F_SIGTERM | Signal::F_SIGHUP | Signal::F_SIGUSR1 | Signal::F_SIGUSR2);

    // run the client
    worker_thread();

    the_client = nullptr;
}

#else

static void handler(int signum)
{
    switch (signum)
    {
    case SIGTERM:
    case SIGINT:
        std::cout << "received stop signal " << signum << std::endl;
        if (the_client)
            the_client->stop();
        break;
    case SIGHUP:
        std::cout << "received reconnect signal " << signum << std::endl;
        if (the_client)
            the_client->reconnect(0);
        break;
    case SIGUSR1:
        if (the_client)
            the_client->print_stats();
        break;
    case SIGUSR2:
        {
            // toggle pause/resume
            static bool hup = false;
            std::cout << "received pause/resume toggle signal " << signum << std::endl;
            if (the_client)
            {
                if (hup)
                    the_client->resume();
                else
                    the_client->pause("pause-resume-signal");
                hup = !hup;
            }
        }
        break;
    default:
        std::cout << "received unknown signal " << signum << std::endl;
        break;
    }
}

static void start_thread(Client &client)
{
    std::unique_ptr<std::thread> thread;

    // start connect thread
    the_client = &client;
    thread.reset(new std::thread([]()
                                 { worker_thread(); }));

    {
        // catch signals that might occur while we're in join()
        Signal signal(handler, Signal::F_SIGINT | Signal::F_SIGTERM | Signal::F_SIGHUP | Signal::F_SIGUSR1 | Signal::F_SIGUSR2);

        // wait for connect thread to exit
        thread->join();
    }
    the_client = nullptr;
}

#endif

int openvpn_client(int argc, char *argv[], const std::string *profile_content)
{
    static const struct option longopts[] = {
        // clang-format off
        { "username",       required_argument,  nullptr,      'u' },
        { "password",       required_argument,  nullptr,      'p' },
        { "response",       required_argument,  nullptr,      'r' },
        { "dc",             required_argument,  nullptr,      'D' },
        { "proto",          required_argument,  nullptr,      'P' },
        { "ipv6",           required_argument,  nullptr,      '6' },
        { "server",         required_argument,  nullptr,      's' },
        { "port",           required_argument,  nullptr,      'R' },
        { "timeout",        required_argument,  nullptr,      't' },
        { "compress",       required_argument,  nullptr,      'c' },
        { "pk-password",    required_argument,  nullptr,      'z' },
        { "tvm-override",   required_argument,  nullptr,      'M' },
        { "proxy-host",     required_argument,  nullptr,      'h' },
        { "proxy-port",     required_argument,  nullptr,      'q' },
        { "proxy-username", required_argument,  nullptr,      'U' },
        { "proxy-password", required_argument,  nullptr,      'W' },
        { "peer-info",      required_argument,  nullptr,      'I' },
        { "gremlin",        required_argument,  nullptr,      'G' },
        { "proxy-basic",    no_argument,        nullptr,      'B' },
        { "alt-proxy",      no_argument,        nullptr,      'A' },
#if defined(ENABLE_KOVPN) || defined(ENABLE_OVPNDCO) || defined(ENABLE_OVPNDCOWIN)
        { "no-dco",         no_argument,        nullptr,      'd' },
#endif
        { "eval",           no_argument,        nullptr,      'e' },
        { "self-test",      no_argument,        nullptr,      'T' },
        { "cache-password", no_argument,        nullptr,      'C' },
        { "no-cert",        no_argument,        nullptr,      'x' },
        { "force-aes-cbc",  no_argument,        nullptr,      'f' },
        { "google-dns",     no_argument,        nullptr,      'g' },
        { "persist-tun",    no_argument,        nullptr,      'j' },
        { "wintun",         no_argument,        nullptr,      'w' },
        { "allow-local-dns-resolvers", no_argument, nullptr,  'l' },
        { "def-keydir",     required_argument,  nullptr,      'k' },
        { "merge",          no_argument,        nullptr,      'm' },
        { "version",        no_argument,        nullptr,      'v' },
        { "auto-sess",      no_argument,        nullptr,      'a' },
        { "auth-retry",     no_argument,        nullptr,      'Y' },
        { "tcprof-override", required_argument, nullptr,      'X' },
        { "write-url",      required_argument,  nullptr,      'Z' },
        { "sso-methods",        required_argument,   nullptr,     'S' },
        { "ssl-debug",      required_argument,  nullptr,       1  },
        { "epki-cert",      required_argument,  nullptr,       2  },
        { "epki-ca",        required_argument,  nullptr,       3  },
        { "epki-key",       required_argument,  nullptr,       4  },
        { "legacy-algorithms", no_argument,      nullptr,      'L' },
        { "non-preferred-algorithms", no_argument, nullptr, 'Q' },
#ifdef OPENVPN_REMOTE_OVERRIDE
        { "remote-override",required_argument,  nullptr,       5  },
#endif
        { "tbc",            no_argument,        nullptr,       6  },
        { nullptr,          0,                  nullptr,       0  }
        // clang-format on
    };

    int ret = 0;
    auto cleanup = Cleanup([]()
                           { the_client = nullptr; });

    try
    {
        if (argc >= 2)
        {
            std::string username;
            std::string password;
            std::string response;
            std::string dynamicChallengeCookie;
            std::string proto;
            std::string allowUnusedAddrFamilies;
            std::string server;
            std::string port;
            int timeout = 0;
            std::string compress;
            std::string privateKeyPassword;
            std::string tlsVersionMinOverride;
            std::string tlsCertProfileOverride;
            std::string proxyHost;
            std::string proxyPort;
            std::string proxyUsername;
            std::string proxyPassword;
            std::string peer_info;
            std::string gremlin;
            std::string ssoMethods;
            bool eval = false;
            bool self_test = false;
            bool cachePassword = false;
            bool disableClientCert = false;
            bool proxyAllowCleartextAuth = false;
            int defaultKeyDirection = -1;
            int sslDebugLevel = 0;
            bool googleDnsFallback = false;
            bool autologinSessions = false;
            bool retryOnAuthFailed = false;
            bool tunPersist = false;
            bool wintun = false;
            bool allowLocalDnsResolvers = false;
            bool enableLegacyAlgorithms = false;
            bool enableNonPreferredDCO = false;
            bool merge = false;
            bool version = false;
            bool altProxy = false;
            bool dco = true;
            bool generate_tun_builder_capture_event = false;
            std::string epki_cert_fn;
            std::string epki_ca_fn;
            std::string epki_key_fn;
#ifdef OPENVPN_REMOTE_OVERRIDE
            std::string remote_override_cmd;
#endif
            std::string write_url_fn;

            int ch;
            optind = 1;

            while ((ch = getopt_long(argc, argv, "6:ABCD:G:I:LM:P:QR:S:TU:W:X:YZ:ac:degh:jk:lmp:q:r:s:t:u:vwxz:", longopts, nullptr)) != -1)
            {
                switch (ch)
                {
                case 1: // ssl-debug
                    sslDebugLevel = ::atoi(optarg);
                    break;
                case 2: // --epki-cert
                    epki_cert_fn = optarg;
                    break;
                case 3: // --epki-ca
                    epki_ca_fn = optarg;
                    break;
                case 4: // --epki-key
                    epki_key_fn = optarg;
                    break;
#ifdef OPENVPN_REMOTE_OVERRIDE
                case 5: // --remote-override
                    remote_override_cmd = optarg;
                    break;
#endif
                case 6: // --tbc
                    generate_tun_builder_capture_event = true;
                    break;
                case 'e':
                    eval = true;
                    break;
                case 'T':
                    self_test = true;
                    break;
                case 'C':
                    cachePassword = true;
                    break;
                case 'x':
                    disableClientCert = true;
                    break;
                case 'u':
                    username = optarg;
                    break;
                case 'p':
                    password = optarg;
                    break;
                case 'r':
                    response = optarg;
                    break;
                case 'P':
                    proto = optarg;
                    break;
                case '6':
                    allowUnusedAddrFamilies = optarg;
                    break;
                case 's':
                    server = optarg;
                    break;
                case 'R':
                    port = optarg;
                    break;
                case 'S':
                    ssoMethods = optarg;
                    break;
                case 't':
                    timeout = ::atoi(optarg);
                    break;
                case 'c':
                    compress = optarg;
                    break;
                case 'z':
                    privateKeyPassword = optarg;
                    break;
                case 'M':
                    tlsVersionMinOverride = optarg;
                    break;
                case 'X':
                    tlsCertProfileOverride = optarg;
                    break;
                case 'h':
                    proxyHost = optarg;
                    break;
                case 'q':
                    proxyPort = optarg;
                    break;
                case 'Q':
                    enableNonPreferredDCO = true;
                    break;
                case 'U':
                    proxyUsername = optarg;
                    break;
                case 'W':
                    proxyPassword = optarg;
                    break;
                case 'B':
                    proxyAllowCleartextAuth = true;
                    break;
                case 'A':
                    altProxy = true;
                    break;
                case 'd':
                    dco = false;
                    break;
                case 'g':
                    googleDnsFallback = true;
                    break;
                case 'a':
                    autologinSessions = true;
                    break;
                case 'Y':
                    retryOnAuthFailed = true;
                    break;
                case 'j':
                    tunPersist = true;
                    break;
                case 'w':
                    wintun = true;
                    break;
                case 'l':
                    allowLocalDnsResolvers = true;
                    break;
                case 'm':
                    merge = true;
                    break;
                case 'v':
                    version = true;
                    break;
                case 'k':
                    {
                        const std::string arg = optarg;
                        if (arg == "bi" || arg == "bidirectional")
                            defaultKeyDirection = -1;
                        else if (arg == "0")
                            defaultKeyDirection = 0;
                        else if (arg == "1")
                            defaultKeyDirection = 1;
                        else
                            OPENVPN_THROW_EXCEPTION("bad default key-direction: " << arg);
                    }
                    break;
                case 'D':
                    dynamicChallengeCookie = optarg;
                    break;
                case 'I':
                    peer_info = optarg;
                    break;
                case 'G':
                    gremlin = optarg;
                    break;
                case 'L':
                    enableLegacyAlgorithms = true;
                    break;
                case 'Z':
                    write_url_fn = optarg;
                    break;
                default:
                    throw usage();
                }
            }
            argc -= optind;
            argv += optind;

            if (version)
            {
                std::cout << "OpenVPN cli 1.0" << std::endl;
                std::cout << ClientAPI::OpenVPNClientHelper::platform() << std::endl;
                std::cout << ClientAPI::OpenVPNClientHelper::copyright() << std::endl;
            }
            else if (self_test)
            {
                ClientAPI::OpenVPNClientHelper clihelper;
                std::cout << clihelper.crypto_self_test();
            }
            else if (merge)
            {
                if (argc != 1)
                    throw usage();
                std::cout << read_profile(argv[0], profile_content);
            }
            else
            {
                if (argc < 1)
                    throw usage();

                bool retry;
                do
                {
                    retry = false;

                    ClientAPI::Config config;
                    config.guiVersion = "cli 1.0";
#if defined(OPENVPN_PLATFORM_WIN)
                    int nargs = 0;
                    auto argvw = CommandLineToArgvW(GetCommandLineW(), &nargs);
                    UTF8 utf8(Win::utf8(argvw[nargs - 1]));
                    config.content = read_profile(utf8.get(), profile_content);
#else
                    config.content = read_profile(argv[0], profile_content);
#endif
                    for (int i = 1; i < argc; ++i)
                    {
                        config.content += argv[i];
                        config.content += '\n';
                    }
                    config.serverOverride = server;
                    config.portOverride = port;
                    config.protoOverride = proto;
                    config.connTimeout = timeout;
                    config.compressionMode = compress;
                    config.allowUnusedAddrFamilies = allowUnusedAddrFamilies;
                    config.privateKeyPassword = privateKeyPassword;
                    config.tlsVersionMinOverride = tlsVersionMinOverride;
                    config.tlsCertProfileOverride = tlsCertProfileOverride;
                    config.disableClientCert = disableClientCert;
                    config.proxyHost = proxyHost;
                    config.proxyPort = proxyPort;
                    config.proxyUsername = proxyUsername;
                    config.proxyPassword = proxyPassword;
                    config.proxyAllowCleartextAuth = proxyAllowCleartextAuth;
                    config.altProxy = altProxy;
                    config.dco = dco;
                    config.generate_tun_builder_capture_event = generate_tun_builder_capture_event;
                    config.defaultKeyDirection = defaultKeyDirection;
                    config.sslDebugLevel = sslDebugLevel;
                    config.googleDnsFallback = googleDnsFallback;
                    config.autologinSessions = autologinSessions;
                    config.retryOnAuthFailed = retryOnAuthFailed;
                    config.tunPersist = tunPersist;
                    config.gremlinConfig = gremlin;
                    config.info = true;
                    config.wintun = wintun;
                    config.allowLocalDnsResolvers = allowLocalDnsResolvers;
                    config.enableLegacyAlgorithms = enableLegacyAlgorithms;
                    config.enableNonPreferredDCAlgorithms = enableNonPreferredDCO;
                    config.ssoMethods = ssoMethods;
#if defined(OPENVPN_OVPNCLI_SINGLE_THREAD)
                    config.clockTickMS = 250;
#endif

                    if (!epki_cert_fn.empty())
                        config.externalPkiAlias = "epki"; // dummy string

                    PeerInfo::Set::parse_flexible(peer_info, config.peerInfo);

                    // allow -s server override to reference a friendly name
                    // in the config.
                    //   setenv SERVER <HOST>/<FRIENDLY_NAME>
                    if (!config.serverOverride.empty())
                    {
                        ClientAPI::OpenVPNClientHelper clihelper;
                        const ClientAPI::EvalConfig cfg_eval = clihelper.eval_config(config);
                        for (auto &se : cfg_eval.serverList)
                        {
                            if (config.serverOverride == se.friendlyName)
                            {
                                config.serverOverride = se.server;
                                break;
                            }
                        }
                    }

                    if (eval)
                    {
                        ClientAPI::OpenVPNClientHelper clihelper;
                        const ClientAPI::EvalConfig cfg_eval = clihelper.eval_config(config);
                        std::cout << "EVAL PROFILE" << std::endl;
                        std::cout << "error=" << cfg_eval.error << std::endl;
                        std::cout << "message=" << cfg_eval.message << std::endl;
                        std::cout << "userlockedUsername=" << cfg_eval.userlockedUsername << std::endl;
                        std::cout << "profileName=" << cfg_eval.profileName << std::endl;
                        std::cout << "friendlyName=" << cfg_eval.friendlyName << std::endl;
                        std::cout << "autologin=" << cfg_eval.autologin << std::endl;
                        std::cout << "externalPki=" << cfg_eval.externalPki << std::endl;
                        std::cout << "staticChallenge=" << cfg_eval.staticChallenge << std::endl;
                        std::cout << "staticChallengeEcho=" << cfg_eval.staticChallengeEcho << std::endl;
                        std::cout << "privateKeyPasswordRequired=" << cfg_eval.privateKeyPasswordRequired << std::endl;
                        std::cout << "allowPasswordSave=" << cfg_eval.allowPasswordSave << std::endl;

                        if (!config.serverOverride.empty())
                            std::cout << "server=" << config.serverOverride << std::endl;

                        for (size_t i = 0; i < cfg_eval.serverList.size(); ++i)
                        {
                            const ClientAPI::ServerEntry &se = cfg_eval.serverList[i];
                            std::cout << '[' << i << "] " << se.server << '/' << se.friendlyName << std::endl;
                        }
                    }
                    else
                    {
#if defined(USE_NETCFG)
                        DBus conn(G_BUS_TYPE_SYSTEM);
                        conn.Connect();
                        NetCfgTunBuilder<Client> client(conn.GetConnection());
#else
                        Client client;
#endif
                        const ClientAPI::EvalConfig eval = client.eval_config(config);
                        if (eval.error)
                            OPENVPN_THROW_EXCEPTION("eval config error: " << eval.message);
                        if (eval.autologin)
                        {
                            if (!username.empty() || !password.empty())
                                std::cout << "NOTE: creds were not needed" << std::endl;

                            // still provide proxy credentials if given
                            if (!proxyUsername.empty())
                            {
                                ClientAPI::ProvideCreds creds;
                                creds.http_proxy_user = proxyUsername;
                                creds.http_proxy_pass = proxyPassword;
                                ClientAPI::Status creds_status = client.provide_creds(creds);
                                if (creds_status.error)
                                    OPENVPN_THROW_EXCEPTION("creds error: " << creds_status.message);
                            }
                        }
                        else
                        {
                            if (username.empty())
                                OPENVPN_THROW_EXCEPTION("need creds");
                            ClientAPI::ProvideCreds creds;
                            if (password.empty() && dynamicChallengeCookie.empty())
                                password = get_password("Password:");
                            creds.username = username;
                            creds.password = password;
                            creds.http_proxy_user = proxyUsername;
                            creds.http_proxy_pass = proxyPassword;
                            creds.response = response;
                            creds.dynamicChallengeCookie = dynamicChallengeCookie;
                            creds.replacePasswordWithSessionID = true;
                            creds.cachePassword = cachePassword;
                            ClientAPI::Status creds_status = client.provide_creds(creds);
                            if (creds_status.error)
                                OPENVPN_THROW_EXCEPTION("creds error: " << creds_status.message);
                        }

                        // external PKI
                        if (!epki_cert_fn.empty())
                        {
                            client.epki_cert = read_text_utf8(epki_cert_fn);
                            if (!epki_ca_fn.empty())
                                client.epki_ca = read_text_utf8(epki_ca_fn);
#if defined(USE_MBEDTLS) || defined(USE_OPENSSL)
                            if (!epki_key_fn.empty())
                            {
                                const std::string epki_key_txt = read_text_utf8(epki_key_fn);
#if defined(USE_MBEDTLS)
                                client.epki_ctx.parse(epki_key_txt, "EPKI", privateKeyPassword);
#else
                                client.epki_pkey.parse_pem(epki_key_txt, "epki private key", nullptr);
#endif
                            }
                            else
                                OPENVPN_THROW_EXCEPTION("--epki-key must be specified");
#endif
                        }

#ifdef OPENVPN_REMOTE_OVERRIDE
                        client.set_remote_override_cmd(remote_override_cmd);
#endif

                        client.set_write_url_fn(write_url_fn);

                        std::cout << "CONNECTING..." << std::endl;

                        // start the client thread
                        start_thread(client);

                        // Get dynamic challenge response
                        if (client.is_dynamic_challenge())
                        {
                            std::cout << "ENTER RESPONSE" << std::endl;
                            std::getline(std::cin, response);
                            if (!response.empty())
                            {
                                dynamicChallengeCookie = client.dynamic_challenge_cookie();
                                retry = true;
                            }
                        }
                        else
                        {
                            // print closing stats
                            client.print_stats();
                        }
                    }
                } while (retry);
            }
        }
        else
            throw usage();
    }
    catch (const usage &)
    {
        std::cout << "OpenVPN Client (ovpncli)" << std::endl;
        std::cout << "usage: cli [options] <config-file> [extra-config-directives...]" << std::endl;
        std::cout << "--version, -v         : show version info" << std::endl;
        std::cout << "--eval, -e            : evaluate profile only (standalone)" << std::endl;
        std::cout << "--merge, -m           : merge profile into unified format (standalone)" << std::endl;
        std::cout << "--username, -u        : username" << std::endl;
        std::cout << "--password, -p        : password" << std::endl;
        std::cout << "--response, -r        : static response" << std::endl;
        std::cout << "--dc, -D              : dynamic challenge/response cookie" << std::endl;
        std::cout << "--proto, -P           : protocol override (udp|tcp)" << std::endl;
        std::cout << "--server, -s          : server override" << std::endl;
        std::cout << "--port, -R            : port override" << std::endl;
#ifdef OPENVPN_REMOTE_OVERRIDE
        std::cout << "--remote-override     : command to run to generate next remote (returning host,ip,port,proto)" << std::endl;
#endif
        std::cout << "--allowAF, -6         : Allow unused address families (yes|no|default)" << std::endl;
        std::cout << "--timeout, -t         : timeout" << std::endl;
        std::cout << "--compress, -c        : compression mode (yes|no|asym)" << std::endl;
        std::cout << "--pk-password, -z     : private key password" << std::endl;
        std::cout << "--tvm-override, -M    : tls-version-min override (disabled, default, tls_1_x)" << std::endl;
        std::cout << "--legacy-algorithms, -L: Enable legacy algorithm (OpenSSL legacy provider)" << std::endl;
        std::cout << "--non-preferred-algorithms, -Q: Enables non preferred data channel algorithms" << std::endl;
        std::cout << "--tcprof-override, -X : tls-cert-profile override ("
                  <<
#ifdef OPENVPN_ALLOW_INSECURE_CERTPROFILE
            "insecure, "
                  <<
#endif
            "legacy, preferred, etc.)" << std::endl;
        std::cout << "--proxy-host, -h      : HTTP proxy hostname/IP" << std::endl;
        std::cout << "--proxy-port, -q      : HTTP proxy port" << std::endl;
        std::cout << "--proxy-username, -U  : HTTP proxy username" << std::endl;
        std::cout << "--proxy-password, -W  : HTTP proxy password" << std::endl;
        std::cout << "--proxy-basic, -B     : allow HTTP basic auth" << std::endl;
        std::cout << "--alt-proxy, -A       : enable alternative proxy module" << std::endl;
#if defined(ENABLE_KOVPN) || defined(ENABLE_OVPNDCO) || defined(ENABLE_OVPNDCOWIN)
        std::cout << "--no-dco, -d          : disable data channel offload" << std::endl;
#endif
        std::cout << "--cache-password, -C  : cache password" << std::endl;
        std::cout << "--no-cert, -x         : disable client certificate" << std::endl;
        std::cout << "--def-keydir, -k      : default key direction ('bi', '0', or '1')" << std::endl;
        std::cout << "--ssl-debug           : SSL debug level" << std::endl;
        std::cout << "--google-dns, -g      : enable Google DNS fallback" << std::endl;
        std::cout << "--auto-sess, -a       : request autologin session" << std::endl;
        std::cout << "--auth-retry, -Y      : retry connection on auth failure" << std::endl;
        std::cout << "--persist-tun, -j     : keep TUN interface open across reconnects" << std::endl;
        std::cout << "--wintun, -w          : use WinTun instead of TAP-Windows6 on Windows" << std::endl;
        std::cout << "--peer-info, -I       : peer info key/value list in the form K1=V1,K2=V2,... or @kv.json" << std::endl;
        std::cout << "--gremlin, -G         : gremlin info (send_delay_ms, recv_delay_ms, send_drop_prob, recv_drop_prob)" << std::endl;
        std::cout << "--epki-ca             : simulate external PKI cert supporting intermediate/root certs" << std::endl;
        std::cout << "--epki-cert           : simulate external PKI cert" << std::endl;
        std::cout << "--epki-key            : simulate external PKI private key" << std::endl;
        std::cout << "--sso-methods         : auth pending methods to announce via IV_SSO" << std::endl;
        std::cout << "--write-url, -Z       : write INFO URL to file" << std::endl;
        std::cout << "--tbc                 : generate INFO_JSON/TUN_BUILDER_CAPTURE event" << std::endl;
        ret = 2;
    }
    return ret;
}

#ifndef OPENVPN_OVPNCLI_OMIT_MAIN

int main(int argc, char *argv[])
{
    int ret = 0;

#ifdef OPENVPN_LOG_LOGBASE_H
    LogBaseSimple log;
#endif

#if defined(OPENVPN_PLATFORM_WIN)
    SetConsoleOutputCP(CP_UTF8);
#endif

    try
    {
        ret = openvpn_client(argc, argv, nullptr);
    }
    catch (const std::exception &e)
    {
        std::cout << "Main thread exception: " << e.what() << std::endl;
        ret = 1;
    }
    return ret;
}

#endif
