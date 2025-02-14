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

// This file describes the basic set of OpenVPN client events, including the
// normal events leading up to a connection as well as error events.

#ifndef OPENVPN_CLIENT_CLIEVENT_H
#define OPENVPN_CLIENT_CLIEVENT_H

#include <sstream>
#include <deque>
#include <utility>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/transport/protocol.hpp>

#ifdef HAVE_JSON
#include <openvpn/common/jsonhelper.hpp>
#endif

namespace openvpn::ClientEvent {
enum Type
{
    // normal events including disconnected, connected, and other transitional events
    DISCONNECTED = 0,
    CONNECTED,
    RECONNECTING,
    AUTH_PENDING,
    RESOLVE,
    WAIT,
    WAIT_PROXY,
    CONNECTING,
    GET_CONFIG,
    ASSIGN_IP,
    ADD_ROUTES,
    ECHO_OPT,
    INFO,
    CUSTOM_CONTROL,
#ifdef HAVE_JSON
    INFO_JSON,
#endif
    WARN,
    PAUSE,
    RESUME,
    RELAY,
    COMPRESSION_ENABLED,
    UNSUPPORTED_FEATURE,

    // start of nonfatal errors, must be marked by NONFATAL_ERROR_START below
    TRANSPORT_ERROR,
    TUN_ERROR,
    CLIENT_RESTART,

    // start of errors, must be marked by FATAL_ERROR_START below
    AUTH_FAILED,
    CERT_VERIFY_FAIL,
    TLS_VERSION_MIN,
    TLS_ALERT_PROTOCOL_VERSION,
    TLS_ALERT_UNKNOWN_CA,
    TLS_ALERT_MISC,
    TLS_ALERT_HANDSHAKE_FAILURE,
    TLS_ALERT_CERTIFICATE_EXPIRED,
    TLS_ALERT_CERTIFICATE_REVOKED,
    TLS_ALERT_BAD_CERTIFICATE,
    TLS_ALERT_UNSUPPORTED_CERTIFICATE,
    TLS_SIGALG_DISALLOWED_OR_UNSUPPORTED,
    CLIENT_HALT,
    CLIENT_SETUP,
    TUN_HALT,
    CONNECTION_TIMEOUT,
    INACTIVE_TIMEOUT,
    DYNAMIC_CHALLENGE,
    PROXY_NEED_CREDS,
    PROXY_ERROR,
    TUN_SETUP_FAILED,
    TUN_IFACE_CREATE,
    TUN_IFACE_DISABLED,
    EPKI_ERROR,         // EPKI refers to External PKI errors, i.e. errors in accessing external
    EPKI_INVALID_ALIAS, //    certificates or keys.
    RELAY_ERROR,
    COMPRESS_ERROR,
    NTLM_MISSING_CRYPTO,
    SESSION_EXPIRED,
    NEED_CREDS,

    N_TYPES
};

enum
{
    NONFATAL_ERROR_START = TRANSPORT_ERROR, // start of nonfatal errors that automatically reconnect
    FATAL_ERROR_START = AUTH_FAILED,        // start of fatal errors
};

inline const char *event_name(const Type type)
{
    static const char *names[] = {
        "DISCONNECTED",
        "CONNECTED",
        "RECONNECTING",
        "AUTH_PENDING",
        "RESOLVE",
        "WAIT",
        "WAIT_PROXY",
        "CONNECTING",
        "GET_CONFIG",
        "ASSIGN_IP",
        "ADD_ROUTES",
        "ECHO",
        "INFO",
        "CUSTOM_CONTROL",
#ifdef HAVE_JSON
        "INFO_JSON",
#endif
        "WARN",
        "PAUSE",
        "RESUME",
        "RELAY",
        "COMPRESSION_ENABLED",
        "UNSUPPORTED_FEATURE",

        // nonfatal errors
        "TRANSPORT_ERROR",
        "TUN_ERROR",
        "CLIENT_RESTART",

        // fatal errors
        "AUTH_FAILED",
        "CERT_VERIFY_FAIL",
        "TLS_VERSION_MIN",
        "TLS_ALERT_PROTOCOL_VERSION",
        "TLS_ALERT_UNKNOWN_CA",
        "TLS_ALERT_MISC",
        "TLS_ALERT_HANDSHAKE_FAILURE",
        "TLS_ALERT_CERTIFICATE_EXPIRED",
        "TLS_ALERT_CERTIFICATE_REVOKED",
        "TLS_ALERT_BAD_CERTIFICATE",
        "TLS_ALERT_UNSUPPORTED_CERTIFICATE",
        "TLS_SIGALG_DISALLOWED_OR_UNSUPPORTED",
        "CLIENT_HALT",
        "CLIENT_SETUP",
        "TUN_HALT",
        "CONNECTION_TIMEOUT",
        "INACTIVE_TIMEOUT",
        "DYNAMIC_CHALLENGE",
        "PROXY_NEED_CREDS",
        "PROXY_ERROR",
        "TUN_SETUP_FAILED",
        "TUN_IFACE_CREATE",
        "TUN_IFACE_DISABLED",
        "EPKI_ERROR",
        "EPKI_INVALID_ALIAS",
        "RELAY_ERROR",
        "COMPRESS_ERROR",
        "NTLM_MISSING_CRYPTO",
        "SESSION_EXPIRED",
        "NEED_CREDS"};

    static_assert(N_TYPES == array_size(names), "event names array inconsistency");
    if (type < N_TYPES)
        return names[type];
    else
        return "UNKNOWN_EVENT_TYPE";
}

struct Connected;

// The base class for all events.
class Base : public RC<thread_safe_refcount>
{
  public:
    typedef RCPtr<Base> Ptr;
    virtual ~Base() = default;
    Base(Type id)
        : id_(id)
    {
    }

