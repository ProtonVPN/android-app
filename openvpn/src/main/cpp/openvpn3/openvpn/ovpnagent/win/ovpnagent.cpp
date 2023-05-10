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

// OpenVPN agent for Windows

#include <iostream>
#include <string>
#include <vector>
#include <memory>
#include <utility>

#include <openvpn/io/io.hpp>

// debug settings (production setting in parentheses)
#define OPENVPN_LOG_SSL(x) OPENVPN_LOG(x)

#include <openvpn/common/stringize.hpp>
// VERSION version can be passed on build command line
#ifdef VERSION
#define HTTP_SERVER_VERSION OPENVPN_STRINGIZE(VERSION)
#else
#define HTTP_SERVER_VERSION "0.1.0"
#endif
// OVPNAGENT_NAME can be passed on build command line.
// Customized agent name is needed with purpose to install
// few app with agents on one OS (e.g OC 3.0 and PT)
#ifdef OVPNAGENT_NAME
#define OVPNAGENT_NAME_STRING OPENVPN_STRINGIZE(OVPNAGENT_NAME)
#else
#define OVPNAGENT_NAME_STRING "ovpnagent"
#endif

#include <openvpn/log/logbase.hpp>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/size.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/common/path.hpp>
#include <openvpn/common/file.hpp>
#include <openvpn/common/splitlines.hpp>
#include <openvpn/common/wstring.hpp>
#include <openvpn/log/logbasesimple.hpp>
#include <openvpn/buffer/buflist.hpp>
#include <openvpn/buffer/bufhex.hpp>
#include <openvpn/init/initprocess.hpp>
#include <openvpn/ssl/sslchoose.hpp>
#include <openvpn/ws/httpserv.hpp>
#include <openvpn/win/winerr.hpp>
#include <openvpn/client/win/agentconfig.hpp>
#include <openvpn/win/scoped_handle.hpp>
#include <openvpn/win/winsvc.hpp>
#include <openvpn/win/logfile.hpp>
#include <openvpn/tun/win/client/tunsetup.hpp>
#include <openvpn/win/npinfo.hpp>
#include <openvpn/win/handlecomm.hpp>

void log_version()
{
    OPENVPN_LOG("OpenVPN Agent " HTTP_SERVER_VERSION " [" SSL_LIB_NAME "]");
}

using namespace openvpn;

struct MyConfig
{
    MyConfig()
    {
        pipe_name = Agent::named_pipe_path();
        server_exe = Win::module_name_utf8();
#ifdef OPENVPN_AGENT_START_PROCESS
        omiclient_exe = Win::omiclient_path();
#endif
        n_pipe_instances = 4;
    }

    std::string pipe_name;
    std::string server_exe;
    std::string omiclient_exe;
    unsigned int n_pipe_instances;
};

class MySessionStats : public SessionStats
{
  public:
    typedef RCPtr<MySessionStats> Ptr;

    virtual void error(const size_t err_type, const std::string *text = nullptr) override
    {
        OPENVPN_LOG(openvpn::Error::name(err_type));
    }

    std::string dump() const
    {
        std::ostringstream os;
        os << "OpenVPN Agent Stats" << std::endl;
        return os.str();
    }
};

class MyListener : public WS::Server::Listener
{
  public:
    typedef RCPtr<MyListener> Ptr;

    MyListener(const MyConfig &config_arg,
               openvpn_io::io_context &io_context,
               const WS::Server::Config::Ptr &hconf,
               const Listen::List &listen_list,
               const WS::Server::Listener::Client::Factory::Ptr &client_factory)
        : WS::Server::Listener(io_context, hconf, listen_list, client_factory),
          config(config_arg),
          client_process(io_context),
          client_confirm_event(io_context),
          client_destroy_event(io_context),
          io_context_(io_context)
    {
    }

    Win::ScopedHANDLE tun_get_handle(std::ostream &os,
                                     const TunWin::Type tun_type,
                                     bool allow_local_dns_resolvers)
    {
        if (!tun)
            tun.reset(new TunWin::Setup(io_context_, tun_type, allow_local_dns_resolvers));
        return Win::ScopedHANDLE(tun->get_handle(os));
    }

