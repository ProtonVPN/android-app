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

// These classes encapsulate the basic setup of the various objects needed to
// create an OpenVPN client session.  The basic idea here is to look at both
// compile time settings (i.e. crypto/SSL/random libraries), and run-time
// (such as transport layer using UDP, TCP, or HTTP-proxy), and
// build the actual objects that will be used to construct a client session.

#ifndef OPENVPN_CLIENT_CLIOPT_H
#define OPENVPN_CLIENT_CLIOPT_H

#include <string>
#include <unordered_set>

#include <openvpn/error/excode.hpp>

#include <openvpn/common/size.hpp>
#include <openvpn/common/platform.hpp>
#include <openvpn/common/options.hpp>
#include <openvpn/common/stop.hpp>
#include <openvpn/frame/frame_init.hpp>
#include <openvpn/pki/epkibase.hpp>
#include <openvpn/crypto/cryptodcsel.hpp>
#include <openvpn/ssl/mssparms.hpp>
#include <openvpn/tun/tunmtu.hpp>
#include <openvpn/tun/tristate_setting.hpp>
#include <openvpn/netconf/hwaddr.hpp>

#include <openvpn/transport/socket_protect.hpp>
#include <openvpn/transport/reconnect_notify.hpp>
#include <openvpn/transport/client/udpcli.hpp>
#include <openvpn/transport/client/tcpcli.hpp>
#include <openvpn/transport/client/httpcli.hpp>
#include <openvpn/transport/altproxy.hpp>
#include <openvpn/transport/dco.hpp>
#include <openvpn/client/cliproto.hpp>
#include <openvpn/client/cliopthelper.hpp>
#include <openvpn/client/optfilt.hpp>
#include <openvpn/client/clilife.hpp>

#include <openvpn/ssl/sslchoose.hpp>

#ifdef OPENVPN_GREMLIN
#include <openvpn/transport/gremlin.hpp>
#endif

#if defined(OPENVPN_PLATFORM_ANDROID)
#include <openvpn/client/cliemuexr.hpp>
#endif

#if defined(OPENVPN_EXTERNAL_TRANSPORT_FACTORY)
#include <openvpn/transport/client/extern/config.hpp>
#include <openvpn/transport/client/extern/fw.hpp>
#endif

#if defined(OPENVPN_EXTERNAL_TUN_FACTORY)
// requires that client implements ExternalTun::Factory::new_tun_factory
#include <openvpn/tun/extern/config.hpp>
#elif defined(USE_TUN_BUILDER)
#include <openvpn/tun/builder/client.hpp>
#elif defined(OPENVPN_PLATFORM_LINUX) && !defined(OPENVPN_FORCE_TUN_NULL)
#include <openvpn/tun/linux/client/tuncli.hpp>
#ifdef OPENVPN_COMMAND_AGENT
#include <openvpn/client/unix/cmdagent.hpp>
#endif
#elif defined(OPENVPN_PLATFORM_MAC) && !defined(OPENVPN_FORCE_TUN_NULL)
#include <openvpn/tun/mac/client/tuncli.hpp>
#include <openvpn/apple/maclife.hpp>
#ifdef OPENVPN_COMMAND_AGENT
#include <openvpn/client/unix/cmdagent.hpp>
#endif
#elif defined(OPENVPN_PLATFORM_WIN) && !defined(OPENVPN_FORCE_TUN_NULL)
#include <openvpn/tun/win/client/tuncli.hpp>
#ifdef OPENVPN_COMMAND_AGENT
#include <openvpn/client/win/cmdagent.hpp>
#endif
#else
#include <openvpn/tun/client/tunnull.hpp>
#endif

#ifdef PRIVATE_TUNNEL_PROXY
#include <openvpn/pt/ptproxy.hpp>
#endif

#if defined(ENABLE_KOVPN) || defined(ENABLE_OVPNDCO) || defined(ENABLE_OVPNDCOWIN)
#include <openvpn/dco/dcocli.hpp>
#endif

#ifndef OPENVPN_UNUSED_OPTIONS
#define OPENVPN_UNUSED_OPTIONS "UNKNOWN/UNSUPPORTED OPTIONS"
#endif

namespace openvpn {

class ClientOptions : public RC<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<ClientOptions> Ptr;

    typedef ClientProto::Session Client;

    struct Config
    {
        std::string gui_version;
        std::string sso_methods;
        std::string server_override;
        std::string port_override;
        std::string hw_addr_override;
        std::string platform_version;
        Protocol proto_override;
        IP::Addr::Version proto_version_override = IP::Addr::Version::UNSPEC;
        TriStateSetting allowUnusedAddrFamilies;
        int conn_timeout = 0;
        SessionStats::Ptr cli_stats;
        ClientEvent::Queue::Ptr cli_events;
        ProtoContextOptions::Ptr proto_context_options;
        HTTPProxyTransport::Options::Ptr http_proxy_options;
        bool alt_proxy = false;
        bool dco = true;
        bool echo = false;
        bool info = false;
        bool tun_persist = false;
        bool wintun = false;
        bool allow_local_dns_resolvers = false;
        bool google_dns_fallback = false;
        bool synchronous_dns_lookup = false;
        bool generate_tun_builder_capture_event = false;
        std::string private_key_password;
        bool disable_client_cert = false;
        int ssl_debug_level = 0;
        int default_key_direction = -1;
        bool autologin_sessions = false;
        bool retry_on_auth_failed = false;
        bool allow_local_lan_access = false;
        bool preferred_security = true;
        std::string tls_version_min_override;
        std::string tls_cert_profile_override;
        std::string tls_cipher_list;
        std::string tls_ciphersuite_list;
        bool enable_legacy_algorithms = false;
        bool enable_nonpreferred_dcalgs = false;
        PeerInfo::Set::Ptr extra_peer_info;
#ifdef OPENVPN_PLATFORM_ANDROID
        bool enable_route_emulation = true;
#endif
#ifdef OPENVPN_GREMLIN
        Gremlin::Config::Ptr gremlin_config;
#endif
        Stop *stop = nullptr;

