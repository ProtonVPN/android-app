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

#pragma once

// General purpose HTTP/HTTPS/Web-services client.
// Supports:
//   * asynchronous I/O through Asio
//   * http/https
//   * chunking
//   * keepalive
//   * connect and overall timeouts
//   * GET, POST, etc.
//   * any OpenVPN SSL module (OpenSSL, MbedTLS)
//   * server CA bundle
//   * client certificate
//   * HTTP basic auth
//   * limits on content-size, header-size, and number of headers
//   * cURL not needed
//
//  See test/ws/wstest.cpp for usage examples including Dropwizard REST/JSON API client.
//  See test/ws/asprof.cpp for sample AS REST API client.

#include <string>
#include <vector>
#include <sstream>
#include <ostream>
#include <cstdint>
#include <utility>
#include <memory>
#include <algorithm> // for std::min, std::max

#ifdef USE_ASYNC_RESOLVE
#include <openvpn/client/async_resolve.hpp>
#endif

#include <openvpn/common/platform.hpp>
#include <openvpn/common/base64.hpp>
#include <openvpn/common/olong.hpp>
#include <openvpn/common/arraysize.hpp>
#include <openvpn/common/hostport.hpp>
#include <openvpn/common/base64.hpp>
#include <openvpn/random/randapi.hpp>
#include <openvpn/addr/ip.hpp>
#include <openvpn/asio/asiopolysock.hpp>
#include <openvpn/asio/asioresolverres.hpp>
#include <openvpn/common/to_string.hpp>
#include <openvpn/error/error.hpp>
#include <openvpn/buffer/bufstream.hpp>
#include <openvpn/http/reply.hpp>
#include <openvpn/time/asiotimersafe.hpp>
#include <openvpn/time/coarsetime.hpp>
#include <openvpn/transport/tcplink.hpp>
#include <openvpn/transport/client/transbase.hpp>
#include <openvpn/ws/httpcommon.hpp>
#include <openvpn/ws/httpcreds.hpp>
#include <openvpn/ws/websocket.hpp>

#ifdef VPN_BINDING_PROFILES
#ifdef USE_ASYNC_RESOLVE
#error VPN_BINDING_PROFILES and USE_ASYNC_RESOLVE cannot be used together
#endif
#include <openvpn/ws/httpvpn.hpp>
#include <openvpn/dns/dnscli.hpp>
#endif

#ifdef SIMULATE_HTTPCLI_FAILURES
// debugging -- simulate network failures
#include <openvpn/common/periodic_fail.hpp>
#endif

#if defined(OPENVPN_POLYSOCK_SUPPORTS_ALT_ROUTING)
#include <openvpn/asio/alt_routing.hpp>
#endif

#if defined(OPENVPN_PLATFORM_WIN)
#include <openvpn/win/scoped_handle.hpp>
#include <openvpn/win/winerr.hpp>
#endif

namespace openvpn {
namespace WS {
namespace Client {

OPENVPN_EXCEPTION(http_client_exception);

struct Status
{
    // Error codes
    enum
    {
        E_SUCCESS = 0,
        E_RESOLVE,
        E_CONNECT,
        E_TRANSPORT,
        E_PROXY,
        E_TCP,
        E_HTTP,
        E_EXCEPTION,
        E_BAD_REQUEST,
        E_HEADER_SIZE,
        E_CONTENT_SIZE,
        E_CONTENT_TYPE,
        E_EOF_SSL,
        E_EOF_TCP,
        E_CONNECT_TIMEOUT,
        E_GENERAL_TIMEOUT,
        E_KEEPALIVE_TIMEOUT,
        E_SHUTDOWN,
        E_ABORTED,
        E_HOST_UPDATE,
        E_BOGON, // simulated fault injection for testing

        N_ERRORS
    };

    static std::string error_str(const int status)
    {
        static const char *error_names[] = {
            "E_SUCCESS",
            "E_RESOLVE",
            "E_CONNECT",
            "E_TRANSPORT",
            "E_PROXY",
            "E_TCP",
            "E_HTTP",
            "E_EXCEPTION",
            "E_BAD_REQUEST",
            "E_HEADER_SIZE",
            "E_CONTENT_SIZE",
            "E_CONTENT_TYPE",
            "E_EOF_SSL",
            "E_EOF_TCP",
            "E_CONNECT_TIMEOUT",
            "E_GENERAL_TIMEOUT",
            "E_KEEPALIVE_TIMEOUT",
            "E_SHUTDOWN",
            "E_ABORTED",
            "E_HOST_UPDATE",
            "E_BOGON",
        };

        static_assert(N_ERRORS == array_size(error_names), "HTTP error names array inconsistency");
        if (status >= 0 && status < N_ERRORS)
            return error_names[status];
        else if (status == -1)
            return "E_UNDEF";
        else
            return "E_?/" + openvpn::to_string(status);
    }