    TunWin::Util::TapNameGuidPair get_adapter_state()
    {
        return tun->get_adapter_state();
    }

    Win::ScopedHANDLE establish_tun(const TunBuilderCapture &tbc,
                                    const std::wstring &openvpn_app_path,
                                    Stop *stop,
                                    std::ostream &os,
                                    TunWin::Type tun_type,
                                    bool allow_local_dns_resolvers,
                                    TunWin::Util::TapNameGuidPair tap)
    {
        if (!tun)
            tun.reset(new TunWin::Setup(io_context_, tun_type, allow_local_dns_resolvers));

        if ((tun_type == TunWin::OvpnDco) && (tap.index != DWORD(-1)))
            tun->set_adapter_state(tap);

        auto th = tun->establish(tbc, openvpn_app_path, stop, os, ring_buffer);
        // store VPN interface index to be able to exclude it
        // when next time adding bypass route
        vpn_interface_index = tun->vpn_interface_index();
        return Win::ScopedHANDLE(th);
    }

    // return true if we did any work
    bool destroy_tun(std::ostream &os)
    {
        bool ret = false;
        try
        {
            // close the remote tap handle in the client process
            if (client_process.is_open() && !remote_tap_handle_hex.empty())
            {
                ret = true;
                const HANDLE remote_tap_handle = BufHex::parse<HANDLE>(remote_tap_handle_hex, "remote TAP handle");
                Win::ScopedHANDLE local_tap_handle; // dummy handle, immediately closed after duplication
                if (::DuplicateHandle(client_process.native_handle(),
                                      remote_tap_handle,
                                      GetCurrentProcess(),
                                      local_tap_handle.ref(),
                                      0,
                                      FALSE,
                                      DUPLICATE_SAME_ACCESS | DUPLICATE_CLOSE_SOURCE))
                {
                    os << "destroy_tun: no client confirm, DuplicateHandle (close) succeeded" << std::endl;
                }
                else
                {
                    const Win::LastError err;
                    os << "destroy_tun: no client confirm, DuplicateHandle (close) failed: " << err.message() << std::endl;
                }
            }
        }
        catch (const std::exception &e)
        {
            os << "destroy_tun: exception in remote tap handle close: " << e.what() << std::endl;
        }

        try
        {
            ring_buffer.reset();

            // undo the effects of establish_tun
            if (tun)
            {
                ret = true;
                tun->destroy(os);
            }
        }
        catch (const std::exception &e)
        {
            os << "destroy_tun: exception in tun teardown: " << e.what() << std::endl;
        }

        try
        {
            tun.reset();
            remote_tap_handle_hex.clear();
            client_process.close();
            client_confirm_event.close();
            client_destroy_event.close();
        }
        catch (const std::exception &e)
        {
            os << "destroy_tun: exception in cleanup: " << e.what() << std::endl;
        }
        vpn_interface_index = DWORD(-1);
        return ret;
    }

    void destroy_tun_exit()
    {
        std::ostringstream os;
        destroy_tun(os);
        OPENVPN_LOG_NTNL("TUN CLOSE (exit)\n"
                         << os.str());
    }

    void set_client_process(Win::ScopedHANDLE &&proc)
    {
        client_process.close();
        client_process.assign(proc.release());

        // special failsafe to destroy tun in case client crashes without closing it
        client_process.async_wait([self = Ptr(this)](const openvpn_io::error_code &error)
                                  {
	if (!error)
	  {
	    {
	      std::ostringstream os;
	      self->remove_cmds_bypass_hosts.execute(os);
	      self->remove_cmds_bypass_hosts.clear();
	      OPENVPN_LOG_NTNL("remove bypass route (failsafe)\n" << os.str());
	    }

	    if (self->tun)
	      {
		std::ostringstream os;
		self->destroy_tun(os);
		OPENVPN_LOG_NTNL("TUN CLOSE (failsafe)\n" << os.str());
	      }
	  } });
    }

