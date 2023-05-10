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
#include <sstream>
#include <ostream>
#include <vector>
#include <memory>
#include <utility>
#include <algorithm>
#include <limits>
#include <map>

#include <openvpn/asio/asiostop.hpp>
#include <openvpn/common/cleanup.hpp>
#include <openvpn/common/function.hpp>
#include <openvpn/common/complog.hpp>
#include <openvpn/time/asiotimersafe.hpp>
#include <openvpn/buffer/buflist.hpp>
#include <openvpn/buffer/bufstr.hpp>
#include <openvpn/buffer/zlib.hpp>
#include <openvpn/random/randapi.hpp>
#include <openvpn/http/urlparse.hpp>
#include <openvpn/http/headredact.hpp>
#include <openvpn/ws/httpcli.hpp>
#include <openvpn/ws/resolver_results.hpp>

#ifndef OPENVPN_HTTP_CLISET_RC
#define OPENVPN_HTTP_CLISET_RC RC<thread_unsafe_refcount>
#endif

namespace openvpn {
namespace WS {

class ClientSet : public RC<thread_unsafe_refcount>
{
    class Client;

  public:
    typedef RCPtr<ClientSet> Ptr;

    typedef WS::Client::HTTPDelegate<Client> HTTPDelegate;

    struct SyncPersistState
    {
        std::unique_ptr<openvpn_io::io_context> io_context;
    };

    class HTTPStateContainer
    {
      public:
        void create_container()
        {
            if (!c)
                c.reset(new Container);
        }

        void stop(const bool shutdown)
        {
            if (c && c->http)
                c->http->stop(shutdown);
        }

        void reset()
        {
            if (c)
                c->http.reset();
        }

        void abort(const std::string &message)
        {
            if (c && c->http)
                c->http->abort(message);
        }

        bool alive() const
        {
            return c && c->http && c->http->is_alive();
        }

        bool alive(const std::string &host) const
        {
            return alive() && c->http->host_match(host);
        }

        // used for synchronous io_context

        std::unique_ptr<openvpn_io::io_context> acquire_io_context()
        {
            if (c)
                return std::move(c->sps.io_context);
            else
                return std::unique_ptr<openvpn_io::io_context>();
        }

        void persist_io_context(std::unique_ptr<openvpn_io::io_context> &&io_context)
        {
            if (c)
                c->sps.io_context = std::move(io_context);
        }

#ifdef ASIO_HAS_LOCAL_SOCKETS
        int unix_fd()
        {
            if (!c || !c->http)
                return -1;
            AsioPolySock::Unix *us = dynamic_cast<AsioPolySock::Unix *>(c->http->get_socket());
            if (!us)
                return -1;
            return us->socket.native_handle();
        }
#endif

      private:
        friend Client;

        struct Container : public RC<thread_unsafe_refcount>
        {
            typedef RCPtr<Container> Ptr;
            SyncPersistState sps;
            HTTPDelegate::Ptr http;
        };

        void attach(Client *parent)
        {
            c->http->attach(parent);
        }

        void close(const bool keepalive, const bool shutdown)
        {
            if (c && c->http)
            {
                c->http->detach(keepalive, shutdown);
                if (!keepalive)
                    stop(shutdown);
            }
        }

        void construct(openvpn_io::io_context &io_context,
                       const WS::Client::Config::Ptr config)
        {
            create_container();
            close(false, false);
            c->http.reset(new HTTPDelegate(io_context, std::move(config), nullptr));
        }

        void start_request()
        {
            c->http->start_request();
        }

        Container::Ptr c;
    };

    // like HTTPStateContainer, but destructor automatically
    // calls stop() method
    class HTTPStateContainerAutoStop : public HTTPStateContainer
    {
      public:
        HTTPStateContainerAutoStop(const bool shutdown)
            : shutdown_(shutdown)
        {
        }

        ~HTTPStateContainerAutoStop()
        {
            HTTPStateContainer::stop(shutdown_);
        }

      private:
        const bool shutdown_;
    };

    class TransactionSet;
    struct Transaction;

    struct ErrorRecovery : public RC<thread_unsafe_refcount>
    {
        typedef RCPtr<ErrorRecovery> Ptr;
        virtual void retry(TransactionSet &ts, Transaction &t) = 0;
    };

