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

// OpenVPN 3 client with Management Interface

#define OMI_VERSION "1.0.0"

#include <string>
#include <vector>
#include <thread>
#include <memory>
#include <utility>
#include <mutex>
#include <condition_variable>

// don't export core symbols
#define OPENVPN_CORE_API_VISIBILITY_HIDDEN

// should be included before other openvpn includes,
// with the exception of openvpn/log includes
#include <client/ovpncli.cpp>

#include <openvpn/common/platform.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/file.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/to_string.hpp>
#include <openvpn/common/platform_string.hpp>
#include <openvpn/common/options.hpp>
#include <openvpn/asio/asiosignal.hpp>
#include <openvpn/common/stop.hpp>
#include <openvpn/time/asiotimersafe.hpp>
#include <openvpn/omi/omi.hpp>

using namespace openvpn;

std::string log_version()
{
  return platform_string("OpenVPN Management Interface", OMI_VERSION)
    + " [" SSL_LIB_NAME "] - server starting";
}

class OMI;

class Client : public ClientAPI::OpenVPNClient
{
public:
  Client(OMI* omi)
    : parent(omi)
  {
  }

private:
  bool socket_protect(int socket, std::string remote, bool ipv6) override
  {
    return true;
  }

  virtual bool pause_on_connection_timeout() override
  {
    return false;
  }

  virtual void event(const ClientAPI::Event& ev) override;
  virtual void log(const ClientAPI::LogInfo& msg) override;
  virtual void external_pki_cert_request(ClientAPI::ExternalPKICertRequest& certreq) override;
  virtual void external_pki_sign_request(ClientAPI::ExternalPKISignRequest& signreq) override;

  OMI* parent;
};

class OMI : public OMICore, public ClientAPI::LogReceiver
{
public:
  typedef RCPtr<OMI> Ptr;

  OMI(openvpn_io::io_context& io_context, OptionList opt_arg)
    : OMICore(io_context),
      opt(std::move(opt_arg)),
      reconnect_timer(io_context),
      bytecount_timer(io_context),
      exit_event(io_context),
      log_context(this)
  {
    signals.reset(new ASIOSignals(io_context));
    signal_rearm();
  }

  void start()
  {
    log_setup(OMICore::LogFn(opt));

    OPENVPN_LOG(log_version());

    // command line options
    connection_timeout = opt.get_num<decltype(connection_timeout)>("connection-timeout", 1, 30);
    management_query_passwords = opt.exists("management-query-passwords");
    auth_nocache = opt.exists("auth-nocache");
    management_external_key = opt.exists("management-external-key");
    proto_override = opt.get_default("proto-force", 1, 16, "adaptive");
    remote_override = opt.get_optional("remote-override", 1, 256);
    management_up_down = opt.exists("management-up-down");
    management_query_remote = opt.exists("management-query-remote");
    exit_event_name = opt.get_optional("exit-event-name", 1, 256);

    // passed by OpenVPN GUI to trigger exit
    if (!exit_event_name.empty())
    {
        exit_event.assign(::CreateEvent(NULL, FALSE, FALSE, exit_event_name.c_str()));
        exit_event.async_wait([self = Ptr(this)](const openvpn_io::error_code& error)
			      {
				if (error)
				  return;
				self->stop();
			      });
    }

    // http-proxy-override
    {
      const Option* o = opt.get_ptr("http-proxy-override");
      if (o)
	{
	  http_proxy_host = o->get(1, 128);
	  http_proxy_port = o->get(2, 16);
	}
    }

    // begin listening/connecting on OMI port
    OMICore::start(opt);
  }

  virtual void log(const ClientAPI::LogInfo& msg) override
  {
    openvpn_io::post(io_context, [this, msg]() {
	log_msg(msg);
      });
  }

  void event(const ClientAPI::Event& ev)
  {
    openvpn_io::post(io_context, [this, ev]() {
	event_msg(ev, nullptr);
      });
  }