    Type id() const
    {
        return id_;
    }

    const char *name() const
    {
        return event_name(id_);
    }

    bool is_error() const
    {
        return int(id_) >= NONFATAL_ERROR_START;
    }

    bool is_fatal() const
    {
        return int(id_) >= FATAL_ERROR_START;
    }

    virtual std::string render() const
    {
        return "";
    }

    const Connected *connected_cast() const
    {
        if (id_ == CONNECTED)
            return (const Connected *)this;
        else
            return nullptr;
    }

  private:
    Type id_;
};

// Specific client events.  Some events have no additional data attached to them,
// while other events (such as Connected) have many additional data fields.

struct Resolve : public Base
{
    Resolve()
        : Base(RESOLVE)
    {
    }
};

struct Wait : public Base
{
    Wait()
        : Base(WAIT)
    {
    }
};

struct WaitProxy : public Base
{
    WaitProxy()
        : Base(WAIT_PROXY)
    {
    }
};

struct Connecting : public Base
{
    Connecting()
        : Base(CONNECTING)
    {
    }
};

struct Reconnecting : public Base
{
    Reconnecting()
        : Base(RECONNECTING)
    {
    }
};

struct GetConfig : public Base
{
    GetConfig()
        : Base(GET_CONFIG)
    {
    }
};

struct AssignIP : public Base
{
    AssignIP()
        : Base(ASSIGN_IP)
    {
    }
};

struct AddRoutes : public Base
{
    AddRoutes()
        : Base(ADD_ROUTES)
    {
    }
};

struct Resume : public Base
{
    Resume()
        : Base(RESUME)
    {
    }
};

struct Relay : public Base
{
    Relay()
        : Base(RELAY)
    {
    }
};

struct Disconnected : public Base
{
    Disconnected()
        : Base(DISCONNECTED)
    {
    }
};

struct ConnectionTimeout : public Base
{
    ConnectionTimeout()
        : Base(CONNECTION_TIMEOUT)
    {
    }
};

struct InactiveTimeout : public Base
{
    InactiveTimeout()
        : Base(INACTIVE_TIMEOUT)
    {
    }
};

struct TLSMinVersion : public Base
{
    TLSMinVersion()
        : Base(TLS_VERSION_MIN)
    {
    }
};

struct TLSVersionMinFail : public Base
{
    TLSVersionMinFail()
        : Base(TLS_VERSION_MIN)
    {
    }
};

struct TLSAlertProtocolVersion : public Base
{
    TLSAlertProtocolVersion()
        : Base(TLS_ALERT_PROTOCOL_VERSION)
    {
    }
};

struct TLSAlertHandshakeFailure : public Base
{
    TLSAlertHandshakeFailure()
        : Base(TLS_ALERT_HANDSHAKE_FAILURE)
    {
    }
};

struct TLSAlertCertificateExpire : public Base
{
    TLSAlertCertificateExpire()
        : Base(TLS_ALERT_CERTIFICATE_EXPIRED)
    {
    }
};

struct TLSAlertCertificateRevoked : public Base
{
    TLSAlertCertificateRevoked()
        : Base(TLS_ALERT_CERTIFICATE_REVOKED)
    {
    }
};

struct TLSAlertBadCertificate : public Base
{
    TLSAlertBadCertificate()
        : Base(TLS_ALERT_BAD_CERTIFICATE)
    {
    }
};

struct TLSAlertUnsupportedCertificate : public Base
{
    TLSAlertUnsupportedCertificate()
        : Base(TLS_ALERT_UNSUPPORTED_CERTIFICATE)
    {
    }
};

struct TLSSigAlgDisallowedOrUnsupported : public Base
{
    TLSSigAlgDisallowedOrUnsupported()
        : Base(TLS_SIGALG_DISALLOWED_OR_UNSUPPORTED)
    {
    }
};

struct TLSAlertProtocolUnknownCA : public Base
{
    TLSAlertProtocolUnknownCA()
        : Base(TLS_ALERT_UNKNOWN_CA)
    {
    }
};



#ifdef HAVE_JSON

struct InfoJSON : public Base
{
    typedef RCPtr<InfoJSON> Ptr;