    struct Transaction
    {
        static constexpr int UNDEF = -1;

        // input
        WS::Client::Request req;
        WS::Client::ContentInfo ci;
        BufferList content_out;
        bool accept_gzip_in = false;
        bool randomize_resolver_results = false;
        IP::Addr::Version ip_version_preference = IP::Addr::UNSPEC;

        // output
        int status = UNDEF;
        std::string description;
        HTTP::Reply reply;
        BufferList content_in;

        std::string url(const TransactionSet &ts) const
        {
            URL::Parse u = URL::Parse::from_components(bool(ts.http_config->ssl_factory),
                                                       ts.host.host,
                                                       ts.host.port,
                                                       req.uri);
            return u.to_string();
        }

        std::string title(const TransactionSet &ts) const
        {
            return req.method + ' ' + url(ts);
        }

        void compress_content_out(const unsigned int min_size = 64,
                                  const bool verbose = false)
        {
#ifdef HAVE_ZLIB
            if (content_out.join_size() >= min_size)
            {
                BufferPtr co = content_out.join();
                content_out.clear();
                const size_t orig_size = co->size();
                co = ZLib::compress_gzip(co, 0, 0, 1);
                if (verbose)
                    log_compress("HTTPClientSet: GZIP COMPRESS", orig_size, co->size());
                ci.length = co->size();
                content_out.push_back(std::move(co));
                ci.content_encoding = "gzip";
            }
#endif
        }

        // Return true if and only if HTTP transaction
        // succeeded AND HTTP status code was in the
        // successful range of 2xx.
        bool http_status_success() const
        {
            return comm_status_success() && request_status_success();
        }

        // Return true if communication succeeded
        bool comm_status_success() const
        {
            return status == WS::Client::Status::E_SUCCESS;
        }

        bool comm_status_timeout() const
        {
            return status == WS::Client::Status::E_CONNECT_TIMEOUT;
        }

        // Return true if request succeeded, i.e. HTTP status
        // code was in the successful range of 2xx.
        bool request_status_success() const
        {
            return reply.status_code >= 200 && reply.status_code < 300;
        }

        bool is_redirect() const
        {
            return reply.status_code >= 300 && reply.status_code < 400 && reply.headers.get("location");
        }

        std::string get_redirect_location() const
        {
            return reply.headers.get_value_trim("location");
        }

        void dump(std::ostream &os, const TransactionSet &ts) const
        {
            os << "----- " << format_status(ts) << " -----\n";
            BufferPtr in = content_in.join();
            const std::string s = buf_to_string(*in);
            os << s;
            if (!s.empty() && !string::ends_with_newline(s))
                os << '\n';
        }

        std::string content_in_string() const
        {
            BufferPtr in = content_in.join();
            return buf_to_string(*in);
        }

        BufferPtr content_in_buffer() const
        {
            return content_in.join();
        }

        std::string format_status(const TransactionSet &ts) const
        {
            std::string ret;
            ret.reserve(256);
            ret += title(ts);
            ret += " : ";
            ret += format_status();
            return ret;
        }

        std::string format_status() const
        {
            std::string ret;
            ret.reserve(64);
            if (status == WS::Client::Status::E_SUCCESS)
            {
                ret += openvpn::to_string(reply.status_code);
                ret += ' ';
                ret += reply.status_text;
            }
            else
            {
                ret += WS::Client::Status::error_str(status);
                ret += ' ';
                ret += description;
            }
            return ret;
        }
    };

    class TransactionSet : public RC<thread_unsafe_refcount>
    {
      public:
        typedef RCPtr<TransactionSet> Ptr;
        typedef std::vector<std::unique_ptr<Transaction>> Vector;

        // Enable preserve_http_state to reuse HTTP session
        // across multiple completions.
        // hsc.stop() can be called to explicitly
        // close persistent state.
        bool preserve_http_state = false;
        HTTPStateContainer hsc;

        // configuration
        WS::Client::Config::Ptr http_config;
        WS::Client::Host host;
        unsigned int max_retries = 1;
        bool retry_on_http_4xx = false;
        int debug_level = 2;
        Time::Duration delayed_start;
        Time::Duration retry_duration = Time::Duration::seconds(5);

        // request/response vector
        Vector transactions;

