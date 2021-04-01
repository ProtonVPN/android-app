//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
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

// Implementation file for OpenVPNClient API defined in ovpncli.hpp.

#include <iostream>
#include <string>
#include <memory>
#include <utility>
#include <atomic>

#include <openvpn/io/io.hpp>

// Set up export of our public interface unless
// OPENVPN_CORE_API_VISIBILITY_HIDDEN is defined
#if defined(__GNUC__)
#define OPENVPN_CLIENT_EXPORT
#ifndef OPENVPN_CORE_API_VISIBILITY_HIDDEN
#pragma GCC visibility push(default)
#endif
#include "ovpncli.hpp" // public interface
#ifndef OPENVPN_CORE_API_VISIBILITY_HIDDEN
#pragma GCC visibility pop
#endif
#else
// no public interface export defined for this compiler
#define OPENVPN_CLIENT_EXPORT
#include "ovpncli.hpp" // public interface
#endif

// debug settings (production setting in parentheses)

//#define OPENVPN_DUMP_CONFIG          // dump parsed configuration (comment out)
//#define OPENVPN_DEBUG_CLIPROTO       // shows packets in/out (comment out)
#define OPENVPN_DEBUG_PROTO   1        // increases low-level protocol verbosity (1)
//#define OPENVPN_DEBUG_PROTO_DUMP     // dump hex of transport-layer packets, requires OPENVPN_DEBUG_CLIPROTO (comment out)
//#define OPENVPN_DEBUG_VERBOSE_ERRORS // verbosely log Error::Type errors (comment out)
#define OPENVPN_DEBUG_TUN     2        // debug level for tun object (2)
#define OPENVPN_DEBUG_UDPLINK 2        // debug level for UDP link object (2)
#define OPENVPN_DEBUG_TCPLINK 2        // debug level for TCP link object (2)
#define OPENVPN_DEBUG_COMPRESS 1       // debug level for compression objects (1)
#define OPENVPN_DEBUG_REMOTELIST 0     // debug level for RemoteList object (0)
#define OPENVPN_DEBUG_TUN_BUILDER 0    // debug level for tun/builder/client.hpp (0)
//#define OPENVPN_SHOW_SESSION_TOKEN   // show server-pushed auth-token (comment out)
//#define OPENVPN_DEBUG_TAPWIN           // shows Windows TAP driver debug logging (comment out)

// enable assertion checks (can safely be disabled in production)
//#define OPENVPN_ENABLE_ASSERT

// force null tun device (useful for testing)
//#define OPENVPN_FORCE_TUN_NULL

// log cleartext tunnel packets to file for debugging/analysis
//#define OPENVPN_PACKET_LOG "pkt.log"

#ifndef OPENVPN_LOG
// log thread settings
#define OPENVPN_LOG_CLASS openvpn::ClientAPI::LogReceiver
#define OPENVPN_LOG_INFO  openvpn::ClientAPI::LogInfo
#include <openvpn/log/logthread.hpp>    // should be included early
#endif

// log SSL handshake messages
#define OPENVPN_LOG_SSL(x) OPENVPN_LOG(x)

// on Android and iOS, use TunBuilderBase abstraction
#include <openvpn/common/platform.hpp>
#if (defined(OPENVPN_PLATFORM_ANDROID) || defined(OPENVPN_PLATFORM_IPHONE)) && !defined(OPENVPN_FORCE_TUN_NULL) && !defined(OPENVPN_EXTERNAL_TUN_FACTORY)
#define USE_TUN_BUILDER
#endif

#include <openvpn/init/initprocess.hpp>
#include <openvpn/common/bigmutex.hpp>
#include <openvpn/common/size.hpp>
#include <openvpn/common/platform_string.hpp>
#include <openvpn/common/count.hpp>
#include <openvpn/asio/asiostop.hpp>
#include <openvpn/time/asiotimer.hpp>
#include <openvpn/client/cliconnect.hpp>
#include <openvpn/client/cliopthelper.hpp>
#include <openvpn/options/merge.hpp>
#include <openvpn/error/excode.hpp>
#include <openvpn/crypto/selftest.hpp>

// copyright
#include <openvpn/legal/copyright.hpp>

namespace openvpn {
  namespace ClientAPI {

    OPENVPN_SIMPLE_EXCEPTION(app_expired);

    class MySessionStats : public SessionStats
    {
    public:
      typedef RCPtr<MySessionStats> Ptr;

      MySessionStats(OpenVPNClient* parent_arg)
	: parent(parent_arg)
      {
	std::memset(errors, 0, sizeof(errors));
#ifdef OPENVPN_DEBUG_VERBOSE_ERRORS
	session_stats_set_verbose(true);
#endif
      }

      static size_t combined_n()
      {
	return N_STATS + Error::N_ERRORS;
      }

      static std::string combined_name(const size_t index)
      {
	if (index < N_STATS + Error::N_ERRORS)
	  {
	    if (index < N_STATS)
	      return stat_name(index);
	    else
	      return Error::name(index - N_STATS);
	  }
	else
	  return "";
      }

      count_t combined_value(const size_t index) const
      {
	if (index < N_STATS + Error::N_ERRORS)
	  {
	    if (index < N_STATS)
	      return get_stat(index);
	    else
	      return errors[index - N_STATS];
	  }
	else
	  return 0;
      }

      count_t stat_count(const size_t index) const
      {
	return get_stat_fast(index);
      }

      count_t error_count(const size_t index) const
      {
	return errors[index];
      }

      void detach_from_parent()
      {
	parent = nullptr;
      }

