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

#ifndef OPENVPN_CLIENT_CLIPROTO_H
#define OPENVPN_CLIENT_CLIPROTO_H

// This is a middle-layer object in the OpenVPN client protocol stack.
// It is above the general OpenVPN protocol implementation in
// class ProtoContext but below the top
// level object in a client connect (ClientConnect).  See ClientConnect for
// a fuller description of the full client stack.
//
// This layer deals with setting up an OpenVPN client connection:
//
// 1. handles creation of transport-layer handler via TransportClientFactory
// 2. handles creation of tun-layer handler via TunClientFactory
// 3. handles sending PUSH_REQUEST to server and processing reply of server-pushed options
// 4. manages the underlying OpenVPN protocol object (class ProtoContext)
// 5. handles timers on behalf of the underlying OpenVPN protocol object
// 6. acts as an exception dispatcher for errors occuring in any of the underlying layers

#include <string>
#include <vector>
#include <memory>
#include <algorithm> // for std::min
#include <cstdint>   // for std::uint...

#include <openvpn/io/io.hpp>

#include <openvpn/common/rc.hpp>
#include <openvpn/common/count.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/base64.hpp>
#include <openvpn/common/clamp_typerange.hpp>
#include <openvpn/ip/ptb.hpp>
#include <openvpn/tun/client/tunbase.hpp>
#include <openvpn/transport/client/transbase.hpp>
#include <openvpn/transport/client/relay.hpp>
#include <openvpn/options/continuation.hpp>
#include <openvpn/options/sanitize.hpp>
#include <openvpn/client/clievent.hpp>
#include <openvpn/client/clicreds.hpp>
#include <openvpn/client/cliconstants.hpp>
#include <openvpn/client/clihalt.hpp>
#include <openvpn/client/optfilt.hpp>
#include <openvpn/time/asiotimer.hpp>
#include <openvpn/time/coarsetime.hpp>
#include <openvpn/time/durhelper.hpp>
#include <openvpn/error/excode.hpp>

#include <openvpn/ssl/proto.hpp>

#ifdef OPENVPN_DEBUG_CLIPROTO
#define OPENVPN_LOG_CLIPROTO(x) OPENVPN_LOG(x)
#else
#define OPENVPN_LOG_CLIPROTO(x)
#endif

using openvpn::numeric_util::clamp_to_typerange;