        // callbacks -- must remain in scope for lifetime of ClientOptions object
        ExternalPKIBase *external_pki = nullptr;
        SocketProtect *socket_protect = nullptr;
        ReconnectNotify *reconnect_notify = nullptr;
        RemoteList::RemoteOverride *remote_override = nullptr;

#if defined(USE_TUN_BUILDER)
        TunBuilderBase *builder = nullptr;
#endif

#if defined(OPENVPN_EXTERNAL_TUN_FACTORY)
        ExternalTun::Factory *extern_tun_factory = nullptr;
#endif

#if defined(OPENVPN_EXTERNAL_TRANSPORT_FACTORY)
        ExternalTransport::Factory *extern_transport_factory = nullptr;
#endif
    };

    ClientOptions(const OptionList &opt, // only needs to remain in scope for duration of constructor call
                  const Config &config)
        : server_addr_float(false),
          socket_protect(config.socket_protect),
          reconnect_notify(config.reconnect_notify),
          cli_stats(config.cli_stats),
          cli_events(config.cli_events),
          server_poll_timeout_(10),
          server_override(config.server_override),
          port_override(config.port_override),
          proto_override(config.proto_override),
          conn_timeout_(config.conn_timeout),
          tcp_queue_limit(64),
          proto_context_options(config.proto_context_options),
          http_proxy_options(config.http_proxy_options),
#ifdef OPENVPN_GREMLIN
          gremlin_config(config.gremlin_config),
#endif
          echo(config.echo),
          info(config.info),
          autologin(false),
          autologin_sessions(false),
          creds_locked(false),
          asio_work_always_on_(false),
          synchronous_dns_lookup(false),
          retry_on_auth_failed_(config.retry_on_auth_failed)
#ifdef OPENVPN_EXTERNAL_TRANSPORT_FACTORY
          ,
          extern_transport_factory(config.extern_transport_factory)
#endif
    {
        // parse general client options
        const ParseClientConfig pcc(opt);

        // creds
        userlocked_username = pcc.userlockedUsername();
        autologin = pcc.autologin();
        autologin_sessions = (autologin && config.autologin_sessions);

        // digest factory
        DigestFactory::Ptr digest_factory(new CryptoDigestFactory<SSLLib::CryptoAPI>());

        // initialize RNG/PRNG
        rng.reset(new SSLLib::RandomAPI(false));
        prng.reset(new SSLLib::RandomAPI(true));

        // frame
        // get tun-mtu and tun-mtu-max parameter from config
        const unsigned int tun_mtu = parse_tun_mtu(opt, 0);
        const unsigned int tun_mtu_max = std::max(parse_tun_mtu_max(opt, TUN_MTU_DEFAULT + 100), tun_mtu);

        const MSSCtrlParms mc(opt);
        frame = frame_init(true, tun_mtu_max, mc.mssfix_ctrl, true);

        // TCP queue limit
        tcp_queue_limit = opt.get_num<decltype(tcp_queue_limit)>("tcp-queue-limit", 1, tcp_queue_limit, 1, 65536);

        // route-nopull
        pushed_options_filter.reset(new PushedOptionsFilter(opt));

        // OpenVPN Protocol context (including SSL)
        cp_main = proto_config(opt, config, pcc, false);
        cp_relay = proto_config(opt, config, pcc, true); // may be null

        CryptoAlgs::allow_default_dc_algs<SSLLib::CryptoAPI>(cp_main->ssl_factory->libctx(),
                                                             !config.enable_nonpreferred_dcalgs,
                                                             config.enable_legacy_algorithms);

#if (defined(ENABLE_KOVPN) || defined(ENABLE_OVPNDCO) || defined(ENABLE_OVPNDCOWIN)) && !defined(OPENVPN_FORCE_TUN_NULL) && !defined(OPENVPN_EXTERNAL_TUN_FACTORY)
        if (config.dco)
#if defined(USE_TUN_BUILDER)
            dco = DCOTransport::new_controller(config.builder);
#else
            dco = DCOTransport::new_controller(nullptr);
#endif
#endif

        layer = cp_main->layer;

#ifdef PRIVATE_TUNNEL_PROXY
        if (config.alt_proxy && !dco)
            alt_proxy = PTProxy::new_proxy(opt, rng);
#endif

        // If HTTP proxy parameters are not supplied by API, try to get them from config
        if (!http_proxy_options)
            http_proxy_options = HTTPProxyTransport::Options::parse(opt);

        // load remote list
        if (config.remote_override)
        {
            remote_list.reset(new RemoteList(config.remote_override));
            remote_list->set_random(prng);
        }
        else
            remote_list.reset(new RemoteList(opt, "", RemoteList::WARN_UNSUPPORTED, nullptr, prng));
        if (!remote_list->defined())
            throw option_error("no remote option specified");

        // If running in tun_persist mode, we need to do basic DNS caching so that
        // we can avoid emitting DNS requests while the tunnel is blocked during
        // reconnections.
        remote_list->set_enable_cache(config.tun_persist);

        // process server/port/family overrides
        remote_list->set_server_override(config.server_override);
        remote_list->set_port_override(config.port_override);
        remote_list->set_proto_version_override(config.proto_version_override);

        // process protocol override, should be called after set_enable_cache
        remote_list->handle_proto_override(config.proto_override,
                                           http_proxy_options || (alt_proxy && alt_proxy->requires_tcp()));

        // process remote-random
        if (opt.exists("remote-random"))
            remote_list->randomize();

        // get "float" option
        server_addr_float = opt.exists("float");

        // special remote cache handling for proxies
        if (alt_proxy)
        {
            remote_list->set_enable_cache(false); // remote server addresses will be resolved by proxy
            alt_proxy->set_enable_cache(config.tun_persist);
        }
        else if (http_proxy_options)
        {
            remote_list->set_enable_cache(false); // remote server addresses will be resolved by proxy
            http_proxy_options->proxy_server_set_enable_cache(config.tun_persist);
        }

        check_for_incompatible_options(opt);

#ifdef OPENVPN_PLATFORM_UWP
        // workaround for OVPN3-62 Busy loop in win_event.hpp
        asio_work_always_on_ = true;
#endif

        synchronous_dns_lookup = config.synchronous_dns_lookup;

#ifdef OPENVPN_TLS_LINK
        if (opt.exists("tls-ca"))
        {
            tls_ca = opt.cat("tls-ca");
        }
#endif

        // init transport config
        const std::string session_name = load_transport_config();

        // initialize tun/tap
        if (dco)
        {
            DCO::TunConfig tunconf;
#if defined(OPENVPN_COMMAND_AGENT) && defined(OPENVPN_PLATFORM_WIN)
            tunconf.setup_factory = WinCommandAgent::new_agent(opt);
#endif
            tunconf.tun_prop.layer = layer;
            tunconf.tun_prop.session_name = session_name;
            if (tun_mtu)
                tunconf.tun_prop.mtu = tun_mtu;
            tunconf.tun_prop.mtu_max = tun_mtu_max;
            tunconf.tun_prop.google_dns_fallback = config.google_dns_fallback;
            tunconf.tun_prop.remote_list = remote_list;
            tunconf.stop = config.stop;
#if defined(OPENVPN_PLATFORM_WIN)
            if (config.tun_persist)
                tunconf.tun_persist.reset(new TunWin::DcoTunPersist(true, TunWrapObjRetain::NO_RETAIN_NO_REPLACE, nullptr));
#endif
            tun_factory = dco->new_tun_factory(tunconf, opt);
        }
        else
        {
#if defined(OPENVPN_EXTERNAL_TUN_FACTORY)
            {
                ExternalTun::Config tunconf;
                tunconf.tun_prop.layer = layer;
                tunconf.tun_prop.session_name = session_name;
                tunconf.tun_prop.google_dns_fallback = config.google_dns_fallback;
                if (tun_mtu)
                    tunconf.tun_prop.mtu = tun_mtu;
                tunconf.tun_prop.mtu_max = tun_mtu_max;
                tunconf.frame = frame;
                tunconf.stats = cli_stats;
                tunconf.tun_prop.remote_list = remote_list;
                tunconf.tun_persist = config.tun_persist;
                tunconf.stop = config.stop;
                tun_factory.reset(config.extern_tun_factory->new_tun_factory(tunconf, opt));
                if (!tun_factory)
                    throw option_error("OPENVPN_EXTERNAL_TUN_FACTORY: no tun factory");
            }
#elif defined(USE_TUN_BUILDER)
            {
                TunBuilderClient::ClientConfig::Ptr tunconf = TunBuilderClient::ClientConfig::new_obj();
                tunconf->builder = config.builder;
                tunconf->tun_prop.session_name = session_name;
                tunconf->tun_prop.google_dns_fallback = config.google_dns_fallback;
                tunconf->tun_prop.allow_local_lan_access = config.allow_local_lan_access;
                if (tun_mtu)
                    tunconf->tun_prop.mtu = tun_mtu;
                tunconf->tun_prop.mtu_max = tun_mtu_max;
                tunconf->frame = frame;
                tunconf->stats = cli_stats;
                tunconf->tun_prop.remote_list = remote_list;
                tun_factory = tunconf;
#if defined(OPENVPN_PLATFORM_IPHONE)
                tunconf->retain_sd = true;
                tunconf->tun_prefix = true;
                if (config.tun_persist)
                    tunconf->tun_prop.remote_bypass = true;
#endif
#if defined(OPENVPN_PLATFORM_ANDROID)
                // Android VPN API only supports excluded IP prefixes starting with Android 13/API 33,
                // so we must emulate them for earlier platforms
                if (config.enable_route_emulation)
                {
                    tunconf->eer_factory.reset(new EmulateExcludeRouteFactoryImpl(false));
                }
                else
                {
                    tunconf->eer_factory.reset(nullptr);
                }
#endif
#if defined(OPENVPN_PLATFORM_MAC)
                tunconf->tun_prefix = true;
#endif
                if (config.tun_persist)
                    tunconf->tun_persist.reset(new TunBuilderClient::TunPersist(true, tunconf->retain_sd ? TunWrapObjRetain::RETAIN : TunWrapObjRetain::NO_RETAIN, config.builder));
                tun_factory = tunconf;
            }
#elif defined(OPENVPN_PLATFORM_LINUX) && !defined(OPENVPN_FORCE_TUN_NULL)
            {
                TunLinux::ClientConfig::Ptr tunconf = TunLinux::ClientConfig::new_obj();
                tunconf->tun_prop.layer = layer;
                tunconf->tun_prop.session_name = session_name;
                if (tun_mtu)
                    tunconf->tun_prop.mtu = tun_mtu;
                tunconf->tun_prop.mtu_max = tun_mtu_max;
                tunconf->tun_prop.google_dns_fallback = config.google_dns_fallback;
                tunconf->generate_tun_builder_capture_event = config.generate_tun_builder_capture_event;
                tunconf->tun_prop.remote_list = remote_list;
                tunconf->frame = frame;
                tunconf->stats = cli_stats;
                if (config.tun_persist)
                    tunconf->tun_persist.reset(new TunLinux::TunPersist(true, TunWrapObjRetain::NO_RETAIN, nullptr));
                tunconf->load(opt);
                tun_factory = tunconf;
            }
#elif defined(OPENVPN_PLATFORM_MAC) && !defined(OPENVPN_FORCE_TUN_NULL)
            {
                TunMac::ClientConfig::Ptr tunconf = TunMac::ClientConfig::new_obj();
                tunconf->tun_prop.layer = layer;
                tunconf->tun_prop.session_name = session_name;
                tunconf->tun_prop.google_dns_fallback = config.google_dns_fallback;
                if (tun_mtu)
                    tunconf->tun_prop.mtu = tun_mtu;
                tunconf->tun_prop.mtu_max = tun_mtu_max;
                tunconf->frame = frame;
                tunconf->stats = cli_stats;
                tunconf->stop = config.stop;
                if (config.tun_persist)
                {
                    tunconf->tun_persist.reset(new TunMac::TunPersist(true, TunWrapObjRetain::NO_RETAIN, nullptr));
#ifndef OPENVPN_COMMAND_AGENT
                    /* remote_list is required by remote_bypass to work */
                    tunconf->tun_prop.remote_bypass = true;
                    tunconf->tun_prop.remote_list = remote_list;
#endif
                }
                client_lifecycle.reset(new MacLifeCycle);
#ifdef OPENVPN_COMMAND_AGENT
                tunconf->tun_setup_factory = UnixCommandAgent::new_agent(opt);
#endif
                tun_factory = tunconf;
            }
#elif defined(OPENVPN_PLATFORM_WIN) && !defined(OPENVPN_FORCE_TUN_NULL)
            {
                TunWin::ClientConfig::Ptr tunconf = TunWin::ClientConfig::new_obj();
                tunconf->tun_prop.layer = layer;
                tunconf->tun_prop.session_name = session_name;
                tunconf->tun_prop.google_dns_fallback = config.google_dns_fallback;
                if (tun_mtu)
                    tunconf->tun_prop.mtu = tun_mtu;
                tunconf->tun_prop.mtu_max = tun_mtu_max;
                tunconf->frame = frame;
                tunconf->stats = cli_stats;
                tunconf->stop = config.stop;
                tunconf->tun_type = config.wintun ? TunWin::Wintun : TunWin::TapWindows6;
                if (config.tun_persist)
                {
                    tunconf->tun_persist.reset(new TunWin::TunPersist(true, TunWrapObjRetain::NO_RETAIN, nullptr));
#ifndef OPENVPN_COMMAND_AGENT
                    /* remote_list is required by remote_bypass to work */
                    tunconf->tun_prop.remote_bypass = true;
                    tunconf->tun_prop.remote_list = remote_list;
#endif
                }
#ifdef OPENVPN_COMMAND_AGENT
                tunconf->tun_setup_factory = WinCommandAgent::new_agent(opt);
#endif
                tun_factory = tunconf;
            }
#else
            {
                TunNull::ClientConfig::Ptr tunconf = TunNull::ClientConfig::new_obj();
                tunconf->frame = frame;
                tunconf->stats = cli_stats;
                tun_factory = tunconf;
            }
#endif
        }

        // The Core Library itself does not handle TAP/OSI_LAYER_2 currently,
        // so we bail out early whenever someone tries to use TAP configurations
        if (layer == Layer(Layer::OSI_LAYER_2))
            throw ErrorCode(Error::TAP_NOT_SUPPORTED, true, "OSI layer 2 tunnels are not currently supported");

        // server-poll-timeout
        {
            const Option *o = opt.get_ptr("server-poll-timeout");
            if (o)
                server_poll_timeout_ = parse_number_throw<unsigned int>(o->get(1, 16), "server-poll-timeout");
        }

        // create default creds object in case submit_creds is not called,
        // and populate it with embedded creds, if available
        {
            ClientCreds::Ptr cc = new ClientCreds();
            if (pcc.hasEmbeddedPassword())
            {
                cc->set_username(userlocked_username);
                cc->set_password(pcc.embeddedPassword());
                cc->enable_password_cache(true);
                cc->set_replace_password_with_session_id(true);
                submit_creds(cc);
                creds_locked = true;
            }
            else if (autologin_sessions)
            {
                // autologin sessions require replace_password_with_session_id
                cc->set_replace_password_with_session_id(true);
                submit_creds(cc);
                creds_locked = true;
            }
            else
            {
                submit_creds(cc);
            }
        }

        // configure push_base, a set of base options that will be combined with
        // options pushed by server.
        {
            push_base.reset(new PushOptionsBase());

            // base options where multiple options of the same type can aggregate
            push_base->multi.extend(opt, "route");
            push_base->multi.extend(opt, "route-ipv6");
            push_base->multi.extend(opt, "redirect-gateway");
            push_base->multi.extend(opt, "redirect-private");
            push_base->multi.extend(opt, "dhcp-option");

            // base options which need to be merged, not just aggregated
            push_base->merge.extend(opt, "dns");

            // base options where only a single instance of each option makes sense
            push_base->singleton.extend(opt, "redirect-dns");
            push_base->singleton.extend(opt, "inactive");
            push_base->singleton.extend(opt, "route-metric");

            // IPv6
            {
                const unsigned int n6 = push_base->singleton.extend(opt, "block-ipv6");
                const unsigned int n4 = push_base->singleton.extend(opt, "block-ipv4");

                if (!n6 && config.allowUnusedAddrFamilies() == TriStateSetting::No)
                {
                    push_base->singleton.emplace_back("block-ipv6");
                }
                if (!n4 && config.allowUnusedAddrFamilies() == TriStateSetting::No)
                {
                    push_base->singleton.emplace_back("block-ipv4");
                }
            }
        }

        handle_unused_options(opt);
    }