      virtual void error(const size_t err, const std::string* text=nullptr)
      {
	if (err < Error::N_ERRORS)
	  {
#ifdef OPENVPN_DEBUG_VERBOSE_ERRORS
	    if (text)
	      OPENVPN_LOG("ERROR: " << Error::name(err) << " : " << *text);
	    else
	      OPENVPN_LOG("ERROR: " << Error::name(err));
#endif
	    ++errors[err];
	  }
      }

    private:
      OpenVPNClient* parent;
      count_t errors[Error::N_ERRORS];
    };

    class MyClientEvents : public ClientEvent::Queue
    {
    public:
      typedef RCPtr<MyClientEvents> Ptr;

      MyClientEvents(OpenVPNClient* parent_arg) : parent(parent_arg) {}

      virtual void add_event(ClientEvent::Base::Ptr event) override
      {
	if (parent)
	  {
	    Event ev;
	    ev.name = event->name();
	    ev.info = event->render();
	    ev.error = event->is_error();
	    ev.fatal = event->is_fatal();

	    // save connected event
	    if (event->id() == ClientEvent::CONNECTED)
	      last_connected = std::move(event);
	    else if (event->id() == ClientEvent::DISCONNECTED)
	      parent->on_disconnect();
	    parent->event(ev);
	  }
      }

      void get_connection_info(ConnectionInfo& ci)
      {
	ClientEvent::Base::Ptr connected = last_connected;
	if (connected)
	  {
	    const ClientEvent::Connected* c = connected->connected_cast();
	    if (c)
	      {
		ci.user = c->user;
		ci.serverHost = c->server_host;
		ci.serverPort = c->server_port;
		ci.serverProto = c->server_proto;
		ci.serverIp = c->server_ip;
		ci.vpnIp4 = c->vpn_ip4;
		ci.vpnIp6 = c->vpn_ip6;
		ci.gw4 = c->vpn_gw4;
		ci.gw6 = c->vpn_gw6;
		ci.clientIp = c->client_ip;
		ci.tunName = c->tun_name;
		ci.defined = true;
		return;
	      }
	  }
	ci.defined = false;
      }

      void detach_from_parent()
      {
	parent = nullptr;
      }

    private:
      OpenVPNClient* parent;
      ClientEvent::Base::Ptr last_connected;
    };

    class MySocketProtect : public SocketProtect
    {
    public:
      MySocketProtect() : parent(nullptr) {}

      void set_parent(OpenVPNClient* parent_arg)
      {
	parent = parent_arg;
      }

      void set_rg_local(bool rg_local_arg)
      {
        rg_local = rg_local_arg;
      }

      bool socket_protect(int socket, IP::Addr endpoint) override
      {
	if (parent)
	  {
#if defined(OPENVPN_COMMAND_AGENT) && defined(OPENVPN_PLATFORM_WIN)
	    return rg_local ? true : WinCommandAgent::add_bypass_route(endpoint);
#elif defined(OPENVPN_COMMAND_AGENT) && defined(OPENVPN_PLATFORM_MAC)
	    return rg_local ? true : UnixCommandAgent::add_bypass_route(endpoint);
#else
	    return parent->socket_protect(socket, endpoint.to_string(), endpoint.is_ipv6());
#endif
	  }
	else
	  return true;
      }

      void detach_from_parent()
      {
	parent = nullptr;
      }

    private:
      OpenVPNClient* parent;
      bool rg_local = false; // do not add bypass route if true
    };

    class MyReconnectNotify : public ReconnectNotify
    {
    public:
      MyReconnectNotify() : parent(nullptr) {}

      void set_parent(OpenVPNClient* parent_arg)
      {
	parent = parent_arg;
      }

      void detach_from_parent()
      {
	parent = nullptr;
      }

      virtual bool pause_on_connection_timeout()
      {
	if (parent)
	  return parent->pause_on_connection_timeout();
	else
	  return false;
      }

    private:
      OpenVPNClient* parent;
    };

    class MyRemoteOverride : public RemoteList::RemoteOverride
    {
    public:
      void set_parent(OpenVPNClient* parent_arg)
      {
	parent = parent_arg;
      }

      void detach_from_parent()
      {
	parent = nullptr;
      }

      virtual RemoteList::Item::Ptr get() override
      {
	if (parent)
	  {
	    const std::string title = "remote-override";
	    ClientAPI::RemoteOverride ro;
	    try {
	      parent->remote_override(ro);
	    }
	    catch (const std::exception& e)
	      {
		ro.error = e.what();
	      }
	    RemoteList::Item::Ptr ri(new RemoteList::Item);
	    if (ro.error.empty())
	      {
		if (!ro.ip.empty())
		  ri->set_ip_addr(IP::Addr(ro.ip, title));
		if (ro.host.empty())
		  ro.host = ro.ip;
		HostPort::validate_host(ro.host, title);
		HostPort::validate_port(ro.port, title);
		ri->server_host = std::move(ro.host);
		ri->server_port = std::move(ro.port);
		ri->transport_protocol = Protocol::parse(ro.proto, Protocol::CLIENT_SUFFIX, title.c_str());
	      }
	    else
	      throw Exception("remote override exception: " + ro.error);
	    return ri;
	  }
	else
	  return RemoteList::Item::Ptr();
      }

    private:
      OpenVPNClient* parent = nullptr;
    };

    class MyClockTick
    {
    public:
      MyClockTick(openvpn_io::io_context& io_context,
		  OpenVPNClient* parent_arg,
		  const unsigned int ms)
	: timer(io_context),
	  parent(parent_arg),
	  period(Time::Duration::milliseconds(ms))
      {
      }

      void cancel()
      {
	timer.cancel();
      }

      void detach_from_parent()
      {
	parent = nullptr;
      }