        // true if all requests were successful
        bool status = false;

        // completion method
        Function<void(TransactionSet &ts)> completion;

        // post-connect method, useful to validate server
        // on local sockets
        Function<void(TransactionSet &ts, AsioPolySock::Base &sock)> post_connect;

        // error recovery method, called before we retry a request
        // after an error to possibly modify connection parameters
        // such as the hostname.
        ErrorRecovery::Ptr error_recovery;

        void assign_http_state(HTTPStateContainer &http_state)
        {
            http_state.create_container();
            hsc = http_state;
            preserve_http_state = true;
        }

        bool alive() const
        {
            return hsc.alive(host.host);
        }

        WS::ClientSet::Transaction &first_transaction()
        {
            if (transactions.empty())
                throw Exception("TransactionSet::first_transaction: transaction list is empty");
            return *transactions[0];
        }

        // Return true if and only if all HTTP transactions
        // succeeded AND each HTTP status code was in the
        // successful range of 2xx.
        bool http_status_success() const
        {
            if (!status)
                return false;
            if (transactions.empty())
                return false;
            for (auto &t : transactions)
            {
                if (!t->http_status_success())
                    return false;
            }
            return true;
        }

        void reset_callbacks()
        {
            completion.reset();
            post_connect.reset();
        }

        void stop(const bool shutdown)
        {
            reset_callbacks();
            hsc.stop(shutdown);
        }

        void dump(std::ostream &os, const bool content_only = false) const
        {
            for (auto &t : transactions)
            {
                if (content_only)
                    os << t->content_in_string();
                else
                    t->dump(os, *this);
            }
        }
    };

    class HostRetry : public std::vector<std::string>,
                      public ErrorRecovery
    {
      public:
        typedef RCPtr<HostRetry> Ptr;

        HostRetry()
        {
        }

        template <typename T, typename... Args>
        HostRetry(T first, Args... args)
        {
            reserve(1 + sizeof...(args));
            from_list(first, args...);
        }

        void shuffle(RandomAPI &prng)
        {
            std::shuffle(begin(), end(), prng);
            index = 0;
        }

        std::string next_host()
        {
            if (empty())
                throw Exception("HostRetry: empty host list");
            if (index >= size())
                index = 0;
            return (*this)[index++];
        }

        virtual void retry(TransactionSet &ts, Transaction &t) override
        {
            ts.host.host = next_host();
        }

      private:
        void from_list(std::string arg)
        {
            push_back(std::move(arg));
        }

        void from_list(const char *arg)
        {
            push_back(std::string(arg));
        }

        template <typename T, typename... Args>
        void from_list(T first, Args... args)
        {
            from_list(first);
            from_list(args...);
        }

        size_t index = 0;
    };

    ClientSet(openvpn_io::io_context &io_context_arg)
        : io_context(io_context_arg),
          halt(false),
          next_id(0)
    {
    }

    void set_random(RandomAPI::Ptr prng_arg)
    {
        prng = std::move(prng_arg);
    }

    void new_request(const TransactionSet::Ptr ts)
    {
        const client_t id = new_client_id();
        Client::Ptr cli = new Client(this, std::move(ts), id);
        clients[id] = cli;
        cli->start();
    }

    static void new_request_synchronous(const TransactionSet::Ptr ts,
                                        Stop *stop = nullptr,
                                        RandomAPI *prng = nullptr,
                                        const bool sps = false)
    {
        std::unique_ptr<openvpn_io::io_context> io_context;
        auto clean = Cleanup([&]()
                             {
	    // ensure that TransactionSet reference to socket
	    // is reset before method returns (unless sps is true
	    // in which case we should retain it).
	    if (!sps)
	      ts->hsc.reset(); });
        ts->preserve_http_state = sps;
        if (sps)
        {
            io_context = ts->hsc.acquire_io_context();
            if (io_context)
            {
                if (io_context->stopped())
                {
                    // OPENVPN_LOG("RESTART IO_CONTEXT");
                    io_context->restart();
                }
                else
                {
                    // OPENVPN_LOG("GET IO_CONTEXT");
                }
            }
        }
        if (!io_context)
        {
            if (sps)
            {
                // OPENVPN_LOG("NEW IO_CONTEXT");
            }
            io_context.reset(new openvpn_io::io_context(1));
        }
        ClientSet::Ptr cs;
        try
        {
            AsioStopScope scope(*io_context, stop, [&]()
                                {
	      if (cs)
		cs->abort("stop message received"); });
            cs.reset(new ClientSet(*io_context));
            if (prng)
                cs->set_random(RandomAPI::Ptr(prng));
            cs->new_request(ts);
            if (sps)
            {
                while (cs->clients.size() && !io_context->stopped())
                    io_context->run_one();
            }
            else
                io_context->run();
        }
        catch (...)
        {
            if (cs)
                cs->stop();     // on exception, stop ClientSet
            io_context->poll(); // execute completion handlers
            throw;
        }
        if (sps)
        {
            // OPENVPN_LOG("PUT IO_CONTEXT");
            ts->hsc.persist_io_context(std::move(io_context));
        }
    }

