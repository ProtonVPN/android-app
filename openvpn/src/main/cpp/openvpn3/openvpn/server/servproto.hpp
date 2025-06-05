//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012- OpenVPN Inc.
//
//    SPDX-License-Identifier: MPL-2.0 OR AGPL-3.0-only WITH openvpn3-openssl-exception
//

// OpenVPN protocol implementation for client-instance object on server

#ifndef OPENVPN_SERVER_SERVPROTO_H
#define OPENVPN_SERVER_SERVPROTO_H

#include <cstdlib> // defines std::abort()
#include <memory>
#include <utility> // for std::move

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/common/unicode.hpp>
#include <openvpn/common/link.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/buffer/bufstream.hpp>
#include <openvpn/time/asiotimer.hpp>
#include <openvpn/time/coarsetime.hpp>
#include <openvpn/crypto/cryptodc.hpp>
#include <openvpn/ssl/proto.hpp>
#include <openvpn/transport/server/transbase.hpp>
#include <openvpn/tun/server/tunbase.hpp>
#include <openvpn/server/manage.hpp>

#ifdef OPENVPN_DEBUG_SERVPROTO
#define OPENVPN_LOG_SERVPROTO(x) OPENVPN_LOG(x)
#else
#define OPENVPN_LOG_SERVPROTO(x)
#endif

namespace openvpn {

class ServerProto
{
    typedef Link<TransportClientInstance::Send, TransportClientInstance::Recv> TransportLink;
    typedef Link<TunClientInstance::Send, TunClientInstance::Recv> TunLink;
    typedef Link<ManClientInstance::Send, ManClientInstance::Recv> ManLink;

  public:
    class Session;

    class Factory : public TransportClientInstance::Factory
    {
      public:
        typedef RCPtr<Factory> Ptr;
        typedef ProtoContext::ProtoConfig ProtoConfig;

        Factory(openvpn_io::io_context &io_context_arg,
                const ProtoConfig &c)
            : io_context(io_context_arg)
        {
            if (c.tls_crypt_enabled() || c.tls_crypt_v2_enabled())
                tls_crypt_preval.reset(new ProtoContext::TLSCryptPreValidate(c, true));

            if (c.tls_auth_enabled())
                tls_auth_preval.reset(new ProtoContext::TLSAuthPreValidate(c, true));
        }

        TransportClientInstance::Recv::Ptr new_client_instance() override;

        bool validate_initial_packet(const BufferAllocated &net_buf) override
        {
            if (!tls_auth_preval && !tls_crypt_preval)
                return true;

            const bool ret = (tls_auth_preval && tls_auth_preval->validate(net_buf))
                             || (tls_crypt_preval && tls_crypt_preval->validate(net_buf));

            if (!ret)
                stats->error(Error::TLS_AUTH_FAIL);

            return ret;
        }

        ProtoConfig::Ptr clone_proto_config() const
        {
            return new ProtoConfig(*proto_context_config);
        }

        openvpn_io::io_context &io_context;
        ProtoConfig::Ptr proto_context_config;

        ManClientInstance::Factory::Ptr man_factory;
        TunClientInstance::Factory::Ptr tun_factory;

        SessionStats::Ptr stats;

      private:
        ProtoContext::TLSWrapPreValidate::Ptr tls_auth_preval;
        ProtoContext::TLSWrapPreValidate::Ptr tls_crypt_preval;
    };