      void schedule()
      {
	timer.expires_after(period);
	timer.async_wait([this](const openvpn_io::error_code& error)
			 {
			   if (!parent || error)
			     return;
			   try {
			     parent->clock_tick();
			   }
			   catch (...)
			     {
			     }
			   schedule();
			 });
      }

    private:
      AsioTimer timer;
      OpenVPNClient* parent;
      const Time::Duration period;
    };

    namespace Private {
      class ClientState
      {
      public:
	// state objects
	OptionList options;
	EvalConfig eval;
	MySocketProtect socket_protect;
	MyReconnectNotify reconnect_notify;
	MyRemoteOverride remote_override;
	ClientCreds::Ptr creds;
	MySessionStats::Ptr stats;
	MyClientEvents::Ptr events;
	ClientConnect::Ptr session;
	std::unique_ptr<MyClockTick> clock_tick;

	// extra settings submitted by API client
	std::string server_override;
	std::string port_override;
	Protocol proto_override;
	IPv6Setting ipv6;
	int conn_timeout = 0;
	bool tun_persist = false;
	bool wintun = false;
	bool google_dns_fallback = false;
	bool synchronous_dns_lookup = false;
	bool autologin_sessions = false;
	bool retry_on_auth_failed = false;
	std::string private_key_password;
	std::string external_pki_alias;
	bool disable_client_cert = false;
	int ssl_debug_level = 0;
	int default_key_direction = -1;
	std::string tls_version_min_override;
	std::string tls_cert_profile_override;
	std::string tls_cipher_list;
	std::string tls_ciphersuite_list;
	std::string gui_version;
	std::string sso_methods;
	bool allow_local_lan_access;
	std::string hw_addr_override;
	std::string platform_version;
	ProtoContextOptions::Ptr proto_context_options;
	PeerInfo::Set::Ptr extra_peer_info;
	HTTPProxyTransport::Options::Ptr http_proxy_options;
	unsigned int clock_tick_ms = 0;
#ifdef OPENVPN_GREMLIN
	Gremlin::Config::Ptr gremlin_config;
#endif
	bool alt_proxy = false;
	bool dco = false;
	bool echo = false;
	bool info = false;

	// Ensure that init is called
	InitProcess::Init init;

	template <typename SESSION_STATS, typename CLIENT_EVENTS>
	void attach(OpenVPNClient* parent,
		    openvpn_io::io_context* io_context,
		    Stop* async_stop_global)
	{
	  // only one attachment per instantiation allowed
	  if (attach_called)
	    throw Exception("ClientState::attach() can only be called once per ClientState instantiation");
	  attach_called = true;

	  // async stop
	  async_stop_global_ = async_stop_global;

	  // io_context
	  if (io_context)
	    io_context_ = io_context;
	  else
	    {
	      io_context_ = new openvpn_io::io_context(1); // concurrency hint=1
	      io_context_owned = true;
	    }

	  // client stats
	  stats.reset(new SESSION_STATS(parent));

	  // client events
	  events.reset(new CLIENT_EVENTS(parent));

	  // socket protect
	  socket_protect.set_parent(parent);
	  RedirectGatewayFlags rg_flags{ options };
	  socket_protect.set_rg_local(rg_flags.redirect_gateway_local());

	  // reconnect notifications
	  reconnect_notify.set_parent(parent);

	  // remote override
	  remote_override.set_parent(parent);
	}

	ClientState() {}

	~ClientState()
	{
	  stop_scope_local.reset();
	  stop_scope_global.reset();
	  socket_protect.detach_from_parent();
	  reconnect_notify.detach_from_parent();
	  remote_override.detach_from_parent();
	  if (clock_tick)
	    clock_tick->detach_from_parent();
	  if (stats)
	    stats->detach_from_parent();
	  if (events)
	    events->detach_from_parent();
	  session.reset();
	  if (io_context_owned)
	    delete io_context_;
	}

	// foreign thread access

	void enable_foreign_thread_access()
	{
	  foreign_thread_ready.store(true, std::memory_order_release);
	}

	bool is_foreign_thread_access()
	{
	  return foreign_thread_ready.load(std::memory_order_acquire);
	}

	// io_context

	openvpn_io::io_context* io_context()
	{
	  return io_context_;
	}

	// async stop

	Stop* async_stop_local()
	{
	  return &async_stop_local_;
	}

	Stop* async_stop_global()
	{
	  return async_stop_global_;
	}

	void trigger_async_stop_local()
	{
	  async_stop_local_.stop();
	}

	// disconnect
	void on_disconnect()
	{
	  if (clock_tick)
	    clock_tick->cancel();
	}

	void setup_async_stop_scopes()
	{
	  stop_scope_local.reset(new AsioStopScope(*io_context(), async_stop_local(), [this]() {
	      OPENVPN_ASYNC_HANDLER;
	      session->graceful_stop();
	    }));

	  stop_scope_global.reset(new AsioStopScope(*io_context(), async_stop_global(), [this]() {
	      OPENVPN_ASYNC_HANDLER;
	      trigger_async_stop_local();
	    }));
	}

      private:
	ClientState(const ClientState&) = delete;
	ClientState& operator=(const ClientState&) = delete;

	bool attach_called = false;

	Stop async_stop_local_;
	Stop* async_stop_global_ = nullptr;

	std::unique_ptr<AsioStopScope> stop_scope_local;
	std::unique_ptr<AsioStopScope> stop_scope_global;

	openvpn_io::io_context* io_context_ = nullptr;
	bool io_context_owned = false;

	std::atomic<bool> foreign_thread_ready{false};
      };
    };