    static void run_synchronous(Function<void(ClientSet::Ptr)> job,
                                Stop *stop = nullptr,
                                RandomAPI *prng = nullptr)
    {
        std::unique_ptr<openvpn_io::io_context> io_context(new openvpn_io::io_context(1));
        ClientSet::Ptr cs;
        try
        {
            AsioStopScope scope(*io_context, stop, [&]()
                                {
	      if (cs)
		cs->abort("stop message received"); });
            cs.reset(new ClientSet(*io_context));
            cs->set_random(prng);
            job(cs);
            io_context->run();
        }
        catch (...)
        {
            if (cs)
                cs->stop();     // on exception, stop ClientSet
            io_context->poll(); // execute completion handlers
            throw;
        }
    }

    void stop()
    {
        if (halt)
            return;
        halt = true;
        for (auto &c : clients)
        {
            c.second->stop(false, false);
            c.second->reset_callbacks();
        }
    }

    void abort(const std::string &message)
    {
        for (auto &c : clients)
            c.second->abort(message);
    }

  private:
    typedef unsigned int client_t;

    class Client : public OPENVPN_HTTP_CLISET_RC
    {
      public:
        typedef RCPtr<Client> Ptr;
        friend HTTPDelegate;

        Client(ClientSet *parent_arg,
               const TransactionSet::Ptr ts_arg,
               client_t client_id_arg)
            : parent(parent_arg),
              ts(std::move(ts_arg)),
              n_retries(0),
              buf_tailroom((*ts->http_config->frame)[Frame::READ_HTTP].tailroom()),
              reconnect_timer(parent_arg->io_context),
              client_id(client_id_arg),
              halt(false),
              started(false)
        {
        }

        bool start()
        {
            if (started || halt)
                return false;
            started = true;
            ts->status = false;
            ts_iter = ts->transactions.begin();
            if (ts->delayed_start.defined())
            {
                retry_duration = ts->delayed_start;
                reconnect_schedule(false);
            }
            else
            {
                next_request(false);
            }
            return true;
        }

        void stop(const bool keepalive, const bool shutdown)
        {
            if (halt)
                return;
            halt = true;
            reconnect_timer.cancel();
            close_http(keepalive, shutdown);
        }

        void reset_callbacks()
        {
            if (ts)
                ts->reset_callbacks(); // break refcount cycles in callback closures
        }

        void abort(const std::string &message)
        {
            if (ts)
                ts->hsc.abort(message);
        }

      private:
        void close_http(const bool keepalive, const bool shutdown)
        {
            ts->hsc.close(keepalive, shutdown);
        }

        void remove_self_from_map()
        {
            openvpn_io::post(parent->io_context,
                             [id = client_id, parent = ClientSet::Ptr(parent)]()
                             { parent->remove_client_id(id); });
        }

        bool check_if_done()
        {
            if (ts_iter == ts->transactions.end())
            {
                done(true, true);
                return true;
            }
            else
                return false;
        }

        void done(const bool status, const bool shutdown)
        {
            {
                auto clean = Cleanup([this, shutdown]()
                                     {
		if (!ts->preserve_http_state)
		  ts->hsc.stop(shutdown); });
                stop(status, shutdown);
                remove_self_from_map();
                ts->status = status;
            }
            if (ts->completion)
                ts->completion(*ts);
        }

        Transaction &trans()
        {
            return **ts_iter;
        }