  void event(const ClientAPI::Event& ev, const ClientAPI::ConnectionInfo& ci)
  {
    openvpn_io::post(io_context, [this, ev, ci]() {
	event_msg(ev, &ci);
      });
  }

  void external_pki_cert_request(ClientAPI::ExternalPKICertRequest& certreq)
  {
    // not currently supported, <cert> must be in config
  }

  void external_pki_sign_request(ClientAPI::ExternalPKISignRequest& signreq)
  {
    try {
      // publish signreq to main thread
      {
	std::lock_guard<std::mutex> lock(epki_mutex);
	epki_signreq = &signreq;
      }

      // message main thread that signreq is published and pending
      openvpn_io::post(io_context, [this]() {
	  epki_sign_request();
	});

      // allow asynchronous stop
      Stop::Scope stop_scope(&async_stop, [this, &signreq]() {
	  {
	    std::lock_guard<std::mutex> lock(epki_mutex);
	    epki_signreq = nullptr;
	    signreq.error = true;
	    signreq.errorText = "External PKI OMI: stop";
	  }
	  epki_cv.notify_all();
	});

      // wait for main thread to signal readiness by nulling epki_signreq ptr
      {
	std::unique_lock<std::mutex> lock(epki_mutex);
	epki_cv.wait(lock, [this]() {
	    return epki_signreq == nullptr;
	  });
      }
    }
    catch (const std::exception& e)
      {
	std::lock_guard<std::mutex> lock(epki_mutex);
	epki_signreq = nullptr;
	signreq.error = true;
	signreq.errorText = std::string("External PKI OMI: ") + e.what();
      }
  }

  void epki_sign_request()
  {
    std::string sr;
    {
      std::lock_guard<std::mutex> lock(epki_mutex);
      if (epki_signreq)
	sr = epki_signreq->data;
    }
    send(">RSA_SIGN:" + sr + "\r\n");
  }

  void epki_sign_reply(const Command& cmd)
  {
    // get base64 signature from command
    std::string sig64;
    for (auto &line : cmd.extra)
      {
	sig64 += line;
      }

    // commit to connection thread
    bool fail = false;
    {
      std::lock_guard<std::mutex> lock(epki_mutex);
      if (epki_signreq)
	{
	  epki_signreq->sig = sig64;
	  epki_signreq = nullptr;
	}
      else
	fail = true;
    }
    if (fail)
      send("ERROR: unsolicited rsa-sig command\r\n");
    else
      {
	epki_cv.notify_all();
	send("SUCCESS: rsa-sig command succeeded\r\n");
      }
  }

  virtual bool omi_command_is_multiline(const std::string& arg0, const Option& o) override
  {
    if (arg0 == "rsa-sig")
      return true;
    return false;
  }

private:
  virtual bool omi_command_in(const std::string& arg0, const Command& cmd) override
  {
    switch (arg0.at(0))
      {
      case 'p':
	{
	  if (is_auth_cmd(arg0))
	    {
	      process_auth_cmd(cmd.option);
	      return false;
	    }
	  break;
	}
      case 'r':
	{
	  if (arg0 == "remote")
	    {
	      process_remote_cmd(cmd.option);
	      return false;
	    }
	  else if (arg0 == "rsa-sig")
	    {
	      epki_sign_reply(cmd);
	      return false;
	    }
	  break;
	}
      case 'u':
	{
	  if (is_auth_cmd(arg0))
	    {
	      process_auth_cmd(cmd.option);
	      return false;
	    }
	  break;
	}
      }
    send("ERROR: unknown command, enter 'help' for more options\r\n");
    return false;
  }

  virtual void omi_done(const bool eof) override
  {
    //OPENVPN_LOG("OMI DONE eof=" << eof);
  }

