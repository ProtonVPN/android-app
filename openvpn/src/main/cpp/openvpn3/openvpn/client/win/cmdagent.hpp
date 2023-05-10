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

#include <utility>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/options.hpp>
#include <openvpn/common/wstring.hpp>
#include <openvpn/common/jsonhelper.hpp>
#include <openvpn/buffer/bufstr.hpp>
#include <openvpn/buffer/bufhex.hpp>
#include <openvpn/frame/frame_init.hpp>
#include <openvpn/ws/httpcliset.hpp>
#include <openvpn/win/winerr.hpp>
#include <openvpn/client/win/agentconfig.hpp>
#include <openvpn/win/modname.hpp>
#include <openvpn/tun/win/client/setupbase.hpp>
#include <openvpn/win/npinfo.hpp>
#include <openvpn/win/handlecomm.hpp>
#include <openvpn/win/event.hpp>
#include <openvpn/error/error.hpp>

namespace openvpn {

class WinCommandAgent : public TunWin::SetupFactory
{
  public:
    typedef RCPtr<WinCommandAgent> Ptr;

    OPENVPN_EXCEPTION(ovpnagent);

    static TunWin::SetupFactory::Ptr new_agent(const OptionList &opt)
    {
        return new WinCommandAgent(opt);
    }

    static bool add_bypass_route(IP::Addr endpoint)
    {
        std::ostringstream os;
        os << "WinCommandAgent: transmitting bypass route to " << endpoint.to_string() << std::endl;

        // Build JSON request
        Json::Value jreq(Json::objectValue);
#if _WIN32_WINNT < 0x0600 // pre-Vista needs us to explicitly communicate our PID
        jreq["pid"] = Json::Value((Json::UInt)::GetProcessId(::GetCurrentProcess()));
#endif

        jreq["host"] = endpoint.to_string();
        jreq["ipv6"] = endpoint.is_ipv6();
        const std::string jtxt = jreq.toStyledString();
        os << jtxt; // dump it

        OPENVPN_LOG(os.str());

        // Create HTTP transaction container
        WS::ClientSet::TransactionSet::Ptr ts = SetupClient::new_transaction_set(Agent::named_pipe_path(), 1, Win::module_name_utf8(), [](HANDLE) {});

        SetupClient::make_transaction("add-bypass-route", jtxt, ts);

        // Execute transaction
        WS::ClientSet::new_request_synchronous(ts);

        return ts->http_status_success();
    }

  private:
    struct Config : public RC<thread_unsafe_refcount>
    {
        typedef RCPtr<Config> Ptr;

        std::string npserv = Agent::named_pipe_path();    // server pipe
        std::string client_exe = Win::module_name_utf8(); // for validation
        int debug_level = 1;
        TunWin::Type tun_type = TunWin::TapWindows6;
        bool allow_local_dns_resolvers = false;
    };

    class SetupClient : public TunWin::SetupBase
    {
      public:
        SetupClient(openvpn_io::io_context &io_context,
                    const Config::Ptr &config_arg)
            : config(config_arg),
              service_process(io_context)
        {
        }

        template <class T>
        static WS::ClientSet::TransactionSet::Ptr new_transaction_set(const std::string &host,
                                                                      int debug_level,
                                                                      const std::string &client_exe,
                                                                      T cb)
        {
            WS::Client::Config::Ptr hc(new WS::Client::Config());
            hc->frame = frame_init_simple(2048);
            hc->connect_timeout = 30;
            hc->general_timeout = 60;

            WS::ClientSet::TransactionSet::Ptr ts = new WS::ClientSet::TransactionSet;
            ts->host.host = host;
            ts->host.port = "np";
            ts->http_config = hc;
            ts->debug_level = debug_level;

#if _WIN32_WINNT >= 0x0600 // Vista and higher
            ts->post_connect = [host, client_exe, cb = std::move(cb)](WS::ClientSet::TransactionSet &ts, AsioPolySock::Base &sock)
            {
                AsioPolySock::NamedPipe *np = dynamic_cast<AsioPolySock::NamedPipe *>(&sock);
                if (np)
                {
                    Win::NamedPipePeerInfoServer npinfo(np->handle.native_handle());
                    const std::string server_exe = wstring::to_utf8(npinfo.exe_path);
                    if (!Agent::valid_pipe(client_exe, server_exe))
                        OPENVPN_THROW(ovpnagent, host << " server running from " << server_exe << " could not be validated");
                    cb(npinfo.proc.release());
                }
            };
#endif
            return ts;
        }

