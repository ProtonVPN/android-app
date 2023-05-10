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

#include <string>
#include <vector>
#include <sstream>
#include <ostream>
#include <cstdint>
#include <utility>
#include <memory>
#include <unordered_map>
#include <deque>

#include <openvpn/io/io.hpp>

#include <openvpn/common/platform.hpp>
#include <openvpn/common/options.hpp>
#include <openvpn/common/to_string.hpp>
#include <openvpn/common/arraysize.hpp>
#include <openvpn/common/function.hpp>
#include <openvpn/common/sockopt.hpp>
#include <openvpn/asio/asiopolysock.hpp>
#include <openvpn/common/core.hpp>
#include <openvpn/buffer/bufstream.hpp>
#include <openvpn/time/timestr.hpp>
#include <openvpn/time/asiotimersafe.hpp>
#include <openvpn/time/coarsetime.hpp>
#include <openvpn/addr/ip.hpp>
#include <openvpn/log/sessionstats.hpp>
#include <openvpn/options/merge.hpp>
#include <openvpn/frame/frame_init.hpp>
#include <openvpn/http/request.hpp>
#include <openvpn/http/reply.hpp>
#include <openvpn/http/status.hpp>
#include <openvpn/transport/tcplink.hpp>
#include <openvpn/ws/httpcommon.hpp>
#include <openvpn/ws/websocket.hpp>
#include <openvpn/server/listenlist.hpp>

#ifdef VPN_BINDING_PROFILES
#include <openvpn/ws/httpvpn.hpp>
#endif

#ifdef OPENVPN_POLYSOCK_SUPPORTS_ALT_ROUTING
#include <openvpn/kovpn/sock_mark.hpp>
#endif

// include acceptors for different protocols
#include <openvpn/proxy/listener.hpp>
#include <openvpn/acceptor/base.hpp>
#include <openvpn/acceptor/tcp.hpp>
#if defined(OPENVPN_PLATFORM_WIN)
#include <openvpn/acceptor/namedpipe.hpp>
#endif
#ifdef ASIO_HAS_LOCAL_SOCKETS
#include <openvpn/acceptor/unix.hpp>
#endif

#ifndef OPENVPN_HTTP_SERV_RC
#define OPENVPN_HTTP_SERV_RC RC<thread_unsafe_refcount>
#endif

namespace openvpn {
namespace WS {
namespace Server {

OPENVPN_EXCEPTION(http_server_exception);

typedef unsigned int client_t;
typedef std::int64_t content_len_t;

struct Status
{
    // Error codes
    enum
    {
        E_SUCCESS = 0,
        E_TCP,
        E_HTTP,
        E_EXCEPTION,
        E_HEADER_SIZE,
        E_CONTENT_SIZE,
        E_EOF_SSL,
        E_EOF_TCP,
        E_GENERAL_TIMEOUT,
        E_EXTERNAL_STOP,
        E_PIPELINE_OVERFLOW,
        E_SHUTDOWN,
        E_ABORTED,

        N_ERRORS
    };

    static std::string error_str(const size_t status)
    {
        static const char *error_names[] = {
            "E_SUCCESS",
            "E_TCP",
            "E_HTTP",
            "E_EXCEPTION",
            "E_HEADER_SIZE",
            "E_CONTENT_SIZE",
            "E_EOF_SSL",
            "E_EOF_TCP",
            "E_GENERAL_TIMEOUT",
            "E_EXTERNAL_STOP",
            "E_PIPELINE_OVERFLOW",
            "E_SHUTDOWN",
            "E_ABORTED",
        };

        static_assert(N_ERRORS == array_size(error_names), "HTTP error names array inconsistency");
        if (status < N_ERRORS)
            return error_names[status];
        else
            return "E_???";
    }
};

struct Config : public RC<thread_unsafe_refcount>
{
    typedef RCPtr<Config> Ptr;