  std::vector<ClientAPI::KeyValue> get_peer_info() const
  {
    std::vector<ClientAPI::KeyValue> ret;
    OptionList::IndexMap::const_iterator se = opt.map().find("setenv");
    if (se != opt.map().end())
      {
	for (OptionList::IndexList::const_iterator i = se->second.begin(); i != se->second.end(); ++i)
	  {
	    const Option& o = opt[*i];
	    o.touch();
	    const std::string& k = o.get(1, 64);
	    if (string::starts_with(k, "IV_") || string::starts_with(k, "UV_"))
	      {
		const std::string& v = o.get(2, 256);
		ret.emplace_back(k, v);
	      }
	  }
      }
    return ret;
  }

  virtual void omi_start_connection() override
  {
    try {
      //OPENVPN_LOG("OMI START CONNECTION");

      // reset state
      reconnect_timer.cancel();
      reconnect_reason = "";

      if (!config)
	{
	  config.reset(new ClientAPI::Config);
	  config->guiVersion = "ovpnmi " OMI_VERSION;
	  config->content = get_config(opt);
	  config->peerInfo = get_peer_info();
	  config->connTimeout = connection_timeout;
	  config->protoOverride = proto_override;
	  config->serverOverride = remote_override;
	  config->tunPersist = true;
	  config->googleDnsFallback = true;
	  config->autologinSessions = true;
	  config->compressionMode = "yes";
	  config->proxyHost = http_proxy_host;
	  config->proxyPort = http_proxy_port;
	  config->echo = true;

	  if (management_external_key)
	    config->externalPkiAlias = "EPKI"; // dummy alias

	  did_query_remote = false;
	}

      const ClientAPI::EvalConfig eval = Client::eval_config_static(*config);
      if (eval.error)
	OPENVPN_THROW_EXCEPTION("eval config error: " << eval.message);

      autologin = eval.autologin;

      // for compatibility with openvpn2
      if (eval.windowsDriver == "wintun")
	config->wintun = true;

      if (!eval.autologin && management_query_passwords && !creds)
	query_username_password("Auth", !dc_cookie.empty(), eval.staticChallenge, eval.staticChallengeEcho);
      else if (proxy_need_creds)
	query_username_password("HTTP Proxy", false, "", false);
      else if (management_query_remote && !did_query_remote)
	query_remote(eval.remoteHost, eval.remotePort, eval.remoteProto);
      else
	start_connection_thread();
    }
    catch (const std::exception& e)
      {
	set_final_error(e.what());
	stop();
      }
  }

  void query_username_password(const std::string& type,
			       const bool password_only,
			       const std::string& static_challenge,
			       const bool static_challenge_echo)
  {
    reset_auth_cmd();
    auth_type = type;
    auth_password_only = password_only;

    std::string notify = ">PASSWORD:Need '" + type + "' ";
    if (password_only)
      notify += "password";
    else
      notify += "username/password";

    // static challenge
    if (!static_challenge.empty())
      {
	notify += " SC:";
	if (static_challenge_echo)
	  notify += '1';
	else
	  notify += '0';
	notify += ',';
	notify += static_challenge;
      }

    notify += "\r\n";
    send(notify);
  }

  bool is_auth_cmd(const std::string& arg0) const
  {
    return arg0 == "username" || arg0 == "password";
  }

  void process_auth_cmd(const Option& o)
  {
    const std::string up = o.get(0, 0);
    const std::string type = o.get(1, 64);
    const std::string cred = o.get(2, 512);
    if (auth_type.empty() || type != auth_type || (up == "username" && auth_password_only))
      {
	send("ERROR: no " + up + " is currently needed at this time\r\n");
	return;
      }
    bool changed = false;
    if (up == "username")
      {
	auth_username = cred;
	changed = true;
      }
    else if (up == "password")
      {
	auth_password = cred;
	changed = true;
      }
    if (changed)
      send("SUCCESS: '" + auth_type + "' " + up + " entered, but not yet verified\r\n");
    if ((!auth_username.empty() || auth_password_only) && !auth_password.empty())
      {
	provide_username_password(auth_type, auth_username, auth_password);
	reset_auth_cmd();
      }
  }

  void reset_auth_cmd()
  {
    auth_type = "";
    auth_password_only = false;
    auth_username = "";
    auth_password = "";
  }