    OPENVPN_CLIENT_EXPORT OpenVPNClient::OpenVPNClient()
    {
#ifndef OPENVPN_NORESET_TIME
      // We keep track of time as binary milliseconds since a time base, and
      // this can wrap after ~48 days on 32 bit systems, so it's a good idea
      // to periodically reinitialize the base.
      Time::reset_base_conditional();
#endif

      state = new Private::ClientState();
      state->proto_context_options.reset(new ProtoContextOptions());
    }

    OPENVPN_CLIENT_EXPORT void OpenVPNClient::parse_config(const Config& config, EvalConfig& eval, OptionList& options)
    {
      try {
	// validate proto_override
	if (!config.protoOverride.empty())
	  Protocol::parse(config.protoOverride, Protocol::NO_SUFFIX);

	// validate IPv6 setting
	if (!config.ipv6.empty())
	  IPv6Setting::parse(config.ipv6);

	// parse config
	OptionList::KeyValueList kvl;
	kvl.reserve(config.contentList.size());
	for (size_t i = 0; i < config.contentList.size(); ++i)
	  {
	    const KeyValue& kv = config.contentList[i];
	    kvl.push_back(new OptionList::KeyValue(kv.key, kv.value));
	  }
	const ParseClientConfig cc = ParseClientConfig::parse(config.content, &kvl, options);
#ifdef OPENVPN_DUMP_CONFIG
	std::cout << "---------- ARGS ----------" << std::endl;
	std::cout << options.render(Option::RENDER_PASS_FMT|Option::RENDER_NUMBER|Option::RENDER_BRACKET) << std::endl;
	std::cout << "---------- MAP ----------" << std::endl;
	std::cout << options.render_map() << std::endl;
#endif
	eval.error = cc.error();
	eval.message = cc.message();
	eval.userlockedUsername = cc.userlockedUsername();
	eval.profileName = cc.profileName();
	eval.friendlyName = cc.friendlyName();
	eval.autologin = cc.autologin();
	eval.externalPki = cc.externalPki();
	eval.staticChallenge = cc.staticChallenge();
	eval.staticChallengeEcho = cc.staticChallengeEcho();
	eval.privateKeyPasswordRequired = cc.privateKeyPasswordRequired();
	eval.allowPasswordSave = cc.allowPasswordSave();
	eval.remoteHost = config.serverOverride.empty() ? cc.firstRemoteListItem().host : config.serverOverride;
	eval.remotePort = cc.firstRemoteListItem().port;
	eval.remoteProto = cc.firstRemoteListItem().proto;
	eval.windowsDriver = cc.windowsDriver();
	for (ParseClientConfig::ServerList::const_iterator i = cc.serverList().begin(); i != cc.serverList().end(); ++i)
	  {
	    ServerEntry se;
	    se.server = i->server;
	    se.friendlyName = i->friendlyName;
	    eval.serverList.push_back(se);
	  }
      }
      catch (const std::exception& e)
	{
	  eval.error = true;
	  eval.message = Unicode::utf8_printable<std::string>(std::string("ERR_PROFILE_GENERIC: ") + e.what(), 256);
	}
    }

    OPENVPN_CLIENT_EXPORT void OpenVPNClient::parse_extras(const Config& config, EvalConfig& eval)
    {
      try {
	state->server_override = config.serverOverride;
	state->port_override = config.portOverride;
	state->conn_timeout = config.connTimeout;
	state->tun_persist = config.tunPersist;
	state->wintun = config.wintun;
	state->google_dns_fallback = config.googleDnsFallback;
	state->synchronous_dns_lookup = config.synchronousDnsLookup;
	state->autologin_sessions = config.autologinSessions;
	state->retry_on_auth_failed = config.retryOnAuthFailed;
	state->private_key_password = config.privateKeyPassword;
	if (!config.protoOverride.empty())
	  state->proto_override = Protocol::parse(config.protoOverride, Protocol::NO_SUFFIX);
	if (!config.ipv6.empty())
	  state->ipv6 = IPv6Setting::parse(config.ipv6);
	if (!config.compressionMode.empty())
	  state->proto_context_options->parse_compression_mode(config.compressionMode);
	if (eval.externalPki)
	  state->external_pki_alias = config.externalPkiAlias;
	state->disable_client_cert = config.disableClientCert;
	state->ssl_debug_level = config.sslDebugLevel;
	state->default_key_direction = config.defaultKeyDirection;
	state->tls_version_min_override = config.tlsVersionMinOverride;
	state->tls_cert_profile_override = config.tlsCertProfileOverride;
	state->tls_cipher_list = config.tlsCipherList;
	state->tls_ciphersuite_list = config.tlsCiphersuitesList;
	state->allow_local_lan_access = config.allowLocalLanAccess;
	state->gui_version = config.guiVersion;
	state->sso_methods = config.ssoMethods;
	state->platform_version = config.platformVersion;
	state->hw_addr_override = config.hwAddrOverride;
	state->alt_proxy = config.altProxy;
	state->dco = config.dco;
	state->echo = config.echo;
	state->info = config.info;
	state->clock_tick_ms = config.clockTickMS;
	if (!config.gremlinConfig.empty())
	  {
#ifdef OPENVPN_GREMLIN
	  state->gremlin_config.reset(new Gremlin::Config(config.gremlinConfig));
#else
	  throw Exception("client not built with OPENVPN_GREMLIN");
#endif
	  }
	state->extra_peer_info = PeerInfo::Set::new_from_foreign_set(config.peerInfo);
	if (!config.proxyHost.empty())
	  {
	    HTTPProxyTransport::Options::Ptr ho(new HTTPProxyTransport::Options());
	    ho->set_proxy_server(config.proxyHost, config.proxyPort);
	    ho->username = config.proxyUsername;
	    ho->password = config.proxyPassword;
	    ho->allow_cleartext_auth = config.proxyAllowCleartextAuth;
	    state->http_proxy_options = ho;
	  }
      }
      catch (const std::exception& e)
	{
	  eval.error = true;
	  eval.message = Unicode::utf8_printable<std::string>(e.what(), 256);
	}
    }