    SSLFactoryAPI::Ptr ssl_factory;
#if defined(OPENVPN_PLATFORM_WIN)
    std::string sddl_string; // Windows named-pipe security descriptor as string
#endif
#ifdef ASIO_HAS_LOCAL_SOCKETS
    mode_t unix_mode = 0;
#endif
    unsigned int tcp_backlog = 16;
    unsigned int tcp_throttle_max_connections_per_period = 0; // set > 0 to enable throttling
    Time::Duration tcp_throttle_period;
    unsigned int tcp_max = 0;
    unsigned int general_timeout = 60;
    unsigned int max_headers = 0;
    unsigned int max_header_bytes = 0;
    content_len_t max_content_bytes = 0;
    unsigned int msg_overhead_bytes = 0;
    unsigned int send_queue_max_size = 0;
    unsigned int free_list_max_size = 8;
    unsigned int pipeline_max_size = 64;
    unsigned int sockopt_flags = 0;
    std::string http_server_id;
    Frame::Ptr frame;
    SessionStats::Ptr stats;
};

struct ContentInfo
{
    // content length if Transfer-Encoding: chunked
    static constexpr content_len_t CHUNKED = -1;

    int http_status = 0;
    std::string http_status_str; // optional
    std::string type;
    std::string content_encoding;
    std::string basic_realm;
    content_len_t length = 0;
    bool no_cache = false;
    bool keepalive = false;
    bool lean_headers = false;
    std::vector<std::string> extra_headers;
    WebSocket::Server::PerRequest::Ptr websocket;
};

class Listener : public ProxyListener
{
  public:
    class Client;

  private:
    typedef WS::HTTPBase<Client, Config, Status, HTTP::RequestType, ContentInfo, content_len_t, OPENVPN_HTTP_SERV_RC> Base;

  public:
    class Client : public Base
    {
        friend Base;
        friend Listener;

      public:
        struct AsioProtocol
        {
            typedef AsioPolySock::Base socket;
        };

        typedef RCPtr<Client> Ptr;

        class Initializer
        {
            friend Listener;
            friend Client;

            Initializer(openvpn_io::io_context &io_context_arg,
                        Listener *parent_arg,
                        AsioPolySock::Base::Ptr &&socket_arg,
                        const client_t client_id_arg)
                : io_context(io_context_arg),
                  parent(parent_arg),
                  socket(std::move(socket_arg)),
                  client_id(client_id_arg)
            {
            }

            openvpn_io::io_context &io_context;
            Listener *parent;
            AsioPolySock::Base::Ptr socket;
            const client_t client_id;
        };

        struct Factory : public OPENVPN_HTTP_SERV_RC
        {
            typedef RCPtr<Factory> Ptr;

            virtual Client::Ptr new_client(Initializer &ci) = 0;
            virtual void stop()
            {
            }
        };

        virtual ~Client()
        {
            stop(false, false);
        }

        bool remote_ip_port(IP::Addr &addr, unsigned int &port) const
        {
            if (sock)
                return sock->remote_ip_port(addr, port);
            else
                return false;
        }

        IP::Addr remote_ip() const
        {
            IP::Addr addr;
            unsigned int port;
            if (remote_ip_port(addr, port))
                return addr;
            else
                return IP::Addr();
        }

        AuthCert::Ptr auth_cert() const
        {
            if (ssl_sess)
                return ssl_sess->auth_cert();
            else
                return AuthCert::Ptr();
        }

        bool is_ssl() const
        {
            return bool(ssl_sess);
        }

        bool is_local() const
        {
            if (sock)
                return sock->is_local();
            else
                return false;
        }

        bool is_alt_routing() const
        {
#ifdef OPENVPN_POLYSOCK_SUPPORTS_ALT_ROUTING
            return is_alt_routing_;
#else
            return false;
#endif
        }

      protected:
        Client(Initializer &ci)
            : Base(ci.parent->config),
              io_context(ci.io_context),
              sock(std::move(ci.socket)),
              parent(ci.parent),
              timeout_timer(ci.io_context),
              client_id(ci.client_id)
        {
        }

        void generate_reply_headers(ContentInfo ci)
        {
            http_out_begin();

            content_info = std::move(ci);

            outbuf.reset(new BufferAllocated(512, BufferAllocated::GROW));
            BufferStreamOut os(*outbuf);

            // websocket?
            const bool ws = (content_info.websocket && content_info.http_status == HTTP::Status::SwitchingProtocols);

            if (ws)
                generate_reply_headers_websocket(os);
            else
                generate_reply_headers_http(os);

            http_headers_sent(*outbuf);
            http_out();

            if (ws)
                begin_websocket();
        }