    static bool is_error(const int status)
    {
        switch (status)
        {
        case E_SUCCESS:
        case E_SHUTDOWN:
            return false;
        default:
            return true;
        }
    }
};

struct Host;

#ifdef OPENVPN_POLYSOCK_SUPPORTS_ALT_ROUTING
struct AltRoutingShimFactory : public RC<thread_unsafe_refcount>
{
    typedef RCPtr<AltRoutingShimFactory> Ptr;

    virtual AltRouting::Shim::Ptr shim(const Host &host) = 0;
    virtual void report_error(const Host &host, const bool alt_routing)
    {
    }
    virtual bool is_reset(const Host &host, const bool alt_routing)
    {
        return false;
    }
    virtual int connect_timeout()
    {
        return -1;
    }
    virtual IP::Addr remote_ip()
    {
        return IP::Addr();
    }
    virtual int remote_port()
    {
        return -1;
    }
    virtual int error_expire()
    {
        return 0;
    }
};
#endif

struct Config : public RCCopyable<thread_unsafe_refcount>
{
    typedef RCPtr<Config> Ptr;

    SSLFactoryAPI::Ptr ssl_factory;
    TransportClientFactory::Ptr transcli;
    std::string user_agent;
    unsigned int connect_timeout = 0;
    unsigned int general_timeout = 0;
    unsigned int keepalive_timeout = 0;
    unsigned int max_headers = 0;
    unsigned int max_header_bytes = 0;
    bool enable_cache = false; // if true, supports TLS session resumption tickets
    olong max_content_bytes = 0;
    unsigned int msg_overhead_bytes = 0;
    int debug_level = 0;
    Frame::Ptr frame;
    SessionStats::Ptr stats;
    RandomAPI::Ptr prng;

#ifdef OPENVPN_POLYSOCK_SUPPORTS_ALT_ROUTING
    AltRoutingShimFactory::Ptr shim_factory;
#endif
};

struct Host
{
    std::string host;
    std::string hint; // overrides host for transport, may be IP address
    std::string cn;   // host for CN verification, defaults to host if empty
    std::string key;  // host for TLS session ticket cache key, defaults to host if empty
    std::string head; // host to send in HTTP header, defaults to host if empty
    std::string port;

    std::string local_addr;     // bind to local address
    std::string local_addr_alt; // alt local addr for different IP version (optional)
    std::string local_port;     // bind to local port (optional)

#ifdef VPN_BINDING_PROFILES
    // use a VPN binding profile to obtain hint
    // and local_addr and possibly DNS resolvers as well
    ViaVPN::Ptr via_vpn;
#endif

    const std::string &host_transport() const
    {
        return hint.empty() ? host : hint;
    }

    const std::string *host_cn_ptr() const
    {
        return cn.empty() ? &host : &cn;
    }

    const std::string &host_cn() const
    {
        return *host_cn_ptr();
    }

    const std::string &host_head() const
    {
        return head.empty() ? host : head;
    }

    std::string host_port_str() const
    {
        std::string ret;
        const std::string &ht = host_transport();
        if (ht == host)
        {
            ret += '[';
            ret += host;
            ret += "]:";
            ret += port;
        }
        else
        {
            ret += host;
            ret += '[';
            ret += ht;
            ret += "]:";
            ret += port;
        }
        return ret;
    }

    std::string cache_key() const
    {
        return key.empty() ? (host + '/' + port) : key;
    }
};

struct Request
{
    bool creds_defined() const
    {
        return !username.empty() || !password.empty();
    }

    void set_creds(const Creds &creds)
    {
        username = creds.username;
        password = creds.password;
    }

    std::string method;
    std::string uri;
    std::string username;
    std::string password;
};

struct ContentInfo
{
    // content length if Transfer-Encoding: chunked
    static constexpr olong CHUNKED = -1;