        static void make_transaction(const std::string &method, const std::string &content, WS::ClientSet::TransactionSet::Ptr ts)
        {
            std::unique_ptr<WS::ClientSet::Transaction> t(new WS::ClientSet::Transaction);
            t->req.method = "POST";
            t->req.uri = "/" + method;
            t->ci.type = "application/json";
            t->content_out.push_back(buf_from_string(content));
            ts->transactions.push_back(std::move(t));
        }

      private:
        HANDLE get_handle(std::ostream &os) override
        {
            // Build JSON request
            Json::Value jreq(Json::objectValue);
#if _WIN32_WINNT < 0x0600 // pre-Vista needs us to explicitly communicate our PID
            jreq["pid"] = Json::Value((Json::UInt)::GetProcessId(::GetCurrentProcess()));
#endif
            jreq["confirm_event"] = confirm_event.duplicate_local();
            jreq["destroy_event"] = destroy_event.duplicate_local();
            const std::string jtxt = jreq.toStyledString();
            os << jtxt; // dump it

            WS::ClientSet::TransactionSet::Ptr ts = new_transaction_set(
                config->npserv,
                config->debug_level,
                config->client_exe,
                [this](HANDLE handle)
                {
	    if (!service_process.is_open())
	      service_process.assign(handle); });

            jreq["allow_local_dns_resolvers"] = config->allow_local_dns_resolvers;
            make_transaction("tun-open", jtxt, ts);

            // Execute transaction
            WS::ClientSet::new_request_synchronous(ts, NULL);

            // Get result
            const Json::Value jres = get_json_result(os, *ts);

            // Dump log
            const std::string log_txt = json::get_string(jres, "log_txt");
            os << log_txt;

            // Parse TAP handle
            const std::string tap_handle_hex = json::get_string(jres, "tap_handle_hex");
            os << "TAP handle: " << tap_handle_hex << std::endl;
            const HANDLE h = BufHex::parse<HANDLE>(tap_handle_hex, "TAP handle");

            tap_.index = (DWORD)json::get_int(jres, "adapter_index");
            tap_.name = json::get_string(jres, "adapter_name");

            return h;
        }

        TunWin::Util::TapNameGuidPair tap_;

        void set_adapter_state(const TunWin::Util::TapNameGuidPair &tap) override
        {
            tap_ = tap;
        }

        TunWin::Util::TapNameGuidPair get_adapter_state() override
        {
            return tap_;
        }

        virtual HANDLE establish(const TunBuilderCapture &pull,
                                 const std::wstring &openvpn_app_path,
                                 Stop *stop,
                                 std::ostream &os,
                                 TunWin::RingBuffer::Ptr ring_buffer) override // TunWin::SetupBase
        {
            os << "SetupClient: transmitting tun setup list to " << config->npserv << std::endl;

            // Build JSON request
            Json::Value jreq(Json::objectValue);
#if _WIN32_WINNT < 0x0600 // pre-Vista needs us to explicitly communicate our PID
            jreq["pid"] = Json::Value((Json::UInt)::GetProcessId(::GetCurrentProcess()));
#endif

            if (ring_buffer)
                ring_buffer->serialize(jreq);

            jreq["destroy_event"] = destroy_event.duplicate_local();
            if (config->tun_type != TunWin::OvpnDco)
            {
                jreq["confirm_event"] = confirm_event.duplicate_local();
            }
            else
            {
                jreq["adapter_name"] = tap_.name;
                jreq["adapter_index"] = Json::Int(tap_.index);
            }

            jreq["allow_local_dns_resolvers"] = config->allow_local_dns_resolvers;
            jreq["tun_type"] = config->tun_type;
            jreq["tun"] = pull.to_json(); // convert TunBuilderCapture to JSON
            const std::string jtxt = jreq.toStyledString();
            os << jtxt; // dump it

            // Create HTTP transaction container
            WS::ClientSet::TransactionSet::Ptr ts = new_transaction_set(config->npserv,
                                                                        config->debug_level,
                                                                        config->client_exe,
                                                                        [this](HANDLE handle)
                                                                        {
	    if (!service_process.is_open())
	      service_process.assign(handle); });

            make_transaction("tun-setup", jtxt, ts);

            // Execute transaction
            WS::ClientSet::new_request_synchronous(ts, stop);

            // Get result
            const Json::Value jres = get_json_result(os, *ts);

            // Dump log
            const std::string log_txt = json::get_string(jres, "log_txt");
            os << log_txt;

            // Parse TAP handle
            const std::string tap_handle_hex = json::get_string(jres, "tap_handle_hex");
            os << "TAP handle: " << tap_handle_hex << std::endl;
            const HANDLE tap = BufHex::parse<HANDLE>(tap_handle_hex, "TAP handle");
            return tap;
        }