  void provide_username_password(const std::string& type, const std::string& username, const std::string& password)
  {
    if (!dc_cookie.empty())
      {
	creds.reset(new ClientAPI::ProvideCreds);
	creds->dynamicChallengeCookie = dc_cookie;
	try {
	  // response could be whole challenge string in case of connect 2.x
	  ChallengeResponse cr{ auth_password };
	  creds->response = std::string{ cr.get_challenge_text() };
	}
	catch (const ChallengeResponse::dynamic_challenge_parse_error&) {
	  // response contains only challenge text
	  creds->response = auth_password;
	}
	creds->cachePassword = !auth_nocache;
	creds->replacePasswordWithSessionID = true;
      }
    else if (type == "Auth")
      {
	creds.reset(new ClientAPI::ProvideCreds);
	creds->username = username;
	creds->password = password;
	creds->replacePasswordWithSessionID = true;
	creds->cachePassword = !auth_nocache;
      }
    else if (type == "HTTP Proxy")
      {
	if (config)
	  {
	    config->proxyUsername = username;
	    config->proxyPassword = password;
	  }
	proxy_need_creds = false;
      }
    omi_start_connection();
  }

  void query_remote(const std::string& host, const std::string& port, const std::string& proto)
  {
    send(">REMOTE:" + host + ',' + port + ',' + proto + "\r\n");
    remote_pending = true;
  }

  void process_remote_cmd(const Option& o)
  {
    if (!remote_pending)
      {
	send("ERROR: no pending remote query\r\n");
	return;
      }

    std::string host;
    std::string port;
    bool mod = false;

    const std::string type = o.get(1, 16);
    if (type == "MOD")
      {
	host = o.get(2, 256);
	port = o.get_optional(3, 16);
	mod = true;
      }
    else if (type == "ACCEPT")
      {
	;
      }
    else
      {
	send("ERROR: remote type must be MOD or ACCEPT\r\n");
	return;
      }

    send("SUCCESS: remote command succeeded\r\n");
    remote_pending = false;

    if (mod && config)
      {
	config->serverOverride = host;
	// fixme -- override port
      }
    did_query_remote = true;
    omi_start_connection();
  }

  void schedule_bytecount_timer()
  {
    if (get_bytecount())
      {
	bytecount_timer.expires_after(Time::Duration::seconds(get_bytecount()));
	bytecount_timer.async_wait([self=Ptr(this)](const openvpn_io::error_code& error)
				   {
				     if (!error)
				       self->report_bytecount();
				   });
      }
    else
      bytecount_timer.cancel();
  }

  void report_bytecount()
  {
    if (client && get_bytecount())
      {
	const ClientAPI::TransportStats ts = client->transport_stats();
	send(">BYTECOUNT:" + openvpn::to_string(ts.bytesIn) + ',' + openvpn::to_string(ts.bytesOut) + "\r\n");
      }
    schedule_bytecount_timer();
  }

  void start_connection_thread()
  {
    try {
      // reset client instance
      client.reset(new Client(this));

      // evaluate config
      const ClientAPI::EvalConfig eval = client->eval_config(*config);
      if (eval.error)
	OPENVPN_THROW_EXCEPTION("eval config error: " << eval.message);

      // add credentials, if available
      if (creds)
	{
	  const ClientAPI::Status creds_status = client->provide_creds(*creds);
	  if (creds_status.error)
	    OPENVPN_THROW_EXCEPTION("creds error: " << creds_status.message);
	}

      // bytecount
      schedule_bytecount_timer();

      // start connection thread
      thread.reset(new std::thread([this]() {
	    connection_thread();
	  }));
    }
    catch (const std::exception& e)
      {
	set_final_error(e.what());
	stop();
      }
  }