    OPENVPN_CLIENT_EXPORT long OpenVPNClient::max_profile_size()
    {
      return ProfileParseLimits::MAX_PROFILE_SIZE;
    }

    OPENVPN_CLIENT_EXPORT MergeConfig OpenVPNClient::merge_config_static(const std::string& path,
									 bool follow_references)
    {
      ProfileMerge pm(path, "ovpn", "", follow_references ? ProfileMerge::FOLLOW_PARTIAL : ProfileMerge::FOLLOW_NONE,
		      ProfileParseLimits::MAX_LINE_SIZE, ProfileParseLimits::MAX_PROFILE_SIZE);
      return build_merge_config(pm);
    }

    OPENVPN_CLIENT_EXPORT MergeConfig OpenVPNClient::merge_config_string_static(const std::string& config_content)
    {
      ProfileMergeFromString pm(config_content, "", ProfileMerge::FOLLOW_NONE,
				ProfileParseLimits::MAX_LINE_SIZE, ProfileParseLimits::MAX_PROFILE_SIZE);
      return build_merge_config(pm);
    }

    OPENVPN_CLIENT_EXPORT MergeConfig OpenVPNClient::build_merge_config(const ProfileMerge& pm)
    {
      MergeConfig ret;
      ret.status = pm.status_string();
      ret.basename = pm.basename();
      if (pm.status() == ProfileMerge::MERGE_SUCCESS)
	{
	  ret.refPathList = pm.ref_path_list();
	  ret.profileContent = pm.profile_content();
	}
      else
	{
	  ret.errorText = pm.error();
	}
      return ret;
    }

    OPENVPN_CLIENT_EXPORT EvalConfig OpenVPNClient::eval_config_static(const Config& config)
    {
      EvalConfig eval;
      OptionList options;
      parse_config(config, eval, options);
      return eval;
    }

    // API client submits the configuration here before calling connect()
    OPENVPN_CLIENT_EXPORT EvalConfig OpenVPNClient::eval_config(const Config& config)
    {
      // parse and validate configuration file
      EvalConfig eval;
      parse_config(config, eval, state->options);
      if (eval.error)
	return eval;

      // handle extra settings in config
      parse_extras(config, eval);
      state->eval = eval;
      return eval;      
    }

    OPENVPN_CLIENT_EXPORT Status OpenVPNClient::provide_creds(const ProvideCreds& creds)
    {
      Status ret;
      try {
	ClientCreds::Ptr cc = new ClientCreds();
	cc->set_username(creds.username);
	cc->set_password(creds.password);
	cc->set_response(creds.response);
	cc->set_dynamic_challenge_cookie(creds.dynamicChallengeCookie, creds.username);
	cc->set_replace_password_with_session_id(creds.replacePasswordWithSessionID);
	cc->enable_password_cache(creds.cachePassword);
	state->creds = cc;
      }
      catch (const std::exception& e)
	{
	  ret.error = true;
	  ret.message = Unicode::utf8_printable<std::string>(e.what(), 256);
	}
      return ret;
    }

    OPENVPN_CLIENT_EXPORT bool OpenVPNClient::socket_protect(int socket, std::string remote, bool ipv6)
    {
      return true;
    }

    OPENVPN_CLIENT_EXPORT bool OpenVPNClient::parse_dynamic_challenge(const std::string& cookie, DynamicChallenge& dc)
    {
      try {
	ChallengeResponse cr(cookie);
	dc.challenge = cr.get_challenge_text();
	dc.echo = cr.get_echo();
	dc.responseRequired = cr.get_response_required();
	dc.stateID = cr.get_state_id();
	return true;
      }
      catch (const std::exception&)
	{
	  return false;
	}
    }

    OPENVPN_CLIENT_EXPORT void OpenVPNClient::process_epki_cert_chain(const ExternalPKICertRequest& req)
    {
      // Get cert and add to options list
      if (!req.cert.empty())
	{
	  Option o;
	  o.push_back("cert");
	  o.push_back(req.cert);
	  state->options.add_item(o);
	}

      // Get the supporting chain, if it exists, and use
      // it for ca (if ca isn't defined), or otherwise use
      // it for extra-certs (if ca is defined but extra-certs
      // is not).
      if (!req.supportingChain.empty())
	{
	  if (!state->options.exists("ca"))
	    {
	      Option o;
	      o.push_back("ca");
	      o.push_back(req.supportingChain);
	      state->options.add_item(o);
	    }
	  else if (!state->options.exists("extra-certs"))
	    {
	      Option o;
	      o.push_back("extra-certs");
	      o.push_back(req.supportingChain);
	      state->options.add_item(o);
	    }
	}
    }

    OPENVPN_CLIENT_EXPORT Status OpenVPNClient::connect()
    {
#if !defined(OPENVPN_OVPNCLI_SINGLE_THREAD)
      openvpn_io::detail::signal_blocker signal_blocker; // signals should be handled by parent thread
#endif
#if defined(OPENVPN_LOG_LOGTHREAD_H) && !defined(OPENVPN_LOG_LOGBASE_H)
#ifdef OPENVPN_LOG_GLOBAL
#error ovpn3 core logging object only supports thread-local scope
#endif
      Log::Context log_context(this);
#endif

      OPENVPN_LOG(ClientAPI::OpenVPNClient::platform());

      return do_connect();
    }