    // This is the main server-side client instance object
    class Session : ProtoContextCallbackInterface, // Callback interface from protocol implementation
                    public TransportLink,          // Transport layer
                    public TunLink,                // Tun/routing layer
                    public ManLink                 // Management layer
    {
        friend class Factory; // calls constructor

      public:
        typedef RCPtr<Session> Ptr;

        bool defined() const override
        {
            return defined_();
        }

        TunClientInstance::Recv *override_tun(TunClientInstance::Send *tun) override
        {
            TunLink::send.reset(tun);
            return this;
        }

        void start(const TransportClientInstance::Send::Ptr &parent,
                   const PeerAddr::Ptr &addr,
                   const int local_peer_id,
                   const ProtoSessionID cookie_psid = ProtoSessionID()) override
        {
            TransportLink::send = parent;
            peer_addr = addr;

            // init OpenVPN protocol handshake
            proto_context.update_now();
            proto_context.reset(cookie_psid);
            proto_context.set_local_peer_id(local_peer_id);
            proto_context.start(cookie_psid);
            proto_context.flush(true);

            // coarse wakeup range
            housekeeping_schedule.init(Time::Duration::binary_ms(512), Time::Duration::binary_ms(1024));
        }

        PeerStats stats_poll() override
        {
            if (TransportLink::send)
                return TransportLink::send->stats_poll();
            else
                return PeerStats();
        }

        bool should_preserve_session_id() override
        {
            return preserve_session_id;
        }

        void stop() override
        {
            if (!halt)
            {
                halt = true;
                housekeeping_timer.cancel();

                if (ManLink::send)
                    ManLink::send->pre_stop();

                // deliver final peer stats to management layer
                if (TransportLink::send && ManLink::send)
                {
                    if (TransportLink::send->stats_pending())
                        ManLink::send->stats_notify(TransportLink::send->stats_poll(), true);
                }

                proto_context.pre_destroy();
                proto_context.reset_dc_factory();
                if (TransportLink::send)
                {
                    TransportLink::send->stop();
                    TransportLink::send.reset();
                }
                if (TunLink::send)
                {
                    TunLink::send->stop();
                    TunLink::send.reset();
                }
                if (ManLink::send)
                {
                    ManLink::send->stop();
                    ManLink::send.reset();
                }
            }
        }

        // called with OpenVPN-encapsulated packets from transport layer
        bool transport_recv(BufferAllocated &buf) override
        {
            bool ret = false;
            if (!proto_context.primary_defined())
                return false;
            try
            {
                OPENVPN_LOG_SERVPROTO(instance_name() << " : Transport RECV[" << buf.size() << "] " << client_endpoint_render() << ' ' << proto_context.dump_packet(buf));

                // update current time
                proto_context.update_now();

                // get packet type
                ProtoContext::PacketType pt = proto_context.packet_type(buf);

                // process packet
                if (pt.is_data())
                {
                    // data packet
                    ret = proto_context.data_decrypt(pt, buf);
                    if (buf.size())
                    {
#ifdef OPENVPN_PACKET_LOG
                        log_packet(buf, false);
#endif
                        // make packet appear as incoming on tun interface
                        if (true) // fixme: was tun
                        {
                            OPENVPN_LOG_SERVPROTO(instance_name() << " : TUN SEND[" << buf.size() << ']');
                            // fixme -- code me
                        }
                    }

                    // do a lightweight flush
                    proto_context.flush(false);
                }
                else if (pt.is_control())
                {
                    // control packet
                    ret = proto_context.control_net_recv(pt, std::move(buf));

                    // do a full flush
                    proto_context.flush(true);
                }

                // schedule housekeeping wakeup
                set_housekeeping_timer();
            }
            catch (const std::exception &e)
            {
                error(e);
                ret = false;
            }

            return ret;
        }

        // called with cleartext IP packets from routing layer
        void tun_recv(BufferAllocated &buf) override
        {
            // fixme -- code me
        }

        // Return true if keepalive parameter(s) are enabled.
        bool is_keepalive_enabled() const override
        {
            return proto_context.is_keepalive_enabled();
        }

        // Disable keepalive for rest of session, but fetch
        // the keepalive parameters (in seconds).
        // Also, allow the management layer to override parameters.
        void disable_keepalive(unsigned int &keepalive_ping,
                               unsigned int &keepalive_timeout) override
        {
            proto_context.disable_keepalive(keepalive_ping, keepalive_timeout);
            if (ManLink::send)
                ManLink::send->keepalive_override(keepalive_ping, keepalive_timeout);
        }

        // override the data channel factory
        void override_dc_factory(const CryptoDCFactory::Ptr &dc_factory) override
        {
            proto_context.dc_settings().set_factory(dc_factory);
        }

        virtual ~Session()
        {
            // fatal error if destructor called while Session is active
            if (defined_())
                std::abort();
        }

      private:
        Session(openvpn_io::io_context &io_context_arg,
                const Factory &factory,
                ManClientInstance::Factory::Ptr man_factory_arg,
                TunClientInstance::Factory::Ptr tun_factory_arg)
            : proto_context(this, factory.clone_proto_config(), factory.stats),
              housekeeping_timer(io_context_arg),
              disconnect_at(Time::infinite()),
              stats(factory.stats),
              man_factory(std::move(man_factory_arg)),
              tun_factory(std::move(tun_factory_arg))
        {
        }

        bool supports_epoch_data() override
        {
            /* TODO: currently all server implementations do not implement this feature in their data channel */
            return false;
        }

        bool defined_() const
        {
            return !halt && TransportLink::send;
        }

        // proto base class calls here for control channel network sends
        void control_net_send(const Buffer &net_buf) override
        {
            OPENVPN_LOG_SERVPROTO(instance_name() << " : Transport SEND[" << net_buf.size() << "] " << client_endpoint_render() << ' ' << proto_context.dump_packet(net_buf));
            if (TransportLink::send)
            {
                if (TransportLink::send->transport_send_const(net_buf))
                    proto_context.update_last_sent();
            }
        }

        // Called on server with credentials and peer info provided by client.
        // Should be overriden by derived class if credentials are required.
        void server_auth(const std::string &username,
                         const SafeString &password,
                         const std::string &peer_info,
                         const AuthCert::Ptr &auth_cert) override
        {
            constexpr size_t MAX_USERNAME_SIZE = 256;
            constexpr size_t MAX_PASSWORD_SIZE = 16384;

            if (get_management())
            {
                AuthCreds::Ptr auth_creds(new AuthCreds(Unicode::utf8_printable(username, MAX_USERNAME_SIZE | Unicode::UTF8_FILTER),
                                                        Unicode::utf8_printable(password, MAX_PASSWORD_SIZE | Unicode::UTF8_FILTER | Unicode::UTF8_PASS_FMT),
                                                        Unicode::utf8_printable(peer_info, Unicode::UTF8_FILTER | Unicode::UTF8_PASS_FMT)));
                proto_request_push = ProtoContext::IvProtoHelper(auth_creds->peer_info).client_supports_request_push();
                ManLink::send->auth_request(auth_creds, auth_cert, peer_addr);
            }
        }

        // proto base class calls here for app-level control-channel messages received
        void control_recv(BufferPtr &&app_bp) override
        {
            const std::string msg = ProtoContext::template read_control_string<std::string>(*app_bp);
            if (!Unicode::is_valid_utf8(msg, Unicode::UTF8_NO_CTRL))
            {
                /* if we received invalid data from a client on the control channel terminate the connection */
                const auto reason = "Control channel message with invalid characters received";
                auth_failed(reason, reason);
                return;
            }

            if (msg == "PUSH_REQUEST")
            {
                if (get_management())
                    ManLink::send->push_request(proto_context.conf_ptr());
                else
                    auth_failed("no management provider", "");
            }
            else if (msg == "EXIT")
            {
                OPENVPN_LOG("Client disconnecting from server, EXIT received");
                disconnect_type = DT_HALT_RESTART;
                disconnect_in(Time::Duration::seconds(1));
            }
            else if (string::starts_with(msg, "ACC,"))
            {
                if (get_management())
                    ManLink::send->app_control(msg);
            }
            else
            {
                OPENVPN_LOG(instance_name() << " : Unrecognized client request: " << msg);
            }
        }

        void active(bool primary) override
        {
            if (proto_request_push && get_management())
                ManLink::send->push_request(proto_context.conf_ptr());
        }

        void auth_failed(const std::string &reason,
                         const std::string &client_reason) override
        {
            push_halt_restart_msg(HaltRestart::AUTH_FAILED, reason, client_reason);
        }

        void relay(const IP::Addr &target, const int port) override
        {
            if (halt || disconnect_type == DT_HALT_RESTART)
                return;

            proto_context.update_now();

            if (TunLink::send && (disconnect_type < DT_RELAY_TRANSITION))
            {
                disconnect_type = DT_RELAY_TRANSITION;
                TunLink::send->relay(target, port);
                disconnect_in(Time::Duration::seconds(10)); // not a real disconnect, just complete transition to relay
            }

            if (proto_context.primary_defined())
            {
                auto buf = BufferAllocatedRc::Create(64);
                buf_append_string(*buf, "RELAY");
                buf->null_terminate();
                proto_context.control_send(std::move(buf));
                proto_context.flush(true);
            }

            set_housekeeping_timer();
        }

        void push_reply(std::vector<BufferPtr> &&push_msgs) override
        {
            if (halt || (disconnect_type >= DT_RELAY_TRANSITION) || !proto_context.primary_defined())
                return;

            if (disconnect_type == DT_AUTH_PENDING)
            {
                disconnect_type = DT_NONE;
                cancel_disconnect();
            }

            proto_context.update_now();

            if (get_tun())
            {
                proto_context.init_data_channel();
                for (auto &msg : push_msgs)
                {
                    msg->null_terminate();
                    proto_context.control_send(std::move(msg));
                }
                proto_context.flush(true);
                set_housekeeping_timer();
            }
            else
            {
                auth_failed("no tun provider", "");
            }
        }

        TunClientInstance::NativeHandle tun_native_handle() override
        {
            if (get_tun())
                return TunLink::send->tun_native_handle();
            else
                return TunClientInstance::NativeHandle();
        }

        void push_halt_restart_msg(const HaltRestart::Type type,
                                   const std::string &reason,
                                   const std::string &client_reason) override
        {
            if (halt || disconnect_type == DT_HALT_RESTART)
                return;

            proto_context.update_now();

            auto buf = BufferAllocatedRc::Create(128, BufAllocFlags::GROW);
            BufferStreamOut os(*buf);

            std::string ts;

            switch (type)
            {
            case HaltRestart::HALT:
                ts = "HALT";
                os << "HALT,";
                if (!client_reason.empty())
                    os << client_reason;
                else
                    os << "client was disconnected from server";
                disconnect_type = DT_HALT_RESTART;
                disconnect_in(Time::Duration::seconds(1));
                preserve_session_id = false;
                break;
            case HaltRestart::RESTART:
                ts = "RESTART";
                os << "RESTART,";
                if (!client_reason.empty())
                    os << client_reason;
                else
                    os << "server requested a client reconnect";
                disconnect_type = DT_HALT_RESTART;
                disconnect_in(Time::Duration::seconds(1));
                preserve_session_id = false;
                break;
            case HaltRestart::RESTART_PASSIVE:
                ts = "RESTART_PASSIVE";
                os << "RESTART,[P]:";
                if (!client_reason.empty())
                    os << client_reason;
                else
                    os << "server requested a client reconnect";
                break;
            case HaltRestart::RESTART_PSID:
                ts = "RESTART_PSID";
                os << "RESTART,[P]:";
                if (!client_reason.empty())
                    os << client_reason;
                else
                    os << "server requested a client reconnect";
                disconnect_type = DT_HALT_RESTART;
                disconnect_in(Time::Duration::seconds(1));
                break;
            case HaltRestart::AUTH_FAILED:
                ts = "AUTH_FAILED";
                os << ts;
                if (!client_reason.empty())
                    os << ',' << client_reason;
                disconnect_type = DT_HALT_RESTART;
                disconnect_in(Time::Duration::seconds(1));
                preserve_session_id = false;
                break;
            case HaltRestart::RAW:
                {
                    const size_t pos = reason.find_first_of(',');
                    if (pos != std::string::npos)
                        ts = reason.substr(0, pos);
                    else
                        ts = reason;
                    os << reason;
                    disconnect_type = DT_HALT_RESTART;
                    disconnect_in(Time::Duration::seconds(1));
                    preserve_session_id = false;
                    break;
                }
            }

            OPENVPN_LOG(instance_name() << " : Disconnect: " << ts << ' ' << reason);

            if (proto_context.primary_defined())
            {
                buf->null_terminate();
                proto_context.control_send(std::move(buf));
                proto_context.flush(true);
            }

            set_housekeeping_timer();
        }

        void schedule_disconnect(const unsigned int seconds) override
        {
            if (halt || disconnect_type == DT_HALT_RESTART)
                return;
            proto_context.update_now();
            disconnect_in(Time::Duration::seconds(seconds));
            set_housekeeping_timer();
        }

        void schedule_auth_pending_timeout(const unsigned int seconds) override
        {
            if (halt || (disconnect_type >= DT_RELAY_TRANSITION) || !seconds)
                return;
            proto_context.update_now();
            disconnect_type = DT_AUTH_PENDING;
            disconnect_in(Time::Duration::seconds(seconds));
            set_housekeeping_timer();
        }

        void post_cc_msg(BufferPtr &&msg) override
        {
            if (halt || !proto_context.primary_defined())
                return;

            proto_context.update_now();
            msg->null_terminate();
            proto_context.control_send(std::move(msg));
            proto_context.flush(true);
            set_housekeeping_timer();
        }

        void stats_notify(const PeerStats &ps, const bool final) override
        {
            if (ManLink::send)
                ManLink::send->stats_notify(ps, final);
        }

        void float_notify(const PeerAddr::Ptr &addr) override
        {
            if (ManLink::send)
                ManLink::send->float_notify(addr);
        }

        void ipma_notify(const struct ovpn_tun_head_ipma &ipma) override
        {
            if (ManLink::send)
                ManLink::send->ipma_notify(ipma);
        }

        void data_limit_notify(const int key_id,
                               const DataLimit::Mode cdl_mode,
                               const DataLimit::State cdl_status) override
        {
            proto_context.update_now();
            proto_context.data_limit_notify(key_id, cdl_mode, cdl_status);
            proto_context.flush(true);
            set_housekeeping_timer();
        }

        bool get_management()
        {
            if (halt)
                OPENVPN_LOG("Debug: ServerProto: get_management() called with halt=true ManLink::send=" << bool(ManLink::send) << " man_factory=" << bool(man_factory));
            if (!ManLink::send)
            {
                if (man_factory && !halt)
                    ManLink::send = man_factory->new_man_obj(this);
            }
            return bool(ManLink::send);
        }

        bool get_tun()
        {
            if (halt)
                OPENVPN_LOG("Debug: ServerProto: get_tun() called with halt=true TunLink::send=" << bool(TunLink::send) << " tun_factory=" << bool(tun_factory));
            if (!TunLink::send)
            {
                if (tun_factory && !halt)
                    TunLink::send = tun_factory->new_tun_obj(this);
            }
            return bool(TunLink::send);
        }

        // caller must ensure that update_now() was called before
        // and set_housekeeping_timer() called after this method
        void disconnect_in(const Time::Duration &dur)
        {
            disconnect_at = proto_context.now() + dur;
        }

        void cancel_disconnect()
        {
            disconnect_at = Time::infinite();
        }

        void housekeeping_callback(const openvpn_io::error_code &e)
        {
            try
            {
                if (!e && !halt)
                {
                    // update current time
                    proto_context.update_now();

                    housekeeping_schedule.reset();
                    proto_context.housekeeping();
                    if (proto_context.invalidated())
                        invalidation_error(proto_context.invalidation_reason());
                    else if (proto_context.now() >= disconnect_at)
                    {
                        switch (disconnect_type)
                        {
                        case DT_HALT_RESTART:
                            error("disconnect triggered");
                            break;
                        case DT_RELAY_TRANSITION:
                            proto_context.pre_destroy();
                            break;
                        case DT_AUTH_PENDING:
                            auth_failed("Auth Pending Timeout", "Auth Pending Timeout");
                            break;
                        default:
                            error("unknown disconnect");
                            break;
                        }
                    }
                    else
                        set_housekeeping_timer();
                }
            }
            catch (const std::exception &exc)
            {
                error(exc);
            }
        }

        void set_housekeeping_timer()
        {
            Time next = proto_context.next_housekeeping();
            next.min(disconnect_at);
            if (!housekeeping_schedule.similar(next))
            {
                if (!next.is_infinite())
                {
                    next.max(proto_context.now());
                    housekeeping_schedule.reset(next);
                    housekeeping_timer.expires_at(next);
                    housekeeping_timer.async_wait([self = Ptr(this)](const openvpn_io::error_code &error)
                                                  { self->housekeeping_callback(error); });
                }
                else
                {
                    housekeeping_timer.cancel();
                    housekeeping_schedule.reset();
                }
            }
        }

        std::string client_endpoint_render()
        {
            if (TransportLink::send)
                return TransportLink::send->transport_info();
            else
                return "";
        }

        void error(const std::string &error)
        {
            OPENVPN_LOG(instance_name() << " : ServerProto: " << error);
            stop();
        }

        void error(const std::exception &e)
        {
            error(e.what());
        }

        void error()
        {
            stop();
        }

        void invalidation_error(const Error::Type err)
        {
            switch (err)
            {
            case Error::KEV_NEGOTIATE_ERROR:
            case Error::KEEPALIVE_TIMEOUT:
                error();
                break;
            default:
                error(std::string("Session invalidated: ") + Error::name(err));
                break;
            }
        }

        std::string instance_name() const
        {
            if (ManLink::send)
                return ManLink::send->instance_name();
            else
                return "UNNAMED_CLIENT";
        }

        // higher values are higher priority
        enum DisconnectType
        {
            DT_NONE = 0,
            DT_AUTH_PENDING,
            DT_RELAY_TRANSITION,
            DT_HALT_RESTART,
        };

        ProtoContext proto_context;
        int disconnect_type = DT_NONE;
        bool preserve_session_id = true;

        bool halt = false;

        PeerAddr::Ptr peer_addr;

        CoarseTime housekeeping_schedule;
        AsioTimer housekeeping_timer;

        Time disconnect_at;

        SessionStats::Ptr stats;

        ManClientInstance::Factory::Ptr man_factory;
        TunClientInstance::Factory::Ptr tun_factory;

        bool proto_request_push = false;
    };
};

inline TransportClientInstance::Recv::Ptr ServerProto::Factory::new_client_instance()
{
    return new Session(io_context, *this, man_factory, tun_factory);
}
} // namespace openvpn

#endif