        void generate_custom_reply_headers(BufferPtr &buf)
        {
            http_out_begin();
            outbuf = std::move(buf);
            http_headers_sent(*outbuf);
            http_out();
        }

        // return true if client asked for keepalive
        bool keepalive_request()
        {
            return headers().get_value_trim("connection") == "keep-alive";
        }

        const HTTP::Request &request() const
        {
            return request_reply();
        }

        void register_activity()
        {
            activity();
        }

        void external_stop(const std::string &description)
        {
            error_handler(Status::E_EXTERNAL_STOP, description);
        }

        void abort(const std::string &description, const int status = Status::E_ABORTED)
        {
            if (!halt)
                error_handler(status, description);
        }

        std::string remote_endpoint_str() const
        {
            try
            {
                if (sock)
                    return sock->remote_endpoint_str();
            }
            catch (const std::exception &)
            {
            }
            return "[unknown endpoint]";
        }

        client_t get_client_id() const
        {
            return client_id;
        }

        Listener *get_parent() const
        {
            return parent;
        }

#ifdef ASIO_HAS_LOCAL_SOCKETS
        int unix_fd()
        {
            AsioPolySock::Unix *uds = dynamic_cast<AsioPolySock::Unix *>(sock.get());
            if (uds)
                return uds->socket.native_handle();
            else
                return -1;
        }
#endif

        openvpn_io::io_context &io_context;
        AsioPolySock::Base::Ptr sock;
        std::deque<BufferAllocated> pipeline;
        Time::Duration timeout_duration;

      private:
        typedef TCPTransport::Link<AsioProtocol, Client *, false> LinkImpl;
        friend LinkImpl::Base; // calls tcp_* handlers

        void generate_reply_headers_http(std::ostream &os)
        {
            os << "HTTP/1.1 " << content_info.http_status << ' ';
            if (content_info.http_status_str.empty())
                os << HTTP::Status::to_string(content_info.http_status);
            else
                os << content_info.http_status_str;
            os << "\r\n";
            if (!content_info.lean_headers)
            {
                if (!parent->config->http_server_id.empty())
                    os << "Server: " << parent->config->http_server_id << "\r\n";
                os << "Date: " << date_time_rfc822() << "\r\n";
            }
            if (!content_info.basic_realm.empty())
                os << "WWW-Authenticate: Basic realm=\"" << content_info.basic_realm << "\"\r\n";
            if (content_info.length)
                os << "Content-Type: " << content_info.type << "\r\n";
            if (content_info.length > 0)
                os << "Content-Length: " << content_info.length << "\r\n";
            else if (content_info.length == ContentInfo::CHUNKED)
                os << "Transfer-Encoding: chunked\r\n";
            for (auto &h : content_info.extra_headers)
                os << h << "\r\n";
            if (!content_info.content_encoding.empty())
                os << "Content-Encoding: " << content_info.content_encoding << "\r\n";
            if (content_info.no_cache && !content_info.lean_headers)
                os << "Cache-Control: no-cache, no-store, must-revalidate\r\n";
            if ((keepalive = content_info.keepalive))
                os << "Connection: keep-alive\r\n";
            else
                os << "Connection: close\r\n";
            os << "\r\n";
        }

        void generate_reply_headers_websocket(std::ostream &os)
        {
            os << "HTTP/1.1 101 Switching Protocols\r\n";
            if (content_info.websocket)
                content_info.websocket->server_headers(os);
            for (auto &h : content_info.extra_headers)
                os << h << "\r\n";
            os << "\r\n";
        }

        // transition to websocket i/o after we push HTTP
        // headers to client
        void begin_websocket()
        {
            cancel_general_timeout(); // timeouts could be harmful for long-running websockets
            set_async_out(true);      // websockets require async output
            websocket = true;         // enable websocket in httpcommon
            ready = false;            // enable tcp_in
            consume_pipeline();       // process data received while tcp_in was disabled
        }