    OPENVPN_CLIENT_EXPORT Status OpenVPNClient::do_connect()
    {
      Status status;
      bool session_started = false;
      try {
	connect_attach();
#if defined(OPENVPN_OVPNCLI_ASYNC_SETUP)
	openvpn_io::post(*state->io_context(), [this]() {
	    do_connect_async();
	  });
#else
	connect_setup(status, session_started);
#endif
	connect_run();
	return status;
      }
      catch (const std::exception& e)
	{
	  if (session_started)
	    connect_session_stop();
	  return status_from_exception(e);
	}
    }

    OPENVPN_CLIENT_EXPORT void OpenVPNClient::do_connect_async()
    {
      enum StopType {
	NONE,
	SESSION,
	EXPLICIT,
      };
      StopType stop_type = NONE;
      Status status;
      bool session_started = false;
      try {
	connect_setup(status, session_started);
      }
      catch (const std::exception& e)
	{
	  stop_type = session_started ? SESSION : EXPLICIT;
	  status = status_from_exception(e);
	}
      if (status.error)
	{
	  ClientEvent::Base::Ptr ev = new ClientEvent::ClientSetup(status.status, status.message);
	  state->events->add_event(std::move(ev));
	}
      if (stop_type == SESSION)
	connect_session_stop();
#ifdef OPENVPN_IO_REQUIRES_STOP
      if (stop_type == EXPLICIT)
	state->io_context()->stop();
#endif
    }

    OPENVPN_CLIENT_EXPORT void OpenVPNClient::connect_setup(Status& status, bool& session_started)
    {
      // set global MbedTLS debug level
#if defined(USE_MBEDTLS) || defined(USE_MBEDTLS_APPLE_HYBRID)
      mbedtls_debug_set_threshold(state->ssl_debug_level); // fixme -- using a global method for this seems wrong
#endif

      // load options
      ClientOptions::Config cc;
      cc.cli_stats = state->stats;
      cc.cli_events = state->events;
      cc.server_override = state->server_override;
      cc.port_override = state->port_override;
      cc.proto_override = state->proto_override;
      cc.ipv6 = state->ipv6;
      cc.conn_timeout = state->conn_timeout;
      cc.tun_persist = state->tun_persist;
      cc.wintun = state->wintun;
      cc.google_dns_fallback = state->google_dns_fallback;
      cc.synchronous_dns_lookup = state->synchronous_dns_lookup;
      cc.autologin_sessions = state->autologin_sessions;
      cc.retry_on_auth_failed = state->retry_on_auth_failed;
      cc.proto_context_options = state->proto_context_options;
      cc.http_proxy_options = state->http_proxy_options;
      cc.alt_proxy = state->alt_proxy;
      cc.dco = state->dco;
      cc.echo = state->echo;
      cc.info = state->info;
      cc.reconnect_notify = &state->reconnect_notify;
      if (remote_override_enabled())
	cc.remote_override = &state->remote_override;
      cc.private_key_password = state->private_key_password;
      cc.disable_client_cert = state->disable_client_cert;
      cc.ssl_debug_level = state->ssl_debug_level;
      cc.default_key_direction = state->default_key_direction;
      cc.tls_version_min_override = state->tls_version_min_override;
      cc.tls_cert_profile_override = state->tls_cert_profile_override;
      cc.tls_cipher_list = state->tls_cipher_list;
      cc.tls_ciphersuite_list = state->tls_ciphersuite_list;
      cc.gui_version = state->gui_version;
      cc.sso_methods = state->sso_methods;
      cc.hw_addr_override = state->hw_addr_override;
      cc.platform_version = state->platform_version;
      cc.extra_peer_info = state->extra_peer_info;
      cc.stop = state->async_stop_local();
      cc.allow_local_lan_access = state->allow_local_lan_access;
#ifdef OPENVPN_GREMLIN
      cc.gremlin_config = state->gremlin_config;
#endif
      cc.socket_protect = &state->socket_protect;
#if defined(USE_TUN_BUILDER)
      cc.builder = this;
#endif
#if defined(OPENVPN_EXTERNAL_TUN_FACTORY)
      cc.extern_tun_factory = this;
#endif
#if defined(OPENVPN_EXTERNAL_TRANSPORT_FACTORY)
      cc.extern_transport_factory = this;
#endif
      // force Session ID use and disable password cache if static challenge is enabled
      if (state->creds
	  && !state->creds->get_replace_password_with_session_id()
	  && !state->eval.autologin
	  && !state->eval.staticChallenge.empty())
	{
	  state->creds->set_replace_password_with_session_id(true);
	  state->creds->enable_password_cache(false);
	}

      // external PKI
#if !defined(USE_APPLE_SSL)
      if (state->eval.externalPki && !state->disable_client_cert)
	{
	  if (!state->external_pki_alias.empty())
	    {
	      ExternalPKICertRequest req;
	      req.alias = state->external_pki_alias;
	      external_pki_cert_request(req);
	      if (!req.error)
		{
		  cc.external_pki = this;
		  process_epki_cert_chain(req);
		}
	      else
		{
		  external_pki_error(req, Error::EPKI_CERT_ERROR);
		  return;
		}
	    }
	  else
	    {
	      status.error = true;
	      status.message = "Missing External PKI alias";
	      return;
	    }
	}
#endif

#ifdef USE_OPENSSL
      if (state->options.exists("allow-name-constraints"))
	{
	  ClientEvent::Base::Ptr ev = new ClientEvent::UnsupportedFeature("allow-name-constraints",
									  "Always verified correctly with OpenSSL", false);
	  state->events->add_event(std::move(ev));
	}
#endif

      // build client options object
      ClientOptions::Ptr client_options = new ClientOptions(state->options, cc);

      // configure creds in options
      client_options->submit_creds(state->creds);

      // instantiate top-level client session
      state->session.reset(new ClientConnect(*state->io_context(), client_options));

      // convenience clock tick
      if (state->clock_tick_ms)
	{
	  state->clock_tick.reset(new MyClockTick(*state->io_context(), this, state->clock_tick_ms));
	  state->clock_tick->schedule();
	}

      // raise an exception if app has expired
      check_app_expired();

      // start VPN
      state->session->start(); // queue reads on socket/tun
      session_started = true;

      // wire up async stop
      state->setup_async_stop_scopes();

      // prepare to start reactor
      connect_pre_run();
      state->enable_foreign_thread_access();
    }

