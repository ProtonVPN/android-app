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

// OpenVPN agent for Mac

// #define OPENVPN_EXIT_IN 30

// #define OPENVPN_SSL_DEBUG 9   // MbedTLS debugging max level

#include <iostream>
#include <string>
#include <utility>

#include <unistd.h>

// debug settings (production setting in parentheses)
#define OPENVPN_LOG_SSL(x) OPENVPN_LOG(x)

// VERSION version can be passed on build command line
#include <openvpn/common/stringize.hpp>
#ifdef VERSION
#define HTTP_SERVER_VERSION OPENVPN_STRINGIZE(VERSION)
#else
#define HTTP_SERVER_VERSION "0.1.1"
#endif

#ifdef OVPNAGENT_NAME
#define OVPNAGENT_NAME_STRING OPENVPN_STRINGIZE(OVPNAGENT_NAME)
#else
#define OVPNAGENT_NAME_STRING "ovpnagent"
#endif

#include <openvpn/log/logbase.hpp>

#include <openvpn/common/platform.hpp>
#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/common/runcontext.hpp>
#include <openvpn/common/file.hpp>
#include <openvpn/common/getopt.hpp>
#include <openvpn/common/scoped_fd.hpp>
#include <openvpn/common/daemon.hpp>
#include <openvpn/common/waitbarrier.hpp>
#include <openvpn/common/usergroup.hpp>
#include <openvpn/common/xmitfd.hpp>
#include <openvpn/buffer/bufstr.hpp>
#include <openvpn/buffer/buflist.hpp>
#include <openvpn/init/initprocess.hpp>
#include <openvpn/ssl/sslchoose.hpp>
#include <openvpn/ws/httpserv.hpp>
#include <openvpn/tun/mac/client/tunsetup.hpp>

void log_version()
{
    std::cout << "OpenVPN Agent (Mac) " HTTP_SERVER_VERSION " [" SSL_LIB_NAME "]"
#ifdef OPENVPN_DEBUG
              << " built on " __DATE__ " " __TIME__
#endif
              << std::endl;
}

using namespace openvpn;

class MySessionStats : public SessionStats
{
  public:
    typedef RCPtr<MySessionStats> Ptr;

    virtual void error(const size_t err_type, const std::string *text = nullptr) override
    {
        OPENVPN_LOG(Error::name(err_type));
    }

    std::string dump() const
    {
        std::ostringstream os;
        os << "OpenVPN Agent Stats" << std::endl;
        return os.str();
    }
};

struct ThreadCommon
{
    ThreadCommon(const char *unix_sock, const char *user, const char *group)
        : listen_list(build_listen_list(unix_sock)),
          user_group(user, group, true),
          stats(new MySessionStats),
          event_loop_bar(1)
    {
    }

    static Listen::List build_listen_list(const char *unix_sock)
    {
        Listen::List ll;
        if (unix_sock)
        {
            Listen::Item li;
            li.directive = "http-listen";
            li.addr = unix_sock;
            li.proto = Protocol(Protocol::UnixStream);
            li.n_threads = 1;
            ll.push_back(std::move(li));
        }
        return ll;
    }

    void show_unused_options() const
    {
    }

    const Listen::List listen_list;
    const SetUserGroup user_group;
    MySessionStats::Ptr stats;
    PThreadBarrier event_loop_bar;
};

class MyListener : public WS::Server::Listener
{
    // handles ungraceful exit of client and closes tun
    class WatchdogThread : public RC<thread_safe_refcount>
    {
      private:
        typedef RCPtr<WatchdogThread> Ptr;

        friend class MyListener;
        MyListener *parent;

      public:
        WatchdogThread(MyListener *parent_arg, openvpn_io::io_context &io_context_arg)
            : parent(parent_arg), io_context(io_context_arg)
        {
        }

        ~WatchdogThread()
        {
            if (th.joinable())
            {
                OPENVPN_LOG("Reaping watchdog thread");
                th.join();
            }
        }