        void cancel_general_timeout()
        {
            timeout_duration.set_zero();
            timeout_timer.cancel();
        }

        void start(const Acceptor::Item::SSLMode ssl_mode)
        {
            timeout_coarse.init(Time::Duration::binary_ms(512), Time::Duration::binary_ms(1024));
            link.reset(new LinkImpl(this,
                                    *sock,
                                    parent->config->send_queue_max_size,
                                    parent->config->free_list_max_size,
                                    (*parent->config->frame)[Frame::READ_HTTP],
                                    stats));
            link->set_raw_mode(true);
            switch (ssl_mode)
            {
            case Acceptor::Item::SSLOff:
                break;
            case Acceptor::Item::SSLOn:
                ssl_sess = parent->config->ssl_factory->ssl();
                break;
#ifdef OPENVPN_POLYSOCK_SUPPORTS_ALT_ROUTING
            case Acceptor::Item::AltRouting:
                is_alt_routing_ = true;
                break;
#endif
            }
            restart(true);
        }

        void restart(const bool initial)
        {
            timeout_duration = Time::Duration::seconds(parent->config->general_timeout);
            timeout_coarse.reset();
            activity();
            rr_reset();
            ready = false;
            consume_pipeline();
            if (initial || handoff)
                link->start();
            handoff = false;
        }

        void stop(const bool remove_self_from_map, const bool shutdown)
        {
            if (halt)
                return;
            halt = true;
            if (!http_stop_called)
                http_stop(Status::E_SUCCESS, "stop");
            http_destroy();
            timeout_timer.cancel();
            if (link)
                link->stop();
            if (sock)
            {
                if (shutdown)
                    sock->shutdown(AsioPolySock::SHUTDOWN_SEND | AsioPolySock::SHUTDOWN_RECV);
                sock->close();
            }
            if (remove_self_from_map)
                openvpn_io::post(io_context, [self = Ptr(this), parent = Listener::Ptr(parent)]() mutable
                                 { parent->remove_client(std::move(self)); });
        }

        void activity()
        {
            if (timeout_duration.defined())
            {
                const Time now = Time::now();
                const Time next = now + timeout_duration;
                if (!timeout_coarse.similar(next))
                {
                    timeout_coarse.reset(next);
                    timeout_timer.expires_at(next);
                    timeout_timer.async_wait([self = Ptr(this)](const openvpn_io::error_code &error)
                                             {
					       if (!error)
						 self->timeout_callback(error); });
                }
            }
        }

        void timeout_callback(const openvpn_io::error_code &e)
        {
            if (halt || e)
                return;
            error_handler(Status::E_GENERAL_TIMEOUT, "General timeout");
        }

        void add_to_pipeline(BufferAllocated &buf)
        {
            if (!buf.empty())
                http_pipeline_peek(buf);
            if (halt)
                return;
            if (buf.empty())
                return;
            if (pipeline.size() >= parent->config->pipeline_max_size)
                error_handler(Status::E_PIPELINE_OVERFLOW, "Pipeline overflow");
            pipeline.push_back(std::move(buf));
        }

        void consume_pipeline()
        {
            while (!pipeline.empty() && !ready)
            {
                BufferAllocated buf(std::move(pipeline.front()));
                pipeline.pop_front();
                tcp_in(buf);
            }
        }

        // Implemented by child class for any kind of intercept processing
        // (i.e. parsing and stripping the Proxy Protocol v1 header)
        virtual void tcp_intercept(BufferAllocated &b)
        {
        }

        // methods called by LinkImpl

        bool tcp_read_handler(BufferAllocated &b)
        {
            if (halt)
                return false;

            tcp_intercept(b);

            try
            {
                activity();
                if (ready)
                    add_to_pipeline(b);
                else
                    tcp_in(b); // call Base
            }
            catch (const std::exception &e)
            {
                handle_exception("tcp_read_handler", e);
            }
            return !handoff; // don't requeue read if handoff, i.e. parent wants to take control of session socket
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
            http_content_out_needed();
        }

        void base_http_out_eof()
        {
            if (http_out_eof())
            {
                if (keepalive && !websocket)
                    restart(false);
                else
                    error_handler(Status::E_SUCCESS, "Succeeded");
            }
        }