    void set_client_confirm_event(const std::string &confirm_handle_hex)
    {
        client_confirm_event.close();

        const HANDLE remote_event = BufHex::parse<HANDLE>(confirm_handle_hex, "confirm event handle");
        HANDLE event_handle;
        if (!::DuplicateHandle(get_client_process(),
                               remote_event,
                               GetCurrentProcess(),
                               &event_handle,
                               0,
                               FALSE,
                               DUPLICATE_SAME_ACCESS | DUPLICATE_CLOSE_SOURCE))
        {
            const Win::LastError err;
            OPENVPN_THROW_EXCEPTION("set_client_confirm_event: DuplicateHandle failed: " << err.message());
        }
        client_confirm_event.assign(event_handle);

        // Check if the event is okay
        {
            const DWORD status = ::WaitForSingleObject(client_confirm_event.native_handle(), 0);
            const Win::LastError err;
            switch (status)
            {
            case WAIT_OBJECT_0: // acceptable status
            case WAIT_TIMEOUT:  // acceptable status
                break;
            case WAIT_ABANDONED:
                throw Exception("set_client_confirm_event: confirm event is abandoned");
            default:
                OPENVPN_THROW_EXCEPTION("set_client_confirm_event: WaitForSingleObject failed: " << err.message());
            }
        }

        // When the client signals the client_confirm event, it means
        // that the client has taken ownership of the TAP device HANDLE,
        // so we locally release ownership by clearing remote_tap_handle_hex,
        // effectively preventing the cross-process release of TAP device
        // HANDLE in destroy_tun() above.
        client_confirm_event.async_wait([self = Ptr(this)](const openvpn_io::error_code &error)
                                        {
	if (!error)
	  {
	    self->remote_tap_handle_hex.clear();
	    OPENVPN_LOG_STRING("TUN CONFIRM\n");
	  } });
    }

    void set_client_destroy_event(const std::string &event_handle_hex)
    {
        client_destroy_event.close();

        // Move the remote event HANDLE (already duplicated in remote process)
        // to local process.
        const HANDLE remote_event = BufHex::parse<HANDLE>(event_handle_hex, "destroy event handle");
        HANDLE event_handle;
        if (!::DuplicateHandle(get_client_process(),
                               remote_event,
                               GetCurrentProcess(),
                               &event_handle,
                               0,
                               FALSE,
                               DUPLICATE_SAME_ACCESS | DUPLICATE_CLOSE_SOURCE))
        {
            const Win::LastError err;
            OPENVPN_THROW_EXCEPTION("set_client_destroy_event: DuplicateHandle failed: " << err.message());
        }
        client_destroy_event.assign(event_handle);

        // Check if the event is already signaled, or has some other error
        {
            const DWORD status = ::WaitForSingleObject(client_destroy_event.native_handle(), 0);
            const Win::LastError err;
            switch (status)
            {
            case WAIT_TIMEOUT: // expected status
                break;
            case WAIT_OBJECT_0:
                throw Exception("set_client_destroy_event: destroy event is already signaled");
            case WAIT_ABANDONED:
                throw Exception("set_client_destroy_event: destroy event is abandoned");
            default:
                OPENVPN_THROW_EXCEPTION("set_client_destroy_event: WaitForSingleObject failed: " << err.message());
            }
        }

        // normal event-based tun close processing
        client_destroy_event.async_wait([self = Ptr(this)](const openvpn_io::error_code &error)
                                        {
	if (!error)
	  {
	    {
	      std::ostringstream os;
	      self->remove_cmds_bypass_hosts.execute(os);
	      self->remove_cmds_bypass_hosts.clear();
	      OPENVPN_LOG_NTNL("remove bypass route (event)\n" << os.str());
	    }

	    if (self->tun)
	      {
		std::ostringstream os;
		self->destroy_tun(os);
		OPENVPN_LOG_NTNL("TUN CLOSE (event)\n" << os.str());
	      }
	  } });
    }

    HANDLE get_client_process()
    {
        if (!client_process.is_open())
            throw Exception("no client process");
        return client_process.native_handle();
    }