        // starts a thread which blocks until:
        // - process with given pid exits
        // - there is a data in pipe
        void watch(pid_t pid)
        {
            if (client_pid != -1)
            {
                OPENVPN_LOG("Watchdog already set for pid " << client_pid << ", won't set for pid " << pid);
                return;
            }

            OPENVPN_LOG("Setting up watchdog for pid " << pid << " exit notification");

            // self-pipe trick to be able to interrupt wait when agent exits
            if (pipe(fds) == -1)
            {
                OPENVPN_LOG("pipe() failed: " << strerror(errno));
                return;
            }
            // make write nonblocking
            fcntl(fds[1], F_SETFL, O_NONBLOCK);

            kq = kqueue();
            if (kq == -1)
            {
                OPENVPN_LOG("kqueue() failed: " << strerror(errno));
                close_pipe_fds();
                return;
            }

            // add pid exit and self-pipe read_fd to kevent changelist
            struct kevent chlist[2];
            EV_SET(&chlist[0], pid, EVFILT_PROC, EV_ADD | EV_RECEIPT, NOTE_EXIT, 0, NULL);
            EV_SET(&chlist[1], fds[0], EVFILT_READ, EV_ADD, 0, 0, NULL);
            if (kevent(kq, chlist, 2, NULL, 0, NULL) == -1)
            {
                OPENVPN_LOG("kevent() failed: " << strerror(errno));
                close_pipe_fds();
                return;
            }

            // reap thread if previous client didn't exit gracefullt
            if (th.joinable())
            {
                OPENVPN_LOG("Reaping watchdog thread");
                th.join();
            }

            client_pid = pid;

            th = std::thread([self = Ptr(this), pid]()
                             {
	struct kevent evlist[2];
	int nev = kevent(self->kq, 0, 0, evlist, 2, NULL);
	if (nev == -1)
	  {
	    OPENVPN_LOG("kevent() failed: " << strerror(errno));
	    self->close_pipe_fds();
	    return;
	  }

	for (int i = 0; i < nev; ++ i)
	  {
	    if (evlist[i].filter == EVFILT_PROC)
	      {
		openvpn_io::post(self->io_context, [self, pid]() {
		  OPENVPN_LOG("Process " << pid << " has exited, destroy tun");
		  std::ostringstream os;
		  self->parent->destroy_tun(os);
		});
	      }
	  }

	self->client_pid = -1;
	self->close_pipe_fds(); });
        }

        void unwatch()
        {
            OPENVPN_LOG("Stopping watchdog thread");
            write(fds[1], "x", 1);
            if (th.joinable())
            {
                OPENVPN_LOG("Reaping watchdog thread");
                th.join();
            }
        }

      private:
        void close_pipe_fds()
        {
            close(fds[0]);
            close(fds[1]);
        }

        pid_t client_pid = -1;
        openvpn_io::io_context &io_context;
        int kq;     // kqueue
        int fds[2]; // file descriptors for self-pipe trick
        std::thread th;
    };

  public:
    typedef RCPtr<MyListener> Ptr;

    MyListener(openvpn_io::io_context &io_context,
               const WS::Server::Config::Ptr &config,
               const Listen::List &listen_list,
               const WS::Server::Listener::Client::Factory::Ptr &client_factory)
        : WS::Server::Listener(io_context, config, listen_list, client_factory)
    {
        watchdog.reset(new WatchdogThread(this, io_context));
    }

    ScopedFD establish_tun(const TunBuilderCapture &tbc,
                           TunBuilderSetup::Config *config,
                           Stop *stop,
                           std::ostream &os)
    {
        if (!tun)
            tun.reset(new TunMac::Setup);
        return ScopedFD(tun->establish(tbc, config, stop, os));
    }

    void destroy_tun(std::ostream &os)
    {
        if (tun)
        {
            tun->destroy(os);
            tun.reset();
        }

        remove_cmds_bypass_hosts.execute(os);
        remove_cmds_bypass_hosts.clear();
    }

    void set_watchdog(pid_t pid)
    {
        watchdog->watch(pid);
    }

    void unset_watchdog()
    {
        watchdog->unwatch();
    }

    void add_bypass_route(const std::string &host, bool ipv6)
    {
        if (host != bypass_host)
        {
            bypass_host = host;

            std::ostringstream os;

            remove_cmds_bypass_hosts.execute(os);
            remove_cmds_bypass_hosts.clear();

            ActionList add_cmds;
            TunMac::Setup::add_bypass_route(host, ipv6, add_cmds, remove_cmds_bypass_hosts);
            add_cmds.execute(os);

            OPENVPN_LOG(os.str());
        }
    }