    InfoJSON(std::string msg_type_arg,
             Json::Value jdata_arg)
        : Base(INFO_JSON),
          msg_type(std::move(msg_type_arg)),
          jdata(std::move(jdata_arg))
    {
    }

    virtual std::string render() const
    {
        BufferAllocated buf(512, BufAllocFlags::GROW);
        buf_append_string(buf, msg_type);
        buf_append_string(buf, ":");
        json::format_compact(jdata, buf);
        return buf_to_string(buf);
    }

    std::string msg_type;
    Json::Value jdata;
};

#endif

struct UnsupportedFeature : public Base
{
    typedef RCPtr<UnsupportedFeature> Ptr;

    UnsupportedFeature(const std::string &name_arg, const std::string &reason_arg, bool critical_arg)
        : Base(UNSUPPORTED_FEATURE),
          name(name_arg),
          reason(reason_arg),
          critical(critical_arg)
    {
    }

    std::string name;
    std::string reason;
    bool critical;

    std::string render() const override
    {
        std::ostringstream out;
        out << "name: " << name << ", reason: " << reason << ", critical: " << critical;
        return out.str();
    }
};

struct Connected : public Base
{
    typedef RCPtr<Connected> Ptr;

    Connected()
        : Base(CONNECTED)
    {
    }

    std::string user;
    std::string server_host;
    std::string server_port;
    std::string server_proto;
    std::string server_ip;
    std::string vpn_ip4;
    std::string vpn_ip6;
    std::string vpn_gw4;
    std::string vpn_gw6;
    std::string vpn_mtu;
    std::string client_ip;
    std::string tun_name;

    std::string render() const override
    {
        std::ostringstream out;
        // eg. "godot@foo.bar.gov:443 (1.2.3.4) via TCPv4 on tun0/5.5.1.1"
        if (!user.empty())
            out << user << '@';
        if (server_host.find_first_of(':') == std::string::npos)
            out << server_host;
        else
            out << '[' << server_host << ']';
        out << ':' << server_port
            << " (" << server_ip << ") via " << client_ip << '/' << server_proto
            << " on " << tun_name << '/' << vpn_ip4 << '/' << vpn_ip6
            << " gw=[" << vpn_gw4 << '/' << vpn_gw6 << ']'
            << " mtu=" << vpn_mtu;
        return out.str();
    }
};

struct NeedCreds : public Base
{
    NeedCreds()
        : Base(NEED_CREDS)
    {
    }
};

struct ReasonBase : public Base
{
    ReasonBase(const Type id, const std::string &reason_arg)
        : Base(id),
          reason(reason_arg)
    {
    }

    ReasonBase(const Type id, std::string &&reason_arg)
        : Base(id),
          reason(std::move(reason_arg))
    {
    }

    std::string render() const override
    {
        return reason;
    }