    OPENVPN_CLIENT_EXPORT Status OpenVPNClient::status_from_exception(const std::exception& e)
    {
      Status ret;
      ret.error = true;
      ret.message = Unicode::utf8_printable<std::string>(e.what(), 256);

      // if exception is an ExceptionCode, translate the code
      // to return status string
      {
	const ExceptionCode *ec = dynamic_cast<const ExceptionCode *>(&e);
	if (ec && ec->code_defined())
	  ret.status = Error::name(ec->code());
      }
      return ret;
    }

    OPENVPN_CLIENT_EXPORT void OpenVPNClient::connect_attach()
    {
      state->attach<MySessionStats, MyClientEvents>(this,
						    nullptr,
						    get_async_stop());
    }

    OPENVPN_CLIENT_EXPORT void OpenVPNClient::connect_pre_run()
    {
    }

    OPENVPN_CLIENT_EXPORT void OpenVPNClient::connect_run()
    {
      state->io_context()->run();
    }

    OPENVPN_CLIENT_EXPORT void OpenVPNClient::connect_session_stop()
    {
      state->session->stop();     // On exception, stop client...
      state->io_context()->poll();  //   and execute completion handlers.
    }

    OPENVPN_CLIENT_EXPORT ConnectionInfo OpenVPNClient::connection_info()
    {
      ConnectionInfo ci;
      if (state->is_foreign_thread_access())
	{
	  MyClientEvents* events = state->events.get();
	  if (events)
	    events->get_connection_info(ci);
	}
      return ci;
    }

    OPENVPN_CLIENT_EXPORT bool OpenVPNClient::session_token(SessionToken& tok)
    {
      if (state->is_foreign_thread_access())
	{
	  ClientCreds* cc = state->creds.get();
	  if (cc && cc->session_id_defined())
	    {
	      tok.username = cc->get_username();
	      tok.session_id = cc->get_password();
	      return true;
	    }
	}
      return false;
    }

    OPENVPN_CLIENT_EXPORT Stop* OpenVPNClient::get_async_stop()
    {
      return nullptr;
    }

    OPENVPN_CLIENT_EXPORT void OpenVPNClient::external_pki_error(const ExternalPKIRequestBase& req, const size_t err_type)
    {
      if (req.error)
	{
	  if (req.invalidAlias)
	    {
	      ClientEvent::Base::Ptr ev = new ClientEvent::EpkiInvalidAlias(req.alias);
	      state->events->add_event(std::move(ev));
	    }

	  ClientEvent::Base::Ptr ev = new ClientEvent::EpkiError(req.errorText);
	  state->events->add_event(std::move(ev));

	  state->stats->error(err_type);
	  if (state->session)
	    state->session->dont_restart();
	}
    }

    OPENVPN_CLIENT_EXPORT bool OpenVPNClient::sign(const std::string& data, std::string& sig, const std::string& algorithm)
    {
      ExternalPKISignRequest req;
      req.data = data;
      req.alias = state->external_pki_alias;
      req.algorithm = algorithm;
      external_pki_sign_request(req); // call out to derived class for RSA signature
      if (!req.error)
	{
	  sig = req.sig;
	  return true;
	}
      else
	{
	  external_pki_error(req, Error::EPKI_SIGN_ERROR);
	  return false;
	}
    }

    OPENVPN_CLIENT_EXPORT bool OpenVPNClient::remote_override_enabled()
    {
      return false;
    }

    OPENVPN_CLIENT_EXPORT void OpenVPNClient::remote_override(RemoteOverride&)
    {
    }

    OPENVPN_CLIENT_EXPORT int OpenVPNClient::stats_n()
    {
      return (int)MySessionStats::combined_n();
    }

    OPENVPN_CLIENT_EXPORT std::string OpenVPNClient::stats_name(int index)
    {
      return MySessionStats::combined_name(index);
    }

    OPENVPN_CLIENT_EXPORT long long OpenVPNClient::stats_value(int index) const
    {
      if (state->is_foreign_thread_access())
	{
	  MySessionStats* stats = state->stats.get();
	  if (stats)
	    {
	      if (index == SessionStats::BYTES_IN || index == SessionStats::BYTES_OUT)
		stats->dco_update();
	      return stats->combined_value(index);
	    }
	}
      return 0;
    }

    OPENVPN_CLIENT_EXPORT std::vector<long long> OpenVPNClient::stats_bundle() const
    {
      std::vector<long long> sv;
      const size_t n = MySessionStats::combined_n();
      sv.reserve(n);
      if (state->is_foreign_thread_access())
	{
	  MySessionStats* stats = state->stats.get();
	  if (stats)
	    stats->dco_update();
	  for (size_t i = 0; i < n; ++i)
	    sv.push_back(stats ? stats->combined_value(i) : 0);
	}
      else
	{
	  for (size_t i = 0; i < n; ++i)
	    sv.push_back(0);
	}
      return sv;
    }