namespace openvpn::ClientProto {

struct NotifyCallback
{
    virtual void client_proto_terminate() = 0;
    virtual void client_proto_connected()
    {
    }
    virtual void client_proto_auth_pending_timeout(int timeout)
    {
    }
    virtual void client_proto_renegotiated()
    {
    }
};

class Session : ProtoContext,
                TransportClientParent,
                TunClientParent,
                public RC<thread_unsafe_refcount>
{
    typedef ProtoContext Base;
    typedef Base::PacketType PacketType;

    using Base::now;
    using Base::stat;

  public:
    typedef RCPtr<Session> Ptr;
    typedef Base::Config ProtoConfig;

    OPENVPN_EXCEPTION(client_exception);
    OPENVPN_EXCEPTION(client_halt_restart);
    OPENVPN_EXCEPTION(tun_exception);
    OPENVPN_EXCEPTION(transport_exception);
    OPENVPN_EXCEPTION(max_pushed_options_exceeded);
    OPENVPN_SIMPLE_EXCEPTION(session_invalidated);
    OPENVPN_SIMPLE_EXCEPTION(authentication_failed);
    OPENVPN_SIMPLE_EXCEPTION(inactive_timer_expired);
    OPENVPN_SIMPLE_EXCEPTION(relay_event);

    OPENVPN_EXCEPTION(proxy_exception);

    struct Config : public RC<thread_unsafe_refcount>
    {
        typedef RCPtr<Config> Ptr;

        Config()
            : pushed_options_limit("server-pushed options data too large",
                                   ProfileParseLimits::MAX_PUSH_SIZE,
                                   ProfileParseLimits::OPT_OVERHEAD,
                                   ProfileParseLimits::TERM_OVERHEAD,
                                   0,
                                   ProfileParseLimits::MAX_DIRECTIVE_SIZE)
        {
        }

        ProtoConfig::Ptr proto_context_config;
        ProtoContextOptions::Ptr proto_context_options;
        PushOptionsBase::Ptr push_base;
        TransportClientFactory::Ptr transport_factory;
        TunClientFactory::Ptr tun_factory;
        SessionStats::Ptr cli_stats;
        ClientEvent::Queue::Ptr cli_events;
        ClientCreds::Ptr creds;
        OptionList::Limits pushed_options_limit;
        OptionList::FilterBase::Ptr pushed_options_filter;
        unsigned int tcp_queue_limit = 0;
        bool echo = false;
        bool info = false;
        bool autologin_sessions = false;
    };

    Session(openvpn_io::io_context &io_context_arg,
            const Config &config,
            NotifyCallback *notify_callback_arg)
        : Base(config.proto_context_config, config.cli_stats),
          io_context(io_context_arg),
          transport_factory(config.transport_factory),
          tun_factory(config.tun_factory),
          tcp_queue_limit(config.tcp_queue_limit),
          notify_callback(notify_callback_arg),
          housekeeping_timer(io_context_arg),
          push_request_timer(io_context_arg),
          received_options(config.push_base),
          creds(config.creds),
          proto_context_options(config.proto_context_options),
          cli_stats(config.cli_stats),
          cli_events(config.cli_events),
          echo(config.echo),
          info(config.info),
          autologin_sessions(config.autologin_sessions),
          pushed_options_limit(config.pushed_options_limit),
          pushed_options_filter(config.pushed_options_filter),
          inactive_timer(io_context_arg),
          info_hold_timer(io_context_arg)
    {
#ifdef OPENVPN_PACKET_LOG
        packet_log.open(OPENVPN_PACKET_LOG, std::ios::binary);
        if (!packet_log)
            OPENVPN_THROW(open_file_error, "cannot open packet log for output: " << OPENVPN_PACKET_LOG);
#endif
        Base::update_now();
        Base::reset();
        // Base::enable_strict_openvpn_2x();

        info_hold.reset(new std::vector<ClientEvent::Base::Ptr>());
    }

    bool first_packet_received() const
    {
        return first_packet_received_;
    }

    void start()
    {
        if (!halt)
        {
            Base::update_now();

            // coarse wakeup range
            housekeeping_schedule.init(Time::Duration::binary_ms(512), Time::Duration::binary_ms(1024));

            // initialize transport-layer packet handler
            transport = transport_factory->new_transport_client_obj(io_context, this);
            transport_has_send_queue = transport->transport_has_send_queue();
            if (transport_factory->is_relay())
                transport_connecting();
            else
                transport->transport_start();
        }
    }

    TransportClientFactory::Ptr transport_factory_relay()
    {
        TransportClient::Ptr tc(new TransportRelayFactory::TransportClientNull(transport.get()));
        tc.swap(transport);
        return new TransportRelayFactory(io_context, std::move(tc), this);
    }

    void transport_factory_override(TransportClientFactory::Ptr factory)
    {
        transport_factory = std::move(factory);
    }

    void send_explicit_exit_notify()
    {
        if (!halt)
            Base::send_explicit_exit_notify();
    }

    void tun_set_disconnect()
    {
        if (tun)
            tun->set_disconnect();
    }

    void post_cc_msg(const std::string &msg)
    {
        Base::update_now();
        Base::write_control_string(msg);
        Base::flush(true);
        set_housekeeping_timer();
    }

    void stop(const bool call_terminate_callback)
    {
        if (!halt)
        {
            halt = true;
            housekeeping_timer.cancel();
            push_request_timer.cancel();
            inactive_timer.cancel();
            info_hold_timer.cancel();
            if (notify_callback && call_terminate_callback)
                notify_callback->client_proto_terminate();
            if (tun)
                tun->stop(); // call after client_proto_terminate() so it can call back to tun_set_disconnect
            if (transport)
                transport->stop();
        }
    }

    void stop_on_signal(const openvpn_io::error_code &error, int signal_number)
    {
        stop(true);
    }

    bool reached_connected_state() const
    {
        return bool(connected_);
    }

    // If fatal() returns something other than Error::UNDEF, it
    // is intended to flag the higher levels (cliconnect.hpp)
    // that special handling is required.  This handling might include
    // considering the error to be fatal and stopping future connect
    // retries, or emitting a special event.  See cliconnect.hpp
    // for actual implementation.
    Error::Type fatal() const
    {
        return fatal_;
    }
    const std::string &fatal_reason() const
    {
        return fatal_reason_;
    }

    // Getters for values which could potentially be modified by
    // a server's AUTH_FAILED,TEMP response flags
    RemoteList::Advance advance_type() const
    {
        return temp_fail_advance_;
    }
    unsigned int reconnect_delay() const
    {
        return temp_fail_backoff_;
    }

    virtual ~Session()
    {
        stop(false);
    }

  private:
    bool transport_is_openvpn_protocol() override
    {
        return true;
    }

    // transport obj calls here with incoming packets
    void transport_recv(BufferAllocated &buf) override
    {
        try
        {
            OPENVPN_LOG_CLIPROTO("Transport RECV " << server_endpoint_render() << ' ' << Base::dump_packet(buf));

            // update current time
            Base::update_now();

            // update last packet received
            stat().update_last_packet_received(now());

            // log connecting event (only on first packet received)
            if (!first_packet_received_)
            {
                ClientEvent::Base::Ptr ev = new ClientEvent::Connecting();
                cli_events->add_event(std::move(ev));
                first_packet_received_ = true;
            }

            // get packet type
            Base::PacketType pt = Base::packet_type(buf);

            // process packet
            if (pt.is_data())
            {
                // data packet
                Base::data_decrypt(pt, buf);
                if (buf.size())
                {
#ifdef OPENVPN_PACKET_LOG
                    log_packet(buf, false);
#endif
                    // make packet appear as incoming on tun interface
                    if (tun)
                    {
                        OPENVPN_LOG_CLIPROTO("TUN send, size=" << buf.size());
                        tun->tun_send(buf);
                    }
                }

                // do a lightweight flush
                Base::flush(false);
            }
            else if (pt.is_control())
            {
                // control packet
                Base::control_net_recv(pt, std::move(buf));

                // do a full flush
                Base::flush(true);
            }
            else
                cli_stats->error(Error::KEY_STATE_ERROR);

            // schedule housekeeping wakeup
            set_housekeeping_timer();
        }
        catch (const ExceptionCode &e)
        {
            if (e.code_defined())
            {
                if (e.fatal())
                    transport_error((Error::Type)e.code(), e.what());
                else
                    cli_stats->error((Error::Type)e.code());
            }
            else
                process_exception(e, "transport_recv_excode");
        }
        catch (const std::exception &e)
        {
            process_exception(e, "transport_recv");
        }
    }

    void transport_needs_send() override
    {
    }

    // tun i/o driver calls here with incoming packets
    void tun_recv(BufferAllocated &buf) override
    {
        try
        {
            OPENVPN_LOG_CLIPROTO("TUN recv, size=" << buf.size());

            // update current time
            Base::update_now();

            // log packet
#ifdef OPENVPN_PACKET_LOG
            log_packet(buf, true);
#endif

            // if transport layer has an output queue, check if it's full
            if (transport_has_send_queue)
            {
                if (transport->transport_send_queue_size() > tcp_queue_limit)
                {
                    buf.reset_size(); // queue full, drop packet
                    cli_stats->error(Error::TCP_OVERFLOW);
                }
            }

            // encrypt packet
            if (buf.size())
            {
                const ProtoContext::Config &c = Base::conf();
                // when calculating mss, we take IPv4 and TCP headers into account
                // here we need to add it back since we check the whole IP packet size, not just TCP payload
                constexpr size_t MinTcpHeader = 20;
                constexpr size_t MinIpHeader = 20;
                size_t mss_no_tcp_ip_encap = c.mss_fix + (MinTcpHeader + MinIpHeader);
                if (c.mss_fix > 0 && buf.size() > mss_no_tcp_ip_encap)
                {
                    Ptb::generate_icmp_ptb(buf, clamp_to_typerange<unsigned short>(mss_no_tcp_ip_encap));
                    tun->tun_send(buf);
                }
                else
                {
                    Base::data_encrypt(buf);
                    if (buf.size())
                    {
                        // send packet via transport to destination
                        OPENVPN_LOG_CLIPROTO("Transport SEND " << server_endpoint_render() << ' ' << Base::dump_packet(buf));
                        if (transport->transport_send(buf))
                            Base::update_last_sent();
                        else if (halt)
                            return;
                    }
                }
            }

            // do a lightweight flush
            Base::flush(false);

            // schedule housekeeping wakeup
            set_housekeeping_timer();
        }
        catch (const std::exception &e)
        {
            process_exception(e, "tun_recv");
        }
    }

    // Return true if keepalive parameter(s) are enabled.
    bool is_keepalive_enabled() const override
    {
        return Base::is_keepalive_enabled();
    }

    // Disable keepalive for rest of session, but fetch
    // the keepalive parameters (in seconds).
    void disable_keepalive(unsigned int &keepalive_ping,
                           unsigned int &keepalive_timeout) override
    {
        Base::disable_keepalive(keepalive_ping, keepalive_timeout);
    }

    void transport_pre_resolve() override
    {
        ClientEvent::Base::Ptr ev = new ClientEvent::Resolve();
        cli_events->add_event(std::move(ev));
    }

    std::string server_endpoint_render()
    {
        std::string server_host, server_port, server_proto, server_ip;
        transport->server_endpoint_info(server_host, server_port, server_proto, server_ip);
        std::ostringstream out;
        out << '[' << server_host << "]:" << server_port << " (" << server_ip << ") via " << server_proto;
        return out.str();
    }

    void transport_wait_proxy() override
    {
        ClientEvent::Base::Ptr ev = new ClientEvent::WaitProxy();
        cli_events->add_event(std::move(ev));
    }

    void transport_wait() override
    {
        ClientEvent::Base::Ptr ev = new ClientEvent::Wait();
        cli_events->add_event(std::move(ev));
    }

    void transport_connecting() override
    {
        try
        {
            OPENVPN_LOG("Connecting to " << server_endpoint_render());
            Base::set_protocol(transport->transport_protocol());
            Base::start();
            Base::flush(true);
            set_housekeeping_timer();
        }
        catch (const std::exception &e)
        {
            process_exception(e, "transport_connecting");
        }
    }

    void transport_error(const Error::Type fatal_err, const std::string &err_text) override
    {
        if (fatal_err != Error::UNDEF)
        {
            fatal_ = fatal_err;
            fatal_reason_ = err_text;
        }
        if (notify_callback)
        {
            OPENVPN_LOG("Transport Error: " << err_text);
            stop(true);
        }
        else
            throw transport_exception(err_text);
    }

    void proxy_error(const Error::Type fatal_err, const std::string &err_text) override
    {
        if (fatal_err != Error::UNDEF)
        {
            fatal_ = fatal_err;
            fatal_reason_ = err_text;
        }
        if (notify_callback)
        {
            OPENVPN_LOG("Proxy Error: " << err_text);
            stop(true);
        }
        else
            throw proxy_exception(err_text);
    }

    void extract_auth_token(const OptionList &opt)
    {
        std::string username;

        // auth-token-user
        {
            const Option *o = opt.get_ptr("auth-token-user");
            if (o)
                username = base64->decode(o->get(1, 340)); // 255 chars after base64 decode
        }

        // auth-token
        {
            // if auth-token is present, use it as the password for future renegotiations
            const Option *o = opt.get_ptr("auth-token");
            if (o)
            {
                const std::string &sess_id = o->get(1, 256);
                if (creds)
                {
                    if (!username.empty())
                        OPENVPN_LOG("Session user: " << username);
#ifdef OPENVPN_SHOW_SESSION_TOKEN
                    OPENVPN_LOG("Session token: " << sess_id);
#else
                    OPENVPN_LOG("Session token: [redacted]");
#endif
                    autologin_sessions = true;
                    conf().set_xmit_creds(true);
                    creds->set_replace_password_with_session_id(true);
                    creds->set_session_id(username, sess_id);
                }
            }
        }
    }

    /**
     * Parses a AUTH_FAILED,TEMP string, extracts the flags and returns
     * the human readable reason part of it, if there is one. The string
     * passed has the format "[flag(s)]:reason".
     *
     * Flags are optional and delimited by a comma (","). They are given
     * as "key=value" strings. Currently there's support for parsing two keys:
     *   - backoff: seconds to wait between reconnects
     *   - advance: how to advance through the remote addresses list.
     *     Possible values are:
     *     - no: do not advance
     *     - addr: use the next address in the list (default)
     *     - remote: use the next remote's first address
     *
     * The reason string is free text and returned verbatim.
     *
     * @param msg  The string to be parsed
     *
     * @return Returns the human readable reason for the auth failure or an
     *         empty string if not applicable.
     */
    std::string parse_auth_failed_temp(const std::string &msg)
    {
        if (msg.empty())
            return msg;

        // Reset to default values
        temp_fail_backoff_ = 0;
        temp_fail_advance_ = RemoteList::Advance::Addr;

        std::string::size_type reason_idx = 0;
        auto endofflags = msg.find(']');
        if (msg.at(0) == '[' && endofflags != std::string::npos)
        {
            reason_idx = ++endofflags;
            auto flags = string::split({msg, 1, endofflags - 2}, ',');
            for (const auto &flag : flags)
            {
                std::string key;
                std::string value;
                std::istringstream f(flag);

                f >> key >> value;
                if (f.fail())
                {
                    OPENVPN_LOG("invalid AUTH_FAILED,TEMP flag: " << flag);
                    continue;
                }

                if (key == "backoff")
                {
                    if (!parse_number(value, temp_fail_backoff_))
                    {
                        OPENVPN_LOG("invalid AUTH_FAILED,TEMP flag: " << flag);
                    }
                    temp_fail_backoff_ *= 1000; // Convert to ms
                }
                else if (key == "advance")
                {
                    if (value == "no")
                    {
                        temp_fail_advance_ = RemoteList::Advance::None;
                    }
                    else if (value == "addr")
                    {
                        temp_fail_advance_ = RemoteList::Advance::Addr;
                    }
                    else if (value == "remote")
                    {
                        temp_fail_advance_ = RemoteList::Advance::Remote;
                    }
                    else
                    {
                        OPENVPN_LOG("unknown AUTH_FAILED,TEMP flag: " << flag);
                    }
                }
                else
                {
                    OPENVPN_LOG("unknown AUTH_FAILED,TEMP flag: " << flag);
                }
            }
        }

        if (reason_idx >= msg.size() || msg[reason_idx] != ':')
            return "";

        // skip leading colon
        return msg.substr(reason_idx + 1);
    }

    // proto base class calls here for control channel network sends
    void control_net_send(const Buffer &net_buf) override
    {
        OPENVPN_LOG_CLIPROTO("Transport SEND " << server_endpoint_render() << ' ' << Base::dump_packet(net_buf));
        if (transport->transport_send_const(net_buf))
            Base::update_last_sent();
    }

    // proto base class calls here for app-level control-channel messages received
    void control_recv(BufferPtr &&app_bp) override
    {
        const std::string msg = Unicode::utf8_printable(Base::template read_control_string<std::string>(*app_bp),
                                                        Unicode::UTF8_FILTER | Unicode::UTF8_PASS_FMT);

        // OPENVPN_LOG("SERVER: " << sanitize_control_message(msg));

        if (!received_options.complete() && string::starts_with(msg, "PUSH_REPLY,"))
        {
            // parse the received options
            auto pushed_options_list = OptionList::parse_from_csv_static(msg.substr(11), &pushed_options_limit);
            try
            {
                received_options.add(pushed_options_list, pushed_options_filter.get());
            }
            catch (const Option::RejectedException &e)
            {
                ClientHalt ch("RESTART,rejected pushed option: " + e.err(), true);
                process_halt_restart(ch);
            }
            if (received_options.complete())
            {
                // show options
                OPENVPN_LOG("OPTIONS:" << std::endl
                                       << render_options_sanitized(received_options, Option::RENDER_PASS_FMT | Option::RENDER_NUMBER | Option::RENDER_BRACKET));

                // relay servers are not allowed to establish a tunnel with us
                if (Base::conf().relay_mode)
                {
                    tun_error(Error::RELAY_ERROR, "tunnel not permitted to relay server");
                    return;
                }

                // Merge local and pushed options
                received_options.finalize(pushed_options_merger);

                // process "echo" directives
                if (echo)
                    process_echo(received_options);

                // process auth-token
                extract_auth_token(received_options);

                // process pushed transport options
                transport_factory->process_push(received_options);

                // modify proto config (cipher, auth, key-derivation and compression methods)
                Base::process_push(received_options, *proto_context_options);

                // initialize tun/routing
                tun = tun_factory->new_tun_client_obj(io_context, *this, transport.get());
                tun->tun_start(received_options, *transport, Base::dc_settings());

                // we should be connected at this point
                if (!connected_)
                    throw tun_exception("not connected");

                // Propagate tun-mtu back, it might have been overwritten by a pushed tun-mtu option
                conf().tun_mtu = tun->vpn_mtu();

                // initialize data channel after pushed options have been processed
                Base::init_data_channel();

                // we got pushed options and initializated crypto - now we can push mss to dco
                tun->adjust_mss(conf().mss_fix);

                // Allow ProtoContext to suggest an alignment adjustment
                // hint for transport layer.
                transport->reset_align_adjust(Base::align_adjust_hint());

                // process "inactive" directive
                process_inactive(received_options);

                // tell parent that we are connected
                if (notify_callback)
                    notify_callback->client_proto_connected();

                // start info-hold timer
                schedule_info_hold_callback();

                // send the Connected event
                cli_events->add_event(connected_);

                // check for proto options
                check_proto_warnings();
            }
            else
                OPENVPN_LOG("Options continuation...");
        }
        else if (received_options.complete() && string::starts_with(msg, "PUSH_REPLY,"))
        {
            // We got a PUSH REPLY in the middle of a session. Ignore it apart from
            // updating the auth-token if included in the push reply
            auto opts = OptionList::parse_from_csv_static(msg.substr(11), nullptr);
            extract_auth_token(opts);
        }
        else if (string::starts_with(msg, "AUTH_FAILED"))
        {
            std::string reason;
            std::string log_reason;

            // get reason (if it exists) for authentication failure
            if (msg.length() >= 13)
                reason = string::trim_left_copy(std::string(msg, 12));

            // If session token problem (such as expiration), and we have a cached
            // password, retry with it.  Otherwise, fail without retry.
            if (string::starts_with(reason, "SESSION:")
                && ((creds && creds->reset_to_cached_password())
                    || autologin_sessions))
            {
                if (creds && creds->session_id_defined())
                    creds->purge_session_id();
                log_reason = "SESSION_AUTH_FAILED";
            }
            // If session token problem (such as expiration), and we have a cached
            // password, retry with it.  Otherwise, fail without retry.
            if (string::starts_with(reason, "TEMP"))
            {
                log_reason = "AUTH_FAILED_TEMP:" + parse_auth_failed_temp(std::string(reason, 4));
            }
            else
            {
                fatal_ = Error::AUTH_FAILED;
                fatal_reason_ = reason;
                log_reason = "AUTH_FAILED";
            }
            if (notify_callback)
            {
                OPENVPN_LOG(log_reason);
                stop(true);
            }
            else
                throw authentication_failed();
        }
        else if (ClientHalt::match(msg))
        {
            const ClientHalt ch(msg, true);
            process_halt_restart(ch);
        }
        else if (info && string::starts_with(msg, "INFO,"))
        {
            // Buffer INFO messages received near Connected event to fire
            // one second after Connected event, to reduce the chance of
            // race conditions in the client app, if the INFO event
            // triggers the client app to perform an operation that
            // requires the VPN tunnel to be ready.
            ClientEvent::Base::Ptr ev = new ClientEvent::Info(msg.substr(5));
            if (info_hold)
                info_hold->push_back(std::move(ev));
            else
                cli_events->add_event(std::move(ev));
        }
        else if (info && string::starts_with(msg, "INFO_PRE,"))
        {
            // INFO_PRE is like INFO but it is never buffered
            ClientEvent::Base::Ptr ev = new ClientEvent::Info(msg.substr(9));
            cli_events->add_event(std::move(ev));
        }
        else if (msg == "AUTH_PENDING" || string::starts_with(msg, "AUTH_PENDING,"))
        {
            // AUTH_PENDING indicates an out-of-band authentication step must
            // be performed before the server will send the PUSH_REPLY message.
            if (!auth_pending)
            {
                auth_pending = true;
                std::string key_words;

                unsigned int timeout = 0;

                if (string::starts_with(msg, "AUTH_PENDING,"))
                {
                    key_words = msg.substr(::strlen("AUTH_PENDING,"));
                    auto opts = OptionList::parse_from_csv_static(key_words, nullptr);
                    std::string timeout_str = opts.get_optional("timeout", 1, 20);
                    if (timeout_str != "")
                    {
                        try
                        {
                            timeout = clamp_to_typerange<unsigned int>(std::stoul(timeout_str));
                            // Cap the timeout to end well before renegotiation starts
                            timeout = std::min(timeout, static_cast<decltype(timeout)>(conf().renegotiate.to_seconds() / 2));
                        }
                        catch (const std::logic_error &e)
                        {
                            OPENVPN_LOG("could not parse AUTH_PENDING timeout: " << timeout_str);
                        }
                    }
                }



                if (notify_callback && timeout > 0)
                {
                    notify_callback->client_proto_auth_pending_timeout(timeout);
                }

                ClientEvent::Base::Ptr ev = new ClientEvent::AuthPending(timeout, key_words);
                cli_events->add_event(std::move(ev));
            }
        }
        else if (msg == "RELAY")
        {
            if (Base::conf().relay_mode)
            {
                fatal_ = Error::RELAY;
                fatal_reason_ = "";
            }
            else
            {
                fatal_ = Error::RELAY_ERROR;
                fatal_reason_ = "not in relay mode";
            }
            if (notify_callback)
            {
                OPENVPN_LOG(Error::name(fatal_) << ' ' << fatal_reason_);
                stop(true);
            }
            else
                throw relay_event();
        }
    }

    void tun_pre_tun_config() override
    {
        ClientEvent::Base::Ptr ev = new ClientEvent::AssignIP();
        cli_events->add_event(std::move(ev));
    }

    void tun_pre_route_config() override
    {
        ClientEvent::Base::Ptr ev = new ClientEvent::AddRoutes();
        cli_events->add_event(std::move(ev));
    }

    void tun_event(ClientEvent::Base::Ptr ev) override
    {
        cli_events->add_event(std::move(ev));
    }

    void tun_connected() override
    {
        OPENVPN_LOG("Connected via " + tun->tun_name());

        ClientEvent::Connected::Ptr ev = new ClientEvent::Connected();
        if (creds)
            ev->user = creds->get_username();
        transport->server_endpoint_info(ev->server_host, ev->server_port, ev->server_proto, ev->server_ip);
        ev->vpn_ip4 = tun->vpn_ip4();
        ev->vpn_ip6 = tun->vpn_ip6();
        ev->vpn_gw4 = tun->vpn_gw4();
        ev->vpn_gw6 = tun->vpn_gw6();
        if (tun->vpn_mtu())
        {
            ev->vpn_mtu = std::to_string(tun->vpn_mtu());
        }
        else
        {
            ev->vpn_mtu = "(default)";
        }

        try
        {
            std::string client_ip = received_options.get_optional("client-ip", 1, 256);
            if (!client_ip.empty())
                ev->client_ip = IP::Addr::validate(client_ip, "client-ip");
        }
        catch (const std::exception &e)
        {
            OPENVPN_LOG("exception parsing client-ip: " << e.what());
        }
        ev->tun_name = tun->tun_name();
        connected_ = std::move(ev);
    }

    void tun_error(const Error::Type fatal_err, const std::string &err_text) override
    {
        if (fatal_err == Error::TUN_HALT)
            send_explicit_exit_notify();
        if (fatal_err != Error::UNDEF)
        {
            fatal_ = fatal_err;
            fatal_reason_ = err_text;
        }
        if (notify_callback)
        {
            OPENVPN_LOG("TUN Error: " << err_text);
            stop(true);
        }
        else
            throw tun_exception(err_text);
    }

    // proto base class calls here to get auth credentials
    void client_auth(Buffer &buf) override
    {
        // we never send creds to a relay server
        if (creds && !Base::conf().relay_mode)
        {
            OPENVPN_LOG("Creds: " << creds->auth_info());
            Base::write_auth_string(creds->get_username(), buf);
#ifdef OPENVPN_DISABLE_AUTH_TOKEN // debugging only
            if (creds->session_id_defined())
            {
                OPENVPN_LOG("NOTE: not sending auth-token");
                Base::write_empty_string(buf);
            }
            else
#endif
            {
                Base::write_auth_string(creds->get_password(), buf);
            }
        }
        else
        {
            OPENVPN_LOG("Creds: None");
            Base::write_empty_string(buf); // username
            Base::write_empty_string(buf); // password
        }
    }

    void send_push_request_callback(const Time::Duration &dur,
                                    const openvpn_io::error_code &e)
    {
        try
        {
            if (!e && !halt && !received_options.partial())
            {
                Base::update_now();
                if (!sent_push_request)
                {
                    ClientEvent::Base::Ptr ev = new ClientEvent::GetConfig();
                    cli_events->add_event(std::move(ev));
                    sent_push_request = true;
                }
                OPENVPN_LOG("Sending PUSH_REQUEST to server...");
                Base::write_control_string(std::string("PUSH_REQUEST"));
                Base::flush(true);
                set_housekeeping_timer();

                {
                    if (auth_pending)
                    {
                        // With auth_pending, we can dial back the PUSH_REQUEST
                        // frequency, but we still need back-and-forth network
                        // activity to avoid an inactivity timeout, since the crypto
                        // layer (and hence keepalive ping) is not initialized until
                        // we receive the PUSH_REPLY from the server.
                        schedule_push_request_callback(Time::Duration::seconds(8));
                    }
                    else
                    {
                        // step function with ceiling: 1 sec, 2 secs, 3 secs, 3, 3, ...
                        const Time::Duration newdur = std::min(dur + Time::Duration::seconds(1),
                                                               Time::Duration::seconds(3));
                        schedule_push_request_callback(newdur);
                    }
                }
            }
        }
        catch (const std::exception &e)
        {
            process_exception(e, "send_push_request_callback");
        }
    }

    void schedule_push_request_callback(const Time::Duration &dur)
    {
        if (!received_options.partial())
        {
            push_request_timer.expires_after(dur);
            push_request_timer.async_wait([self = Ptr(this), dur](const openvpn_io::error_code &error)
                                          {
                                            OPENVPN_ASYNC_HANDLER;
                                            self->send_push_request_callback(dur, error); });
        }
    }

    // react to any tls warning triggered during the tls-handshake
    virtual void check_tls_warnings()
    {
        uint32_t tls_warnings = get_tls_warnings();

        if (tls_warnings & SSLAPI::TLS_WARN_SIG_MD5)
        {
            ClientEvent::Base::Ptr ev = new ClientEvent::Warn("TLS: received certificate signed with MD5. Please inform your admin to upgrade to a stronger algorithm. Support for MD5 will be dropped at end of Apr 2018");
            cli_events->add_event(std::move(ev));
        }

        if (tls_warnings & SSLAPI::TLS_WARN_SIG_SHA1)
        {
            ClientEvent::Base::Ptr ev = new ClientEvent::Warn("TLS: received certificate signed with SHA1. Please inform your admin to upgrade to a stronger algorithm. Support for SHA1 signatures will be dropped in the future");
            cli_events->add_event(std::move(ev));
        }
    }

    void check_proto_warnings()
    {
        if (uses_bs64_cipher())
        {
            ClientEvent::Base::Ptr ev = new ClientEvent::Warn("Proto: Using a 64-bit block cipher that is vulnerable to the SWEET32 attack. Please inform your admin to upgrade to a stronger algorithm. Support for 64-bit block cipher will be dropped in the future.");
            cli_events->add_event(std::move(ev));
        }

        // Issue an event if compression is enabled
        CompressContext::Type comp_type = Base::conf().comp_ctx.type();
        if (comp_type != CompressContext::NONE
            && !CompressContext::is_any_stub(comp_type))
        {
            std::ostringstream msg;
            msg << (proto_context_options->is_comp_asym()
                        ? "Asymmetric compression enabled.  Server may send compressed data."
                        : "Compression enabled.");
            msg << "  This may be a potential security issue.";
            ClientEvent::Base::Ptr ev = new ClientEvent::CompressionEnabled(msg.str());
            cli_events->add_event(std::move(ev));
        }
    }

    // base class calls here when session transitions to ACTIVE state
    void active(bool primary) override
    {
        if (primary)
        {
            OPENVPN_LOG("Session is ACTIVE");
            check_tls_warnings();
            schedule_push_request_callback(Time::Duration::seconds(0));
        }
        else if (notify_callback)
            notify_callback->client_proto_renegotiated();
    }

    void housekeeping_callback(const openvpn_io::error_code &e)
    {
        try
        {
            if (!e && !halt)
            {
                // update current time
                Base::update_now();

                housekeeping_schedule.reset();
                Base::housekeeping();
                if (Base::invalidated())
                {
                    if (notify_callback)
                    {
                        OPENVPN_LOG("Session invalidated: " << Error::name(Base::invalidation_reason()));
                        stop(true);
                    }
                    else
                        throw session_invalidated();
                }
                set_housekeeping_timer();
            }
        }
        catch (const std::exception &e)
        {
            process_exception(e, "housekeeping_callback");
        }
    }

    void set_housekeeping_timer()
    {
        if (halt)
            return;

        Time next = Base::next_housekeeping();
        if (!housekeeping_schedule.similar(next))
        {
            if (!next.is_infinite())
            {
                next.max(now());
                housekeeping_schedule.reset(next);
                housekeeping_timer.expires_at(next);
                housekeeping_timer.async_wait([self = Ptr(this)](const openvpn_io::error_code &error)
                                              {
                                                OPENVPN_ASYNC_HANDLER;
                                                self->housekeeping_callback(error); });
            }
            else
            {
                housekeeping_timer.cancel();
                housekeeping_schedule.reset();
            }
        }
    }

    void process_inactive(const OptionList &opt)
    {
        try
        {
            const Option *o = load_duration_parm(inactive_duration, "inactive", opt, 1, false, false);
            if (o)
            {
                if (o->size() >= 3)
                    inactive_bytes = parse_number_throw<unsigned int>(o->get(2, 16), "inactive bytes");
                schedule_inactive_timer();
            }
        }
        catch (const std::exception &e)
        {
            OPENVPN_LOG("exception parsing inactive: " << e.what());
        }
    }

    void schedule_inactive_timer()
    {
        inactive_timer.expires_after(inactive_duration);
        inactive_timer.async_wait([self = Ptr(this)](const openvpn_io::error_code &error)
                                  {
                                    OPENVPN_ASYNC_HANDLER;
                                    self->inactive_callback(error); });
    }

    void inactive_callback(const openvpn_io::error_code &e) // fixme for DCO
    {
        try
        {
            if (!e && !halt)
            {
                // update current time
                Base::update_now();
                const count_t sample = cli_stats->get_stat(SessionStats::TUN_BYTES_IN) + cli_stats->get_stat(SessionStats::TUN_BYTES_OUT);
                const count_t delta = sample - inactive_last_sample;
                // OPENVPN_LOG("*** INACTIVE SAMPLE " << delta << ' ' << inactive_bytes);
                if (delta <= count_t(inactive_bytes))
                {
                    fatal_ = Error::INACTIVE_TIMEOUT;
                    send_explicit_exit_notify();
                    if (notify_callback)
                    {
                        OPENVPN_LOG("inactive timer expired");
                        stop(true);
                    }
                    else
                        throw inactive_timer_expired();
                }
                else
                {
                    inactive_last_sample = sample;
                    schedule_inactive_timer();
                }
            }
        }
        catch (const std::exception &e)
        {
            process_exception(e, "inactive_callback");
        }
    }

    void process_echo(const OptionList &opt)
    {
        OptionList::IndexMap::const_iterator echo_opt = opt.map().find("echo");
        if (echo_opt != opt.map().end())
        {
            for (OptionList::IndexList::const_iterator i = echo_opt->second.begin(); i != echo_opt->second.end(); ++i)
            {
                const Option &o = opt[*i];
                o.touch();
                const std::string &value = o.get(1, 512);
                ClientEvent::Base::Ptr ev = new ClientEvent::Echo(value);
                cli_events->add_event(std::move(ev));
            }
        }
    }

    void process_exception(const std::exception &e, const char *method_name)
    {
        if (notify_callback)
        {
            OPENVPN_LOG("Client exception in " << method_name << ": " << e.what());
            stop(true);
        }
        else
            throw client_exception(e.what());
    }

    void process_halt_restart(const ClientHalt &ch)
    {
        if (!ch.psid() && creds)
            creds->purge_session_id();
        if (ch.restart())
            fatal_ = Error::CLIENT_RESTART;
        else
            fatal_ = Error::CLIENT_HALT;
        fatal_reason_ = ch.reason();
        if (notify_callback)
        {
            OPENVPN_LOG("Client halt/restart: " << ch.render());
            stop(true);
        }
        else
            throw client_halt_restart(ch.render());
    }

    void schedule_info_hold_callback()
    {
        Base::update_now();
        info_hold_timer.expires_after(Time::Duration::seconds(1));
        info_hold_timer.async_wait([self = Ptr(this)](const openvpn_io::error_code &error)
                                   {
                                    OPENVPN_ASYNC_HANDLER;
                                    self->info_hold_callback(error); });
    }

    void info_hold_callback(const openvpn_io::error_code &e)
    {
        try
        {
            if (!e && !halt)
            {
                Base::update_now();
                if (info_hold)
                {
                    for (auto &ev : *info_hold)
                        cli_events->add_event(std::move(ev));
                    info_hold.reset();
                }
            }
        }
        catch (const std::exception &e)
        {
            process_exception(e, "info_hold_callback");
        }
    }

#ifdef OPENVPN_PACKET_LOG
    void log_packet(const Buffer &buf, const bool out)
    {
        if (buf.size())
        {
            std::uint16_t len = buf.size() & 0x7FFF;
            if (out)
                len |= 0x8000;
            packet_log.write((const char *)&len, sizeof(len));
            packet_log.write((const char *)buf.c_data(), buf.size());
        }
    }
#endif

    openvpn_io::io_context &io_context;

    TransportClientFactory::Ptr transport_factory;
    TransportClient::Ptr transport;

    TunClientFactory::Ptr tun_factory;
    TunClient::Ptr tun;

    unsigned int tcp_queue_limit;
    bool transport_has_send_queue = false;

    NotifyCallback *notify_callback;

    CoarseTime housekeeping_schedule;
    AsioTimer housekeeping_timer;
    AsioTimer push_request_timer;
    bool halt = false;

    OptionListContinuation received_options;

    ClientCreds::Ptr creds;

    ProtoContextOptions::Ptr proto_context_options;

    bool first_packet_received_ = false;
    bool sent_push_request = false;
    bool auth_pending = false;

    SessionStats::Ptr cli_stats;
    ClientEvent::Queue::Ptr cli_events;

    ClientEvent::Connected::Ptr connected_;

    bool echo;
    bool info;
    bool autologin_sessions;

    Error::Type fatal_ = Error::UNDEF;
    std::string fatal_reason_;

    OptionList::Limits pushed_options_limit;
    OptionList::FilterBase::Ptr pushed_options_filter;
    PushOptionsMerger::Ptr pushed_options_merger;

    AsioTimer inactive_timer;
    Time::Duration inactive_duration;
    unsigned int inactive_bytes = 0;
    count_t inactive_last_sample = 0;

    std::unique_ptr<std::vector<ClientEvent::Base::Ptr>> info_hold;
    AsioTimer info_hold_timer;

    // AUTH_FAILED,TEMP flag values
    unsigned int temp_fail_backoff_ = 0;
    RemoteList::Advance temp_fail_advance_ = RemoteList::Advance::Addr;

#ifdef OPENVPN_PACKET_LOG
    std::ofstream packet_log;
#endif
};
} // namespace openvpn::ClientProto

#endif