    std::string reason;
};

/* thrown if no other of the TLSAlert* events are matching */
struct TLSAlertMisc : public ReasonBase
{
    TLSAlertMisc(std::string reason)
        : ReasonBase(TLS_ALERT_MISC, std::move(reason))
    {
    }
};

struct AuthFailed : public ReasonBase
{
    AuthFailed(std::string reason)
        : ReasonBase(AUTH_FAILED, std::move(reason))
    {
    }
};

struct SessionExpired : public ReasonBase
{
    SessionExpired(std::string reason)
        : ReasonBase(SESSION_EXPIRED, std::move(reason))
    {
    }
};

struct CertVerifyFail : public ReasonBase
{
    CertVerifyFail(std::string reason)
        : ReasonBase(CERT_VERIFY_FAIL, std::move(reason))
    {
    }
};

struct ClientHalt : public ReasonBase
{
    ClientHalt(std::string reason)
        : ReasonBase(CLIENT_HALT, std::move(reason))
    {
    }
};

struct ClientRestart : public ReasonBase
{
    ClientRestart(std::string reason)
        : ReasonBase(CLIENT_RESTART, std::move(reason))
    {
    }
};

struct TunHalt : public ReasonBase
{
    TunHalt(std::string reason)
        : ReasonBase(TUN_HALT, std::move(reason))
    {
    }
};

struct RelayError : public ReasonBase
{
    RelayError(std::string reason)
        : ReasonBase(RELAY_ERROR, std::move(reason))
    {
    }
};

struct CompressError : public ReasonBase
{
    CompressError(std::string reason)
        : ReasonBase(COMPRESS_ERROR, std::move(reason))
    {
    }
};

struct DynamicChallenge : public ReasonBase
{
    DynamicChallenge(std::string reason)
        : ReasonBase(DYNAMIC_CHALLENGE, std::move(reason))
    {
    }
};

struct Pause : public ReasonBase
{
    Pause(std::string reason)
        : ReasonBase(PAUSE, std::move(reason))
    {
    }
};

struct ProxyError : public ReasonBase
{
    ProxyError(std::string reason)
        : ReasonBase(PROXY_ERROR, std::move(reason))
    {
    }
};

struct NtlmMissingCryptoError : public ReasonBase
{
    NtlmMissingCryptoError(std::string reason)
        : ReasonBase(NTLM_MISSING_CRYPTO, std::move(reason))
    {
    }
};


struct ProxyNeedCreds : public ReasonBase
{
    ProxyNeedCreds(std::string reason)
        : ReasonBase(PROXY_NEED_CREDS, std::move(reason))
    {
    }
};

struct TransportError : public ReasonBase
{
    TransportError(std::string reason)
        : ReasonBase(TRANSPORT_ERROR, std::move(reason))
    {
    }
};

struct TunSetupFailed : public ReasonBase
{
    TunSetupFailed(std::string reason)
        : ReasonBase(TUN_SETUP_FAILED, std::move(reason))
    {
    }
};

struct TunIfaceCreate : public ReasonBase
{
    TunIfaceCreate(std::string reason)
        : ReasonBase(TUN_IFACE_CREATE, std::move(reason))
    {
    }
};

struct TunIfaceDisabled : public ReasonBase
{
    TunIfaceDisabled(std::string reason)
        : ReasonBase(TUN_IFACE_DISABLED, std::move(reason))
    {
    }
};

struct TunError : public ReasonBase
{
    TunError(std::string reason)
        : ReasonBase(TUN_ERROR, std::move(reason))
    {
    }
};

struct EpkiError : public ReasonBase
{
    EpkiError(std::string reason)
        : ReasonBase(EPKI_ERROR, std::move(reason))
    {
    }
};

struct EpkiInvalidAlias : public ReasonBase
{
    EpkiInvalidAlias(std::string reason)
        : ReasonBase(EPKI_INVALID_ALIAS, std::move(reason))
    {
    }
};

struct Echo : public ReasonBase
{
    Echo(std::string value)
        : ReasonBase(ECHO_OPT, std::move(value))
    {
    }
};

struct Info : public ReasonBase
{
    Info(std::string value)
        : ReasonBase(INFO, std::move(value))
    {
    }
};

/**
 * Message to signal a custom app control message from the peer
 */
struct AppCustomControlMessage : public Base
{
    AppCustomControlMessage(std::string protocol, std::string message)
        : Base(CUSTOM_CONTROL), protocol(std::move(protocol)), custommessage(std::move(message))
    {
    }
    std::string protocol;
    std::string custommessage;
};

struct AuthPending : public ReasonBase
{
    int timeout;
    AuthPending(int timeout, std::string value)
        : ReasonBase(AUTH_PENDING, std::move(value)), timeout(timeout)
    {
    }
};

struct Warn : public ReasonBase
{
    Warn(std::string value)
        : ReasonBase(WARN, std::move(value))
    {
    }
};

class ClientSetup : public ReasonBase
{
  public:
    ClientSetup(const std::string &status, const std::string &message)
        : ReasonBase(CLIENT_SETUP, make(status, message))
    {
    }

  private:
    static std::string make(const std::string &status, const std::string &message)
    {
        std::string ret;
        ret += status;
        if (!status.empty() && !message.empty())
            ret += ": ";
        ret += message;
        return ret;
    }
};

struct CompressionEnabled : public ReasonBase
{
    CompressionEnabled(std::string msg)
        : ReasonBase(COMPRESSION_ENABLED, std::move(msg))
    {
    }
};

class Queue : public RC<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<Queue> Ptr;

    virtual void add_event(Base::Ptr event) = 0;
};
} // namespace openvpn::ClientEvent

#endif // OPENVPN_CLIENT_CLIEVENT_H