  private:
    virtual bool allow_client(AsioPolySock::Base &sock) override
    {
        return true;
    }

    std::string bypass_host;
    ActionList remove_cmds_bypass_hosts;

    TunMac::Setup::Ptr tun;
    WatchdogThread::Ptr watchdog;
};

class MyClientInstance : public WS::Server::Listener::Client
{
  public:
    typedef RCPtr<MyClientInstance> Ptr;

    MyClientInstance(WS::Server::Listener::Client::Initializer &ci)
        : WS::Server::Listener::Client(ci)
    {
        // OPENVPN_LOG("INSTANCE START");
    }

    virtual ~MyClientInstance()
    {
        // OPENVPN_LOG("INSTANCE DESTRUCT");
    }

  private:
    void generate_reply(const Json::Value &jout)
    {
        out = buf_from_string(jout.toStyledString());

        WS::Server::ContentInfo ci;
        ci.http_status = HTTP::Status::OK;
        ci.type = "application/json";
        ci.length = out->size();
        ci.keepalive = keepalive_request();
        generate_reply_headers(ci);
    }

    virtual void http_request_received() override
    {
        // alloc output buffer
        std::ostringstream os;

        try
        {
            const HTTP::Request &req = request();
            OPENVPN_LOG("HTTP request received from " << sock->remote_endpoint_str() << '\n'
                                                      << req.to_string());

            // get content-type
            const std::string content_type = req.headers.get_value_trim("content-type");

            if (req.method == "POST")
            {
                // verify correct content-type
                if (string::strcasecmp(content_type, "application/json"))
                    throw Exception("bad content-type");

                // parse the json dict
                const Json::Value root = json::parse(in.to_string(), "JSON request");
                if (!root.isObject())
                    throw Exception("json parse error: top level json object is not a dictionary");

                if (req.uri == "/tun-setup")
                {
                    send_fd.reset();

                    // get PID
                    pid_t pid = json::get_int_optional(root, "pid", -1);
                    if (pid != -1)
                        parent()->set_watchdog(pid);

                    // parse JSON data into a TunBuilderCapture object
                    TunBuilderCapture::Ptr tbc = TunBuilderCapture::from_json(json::get_dict(root, "tun", false));
                    tbc->validate();

                    // get config
                    TunMac::Setup::Config config;
                    config.from_json(json::get_dict(root, "config", false), "config");

                    // establish the tun setup object
                    send_fd = parent()->establish_tun(*tbc, &config, nullptr, os);

                    // build JSON return dictionary
                    Json::Value jout(Json::objectValue);
                    jout["log_txt"] = Json::Value(string::remove_blanks(os.str()));
                    jout["config"] = config.to_json();
                    generate_reply(jout);
                }
                else if (req.uri == "/add-bypass-route")
                {
                    pid_t pid = json::get_int_optional(root, "pid", -1);
                    if (pid != -1)
                        parent()->set_watchdog(pid);
                    bool ipv6 = json::get_bool(root, "ipv6");
                    std::string host = json::get_string(root, "host");

                    parent()->add_bypass_route(host, ipv6);

                    Json::Value jout(Json::objectValue);
                    generate_reply(jout);
                }
            }
            else if (req.method == "GET" && req.uri == "/tun-destroy")
            {
                // destroy tun object
                parent()->destroy_tun(os);

                // build JSON return dictionary
                Json::Value jout(Json::objectValue);
                jout["log_txt"] = Json::Value(string::remove_blanks(os.str()));
                generate_reply(jout);
            }
            else
            {
                out = buf_from_string("page not found\n");
                WS::Server::ContentInfo ci;
                ci.http_status = HTTP::Status::NotFound;
                ci.type = "text/plain";
                ci.length = out->size();
                generate_reply_headers(ci);
            }
        }
        catch (const std::exception &e)
        {
            out = buf_from_string(string::remove_blanks(os.str() + e.what() + '\n'));
            WS::Server::ContentInfo ci;
            ci.http_status = HTTP::Status::BadRequest;
            ci.type = "text/plain";
            ci.length = out->size();
            generate_reply_headers(ci);
        }
    }

    virtual void http_content_in(BufferAllocated &buf) override
    {
        if (buf.defined())
            in.emplace_back(new BufferAllocated(std::move(buf)));
    }