    void check_for_incompatible_options(const OptionList &opt)
    {
        // secret option not supported
        if (opt.exists("secret"))
            throw option_error("sorry, static key encryption mode (non-SSL/TLS) is not supported");

        // fragment option not supported
        if (opt.exists("fragment"))
            throw option_error("sorry, 'fragment' directive is not supported, nor is connecting to a server that uses 'fragment' directive");

        // Only p2p mode accept
        if (opt.exists("mode"))
        {
            auto mode = opt.get("mode");
            if (mode.size() != 1 || mode.get(1, 128) != "p2p")
            {
                throw option_error("Only 'mode p2p' supported");
            }
        }
    }

    std::unordered_set<std::string> settings_ignoreWithWarning = {
        "allow-compression", /* TODO: maybe check against our client option compression setting? */
        "allow-recursive-routing",
        "auth-nocache",
        "auth-retry",
        "compat-mode",
        "connect-retry",
        "connect-retry-max",
        "connect-timeout", /* TODO: this should be really implemented */
        "data-ciphers",    /* TODO: maybe add more special warning that checks it against our supported ciphers */
        "data-ciphers-fallback",
        "disable-dco", /* TODO: maybe throw an error if DCO is active? */
        "disable-occ",
        "engine",
        "explicit-exit-notify", /* ignoring it in config does not break connection or functionality */
        "group",
        "ifconfig-nowarn", /* v3 does not do OCC checks */
        "ip-win32",
        "keepalive", /* A push only feature (ping/ping-restart) in v3. Ignore with warning since often present in configs too */
        "link-mtu",
        "machine-readable-output", /* would be set by a CliOptions */
        "mark",                    /* enables SO_MARK */
        "mute",
        "ncp-ciphers",
        "nice",
        "opt-verify",
        "passtos",
        "persist-key",
        "persist-tun",
        "preresolve",
        "providers", /* Done via client options */
        "remap-usr1",
        "reneg-bytes",
        "reneg-pkts",
        "replay-window",
        "resolv-retry",
        "route-method", /* Windows specific fine tuning option */
        "show-net-up",
        "socket-flags",
        "suppress-timestamps", /* harmless to ignore  */
        "tcp-nodelay",
        "tls-version-max", /* We don't allow restricting max version */
        "udp-mtu",         /* Alias for link-mtu */
        "user",
    };