    std::string type;
    std::string content_encoding;
    olong length = 0;
    bool keepalive = false;
    bool lean_headers = false;
    std::vector<std::string> extra_headers;
    WebSocket::Client::PerRequest::Ptr websocket;
};

struct TimeoutOverride
{
    // Timeout overrides in seconds.
    // Set to -1 to disable.
    int connect = -1;
    int general = -1;
    int keepalive = -1;
};

class HTTPCore;
typedef HTTPBase<HTTPCore, Config, Status, HTTP::ReplyType, ContentInfo, olong, RC<thread_unsafe_refcount>> Base;

class HTTPCore : public Base, public TransportClientParent
#ifdef USE_ASYNC_RESOLVE
    ,
                 public AsyncResolvableTCP
#endif
{
  public:
    friend Base;

    typedef RCPtr<HTTPCore> Ptr;
#ifndef USE_ASYNC_RESOLVE
    using results_type = openvpn_io::ip::tcp::resolver::results_type;
#endif

    struct AsioProtocol
    {
        typedef AsioPolySock::Base socket;
    };

    HTTPCore(openvpn_io::io_context &io_context_arg,
             Config::Ptr config_arg)
        : Base(std::move(config_arg)),
#ifdef USE_ASYNC_RESOLVE
          AsyncResolvableTCP(io_context_arg),
#endif
          io_context(io_context_arg),
#ifndef USE_ASYNC_RESOLVE
          resolver(io_context_arg),
#endif
          connect_timer(io_context_arg),
          general_timer(io_context_arg),
          general_timeout_coarse(Time::Duration::binary_ms(512), Time::Duration::binary_ms(1024))
    {
    }

    virtual ~HTTPCore()
    {
        stop(false);
    }

    // Should be called before start_request().
    void override_timeouts(TimeoutOverride to_arg)
    {
        to = std::move(to_arg);
    }

    bool is_alive() const
    {
        return alive;
    }

    bool is_link_active()
    {
        return link && !halt;
    }

    // return true if the alt-routing state for this session
    // has changed, requiring a reset
    bool is_alt_routing_reset() const
    {
#ifdef OPENVPN_POLYSOCK_SUPPORTS_ALT_ROUTING
        if (config->shim_factory
            && socket
            && config->shim_factory->is_reset(host, socket->alt_routing_enabled()))
            return true;
#endif
        return false;
    }

    void check_ready() const
    {
        if (!is_ready())
            throw http_client_exception("not ready");
    }

    void start_request()
    {
        check_ready();
        ready = false;
        cancel_keepalive_timer();
        openvpn_io::post(io_context, [self = Ptr(this)]()
                         { self->handle_request(); });
    }

    void start_request_after(const Time::Duration dur)
    {
        check_ready();
        ready = false;
        cancel_keepalive_timer();
        if (!req_timer)
            req_timer.reset(new AsioTimerSafe(io_context));
        req_timer->expires_after(dur);
        req_timer->async_wait([self = Ptr(this)](const openvpn_io::error_code &error)
                              {
				  if (!error)
				    self->handle_request(); });
    }

    void stop(const bool shutdown)
    {
        if (!halt)
        {
            halt = true;
            ready = false;
            alive = false;
            if (transcli)
                transcli->stop();
            if (link)
                link->stop();
            if (socket)
            {
                if (shutdown)
                    socket->shutdown(AsioPolySock::SHUTDOWN_SEND | AsioPolySock::SHUTDOWN_RECV);
                socket->close();
            }
#ifdef USE_ASYNC_RESOLVE
            async_resolve_cancel();
#else
            resolver.cancel();
#endif
#ifdef VPN_BINDING_PROFILES
            if (alt_resolve)
                alt_resolve->stop();
#endif
            if (req_timer)
                req_timer->cancel();
            cancel_keepalive_timer();
            general_timer.cancel();
            connect_timer.cancel();
        }
    }

    void abort(const std::string &message, const int status = Status::E_ABORTED)
    {
        if (!halt)
            error_handler(status, message);
    }

    const HTTP::Reply &reply() const
    {
        return request_reply();
    }

    std::string remote_endpoint_str() const
    {
        try
        {
            if (socket)
                return socket->remote_endpoint_str();
        }
        catch (const std::exception &e)
        {
        }
        return "[unknown endpoint]";
    }

    bool remote_ip_port(IP::Addr &addr, unsigned int &port) const
    {
        if (socket)
            return socket->remote_ip_port(addr, port);
        else
            return false;
    }

    // Return the current Host object, but
    // set the hint/port fields to the live
    // IP address/port of the connection.
    Host host_hint()
    {
        Host h = host;
        if (socket)
        {
            IP::Addr addr;
            unsigned int port;
            if (socket->remote_ip_port(addr, port))
            {
                h.hint = addr.to_string();
                h.port = openvpn::to_string(port);
            }
        }
        return h;
    }

    bool host_match(const std::string &host_arg) const
    {
        if (host.host.empty())
            return false;
        else
            return host.host == host_arg;
    }

    AsioPolySock::Base *get_socket()
    {
        return socket.get();
    }

    void streaming_start()
    {
        cancel_general_timeout(); // cancel general timeout once websocket streaming begins
        content_out_hold = false;
        if (is_deferred())
            http_content_out_needed();
    }

    void streaming_restart()
    {
        if (content_out_hold)
            throw http_client_exception("streaming_restart() called when content-out is still in hold state");
        if (is_deferred())
            http_content_out_needed();
    }

    bool is_streaming_restartable() const
    {
        return !content_out_hold;
    }

    bool is_streaming_hold() const
    {
        return content_out_hold;
    }

    // virtual methods

    virtual Host http_host() = 0;

    virtual Request http_request() = 0;

    virtual ContentInfo http_content_info()
    {
        return ContentInfo();
    }

    virtual BufferPtr http_content_out()
    {
        return BufferPtr();
    }

    virtual void http_content_out_needed()
    {
    }

    virtual void http_headers_received()
    {
    }

    virtual void http_headers_sent(const Buffer &buf)
    {
    }

    virtual void http_mutate_resolver_results(results_type &results)
    {
    }

    virtual void http_content_in(BufferAllocated &buf) = 0;

    virtual void http_done(const int status, const std::string &description) = 0;

    virtual void http_keepalive_close(const int status, const std::string &description)
    {
    }

    virtual void http_post_connect(AsioPolySock::Base &sock)
    {
    }

  private:
    typedef TCPTransport::Link<AsioProtocol, HTTPCore *, false> LinkImpl;
    friend LinkImpl::Base; // calls tcp_* handlers

    void verify_frame()
    {
        if (!frame)
            throw http_client_exception("frame undefined");
    }

#ifdef SIMULATE_HTTPCLI_FAILURES // debugging -- simulate network failures
    bool inject_fault(const char *caller)
    {
        if (periodic_fail.trigger("httpcli", SIMULATE_HTTPCLI_FAILURES))
        {
            OPENVPN_LOG("HTTPCLI BOGON on " << host.host_port_str() << " (" << caller << ')');
            error_handler(Status::E_BOGON, caller);
            return true;
        }
        else
            return false;
    }
#endif

    void activity(const bool init)
    {
        const Time now = Time::now();
        if (general_timeout_duration.defined())
        {
            const Time next = now + general_timeout_duration;
            if (init || !general_timeout_coarse.similar(next))
            {
                general_timeout_coarse.reset(next);
                general_timer.expires_at(next);
                general_timer.async_wait([self = Ptr(this)](const openvpn_io::error_code &error)
                                         {
					     if (!error)
					       self->general_timeout_handler(error); });
            }
        }
        else if (init)
        {
            general_timeout_coarse.reset();
            general_timer.cancel();
        }
    }

    void handle_request() // called by Asio
    {
        if (halt)
            return;

        try
        {
            if (ready)
                throw http_client_exception("handle_request called in ready state");

            verify_frame();

            general_timeout_duration = Time::Duration::seconds(to.general >= 0
                                                                   ? to.general
                                                                   : config->general_timeout);
            general_timeout_coarse.reset();
            activity(true);

            // already in persistent session?
            if (alive)
            {
                generate_request();
                return;
            }

            // get new Host object
            host = http_host();

#ifdef VPN_BINDING_PROFILES
            // support VPN binding profile
            Json::Value via_vpn_conf;
            if (host.via_vpn)
                via_vpn_conf = host.via_vpn->client_update_host(host);
#endif

#ifdef ASIO_HAS_LOCAL_SOCKETS
            // unix domain socket?
            if (host.port == "unix")
            {
                openvpn_io::local::stream_protocol::endpoint ep(host.host_transport());
                AsioPolySock::Unix *s = new AsioPolySock::Unix(io_context, 0);
                socket.reset(s);
                s->socket.async_connect(ep,
                                        [self = Ptr(this)](const openvpn_io::error_code &error)
                                        {
                    self->handle_unix_connect(error);
                });
                set_connect_timeout(config->connect_timeout);
                return;
            }
#endif
#ifdef OPENVPN_PLATFORM_WIN
            // windows named pipe?
            if (host.port == "np")
            {
                const std::string &ht = host.host_transport();
                const HANDLE h = ::CreateFileA(
                    ht.c_str(),
                    GENERIC_READ | GENERIC_WRITE,
                    0,
                    NULL,
                    OPEN_EXISTING,
                    FILE_FLAG_OVERLAPPED,
                    NULL);
                if (!Win::Handle::defined(h))
                {
                    const Win::LastError err;
                    OPENVPN_THROW(http_client_exception, "failed to open existing named pipe: " << ht << " : " << err.message());
                }
                socket.reset(new AsioPolySock::NamedPipe(openvpn_io::windows::stream_handle(io_context, h), 0));
                do_connect(true);
                set_connect_timeout(config->connect_timeout);
                return;
            }
#endif
#if defined(OPENVPN_POLYSOCK_SUPPORTS_ALT_ROUTING)
            // alt routing?
            if (config->shim_factory)
            {
                AltRouting::Shim::Ptr shim = config->shim_factory->shim(host);
                if (shim)
                {
                    alt_routing_connect(std::move(shim));
                    return;
                }
            }
#endif

            // standard TCP (with or without SSL)
            if (host.port.empty())
                host.port = config->ssl_factory ? "443" : "80";

            if (config->ssl_factory)
            {
                if (config->enable_cache)
                {
                    std::string cache_key = host.cache_key();
                    ssl_sess = config->ssl_factory->ssl(host.host_cn_ptr(), &cache_key);
                }
                else
                    ssl_sess = config->ssl_factory->ssl(host.host_cn_ptr(), nullptr);
            }

            if (config->transcli)
            {
                transcli = config->transcli->new_transport_client_obj(io_context, this);
                transcli->transport_start();
            }
            else
            {
#ifdef USE_ASYNC_RESOLVE
                async_resolve_name(host.host_transport(), host.port);
#else
#ifdef VPN_BINDING_PROFILES
                if (via_vpn_conf)
                {
                    DNSClient::ResolverList::Ptr resolver_list(new DNSClient::ResolverList(via_vpn_conf));
                    alt_resolve = DNSClient::async_resolve(io_context,
                                                           std::move(resolver_list),
                                                           config->prng.get(),
                                                           host.host_transport(),
                                                           host.port,
                                                           [self = Ptr(this)](const openvpn_io::error_code &error,
                                                                              results_type results) mutable
                                                           { self->resolve_callback(error, std::move(results)); });
                }
                else
#endif
                    resolver.async_resolve(host.host_transport(),
                                           host.port,
                                           [self = Ptr(this)](const openvpn_io::error_code &error,
                                                              results_type results) mutable
                                           { self->resolve_callback(error, std::move(results)); });
#endif
            }
            set_connect_timeout(config->connect_timeout);
        }
        catch (const std::exception &e)
        {
            handle_exception("handle_request", e);
        }
    }

    void resolve_callback(const openvpn_io::error_code &error, // called by Asio
                          results_type results)
    {
        if (halt)
            return;

#ifdef SIMULATE_HTTPCLI_FAILURES // debugging -- simulate network failures
        if (inject_fault("resolve_callback"))
            return;
#endif

        if (error)
        {
            asio_error_handler(Status::E_RESOLVE, "resolve_callback", error);
            return;
        }

        try
        {
            http_mutate_resolver_results(results);
            if (results.empty())
                OPENVPN_THROW_EXCEPTION("no results");

            AsioPolySock::TCP *s = new AsioPolySock::TCP(io_context, 0);
            socket.reset(s);
            bind_local_addr(s);

            if (config->debug_level >= 2)
                OPENVPN_LOG("TCP HTTP CONNECT to " << s->remote_endpoint_str() << " res=" << asio_resolver_results_to_string(results));

            openvpn_io::async_connect(s->socket,
                                      std::move(results),
                                      [self = Ptr(this)](const openvpn_io::error_code &error, const openvpn_io::ip::tcp::endpoint &endpoint)
                                      { self->handle_tcp_connect(error, endpoint); });
        }
        catch (const std::exception &e)
        {
            handle_exception("resolve_callback", e);
        }
    }

    void handle_tcp_connect(const openvpn_io::error_code &error, // called by Asio
                            const openvpn_io::ip::tcp::endpoint &endpoint)
    {
        if (halt)
            return;

#ifdef SIMULATE_HTTPCLI_FAILURES // debugging -- simulate network failures
        if (inject_fault("handle_tcp_connect"))
            return;
#endif

        if (error)
        {
            asio_error_handler(Status::E_CONNECT, "handle_tcp_connect", error);
            return;
        }

        try
        {
            do_connect(true);
        }
        catch (const std::exception &e)
        {
            handle_exception("handle_tcp_connect", e);
        }
    }

#ifdef ASIO_HAS_LOCAL_SOCKETS
    void handle_unix_connect(const openvpn_io::error_code &error) // called by Asio
    {
        if (halt)
            return;

#ifdef SIMULATE_HTTPCLI_FAILURES // debugging -- simulate network failures
        if (inject_fault("handle_unix_connect"))
            return;
#endif

        if (error)
        {
            asio_error_handler(Status::E_CONNECT, "handle_unix_connect", error);
            return;
        }

        try
        {
            do_connect(true);
        }
        catch (const std::exception &e)
        {
            handle_exception("handle_unix_connect", e);
        }
    }
#endif

#if defined(OPENVPN_POLYSOCK_SUPPORTS_ALT_ROUTING)
    void alt_routing_connect(AltRouting::Shim::Ptr shim)
    {
        AltRoutingShimFactory &sf = *config->shim_factory;

        // build socket and assign shim
        AsioPolySock::TCP *s = new AsioPolySock::TCP(io_context, 0);
        socket.reset(s);
        bind_local_addr(s);
        s->socket.shim = std::move(shim);

        // build results
        int port = sf.remote_port();
        if (port < 0)
            port = HostPort::parse_port(host.port, "AltRouting");
        IP::Addr addr = sf.remote_ip();
        if (!addr.defined())
            addr = IP::Addr(host.host_transport(), "AltRouting");
        results_type results = results_type::create(openvpn_io::ip::tcp::endpoint(addr.to_asio(),
                                                                                  port),
                                                    host.host,
                                                    "");

        if (config->debug_level >= 2)
            OPENVPN_LOG("ALT_ROUTING HTTP CONNECT to " << s->remote_endpoint_str() << " res=" << asio_resolver_results_to_string(results));

        // do async connect
        openvpn_io::async_connect(s->socket,
                                  std::move(results),
                                  [self = Ptr(this)](const openvpn_io::error_code &error, const openvpn_io::ip::tcp::endpoint &endpoint)
                                  { self->handle_tcp_connect(error, endpoint); });

        // set connect timeout
        {
            int ct = sf.connect_timeout();
            if (ct < 0)
                ct = config->connect_timeout;
            set_connect_timeout(ct);
        }
    }
#endif

    void do_connect(const bool use_link)
    {
        connect_timer.cancel();
        set_default_stats();

        if (use_link)
        {
            socket->set_cloexec();
            socket->tcp_nodelay();
            http_post_connect(*socket);
            link.reset(new LinkImpl(this,
                                    *socket,
                                    0, // send_queue_max_size (unlimited)
                                    8, // free_list_max_size
                                    (*frame)[Frame::READ_HTTP],
                                    stats));
            link->set_raw_mode(true);
            link->start();
        }

        if (ssl_sess)
            ssl_sess->start_handshake();

        // xmit the request
        generate_request();
    }

    void set_connect_timeout(unsigned int connect_timeout)
    {
        if (config->connect_timeout)
        {
            connect_timer.expires_after(Time::Duration::seconds(to.connect >= 0
                                                                    ? to.connect
                                                                    : connect_timeout));
            connect_timer.async_wait([self = Ptr(this)](const openvpn_io::error_code &error)
                                     {
					 if (!error)
					   self->connect_timeout_handler(error); });
        }
    }

    void bind_local_addr(AsioPolySock::TCP *s)
    {
        // optionally bind to local addr/port
        if (!host.local_addr.empty())
        {
#if defined(OPENVPN_POLYSOCK_SUPPORTS_BIND) || defined(OPENVPN_POLYSOCK_SUPPORTS_ALT_ROUTING)
            const IP::Addr local_addr(host.local_addr, "local_addr");
            unsigned short local_port = 0;
            if (!host.local_port.empty())
                local_port = HostPort::parse_port(host.local_port, "local_port");
            s->socket.bind_local(local_addr, local_port);

            if (!host.local_addr_alt.empty())
            {
                const IP::Addr local_addr_alt(host.local_addr_alt, "local_addr_alt");
                if (local_addr.version() == local_addr_alt.version())
                    throw Exception("local bind addresses having the same IP version don't make sense: " + local_addr.to_string() + ' ' + local_addr_alt.to_string());
                s->socket.bind_local(local_addr_alt, local_port);
            }
#else
            throw Exception("httpcli must be built with OPENVPN_POLYSOCK_SUPPORTS_BIND or OPENVPN_POLYSOCK_SUPPORTS_ALT_ROUTING to support local bind");
#endif
        }
    }

    void schedule_keepalive_timer()
    {
        if (config->keepalive_timeout || to.keepalive >= 0)
        {
            const Time::Duration dur = Time::Duration::seconds(to.keepalive >= 0
                                                                   ? to.keepalive
                                                                   : config->keepalive_timeout);
            if (!keepalive_timer)
                keepalive_timer.reset(new AsioTimerSafe(io_context));
            keepalive_timer->expires_after(dur);
            keepalive_timer->async_wait([self = Ptr(this)](const openvpn_io::error_code &error)
                                        {
			if (!self->halt && !error && self->ready)
			  {
			    self->error_handler(Status::E_KEEPALIVE_TIMEOUT, "Keepalive timeout");
			  } });
        }
    }

    void cancel_keepalive_timer()
    {
        if (keepalive_timer)
            keepalive_timer->cancel();
    }

    void cancel_general_timeout()
    {
        general_timeout_duration.set_zero();
        general_timer.cancel();
    }

    void general_timeout_handler(const openvpn_io::error_code &e) // called by Asio
    {
        if (!halt && !e)
            error_handler(Status::E_GENERAL_TIMEOUT, "General timeout");
    }

    void connect_timeout_handler(const openvpn_io::error_code &e) // called by Asio
    {
        if (!halt && !e)
            error_handler(Status::E_CONNECT_TIMEOUT, "Connect timeout");
    }

    void set_default_stats()
    {
        if (!stats)
            stats.reset(new SessionStats());
    }

    void generate_request()
    {
        rr_reset();
        http_out_begin();

        const Request req = http_request();
        content_info = http_content_info();

        outbuf.reset(new BufferAllocated(512, BufferAllocated::GROW));
        BufferStreamOut os(*outbuf);

        if (content_info.websocket)
        {
            // no content-out until after server reply (content_out_hold kept at true)
            generate_request_websocket(os, req);
        }
        else
        {
            // non-websocket allows immediate content-out
            content_out_hold = false;
            generate_request_http(os, req);
        }

        http_headers_sent(*outbuf);
        http_out();
    }

    void generate_request_http(std::ostream &os, const Request &req)
    {
        os << req.method << ' ' << req.uri << " HTTP/1.1\r\n";
        if (!content_info.lean_headers)
        {
            os << "Host: " << host.host_head() << "\r\n";
            if (!config->user_agent.empty())
                os << "User-Agent: " << config->user_agent << "\r\n";
        }
        generate_basic_auth_headers(os, req);
        if (content_info.length)
            os << "Content-Type: " << content_info.type << "\r\n";
        if (content_info.length > 0)
            os << "Content-Length: " << content_info.length << "\r\n";
        else if (content_info.length == ContentInfo::CHUNKED)
            os << "Transfer-Encoding: chunked"
               << "\r\n";
        for (auto &h : content_info.extra_headers)
            os << h << "\r\n";
        if (!content_info.content_encoding.empty())
            os << "Content-Encoding: " << content_info.content_encoding << "\r\n";
        if (content_info.keepalive)
            os << "Connection: keep-alive\r\n";
        if (!content_info.lean_headers)
            os << "Accept: */*\r\n";
        os << "\r\n";
    }

    void generate_request_websocket(std::ostream &os, const Request &req)
    {
        os << req.method << ' ' << req.uri << " HTTP/1.1\r\n";
        os << "Host: " << host.host_head() << "\r\n";
        if (!config->user_agent.empty())
            os << "User-Agent: " << config->user_agent << "\r\n";
        generate_basic_auth_headers(os, req);
        if (content_info.length)
            os << "Content-Type: " << content_info.type << "\r\n";
        if (content_info.websocket)
            content_info.websocket->client_headers(os);
        for (auto &h : content_info.extra_headers)
            os << h << "\r\n";
        os << "\r\n";
    }

    void generate_basic_auth_headers(std::ostream &os, const Request &req)
    {
        if (!req.username.empty() || !req.password.empty())
            os << "Authorization: Basic "
               << base64->encode(req.username + ':' + req.password)
               << "\r\n";
    }

    // error handlers

    void asio_error_handler(int errcode, const char *func_name, const openvpn_io::error_code &error)
    {
        error_handler(errcode, std::string("HTTPCore Asio ") + func_name + ": " + error.message());
    }

    void handle_exception(const char *func_name, const std::exception &e)
    {
        error_handler(Status::E_EXCEPTION, std::string("HTTPCore Exception ") + func_name + ": " + e.what());
    }

    void error_handler(const int errcode, const std::string &err)
    {
        const bool in_transaction = !ready;
        const bool keepalive = alive;
        const bool error = Status::is_error(errcode);
#if defined(OPENVPN_POLYSOCK_SUPPORTS_ALT_ROUTING)
        if (config->shim_factory && error && in_transaction && socket)
            config->shim_factory->report_error(host, socket->alt_routing_enabled());
#endif
        stop(!error);
        if (in_transaction)
            http_done(errcode, err);
        else if (keepalive)
            http_keepalive_close(errcode, err); // keepalive connection close outside of transaction
    }

    // methods called by LinkImpl

    bool tcp_read_handler(BufferAllocated &b)
    {
        if (halt)
            return false;

        try
        {
#ifdef SIMULATE_HTTPCLI_FAILURES // debugging -- simulate network failures
            if (inject_fault("tcp_read_handler"))
                return false;
#endif
            activity(false);
            tcp_in(b); // call Base
            return true;
        }
        catch (const std::exception &e)
        {
            handle_exception("tcp_read_handler", e);
            return false;
        }
    }

    void tcp_write_queue_needs_send()
    {
        if (halt)
            return;

        try
        {
            http_out();
        }
        catch (const std::exception &e)
        {
            handle_exception("tcp_write_queue_needs_send", e);
        }
    }

    void tcp_eof_handler()
    {
        if (halt)
            return;

        try
        {
            error_handler(Status::E_EOF_TCP, "TCP EOF");
            return;
        }
        catch (const std::exception &e)
        {
            handle_exception("tcp_eof_handler", e);
        }
    }

    void tcp_error_handler(const char *error)
    {
        if (halt)
            return;
        error_handler(Status::E_TCP, std::string("HTTPCore TCP: ") + error);
    }

    // methods called by Base

    BufferPtr base_http_content_out()
    {
        return http_content_out();
    }

    void base_http_content_out_needed()
    {
        if (!content_out_hold)
            http_content_out_needed();
    }

    void base_http_out_eof()
    {
        if (websocket)
        {
            stop(true);
            http_done(Status::E_SUCCESS, "Succeeded");
        }
    }

    bool base_http_headers_received()
    {
        if (content_info.websocket)
            websocket = true; // enable websocket in httpcommon
        http_headers_received();
        return true; // continue to receive content
    }

    void base_http_content_in(BufferAllocated &buf)
    {
        http_content_in(buf);
    }

    bool base_link_send(BufferAllocated &buf)
    {
        try
        {
#ifdef SIMULATE_HTTPCLI_FAILURES // debugging -- simulate network failures
            if (inject_fault("base_link_send"))
                return false;
#endif
            activity(false);
            if (transcli)
                return transcli->transport_send(buf);
            else
                return link->send(buf);
        }
        catch (const std::exception &e)
        {
            handle_exception("base_link_send", e);
            return false;
        }
    }

    bool base_send_queue_empty()
    {
        if (transcli)
            return transcli->transport_send_queue_empty();
        else
            return link->send_queue_empty();
    }

    void base_http_done_handler(BufferAllocated &residual,
                                const bool parent_handoff)
    {
        if (halt)
            return;
        if ((content_info.keepalive || parent_handoff) && !websocket)
        {
            general_timer.cancel();
            schedule_keepalive_timer();
            alive = true;
            ready = true;
        }
        else
            stop(true);
        http_done(Status::E_SUCCESS, "Succeeded");
    }

    void base_error_handler(const int errcode, const std::string &err)
    {
        error_handler(errcode, err);
    }

    // TransportClientParent methods

    virtual bool transport_is_openvpn_protocol()
    {
        return false;
    }

    virtual void transport_recv(BufferAllocated &buf)
    {
        tcp_read_handler(buf);
    }

    virtual void transport_needs_send()
    {
        tcp_write_queue_needs_send();
    }

    std::string err_fmt(const Error::Type fatal_err, const std::string &err_text)
    {
        std::ostringstream os;
        if (fatal_err != Error::SUCCESS)
            os << Error::name(fatal_err) << " : ";
        os << err_text;
        return os.str();
    }

    virtual void transport_error(const Error::Type fatal_err, const std::string &err_text)
    {
        return error_handler(Status::E_TRANSPORT, err_fmt(fatal_err, err_text));
    }

    virtual void proxy_error(const Error::Type fatal_err, const std::string &err_text)
    {
        return error_handler(Status::E_PROXY, err_fmt(fatal_err, err_text));
    }

    virtual void transport_pre_resolve()
    {
    }

    virtual void transport_wait_proxy()
    {
    }

    virtual void transport_wait()
    {
    }

    virtual bool is_keepalive_enabled() const
    {
        return false;
    }

    virtual void disable_keepalive(unsigned int &keepalive_ping,
                                   unsigned int &keepalive_timeout)
    {
    }

    virtual void transport_connecting()
    {
        do_connect(false);
    }

    openvpn_io::io_context &io_context;

    TimeoutOverride to;

    AsioPolySock::Base::Ptr socket;

#ifndef USE_ASYNC_RESOLVE
    openvpn_io::ip::tcp::resolver resolver;
#endif
#ifdef VPN_BINDING_PROFILES
    DNSClient::Context::Ptr alt_resolve;
#endif
    Host host;

    LinkImpl::Ptr link;

    TransportClient::Ptr transcli;

    AsioTimerSafe connect_timer;
    AsioTimerSafe general_timer;
    std::unique_ptr<AsioTimerSafe> req_timer;
    std::unique_ptr<AsioTimerSafe> keepalive_timer;

    Time::Duration general_timeout_duration;
    CoarseTime general_timeout_coarse;

    bool content_out_hold = true;
    bool alive = false;

#ifdef SIMULATE_HTTPCLI_FAILURES // debugging -- simulate network failures
    PeriodicFail periodic_fail;
#endif
};

template <typename PARENT>
class HTTPDelegate : public HTTPCore
{
  public:
    OPENVPN_EXCEPTION(http_delegate_error);