        const Transaction &trans() const
        {
            return **ts_iter;
        }

        std::string title() const
        {
            return trans().title(*ts);
        }

        void next_request(const bool error_retry)
        {
            if (check_if_done())
                return;

            retry_duration = ts->retry_duration;

            // get current transaction
            Transaction &t = trans();

            // set up content out iterator
            out_iter = t.content_out.begin();

            // init buffer to receive content in
            t.content_in.clear();

            // if this is an error retry, allow user-defined recovery
            if (error_retry && ts->error_recovery)
                ts->error_recovery->retry(*ts, t);

            // init and attach HTTPStateContainer
            if (ts->debug_level >= 3)
                OPENVPN_LOG("HTTPStateContainer alive=" << ts->alive() << " error_retry=" << error_retry << " n_clients=" << parent->clients.size());
            if (!ts->alive())
                ts->hsc.construct(parent->io_context, ts->http_config);
            ts->hsc.attach(this);

            ts->hsc.start_request();
        }

        void reconnect_schedule(const bool error_retry)
        {
            if (check_if_done())
                return;
            reconnect_timer.expires_after(retry_duration);
            reconnect_timer.async_wait([self = Ptr(this), error_retry](const openvpn_io::error_code &error)
                                       {
				       if (!error && !self->halt)
					 self->next_request(error_retry); });
        }

        WS::Client::Host http_host(HTTPDelegate &hd) const
        {
            return ts->host;
        }

        WS::Client::Request http_request(HTTPDelegate &hd) const
        {
            return trans().req;
        }

        WS::Client::ContentInfo http_content_info(HTTPDelegate &hd) const
        {
            const Transaction &t = trans();
            WS::Client::ContentInfo ci = t.ci;
            if (!ci.length)
                ci.length = t.content_out.join_size();
#ifdef HAVE_ZLIB
            if (t.accept_gzip_in)
                ci.extra_headers.emplace_back("Accept-Encoding: gzip");
#endif
            return ci;
        }

        void http_headers_received(HTTPDelegate &hd)
        {
            if (ts->debug_level >= 2)
            {
                std::ostringstream os;
                os << "----- HEADERS RECEIVED -----\n";
                os << "    " << title() << '\n';
                os << "    ENDPOINT: " << hd.remote_endpoint_str() << '\n';
                os << "    HANDSHAKE_DETAILS: " << hd.ssl_handshake_details() << '\n';
                os << "    CONTENT-LENGTH: " << hd.content_length() << '\n';
                os << "    HEADERS: " << string::indent(HTTP::headers_redact(hd.reply().to_string()), 0, 13) << '\n';
                OPENVPN_LOG_STRING(os.str());
            }

            Transaction &t = trans();

            // save reply
            t.reply = hd.reply();
        }

        BufferPtr http_content_out(HTTPDelegate &hd)
        {
            if (out_iter != trans().content_out.end())
            {
                BufferPtr ret = new BufferAllocated(**out_iter);
                ++out_iter;
                return ret;
            }
            else
                return BufferPtr();
        }

        void http_content_out_needed(HTTPDelegate &hd)
        {
        }

        void http_headers_sent(HTTPDelegate &hd, const Buffer &buf)
        {
            if (ts->debug_level >= 2)
            {
                std::ostringstream os;
                os << "----- HEADERS SENT -----\n";
                os << "    " << title() << '\n';
                os << "    ENDPOINT: " << hd.remote_endpoint_str() << '\n';
                os << "    HEADERS: " << string::indent(HTTP::headers_redact(buf_to_string(buf)), 0, 13) << '\n';
                OPENVPN_LOG_STRING(os.str());
            }
        }

        void http_mutate_resolver_results(HTTPDelegate &hd, openvpn_io::ip::tcp::resolver::results_type &results)
        {
            // filter results by IP version
            if (trans().ip_version_preference != IP::Addr::UNSPEC)
                filter_by_ip_version(results, trans().ip_version_preference);

            // randomize results
            if (parent->prng && trans().randomize_resolver_results)
                randomize_results(results, *parent->prng);
        }

        void http_content_in(HTTPDelegate &hd, BufferAllocated &buf)
        {
            trans().content_in.put_consume(buf, buf_tailroom);
        }