        bool base_http_headers_received()
        {
            return http_headers_received();
        }

        void base_http_content_in(BufferAllocated &buf)
        {
            http_content_in(buf);
        }

        bool base_link_send(BufferAllocated &buf)
        {
            activity();
            return link->send(buf);
        }

        bool base_send_queue_empty()
        {
            return link->send_queue_empty();
        }

        void base_http_done_handler(BufferAllocated &residual,
                                    const bool parent_handoff)
        {
            if (halt)
                return;
            ready = true;
            handoff = parent_handoff;
            add_to_pipeline(residual);
            http_request_received();
        }

        void base_error_handler(const int errcode, const std::string &err)
        {
            error_handler(errcode, err);
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
            const bool shutdown = http_stop(errcode, err);
            http_stop_called = true;
            stop(true, shutdown);
        }

        // virtual methods

        virtual BufferPtr http_content_out()
        {
            return BufferPtr();
        }

        virtual void http_content_out_needed()
        {
        }

        virtual bool http_headers_received()
        {
            return true;
        }

        virtual void http_request_received()
        {
        }

        virtual void http_pipeline_peek(BufferAllocated &buf)
        {
        }

        virtual void http_content_in(BufferAllocated &buf)
        {
        }

        virtual void http_headers_sent(const Buffer &buf)
        {
        }

        virtual bool http_out_eof()
        {
            return true;
        }

        virtual bool http_stop(const int status, const std::string &description)
        {
            return false;
        }

        virtual void http_destroy()
        {
        }

        Listener *parent;
        AsioTimerSafe timeout_timer;
        CoarseTime timeout_coarse;
        client_t client_id;
        LinkImpl::Ptr link;
        bool keepalive = false;
        bool handoff = false;
        bool http_stop_called = false;

#ifdef OPENVPN_POLYSOCK_SUPPORTS_ALT_ROUTING
        bool is_alt_routing_ = false;
#endif
    };

  public:
    typedef RCPtr<Listener> Ptr;

    template <typename L> // L is a Listen::Item or Listen::List
    Listener(openvpn_io::io_context &io_context_arg,
             const Config::Ptr &config_arg,
             const L &listen_item_or_list,
             const Client::Factory::Ptr &client_factory_arg)
        : io_context(io_context_arg),
          listen_list(listen_item_or_list),
          config(config_arg),
          client_factory(client_factory_arg),
          throttle_timer(io_context)
    {
    }