    typedef RCPtr<HTTPDelegate> Ptr;

    HTTPDelegate(openvpn_io::io_context &io_context,
                 WS::Client::Config::Ptr config,
                 PARENT *parent)
        : WS::Client::HTTPCore(io_context, std::move(config)),
          parent_(parent)
    {
    }

    void attach(PARENT *parent)
    {
        parent_ = parent;
    }

    void detach(const bool keepalive, const bool shutdown)
    {
        if (parent_)
        {
            parent_ = nullptr;
            if (!keepalive)
                stop(shutdown);
        }
    }

    PARENT *parent()
    {
        return parent_;
    }

    virtual Host http_host()
    {
        if (parent_)
            return parent_->http_host(*this);
        else
            throw http_delegate_error("http_host");
    }

    virtual Request http_request()
    {
        if (parent_)
            return parent_->http_request(*this);
        else
            throw http_delegate_error("http_request");
    }

    virtual ContentInfo http_content_info()
    {
        if (parent_)
            return parent_->http_content_info(*this);
        else
            throw http_delegate_error("http_content_info");
    }

    virtual BufferPtr http_content_out()
    {
        if (parent_)
            return parent_->http_content_out(*this);
        else
            throw http_delegate_error("http_content_out");
    }

    virtual void http_content_out_needed()
    {
        if (parent_)
            parent_->http_content_out_needed(*this);
        else
            throw http_delegate_error("http_content_out_needed");
    }

    virtual void http_headers_received()
    {
        if (parent_)
            parent_->http_headers_received(*this);
    }

    virtual void http_headers_sent(const Buffer &buf)
    {
        if (parent_)
            parent_->http_headers_sent(*this, buf);
    }

    virtual void http_mutate_resolver_results(results_type &results)
    {
        if (parent_)
            parent_->http_mutate_resolver_results(*this, results);
    }

    virtual void http_content_in(BufferAllocated &buf)
    {
        if (parent_)
            parent_->http_content_in(*this, buf);
    }

    virtual void http_done(const int status, const std::string &description)
    {
        if (parent_)
            parent_->http_done(*this, status, description);
    }

    virtual void http_keepalive_close(const int status, const std::string &description)
    {
        if (parent_)
            parent_->http_keepalive_close(*this, status, description);
    }

    virtual void http_post_connect(AsioPolySock::Base &sock)
    {
        if (parent_)
            parent_->http_post_connect(*this, sock);
    }

  private:
    PARENT *parent_;
};
} // namespace Client
} // namespace WS
} // namespace openvpn