    void set_remote_tap_handle_hex(const HANDLE tap_handle)
    {
        remote_tap_handle_hex = Win::HandleComm::send_handle(tap_handle, get_client_process());
    }

    const std::string &get_remote_tap_handle_hex()
    {
        return remote_tap_handle_hex;
    }

    void assign_ring_buffer(TunWin::RingBuffer *ring_buffer_arg)
    {
        ring_buffer.reset(ring_buffer_arg);
    }

    void add_bypass_route(const std::string &host, bool ipv6)
    {
        std::ostringstream os;
        remove_cmds_bypass_hosts.execute(os);
        remove_cmds_bypass_hosts.clear();

        ActionList add_cmds;
        // we might have broken VPN connection up, so we must
        // exclude VPN interface whe searching for the best gateway
        const TunWin::Util::BestGateway gw{host, vpn_interface_index};
        TunWin::Setup::add_bypass_route(gw, host, ipv6, add_cmds, remove_cmds_bypass_hosts);
        add_cmds.execute(os);

        OPENVPN_LOG(os.str());
    }

#ifdef OPENVPN_AGENT_START_PROCESS
    void start_openvpn_process(HANDLE client_pipe,
                               const std::string &config_file,
                               const std::string &config_dir,
                               const std::string &exit_event_name,
                               const std::string &management_host,
                               const std::string &management_password,
                               const int management_port,
                               const std::string &log,
                               const bool log_append)
    {
        // impersonate pipe client
        Win::NamedPipeImpersonate impersonate{client_pipe};

        {
            // create primary token from impersonation token
            HANDLE imp_token = NULL, pri_token = NULL;
            BOOL res = OpenThreadToken(GetCurrentThread(), TOKEN_QUERY | TOKEN_DUPLICATE | TOKEN_ASSIGN_PRIMARY, FALSE, &imp_token);
            if (res == 0)
            {
                const openvpn_io::error_code err(::GetLastError(), openvpn_io::error::get_system_category());
                OPENVPN_THROW_EXCEPTION("failed to open thread token: " << err.message());
            }
            res = DuplicateTokenEx(imp_token, 0, NULL, SECURITY_IMPERSONATION_LEVEL::SecurityAnonymous, TokenPrimary, &pri_token);
            if (res == 0)
            {
                const openvpn_io::error_code err(::GetLastError(), openvpn_io::error::get_system_category());
                OPENVPN_THROW_EXCEPTION("failed to duplicate token: " << err.message());
            }

            // create pipe which is used to write password to openvpn process's stdin
            HANDLE stdin_read = NULL, stdin_write = NULL;
            SECURITY_ATTRIBUTES inheritable = {sizeof(inheritable), NULL, TRUE};
            if (!CreatePipe(&stdin_read, &stdin_write, &inheritable, 0)
                || !SetHandleInformation(stdin_write, HANDLE_FLAG_INHERIT, 0))
            {
                const openvpn_io::error_code err(::GetLastError(), openvpn_io::error::get_system_category());
                OPENVPN_THROW_EXCEPTION("failed to set up pipe: " << err.message());
            }

            // create command line for openvpn process
            std::ostringstream ss;
            ss << "client --config \"" << config_dir << "\\" << config_file << "\" --exit-event-name "
               << exit_event_name << " --auth-retry interact --management " << management_host << " "
               << management_port << " stdin --management-query-passwords --management-hold "
               << "--log"
               << (log_append ? "-append \"" : " \"") << log << "\"";
            std::string cmd = ss.str();
            std::unique_ptr<char[]> buf(new char[cmd.length() + 1]);
            strcpy(buf.get(), cmd.c_str());

            // OPENVPN_LOG("Launching omiclient: " << config.omiclient_exe.c_str() << " " << buf.get());

            STARTUPINFO startup_info = {0};
            startup_info.cb = sizeof(startup_info);
            startup_info.dwFlags = STARTF_USESTDHANDLES;
            startup_info.hStdInput = stdin_read;

            // create openvpn process
            PROCESS_INFORMATION proc_info;
            ZeroMemory(&proc_info, sizeof(proc_info));
            res = CreateProcessAsUser(pri_token,
                                      config.omiclient_exe.c_str(),
                                      buf.get(),
                                      NULL,
                                      NULL,
                                      TRUE,
                                      CREATE_NO_WINDOW | CREATE_UNICODE_ENVIRONMENT,
                                      0,
                                      0,
                                      &startup_info,
                                      &proc_info);
            if (res == 0)
            {
                const openvpn_io::error_code err(::GetLastError(), openvpn_io::error::get_system_category());
                OPENVPN_THROW_EXCEPTION("failed to create openvpn process: " << err.message());
            }
            CloseHandle(proc_info.hProcess);
            CloseHandle(proc_info.hThread);

            // write management password to process's stdin
            DWORD written;
            WriteFile(stdin_write, management_password.c_str(), management_password.length(), &written, NULL);
        }
    }
#endif