    virtual void start() override
    {
        if (halt)
            return;

        acceptors.reserve(listen_list.size());
        for (const auto &listen_item : listen_list)
        {
            switch (listen_item.proto())
            {
            case Protocol::TCP:
            case Protocol::TCPv4:
            case Protocol::TCPv6:
                {
                    // ssl enabled?
                    Acceptor::Item::SSLMode ssl_mode = Acceptor::Item::SSLOff;
                    switch (listen_item.ssl)
                    {
                    case Listen::Item::SSLUnspecified:
                        ssl_mode = bool(config->ssl_factory) ? Acceptor::Item::SSLOn : Acceptor::Item::SSLOff;
                        break;
                    case Listen::Item::SSLOn:
                        if (listen_item.ssl == Listen::Item::SSLOn && !config->ssl_factory)
                            throw http_server_exception("listen item has 'ssl' qualifier, but no SSL configuration");
                        ssl_mode = Acceptor::Item::SSLOn;
                        break;
                    case Listen::Item::SSLOff:
                        break;
#ifdef OPENVPN_POLYSOCK_SUPPORTS_ALT_ROUTING
                    case Listen::Item::AltRouting:
                        ssl_mode = Acceptor::Item::AltRouting;
                        break;
#endif
                    }

                    OPENVPN_LOG("HTTP" << ((ssl_mode == Acceptor::Item::SSLOn) ? "S" : "") << " Listen: " << listen_item.to_string());

                    // init TCP acceptor
                    Acceptor::TCP::Ptr a(new Acceptor::TCP(io_context));

                    // parse address/port of local endpoint
#ifdef VPN_BINDING_PROFILES
                    const IP::Addr ip_addr = ViaVPN::server_local_addr(listen_item, via_vpn_gw(listen_item.proto));
#else
                    const IP::Addr ip_addr(listen_item.addr, listen_item.directive);
#endif
                    a->local_endpoint.address(ip_addr.to_asio());
                    a->local_endpoint.port(HostPort::parse_port(listen_item.port, "http listen"));

                    // open socket
                    a->acceptor.open(a->local_endpoint.protocol());

                    // set options
                    a->set_socket_options(config->sockopt_flags);

                    // bind to local address
#ifdef OPENVPN_DEBUG_ACCEPT
                    OPENVPN_LOG("ACCEPTOR BIND " << a->local_endpoint);
#endif
                    a->acceptor.bind(a->local_endpoint);

                    // listen for incoming client connections
                    a->acceptor.listen(config->tcp_backlog);

                    // save acceptor
                    acceptors.emplace_back(std::move(a), ssl_mode);

                    // queue accept on listen socket
                    queue_accept_throttled(acceptors.size() - 1, false);
                }
                break;
#if defined(OPENVPN_PLATFORM_WIN)
            case Protocol::NamedPipe:
                {
                    OPENVPN_LOG("HTTP Listen: " << listen_item.to_string());

                    // create named pipe
                    Acceptor::NamedPipe::Ptr a(new Acceptor::NamedPipe(io_context, listen_item.addr, config->sddl_string));

                    // save acceptor
                    acceptors.emplace_back(std::move(a), Acceptor::Item::SSLOff);

                    // queue accept on listen socket
                    queue_accept_throttled(acceptors.size() - 1, false);
                }
                break;
#endif
#ifdef ASIO_HAS_LOCAL_SOCKETS
            case Protocol::UnixStream:
                {
                    OPENVPN_LOG("HTTP Listen: " << listen_item.to_string());

                    Acceptor::Unix::Ptr a(new Acceptor::Unix(io_context));

                    // set endpoint
                    a->pre_listen(listen_item.addr);
                    a->local_endpoint.path(listen_item.addr);

                    // open socket
                    a->acceptor.open(a->local_endpoint.protocol());

                    // bind to local address
                    a->acceptor.bind(a->local_endpoint);

                    // set socket permissions in filesystem
                    a->set_socket_permissions(listen_item.addr, config->unix_mode);

                    // listen for incoming client connections
                    a->acceptor.listen();

                    // save acceptor
                    acceptors.emplace_back(std::move(a), Acceptor::Item::SSLOff);

                    // queue accept on listen socket
                    queue_accept_throttled(acceptors.size() - 1, false);
                }
                break;
#endif
            default:
                throw http_server_exception("listen on unknown protocol");
            }
        }
    }

    virtual void stop() override
    {
        if (halt)
            return;
        halt = true;

        // close acceptors
        acceptors.close();

        throttle_timer.cancel();

        // stop clients
        for (auto &c : clients)
            c.second->stop(false, false);
        clients.clear();

        // stop client factory
        if (client_factory)
            client_factory->stop();
    }

    template <typename CLIENT_INSTANCE, typename FUNC>
    void walk(FUNC func) const
    {
        for (auto &c : clients)
            func(*static_cast<CLIENT_INSTANCE *>(c.second.get()));
    }

  private:
    typedef std::unordered_map<client_t, Client::Ptr> ClientMap;

    void queue_accept(const size_t acceptor_index)
    {
        acceptors[acceptor_index].acceptor->async_accept(this, acceptor_index, io_context);
    }

    void queue_accept_throttled(const size_t acceptor_index, const bool debit_one)
    {
        if (config->tcp_throttle_max_connections_per_period)
        {
            if (throttle_acceptor_indices.empty())
            {
                const Time now = Time::now();
                if (now >= throttle_expire)
                    throttle_reset(now, debit_one);
                if (throttle_connections > 0)
                {
                    --throttle_connections;
                    queue_accept(acceptor_index);
                }
                else
                {
                    // throttle it
                    throttle_acceptor_indices.push_back(acceptor_index);
                    throttle_timer_wait();
                }
            }
            else
                throttle_acceptor_indices.push_back(acceptor_index);
        }
        else
            queue_accept(acceptor_index);
    }