    std::unordered_set<std::string> settings_serverOnlyOptions = {
        "auth-gen-token",
        "auth-gen-token-secret",
        "auth-user-pass-optional",
        "auth-user-pass-verify",
        "bcast-buffers",
        "ccd-exclusive",
        "client-config-dir",
        "client-connect",
        "client-disconnect",
        "client-to-client",
        "connect-freq",
        "dh",
        "disable",
        "duplicate-cn",
        "hash-size",
        "ifconfig-ipv6-pool",
        "ifconfig-pool",
        "ifconfig-pool-persist",
        "ifconfig-push",
        "ifconfig-push-constraint",
        "iroute",
        "iroute-ipv6",
        "max-clients",
        "max-routes-per-client",
        "push",
        "push-remove",
        "push-reset",
        "server",
        "server-bridge",
        "server-ipv6",
        "stale-routes-check",
        "tls-crypt-v2-verify",
        "username-as-common-name",
        "verify-client-cert",
        "vlan-accept",
        "vlan-pvid",
        "vlan-tagging",
    };

    /* Features not implemented and not safe to ignore */
    std::unordered_set<std::string> settings_feature_not_implemented_fatal = {
        "askpass",
        "capath",
        "cd",
        "chroot",
        "client-nat",
        "cryptoapicert",
        "daemon",
        "daemon",
        "errors-to-stderr",
        "gremlin",
        "lladdr",
        "log",
        "log",
        "log-append",
        "management",
        "memstats",
        "msg-channel", /* (Windows service in v2) */
        "ping-timer-rem",
        "single-session", /* This option is quite obscure but changes behaviour enough to not ignore it */
        "socks-proxy",
        "status",
        "status-version",
        "syslog",
        "tls-server",    /* No p2p mode in v3 */
        "tun-mtu-extra", /*(only really used in tap in OpenVPN 2.x)*/
        "verify-hash",
        "win-sys",
        "writepid",
        "x509-username-field",
    };