    const MyConfig &config;
    ActionList remove_cmds_bypass_hosts;

    TunWin::RingBuffer::Ptr ring_buffer;

  private:
    virtual bool allow_client(AsioPolySock::Base &sock) override
    {
        AsioPolySock::NamedPipe *np = dynamic_cast<AsioPolySock::NamedPipe *>(&sock);
        if (np)
        {
#if _WIN32_WINNT >= 0x0600 // Vista and higher
            Win::NamedPipePeerInfoClient npinfo(np->handle.native_handle());
            const std::string client_exe = wstring::to_utf8(npinfo.exe_path);
            OPENVPN_LOG("connection from " << client_exe);
            if (Agent::valid_pipe(client_exe, config.server_exe))
                return true;
            OPENVPN_LOG(client_exe << " not recognized as a valid client");
#else
            return true;
#endif
        }
        else
            OPENVPN_LOG("only named pipe clients are allowed");
        return false;
    }

    TunWin::Setup::Ptr tun;
    openvpn_io::windows::object_handle client_process;
    openvpn_io::windows::object_handle client_confirm_event;
    openvpn_io::windows::object_handle client_destroy_event;
    std::string remote_tap_handle_hex;
    openvpn_io::io_context &io_context_;

    // with persist tunnel and redirect-gw we must exclude
    // VPN interface when searching for best gateway when
    // adding bypass route for the next remote
    DWORD vpn_interface_index = DWORD(-1);
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
            const HANDLE client_pipe = get_client_pipe();
            const std::wstring client_exe = get_client_exe(client_pipe);

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