        virtual void l2_finish(const TunBuilderCapture &pull,
                               Stop *stop,
                               std::ostream &os) override
        {
            throw ovpnagent("l2_finish not implemented");
        }

        virtual bool l2_ready(const TunBuilderCapture &pull) override
        {
            throw ovpnagent("l2_ready not implemented");
        }

        virtual void confirm() override
        {
            confirm_event.signal_event();
        }

        void set_service_fail_handler(std::function<void()> &&handler) override
        {
            if (service_process.is_open())
            {
                service_process.async_wait([handler = std::move(handler)](const openvpn_io::error_code &error)
                                           {
		if (!error)
		  handler(); });
            }
        }

        virtual void destroy(std::ostream &os) override // defined by DestructorBase
        {
            os << "SetupClient: signaling tun destroy event" << std::endl;
            service_process.close();
            destroy_event.signal_event();
        }

        Json::Value get_json_result(std::ostream &os, WS::ClientSet::TransactionSet &ts)
        {
            // Get content
            if (ts.transactions.size() != 1)
                throw ovpnagent("unexpected transaction set size");
            WS::ClientSet::Transaction &t = *ts.transactions[0];
            const std::string content = t.content_in.to_string();
            os << t.format_status(ts) << std::endl;

            if (t.comm_status_timeout())
            {
                // this could be the case when agent service
                // hasn't been started yet, so we throw a non-fatal
                // exception which makes core retry.
                os << "connection timeout";
                throw ExceptionCode(Error::TUN_ERROR);
            }

            if (!t.comm_status_success())
            {
                os << content;
                throw ovpnagent("communication error");
            }
            if (!t.request_status_success())
            {
                os << content;
                throw ovpnagent("request error");
            }

            // Verify content-type
            if (t.reply.headers.get_value_trim("content-type") != "application/json")
            {
                os << content;
                throw ovpnagent("unexpected content-type");
            }

            // Parse the returned json dict
            Json::CharReaderBuilder builder;
            std::unique_ptr<Json::CharReader> reader(builder.newCharReader());
            Json::Value jres;
            std::string err;
            if (!reader->parse(content.c_str(), content.c_str() + content.size(), &jres, &err))
            {
                os << content;
                OPENVPN_THROW(ovpnagent, "error parsing returned JSON: " << err);
            }
            return jres;
        }

        Config::Ptr config;
        openvpn_io::windows::object_handle service_process;
        Win::Event confirm_event;
        Win::DestroyEvent destroy_event;
    };

    virtual TunWin::SetupBase::Ptr new_setup_obj(openvpn_io::io_context &io_context, TunWin::Type tun_type, bool allow_local_dns_resolvers) override
    {
        if (config)
        {
            config->tun_type = tun_type;
            config->allow_local_dns_resolvers = allow_local_dns_resolvers;
            return new SetupClient(io_context, config);
        }
        else
            return new TunWin::Setup(io_context, tun_type, allow_local_dns_resolvers);
    }

    WinCommandAgent(const OptionList &opt_parent)
    {
        config.reset(new Config);
    }

    Config::Ptr config;
};
} // namespace openvpn