    void throttle_reset(const Time &now, const bool debit_one)
    {
        throttle_connections = config->tcp_throttle_max_connections_per_period;
        if (debit_one)
            --throttle_connections;
        throttle_expire = now + config->tcp_throttle_period;
    }

    void throttle_timer_wait()
    {
        throttle_timer.expires_at(throttle_expire);
        throttle_timer.async_wait([self = Ptr(this)](const openvpn_io::error_code &error)
                                  {
				      if (self->halt || error)
					return;
				      self->throttle_timer_callback(); });
    }

    void throttle_timer_callback()
    {
        throttle_reset(Time::now(), false);
        while (!throttle_acceptor_indices.empty() && throttle_connections > 0)
        {
            const size_t acceptor_index = throttle_acceptor_indices.front();
            queue_accept(acceptor_index);
            throttle_acceptor_indices.pop_front();
            --throttle_connections;
        }
        if (!throttle_acceptor_indices.empty())
            throttle_timer_wait();
    }

    virtual void handle_accept(AsioPolySock::Base::Ptr sock, const openvpn_io::error_code &error) override
    {
        if (halt)
            return;

        const size_t acceptor_index = sock->index();

        try
        {
            if (!error)
            {
                const Acceptor::Item::SSLMode ssl_mode = acceptors[acceptor_index].ssl_mode;

#ifdef OPENVPN_DEBUG_ACCEPT
                OPENVPN_LOG("ACCEPT from " << sock->remote_endpoint_str());
#endif

                sock->non_blocking(true);
                sock->set_cloexec();
                sock->tcp_nodelay();

                if (config->tcp_max && clients.size() >= config->tcp_max)
                    throw http_server_exception("max clients exceeded");
                if (!allow_client(*sock))
                    throw http_server_exception("client socket rejected");

#ifdef OPENVPN_POLYSOCK_SUPPORTS_ALT_ROUTING
                if (ssl_mode == Acceptor::Item::AltRouting)
                {
                    const KovpnSockMark ksm(sock->native_handle());
                    if (!ksm.is_internal())
                        throw http_server_exception("non alt-routing socket: " + ksm.to_string());
                }
#endif

                const client_t client_id = new_client_id();
                Client::Initializer ci(io_context, this, std::move(sock), client_id);
                Client::Ptr cli = client_factory->new_client(ci);
                clients[client_id] = cli;

                cli->start(ssl_mode);
            }
            else
                throw http_server_exception("accept failed: " + error.message());
        }
        catch (const std::exception &e)
        {
            OPENVPN_LOG("exception in handle_accept: " << e.what());
        }

        queue_accept_throttled(acceptor_index, true);
    }

    client_t new_client_id()
    {
        while (true)
        {
            // find an ID that's not already in use
            const client_t id = next_id++;
            if (clients.find(id) == clients.end())
                return id;
        }
    }

    void remove_client(Client::Ptr cli)
    {
        remove_client_id(cli->get_client_id());
    }

    void remove_client_id(const client_t client_id)
    {
        ClientMap::const_iterator e = clients.find(client_id);
        if (e != clients.end())
            clients.erase(e);
    }

    virtual bool allow_client(AsioPolySock::Base &sock)
    {
        return true;
    }

#ifdef VPN_BINDING_PROFILES
    static ViaVPN::GatewayType via_vpn_gw(const Protocol &proto)
    {
        switch (proto())
        {
        case Protocol::TCPv4:
            return ViaVPN::GW4;
        case Protocol::TCPv6:
            return ViaVPN::GW6;
        default:
            return ViaVPN::GW;
        }
    }
#endif

    openvpn_io::io_context &io_context;
    Listen::List listen_list;
    Config::Ptr config;
    Client::Factory::Ptr client_factory;
    bool halt = false;

    Acceptor::Set acceptors;

    AsioTimerSafe throttle_timer;
    Time throttle_expire;
    int throttle_connections = 0;
    std::deque<size_t> throttle_acceptor_indices;

    client_t next_id = 0;
    ClientMap clients;
};

} // namespace Server
} // namespace WS
} // namespace openvpn