                if (req.uri == "/tun-open")
                {
                    // destroy previous instance
                    if (parent()->destroy_tun(os))
                    {
                        os << "Destroyed previous TAP instance" << std::endl;
                        ::Sleep(1000);
                    }

                    {
                        // remember the client process that sent the request
                        ULONG pid = json::get_uint_optional(root, "pid", 0);
                        const std::string confirm_event_hex = json::get_string(root, "confirm_event");
                        const std::string destroy_event_hex = json::get_string(root, "destroy_event");

                        Win::NamedPipeImpersonate impersonate(client_pipe);

                        parent()->set_client_process(get_client_process(client_pipe, pid));
                        parent()->set_client_confirm_event(confirm_event_hex);
                        parent()->set_client_destroy_event(destroy_event_hex);
                    }

                    bool allow_local_dns_resolvers = json::get_bool_optional(root, "allow_local_dns_resolvers");
                    Win::ScopedHANDLE th(parent()->tun_get_handle(os, TunWin::OvpnDco, allow_local_dns_resolvers));
                    {
                        // duplicate the TAP handle into the client process
                        Win::NamedPipeImpersonate impersonate(client_pipe);
                        parent()->set_remote_tap_handle_hex(th());
                    }

                    // build JSON return dictionary
                    const std::string log_txt = string::remove_blanks(os.str());
                    Json::Value jout(Json::objectValue);
                    jout["log_txt"] = log_txt;
                    jout["tap_handle_hex"] = parent()->get_remote_tap_handle_hex();

                    auto tap = parent()->get_adapter_state();
                    jout["adapter_index"] = Json::Int(tap.index);
                    jout["adapter_name"] = tap.name;

                    OPENVPN_LOG_NTNL("TUN SETUP\n"
                                     << log_txt);

                    generate_reply(jout);
                }
                else if (req.uri == "/tun-setup")
                {
                    // get PID
                    ULONG pid = json::get_uint_optional(root, "pid", 0);

                    TunWin::Type tun_type;
                    switch (json::get_int_optional(root, "tun_type", TunWin::TapWindows6))
                    {
                    case TunWin::Wintun:
                        tun_type = TunWin::Wintun;
                        break;
                    case TunWin::OvpnDco:
                        tun_type = TunWin::OvpnDco;
                        break;
                    case TunWin::TapWindows6:
                    default:
                        tun_type = TunWin::TapWindows6;
                        break;
                    }

                    bool allow_local_dns_resolvers = json::get_bool_optional(root, "allow_local_dns_resolvers");
                    // parse JSON data into a TunBuilderCapture object
                    TunBuilderCapture::Ptr tbc = TunBuilderCapture::from_json(json::get_dict(root, "tun", false));
                    tbc->validate();

                    // destroy previous instance
                    if (tun_type != TunWin::OvpnDco && parent()->destroy_tun(os))
                    {
                        os << "Destroyed previous TAP instance" << std::endl;
                        ::Sleep(1000);
                    }

                    // pre-establish impersonation
                    {
                        Win::NamedPipeImpersonate impersonate(client_pipe);

                        // remember the client process that sent the request
                        parent()->set_client_process(get_client_process(client_pipe, pid));

                        // save destroy event
                        const std::string destroy_event_hex = json::get_string(root, "destroy_event");
                        parent()->set_client_destroy_event(destroy_event_hex);

                        if (tun_type != TunWin::OvpnDco)
                        {
                            // get remote event handle for tun object confirmation
                            const std::string confirm_event_hex = json::get_string(root, "confirm_event");

                            // save the confirm event
                            parent()->set_client_confirm_event(confirm_event_hex);
                        }
                    }

                    if (tun_type == TunWin::Wintun)
                    {
                        parent()->assign_ring_buffer(new TunWin::RingBuffer(io_context,
                                                                            parent()->get_client_process(),
                                                                            json::get_string(root, "send_ring_hmem"),
                                                                            json::get_string(root, "receive_ring_hmem"),
                                                                            json::get_string(root, "send_ring_tail_moved"),
                                                                            json::get_string(root, "receive_ring_tail_moved")));
                    }

                    TunWin::Util::TapNameGuidPair tap;
                    if (tun_type == TunWin::OvpnDco)
                    {
                        tap.index = (DWORD)json::get_int(root, "adapter_index");
                        tap.name = json::get_string(root, "adapter_name");
                    }

                    // establish the tun setup object
                    Win::ScopedHANDLE tap_handle(parent()->establish_tun(*tbc, client_exe, nullptr, os, tun_type, allow_local_dns_resolvers, tap));

                    // post-establish impersonation
                    {
                        Win::NamedPipeImpersonate impersonate(client_pipe);

                        // duplicate the TAP handle into the client process
                        parent()->set_remote_tap_handle_hex(tap_handle());
                    }

                    // build JSON return dictionary
                    const std::string log_txt = string::remove_blanks(os.str());
                    Json::Value jout(Json::objectValue);
                    jout["log_txt"] = log_txt;
                    jout["tap_handle_hex"] = parent()->get_remote_tap_handle_hex();
                    OPENVPN_LOG_NTNL("TUN SETUP\n"
                                     << log_txt);

                    generate_reply(jout);
                }
                else if (req.uri == "/add-bypass-route")
                {
                    ULONG pid = json::get_uint_optional(root, "pid", 0);
                    bool ipv6 = json::get_bool(root, "ipv6");
                    const std::string host = json::get_string(root, "host");

                    // pre-establish impersonation
                    {
                        Win::NamedPipeImpersonate impersonate(client_pipe);

                        // remember the client process that sent the request
                        parent()->set_client_process(get_client_process(client_pipe, pid));
                    }

                    parent()->add_bypass_route(host, ipv6);

                    Json::Value jout(Json::objectValue);

                    generate_reply(jout);
                }
#ifdef OPENVPN_AGENT_START_PROCESS
                else if (req.uri == "/start")
                {
                    const std::string config_file = json::get_string(root, "config_file");
                    const std::string config_dir = json::get_string(root, "config_dir");
                    const std::string exit_event_name = json::get_string(root, "exit_event_name");
                    const std::string management_host = json::get_string(root, "management_host");
                    const std::string management_password = json::get_string(root, "management_password") + "\n";
                    const int management_port = json::get_int(root, "management_port");
                    const std::string log = json::get_string(root, "log");
                    const bool log_append = json::get_int(root, "log-append") == 1;

                    parent()->start_openvpn_process(client_pipe,
                                                    config_file,
                                                    config_dir,
                                                    exit_event_name,
                                                    management_host,
                                                    management_password,
                                                    management_port,
                                                    log,
                                                    log_append);

                    Json::Value jout(Json::objectValue);
                    generate_reply(jout);
                }
#endif
                else
                {
                    OPENVPN_LOG("PAGE NOT FOUND");
                    out = buf_from_string("page not found\n");
                    WS::Server::ContentInfo ci;
                    ci.http_status = HTTP::Status::NotFound;
                    ci.type = "text/plain";
                    ci.length = out->size();
                    generate_reply_headers(ci);
                }
            }
        }
        catch (const std::exception &e)
        {
            if (parent()->destroy_tun(os))
                os << "Destroyed previous TAP instance due to exception" << std::endl;

            const std::string error_msg = string::remove_blanks(os.str() + e.what() + '\n');
            OPENVPN_LOG_NTNL("EXCEPTION\n"
                             << error_msg);

            out = buf_from_string(error_msg);
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

    virtual bool http_out_eof() override
    {
        // OPENVPN_LOG("HTTP output EOF");
        return true;
    }

    virtual bool http_stop(const int status, const std::string &description) override
    {
        if (status != WS::Server::Status::E_SUCCESS)
        {
            OPENVPN_LOG("INSTANCE STOP : " << WS::Server::Status::error_str(status) << " : " << description);
            return false;
        }
        else
            return true;
    }

    HANDLE get_client_pipe() const
    {
        AsioPolySock::NamedPipe *np = dynamic_cast<AsioPolySock::NamedPipe *>(sock.get());
        if (!np)
            throw Exception("only named pipe clients are allowed");
        return np->handle.native_handle();
    }

    std::wstring get_client_exe(const HANDLE client_pipe)
    {
#if _WIN32_WINNT >= 0x0600 // Vista and higher
        Win::NamedPipePeerInfoClient npinfo(client_pipe);
        return npinfo.exe_path;
#else
        return std::wstring();
#endif
    }

    Win::ScopedHANDLE get_client_process(const HANDLE pipe, ULONG pid_hint) const
    {
#if _WIN32_WINNT >= 0x0600 // Vista and higher
        pid_hint = Win::NamedPipePeerInfo::get_pid(pipe, true);
#endif
        if (!pid_hint)
            throw Exception("cannot determine client PID");
        return Win::NamedPipePeerInfo::get_process(pid_hint, false);
    }

    MyListener *parent()
    {
        return static_cast<MyListener *>(get_parent());
    }

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

class MyService : public Win::Service
{
  public:
    MyService()
        : Win::Service(config())
    {
    }

    virtual void service_work(DWORD argc, LPWSTR *argv) override
    {
        if (is_service())
        {
            try
            {
                log.reset(new Win::LogFile(log_fn(), "", false));
            }
            catch (const std::exception &e)
            {
                std::cerr << e.what() << std::endl;
            }
        }
        if (!log)
            log.reset(new LogBaseSimple());

        io_context.reset(new openvpn_io::io_context(1)); // concurrency hint=1

        log_version();

        MyConfig conf;

#if _WIN32_WINNT >= 0x0600 // Vista and higher
        Win::NamedPipePeerInfo::allow_client_query();
        TunWin::NRPT::delete_rule(); // remove stale NRPT rules
#endif

        WS::Server::Config::Ptr hconf = new WS::Server::Config();
        hconf->http_server_id = OVPNAGENT_NAME_STRING "/" HTTP_SERVER_VERSION;
        hconf->frame = frame_init_simple(2048);
        hconf->stats.reset(new MySessionStats);

        // DACL string for creating named pipe
        hconf->sddl_string = "D:"                           // discretionary ACL
                             "(D;OICI;GA;;;S-1-5-2)"        // deny all access for network users
                             "(A;OICI;GA;;;S-1-5-32-544)"   // allow full access to Admin group
                             "(A;OICI;GA;;;S-1-5-18)"       // allow full access to Local System account
                             "(D;OICI;0x4;;;S-1-1-0)"       // deny FILE_CREATE_PIPE_INSTANCE for Everyone
                             "(A;OICI;GRGW;;;S-1-5-11)"     // allow read/write access for authenticated users
                             "(A;OICI;GRGW;;;S-1-5-32-546)" // allow read/write access for built-in guest account
            ;

        Listen::List ll;
        const unsigned int n_pipe_instances = 4;
        for (unsigned int i = 0; i < n_pipe_instances; ++i)
        {
            Listen::Item li;
            li.directive = "http-listen";
            li.addr = conf.pipe_name;
            li.proto = Protocol(Protocol::NamedPipe);
            li.ssl = Listen::Item::SSLOff;
            li.n_threads = n_pipe_instances;
            ll.push_back(std::move(li));
        }

        MyClientFactory::Ptr factory = new MyClientFactory();
        listener.reset(new MyListener(conf, *io_context, hconf, ll, factory));
        listener->start();

        report_service_running();

        io_context->run();
    }

    // Called by service control manager in another thread
    // to signal the service_work() method to exit.
    virtual void service_stop() override
    {
        openvpn_io::post(*io_context, [this]()
                         {
	if (listener)
	  {
	    listener->destroy_tun_exit();
	    listener->stop();
	  } });
    }

  private:
    static Config config()
    {
        Config c;
        c.name = OVPNAGENT_NAME_STRING;
        c.display_name = "OpenVPN Agent " OVPNAGENT_NAME_STRING;
#if _WIN32_WINNT < 0x0600                 // pre-Vista
        c.dependencies.push_back("Dhcp"); // DHCP client
#endif
        c.autostart = true;
        c.restart_on_fail = true;
        return c;
    }

    static std::string log_fn()
    {
        const std::string modname = Win::module_name_utf8();
        const std::string moddir = path::dirname(modname);
        const std::string fn = path::join(moddir, "agent.log");
        return fn;
    }

    std::unique_ptr<openvpn_io::io_context> io_context;
    MyListener::Ptr listener;
    LogBase::Ptr log;
};

OPENVPN_SIMPLE_EXCEPTION(usage);

int main(int argc, char *argv[])
{
    int ret = 0;

    // process-wide initialization
    InitProcess::Init init;

    try
    {
        MyService serv;
        if (argc >= 2)
        {
            const std::string arg = argv[1];
            if (arg == "run")
                serv.service_work(0, nullptr);
            else if (arg == "install")
                serv.install();
            else if (arg == "remove")
                serv.remove();
            else if (arg == "modname")
                std::wcout << Win::module_name() << std::endl;
            else if (arg == "help")
            {
                std::cout << "usage: ovpnagent [options]" << std::endl;
                std::cout << "  run       -- run in foreground (for debugging)" << std::endl;
                std::cout << "  install   -- install as service" << std::endl;
                std::cout << "  remove    -- uninstall" << std::endl;
                std::cout << "  modname   -- show module name" << std::endl;
                std::cout << "  help      -- show help message" << std::endl;
                std::cout << "  [default] -- start as service" << std::endl;
            }
            else
            {
                std::cout << "unrecognized option, use 'help' for more info" << std::endl;
                ret = 2;
            }
        }
        else
            serv.start();
    }
    catch (const std::exception &e)
    {
        std::cout << "ovpnagent: " << e.what() << std::endl;
        ret = 1;
    }

    return ret;
}