  void connection_thread()
  {
    openvpn_io::detail::signal_blocker signal_blocker; // signals should be handled by parent thread
    std::string error;
    try {
      const ClientAPI::Status cs = client->connect();
      if (cs.error)
	{
	  error = "connect error: ";
	  if (!cs.status.empty())
	    {
	      error += cs.status;
	      error += ": ";
	    }
	  error += cs.message;
	}
    }
    catch (const std::exception& e)
      {
	error = "connect thread exception: ";
	error += e.what();
      }

    // generate an internal event for client exceptions
    if (!error.empty())
      {
	ClientAPI::Event ev;
	ev.error = true;
	ev.fatal = true;
	ev.name = "CLIENT_EXCEPTION";
	ev.info = error;
	event(ev);
      }
  }

  void join_thread()
  {
    try {
      if (thread)
	thread->join(); // may throw if thread has already exited
    }
    catch (const std::exception& e)
      {
      }
  }

  virtual bool omi_stop() override
  {
    bool ret = false;

    // in case connect thread is blocking in external_pki_sign_request
    async_stop.stop();

    // cancel wait on exit_event
    if (exit_event.is_open())
      exit_event.cancel();

    // stop timers
    reconnect_timer.cancel();
    bytecount_timer.cancel();

    // stop the client
    if (client)
      client->stop();

    // wait for client thread to exit
    join_thread();

    // if there's a final error, dump to management interface
    const std::string fe = get_final_error();
    if (!fe.empty())
      {
	send(string::add_trailing_crlf_copy(fe));
	if (is_errors_to_stderr())
	  std::cerr << fe << std::endl;
	OPENVPN_LOG_STRING(fe + '\n');
	ret = true;
      }

    // cancel signals
    if (signals)
      signals->cancel();

    return ret;
  }

  void retry()
  {
    // wait for client thread to exit
    join_thread();

    // restart connection
    omi_start_connection();
  }

  void deferred_reconnect(const unsigned int seconds, const std::string& reason)
  {
    reconnect_timer.expires_after(Time::Duration::seconds(seconds));
    reconnect_timer.async_wait([self=Ptr(this), reason](const openvpn_io::error_code& error)
			       {
				 if (!error)
				   {
				     self->state_line(gen_state_msg(false, "RECONNECTING", reason));
				     self->retry();
				   }
			       });
  }

  virtual void omi_sigterm() override
  {
    if (client)
      set_final_error(gen_state_msg(true, "EXITING", "exit-with-notification"));
    stop();
  }

  virtual bool omi_is_sighup_implemented() override
  {
    return true;
  }

  virtual void omi_sighup() override
  {
    if (client)
      client->reconnect(1);
  }

  void log_msg(const ClientAPI::LogInfo& msg)
  {
    log_full(msg.text);
  }

  static std::string event_format(const ClientAPI::Event& ev, const ClientAPI::ConnectionInfo* ci)
  {
    const time_t now = ::time(NULL);
    std::string evstr = openvpn::to_string(now) + ',' + ev.name;
    if (ev.name == "CONNECTED" && ci)
      evstr += ",SUCCESS," + ci->vpnIp4 + ',' + ci->serverIp + ',' + ci->serverPort + ",,," + ci->vpnIp6;
    else
      evstr += ',' + ev.info + ",,";
    evstr += "\r\n";
    return evstr;
  }

  static std::string gen_state_msg(const bool prefix, std::string name, std::string info)
  {
    ClientAPI::Event ev;
    ev.name = std::move(name);
    ev.info = std::move(info);
    std::string ret;
    if (prefix)
      ret = ">STATE:";
    ret += event_format(ev, nullptr);
    return ret;
  }