    /* Features not implemented but safe enough to ignore */
    std::unordered_set<std::string> settings_feature_not_implemented_warn = {
        "allow-pull-fqdn",
        "bind",
        "local",
        "lport",
        "mlock",
        "mtu-disc",
        "mtu-test",
        "persist-local-ip",
        "persist-remote-ip",
        "shaper",
        "tls-exit",
    };

    /* Push only options (some are allowed in the config in OpenVPN 2
     * but really push only options) */
    std::unordered_set<std::string> settings_pushonlyoptions = {
        "auth-token",
        "auth-token-user",
        "echo",
        "parameter",
        "ping",
        "ping-exit",
        "ping-restart", /* ping related options are pull only in v3, v2 needs them in the config for pure p2p */
        "key-derivation",
        "peer-id",
        "protocol-flags",
    };

    /* Features related to scripts/plugins */
    std::unordered_set<std::string> settings_script_plugin_feature = {
        "down",
        "down-pre",
        "ifconfig-noexec",
        "ipchange",
        "learn-address",
        "plugin",
        "route-noexec",
        "route-pre-down",
        "route-up",
        "setenv-safe",
        "tls-export-cert",
        "tls-verify",
        "up",
        "up-delay",
        "x509-track"};

    /* Standalone OpenVPN v2 modes */
    std::unordered_set<std::string> settings_standalone_options = {
        "genkey",
        "mktun",
        "rmtun",
        "show-ciphers",
        "show-curves",
        "show-digests",
        "show-engines",
        "show-groups",
        "show-tls",
        "test-crypto"};

    /* Deprecated/throwing error in OpenVPN 2.x already: */
    std::unordered_set<std::string> settings_removedOptions = {
        "mtu-dynamic", "no-replay", "no-name-remapping", "compat-names", "ncp-disable"};

    std::unordered_set<std::string> settings_ignoreSilently = {
        "ecdh-curve", /* Deprecated in v2, not needed with modern OpenSSL */
        "fast-io",
        "max-routes",
        "mute-replay-warnings",
        "nobind", /* only behaviour in v3 client anyway */
        "prng",
        "rcvbuf",         /* present in many configs */
        "replay-persist", /* Makes little sense in TLS mode */
        "script-security",
        "sndbuf",
        "tls-client", /* Always enabled */
        "tmp-dir",
        "tun-ipv6",   /* ignored in v2 as well */
        "txqueuelen", /* so platforms evaluate that in tun, some do not, do not warn about that */
        "verb"};