    virtual BufferPtr http_content_out() override
    {
        BufferPtr ret;
        ret.swap(out);
        return ret;
    }

    // Normally true is returned, however return false if we
    // are planning to send the tun file descriptor to the client.
    virtual bool http_out_eof() override
    {
        // OPENVPN_LOG("HTTP output EOF send_fd=" << send_fd());
        return !send_fd.defined();
    }

    // After HTTP reply has been transmitted, wait for client to
    // send a 't' message.  On receipt, reply with a 'T' message
    // that bundles the tun file descriptor.
    virtual void http_pipeline_peek(BufferAllocated &buf) override
    {
        // OPENVPN_LOG("HTTP PIPELINE PEEK send_fd=" << send_fd() << " CONTENT=" << buf_to_string(buf));
        if (send_fd.defined())
        {
            if (buf.size() == 1 && buf.front() == 't')
            {
                const int fd = unix_fd();
                if (fd < 0)
                    OPENVPN_THROW_EXCEPTION("http_pipeline_peek: not a unix socket");
                XmitFD::xmit_fd(fd, send_fd(), "T", 5000);
                external_stop("FD transmitted");
            }
            else
                OPENVPN_THROW_EXCEPTION("bad FD request message");
        }
    }

    virtual bool http_stop(const int status, const std::string &description) override
    {
        OPENVPN_LOG("INSTANCE STOP : " << WS::Server::Status::error_str(status) << " : " << description);

        // if the shutdown happened due to an unexpected error, the TUN status has
        // to be cleaned up to avoid configuration inconsistency.
        //
        // A problem we have witnessed was that the DNS settings were not being
        // reverted when the HTTP connection with the core was interrupted in the
        // middle of the establish() call.
        if (status != WS::Server::Status::E_SUCCESS
            && status != WS::Server::Status::E_EXTERNAL_STOP)
        {
            std::ostringstream os;
            parent()->destroy_tun(os);
        }

        // returning true here triggers socket shutdown, which triggers "Socket is not connected" error
        return false;
    }

    MyListener *parent()
    {
        return static_cast<MyListener *>(get_parent());
    }

    ScopedFD send_fd;
    BufferList in;
    BufferPtr out;
};

class MyClientFactory : public WS::Server::Listener::Client::Factory
{
  public:
    typedef RCPtr<MyClientFactory> Ptr;

    virtual WS::Server::Listener::Client::Ptr new_client(WS::Server::Listener::Client::Initializer &ci) override
    {
        return new MyClientInstance(ci);
    }
};

class ServerThread : public ServerThreadBase
{
  public:
    typedef RCPtr<ServerThread> Ptr;

    ServerThread(openvpn_io::io_context &io_context_arg,
                 ThreadCommon &tc)
        : io_context(io_context_arg),
          halt(false)
    {
        Frame::Ptr frame = frame_init_simple(2048);

        WS::Server::Config::Ptr config = new WS::Server::Config();
        config->http_server_id = OVPNAGENT_NAME_STRING "/" HTTP_SERVER_VERSION;
        config->frame = frame;
        config->stats = tc.stats;
        config->unix_mode = 0777;

        MyClientFactory::Ptr factory = new MyClientFactory();
        listener.reset(new MyListener(io_context_arg, config, tc.listen_list, factory));
    }

    void start()
    {
        if (!halt)
        {
            listener->start();
        }
    }

    void stop()
    {
        if (!halt)
        {
            halt = true;
            listener->stop();
            listener->unset_watchdog();
        }
    }

    virtual void thread_safe_stop() override
    {
        if (!halt)
        {
            openvpn_io::post(io_context, [self = Ptr(this)]()
                             { self->stop(); });
        }
    }

  private:
    openvpn_io::io_context &io_context;
    MyListener::Ptr listener;
    bool halt;
};

typedef RunContext<ServerThreadBase, MySessionStats> MyRunContext;

void work(openvpn_io::io_context &io_context,
          ThreadCommon &tc,
          MyRunContext &runctx,
          const unsigned int unit)
{
    ServerThread::Ptr serv;

    try
    {
        serv.reset(new ServerThread(io_context, tc));
        runctx.set_server(unit, serv.get());

        serv->start();

        // barrier prior to event-loop entry
        event_loop_wait_barrier(tc);

        // privilege has now been downgraded

        // run i/o reactor
        io_context.run();
        runctx.clear_server(unit);
        serv->stop();
    }
    catch (...)
    {
        tc.event_loop_bar.error();
        if (serv)
        {
            runctx.clear_server(unit);
            serv->stop(); // on exception, stop server
        }
        io_context.poll(); // execute completion handlers
        throw;
    }
}