  void event_msg(const ClientAPI::Event& ev, const ClientAPI::ConnectionInfo* ci)
  {
    // log events (even if in stopping state)
    {
      ClientAPI::LogInfo li;
      li.text = ev.name;
      if (!ev.info.empty())
	{
	  li.text += " : ";
	  li.text += ev.info;
	}
      if (ev.fatal)
	li.text += " [FATAL-ERR]";
      else if (ev.error)
	li.text += " [ERR]";
      li.text += '\n';
      log_msg(li);
    }

    // if we are in a stopping state, don't process the event
    if (is_stopping())
      return;

    // process events
    if ((ev.name == "AUTH_FAILED" || ev.name == "DYNAMIC_CHALLENGE") && management_query_passwords)
      {
	if (ev.name == "DYNAMIC_CHALLENGE")
	  {
	    dc_cookie = ev.info;
	  }
	else
	  {
	    dc_cookie = "";
	  }

	// handle auth failures
	std::string msg = ">PASSWORD:Verification Failed: 'Auth'";
	if (!ev.info.empty())
	  msg += " ['" + ev.info + "']";
	msg += "\r\n";
	send(msg);

	// reset query state
	creds.reset();
	did_query_remote = false;

	// exit/reconnect
	if (autologin)
	  {
	    set_final_error(">FATAL: auth-failure: " + ev.info + "\r\n");
	    stop();
	  }
	else
	  deferred_reconnect(1, "auth-failure");
      }

    else if (ev.name == "CLIENT_HALT")
      {
	std::string reason = ev.info;
	if (reason.empty())
	  reason = "client was disconnected from server";
	send(">NOTIFY:info,server-pushed-halt," + reason + "\r\n");
	set_final_error(gen_state_msg(true, "EXITING", "exit-with-notification"));
	stop();
      }

    else if (ev.name == "CLIENT_RESTART")
      {
	// fixme -- handle PSID
	std::string reason = ev.info;
	if (reason.empty())
	  reason = "server requested a client reconnect";
	reconnect_reason = "server-pushed-connection-reset";
	send(">NOTIFY:info,"+ reconnect_reason + ',' + reason + "\r\n");
	omi_sighup();
      }

    else if (ev.name == "RECONNECTING")
      {
	ClientAPI::Event nev(ev);
	if (nev.info.empty())
	  nev.info = reconnect_reason;
	reconnect_reason = "";
	state_line(event_format(nev, nullptr));
      }

    else if (ev.name == "PROXY_NEED_CREDS" && management_query_passwords)
      {
	// need proxy credentials, retry
	proxy_need_creds = true;
	state_line(event_format(ev, nullptr));
	retry();
      }

    else if (ev.name == "DISCONNECTED")
      {
	// for now, we ignore DISCONNECTED messages
      }

    else if (ev.fatal)
      {
	// this event is a fatal error
	std::string reason = ev.name;
	if (!ev.info.empty())
	  {
	    reason += ": ";
	    reason += ev.info;
	  }
	set_final_error(">FATAL:" + reason + "\r\n");
	stop();
      }

    else if (ev.name == "ECHO")
      {
	echo_line(openvpn::to_string(::time(NULL)) + ',' + ev.info + "\r\n");
      }

    else if (ev.name == "CONNECTED")
      {
	// generate >UPDOWN: event
	if (management_up_down)
	  emit_up_down("UP");

	// reset pre-connection state
	creds.reset();
	reconnect_reason = "";

	// generate a TCP_CONNECT event if TCP connection
	if (ci && string::starts_with(ci->serverProto, "TCP"))
	  state_line(gen_state_msg(false, "TCP_CONNECT", ""));

	// push the event string to state notification/history
	state_line(event_format(ev, ci));
      }

    else
      {
	// push the event string to state notification/history
	state_line(event_format(ev, ci));
      }
  }

  void emit_up_down(const std::string& state)
  {
    std::string out = ">UPDOWN:" + state + "\r\n";
    out += ">UPDOWN:ENV,END\r\n";
    send(out);
  }

  void set_final_error(const std::string& err)
  {
    if (!err.empty())
      final_error = string::trim_crlf_copy(err);
  }

  std::string get_final_error()
  {
    return final_error;
  }

  void signal(const openvpn_io::error_code& error, int signum)
  {
    if (!error && !is_stopping())
      {
	OPENVPN_LOG("ASIO SIGNAL " << signum);
	switch (signum)
	  {
	  case SIGINT:
	  case SIGTERM:
	    omi_sigterm();
	    break;
#if !defined(OPENVPN_PLATFORM_WIN)
	  case SIGHUP:
	    omi_sighup();
	    signal_rearm();
	    break;
#endif
	  }
      }
  }