    OPENVPN_CLIENT_EXPORT InterfaceStats OpenVPNClient::tun_stats() const
    {
      InterfaceStats ret;
      if (state->is_foreign_thread_access())
	{
	  MySessionStats* stats = state->stats.get();

	  // The reason for the apparent inversion between in/out below is
	  // that TUN_*_OUT stats refer to data written to tun device,
	  // but from the perspective of tun interface, this is incoming
	  // data.  Vice versa for TUN_*_IN.
	  if (stats)
	    {
	      ret.bytesOut = stats->stat_count(SessionStats::TUN_BYTES_IN);
	      ret.bytesIn = stats->stat_count(SessionStats::TUN_BYTES_OUT);
	      ret.packetsOut = stats->stat_count(SessionStats::TUN_PACKETS_IN);
	      ret.packetsIn = stats->stat_count(SessionStats::TUN_PACKETS_OUT);
	      ret.errorsOut = stats->error_count(Error::TUN_READ_ERROR);
	      ret.errorsIn = stats->error_count(Error::TUN_WRITE_ERROR);
	      return ret;
	    }
	}

      ret.bytesOut = 0;
      ret.bytesIn = 0;
      ret.packetsOut = 0;
      ret.packetsIn = 0;
      ret.errorsOut = 0;
      ret.errorsIn = 0;
      return ret;
    }

    OPENVPN_CLIENT_EXPORT TransportStats OpenVPNClient::transport_stats() const
    {
      TransportStats ret;
      ret.lastPacketReceived = -1; // undefined

      if (state->is_foreign_thread_access())
	{
	  MySessionStats* stats = state->stats.get();
	  if (stats)
	    {
	      stats->dco_update();
	      ret.bytesOut = stats->stat_count(SessionStats::BYTES_OUT);
	      ret.bytesIn = stats->stat_count(SessionStats::BYTES_IN);
	      ret.packetsOut = stats->stat_count(SessionStats::PACKETS_OUT);
	      ret.packetsIn = stats->stat_count(SessionStats::PACKETS_IN);

	      // calculate time since last packet received
	      {
		const Time& lpr = stats->last_packet_received();
		if (lpr.defined())
		  {
		    const Time::Duration dur = Time::now() - lpr;
		    const unsigned int delta = (unsigned int)dur.to_binary_ms();
		    if (delta <= 60*60*24*1024) // only define for time periods <= 1 day
		      ret.lastPacketReceived = delta;
		  }
	      }
	      return ret;
	    }
	}

      ret.bytesOut = 0;
      ret.bytesIn = 0;
      ret.packetsOut = 0;
      ret.packetsIn = 0;
      return ret;
    }

    OPENVPN_CLIENT_EXPORT void OpenVPNClient::stop()
    {
      if (state->is_foreign_thread_access())
	state->trigger_async_stop_local();
    }

    OPENVPN_CLIENT_EXPORT void OpenVPNClient::pause(const std::string& reason)
    {
      if (state->is_foreign_thread_access())
	{
	  ClientConnect* session = state->session.get();
	  if (session)
	    session->thread_safe_pause(reason);
	}
    }

    OPENVPN_CLIENT_EXPORT void OpenVPNClient::resume()
    {
      if (state->is_foreign_thread_access())
	{
	  ClientConnect* session = state->session.get();
	  if (session)
	    session->thread_safe_resume();
	}
    }

    OPENVPN_CLIENT_EXPORT void OpenVPNClient::reconnect(int seconds)
    {
      if (state->is_foreign_thread_access())
	{
	  ClientConnect* session = state->session.get();
	  if (session)
	    session->thread_safe_reconnect(seconds);
	}
    }

    OPENVPN_CLIENT_EXPORT void OpenVPNClient::post_cc_msg(const std::string& msg)
    {
      if (state->is_foreign_thread_access())
	{
	  ClientConnect* session = state->session.get();
	  if (session)
	    session->thread_safe_post_cc_msg(msg);
	}
    }

    OPENVPN_CLIENT_EXPORT void OpenVPNClient::clock_tick()
    {
    }

    OPENVPN_CLIENT_EXPORT void OpenVPNClient::on_disconnect()
    {
      state->on_disconnect();
    }

    OPENVPN_CLIENT_EXPORT std::string OpenVPNClient::crypto_self_test()
    {
      return SelfTest::crypto_self_test();
    }

    OPENVPN_CLIENT_EXPORT int OpenVPNClient::app_expire()
    {
#ifdef APP_EXPIRE_TIME
      return APP_EXPIRE_TIME;
#else
      return 0;
#endif
    }

    OPENVPN_CLIENT_EXPORT void OpenVPNClient::check_app_expired()
    {
#ifdef APP_EXPIRE_TIME
      if (Time::now().seconds_since_epoch() >= APP_EXPIRE_TIME)
	throw app_expired();
#endif
    }

    OPENVPN_CLIENT_EXPORT std::string OpenVPNClient::copyright()
    {
      return openvpn_copyright;
    }

    OPENVPN_CLIENT_EXPORT std::string OpenVPNClient::platform()
    {
      std::string ret = platform_string();
#ifdef PRIVATE_TUNNEL_PROXY
      ret += " PT_PROXY";
#endif
#ifdef ENABLE_KOVPN
      ret += " KOVPN";
#elif ENABLE_OVPNDCO
      ret += " OVPN-DCO";
#endif
#ifdef OPENVPN_GREMLIN
      ret += " GREMLIN";
#endif
#ifdef OPENVPN_DEBUG
      ret += " built on " __DATE__ " " __TIME__;
#endif
      return ret;
    }

    OPENVPN_CLIENT_EXPORT OpenVPNClient::~OpenVPNClient()
    {
      delete state;
    }
  }
}