    /**
     * This groups all the options that OpenVPN 2.x supports that the
     * OpenVPN v3 client does not support into a number of different groups
     * and warns or errors out if with a specific message for that group.
     *
     * If any option that is not touched() after going through all groups
     * the function will print them as unknown unsupported option(s) and
     * error out
     */
    void handle_unused_options(const OptionList &opt)
    {
        /* Meta options that AS profiles often have that we do not parse and
         * can ignore without warning */
        std::unordered_set<std::string> ignoreMetaOptions = {
            "CLI_PREF_ALLOW_WEB_IMPORT",
            "CLI_PREF_BASIC_CLIENT",
            "CLI_PREF_ENABLE_CONNECT",
            "CLI_PREF_ENABLE_XD_PROXY",
            "WSHOST",
            "WEB_CA_BUNDLE",
            "IS_OPENVPN_WEB_CA",
            "OVPN_ACCESS_SERVER_NO_WEB",
        };

        std::unordered_set<std::string> ignore_unknown_option_list;

        if (opt.exists("ignore-unknown-option"))
        {
            auto igOptlist = opt.get_index("ignore-unknown-option");
            for (auto igUnOptIdx : igOptlist)
            {
                const Option &o = opt[igUnOptIdx];
                for (size_t i = 1; i < o.size(); i++)
                {
                    auto optionToIgnore = o.get(i, 0);

                    ignore_unknown_option_list.insert(optionToIgnore);
                }
                o.touch();
            }
        }

        for (const auto &o : opt)
        {
            if (!o.meta() && settings_ignoreSilently.find(o.get(0, 0)) != settings_ignoreSilently.end())
            {
                o.touch();
            }
            if (o.meta() && ignoreMetaOptions.find(o.get(0, 0)) != ignoreMetaOptions.end())
            {
                o.touch();
            }
        }

        /* Mark all options that will not trigger any kind of message
         * as touched to avoid an empty message with unused options */
        if (opt.n_unused() == 0)
            return;

        OPENVPN_LOG_NTNL("NOTE: This configuration contains options that were not used:" << std::endl);

        /* Go through all options and check all options that have not been
         * touched (parsed) yet */
        showUnusedOptionsByList(opt, settings_removedOptions, "Removed deprecated option", true);
        showUnusedOptionsByList(opt, settings_serverOnlyOptions, "Server only option", true);
        showUnusedOptionsByList(opt, settings_standalone_options, "OpenVPN 2.x command line operation", true);
        showUnusedOptionsByList(opt, settings_feature_not_implemented_warn, "Feature not implemented (option ignored)", false);
        showUnusedOptionsByList(opt, settings_pushonlyoptions, "Option allowed only to be pushed by the server", true);

        showUnusedOptionsByList(opt, settings_feature_not_implemented_warn, "feature not implemented/available", false);
        showUnusedOptionsByList(opt, settings_script_plugin_feature, "Ignored (no script/plugin support)", false);
        showUnusedOptionsByList(opt, ignore_unknown_option_list, "Ignored by option 'ignore-unknown-option'", false);
        showUnusedOptionsByList(opt, settings_ignoreWithWarning, "Unsupported option (ignored)", false);

        auto ignoredBySetenvOpt = [](const Option &option)
        { return !option.touched() && option.warnonlyunknown(); };
        showOptionsByFunction(opt, ignoredBySetenvOpt, "Ignored options prefixed with 'setenv opt'", false);

        auto unusedMetaOpt = [](const Option &option)
        { return !option.touched() && option.meta(); };
        showOptionsByFunction(opt, unusedMetaOpt, "Unused ignored meta options", false);

        auto managmentOpt = [](const Option &option)
        { return !option.touched() && option.get(0, 0).rfind("management", 0) == 0; };
        showOptionsByFunction(opt, managmentOpt, "OpenVPN management interface is not supported by this client", true);

        // If we still have options that are unaccounted for, we print them and throw an error or just warn about them
        auto onlyLightlyTouchedOptions = [](const Option &option)
        { return option.touched_lightly(); };
        showOptionsByFunction(opt, onlyLightlyTouchedOptions, "Unused options, probably specified multiple times in the configuration file", false);

        auto nonTouchedOptions = [](const Option &option)
        { return !option.touched() && !option.touched_lightly(); };
        showOptionsByFunction(opt, nonTouchedOptions, OPENVPN_UNUSED_OPTIONS, true);
    }

    void showUnusedOptionsByList(const OptionList &optlist, std::unordered_set<std::string> option_set, const std::string &message, bool fatal)
    {
        auto func = [&option_set](const Option &opt)
        { return !opt.touched() && option_set.find(opt.get(0, 0)) != option_set.end(); };
        showOptionsByFunction(optlist, func, message, fatal);
    }

    /* lambda expression that capture variables have complex signatures, avoid these by letting the compiler
     * itself figure it out with a template */
    template <typename T>
    void showOptionsByFunction(const OptionList &opt, T func, const std::string &message, bool fatal)
    {
        bool messageShown = false;
        for (size_t i = 0; i < opt.size(); ++i)
        {
            auto &o = opt[i];
            if (func(o))
            {
                if (!messageShown)
                {
                    OPENVPN_LOG(message);
                    messageShown = true;
                }
                o.touch();

                OPENVPN_LOG_NTNL(std::to_string(i) << ' ' << o.render(Option::RENDER_BRACKET | Option::RENDER_TRUNC_64) << std::endl);
            }
        }
        if (fatal && messageShown)
        {
            throw option_error("sorry, unsupported options present in configuration: " + message);
        }
    }

    static PeerInfo::Set::Ptr build_peer_info(const Config &config, const ParseClientConfig &pcc, const bool autologin_sessions)
    {
        PeerInfo::Set::Ptr pi(new PeerInfo::Set);

        // autologin sessions
        if (autologin_sessions)
            pi->emplace_back("IV_AUTO_SESS", "1");

        if (pcc.pushPeerInfo())
        {
            /* ensure that we use only one variable with the same name */
            std::unordered_map<std::string, std::string> extra_values;

            if (pcc.peerInfoUV())
            {
                for (auto const &kv : *pcc.peerInfoUV())
                {
                    extra_values[kv.key] = kv.value;
                }
            }

            /* Config::peerInfo takes precedence */
            if (config.extra_peer_info.get())
            {
                for (auto const &kv : *config.extra_peer_info.get())
                {
                    extra_values[kv.key] = kv.value;
                }
            }

            for (auto kv : extra_values)
            {
                pi->emplace_back(kv.first, kv.second);
            }
        }

        // UI version
        if (!config.gui_version.empty())
            pi->emplace_back("IV_GUI_VER", config.gui_version);

        // Supported SSO methods
        if (!config.sso_methods.empty())
            pi->emplace_back("IV_SSO", config.sso_methods);

        // MAC address
        if (pcc.pushPeerInfo())
        {
            std::string hwaddr = get_hwaddr();
            if (!config.hw_addr_override.empty())
                pi->emplace_back("IV_HWADDR", config.hw_addr_override);
            else if (!hwaddr.empty())
                pi->emplace_back("IV_HWADDR", hwaddr);
            pi->emplace_back("IV_SSL", get_ssl_library_version());

            if (!config.platform_version.empty())
                pi->emplace_back("IV_PLAT_VER", config.platform_version);
        }
        return pi;
    }

    void next(RemoteList::Advance type)
    {
        bool omit_next = false;

        if (alt_proxy)
            omit_next = alt_proxy->next();
        if (!omit_next)
            remote_list->next(type);
        load_transport_config();
    }

    void remote_reset_cache_item()
    {
        remote_list->reset_cache_item();
    }