  void signal_rearm()
  {
    signals->register_signals_all([self=Ptr(this)](const openvpn_io::error_code& error, int signal_number)
				  {
				    self->signal(error, signal_number);
				  });
  }

  // options
  OptionList opt;

  // general
  std::unique_ptr<Client> client;
  std::unique_ptr<ClientAPI::Config> config;
  std::unique_ptr<ClientAPI::ProvideCreds> creds;
  std::unique_ptr<std::thread> thread;
  std::string final_error;
  Stop async_stop;

  // timeout
  int connection_timeout = 0;

  // auth
  bool management_query_passwords = false;
  bool auth_nocache = false;
  std::string auth_type;
  bool auth_password_only = false;
  std::string auth_username;
  std::string auth_password;
  std::string dc_cookie;

  // remote override
  bool management_query_remote = false;
  bool did_query_remote = false;
  bool remote_pending = false;
  std::string remote_override;

  // protocol override (udp/tcp)
  std::string proto_override;

  // proxy
  std::string http_proxy_host;
  std::string http_proxy_port;
  bool proxy_need_creds = false;

  // reconnections
  std::string reconnect_reason;

  // reconnect
  AsioTimerSafe reconnect_timer;

  // bytecount
  AsioTimerSafe bytecount_timer;

  // external PKI
  bool management_external_key = false;
  std::mutex epki_mutex;
  std::condition_variable epki_cv;
  ClientAPI::ExternalPKISignRequest* epki_signreq = nullptr; // protected by epki_mutex

  // up/down
  bool management_up_down = false;

  // autologin
  bool autologin = false;

  // signals
  ASIOSignals::Ptr signals;

  typedef openvpn_io::windows::object_handle AsioEvent;
  AsioEvent exit_event;
  std::string exit_event_name;

  Log::Context log_context; // should be initialized last
};

void Client::event(const ClientAPI::Event& ev)
{
  if (ev.name == "CONNECTED")
    {
      const ClientAPI::ConnectionInfo ci = connection_info();
      parent->event(ev, ci);
    }
  else
    parent->event(ev);
}

void Client::log(const ClientAPI::LogInfo& msg)
{
  parent->log(msg);
}

void Client::external_pki_cert_request(ClientAPI::ExternalPKICertRequest& certreq)
{
  parent->external_pki_cert_request(certreq);
}

void Client::external_pki_sign_request(ClientAPI::ExternalPKISignRequest& signreq)
{
  parent->external_pki_sign_request(signreq);
}

int run(OptionList opt)
{
  openvpn_io::io_context io_context(1);
  bool io_context_run_called = false;
  int ret = 0;
  OMI::Ptr omi;

  try {
#if _WIN32_WINNT >= 0x0600 // Vista+
    TunWin::NRPT::delete_rule(); // delete stale NRPT rules
#endif
    omi.reset(new OMI(io_context, std::move(opt)));
    omi->start();
    io_context_run_called = true;
    io_context.run();
    omi->stop();
  }
  catch (const std::exception& e)
    {
      if (omi)
	omi->stop();
      if (io_context_run_called)
	io_context.poll(); // execute completion handlers,
      std::cerr << "openvpn: run loop exception: " << e.what() << std::endl;
      ret = 1;
    }
  return ret;
}

int main(int argc, char *argv[])
{
  int ret = 0;

  try {
    if (argc >= 2)
      {
	ret = run(OptionList::parse_from_argv_static(string::from_argv(argc, argv, true)));
      }
    else
      {
	std::cout << log_version() << std::endl;
	std::cout << "Usage: openvpn [args...]" << std::endl;
	ret = 2;
      }
  }
  catch (const std::exception& e)
    {
      std::cerr << "openvpn: " << e.what() << std::endl;
      ret = 1;
    }
  return ret;
}