        void http_done(HTTPDelegate &hd, const int status, const std::string &description)
        {
            Transaction &t = trans();
            try
            {
                // save status
                t.status = status;
                t.description = description;

                // status value should reflect HTTP status
                const int http_status = hd.reply().status_code;
                if (t.status == WS::Client::Status::E_SUCCESS && http_status_should_retry(http_status))
                {
                    switch (http_status)
                    {
                    case 400:
                        t.status = WS::Client::Status::E_BAD_REQUEST;
                        break;
                    default:
                        t.status = WS::Client::Status::E_HTTP;
                        break;
                    }
                    t.description = std::to_string(http_status) + ' ' + WS::Client::Status::error_str(t.status);
                }

                // debug output
                if (ts->debug_level >= 2)
                {
                    std::ostringstream os;
                    os << "----- DONE -----\n";
                    os << "    " << title() << '\n';
                    os << "    STATUS: " << WS::Client::Status::error_str(t.status) << '\n';
                    os << "    DESCRIPTION: " << t.description << '\n';
                    OPENVPN_LOG_STRING(os.str());
                }

                if (t.status == WS::Client::Status::E_SUCCESS)
                {
                    // uncompress if server sent gzip-compressed data
                    if (hd.reply().headers.get_value_trim("content-encoding") == "gzip")
                    {
#ifdef HAVE_ZLIB
                        BufferPtr bp = t.content_in.join();
                        t.content_in.clear();
                        bp = ZLib::decompress_gzip(std::move(bp), 0, 0, hd.http_config().max_content_bytes);
                        t.content_in.push_back(std::move(bp));
#else
                        throw Exception("gzip-compressed data returned from server but app not linked with zlib");
#endif
                    }

                    // do next request
                    ++ts_iter;

                    // Post a call to next_request() under a fresh stack.
                    // Currently we may actually be under tcp_read_handler() and
                    // next_request() can trigger destructors.
                    post_next_request();
                }
                else
                {
                    // failed
                    ++n_retries;
                    if (ts->max_retries && n_retries >= ts->max_retries)
                    {
                        // fail -- no more retries
                        done(false, false);
                    }
                    else
                    {
                        // fail -- retry
                        close_http(false, false);

                        // special case -- no delay after TCP EOF on first retry
                        if (t.status == WS::Client::Status::E_EOF_TCP && n_retries == 1)
                            post_next_request();
                        else
                            reconnect_schedule(true);
                    }
                }
            }
            catch (const std::exception &e)
            {
                t.status = WS::Client::Status::E_EXCEPTION;
                t.description = std::string("http_done: ") + e.what();
                if (!halt)
                    done(false, false);
            }
        }

        void post_next_request()
        {
            openvpn_io::post(parent->io_context,
                             [self = Ptr(this)]()
                             { self->next_request(false); });
        }

        void http_keepalive_close(HTTPDelegate &hd, const int status, const std::string &description)
        {
            // this may be a no-op because ts->hsc.alive() is always tested before construction
            // OPENVPN_LOG("http_keepalive_close " << WS::Client::Status::error_str(status) << " description=" << description << " http_status=" << std::to_string(hd.reply().status_code) << " http_text=" << hd.reply().status_text);
        }

        void http_post_connect(HTTPDelegate &hd, AsioPolySock::Base &sock)
        {
            if (ts->post_connect)
                ts->post_connect(*ts, sock);
        }

        bool http_status_should_retry(const int status) const
        {
            return status >= (ts->retry_on_http_4xx ? 400 : 500) && status < 600;
        }

        ClientSet *parent;
        TransactionSet::Ptr ts;
        TransactionSet::Vector::const_iterator ts_iter;
        BufferList content_out;
        BufferList::const_iterator out_iter;
        unsigned int n_retries;
        unsigned int buf_tailroom;
        Time::Duration retry_duration;
        AsioTimerSafe reconnect_timer;
        client_t client_id;
        bool halt;
        bool started;
    };

    void remove_client_id(const client_t client_id)
    {
        auto e = clients.find(client_id);
        if (e != clients.end())
            clients.erase(e);
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

    openvpn_io::io_context &io_context;
    bool halt;
    client_t next_id;
    RandomAPI::Ptr prng;
    std::map<client_t, Client::Ptr> clients;
};

} // namespace WS
} // namespace openvpn