    bool pause_on_connection_timeout()
    {
        if (reconnect_notify)
            return reconnect_notify->pause_on_connection_timeout();
        else
            return false;
    }

    bool retry_on_auth_failed() const
    {
        return retry_on_auth_failed_;
    }

    Client::Config::Ptr client_config(const bool relay_mode)
    {
        Client::Config::Ptr cli_config = new Client::Config;

        // Copy ProtoConfig so that modifications due to server push will
        // not persist across client instantiations.
        cli_config->proto_context_config.reset(new Client::ProtoConfig(proto_config_cached(relay_mode)));

        cli_config->proto_context_options = proto_context_options;
        cli_config->push_base = push_base;
        cli_config->transport_factory = transport_factory;
        cli_config->tun_factory = tun_factory;
        cli_config->cli_stats = cli_stats;
        cli_config->cli_events = cli_events;
        cli_config->creds = creds;
        cli_config->pushed_options_filter = pushed_options_filter;
        cli_config->tcp_queue_limit = tcp_queue_limit;
        cli_config->echo = echo;
        cli_config->info = info;
        cli_config->autologin_sessions = autologin_sessions;
        return cli_config;
    }

    bool need_creds() const
    {
        return !autologin;
    }

    void submit_creds(const ClientCreds::Ptr &creds_arg)
    {
        if (!creds_arg)
            return;

        // Override HTTP proxy credentials if provided dynamically
        if (http_proxy_options && creds_arg->http_proxy_username_defined())
            http_proxy_options->username = creds_arg->get_http_proxy_username();
        if (http_proxy_options && creds_arg->http_proxy_password_defined())
            http_proxy_options->password = creds_arg->get_http_proxy_password();

        if (!creds_locked)
        {
            // if no username is defined in creds and userlocked_username is defined
            // in profile, set the creds username to be the userlocked_username
            if (!creds_arg->username_defined() && !userlocked_username.empty())
                creds_arg->set_username(userlocked_username);
            creds = creds_arg;
        }
    }

    bool server_poll_timeout_enabled() const
    {
        return !http_proxy_options;
    }

    Time::Duration server_poll_timeout() const
    {
        return Time::Duration::seconds(server_poll_timeout_);
    }

    SessionStats &stats()
    {
        return *cli_stats;
    }
    const SessionStats::Ptr &stats_ptr() const
    {
        return cli_stats;
    }
    ClientEvent::Queue &events()
    {
        return *cli_events;
    }
    ClientLifeCycle *lifecycle()
    {
        return client_lifecycle.get();
    }

    int conn_timeout() const
    {
        return conn_timeout_;
    }

    bool asio_work_always_on() const
    {
        return asio_work_always_on_;
    }

    RemoteList::Ptr remote_list_precache() const
    {
        RemoteList::Ptr r;
        if (alt_proxy)
        {
            alt_proxy->precache(r);
            if (r)
                return r;
        }
        if (http_proxy_options)
        {
            http_proxy_options->proxy_server_precache(r);
            if (r)
                return r;
        }
        return remote_list;
    }

    void update_now()
    {
        now_.update();
    }

    void finalize(const bool disconnected)
    {
        if (tun_factory)
            tun_factory->finalize(disconnected);
    }

  private:
    Client::ProtoConfig &proto_config_cached(const bool relay_mode)
    {
        if (relay_mode && cp_relay)
            return *cp_relay;
        else
            return *cp_main;
    }

    Client::ProtoConfig::Ptr proto_config(const OptionList &opt,
                                          const Config &config,
                                          const ParseClientConfig &pcc,
                                          const bool relay_mode)
    {
        // relay mode is null unless one of the below directives is defined
        if (relay_mode && !opt.exists("relay-mode"))
            return Client::ProtoConfig::Ptr();

        // load flags
        unsigned int lflags = SSLConfigAPI::LF_PARSE_MODE;
        if (relay_mode)
            lflags |= SSLConfigAPI::LF_RELAY_MODE;

        // client SSL config
        SSLLib::SSLAPI::Config::Ptr cc(new SSLLib::SSLAPI::Config());
        cc->set_external_pki_callback(config.external_pki);
        cc->set_frame(frame);
        cc->set_flags(SSLConst::LOG_VERIFY_STATUS);
        cc->set_debug_level(config.ssl_debug_level);
        cc->set_rng(rng);
        cc->set_local_cert_enabled(pcc.clientCertEnabled() && !config.disable_client_cert);
        /* load depends on private key password and legacy algorithms */
        cc->enable_legacy_algorithms(config.enable_legacy_algorithms);
        cc->set_private_key_password(config.private_key_password);
        cc->load(opt, lflags);
        cc->set_tls_version_min_override(config.tls_version_min_override);
        cc->set_tls_cert_profile_override(config.tls_cert_profile_override);
        cc->set_tls_cipher_list(config.tls_cipher_list);
        cc->set_tls_ciphersuite_list(config.tls_ciphersuite_list);
        if (!cc->get_mode().is_client())
            throw option_error("only client configuration supported");

        // client ProtoContext config
        Client::ProtoConfig::Ptr cp(new Client::ProtoConfig());
        cp->ssl_factory = cc->new_factory();
        cp->relay_mode = relay_mode;
        cp->dc.set_factory(new CryptoDCSelect<SSLLib::CryptoAPI>(cp->ssl_factory->libctx(), frame, cli_stats, prng));
        cp->dc_deferred = true; // defer data channel setup until after options pull
        cp->tls_auth_factory.reset(new CryptoOvpnHMACFactory<SSLLib::CryptoAPI>());
        cp->tls_crypt_factory.reset(new CryptoTLSCryptFactory<SSLLib::CryptoAPI>());
        cp->tls_crypt_metadata_factory.reset(new CryptoTLSCryptMetadataFactory());
        cp->tlsprf_factory.reset(new CryptoTLSPRFFactory<SSLLib::CryptoAPI>());
        cp->load(opt, *proto_context_options, config.default_key_direction, false);
        cp->set_xmit_creds(!autologin || pcc.hasEmbeddedPassword() || autologin_sessions);
        cp->extra_peer_info = build_peer_info(config, pcc, autologin_sessions);
        cp->frame = frame;
        cp->now = &now_;
        cp->rng = rng;
        cp->prng = prng;

        return cp;
    }