void worker_thread(ThreadCommon &tc,
                   MyRunContext &runctx,
                   const unsigned int unit)
{
    SignalBlockerDefault signal_blocker;
    openvpn_io::io_context io_context(1); // concurrency hint=1
    Log::Context log_context(runctx.log_wrapper());
    MyRunContext::ThreadContext thread_ctx(runctx);

    try
    {
        work(io_context, tc, runctx, unit);
    }
    catch (const std::exception &e)
    {
        OPENVPN_LOG("Worker thread exception: " << e.what());
    }
}

int ovpnagent(const char *sock_fn,
              const char *log_fn,
              const bool log_append,
              const char *pid_fn,
              const char *user,
              const char *group)
{
    if (log_fn)
        daemonize(log_fn, nullptr, log_append, 0);

    if (pid_fn)
        write_pid(pid_fn);

    log_version();

    MyRunContext::Ptr runctx(new MyRunContext());
    ThreadCommon tc(sock_fn, user, group);

    // Give runctx visibility into global stats
    // for SIGUSR2 dump.
    runctx->set_stats_obj(tc.stats);

    // Main worker thread
    {
        const unsigned int thread_num = 0;
        std::thread *thread = new std::thread([&tc, &runctx]()
                                              { worker_thread(tc, *runctx, thread_num); });
        runctx->set_thread(thread_num, thread);
    }

    // wait for worker to exit
    runctx->run();
    runctx->join();

    // dump final stats
    OPENVPN_LOG_NTNL(tc.stats->dump());

    // remove pidfile
    if (pid_fn)
        ::unlink(pid_fn);

    return 0;
}

OPENVPN_SIMPLE_EXCEPTION(usage);

int main(int argc, char *argv[])
{
    static const struct option longopts[] = {
        {"help", no_argument, nullptr, 'h'},
        {"append", no_argument, nullptr, 'a'},
        {"daemon", required_argument, nullptr, 'd'},
        {"pidfile", required_argument, nullptr, 'p'},
        {"user", required_argument, nullptr, 'u'},
        {"group", required_argument, nullptr, 'g'},
        {nullptr, 0, nullptr, 0}};

    int ret = 0;

    bool append = false;
    const char *logfile = nullptr;
    const char *pidfile = nullptr;
    const char *user = nullptr;
    const char *group = nullptr;

    // process-wide initialization
    InitProcess::Init init;

    // set global MbedTLS debug level
#if defined(USE_MBEDTLS) && defined(OPENVPN_SSL_DEBUG)
    debug_set_threshold(OPENVPN_SSL_DEBUG);
#endif

    try
    {
        if (argc < 1)
            throw usage();

        int ch;
        while ((ch = getopt_long(argc, argv, "had:p:u:g:", longopts, nullptr)) != -1)
        {
            switch (ch)
            {
            case 'a':
                append = true;
                break;
            case 'd':
                logfile = optarg;
                break;
            case 'p':
                pidfile = optarg;
                break;
            case 'u':
                user = optarg;
                break;
            case 'g':
                group = optarg;
                break;
            default:
                throw usage();
            }
        }
        argc -= optind;
        argv += optind;

        ret = ovpnagent("/var/run/" OVPNAGENT_NAME_STRING ".sock", logfile, append, pidfile, user, group);
    }
    catch (const usage &)
    {
        log_version();
        std::cout << "usage: ovpnagent [options]" << std::endl;
        std::cout << "  --daemon <file>, -d       : daemonize, log to file" << std::endl;
        std::cout << "  --append, -a              : append to log file" << std::endl;
        std::cout << "  --pidfile <file>, -p      : write pid to file" << std::endl;
        std::cout << "  --user <user>, -u         : set UID to user" << std::endl;
        std::cout << "  --group <group>, -g       : set group" << std::endl;
        ret = 2;
    }
    catch (const std::exception &e)
    {
        std::cout << "Main thread exception: " << e.what() << std::endl;
        ret = 1;
    }

    return ret;
}