    std::string load_transport_config()
    {
        // get current transport protocol
        const Protocol &transport_protocol = remote_list->current_transport_protocol();

        // If we are connecting over a proxy, and TCP protocol is required, but current
        // transport protocol is NOT TCP, we will throw an internal error because this
        // should have been caught earlier in RemoteList::handle_proto_override.

        // construct transport object
#ifdef OPENVPN_EXTERNAL_TRANSPORT_FACTORY
        ExternalTransport::Config transconf;
        transconf.remote_list = remote_list;
        transconf.frame = frame;
        transconf.stats = cli_stats;
        transconf.socket_protect = socket_protect;
        transconf.server_addr_float = server_addr_float;
        transconf.synchronous_dns_lookup = synchronous_dns_lookup;
        transconf.protocol = transport_protocol;
        transport_factory = extern_transport_factory->new_transport_factory(transconf);
#ifdef OPENVPN_GREMLIN
        udpconf->gremlin_config = gremlin_config;
#endif

#else
        if (dco)
        {
            DCO::TransportConfig transconf;
            transconf.protocol = transport_protocol;
            transconf.remote_list = remote_list;
            transconf.frame = frame;
            transconf.stats = cli_stats;
            transconf.server_addr_float = server_addr_float;
            transconf.socket_protect = socket_protect;
            transport_factory = dco->new_transport_factory(transconf);
        }
        else if (alt_proxy)
        {
            if (alt_proxy->requires_tcp() && !transport_protocol.is_tcp())
                throw option_error("internal error: no TCP server entries for " + alt_proxy->name() + " transport");
            AltProxy::Config conf;
            conf.remote_list = remote_list;
            conf.frame = frame;
            conf.stats = cli_stats;
            conf.digest_factory.reset(new CryptoDigestFactory<SSLLib::CryptoAPI>());
            conf.socket_protect = socket_protect;
            conf.rng = rng;
            transport_factory = alt_proxy->new_transport_client_factory(conf);
        }
        else if (http_proxy_options)
        {
            if (!transport_protocol.is_tcp())
                throw option_error("internal error: no TCP server entries for HTTP proxy transport");

            // HTTP Proxy transport
            HTTPProxyTransport::ClientConfig::Ptr httpconf = HTTPProxyTransport::ClientConfig::new_obj();
            httpconf->remote_list = remote_list;
            httpconf->frame = frame;
            httpconf->stats = cli_stats;
            httpconf->digest_factory.reset(new CryptoDigestFactory<SSLLib::CryptoAPI>());
            httpconf->socket_protect = socket_protect;
            httpconf->http_proxy_options = http_proxy_options;
            httpconf->rng = rng;
#ifdef PRIVATE_TUNNEL_PROXY
            httpconf->skip_html = true;
#endif
            transport_factory = httpconf;
        }
        else
        {
            if (transport_protocol.is_udp())
            {
                // UDP transport
                UDPTransport::ClientConfig::Ptr udpconf = UDPTransport::ClientConfig::new_obj();
                udpconf->remote_list = remote_list;
                udpconf->frame = frame;
                udpconf->stats = cli_stats;
                udpconf->socket_protect = socket_protect;
                udpconf->server_addr_float = server_addr_float;
#ifdef OPENVPN_GREMLIN
                udpconf->gremlin_config = gremlin_config;
#endif
                transport_factory = udpconf;
            }
            else if (transport_protocol.is_tcp()
#ifdef OPENVPN_TLS_LINK
                     || transport_protocol.is_tls()
#endif
            )
            {
                // TCP transport
                TCPTransport::ClientConfig::Ptr tcpconf = TCPTransport::ClientConfig::new_obj();
                tcpconf->remote_list = remote_list;
                tcpconf->frame = frame;
                tcpconf->stats = cli_stats;
                tcpconf->socket_protect = socket_protect;
#ifdef OPENVPN_TLS_LINK
                if (transport_protocol.is_tls())
                    tcpconf->use_tls = true;
                tcpconf->tls_ca = tls_ca;
#endif
#ifdef OPENVPN_GREMLIN
                tcpconf->gremlin_config = gremlin_config;
#endif
                transport_factory = tcpconf;
            }
            else
                throw option_error("internal error: unknown transport protocol");
        }
#endif // OPENVPN_EXTERNAL_TRANSPORT_FACTORY
        return remote_list->current_server_host();
    }

    Time now_; // current time
    RandomAPI::Ptr rng;
    RandomAPI::Ptr prng;
    Frame::Ptr frame;
    Layer layer;
    Client::ProtoConfig::Ptr cp_main;
    Client::ProtoConfig::Ptr cp_relay;
    RemoteList::Ptr remote_list;
    bool server_addr_float;
    TransportClientFactory::Ptr transport_factory;
    TunClientFactory::Ptr tun_factory;
    SocketProtect *socket_protect;
    ReconnectNotify *reconnect_notify;
    SessionStats::Ptr cli_stats;
    ClientEvent::Queue::Ptr cli_events;
    ClientCreds::Ptr creds;
    unsigned int server_poll_timeout_;
    std::string server_override;
    std::string port_override;
    Protocol proto_override;
    int conn_timeout_;
    unsigned int tcp_queue_limit;
    ProtoContextOptions::Ptr proto_context_options;
    HTTPProxyTransport::Options::Ptr http_proxy_options;
#ifdef OPENVPN_GREMLIN
    Gremlin::Config::Ptr gremlin_config;
#endif
    std::string userlocked_username;
    bool echo;
    bool info;
    bool autologin;
    bool autologin_sessions;
    bool creds_locked;
    bool asio_work_always_on_;
    bool synchronous_dns_lookup;
    bool retry_on_auth_failed_;
    PushOptionsBase::Ptr push_base;
    OptionList::FilterBase::Ptr pushed_options_filter;
    ClientLifeCycle::Ptr client_lifecycle;
    AltProxy::Ptr alt_proxy;
    DCO::Ptr dco;
#ifdef OPENVPN_EXTERNAL_TRANSPORT_FACTORY
    ExternalTransport::Factory *extern_transport_factory;
#endif
#ifdef OPENVPN_TLS_LINK
    std::string tls_ca;
#endif
};
} // namespace openvpn

#endif
