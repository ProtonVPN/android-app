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

// ProtoContext, the fundamental OpenVPN protocol implementation.
// It can be used by OpenVPN clients, servers, or unit tests.

#ifndef OPENVPN_SSL_PROTO_H
#define OPENVPN_SSL_PROTO_H

#include <cstring>
#include <string>
#include <sstream>
#include <algorithm> // for std::min
#include <cstdint>   // for std::uint32_t, etc.
#include <memory>
#include <optional>


#include <openvpn/common/clamp_typerange.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/size.hpp>
#include <openvpn/common/version.hpp>
#include <openvpn/common/platform_name.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/common/hexstr.hpp>
#include <openvpn/common/options.hpp>
#include <openvpn/common/mode.hpp>
#include <openvpn/common/socktypes.hpp>
#include <openvpn/common/number.hpp>
#include <openvpn/common/likely.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/to_string.hpp>
#include <openvpn/common/numeric_cast.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/buffer/safestr.hpp>
#include <openvpn/buffer/bufcomposed.hpp>
#include <openvpn/ip/ip4.hpp>
#include <openvpn/ip/ip6.hpp>
#include <openvpn/ip/udp.hpp>
#include <openvpn/ip/tcp.hpp>
#include <openvpn/time/time.hpp>
#include <openvpn/time/durhelper.hpp>
#include <openvpn/frame/frame.hpp>
#include <openvpn/random/randapi.hpp>
#include <openvpn/crypto/cryptoalgs.hpp>
#include <openvpn/crypto/cryptodc.hpp>
#include <openvpn/crypto/cipher.hpp>
#include <openvpn/crypto/ovpnhmac.hpp>
#include <openvpn/crypto/tls_crypt.hpp>
#include <openvpn/crypto/tls_crypt_v2.hpp>
#include <openvpn/crypto/packet_id_control.hpp>
#include <openvpn/crypto/static_key.hpp>
#include <openvpn/crypto/bs64_data_limit.hpp>
#include <openvpn/log/sessionstats.hpp>
#include <openvpn/ssl/protostack.hpp>
#include <openvpn/ssl/psid.hpp>
#include <openvpn/ssl/tlsprf.hpp>
#include <openvpn/ssl/datalimit.hpp>
#include <openvpn/ssl/mssparms.hpp>
#include <openvpn/transport/mssfix.hpp>
#include <openvpn/transport/protocol.hpp>
#include <openvpn/transport/client/transbase.hpp>
#include <openvpn/tun/layer.hpp>
#include <openvpn/tun/tunmtu.hpp>
#include <openvpn/compress/compress.hpp>
#include <openvpn/ssl/proto_context_options.hpp>
#include <openvpn/ssl/peerinfo.hpp>
#include <openvpn/crypto/crypto_aead.hpp>
#include <openvpn/ssl/customcontrolchannel.hpp>
#include <openvpn/netconf/hwaddr.hpp>

#ifndef OPENVPN_DEBUG_PROTO
#define OPENVPN_DEBUG_PROTO 1
#endif

/*

ProtoContext -- OpenVPN protocol implementation

Protocol negotiation states:

Client:

1. send client reset to server
2. wait for server reset from server AND ack from 1 (C_WAIT_RESET, C_WAIT_RESET_ACK)
3. start SSL handshake
4. send auth message to server
5. wait for server auth message AND ack from 4 (C_WAIT_AUTH, C_WAIT_AUTH_ACK)
6. go active (ACTIVE)

Server:

1. wait for client reset (S_WAIT_RESET)
2. send server reset to client
3. wait for ACK from 2 (S_WAIT_RESET_ACK)
4. start SSL handshake
5. wait for auth message from client (S_WAIT_AUTH)
6. send auth message to client
7. wait for ACK from 6 (S_WAIT_AUTH_ACK)
8. go active (ACTIVE)

*/

namespace openvpn {

// utility namespace for ProtoContext
namespace proto_context_private {
namespace {
// clang-format off
const unsigned char auth_prefix[] = { 0, 0, 0, 0, 2 }; // CONST GLOBAL

const unsigned char keepalive_message[] = {    // CONST GLOBAL
    0x2a, 0x18, 0x7b, 0xf3, 0x64, 0x1e, 0xb4, 0xcb,
    0x07, 0xed, 0x2d, 0x0a, 0x98, 0x1f, 0xc7, 0x48
};

enum
{
    KEEPALIVE_FIRST_BYTE = 0x2a // first byte of keepalive message
};

inline bool is_keepalive(const Buffer &buf)
{
    return buf.size() >= sizeof(keepalive_message)
           && buf[0] == KEEPALIVE_FIRST_BYTE
           && !std::memcmp(keepalive_message, buf.c_data(), sizeof(keepalive_message));
}

const unsigned char explicit_exit_notify_message[] = {    // CONST GLOBAL
    0x28, 0x7f, 0x34, 0x6b, 0xd4, 0xef, 0x7a, 0x81,
    0x2d, 0x56, 0xb8, 0xd3, 0xaf, 0xc5, 0x45, 0x9c,
    6 // OCC_EXIT
};
// clang-format on

enum
{
    EXPLICIT_EXIT_NOTIFY_FIRST_BYTE = 0x28 // first byte of exit message
};
} // namespace
} // namespace proto_context_private

class ProtoContextCallbackInterface
{
  public:
    virtual ~ProtoContextCallbackInterface() = default;

    /**
     * Sends out bytes to the network.
     */
    virtual void control_net_send(const Buffer &net_buf) = 0;

    /*
     * Receive as packet from the network
     * \note app may take ownership of app_bp via std::move
     */
    virtual void control_recv(BufferPtr &&app_bp) = 0;

    /** Called on client to request username/password credentials.
     * Should be overridden by derived class if credentials are required.
     * username and password should be written into buf with write_auth_string().
     */
    virtual void client_auth(Buffer &buf)
    {
        write_empty_string(buf); // username
        write_empty_string(buf); // password
    }

    /** Called on server with credentials and peer info provided by client.
     *Should be overriden by derived class if credentials are required. */
    virtual void server_auth(const std::string &username,
                             const SafeString &password,
                             const std::string &peer_info,
                             const AuthCert::Ptr &auth_cert)
    {
    }

    /**
     * Writes an empty user or password string for the key-method 2 packet in the OpenVPN protocol
     * @param buf  buffer to write to
     */
    static void write_empty_string(Buffer &buf)
    {
        uint8_t empty[]{0x00, 0x00}; // empty length field without content
        buf.write(&empty, 2);
    }

    /** the protocol context needs to know if the parent and its tun/transport layer are able to
     * support epoch key data format to properly handshake this protocol feature
     */
    virtual bool supports_epoch_data() = 0;

    //! Called when KeyContext transitions to ACTIVE state
    virtual void active(bool primary) = 0;
};

class ProtoContext : public logging::LoggingMixin<OPENVPN_DEBUG_PROTO,
                                                  logging::LOG_LEVEL_VERB,
                                                  ProtoContext>
{
#ifdef UNIT_TEST
  public:
#else
  protected:
#endif
    static constexpr size_t APP_MSG_MAX = 65536;

    enum
    {
        // packet opcode (high 5 bits) and key-id (low 3 bits) are combined in one byte
        KEY_ID_MASK = 0x07,
        OPCODE_SHIFT = 3,

        // packet opcodes -- the V1 is intended to allow protocol changes in the future
        // CONTROL_HARD_RESET_CLIENT_V1 = 1,   // (obsolete) initial key from client, forget previous state
        // CONTROL_HARD_RESET_SERVER_V1 = 2,   // (obsolete) initial key from server, forget previous state
        CONTROL_SOFT_RESET_V1 = 3, // new key, graceful transition from old to new key
        CONTROL_V1 = 4,            // control channel packet (usually TLS ciphertext)
        CONTROL_WKC_V1 = 11,       // control channel packet with wrapped client key appended
        ACK_V1 = 5,                // acknowledgement for packets received
        DATA_V1 = 6,               // data channel packet with 1-byte header
        DATA_V2 = 9,               // data channel packet with 4-byte header

        // indicates key_method >= 2
        CONTROL_HARD_RESET_CLIENT_V2 = 7,  // initial key from client, forget previous state
        CONTROL_HARD_RESET_CLIENT_V3 = 10, // initial key from client, forget previous state
        CONTROL_HARD_RESET_SERVER_V2 = 8,  // initial key from server, forget previous state

        INVALID_OPCODE = 0,

        // DATA_V2 constants
        OP_SIZE_V2 = 4,                // size of initial packet opcode
        OP_PEER_ID_UNDEF = 0x00FFFFFF, // indicates that Peer ID is undefined

        // states
        // C_x : client states
        // S_x : server states

        // ACK states -- must be first before other states
        STATE_UNDEF = -1,
        C_WAIT_RESET_ACK = 0,
        C_WAIT_AUTH_ACK = 1,
        S_WAIT_RESET_ACK = 2,
        S_WAIT_AUTH_ACK = 3,
        LAST_ACK_STATE = 3, // all ACK states must be <= this value

        // key negotiation states (client)
        C_INITIAL = 4,
        C_WAIT_RESET = 5, // must be C_INITIAL+1
        C_WAIT_AUTH = 6,

        // key negotiation states (server)
        S_INITIAL = 7,
        S_WAIT_RESET = 8, // must be S_INITIAL+1
        S_WAIT_AUTH = 9,

        // key negotiation states (client and server)
        ACTIVE = 10,
    };

    enum iv_proto_flag : unsigned int
    {
        // See ssl.h in openvpn2 for detailed documentation of IV_PROTO
        //
        // NOTE: Bit field (1 << 0) is reserved for historic reasons
        //       and not expected to be set. Do not use this field.
        //
        IV_PROTO_DATA_V2 = (1 << 1),
        IV_PROTO_REQUEST_PUSH = (1 << 2),
        IV_PROTO_TLS_KEY_EXPORT = (1 << 3),
        IV_PROTO_AUTH_PENDING_KW = (1 << 4),
        IV_PROTO_NCP_P2P = (1 << 5),    // not implemented
        IV_PROTO_DNS_OPTION = (1 << 6), // outdated, don't send
        IV_PROTO_CC_EXIT_NOTIFY = (1 << 7),
        IV_PROTO_AUTH_FAIL_TEMP = (1 << 8),
        IV_PROTO_DYN_TLS_CRYPT = (1 << 9),
        IV_PROTO_DATA_EPOCH = (1 << 10),
        IV_PROTO_DNS_OPTION_V2 = (1 << 11),
        IV_PROTO_PUSH_UPDATE = (1 << 12)
    };

    enum tlv_types : uint16_t
    {
        EARLY_NEG_FLAGS = 0x0001
    };

    enum early_neg_flags : uint16_t
    {
        EARLY_NEG_FLAG_RESEND_WKC = 0x0001
    };

    static unsigned int opcode_extract(const unsigned int op)
    {
        return op >> OPCODE_SHIFT;
    }

    static unsigned int key_id_extract(const unsigned int op)
    {
        return op & KEY_ID_MASK;
    }

    static size_t op_head_size(const unsigned int op)
    {
        return opcode_extract(op) == DATA_V2 ? OP_SIZE_V2 : 1;
    }

    static unsigned char op_compose(const unsigned int opcode, const unsigned int key_id)
    {
        // As long as 'opcode' stays within the range specified by the enum the cast should be safe.
        // TODO: Use a more constrained type for opcode to ensure range violations can't happen.
        return static_cast<unsigned char>((opcode << OPCODE_SHIFT) | key_id);
    }

    static unsigned int op32_compose(const unsigned int opcode,
                                     const unsigned int key_id,
                                     const int op_peer_id)
    {
        return (op_compose(opcode, key_id) << 24) | (op_peer_id & 0x00FFFFFF);
    }

  public:
    OPENVPN_UNTAGGED_EXCEPTION_INHERIT(option_error, proto_error);
    OPENVPN_UNTAGGED_EXCEPTION_INHERIT(option_error, process_server_push_error);
    OPENVPN_UNTAGGED_EXCEPTION_INHERIT(option_error, proto_option_error);

    // configuration data passed to ProtoContext constructor
    class ProtoConfig : public RCCopyable<thread_unsafe_refcount>
    {
      public:
        typedef RCPtr<ProtoConfig> Ptr;

        // master SSL context factory
        SSLFactoryAPI::Ptr ssl_factory;

        // data channel
        CryptoDCSettings dc;

        // TLSPRF factory
        TLSPRFFactory::Ptr tlsprf_factory;

        // master Frame object
        Frame::Ptr frame;

        // (non-smart) pointer to current time
        TimePtr now;

        // Random number generator.
        // Use-cases demand highest cryptographic strength
        // such as key generation.
        StrongRandomAPI::Ptr rng;

        // Pseudo-random number generator.
        // Use-cases demand cryptographic strength
        // combined with high performance.  Used for
        // IV and ProtoSessionID generation.
        RandomAPI::Ptr prng;

        // If relay mode is enabled, connect to a special OpenVPN
        // server that acts as a relay/proxy to a second server.
        bool relay_mode = false;

        // defer data channel initialization until after client options pull
        bool dc_deferred = false;

        // transmit username/password creds to server (client-only)
        bool xmit_creds = true;

        // send client exit notifications via control channel
        bool cc_exit_notify = false;

        // Transport protocol, i.e. UDPv4, etc.
        Protocol protocol; // set with set_protocol()

        // OSI layer
        Layer layer;

        // compressor
        CompressContext comp_ctx;

        // tls_auth/crypt parms
        enum TLSCrypt
        {
            None = 0,
            V1 = (1 << 0),
            V2 = (1 << 1),
            Dynamic = (1 << 2)
        };

        //! leave this undefined to disable tls_auth
        OpenVPNStaticKey tls_auth_key;

        //! leave this undefined to disable tls-crypt/tls-crypt-v2
        OpenVPNStaticKey tls_crypt_key;

        //! needed to distinguish between tls-crypt and tls-crypt-v2 server mode
        unsigned tls_crypt_ = TLSCrypt::None;

        //! do we expect keys to contain a server key ID?
        bool tls_crypt_v2_serverkey_id = false;

        //! server keys location, if tls_crypt_v2_serverkey_id is true
        std::string tls_crypt_v2_serverkey_dir;

        //! leave this undefined to disable tls-crypt-v2 on client
        BufferAllocated wkc;

        OvpnHMACFactory::Ptr tls_auth_factory;
        OvpnHMACContext::Ptr tls_auth_context;
        int key_direction = -1; // 0, 1, or -1 for bidirectional

        TLSCryptFactory::Ptr tls_crypt_factory;
        TLSCryptContext::Ptr tls_crypt_context;

        TLSCryptMetadataFactory::Ptr tls_crypt_metadata_factory;

        // timeout parameters, relative to construction of KeyContext object
        Time::Duration handshake_window; // SSL/TLS negotiation must complete by this time
        Time::Duration become_primary;   // KeyContext (that is ACTIVE) becomes primary at this time
        Time::Duration renegotiate;      // start SSL/TLS renegotiation at this time
        Time::Duration expire;           // KeyContext expires at this time
        Time::Duration tls_timeout;      // Packet retransmit timeout on TLS control channel

        // keepalive parameters
        Time::Duration keepalive_ping;          // ping xmit period
        Time::Duration keepalive_timeout;       // timeout period after primary KeyContext reaches ACTIVE state
        Time::Duration keepalive_timeout_early; // timeout period before primary KeyContext reaches ACTIVE state

        //! extra peer info key/value pairs generated by client app
        PeerInfo::Set::Ptr extra_peer_info;

        // App control config
        AppControlMessageConfig app_control_config;
        AppControlMessageReceiver app_control_recv;
        /** extra peer information that depends on the state of the underlying transport and needs to be initialised
         * after the transport is initialised but before the IV variables are sent */
        PeerInfo::Set::Ptr extra_peer_info_transport;

        /** When the extra_peer_info_transport is being built, we need to remember if it should include the more
         * sensitive information that push-peer-info includes */
        bool extra_peer_info_push_peerinfo = false;

        // op header
        bool enable_op32 = false;
        int remote_peer_id = -1; // -1 to disable
        int local_peer_id = -1;  // -1 to disable

        // MTU
        unsigned int tun_mtu = TUN_MTU_DEFAULT;
        unsigned int tun_mtu_max = TUN_MTU_DEFAULT + 100;
        MSSParms mss_parms;
        unsigned int mss_fix = 0;

        // For compatibility with openvpn2 we send initial options on rekeying,
        // instead of possible modifications caused by NCP
        std::string initial_options;

        bool auth_nocache = false;

        void load(const OptionList &opt,
                  const ProtoContextCompressionOptions &pco,
                  const int default_key_direction,
                  const bool server)
        {
            // first set defaults
            handshake_window = Time::Duration::seconds(60);
            renegotiate = Time::Duration::seconds(3600);
            tls_timeout = Time::Duration::seconds(1);
            keepalive_ping = Time::Duration::seconds(8);
            keepalive_timeout = Time::Duration::seconds(40);
            keepalive_timeout_early = keepalive_timeout;
            comp_ctx = CompressContext(CompressContext::NONE, false);
            protocol = Protocol();
            key_direction = default_key_direction;

            // layer
            {
                const Option *dev = opt.get_ptr("dev-type");
                if (!dev)
                    dev = opt.get_ptr("dev");
                if (!dev)
                    throw proto_option_error(ERR_INVALID_CONFIG, "missing dev-type or dev option");
                const std::string &dev_type = dev->get(1, 64);
                if (string::starts_with(dev_type, "tun"))
                    layer = Layer(Layer::OSI_LAYER_3);
                else if (string::starts_with(dev_type, "tap"))
                    throw proto_option_error(ERR_INVALID_CONFIG, "TAP mode is not supported");
                else
                    throw proto_option_error(ERR_INVALID_OPTION_VAL, "bad dev-type");
            }

            // cipher/digest/tls-auth/tls-crypt
            {
                CryptoAlgs::Type cipher = CryptoAlgs::NONE;
                CryptoAlgs::Type digest = CryptoAlgs::NONE;

                // data channel cipher
                {
                    const Option *o = opt.get_ptr("cipher");
                    if (o)
                    {
                        const std::string &cipher_name = o->get(1, 128);
                        if (cipher_name != "none")
                            cipher = CryptoAlgs::lookup(cipher_name);
                    }
                    else
                        cipher = CryptoAlgs::lookup("BF-CBC");
                }

                // data channel HMAC
                {
                    const Option *o = opt.get_ptr("auth");
                    if (o)
                    {
                        const std::string &auth_name = o->get(1, 128);
                        if (auth_name != "none")
                            digest = CryptoAlgs::lookup(auth_name);
                    }
                    else
                        digest = CryptoAlgs::lookup("SHA1");
                }
                dc.set_cipher(cipher);
                dc.set_digest(digest);

                // tls-auth
                {
                    const Option *o = opt.get_ptr(relay_prefix("tls-auth"));
                    if (o)
                    {
                        if (!server && tls_crypt_context)
                            throw proto_option_error(ERR_INVALID_OPTION_CRYPTO, "tls-auth and tls-crypt are mutually exclusive");

                        tls_auth_key.parse(o->get(1, 0));

                        const Option *tad = opt.get_ptr(relay_prefix("tls-auth-digest"));
                        if (tad)
                            digest = CryptoAlgs::lookup(tad->get(1, 128));
                        if (digest != CryptoAlgs::NONE)
                            set_tls_auth_digest(digest);
                    }
                }

                // tls-crypt
                {
                    const Option *o = opt.get_ptr(relay_prefix("tls-crypt"));
                    if (o)
                    {
                        if (!server && tls_auth_context)
                            throw proto_option_error(ERR_INVALID_OPTION_CRYPTO, "tls-auth and tls-crypt are mutually exclusive");
                        if (tls_crypt_context)
                            throw proto_option_error(ERR_INVALID_OPTION_CRYPTO, "tls-crypt and tls-crypt-v2 are mutually exclusive");

                        tls_crypt_ = TLSCrypt::V1;
                        tls_crypt_key.parse(o->get(1, 0));

                        set_tls_crypt_algs();
                    }
                }

                // tls-crypt-v2
                {
                    const Option *o = opt.get_ptr(relay_prefix("tls-crypt-v2"));
                    if (o)
                    {
                        if (!server && tls_auth_context)
                            throw proto_option_error(ERR_INVALID_OPTION_CRYPTO, "tls-auth and tls-crypt-v2 are mutually exclusive");
                        if (tls_crypt_context)
                            throw proto_option_error(ERR_INVALID_OPTION_CRYPTO, "tls-crypt and tls-crypt-v2 are mutually exclusive");

                        // initialize tls_crypt_context
                        set_tls_crypt_algs();

                        std::string keyfile = o->get(1, 0);

                        if (opt.exists("client"))
                        {
                            // in client mode expect the key to be a PEM encoded tls-crypt-v2 client key (key + WKc)
                            TLSCryptV2ClientKey tls_crypt_v2_key(tls_crypt_context);
                            tls_crypt_v2_key.parse(keyfile);
                            tls_crypt_v2_key.extract_key(tls_crypt_key);
                            tls_crypt_v2_key.extract_wkc(wkc);
                        }
                        else
                        {
                            if (!tls_crypt_v2_serverkey_id)
                            {
                                // in server mode this is a PEM encoded tls-crypt-v2 server key
                                TLSCryptV2ServerKey tls_crypt_v2_key;
                                tls_crypt_v2_key.parse(keyfile);
                                tls_crypt_v2_key.extract_key(tls_crypt_key);
                            }
                        }
                        tls_crypt_ = TLSCrypt::V2;
                    }
                }
            }

            // key-direction
            {
                if (key_direction >= -1 && key_direction <= 1)
                {
                    const Option *o = opt.get_ptr(relay_prefix("key-direction"));
                    if (o)
                    {
                        const std::string &dir = o->get(1, 16);
                        if (dir == "0")
                            key_direction = 0;
                        else if (dir == "1")
                            key_direction = 1;
                        else if (dir == "bidirectional" || dir == "bi")
                            key_direction = -1;
                        else
                            throw proto_option_error(ERR_INVALID_OPTION_CRYPTO, "bad key-direction parameter");
                    }
                }
                else
                    throw proto_option_error(ERR_INVALID_OPTION_CRYPTO, "bad key-direction default");
            }

            // compression
            {
                const Option *o = opt.get_ptr("compress");
                if (o)
                {
                    if (o->size() >= 2)
                    {
                        const std::string meth_name = o->get(1, 128);
                        CompressContext::Type meth = CompressContext::parse_method(meth_name);
                        if (meth == CompressContext::NONE)
                            OPENVPN_THROW_ARG1(proto_option_error, ERR_INVALID_OPTION_VAL, "Unknown compressor: '" << meth_name << '\'');
                        comp_ctx = CompressContext(pco.is_comp() ? meth : CompressContext::stub(meth), pco.is_comp_asym());
                    }
                    else
                        comp_ctx = CompressContext(pco.is_comp() ? CompressContext::ANY : CompressContext::COMP_STUB, pco.is_comp_asym());
                }
                else
                {
                    o = opt.get_ptr("comp-lzo");
                    if (o)
                    {
                        if (o->size() == 2 && o->ref(1) == "no")
                        {
                            // On the client, by using ANY instead of ANY_LZO, we are telling the server
                            // that it's okay to use any of our supported compression methods.
                            comp_ctx = CompressContext(pco.is_comp() ? CompressContext::ANY : CompressContext::LZO_STUB, pco.is_comp_asym());
                        }
                        else
                        {
                            comp_ctx = CompressContext(pco.is_comp() ? CompressContext::LZO : CompressContext::LZO_STUB, pco.is_comp_asym());
                        }
                    }
                }
            }

            // tun-mtu
            tun_mtu = parse_tun_mtu(opt, tun_mtu);
            tun_mtu_max = parse_tun_mtu_max(opt, tun_mtu_max);

            // mssfix
            mss_parms.parse(opt, true);
            if (mss_parms.mssfix_default)
            {
                if (tun_mtu == TUN_MTU_DEFAULT)
                {
                    mss_parms.mssfix = MSSParms::MSSFIX_DEFAULT;
                    mss_parms.mtu = true;
                }
                else
                {
                    mss_parms.mssfix = tun_mtu;
                    mss_parms.fixed = true;
                }
            }

            // load parameters that can be present in both config file or pushed options
            load_common(opt, pco, server ? LOAD_COMMON_SERVER : LOAD_COMMON_CLIENT);
        }

        /**
         * Fire up the infrastructure needed in order to be able to process dynamic
         * TLS-crypt renegotiation.
         */
        void enable_dynamic_tls_crypt()
        {
            set_tls_crypt_algs();
            tls_crypt_ |= TLSCrypt::Dynamic;
        }

        // load options string pushed by server
        void process_push(const OptionList &opt, const ProtoContextCompressionOptions &pco)
        {
            // data channel
            parse_pushed_data_channel_options(opt);

            // protocol-flags
            parse_pushed_protocol_flags(opt);

            // compression
            parse_pushed_compression(opt, pco);

            // peer ID
            parse_pushed_peer_id(opt);

            // custom app control channel options
            parse_custom_app_control(opt);

            try
            {
                // load parameters that can be present in both config file or pushed options
                load_common(opt, pco, LOAD_COMMON_CLIENT_PUSHED);
            }
            catch (const std::exception &e)
            {
                OPENVPN_THROW(process_server_push_error, "Problem accepting server-pushed parameter: " << e.what());
            }

            // show negotiated options
            OVPN_LOG_INFO(show_options());
        }

        void parse_custom_app_control(const OptionList &opt)
        {
            try
            {
                const Option *o = opt.get_ptr("custom-control");
                if (o)
                {
                    app_control_config.max_msg_size = o->get_num(1, 1, std::numeric_limits<int>::max());
                    const auto &flags = o->get(2, 1024);
                    const auto &protocols = o->get(3, 1024);
                    app_control_config.parse_flags(flags);

                    app_control_config.supported_protocols = string::split(protocols, ':');

                    /* This implementation always wants to have at least both base64 and text encoding */
                    if (!app_control_config.encoding_text)
                    {
                        OPENVPN_LOG("Warning: custom app control requires base64 encoding to properly work");
                    }
                }
            }
            catch (const std::exception &e)
            {
                OPENVPN_THROW(process_server_push_error, "Problem accepting server-pushed parameter: " << e.what());
                app_control_config = {};
            }
        }

        void parse_pushed_data_channel_options(const OptionList &opt)
        {
            // cipher
            std::string new_cipher;
            try
            {
                const Option *o = opt.get_ptr("cipher");
                if (o)
                {
                    new_cipher = o->get(1, 128);
                    if (new_cipher != "none")
                        dc.set_cipher(CryptoAlgs::lookup(new_cipher));
                }
            }
            catch (const std::exception &e)
            {
                OPENVPN_THROW(process_server_push_error, "Problem accepting server-pushed cipher '" << new_cipher << "': " << e.what());
            }

            // digest
            std::string new_digest;
            try
            {
                const Option *o = opt.get_ptr("auth");
                if (o)
                {
                    new_digest = o->get(1, 128);
                    if (new_digest != "none")
                        dc.set_digest(CryptoAlgs::lookup(new_digest));
                }
            }
            catch (const std::exception &e)
            {
                OPENVPN_THROW(process_server_push_error, "Problem accepting server-pushed digest '" << new_digest << "': " << e.what());
            }
        }

        void parse_pushed_peer_id(const OptionList &opt)
        {
            try
            {
                const Option *o = opt.get_ptr("peer-id");
                if (o)
                {
                    bool status = parse_number_validate<int>(o->get(1, 16),
                                                             16,
                                                             -1,
                                                             0xFFFFFE,
                                                             &remote_peer_id);
                    if (!status)
                        throw Exception("parse/range issue");
                    enable_op32 = true;
                }
            }
            catch (const std::exception &e)
            {
                OPENVPN_THROW(process_server_push_error, "Problem accepting server-pushed peer-id: " << e.what());
            }
        }

        void parse_pushed_protocol_flags(const OptionList &opt)
        {
            // tls key-derivation method with old key-derivation option
            std::string key_method;
            try
            {
                const Option *o = opt.get_ptr("key-derivation");
                if (o)
                {
                    key_method = o->get(1, 128);
                    if (key_method == "tls-ekm")
                        dc.set_key_derivation(CryptoAlgs::KeyDerivation::TLS_EKM);
                    else
                        OPENVPN_THROW(process_server_push_error, "Problem accepting key-derivation method '" << key_method << "'");
                }
                else
                    dc.set_key_derivation(CryptoAlgs::KeyDerivation::OPENVPN_PRF);
            }
            catch (const std::exception &e)
            {
                OPENVPN_THROW(process_server_push_error, "Problem accepting key-derivation method '" << key_method << "': " << e.what());
            }

            try
            {
                const Option *o = opt.get_ptr("protocol-flags");
                if (o)
                {
                    o->min_args(2);
                    for (std::size_t i = 1; i < o->size(); i++)
                    {
                        std::string flag = o->get(i, 128);
                        if (flag == "cc-exit")
                        {
                            cc_exit_notify = true;
                        }
                        else if (flag == "dyn-tls-crypt")
                        {
                            enable_dynamic_tls_crypt();
                        }
                        else if (flag == "tls-ekm")
                        {
                            // Overrides "key-derivation" method set above
                            dc.set_key_derivation(CryptoAlgs::KeyDerivation::TLS_EKM);
                        }
                        else if (flag == "aead-epoch")
                        {
                            dc.set_use_epoch_keys(true);
                        }
                        else
                        {
                            OPENVPN_THROW(process_server_push_error, "unknown flag '" << flag << "'");
                        }
                    }
                }
            }
            catch (const std::exception &e)
            {
                OPENVPN_THROW(process_server_push_error, "Problem accepting protocol-flags: " << e.what());
            }
        }

        void parse_pushed_compression(const OptionList &opt, const ProtoContextCompressionOptions &pco)
        {
            std::string new_comp;
            try
            {
                const Option *o;
                o = opt.get_ptr("compress");
                if (o)
                {
                    new_comp = o->get(1, 128);
                    CompressContext::Type meth = CompressContext::parse_method(new_comp);
                    if (meth != CompressContext::NONE)
                    {
                        // if compression is not availabe, CompressContext ctor throws an exception
                        if (pco.is_comp())
                            comp_ctx = CompressContext(meth, pco.is_comp_asym());
                        else
                        {
                            // server pushes compression but client has compression disabled
                            // degrade to asymmetric compression (downlink only)
                            comp_ctx = CompressContext(meth, true);
                            if (!comp_ctx.is_any_stub(meth))
                            {
                                OPENVPN_LOG("Server has pushed compressor "
                                            << comp_ctx.str()
                                            << ", but client has disabled compression, switching to asymmetric");
                            }
                        }
                    }
                }
                else
                {
                    o = opt.get_ptr("comp-lzo");
                    if (o)
                    {
                        if (o->size() == 2 && o->ref(1) == "no")
                        {
                            comp_ctx = CompressContext(CompressContext::LZO_STUB, false);
                        }
                        else
                        {
                            comp_ctx = CompressContext(pco.is_comp() ? CompressContext::LZO : CompressContext::LZO_STUB, pco.is_comp_asym());
                        }
                    }
                }
            }
            catch (const std::exception &e)
            {
                OPENVPN_THROW(process_server_push_error, "Problem accepting server-pushed compressor '" << new_comp << "': " << e.what());
            }
        }

        void get_data_channel_options(std::ostringstream &os) const
        {
            os << "  data channel:";
            os << " cipher " << CryptoAlgs::name(dc.cipher());
            if (CryptoAlgs::mode(dc.cipher()) != CryptoAlgs::Mode::AEAD)
                os << ", digest " << CryptoAlgs::name(dc.digest());

            os << ", peer-id " << remote_peer_id;

            if (dc.useEpochKeys())
                os << ", aead-epoch";

            os << std::endl;
        }

        void show_cc_enc_option(std::ostringstream &os) const
        {
            if (tls_auth_enabled())
            {
                os << "  control channel: tls-auth enabled" << std::endl;
            }
            if (tls_crypt_v2_enabled())
            {
                os << "  control channel: tls-crypt v2 enabled" << std::endl;
            }
            else if (tls_crypt_enabled())
            {
                os << "  control channel: tls-crypt enabled" << std::endl;
            }
            else if (dynamic_tls_crypt_enabled())
            {
                os << "  control channel: dynamic tls-crypt enabled" << std::endl;
            }
        }

        std::string show_options() const
        {
            std::ostringstream os;
            os << "PROTOCOL OPTIONS:" << std::endl;
            os << "  key-derivation: " << CryptoAlgs::name(dc.key_derivation()) << std::endl;
            if (comp_ctx.type() != CompressContext::NONE)
                os << "  compress: " << comp_ctx.str() << std::endl;

            show_cc_enc_option(os);
            get_data_channel_options(os);

            if (!app_control_config.supported_protocols.empty())
            {
                os << "  app custom control channel: " << app_control_config.str() << std::endl;
            }

            return os.str();
        }

        void set_protocol(const Protocol &p)
        {
            // adjust options for new transport protocol
            protocol = p;
        }

        void set_tls_auth_digest(const CryptoAlgs::Type digest)
        {
            tls_auth_context = tls_auth_factory->new_obj(digest);
        }

        void set_tls_crypt_algs()
        {
            if (tls_crypt_context)
                return;

            auto digest = CryptoAlgs::lookup("SHA256");
            auto cipher = CryptoAlgs::lookup("AES-256-CTR");

            if ((digest == CryptoAlgs::NONE) || (cipher == CryptoAlgs::NONE))
                throw proto_option_error(ERR_INVALID_OPTION_CRYPTO, "missing support for tls-crypt algorithms");

            /* TODO: we currently use the default SSL library context here as the
             * library context is not available this early. This should not matter
             * for the algorithms used by tls_crypt */
            tls_crypt_context = tls_crypt_factory->new_obj(nullptr, digest, cipher);
        }

        void set_xmit_creds(const bool xmit_creds_arg)
        {
            xmit_creds = xmit_creds_arg;
        }

        bool tls_auth_enabled() const
        {
            return tls_auth_key.defined() && tls_auth_context;
        }

        bool tls_crypt_enabled() const
        {
            return tls_crypt_key.defined() && (tls_crypt_ & TLSCrypt::V1);
        }

        bool tls_crypt_v2_enabled() const
        {
            return (tls_crypt_key.defined() || tls_crypt_v2_serverkey_id) && (tls_crypt_ & TLSCrypt::V2);
        }

        bool dynamic_tls_crypt_enabled() const
        {
            return (tls_crypt_ & TLSCrypt::Dynamic);
        }

        // generate a string summarizing options that will be
        // transmitted to peer for options consistency check
        std::string options_string()
        {
            if (!initial_options.empty())
                return initial_options;

            std::ostringstream out;

            const bool server = ssl_factory->mode().is_server();
            const unsigned int l2extra = (layer() == Layer::OSI_LAYER_2 ? 32 : 0);

            out << "V4";

            out << ",dev-type " << layer.dev_type();
            out << ",link-mtu " << tun_mtu + link_mtu_adjust() + l2extra;
            out << ",tun-mtu " << tun_mtu + l2extra;
            out << ",proto " << protocol.occ_str(server);

            {
                const char *compstr = comp_ctx.options_string();
                if (compstr)
                    out << ',' << compstr;
            }

            if (tls_auth_context && (key_direction >= 0))
                out << ",keydir " << key_direction;

            out << ",cipher " << CryptoAlgs::name(dc.cipher(), "[null-cipher]");
            out << ",auth " << CryptoAlgs::name(dc.digest(), "[null-digest]");
            out << ",keysize " << (CryptoAlgs::key_length(dc.cipher()) * 8);

            if (tls_auth_context)
                out << ",tls-auth";

            // sending tls-crypt does not make sense. If we got to this point it
            // means that tls-crypt was already there and it worked fine.
            // tls-auth has to be kept for backward compatibility as it is there
            // since a bit.

            out << ",key-method 2";

            if (server)
                out << ",tls-server";
            else
                out << ",tls-client";

            initial_options = out.str();

            return initial_options;
        }

        /**
         * This method adds the parts of the peer info string that depend on the state of the
         * connection, especially the remote that we are connecting to.
         */
        void build_connect_time_peer_info_string(TransportClient::Ptr transport)
        {
            extra_peer_info_transport.reset(new PeerInfo::Set{});
            if (extra_peer_info_push_peerinfo)
            {
                /* check if the IV_HWADDR is already present in the extra_peer_info set as it has then been
                 * statically been overridden */
                if (!extra_peer_info->contains_key("IV_HWADDR"))
                {
                    std::string hwaddr = get_hwaddr(transport->server_endpoint_addr());
                    if (!hwaddr.empty())
                        extra_peer_info_transport->emplace_back("IV_HWADDR", hwaddr);
                }
            }
        }

        // generate a string summarizing information about the client
        // including capabilities
        std::string peer_info_string(bool proto_v3_support) const
        {
            std::ostringstream out;
            const char *compstr = nullptr;

            // supports op32 and P_DATA_V2 and expects a push reply
            unsigned int iv_proto = IV_PROTO_DATA_V2
                                    | IV_PROTO_REQUEST_PUSH
                                    | IV_PROTO_AUTH_PENDING_KW
                                    | IV_PROTO_DNS_OPTION_V2
                                    | IV_PROTO_CC_EXIT_NOTIFY
                                    | IV_PROTO_AUTH_FAIL_TEMP
                                    | IV_PROTO_DATA_EPOCH
                                    | IV_PROTO_PUSH_UPDATE;

            if (CryptoAlgs::lookup("SHA256") != CryptoAlgs::NONE && CryptoAlgs::lookup("AES-256-CTR") != CryptoAlgs::NONE)
                iv_proto |= IV_PROTO_DYN_TLS_CRYPT;

            if (SSLLib::SSLAPI::support_key_material_export())
            {
                iv_proto |= IV_PROTO_TLS_KEY_EXPORT;
            }

            out << "IV_VER=" << OPENVPN_VERSION << '\n';
            out << "IV_PLAT=" << platform_name() << '\n';
            out << "IV_NCP=2\n";   // negotiable crypto parameters V2
            out << "IV_TCPNL=1\n"; // supports TCP non-linear packet ID
            out << "IV_PROTO=" << iv_proto << '\n';
            out << "IV_MTU=" << tun_mtu_max << "\n";
            /*
             * OpenVPN3 allows to be pushed any cipher that it supports as it
             * only implements secure ones and BF-CBC for backwards
             * compatibility and generally adopts the concept of the server being
             * responsible for sensible choices. Include the cipher here since
             * OpenVPN 2.5 will otherwise ignore it and break on conrer cases
             * like --cipher AES-128-CBC on client and --data-ciphers "AES-128-CBC"
             * on server.
             *
             */
            out << "IV_CIPHERS=";
            CryptoAlgs::for_each(
                [&out](CryptoAlgs::Type type, const CryptoAlgs::Alg &alg) -> bool
                {
                    if (!alg.dc_cipher())
                        return false;
                    out << alg.name() << ':';
                    return true;
                });
            out.seekp(-1, std::ios_base::cur);
            out << "\n";

            compstr = comp_ctx.peer_info_string();

            if (compstr)
                out << compstr;
            if (extra_peer_info)
                out << extra_peer_info->to_string();
            if (extra_peer_info_transport)
                out << extra_peer_info_transport->to_string();
            if (is_bs64_cipher(dc.cipher()))
                out << "IV_BS64DL=1\n"; // indicate support for data limits when using 64-bit block-size ciphers, version 1 (CVE-2016-6329)
            if (relay_mode)
                out << "IV_RELAY=1\n";


            const std::string ret = out.str();
            OVPN_LOG_INFO("Sending Peer Info:" << std::endl
                                               << ret);
            return ret;
        }

        // Used to generate link_mtu option sent to peer.
        // Not const because dc.context() caches the DC context.
        unsigned int link_mtu_adjust()
        {
            size_t dc_overhead;
            if (dc.cipher() == CryptoAlgs::BF_CBC)
            {
                /* since often configuration lack BF-CBC, we hardcode the overhead for BF-CBC to avoid
                 * trying to load BF-CBC, which is not available anymore in modern crypto libraries */
                dc_overhead = CryptoAlgs::size(dc.digest()) // HMAC
                              + 64 / 8                      // Cipher IV
                              + 64 / 8;                     // worst-case PKCS#7 padding expansion (blocksize)
            }
            else
            {
                dc_overhead = dc.context().encap_overhead();
            }
            const size_t adj = protocol.extra_transport_bytes() + // extra 2 bytes for TCP-streamed packet length
                               (enable_op32 ? 4 : 1) +            // leading op
                               comp_ctx.extra_payload_bytes() +   // compression header
                               PacketIDData::size(false) +        // sequence number
                               dc_overhead;                       // data channel crypto layer overhead
            return (unsigned int)adj;
        }

      private:
        enum LoadCommonType
        {
            LOAD_COMMON_SERVER,
            LOAD_COMMON_CLIENT,
            LOAD_COMMON_CLIENT_PUSHED,
        };

        // load parameters that can be present in both config file or pushed options
        void load_common(const OptionList &opt,
                         const ProtoContextCompressionOptions &pco,
                         const LoadCommonType type)
        {
            // duration parms
            load_duration_parm(renegotiate, "reneg-sec", opt, 10, false, false);
            expire = renegotiate;
            load_duration_parm(expire, "tran-window", opt, 10, false, false);
            expire += renegotiate;
            load_duration_parm(handshake_window, "hand-window", opt, 10, false, false);
            if (is_bs64_cipher(dc.cipher())) // special data limits for 64-bit block-size ciphers (CVE-2016-6329)
            {
                become_primary = Time::Duration::seconds(5);
                tls_timeout = Time::Duration::milliseconds(1000);
            }
            else
                become_primary = Time::Duration::seconds(std::min(handshake_window.to_seconds(),
                                                                  renegotiate.to_seconds() / 2));
            load_duration_parm(become_primary, "become-primary", opt, 0, false, false);
            load_duration_parm(tls_timeout, "tls-timeout", opt, 100, false, true);

            if (type == LOAD_COMMON_SERVER)
                renegotiate += handshake_window; // avoid renegotiation collision with client

            // keepalive, ping, ping-restart
            {
                const Option *o = opt.get_ptr("keepalive");
                if (o)
                {
                    set_duration_parm(keepalive_ping, "keepalive ping", o->get(1, 16), 1, false, false);
                    set_duration_parm(keepalive_timeout, "keepalive timeout", o->get(2, 16), 1, type == LOAD_COMMON_SERVER, false);

                    if (o->size() >= 4)
                        set_duration_parm(keepalive_timeout_early, "keepalive timeout early", o->get(3, 16), 1, false, false);
                    else
                        keepalive_timeout_early = keepalive_timeout;
                }
                else
                {
                    load_duration_parm(keepalive_ping, "ping", opt, 1, false, false);
                    load_duration_parm(keepalive_timeout, "ping-restart", opt, 1, false, false);
                }
            }

            if ((type == LOAD_COMMON_CLIENT) || (type == LOAD_COMMON_CLIENT_PUSHED))
            {
                auth_nocache = opt.exists("auth-nocache");
            }
        }

        std::string relay_prefix(const char *optname) const
        {
            std::string ret;
            if (relay_mode)
                ret = "relay-";
            ret += optname;
            return ret;
        }
    };

    // Used to describe an incoming network packet
    class PacketType
    {
        friend class ProtoContext;

        enum
        {
            DEFINED = 1 << 0,    // packet is valid (otherwise invalid)
            CONTROL = 1 << 1,    // packet for control channel (otherwise for data channel)
            SECONDARY = 1 << 2,  // packet is associated with secondary KeyContext (otherwise primary)
            SOFT_RESET = 1 << 3, // packet is a CONTROL_SOFT_RESET_V1 msg indicating a request for SSL/TLS renegotiate
        };

      public:
        bool is_defined() const
        {
            return flags & DEFINED;
        }
        bool is_control() const
        {
            return (flags & (CONTROL | DEFINED)) == (CONTROL | DEFINED);
        }
        bool is_data() const
        {
            return (flags & (CONTROL | DEFINED)) == DEFINED;
        }
        bool is_soft_reset() const
        {
            return (flags & (CONTROL | DEFINED | SECONDARY | SOFT_RESET))
                   == (CONTROL | DEFINED | SECONDARY | SOFT_RESET);
        }
        int peer_id() const
        {
            return peer_id_;
        }

      private:
        PacketType(const Buffer &buf, class ProtoContext &proto)
            : flags(0), opcode(INVALID_OPCODE), peer_id_(-1)
        {
            if (likely(buf.size()))
            {
                // get packet header byte
                const unsigned int op = buf[0];

                // examine opcode
                {
                    const unsigned int opc = opcode_extract(op);
                    switch (opc)
                    {
                    case CONTROL_SOFT_RESET_V1:
                    case CONTROL_V1:
                    case ACK_V1:
                        {
                            flags |= CONTROL;
                            opcode = opc;
                            break;
                        }
                    case DATA_V2:
                        {
                            if (unlikely(buf.size() < 4))
                                return;
                            std::uint32_t opi;
                            // avoid unaligned access
                            std::memcpy(&opi, buf.c_data(), sizeof(opi));
                            opi = ntohl(opi) & 0x00FFFFFF;
                            if (opi != OP_PEER_ID_UNDEF)
                                peer_id_ = opi;
                            opcode = opc;
                            break;
                        }
                    case DATA_V1:
                        {
                            opcode = opc;
                            break;
                        }
                    case CONTROL_HARD_RESET_CLIENT_V2:
                    case CONTROL_HARD_RESET_CLIENT_V3:
                        {
                            if (!proto.is_server())
                                return;
                            flags |= CONTROL;
                            opcode = opc;
                            break;
                        }
                    case CONTROL_HARD_RESET_SERVER_V2:
                    case CONTROL_WKC_V1:
                        {
                            if (proto.is_server())
                                return;
                            flags |= CONTROL;
                            opcode = opc;
                            break;
                        }
                    default:
                        return;
                    }
                }

                // examine key ID
                {
                    const unsigned int kid = key_id_extract(op);
                    if (proto.primary && kid == proto.primary->key_id())
                        flags |= DEFINED;
                    else if (proto.secondary && kid == proto.secondary->key_id())
                        flags |= (DEFINED | SECONDARY);
                    else if (opcode == CONTROL_SOFT_RESET_V1 && kid == proto.upcoming_key_id)
                        flags |= (DEFINED | SECONDARY | SOFT_RESET);
                }
            }
        }

        unsigned int flags;
        unsigned int opcode;
        int peer_id_;
    };

    static const char *opcode_name(const unsigned int opcode)
    {
        switch (opcode)
        {
        case CONTROL_SOFT_RESET_V1:
            return "CONTROL_SOFT_RESET_V1";
        case CONTROL_V1:
            return "CONTROL_V1";
        case ACK_V1:
            return "ACK_V1";
        case DATA_V1:
            return "DATA_V1";
        case DATA_V2:
            return "DATA_V2";
        case CONTROL_HARD_RESET_CLIENT_V2:
            return "CONTROL_HARD_RESET_CLIENT_V2";
        case CONTROL_HARD_RESET_CLIENT_V3:
            return "CONTROL_HARD_RESET_CLIENT_V3";
        case CONTROL_HARD_RESET_SERVER_V2:
            return "CONTROL_HARD_RESET_SERVER_V2";
        case CONTROL_WKC_V1:
            return "CONTROL_WKC_V1";
        }
        return nullptr;
    }

    std::string dump_packet(const Buffer &buf)
    {
        std::ostringstream out;
        try
        {
            Buffer b(buf);
            const size_t orig_size = b.size();
            const unsigned int op = b.pop_front();

            const unsigned int opcode = opcode_extract(op);
            const char *op_name = opcode_name(opcode);
            if (op_name)
                out << op_name << '/' << key_id_extract(op);
            else
                return "BAD_PACKET";

            if (opcode == DATA_V1 || opcode == DATA_V2)
            {
                if (opcode == DATA_V2)
                {
                    const unsigned int p1 = b.pop_front();
                    const unsigned int p2 = b.pop_front();
                    const unsigned int p3 = b.pop_front();
                    const unsigned int peer_id = (p1 << 16) + (p2 << 8) + p3;
                    if (peer_id != 0xFFFFFF)
                        out << " PEER_ID=" << peer_id;
                }
                out << " SIZE=" << b.size() << '/' << orig_size;
            }
            else
            {
                {
                    ProtoSessionID src_psid(b);
                    out << " SRC_PSID=" << src_psid.str();
                }

                if (tls_wrap_mode == TLS_CRYPT || tls_wrap_mode == TLS_CRYPT_V2)
                {
                    PacketIDControl pid;
                    pid.read(b);
                    out << " PID=" << pid.str();

                    const unsigned char *hmac = b.read_alloc(hmac_size);
                    out << " HMAC=" << render_hex(hmac, hmac_size);
                    out << " TLS-CRYPT ENCRYPTED PAYLOAD=" << b.size() << " bytes";
                }
                else
                {
                    if (tls_wrap_mode == TLS_AUTH)
                    {
                        const unsigned char *hmac = b.read_alloc(hmac_size);
                        out << " HMAC=" << render_hex(hmac, hmac_size);

                        PacketIDControl pid;
                        pid.read(b);
                        out << " PID=" << pid.str();
                    }

                    ReliableAck ack{};
                    ack.read(b);
                    const bool dest_psid_defined = !ack.empty();
                    out << " ACK=[";
                    while (!ack.empty())
                    {
                        out << " " << ack.front();
                        ack.pop_front();
                    }
                    out << " ]";

                    if (dest_psid_defined)
                    {
                        ProtoSessionID dest_psid(b);
                        out << " DEST_PSID=" << dest_psid.str();
                    }

                    if (opcode != ACK_V1)
                        out << " MSG_ID=" << ReliableAck::read_id(b);

                    out << " SIZE=" << b.size() << '/' << orig_size;
                }
            }
#ifdef OPENVPN_DEBUG_PROTO_DUMP
            out << '\n'
                << string::trim_crlf_copy(dump_hex(buf));
#endif
        }
        catch (const std::exception &e)
        {
            out << " EXCEPTION: " << e.what();
        }
        return out.str();
    }

    // used for reading/writing authentication strings (username, password, etc.) from buffer using the
    //  2 byte prefix for length
    static void write_uint16_length(const size_t size, Buffer &buf)
    {
        if (size > 0xFFFF)
            throw proto_error("auth_string_overflow");
        const std::uint16_t net_size = htons(static_cast<std::uint16_t>(size));
        buf.write((const unsigned char *)&net_size, sizeof(net_size));
    }

    static uint16_t read_uint16_length(Buffer &buf)
    {
        if (buf.size())
        {
            std::uint16_t net_size;
            buf.read((unsigned char *)&net_size, sizeof(net_size));
            return ntohs(net_size);
        }
        else
            return 0;
    }

    template <typename S>
    static void write_auth_string(const S &str, Buffer &buf)
    {
        const size_t len = str.length();
        if (len)
        {
            write_uint16_length(len + 1, buf);
            buf.write((const unsigned char *)str.c_str(), len);
            buf.null_terminate();
        }
        else
            write_uint16_length(0, buf);
    }

    template <typename S>
    static S read_auth_string(Buffer &buf)
    {
        const size_t len = read_uint16_length(buf);
        if (len)
        {
            const char *data = (const char *)buf.read_alloc(len);
            if (len > 1)
                return S(data, len - 1);
        }
        return S();
    }

    template <typename S>
    static void write_control_string(const S &str, Buffer &buf)
    {
        const size_t len = str.length();
        buf.write((const unsigned char *)str.c_str(), len);
        buf.null_terminate();
    }

    static void write_empty_string(Buffer &buf)
    {
        write_uint16_length(0, buf);
    }

    template <typename S>
    static S read_control_string(const Buffer &buf)
    {
        size_t size = buf.size();
        if (size)
        {
            /* Trim any trailing \n or \r or 0x00 characters. Scripts plugin sometimes accidentally include a \n or \r\n in AUTH_FAILED
             * or similar messages */
            while (size > 0 && (buf[size - 1] == 0 || buf[size - 1] == '\r' || buf[size - 1] == '\n'))
            {
                --size;
            }

            if (size)
            {
                return S{reinterpret_cast<const char *>(buf.c_data()), size};
            }
        }
        return {};
    }

    template <typename S>
    void write_control_string(const S &str)
    {
        const size_t len = str.length();
        auto bp = BufferAllocatedRc::Create(len + 1);
        write_control_string(str, *bp);
        control_send(std::move(bp));
    }

  protected:
    // Packet structure for managing network packets, passed as a template
    // parameter to ProtoStackBase
    class Packet
    {
        friend class ProtoContext;

      public:
        Packet()
        {
            reset_non_buf();
        }

        Packet(BufferPtr &&buf_arg, const unsigned int opcode_arg = CONTROL_V1)
            : opcode(opcode_arg), buf(std::move(buf_arg))
        {
        }

        // clone packet, including buffer content
        Packet clone() const
        {
            Packet pkt;
            pkt.opcode = opcode;
            pkt.buf = BufferAllocatedRc::Create(*buf);
            return pkt;
        }

        void reset()
        {
            reset_non_buf();
            buf.reset();
        }

        void frame_prepare(const Frame &frame, const unsigned int context)
        {
            if (!buf)
                buf = BufferAllocatedRc::Create();
            frame.prepare(context, *buf);
        }

        /**
         * This returns if this packet type has a payload that should considered
         * to be TLS ciphertext/TLS packet
         */
        bool contains_tls_ciphertext() const
        {
            return opcode == CONTROL_V1 || opcode == CONTROL_WKC_V1;
        }
        operator bool() const
        {
            return bool(buf);
        }
        const BufferPtr &buffer_ptr()
        {
            return buf;
        }
        const Buffer &buffer() const
        {
            return *buf;
        }

      private:
        void reset_non_buf()
        {
            opcode = INVALID_OPCODE;
        }

        unsigned int opcode;
        BufferPtr buf;
    };

    // KeyContext encapsulates a single SSL/TLS session.
    // ProtoStackBase uses CRTP-based static polymorphism for method callbacks.
    class KeyContext : ProtoStackBase<Packet, KeyContext>, public RC<thread_unsafe_refcount>
    {
        typedef ProtoStackBase<Packet, KeyContext> Base;
        friend Base;
        typedef Base::ReliableSend ReliableSend;
        typedef Base::ReliableRecv ReliableRecv;

        // ProtoStackBase protected vars
        using Base::now;
        using Base::rel_recv;
        using Base::rel_send;
        using Base::xmit_acks;

        // ProtoStackBase member functions
        using Base::raw_send;
        using Base::send_pending_acks;
        using Base::start_handshake;

        // Helper for handling deferred data channel setup,
        // for example if cipher/digest are pushed.
        struct DataChannelKey
        {
            DataChannelKey()
            {
            }

            OpenVPNStaticKey key;
            std::optional<CryptoDCInstance::RekeyType> rekey_type;
        };

      public:
        typedef RCPtr<KeyContext> Ptr;

        // ProtoStackBase member functions
        using Base::export_key_material;

        OPENVPN_SIMPLE_EXCEPTION(tls_crypt_unwrap_wkc_error);

        // KeyContext events occur on two basic key types:
        //   Primary Key -- the key we transmit/encrypt on.
        //   Secondary Key -- new keys and retiring keys.
        //
        // The very first key created (key_id == 0) is a
        // primary key.  Subsequently created keys are always,
        // at least initially, secondary keys.  Secondary keys
        // promote to primary via the KEV_BECOME_PRIMARY event
        // (actually KEV_BECOME_PRIMARY swaps the primary and
        // secondary keys, so the old primary is demoted
        // to secondary and marked for expiration).
        //
        // Secondary keys are created by:
        // 1. locally-generated soft renegotiation requests, and
        // 2. peer-requested soft renegotiation requests.
        // In each case, any previous secondary key will be
        // wiped (including a secondary key that exists due to
        // demotion of a previous primary key that has been marked
        // for expiration).
        enum EventType
        {
            KEV_NONE,

            // KeyContext has reached the ACTIVE state, occurs on both
            // primary and secondary.
            KEV_ACTIVE,

            // SSL/TLS negotiation must complete by this time.  If this
            // event is hit on the first primary (i.e. first KeyContext
            // with key_id == 0), it is fatal to the session and will
            // trigger a disconnect/reconnect.  If it's hit on the
            // secondary, it will trigger a soft renegotiation.
            KEV_NEGOTIATE,

            // When a KeyContext (normally the secondary) is scheduled
            // to transition to the primary state.
            KEV_BECOME_PRIMARY,

            // Waiting for condition on secondary (usually
            // dataflow-based) to trigger KEV_BECOME_PRIMARY.
            KEV_PRIMARY_PENDING,

            // Start renegotiating a new KeyContext on secondary
            // (ignored unless originating on primary).
            KEV_RENEGOTIATE,

            // Trigger a renegotiation originating from either
            // primary or secondary.
            KEV_RENEGOTIATE_FORCE,

            // Queue delayed renegotiation request from secondary
            // to take effect after KEV_BECOME_PRIMARY.
            KEV_RENEGOTIATE_QUEUE,

            // Expiration of KeyContext.
            KEV_EXPIRE,
        };

        // for debugging
        static const char *event_type_string(const EventType et)
        {
            switch (et)
            {
            case KEV_NONE:
                return "KEV_NONE";
            case KEV_ACTIVE:
                return "KEV_ACTIVE";
            case KEV_NEGOTIATE:
                return "KEV_NEGOTIATE";
            case KEV_BECOME_PRIMARY:
                return "KEV_BECOME_PRIMARY";
            case KEV_PRIMARY_PENDING:
                return "KEV_PRIMARY_PENDING";
            case KEV_RENEGOTIATE:
                return "KEV_RENEGOTIATE";
            case KEV_RENEGOTIATE_FORCE:
                return "KEV_RENEGOTIATE_FORCE";
            case KEV_RENEGOTIATE_QUEUE:
                return "KEV_RENEGOTIATE_QUEUE";
            case KEV_EXPIRE:
                return "KEV_EXPIRE";
            default:
                return "KEV_?";
            }
        }

        KeyContext(ProtoContext &p, const bool initiator, bool psid_cookie_mode = false)
            : Base(*p.config->ssl_factory,
                   p.config->now,
                   p.config->tls_timeout,
                   p.config->frame,
                   p.stats,
                   psid_cookie_mode),
              proto(p),
              state(STATE_UNDEF),
              crypto_flags(0),
              dirty(0),
              key_limit_renegotiation_fired(false),
              tlsprf(p.config->tlsprf_factory->new_obj(p.is_server()))
        {
            // reliable protocol?
            set_protocol(proto.config->protocol);

            // get key_id from parent
            key_id_ = proto.next_key_id();

            // set initial state
            set_state((proto.is_server() ? S_INITIAL : C_INITIAL) + (initiator ? 0 : 1));

            // cache stuff that we need to access in hot path
            cache_op32();

            // remember when we were constructed
            construct_time = *now;

            // set must-negotiate-by time
            set_event(KEV_NONE, KEV_NEGOTIATE, construct_time + proto.config->handshake_window);
        }

        void set_protocol(const Protocol &p)
        {
            is_reliable = p.is_reliable(); // cache is_reliable state locally
        }

        uint32_t get_tls_warnings() const
        {
            return Base::get_tls_warnings();
        }

        /**
         * @brief Initialize the state machine and start protocol negotiation
         *
         * Called by ProtoContext::start()
         *
         * @param cookie_psid  see comment in ProtoContext::reset()
         */
        void start(const ProtoSessionID cookie_psid = ProtoSessionID())
        {
            if (cookie_psid.defined())
            {
                set_state(S_WAIT_RESET_ACK);
                dirty = true;
            }
            if (state == C_INITIAL || state == S_INITIAL)
            {
                send_reset();
                set_state(state + 1);
                dirty = true;
            }
        }

        // control channel flush
        void flush()
        {
            if (dirty)
            {
                post_ack_action();
                Base::flush();
                send_pending_acks();
                dirty = false;
            }
        }

        void invalidate(const Error::Type reason)
        {
            Base::invalidate(reason);
        }

        // retransmit packets as part of reliability layer
        void retransmit()
        {
            // note that we don't set dirty here
            Base::retransmit();
        }

        // when should we next call retransmit method
        Time next_retransmit() const
        {
            const Time t = Base::next_retransmit();
            if (t <= next_event_time)
                return t;
            else
                return next_event_time;
        }

        void app_send_validate(BufferPtr &&bp)
        {
            if (bp->size() > APP_MSG_MAX)
                throw proto_error("app_send: sent control message is too large");
            Base::app_send(std::move(bp));
        }

        // send app-level cleartext data to peer via SSL
        void app_send(BufferPtr &&bp)
        {
            if (state >= ACTIVE)
            {
                app_send_validate(std::move(bp));
                dirty = true;
            }
            else
                app_pre_write_queue.push_back(bp);
        }

        // pass received ciphertext packets on network to SSL/reliability layers
        bool net_recv(Packet &&pkt)
        {
            const bool ret = Base::net_recv(std::move(pkt));
            dirty = true;
            return ret;
        }

        // data channel encrypt
        void encrypt(BufferAllocated &buf)
        {
            if (state >= ACTIVE
                && (crypto_flags & CryptoDCInstance::CRYPTO_DEFINED)
                && !invalidated())
            {
                // compress and encrypt packet and prepend op header
                const bool pid_wrap = do_encrypt(buf, true);

                // Trigger a new SSL/TLS negotiation if packet ID (a 32-bit unsigned int)
                // is getting close to wrapping around.  If it wraps back to 0 without
                // a renegotiation, it would cause the replay protection logic to wrongly
                // think that all further packets are replays.
                if (pid_wrap)
                    schedule_key_limit_renegotiation();
            }
            else
                buf.reset_size(); // no crypto context available
        }

        // data channel decrypt
        void decrypt(BufferAllocated &buf)
        {
            try
            {
                if (state >= ACTIVE
                    && (crypto_flags & CryptoDCInstance::CRYPTO_DEFINED)
                    && !invalidated())
                {
                    // Knock off leading op from buffer, but pass the 32-bit version to
                    // decrypt so it can be used as Additional Data for packet authentication.
                    const size_t head_size = op_head_size(buf[0]);
                    const unsigned char *op32 = (head_size == OP_SIZE_V2) ? buf.c_data() : nullptr;
                    buf.advance(head_size);

                    // decrypt packet
                    const Error::Type err = crypto->decrypt(buf, now->seconds_since_epoch(), op32);
                    if (err)
                    {
                        proto.stats->error(err);
                        if (proto.is_tcp() && (err == Error::DECRYPT_ERROR || err == Error::HMAC_ERROR))
                            invalidate(err);
                    }

                    // trigger renegotiation if we hit decrypt data limit
                    if (data_limit)
                        if (!data_limit_add(DataLimit::Decrypt, buf.size()))
                            throw proto_option_error(ERR_INVALID_OPTION_CRYPTO, "Unable to add data limit");

                    // decompress packet
                    if (compress)
                        compress->decompress(buf);

                    // set MSS for segments server can receive
                    if (proto.config->mss_fix > 0)
                        MSSFix::mssfix(buf, numeric_cast<uint16_t>(proto.config->mss_fix));
                }
                else
                    buf.reset_size(); // no crypto context available
            }
            catch (std::exception &)
            {
                proto.stats->error(Error::BUFFER_ERROR);
                buf.reset_size();
                if (proto.is_tcp())
                    invalidate(Error::BUFFER_ERROR);
            }
        }

        // usually called by parent ProtoContext object when this KeyContext
        // has been retired.
        void prepare_expire(const EventType current_ev = KeyContext::KEV_NONE)
        {
            set_event(current_ev,
                      KEV_EXPIRE,
                      key_limit_renegotiation_fired ? data_limit_expire() : construct_time + proto.config->expire);
        }

        // set a default next event, if unspecified
        void set_next_event_if_unspecified()
        {
            if (next_event == KEV_NONE && !invalidated())
                prepare_expire();
        }

        // set a key limit renegotiation event at time t
        void key_limit_reneg(const EventType ev, const Time &t)
        {
            if (t.defined())
                set_event(KEV_NONE, ev, t + Time::Duration::seconds(proto.is_server() ? 2 : 1));
        }

        // return time of upcoming KEV_BECOME_PRIMARY event
        Time become_primary_time()
        {
            if (next_event == KEV_BECOME_PRIMARY)
                return next_event_time;
            else
                return Time();
        }

        // is an KEV_x event pending?
        bool event_pending()
        {
            if (current_event == KEV_NONE && *now >= next_event_time)
                process_next_event();
            return current_event != KEV_NONE;
        }

        // get KEV_x event
        EventType get_event() const
        {
            return current_event;
        }

        // clear KEV_x event
        void reset_event()
        {
            current_event = KEV_NONE;
        }

        // was session invalidated by an exception?
        bool invalidated() const
        {
            return Base::invalidated();
        }

        // Reason for invalidation
        Error::Type invalidation_reason() const
        {
            return Base::invalidation_reason();
        }

        // our Key ID in the OpenVPN protocol
        unsigned int key_id() const
        {
            return key_id_;
        }

        // indicates that data channel is keyed and ready to encrypt/decrypt packets
        bool data_channel_ready() const
        {
            return state >= ACTIVE;
        }

        bool is_dirty() const
        {
            return dirty;
        }

        // notification from parent of rekey operation
        void rekey(const CryptoDCInstance::RekeyType type)
        {
            if (crypto)
                crypto->rekey(type);
            else if (data_channel_key)
            {
                // save for deferred processing
                data_channel_key->rekey_type = type;
            }
        }

        // time that our state transitioned to ACTIVE
        Time reached_active() const
        {
            return reached_active_time_;
        }

        // transmit a keepalive message to peer
        void send_keepalive()
        {
            send_data_channel_message(proto_context_private::keepalive_message,
                                      sizeof(proto_context_private::keepalive_message));
        }

        // send explicit-exit-notify message to peer
        void send_explicit_exit_notify()
        {
            if (crypto_flags & CryptoDCInstance::EXPLICIT_EXIT_NOTIFY_DEFINED)
                crypto->explicit_exit_notify();
            else
                send_data_channel_message(proto_context_private::explicit_exit_notify_message,
                                          sizeof(proto_context_private::explicit_exit_notify_message));
        }

        // general purpose method for sending constant string messages
        // to peer via data channel
        void send_data_channel_message(const unsigned char *data, const size_t size)
        {
            if (state >= ACTIVE
                && (crypto_flags & CryptoDCInstance::CRYPTO_DEFINED)
                && !invalidated())
            {
                // allocate packet
                Packet pkt;
                pkt.frame_prepare(*proto.config->frame, Frame::WRITE_DC_MSG);

                // write keepalive message
                pkt.buf->write(data, size);

                // process packet for transmission
                do_encrypt(*pkt.buf, false); // set compress hint to "no"

                // send it
                proto.net_send(key_id_, pkt);
            }
        }

        // validate the integrity of a packet
        static bool validate(const Buffer &net_buf, ProtoContext &proto, TimePtr now)
        {
            try
            {
                Buffer recv(net_buf);

                switch (proto.tls_wrap_mode)
                {
                case TLS_AUTH:
                    return validate_tls_auth(recv, proto, now);
                case TLS_CRYPT_V2:
                    if (opcode_extract(recv[0]) == CONTROL_HARD_RESET_CLIENT_V3)
                    {
                        // skip validation of HARD_RESET_V3 because the tls-crypt
                        // engine has not been initialized yet
                        OVPN_LOG_VERBOSE("SKIPPING VALIDATION OF HARD_RESET_V3");
                        return true;
                    }
                    /* no break */
                case TLS_CRYPT:
                    return validate_tls_crypt(recv, proto, now);
                case TLS_PLAIN:
                    return validate_tls_plain(recv, proto, now);
                }
            }
            catch ([[maybe_unused]] BufferException &e)
            {
                OVPN_LOG_VERBOSE("validate() exception: " << e.what());
            }
            return false;
        }

        // Resets data_channel_key but also retains old
        // rekey_defined and rekey_type from previous instance.
        void generate_datachannel_keys()
        {
            std::unique_ptr<DataChannelKey> dck(new DataChannelKey());

            if (proto.config->dc.key_derivation() == CryptoAlgs::KeyDerivation::TLS_EKM)
            {
                // USE RFC 5705 key material export
                export_key_material(dck->key, "EXPORTER-OpenVPN-datakeys");
            }
            else
            {
                // use the TLS PRF construction to exchange session keys for building
                // the data channel crypto context
                tlsprf->generate_key_expansion(dck->key, proto.psid_self, proto.psid_peer);
            }
            tlsprf->erase();
            OVPN_LOG_VERBOSE(proto.debug_prefix()
                             << " KEY " << CryptoAlgs::name(proto.config->dc.key_derivation())
                             << " " << proto.mode().str() << ' ' << dck->key.render());

            if (data_channel_key)
            {
                dck->rekey_type = data_channel_key->rekey_type;
            }
            dck.swap(data_channel_key);
        }

        void calculate_mssfix(ProtoConfig &c)
        {
            if (c.mss_parms.fixed)
            {
                // substract IPv4 and TCP overhead, mssfix method will add extra 20 bytes for IPv6
                c.mss_fix = c.mss_parms.mssfix - (20 + 20);
                OPENVPN_LOG("fixed mssfix=" << c.mss_fix);
                return;
            }

            /* If we are running default mssfix but have a different tun-mtu pushed
             * disable mssfix */
            if (c.tun_mtu != TUN_MTU_DEFAULT && c.tun_mtu != 0 && c.mss_parms.mssfix_default)
            {
                c.mss_fix = 0;
                OPENVPN_LOG("mssfix disabled since tun-mtu is non-default ("
                            << c.tun_mtu << ")");
                return;
            }

            auto payload_overhead = size_t(0);

            // compv2 doesn't increase payload size
            switch (c.comp_ctx.type())
            {
            case CompressContext::NONE:
            case CompressContext::COMP_STUBv2:
            case CompressContext::LZ4v2:
                break;
            default:
                payload_overhead += 1;
            }

            if (CryptoAlgs::mode(c.dc.cipher()) == CryptoAlgs::CBC_HMAC)
                payload_overhead += PacketIDData::size(false);

            // account for IPv4 and TCP headers of the payload, mssfix method
            // will add 20 extra bytes if payload is IPv6
            payload_overhead += 20 + 20;

            auto overhead = c.protocol.extra_transport_bytes()
                            + (enable_op32 ? OP_SIZE_V2 : 1)
                            + c.dc.context().encap_overhead();

            // in CBC mode, the packet id is part of the payload size / overhead
            if (CryptoAlgs::mode(c.dc.cipher()) != CryptoAlgs::CBC_HMAC)
                overhead += PacketIDData::size(false);

            if (c.mss_parms.mtu)
            {
                overhead += c.protocol.is_ipv6()
                                ? sizeof(struct IPv6Header)
                                : sizeof(struct IPv4Header);
                overhead += proto.is_tcp()
                                ? sizeof(struct TCPHeader)
                                : sizeof(struct UDPHeader);
            }

            auto target = c.mss_parms.mssfix - overhead;
            if (CryptoAlgs::mode(c.dc.cipher()) == CryptoAlgs::CBC_HMAC)
            {
                // openvpn3 crypto includes blocksize in overhead, but we can
                // be a bit smarter here and instead make sure that resulting
                // ciphertext size (which is always multiple blocksize) is not
                // larger than target by running down target to the nearest
                // multiple of multiple and substracting 1.

                auto block_size = CryptoAlgs::block_size(c.dc.cipher());
                target += block_size;
                target = (target / block_size) * block_size;
                target -= 1;
            }

            if (!is_safe_conversion<decltype(c.mss_fix)>(target - payload_overhead))
            {
                OPENVPN_LOG("mssfix disabled since computed value is outside type bounds ("
                            << c.mss_fix << ")");
                c.mss_fix = 0;
                return;
            }

            c.mss_fix = static_cast<decltype(c.mss_fix)>(target - payload_overhead);
            OVPN_LOG_VERBOSE("mssfix=" << c.mss_fix
                                       << " (upper bound=" << c.mss_parms.mssfix
                                       << ", overhead=" << overhead
                                       << ", payload_overhead=" << payload_overhead
                                       << ", target=" << target << ")");
        }

        // Initialize the components of the OpenVPN data channel protocol
        void init_data_channel()
        {
            // don't run until our prerequisites are satisfied
            if (!data_channel_key)
                return;
            generate_datachannel_keys();

            // set up crypto for data channel
            bool enable_compress = true;
            ProtoConfig &c = *proto.config;
            const unsigned int key_dir = proto.is_server() ? OpenVPNStaticKey::INVERSE : OpenVPNStaticKey::NORMAL;
            const OpenVPNStaticKey &key = data_channel_key->key;

            // special data limits for 64-bit block-size ciphers (CVE-2016-6329)
            if (is_bs64_cipher(c.dc.cipher()))
            {
                DataLimit::Parameters dp;
                dp.encrypt_red_limit = OPENVPN_BS64_DATA_LIMIT;
                dp.decrypt_red_limit = OPENVPN_BS64_DATA_LIMIT;
                OVPN_LOG_INFO("Per-Key Data Limit: "
                              << dp.encrypt_red_limit << '/' << dp.decrypt_red_limit);
                data_limit.reset(new DataLimit(dp));
            }

            // build crypto context for data channel encryption/decryption
            crypto = c.dc.context().new_obj(key_id_);
            crypto_flags = crypto->defined();

            if (crypto_flags & CryptoDCInstance::CIPHER_DEFINED)
                crypto->init_cipher(
                    key.slice(OpenVPNStaticKey::CIPHER | OpenVPNStaticKey::ENCRYPT | key_dir),
                    key.slice(OpenVPNStaticKey::CIPHER | OpenVPNStaticKey::DECRYPT | key_dir));

            if (crypto_flags & CryptoDCInstance::HMAC_DEFINED)
                crypto->init_hmac(key.slice(OpenVPNStaticKey::HMAC | OpenVPNStaticKey::ENCRYPT | key_dir),
                                  key.slice(OpenVPNStaticKey::HMAC | OpenVPNStaticKey::DECRYPT | key_dir));

            crypto->init_pid("DATA",
                             int(key_id_),
                             proto.stats);

            crypto->init_remote_peer_id(c.remote_peer_id);

            enable_compress = crypto->consider_compression(proto.config->comp_ctx);

            if (data_channel_key->rekey_type.has_value())
                crypto->rekey(data_channel_key->rekey_type.value());
            data_channel_key.reset();

            // set up compression for data channel
            if (enable_compress)
                compress = proto.config->comp_ctx.new_compressor(proto.config->frame, proto.stats);
            else
                compress.reset();

            // cache op32 for hot path in do_encrypt
            cache_op32();

            calculate_mssfix(c);
        }

        void data_limit_notify(const DataLimit::Mode cdl_mode,
                               const DataLimit::State cdl_status)
        {
            if (data_limit)
                data_limit_event(cdl_mode, data_limit->update_state(cdl_mode, cdl_status));
        }

        int get_state() const
        {
            return state;
        }

      private:
        static bool validate_tls_auth(Buffer &recv, ProtoContext &proto, TimePtr now)
        {
            const unsigned char *orig_data = recv.data();
            const size_t orig_size = recv.size();

            // advance buffer past initial op byte
            recv.advance(1);

            // get source PSID
            ProtoSessionID src_psid(recv);

            // verify HMAC
            {
                recv.advance(proto.hmac_size);
                if (!proto.ta_hmac_recv->ovpn_hmac_cmp(orig_data,
                                                       orig_size,
                                                       1 + ProtoSessionID::SIZE,
                                                       proto.hmac_size,
                                                       PacketIDControl::size()))
                {
                    return false;
                }
            }

            // verify source PSID
            if (!proto.psid_peer.match(src_psid))
                return false;

            // read tls_auth packet ID
            const PacketIDControl pid = proto.ta_pid_recv.read_next(recv);

            // get current time_t
            const PacketIDControl::time_t t = now->seconds_since_epoch();

            // verify tls_auth packet ID
            const bool pid_ok = proto.ta_pid_recv.test_add(pid, t, false);

            // make sure that our own PSID is contained in packet received from peer
            if (ReliableAck::ack_skip(recv))
            {
                ProtoSessionID dest_psid(recv);
                if (!proto.psid_self.match(dest_psid))
                    return false;
            }

            return pid_ok;
        }

        static bool validate_tls_crypt(Buffer &recv, ProtoContext &proto, TimePtr now)
        {
            const unsigned char *orig_data = recv.data();
            const size_t orig_size = recv.size();

            // advance buffer past initial op byte
            recv.advance(1);
            // get source PSID
            ProtoSessionID src_psid(recv);
            // read tls_auth packet ID
            const PacketIDControl pid = proto.ta_pid_recv.read_next(recv);

            recv.advance(proto.hmac_size);

            const size_t head_size = 1 + ProtoSessionID::SIZE + PacketIDControl::size();
            const size_t data_offset = head_size + proto.hmac_size;
            if (orig_size < data_offset)
                return false;

            // we need a buffer to perform the payload decryption and being this a static
            // function we can't use the instance member like in decapsulate_tls_crypt()
            BufferAllocated work;
            proto.config->frame->prepare(Frame::DECRYPT_WORK, work);

            // decrypt payload from 'recv' into 'work'
            const size_t decrypt_bytes = proto.tls_crypt_recv->decrypt(orig_data + head_size,
                                                                       work.data(),
                                                                       work.max_size(),
                                                                       recv.c_data(),
                                                                       recv.size());
            if (!decrypt_bytes)
                return false;

            work.inc_size(decrypt_bytes);

            // verify HMAC
            if (!proto.tls_crypt_recv->hmac_cmp(orig_data,
                                                TLSCryptContext::hmac_offset,
                                                work.c_data(),
                                                work.size()))
                return false;

            // verify source PSID
            if (proto.psid_peer.defined())
            {
                if (!proto.psid_peer.match(src_psid))
                    return false;
            }
            else
            {
                proto.psid_peer = src_psid;
            }

            // get current time_t
            const PacketIDControl::time_t t = now->seconds_since_epoch();

            // verify tls_auth packet ID
            const bool pid_ok = proto.ta_pid_recv.test_add(pid, t, false);
            // make sure that our own PSID is contained in packet received from peer
            if (ReliableAck::ack_skip(work))
            {
                ProtoSessionID dest_psid(work);
                if (!proto.psid_self.match(dest_psid))
                    return false;
            }

            return pid_ok;
        }

        static bool validate_tls_plain(Buffer &recv, ProtoContext &proto, TimePtr now)
        {
            // advance buffer past initial op byte
            recv.advance(1);

            // verify source PSID
            ProtoSessionID src_psid(recv);
            if (!proto.psid_peer.match(src_psid))
                return false;

            // make sure that our own PSID is contained in packet received from peer
            if (ReliableAck::ack_skip(recv))
            {
                ProtoSessionID dest_psid(recv);
                if (!proto.psid_self.match(dest_psid))
                    return false;
            }
            return true;
        }

        bool do_encrypt(BufferAllocated &buf, const bool compress_hint)
        {
            if (!is_safe_conversion<uint16_t>(proto.config->mss_fix))
                return false;

            // set MSS for segments client can receive
            if (proto.config->mss_fix > 0)
                MSSFix::mssfix(buf, static_cast<uint16_t>(proto.config->mss_fix));

            // compress packet
            if (compress)
                compress->compress(buf, compress_hint);

            // trigger renegotiation if we hit encrypt data limit
            if (data_limit)
                if (!data_limit_add(DataLimit::Encrypt, buf.size()))
                    return false;

            bool pid_wrap;

            if (enable_op32)
            {
                const std::uint32_t op32 = htonl(op32_compose(DATA_V2, key_id_, remote_peer_id));

                static_assert(sizeof(op32) == OP_SIZE_V2, "OP_SIZE_V2 inconsistency");

                // encrypt packet
                pid_wrap = crypto->encrypt(buf, (const unsigned char *)&op32);

                // prepend op
                buf.prepend((const unsigned char *)&op32, sizeof(op32));
            }
            else
            {
                // encrypt packet
                pid_wrap = crypto->encrypt(buf, nullptr);

                // prepend op
                buf.push_front(op_compose(DATA_V1, key_id_));
            }
            return pid_wrap;
        }

        // cache op32 and remote_peer_id
        void cache_op32()
        {
            enable_op32 = proto.config->enable_op32;
            remote_peer_id = proto.config->remote_peer_id;
        }

        void set_state(const int newstate)
        {
            OVPN_LOG_VERBOSE(proto.debug_prefix()
                             << " KeyContext[" << key_id_ << "] "
                             << state_string(state) << " -> " << state_string(newstate));
            state = newstate;
        }

        void set_event(const EventType current)
        {
            OVPN_LOG_VERBOSE(proto.debug_prefix()
                             << " KeyContext[" << key_id_ << "] "
                             << event_type_string(current));
            current_event = current;
        }

        void set_event(const EventType current, const EventType next, const Time &next_time)
        {
            OVPN_LOG_VERBOSE(proto.debug_prefix()
                             << " KeyContext[" << key_id_ << "] "
                             << event_type_string(current) << " -> " << event_type_string(next)
                             << '(' << seconds_until(next_time) << ')');
            current_event = current;
            next_event = next;
            next_event_time = next_time;
        }

        void invalidate_callback() // called by ProtoStackBase when session is invalidated
        {
            reached_active_time_ = Time();
            next_event = KEV_NONE;
            next_event_time = Time::infinite();
        }

        // Trigger a renegotiation based on data flow condition such
        // as per-key data limit or packet ID approaching wraparound.
        void schedule_key_limit_renegotiation()
        {
            if (!key_limit_renegotiation_fired && state >= ACTIVE && !invalidated())
            {
                OVPN_LOG_VERBOSE(proto.debug_prefix() << " SCHEDULE KEY LIMIT RENEGOTIATION");

                key_limit_renegotiation_fired = true;
                proto.stats->error(Error::N_KEY_LIMIT_RENEG);

                // If primary, renegotiate now (within a second or two).
                // If secondary, queue the renegotiation request until
                // key reaches primary.
                if (next_event == KEV_BECOME_PRIMARY) // secondary key before transition to primary?
                {
                    // reneg request crosses over to primary,
                    // doesn't wipe next_event (KEV_BECOME_PRIMARY)
                    set_event(KEV_RENEGOTIATE_QUEUE);
                }
                else
                {
                    key_limit_reneg(KEV_RENEGOTIATE, *now);
                }
            }
        }

        // Handle data-limited keys such as Blowfish and other 64-bit block-size ciphers.
        bool data_limit_add(const DataLimit::Mode mode, const size_t size)
        {
            if (is_safe_conversion<DataLimit::size_type>(size))
                return false;
            const DataLimit::State state = data_limit->add(mode, static_cast<DataLimit::size_type>(size));
            if (state > DataLimit::None)
                data_limit_event(mode, state);
            return true;
        }

        // Handle a DataLimit event.
        void data_limit_event(const DataLimit::Mode mode, const DataLimit::State state)
        {
            OVPN_LOG_VERBOSE(proto.debug_prefix()
                             << " DATA LIMIT " << DataLimit::mode_str(mode)
                             << ' ' << DataLimit::state_str(state)
                             << " key_id=" << key_id_);

            // State values:
            //   DataLimit::Green -- first packet received and decrypted.
            //   DataLimit::Red -- data limit has been exceeded, so trigger a renegotiation.
            if (state == DataLimit::Red)
                schedule_key_limit_renegotiation();

            // When we are in KEV_PRIMARY_PENDING state, we must receive at least
            // one packet from the peer on this key before we transition to
            // KEV_BECOME_PRIMARY so we can transmit on it.
            if (next_event == KEV_PRIMARY_PENDING && data_limit->is_decrypt_green())
                set_event(KEV_NONE, KEV_BECOME_PRIMARY, *now + Time::Duration::seconds(1));
        }

        // Should we enter KEV_PRIMARY_PENDING state?  Do it if:
        // 1. we are a client,
        // 2. data limit is enabled,
        // 3. this is a renegotiated key in secondary context, i.e. not the first key, and
        // 4. no data received yet from peer on this key.
        bool data_limit_defer() const
        {
            return !proto.is_server()
                   && data_limit
                   && key_id_
                   && !data_limit->is_decrypt_green();
        }

        // General expiration set when key hits data limit threshold.
        Time data_limit_expire() const
        {
            return *now + (proto.config->handshake_window * 2);
        }

        void active_event()
        {
            set_event(KEV_ACTIVE,
                      KEV_BECOME_PRIMARY,
                      reached_active() + proto.config->become_primary);
        }

        void process_next_event()
        {
            if (*now >= next_event_time)
            {
                switch (next_event)
                {
                case KEV_BECOME_PRIMARY:
                    if (data_limit_defer())
                        set_event(KEV_NONE, KEV_PRIMARY_PENDING, data_limit_expire());
                    else
                        set_event(KEV_BECOME_PRIMARY,
                                  KEV_RENEGOTIATE,
                                  construct_time + proto.config->renegotiate);
                    break;
                case KEV_RENEGOTIATE:
                case KEV_RENEGOTIATE_FORCE:
                    prepare_expire(next_event);
                    break;
                case KEV_NEGOTIATE:
                    kev_error(KEV_NEGOTIATE, Error::KEV_NEGOTIATE_ERROR);
                    break;
                case KEV_PRIMARY_PENDING:
                    kev_error(KEV_PRIMARY_PENDING, Error::KEV_PENDING_ERROR);
                    break;
                case KEV_EXPIRE:
                    kev_error(KEV_EXPIRE, Error::N_KEV_EXPIRE);
                    break;
                default:
                    break;
                }
            }
        }

        void kev_error(const EventType ev, const Error::Type reason)
        {
            proto.stats->error(reason);
            invalidate(reason);
            set_event(ev);
        }

        unsigned int initial_op(const bool sender, const bool tls_crypt_v2) const
        {
            if (key_id_)
            {
                return CONTROL_SOFT_RESET_V1;
            }
            else
            {
                if (proto.is_server() == sender)
                    return CONTROL_HARD_RESET_SERVER_V2;

                if (!tls_crypt_v2)
                    return CONTROL_HARD_RESET_CLIENT_V2;
                else
                    return CONTROL_HARD_RESET_CLIENT_V3;
            }
        }

        void send_reset()
        {
            Packet pkt;
            pkt.opcode = initial_op(true, proto.tls_wrap_mode == TLS_CRYPT_V2);
            pkt.frame_prepare(*proto.config->frame, Frame::WRITE_SSL_INIT);
            raw_send(std::move(pkt));
        }

        bool parse_early_negotiation(const Packet &pkt)
        {
            /* The data in the early negotiation packet is structured as
             * TLV (type, length, value) */

            Buffer buf = pkt.buffer();
            while (!buf.empty())
            {
                if (buf.size() < 4)
                {
                    /* Buffer does not have enough bytes for type (uint16) and length (uint16) */
                    return false;
                }

                uint16_t type = read_uint16_length(buf);
                uint16_t len = read_uint16_length(buf);

                /* TLV defines a length that is larger than the remainder in the buffer.  */
                if (buf.size() < len)
                    return false;

                if (type == EARLY_NEG_FLAGS)
                {
                    if (len != 2)
                        return false;
                    uint16_t flags = read_uint16_length(buf);

                    if (flags & EARLY_NEG_FLAG_RESEND_WKC)
                    {
                        resend_wkc = true;
                    }
                }
                else
                {
                    /* skip over unknown types. We rather ignore undefined TLV to
                     * not needing to add bits initial reset message (where space
                     * is really tight) for optional features. */
                    buf.advance(len);
                }
            }
            return true;
        }


        void raw_recv(Packet &&raw_pkt) // called by ProtoStackBase
        {
            if (raw_pkt.opcode == initial_op(false, proto.tls_wrap_mode == TLS_CRYPT_V2))
            {
                switch (state)
                {
                case C_WAIT_RESET:
                    set_state(C_WAIT_RESET_ACK);
                    if (!parse_early_negotiation(raw_pkt))
                    {
                        invalidate(Error::EARLY_NEG_INVALID);
                    }
                    break;
                case S_WAIT_RESET:
                    send_reset();
                    set_state(S_WAIT_RESET_ACK);
                    break;
                }
            }
        }

        void app_recv(BufferPtr &&to_app_buf) // called by ProtoStackBase
        {
            app_recv_buf.put(std::move(to_app_buf));
            if (app_recv_buf.size() > APP_MSG_MAX)
                throw proto_error("app_recv: received control message is too large");
            BufferComposed::Complete bcc = app_recv_buf.complete();
            switch (state)
            {
            case C_WAIT_AUTH:
                if (recv_auth_complete(bcc))
                {
                    recv_auth(bcc.get());
                    set_state(C_WAIT_AUTH_ACK);
                }
                break;
            case S_WAIT_AUTH:
                if (recv_auth_complete(bcc))
                {
                    recv_auth(bcc.get());
                    send_auth();
                    set_state(S_WAIT_AUTH_ACK);
                }
                break;
            case S_WAIT_AUTH_ACK:
                // rare case where client receives auth, goes ACTIVE,
                // but the ACK response is dropped
            case ACTIVE:
                if (bcc.advance_to_null()) // does composed buffer contain terminating null char?
                    proto.app_recv(key_id_, bcc.get());
                break;
            }
        }

        void net_send(const Packet &net_pkt, const Base::NetSendType nstype) // called by ProtoStackBase
        {
            if (!is_reliable || nstype != Base::NET_SEND_RETRANSMIT) // retransmit packets on UDP only, not TCP
                proto.net_send(key_id_, net_pkt);
        }

        void post_ack_action()
        {
            if (state <= LAST_ACK_STATE && !rel_send.n_unacked())
            {
                switch (state)
                {
                case C_WAIT_RESET_ACK:
                    start_handshake();
                    send_auth();
                    set_state(C_WAIT_AUTH);
                    break;
                case S_WAIT_RESET_ACK:
                    start_handshake();
                    set_state(S_WAIT_AUTH);
                    break;
                case C_WAIT_AUTH_ACK:
                    active();
                    set_state(ACTIVE);
                    break;
                case S_WAIT_AUTH_ACK:
                    active();
                    set_state(ACTIVE);
                    break;
                }
            }
        }

        void send_auth()
        {
            auto buf = BufferAllocatedRc::Create();
            proto.config->frame->prepare(Frame::WRITE_SSL_CLEARTEXT, *buf);
            buf->write(proto_context_private::auth_prefix, sizeof(proto_context_private::auth_prefix));
            tlsprf->self_randomize(*proto.config->rng);
            tlsprf->self_write(*buf);
            const std::string options = proto.config->options_string();
            write_auth_string(options, *buf);
            if (!proto.is_server())
            {
                OVPN_LOG_INFO("Tunnel Options:" << options);
                buf->add_flags(BufAllocFlags::DESTRUCT_ZERO);
                if (proto.config->xmit_creds)
                    proto.client_auth(*buf);
                else
                {
                    write_empty_string(*buf); // username
                    write_empty_string(*buf); // password
                }
                const std::string peer_info = proto.config->peer_info_string(proto.proto_callback->supports_epoch_data());
                write_auth_string(peer_info, *buf);
            }
            app_send_validate(std::move(buf));
            dirty = true;
        }

        void recv_auth(BufferPtr buf)
        {
            const unsigned char *buf_pre = buf->read_alloc(sizeof(proto_context_private::auth_prefix));
            if (std::memcmp(buf_pre, proto_context_private::auth_prefix, sizeof(proto_context_private::auth_prefix)))
                throw proto_error("bad_auth_prefix");
            tlsprf->peer_read(*buf);
            const std::string options = read_auth_string<std::string>(*buf);
            if (proto.is_server())
            {
                const std::string username = read_auth_string<std::string>(*buf);
                const SafeString password = read_auth_string<SafeString>(*buf);
                const std::string peer_info = read_auth_string<std::string>(*buf);
                proto.proto_callback->server_auth(username, password, peer_info, Base::auth_cert());
            }
        }

        // return true if complete recv_auth message is contained in buffer
        bool recv_auth_complete(BufferComplete &bc) const
        {
            if (!bc.advance(sizeof(proto_context_private::auth_prefix)))
                return false;
            if (!tlsprf->peer_read_complete(bc))
                return false;
            if (!bc.advance_string()) // options
                return false;
            if (proto.is_server())
            {
                if (!bc.advance_string()) // username
                    return false;
                if (!bc.advance_string()) // password
                    return false;
                if (!bc.advance_string()) // peer_info
                    return false;
            }
            return true;
        }

        void active()
        {
            OVPN_LOG_INFO("TLS Handshake: " << Base::ssl_handshake_details());

            /* Our internal state machine only decides after push request what protocol
             * options we want to use. Therefore we also have to postpone data key
             * generation until this happens, create a empty DataChannelKey as
             * placeholder */
            data_channel_key.reset(new DataChannelKey());
            if (!proto.dc_deferred)
                init_data_channel();

            while (!app_pre_write_queue.empty())
            {
                app_send_validate(std::move(app_pre_write_queue.front()));
                app_pre_write_queue.pop_front();
                dirty = true;
            }
            reached_active_time_ = *now;
            proto.slowest_handshake_.max(reached_active_time_ - construct_time);
            active_event();
        }

        void prepend_dest_psid_and_acks(Buffer &buf, unsigned int opcode)
        {
            // if sending ACKs, prepend dest PSID
            if (xmit_acks.acks_ready())
            {
                if (proto.psid_peer.defined())
                    proto.psid_peer.prepend(buf);
                else
                {
                    proto.stats->error(Error::CC_ERROR);
                    throw proto_error("peer_psid_undef");
                }
            }

            // prepend ACKs for messages received from peer
            xmit_acks.prepend(buf, opcode == ACK_V1);
        }

        bool verify_src_psid(const ProtoSessionID &src_psid)
        {
            if (proto.psid_peer.defined())
            {
                if (!proto.psid_peer.match(src_psid))
                {
                    proto.stats->error(Error::CC_ERROR);
                    if (proto.is_tcp())
                        invalidate(Error::CC_ERROR);
                    return false;
                }
            }
            else
            {
                proto.psid_peer = src_psid;
            }
            return true;
        }

        bool verify_dest_psid(Buffer &buf)
        {
            ProtoSessionID dest_psid(buf);
            if (!proto.psid_self.match(dest_psid))
            {
                proto.stats->error(Error::CC_ERROR);
                if (proto.is_tcp())
                    invalidate(Error::CC_ERROR);
                return false;
            }
            return true;
        }

        void gen_head_tls_auth(const unsigned int opcode, Buffer &buf)
        {
            // write tls-auth packet ID
            proto.ta_pid_send.write_next(buf, true, now->seconds_since_epoch());

            // make space for tls-auth HMAC
            buf.prepend_alloc(proto.hmac_size);

            // write source PSID
            proto.psid_self.prepend(buf);

            // write opcode
            buf.push_front(op_compose(opcode, key_id_));

            // write hmac
            proto.ta_hmac_send->ovpn_hmac_gen(buf.data(),
                                              buf.size(),
                                              1 + ProtoSessionID::SIZE,
                                              proto.hmac_size,
                                              PacketIDControl::size());
        }

        void gen_head_tls_crypt(const unsigned int opcode, BufferAllocated &buf)
        {
            // in 'work' we store all the fields that are not supposed to be encrypted
            proto.config->frame->prepare(Frame::ENCRYPT_WORK, work);
            // make space for HMAC
            work.prepend_alloc(proto.hmac_size);
            // write tls-crypt packet ID
            proto.ta_pid_send.write_next(work, true, now->seconds_since_epoch());
            // write source PSID
            proto.psid_self.prepend(work);
            // write opcode
            work.push_front(op_compose(opcode, key_id_));

            // compute HMAC using header fields (from 'work') and plaintext
            // payload (from 'buf')
            proto.tls_crypt_send->hmac_gen(work.data(),
                                           TLSCryptContext::hmac_offset,
                                           buf.c_data(),
                                           buf.size());

            const size_t data_offset = TLSCryptContext::hmac_offset + proto.hmac_size;

            // encrypt the content of 'buf' (packet payload) into 'work'
            const size_t encrypt_bytes = proto.tls_crypt_send->encrypt(work.c_data() + TLSCryptContext::hmac_offset,
                                                                       work.data() + data_offset,
                                                                       work.max_size() - data_offset,
                                                                       buf.c_data(),
                                                                       buf.size());
            if (!encrypt_bytes)
            {
                buf.reset_size();
                return;
            }
            work.inc_size(encrypt_bytes);

            // append WKc to wrapped packet for tls-crypt-v2
            if ((opcode == CONTROL_HARD_RESET_CLIENT_V3 || opcode == CONTROL_WKC_V1)
                && (proto.tls_wrap_mode == TLS_CRYPT_V2))
                proto.tls_crypt_append_wkc(work);

            // 'work' now contains the complete packet ready to go. swap it with 'buf'
            buf.swap(work);
        }

        void gen_head_tls_plain(const unsigned int opcode, Buffer &buf)
        {
            // write source PSID
            proto.psid_self.prepend(buf);
            // write opcode
            buf.push_front(op_compose(opcode, key_id_));
        }

        void gen_head(const unsigned int opcode, BufferAllocated &buf)
        {
            switch (proto.tls_wrap_mode)
            {
            case TLS_AUTH:
                gen_head_tls_auth(opcode, buf);
                break;
            case TLS_CRYPT:
            case TLS_CRYPT_V2:
                gen_head_tls_crypt(opcode, buf);
                break;
            case TLS_PLAIN:
                gen_head_tls_plain(opcode, buf);
                break;
            }
        }

        void encapsulate(id_t id, Packet &pkt) // called by ProtoStackBase
        {
            BufferAllocated &buf = *pkt.buf;

            // prepend message sequence number
            ReliableAck::prepend_id(buf, id);

            // prepend dest PSID and ACKs to reply to peer
            prepend_dest_psid_and_acks(buf, pkt.opcode);

            // generate message head
            int opcode = pkt.opcode;
            if (id == 1 && resend_wkc)
            {
                opcode = CONTROL_WKC_V1;
            }

            gen_head(opcode, buf);
        }

        void generate_ack(Packet &pkt) // called by ProtoStackBase
        {
            BufferAllocated &buf = *pkt.buf;

            // prepend dest PSID and ACKs to reply to peer
            prepend_dest_psid_and_acks(buf, pkt.opcode);

            gen_head(ACK_V1, buf);
        }

        bool decapsulate_post_process(Packet &pkt, ProtoSessionID &src_psid, const PacketIDControl pid)
        {
            Buffer &recv = *pkt.buf;

            // update our last-packet-received time
            proto.update_last_received();

            // verify source PSID
            if (!verify_src_psid(src_psid))
                return false;

            // get current time_t
            const PacketIDControl::time_t t = now->seconds_since_epoch();
            // verify tls_auth/crypt packet ID
            const bool pid_ok = proto.ta_pid_recv.test_add(pid, t, false);

            // process ACKs sent by peer (if packet ID check failed,
            // read the ACK IDs, but don't modify the rel_send object).
            if (ReliableAck::ack(rel_send, recv, pid_ok))
            {
                // make sure that our own PSID is contained in packet received from peer
                if (!verify_dest_psid(recv))
                    return false;
            }

            // for CONTROL packets only, not ACK
            if (pkt.opcode != ACK_V1)
            {
                // get message sequence number
                const id_t id = ReliableAck::read_id(recv);

                if (pid_ok)
                {
                    // try to push message into reliable receive object
                    const unsigned int rflags = rel_recv.receive(pkt, id);

                    // should we ACK packet back to sender?
                    if (rflags & ReliableRecv::ACK_TO_SENDER)
                        xmit_acks.push_back(id); // ACK packet to sender

                    // was packet accepted by reliable receive object?
                    if (rflags & ReliableRecv::IN_WINDOW)
                    {
                        // remember tls_auth packet ID so that it can't be replaye
                        proto.ta_pid_recv.test_add(pid, t, true);
                        return true;
                    }
                }
                else // treat as replay
                {
                    proto.stats->error(Error::REPLAY_ERROR);
                    if (pid.is_valid())
                        // even replayed packets must be ACKed or protocol could deadlock
                        xmit_acks.push_back(id);
                }
            }
            else
            {
                if (pid_ok)
                    // remember tls_auth packet ID of ACK packet to prevent replay
                    proto.ta_pid_recv.test_add(pid, t, true);
                else
                    proto.stats->error(Error::REPLAY_ERROR);
            }
            return false;
        }

        bool decapsulate_tls_auth(Packet &pkt)
        {
            Buffer &recv = *pkt.buf;
            const unsigned char *orig_data = recv.data();
            const size_t orig_size = recv.size();

            // advance buffer past initial op byte
            recv.advance(1);

            // get source PSID
            ProtoSessionID src_psid(recv);

            // verify HMAC
            {
                recv.advance(proto.hmac_size);
                if (!proto.ta_hmac_recv->ovpn_hmac_cmp(orig_data,
                                                       orig_size,
                                                       1 + ProtoSessionID::SIZE,
                                                       proto.hmac_size,
                                                       PacketIDControl::size()))
                {
                    proto.stats->error(Error::HMAC_ERROR);
                    if (proto.is_tcp())
                        invalidate(Error::HMAC_ERROR);
                    return false;
                }
            }

            // read tls_auth packet ID
            const PacketIDControl pid = proto.ta_pid_recv.read_next(recv);

            return decapsulate_post_process(pkt, src_psid, pid);
        }

        bool decapsulate_tls_crypt(Packet &pkt)
        {
            auto &recv = *pkt.buf;
            const unsigned char *orig_data = recv.data();
            const size_t orig_size = recv.size();

            // advance buffer past initial op byte
            recv.advance(1);
            // get source PSID
            ProtoSessionID src_psid(recv);
            // get tls-crypt packet ID
            const PacketIDControl pid = proto.ta_pid_recv.read_next(recv);
            // skip the hmac
            recv.advance(proto.hmac_size);

            const size_t data_offset = TLSCryptContext::hmac_offset + proto.hmac_size;
            if (orig_size < data_offset)
                return false;

            // decrypt payload
            proto.config->frame->prepare(Frame::DECRYPT_WORK, work);

            const size_t decrypt_bytes = proto.tls_crypt_recv->decrypt(orig_data + TLSCryptContext::hmac_offset,
                                                                       work.data(),
                                                                       work.max_size(),
                                                                       recv.c_data(),
                                                                       recv.size());
            if (!decrypt_bytes)
            {
                proto.stats->error(Error::DECRYPT_ERROR);
                if (proto.is_tcp())
                    invalidate(Error::DECRYPT_ERROR);
                return false;
            }

            work.inc_size(decrypt_bytes);

            // verify HMAC
            if (!proto.tls_crypt_recv->hmac_cmp(orig_data,
                                                TLSCryptContext::hmac_offset,
                                                work.c_data(),
                                                work.size()))
            {
                proto.stats->error(Error::HMAC_ERROR);
                if (proto.is_tcp())
                    invalidate(Error::HMAC_ERROR);
                return false;
            }

            // move the decrypted payload to 'recv', so that the processing of the
            // packet can continue
            recv.swap(work);

            return decapsulate_post_process(pkt, src_psid, pid);
        }

        bool decapsulate_tls_plain(Packet &pkt)
        {
            Buffer &recv = *pkt.buf;

            // update our last-packet-received time
            proto.update_last_received();

            // advance buffer past initial op byte
            recv.advance(1);

            // verify source PSID
            ProtoSessionID src_psid(recv);
            if (!verify_src_psid(src_psid))
                return false;

            // process ACKs sent by peer
            if (ReliableAck::ack(rel_send, recv, true))
            {
                // make sure that our own PSID is in packet received from peer
                if (!verify_dest_psid(recv))
                    return false;
            }

            // for CONTROL packets only, not ACK
            if (pkt.opcode != ACK_V1)
            {
                // get message sequence number
                const id_t id = ReliableAck::read_id(recv);

                // try to push message into reliable receive object
                const unsigned int rflags = rel_recv.receive(pkt, id);

                // should we ACK packet back to sender?
                if (rflags & ReliableRecv::ACK_TO_SENDER)
                    xmit_acks.push_back(id); // ACK packet to sender

                // was packet accepted by reliable receive object?
                if (rflags & ReliableRecv::IN_WINDOW)
                    return true;
            }
            return false;
        }

        /**
         * @brief  Extract and process the TLS crypt WKc information.
         * @param  recv             Buffer containing the raw packet.
         * @return true on success.
         */
        bool unwrap_tls_crypt_wkc(Buffer &recv)
        {
            // the ``WKc`` is located at the end of the packet, after the tls-crypt
            // payload.
            //
            // K_id is optional, and controlled by proto.config->tls_crypt_v2_serverkey_id.
            // If it is missing, we will use a single server key for all clients.
            //
            // Format is as follows:
            //
            // ``len = len(WKc)`` (16 bit, network byte order)
            // ``T = HMAC-SHA256(Ka, len || K_id || Kc || metadata)``
            // ``IV = 128 most significant bits of T``
            // ``WKc = T || AES-256-CTR(Ke, IV, Kc || metadata) || K_id || len``

            const unsigned char *orig_data = recv.data();
            const size_t orig_size = recv.size();
            const size_t hmac_size = proto.config->tls_crypt_context->digest_size();
            const size_t tls_frame_size = 1 + ProtoSessionID::SIZE
                                          + PacketIDControl::size()
                                          + hmac_size
                                          // the following is the tls-crypt payload
                                          + sizeof(char)  // length of ACK array
                                          + sizeof(id_t); // reliable ID

            // check that at least the authentication tag ``T`` is present
            if (orig_size < (tls_frame_size + hmac_size))
                return false;

            // the ``WKc`` is just appended after the standard tls-crypt frame
            const unsigned char *wkc_raw = orig_data + tls_frame_size;
            const size_t wkc_raw_size = orig_size - tls_frame_size - sizeof(uint16_t);
            // retrieve the ``WKc`` len from the bottom of the packet and convert it to Host Order
            uint16_t wkc_len;
            // avoid unaligned access
            std::memcpy(&wkc_len, wkc_raw + wkc_raw_size, sizeof(wkc_len));
            wkc_len = ntohs(wkc_len);

            uint32_t k_id = 0;
            const size_t serverkey_id_size = proto.config->tls_crypt_v2_serverkey_id ? sizeof(k_id) : 0;

            if (proto.config->tls_crypt_v2_serverkey_id)
            {
                std::memcpy(&k_id, wkc_raw + wkc_raw_size - serverkey_id_size, sizeof(k_id));
                k_id = ntohl(k_id);
            }

            // length sanity check (the size of the ``len`` field is included in the value)
            if ((wkc_len - sizeof(uint16_t)) != wkc_raw_size)
                return false;

            BufferAllocated plaintext(wkc_len, BufAllocFlags::CONSTRUCT_ZERO);
            // plaintext will be used to compute the Auth Tag, therefore start by prepending
            // the WKc length in network order
            wkc_len = htons(wkc_len);
            plaintext.write(&wkc_len, sizeof(wkc_len));

            if (proto.config->tls_crypt_v2_serverkey_id)
            {
                std::stringstream ss;
                ss << std::hex << std::setfill('0') << std::uppercase << std::setw(8) << k_id;

                const std::string serverkey_fn = ss.str() + ".key";
                const std::string serverkey_path = proto.config->tls_crypt_v2_serverkey_dir + "/"
                                                   + serverkey_fn.substr(0, 2) + "/" + serverkey_fn;

                // If the key is missing, an exception will be thrown here, for example:
                // "cannot open for read: <KEYS_DIR>/06/063FE634.key"
                const std::string serverkey = read_text(serverkey_path);

                OVPN_LOG_VERBOSE(proto.debug_prefix() << " Using TLS-crypt-V2 server key " << serverkey_path);

                TLSCryptV2ServerKey tls_crypt_v2_key;
                tls_crypt_v2_key.parse(serverkey);
                tls_crypt_v2_key.extract_key(proto.config->tls_crypt_key);

                // the server key is composed by one key set only, therefore direction and
                // mode should not be specified when slicing
                proto.tls_crypt_server->init(proto.config->ssl_factory->libctx(),
                                             proto.config->tls_crypt_key.slice(OpenVPNStaticKey::HMAC),
                                             proto.config->tls_crypt_key.slice(OpenVPNStaticKey::CIPHER));

                k_id = htonl(k_id);
                plaintext.write(&k_id, sizeof(k_id));
            }

            const size_t decrypt_bytes = proto.tls_crypt_server->decrypt(wkc_raw,
                                                                         plaintext.data() + 2 + serverkey_id_size,
                                                                         plaintext.max_size() - 2 - serverkey_id_size,
                                                                         wkc_raw + hmac_size,
                                                                         wkc_raw_size - hmac_size - serverkey_id_size);
            plaintext.inc_size(decrypt_bytes);
            // decrypted data must at least contain a full 2048bits client key
            // (metadata is optional)
            if (plaintext.size() < OpenVPNStaticKey::KEY_SIZE)
            {
                proto.stats->error(Error::DECRYPT_ERROR);
                if (proto.is_tcp())
                    invalidate(Error::DECRYPT_ERROR);
                return false;
            }

            if (!proto.tls_crypt_server->hmac_cmp(wkc_raw,
                                                  0,
                                                  plaintext.c_data(),
                                                  plaintext.size()))
            {
                proto.stats->error(Error::HMAC_ERROR);
                if (proto.is_tcp())
                    invalidate(Error::HMAC_ERROR);
                return false;
            }

            // we can now remove the WKc length (and the server key ID, if present)
            // from the plaintext, as they are not really part of the key material
            plaintext.advance(sizeof(wkc_len));

            if (proto.config->tls_crypt_v2_serverkey_id)
                plaintext.advance(sizeof(k_id));

            // WKc has been authenticated: it contains the client key followed
            // by the optional metadata. Let's initialize the tls-crypt context
            // with the client key

            OpenVPNStaticKey client_key;
            plaintext.read(client_key.raw_alloc(), OpenVPNStaticKey::KEY_SIZE);
            proto.reset_tls_crypt(*proto.config, client_key);

            // verify metadata
            int metadata_type = -1;
            if (!plaintext.empty())
                metadata_type = plaintext.pop_front();

            if (!proto.tls_crypt_metadata->verify(metadata_type, plaintext))
            {
                proto.stats->error(Error::TLS_CRYPT_META_FAIL);
                return false;
            }

            // virtually remove the WKc from the packet
            recv.set_size(tls_frame_size);

            return true;
        }

        bool decapsulate(Packet &pkt) // called by ProtoStackBase
        {
            try
            {
                if (proto.is_server()
                    && proto.tls_wrap_mode != TLS_CRYPT_V2
                    && proto.config->tls_crypt_v2_enabled()
                    && pkt.opcode == CONTROL_HARD_RESET_CLIENT_V3)
                {
                    // setup key to be used to unwrap WKc upon client connection.
                    // tls-crypt session key setup is postponed to reception of WKc
                    // from client
                    proto.reset_tls_crypt_server(*proto.config);

                    proto.tls_wrap_mode = TLS_CRYPT_V2;
                    proto.hmac_size = proto.config->tls_crypt_context->digest_size();

                    // init tls_crypt packet ID
                    proto.ta_pid_send.init(EARLY_NEG_START);
                    proto.ta_pid_recv.init("SSL-CC", 0, proto.stats);
                }

                switch (proto.tls_wrap_mode)
                {
                case TLS_AUTH:
                    return decapsulate_tls_auth(pkt);
                case TLS_CRYPT_V2:
                    if (pkt.opcode == CONTROL_HARD_RESET_CLIENT_V3)
                    {
                        // unwrap WKc and extract Kc (client key) from packet.
                        // This way we can initialize the tls-crypt per-client contexts
                        // (this happens on the server side only)
                        if (!unwrap_tls_crypt_wkc(*pkt.buf))
                        {
                            return false;
                        }
                    }
                    // now that the tls-crypt contexts have been initialized it is
                    // possible to proceed with the standard tls-crypt decapsulation
                    /* no break */
                case TLS_CRYPT:
                    return decapsulate_tls_crypt(pkt);
                case TLS_PLAIN:
                    return decapsulate_tls_plain(pkt);
                }
            }
            catch (BufferException &)
            {
                proto.stats->error(Error::BUFFER_ERROR);
                if (proto.is_tcp())
                    invalidate(Error::BUFFER_ERROR);
            }
            return false;
        }

        // for debugging
        static const char *state_string(const int s)
        {
            switch (s)
            {
            case C_WAIT_RESET_ACK:
                return "C_WAIT_RESET_ACK";
            case C_WAIT_AUTH_ACK:
                return "C_WAIT_AUTH_ACK";
            case S_WAIT_RESET_ACK:
                return "S_WAIT_RESET_ACK";
            case S_WAIT_AUTH_ACK:
                return "S_WAIT_AUTH_ACK";
            case C_INITIAL:
                return "C_INITIAL";
            case C_WAIT_RESET:
                return "C_WAIT_RESET";
            case C_WAIT_AUTH:
                return "C_WAIT_AUTH";
            case S_INITIAL:
                return "S_INITIAL";
            case S_WAIT_RESET:
                return "S_WAIT_RESET";
            case S_WAIT_AUTH:
                return "S_WAIT_AUTH";
            case ACTIVE:
                return "ACTIVE";
            default:
                return "STATE_UNDEF";
            }
        }

        // for debugging
        int seconds_until(const Time &next_time)
        {
            Time::Duration d = next_time - *now;
            if (d.is_infinite())
                return -1;
            else
                return numeric_cast<int>(d.to_seconds());
        }

        // BEGIN KeyContext data members

        ProtoContext &proto; // parent
        int state;
        unsigned int key_id_;
        unsigned int crypto_flags;
        int remote_peer_id; // -1 to disable
        bool enable_op32;
        /* early negotiation enabled resending of wrapped tls-crypt-v2 client key
         * with third packet of the three-way handshake
         */
        bool resend_wkc = false;
        bool dirty;
        bool key_limit_renegotiation_fired;
        bool is_reliable;
        Compress::Ptr compress;
        CryptoDCInstance::Ptr crypto;
        TLSPRFInstance::Ptr tlsprf;
        Time construct_time;
        Time reached_active_time_;
        Time next_event_time;
        EventType current_event;
        EventType next_event;
        std::deque<BufferPtr> app_pre_write_queue;
        std::unique_ptr<DataChannelKey> data_channel_key;
        BufferComposed app_recv_buf;
        std::unique_ptr<DataLimit> data_limit;
        BufferAllocated work;

        // static member used by validate_tls_crypt()
        static BufferAllocated static_work;
    };

  public:
    class PsidCookieHelper
    {
      public:
        PsidCookieHelper(unsigned int op_field)
            : op_code_(opcode_extract(op_field)), key_id_(key_id_extract(op_field))
        {
        }

        bool is_clients_initial_reset() const
        {
            return key_id_ == 0 && op_code_ == CONTROL_HARD_RESET_CLIENT_V2;
        }

        bool is_clients_server_reset_ack() const
        {
            return key_id_ == 0 && (op_code_ == CONTROL_V1 || op_code_ == ACK_V1);
        }

        static unsigned char get_server_hard_reset_opfield()
        {
            return op_compose(CONTROL_HARD_RESET_SERVER_V2, 0);
        }

      private:
        const unsigned int op_code_;
        const unsigned int key_id_;
    };

    class IvProtoHelper
    {
      public:
        IvProtoHelper(const OptionList &peer_info)
            : proto_field_(peer_info.get_num<unsigned int>("IV_PROTO", 1, 0))
        {
        }

        bool client_supports_ekm_key_method() const
        {
            return proto_field_ & iv_proto_flag::IV_PROTO_TLS_KEY_EXPORT;
        }

        bool client_supports_temp_auth_failed() const
        {
            return proto_field_ & iv_proto_flag::IV_PROTO_AUTH_FAIL_TEMP;
        }

        bool client_supports_data_v2() const
        {
            return proto_field_ & iv_proto_flag::IV_PROTO_DATA_V2;
        }

        bool client_supports_auth_pending_kwargs() const
        {
            return proto_field_ & iv_proto_flag::IV_PROTO_AUTH_PENDING_KW;
        }

        bool client_supports_push_update() const
        {
            return proto_field_ & iv_proto_flag::IV_PROTO_PUSH_UPDATE;
        }

        bool client_supports_request_push() const
        {
            return proto_field_ & iv_proto_flag::IV_PROTO_REQUEST_PUSH;
        }

        //! Checks if the client is able to send an explicit EXIT message before exiting.
        bool client_supports_exit_notify() const
        {
            return proto_field_ & iv_proto_flag::IV_PROTO_CC_EXIT_NOTIFY;
        }

        //! Checks if the client can handle dynamic TLS-crypt.
        bool client_supports_dynamic_tls_crypt() const
        {
            return proto_field_ & iv_proto_flag::IV_PROTO_DYN_TLS_CRYPT;
        }

        //! Checks if the client can handle `dns` (as opposed to `dhcp-option`).
        bool client_supports_dns_option() const
        {
            return proto_field_ & iv_proto_flag::IV_PROTO_DNS_OPTION_V2;
        }

      private:
        unsigned int proto_field_;
    };

    class TLSWrapPreValidate : public RC<thread_unsafe_refcount>
    {
      public:
        typedef RCPtr<TLSWrapPreValidate> Ptr;

        virtual bool validate(const BufferAllocated &net_buf) = 0;
    };

    // Validate the integrity of a packet, only considering tls-auth HMAC.
    class TLSAuthPreValidate : public TLSWrapPreValidate
    {
      public:
        OPENVPN_SIMPLE_EXCEPTION(tls_auth_pre_validate);

        TLSAuthPreValidate(const ProtoConfig &c, const bool server)
        {
            if (!c.tls_auth_enabled())
                throw tls_auth_pre_validate();

            // save hard reset op we expect to receive from peer
            reset_op = server ? CONTROL_HARD_RESET_CLIENT_V2 : CONTROL_HARD_RESET_SERVER_V2;

            // init OvpnHMACInstance
            ta_hmac_recv = c.tls_auth_context->new_obj();

            // init tls_auth hmac
            if (c.key_direction >= 0)
            {
                // key-direction is 0 or 1
                const unsigned int key_dir = c.key_direction
                                                 ? OpenVPNStaticKey::INVERSE
                                                 : OpenVPNStaticKey::NORMAL;
                ta_hmac_recv->init(c.tls_auth_key.slice(OpenVPNStaticKey::HMAC | OpenVPNStaticKey::DECRYPT | key_dir));
            }
            else
            {
                // key-direction bidirectional mode
                ta_hmac_recv->init(c.tls_auth_key.slice(OpenVPNStaticKey::HMAC));
            }
        }

        bool validate(const BufferAllocated &net_buf)
        {
            try
            {
                if (!net_buf.size())
                    return false;

                const unsigned int op = net_buf[0];
                if (opcode_extract(op) != reset_op || key_id_extract(op) != 0)
                    return false;

                return ta_hmac_recv->ovpn_hmac_cmp(net_buf.c_data(),
                                                   net_buf.size(),
                                                   1 + ProtoSessionID::SIZE,
                                                   ta_hmac_recv->output_size(),
                                                   PacketIDControl::size());
            }
            catch (BufferException &)
            {
            }

            return false;
        }

      private:
        OvpnHMACInstance::Ptr ta_hmac_recv;
        unsigned int reset_op;
    };

    class TLSCryptPreValidate : public TLSWrapPreValidate
    {
      public:
        OPENVPN_SIMPLE_EXCEPTION(tls_crypt_pre_validate);

        TLSCryptPreValidate(const ProtoConfig &c, const bool server)
        {
            const bool tls_crypt_v2_enabled = c.tls_crypt_v2_enabled();

            if (!c.tls_crypt_enabled() && !tls_crypt_v2_enabled)
                throw tls_crypt_pre_validate();

            // save hard reset op we expect to receive from peer
            reset_op = CONTROL_HARD_RESET_SERVER_V2;

            if (server)
            {
                // We can't pre-validate because we haven't extracted the server key from
                // the server key ID that's present in the client key yet.
                if (tls_crypt_v2_enabled && c.tls_crypt_v2_serverkey_id)
                {
                    disabled = true;
                    return;
                }

                reset_op = tls_crypt_v2_enabled
                               ? CONTROL_HARD_RESET_CLIENT_V3
                               : CONTROL_HARD_RESET_CLIENT_V2;
            }

            tls_crypt_recv = c.tls_crypt_context->new_obj_recv();

            // static direction assignment - not user configurable
            const unsigned int key_dir = server ? OpenVPNStaticKey::NORMAL : OpenVPNStaticKey::INVERSE;
            tls_crypt_recv->init(c.ssl_factory->libctx(),
                                 c.tls_crypt_key.slice(OpenVPNStaticKey::HMAC | OpenVPNStaticKey::DECRYPT | key_dir),
                                 c.tls_crypt_key.slice(OpenVPNStaticKey::CIPHER | OpenVPNStaticKey::DECRYPT | key_dir));

            // needed to create the decrypt buffer during validation
            frame = c.frame;
        }

        bool validate(const BufferAllocated &net_buf)
        {
            if (disabled)
                return true;

            try
            {
                if (!net_buf.size())
                    return false;

                const unsigned int op = net_buf[0];
                if (opcode_extract(op) != reset_op || key_id_extract(op) != 0)
                    return false;

                const size_t data_offset = TLSCryptContext::hmac_offset + tls_crypt_recv->output_hmac_size();
                if (net_buf.size() < data_offset)
                    return false;

                frame->prepare(Frame::DECRYPT_WORK, work);

                // decrypt payload from 'net_buf' into 'work'
                const size_t decrypt_bytes = tls_crypt_recv->decrypt(net_buf.c_data() + TLSCryptContext::hmac_offset,
                                                                     work.data(),
                                                                     work.max_size(),
                                                                     net_buf.c_data() + data_offset,
                                                                     net_buf.size() - data_offset);
                if (!decrypt_bytes)
                    return false;

                work.inc_size(decrypt_bytes);

                // verify HMAC
                return tls_crypt_recv->hmac_cmp(net_buf.c_data(),
                                                TLSCryptContext::hmac_offset,
                                                work.data(),
                                                work.size());
            }
            catch (BufferException &)
            {
            }
            return false;
        }

      protected:
        unsigned int reset_op;

      private:
        TLSCryptInstance::Ptr tls_crypt_recv;
        Frame::Ptr frame;
        BufferAllocated work;
        bool disabled = false;
    };

    OPENVPN_SIMPLE_EXCEPTION(select_key_context_error);

    ProtoContext(ProtoContextCallbackInterface *cb_arg,
                 const ProtoConfig::Ptr &config_arg, // configuration
                 const SessionStats::Ptr &stats_arg) // error stats
        : proto_callback(cb_arg),
          config(config_arg),
          stats(stats_arg),
          mode_(config_arg->ssl_factory->mode()),
          n_key_ids(0),
          now_(config_arg->now)
    {
        reset_tls_wrap_mode(*config);
    }

    void reset_tls_wrap_mode(const ProtoConfig &c)
    {
        // Prefer TLS auth as the default if both TLS crypt V2 and TLS auth
        // are enabled.
        if (c.tls_crypt_v2_enabled() && !c.tls_auth_enabled())
        {
            tls_wrap_mode = TLS_CRYPT_V2;

            // get HMAC size from Digest object
            hmac_size = c.tls_crypt_context->digest_size();

            return;
        }

        if (c.tls_crypt_enabled() && !c.tls_auth_enabled())
        {
            tls_wrap_mode = TLS_CRYPT;

            // get HMAC size from Digest object
            hmac_size = c.tls_crypt_context->digest_size();

            return;
        }

        if (c.tls_auth_enabled())
        {
            tls_wrap_mode = TLS_AUTH;

            // get HMAC size from Digest object
            hmac_size = c.tls_auth_context->size();

            return;
        }

        tls_wrap_mode = TLS_PLAIN;
        hmac_size = 0;
    }

    uint32_t get_tls_warnings() const
    {
        if (primary)
            return primary->get_tls_warnings();

        OPENVPN_LOG("TLS: primary key context uninitialized. Can't retrieve TLS warnings");
        return 0;
    }

    bool uses_bs64_cipher() const
    {
        return is_bs64_cipher(conf().dc.cipher());
    }

    void reset_tls_crypt(const ProtoConfig &c, const OpenVPNStaticKey &key)
    {
        tls_crypt_send = c.tls_crypt_context->new_obj_send();
        tls_crypt_recv = c.tls_crypt_context->new_obj_recv();

        // static direction assignment - not user configurable
        unsigned int key_dir = is_server() ? OpenVPNStaticKey::NORMAL : OpenVPNStaticKey::INVERSE;

        tls_crypt_send->init(c.ssl_factory->libctx(),
                             key.slice(OpenVPNStaticKey::HMAC | OpenVPNStaticKey::ENCRYPT | key_dir),
                             key.slice(OpenVPNStaticKey::CIPHER | OpenVPNStaticKey::ENCRYPT | key_dir));
        tls_crypt_recv->init(c.ssl_factory->libctx(),
                             key.slice(OpenVPNStaticKey::HMAC | OpenVPNStaticKey::DECRYPT | key_dir),
                             key.slice(OpenVPNStaticKey::CIPHER | OpenVPNStaticKey::DECRYPT | key_dir));
    }

    void set_dynamic_tls_crypt(const ProtoConfig &c, const KeyContext::Ptr &key_ctx)
    {
        OpenVPNStaticKey dyn_key;
        key_ctx->export_key_material(dyn_key, "EXPORTER-OpenVPN-dynamic-tls-crypt");

        if (c.tls_auth_enabled())
            dyn_key.XOR(c.tls_auth_key);
        else if (c.tls_crypt_enabled() || c.tls_crypt_v2_enabled())
            dyn_key.XOR(c.tls_crypt_key);

        tls_wrap_mode = TLS_CRYPT;

        // get HMAC size from Digest object
        hmac_size = c.tls_crypt_context->digest_size();

        ta_pid_send.init();
        ta_pid_recv.init("SSL-CC", 0, stats);

        reset_tls_crypt(c, dyn_key);
    }

    void reset_tls_crypt_server(const ProtoConfig &c)
    {
        // tls-crypt session key is derived later from WKc received from the client
        tls_crypt_send.reset();
        tls_crypt_recv.reset();

        // server context is used only to process incoming WKc's
        tls_crypt_server = c.tls_crypt_context->new_obj_recv();

        if (!c.tls_crypt_v2_serverkey_id)
        {
            // the server key is composed by one key set only, therefore direction and
            // mode should not be specified when slicing
            tls_crypt_server->init(c.ssl_factory->libctx(),
                                   c.tls_crypt_key.slice(OpenVPNStaticKey::HMAC),
                                   c.tls_crypt_key.slice(OpenVPNStaticKey::CIPHER));
        }

        tls_crypt_metadata = c.tls_crypt_metadata_factory->new_obj();
    }

    /**
     * @brief Resets ProtoContext *this to it's initial state
     *
     * @param cookie_psid the ProtoSessionID parameter that allows a server
     *  implementation using the psid cookie mechanism to pass in the verified hmac
     *  server session cookie.  In the client implementation, the parameter is
     *  meaningless and defaults to an empty ProtoSessionID which is created at compile
     *  time since the default ProtoSessionID ctor is constexpr.  For the default
     *  cookie_psid, defined() returns false (vs true for the verified session cookie)
     *  so the absence of a parameter selects the correct code path.
     */
    void reset(const ProtoSessionID cookie_psid = ProtoSessionID())
    {
        const ProtoConfig &c = *config;

        // defer data channel initialization until after client options pull?
        dc_deferred = c.dc_deferred;

        // clear key contexts
        reset_all();

        // start with key ID 0
        upcoming_key_id = 0;

        unsigned int key_dir;
        const PacketIDControl::id_t EARLY_NEG_START = 0x0f000000;

        // tls-auth initialization
        reset_tls_wrap_mode(c);
        switch (tls_wrap_mode)
        {
        case TLS_CRYPT:
            reset_tls_crypt(c, c.tls_crypt_key);
            // init tls_crypt packet ID
            ta_pid_send.init();
            ta_pid_recv.init("SSL-CC", 0, stats);
            break;
        case TLS_CRYPT_V2:
            if (is_server())
                // setup key to be used to unwrap WKc upon client connection.
                // tls-crypt session key setup is postponed to reception of WKc
                // from client
                reset_tls_crypt_server(c);
            else
                reset_tls_crypt(c, c.tls_crypt_key);
            /** tls-auth/tls-crypt packet id. We start with a different id here
             * to indicate EARLY_NEG_START/CONTROL_WKC_V1 support */
            // init tls_crypt packet ID
            ta_pid_send.init(EARLY_NEG_START);
            ta_pid_recv.init("SSL-CC", 0, stats);
            break;
        case TLS_AUTH:
            // init OvpnHMACInstance
            ta_hmac_send = c.tls_auth_context->new_obj();
            ta_hmac_recv = c.tls_auth_context->new_obj();

            // init tls_auth hmac
            if (c.key_direction >= 0)
            {
                // key-direction is 0 or 1
                key_dir = c.key_direction ? OpenVPNStaticKey::INVERSE : OpenVPNStaticKey::NORMAL;
                ta_hmac_send->init(c.tls_auth_key.slice(OpenVPNStaticKey::HMAC | OpenVPNStaticKey::ENCRYPT | key_dir));
                ta_hmac_recv->init(c.tls_auth_key.slice(OpenVPNStaticKey::HMAC | OpenVPNStaticKey::DECRYPT | key_dir));
            }
            else
            {
                // key-direction bidirectional mode
                ta_hmac_send->init(c.tls_auth_key.slice(OpenVPNStaticKey::HMAC));
                ta_hmac_recv->init(c.tls_auth_key.slice(OpenVPNStaticKey::HMAC));
            }

            /**
             * @brief Initialize tls_auth packet ID for the send case
             *
             * The second argument sets the expected packet id.  If the server
             * implementation is using the psid cookie mechanism, the state creation is
             * deferred until the client's second packet, id 1, is received; otherwise we
             * expect to handle the 1st packet, id 0.
             *
             */
            ta_pid_send.init(cookie_psid.defined() ? 1 : 0);
            ta_pid_recv.init("SSL-CC", 0, stats);
            break;
        case TLS_PLAIN:
            break;
        }

        // initialize proto session ID
        if (cookie_psid.defined())
            psid_self = cookie_psid;
        else
            psid_self.randomize(*c.rng);
        psid_peer.reset();

        // initialize key contexts
        primary.reset(new KeyContext(*this, is_client(), cookie_psid.defined()));
        OVPN_LOG_VERBOSE(debug_prefix() << " New KeyContext PRIMARY id=" << primary->key_id());

        // initialize keepalive timers
        keepalive_expire = Time::infinite(); // initially disabled
        update_last_sent();                  // set timer for initial keepalive send
    }

    void set_protocol(const Protocol &p)
    {
        config->set_protocol(p);
        if (primary)
            primary->set_protocol(p);
        if (secondary)
            secondary->set_protocol(p);
    }

    // Free up space when parent object has been halted but
    // object destruction is not immediately scheduled.
    void pre_destroy()
    {
        reset_all();
    }

    // Is primary key defined
    bool primary_defined()
    {
        return bool(primary);
    }

    virtual ~ProtoContext() = default;

    // return the PacketType of an incoming network packet
    PacketType packet_type(const Buffer &buf)
    {
        return PacketType(buf, *this);
    }

    /**
     * @brief Initialize the state machine and start protocol negotiation
     *
     * Called by both derived client and server protocol classes, this function hands
     * off to the implementation in KeyContext::start()
     *
     * @param cookie_psid  see ProtoContext::reset()
     */
    void start(const ProtoSessionID cookie_psid = ProtoSessionID())
    {
        if (!primary)
            throw proto_error("start: no primary key");
        primary->start(cookie_psid);
        update_last_received(); // set an upper bound on when we expect a response
    }

    // trigger a protocol renegotiation
    void renegotiate()
    {
        // set up dynamic tls-crypt keys when the first rekeying happens
        // primary key_id 0 indicates that it is the first rekey
        if (conf().dynamic_tls_crypt_enabled() && primary && primary->key_id() == 0)
            set_dynamic_tls_crypt(conf(), primary);

        // initialize secondary key context
        new_secondary_key(true);
        secondary->start();
    }

    // Should be called at the end of sequence of send/recv
    // operations on underlying protocol object.
    // If control_channel is true, do a full flush.
    // If control_channel is false, optimize flush for data
    // channel only.
    void flush(const bool control_channel)
    {
        if (control_channel || process_events())
        {
            do
            {
                if (primary)
                    primary->flush();
                if (secondary)
                    secondary->flush();
            } while (process_events());
        }
    }

    // Perform various time-based housekeeping tasks such as retransmiting
    // unacknowleged packets as part of the reliability layer and testing
    // for keepalive timouts.
    // Should be called at the time returned by next_housekeeping.
    void housekeeping()
    {
        // handle control channel retransmissions on primary
        if (primary)
            primary->retransmit();

        // handle control channel retransmissions on secondary
        if (secondary)
            secondary->retransmit();

        // handle possible events
        flush(false);

        // handle keepalive/expiration
        keepalive_housekeeping();
    }

    // When should we next call housekeeping?
    // Will return a time value for immediate execution
    // if session has been invalidated.
    Time next_housekeeping() const
    {
        if (!invalidated())
        {
            Time ret = Time::infinite();
            if (primary)
                ret.min(primary->next_retransmit());
            if (secondary)
                ret.min(secondary->next_retransmit());
            ret.min(keepalive_xmit);
            ret.min(keepalive_expire);
            return ret;
        }
        else
            return Time();
    }

    // send app-level cleartext to remote peer

    void control_send(BufferPtr &&app_bp)
    {
        select_control_send_context().app_send(std::move(app_bp));
    }

    void control_send(BufferAllocated &&app_buf)
    {
        control_send(BufferAllocatedRc::Create(std::move(app_buf)));
    }

    // validate a control channel network packet
    bool control_net_validate(const PacketType &type, const Buffer &net_buf)
    {
        return type.is_defined() && KeyContext::validate(net_buf, *this, now_);
    }

    // pass received control channel network packets (ciphertext) into protocol object
    bool control_net_recv(const PacketType &type, BufferPtr &&net_bp)
    {
        Packet pkt(std::move(net_bp), type.opcode);
        if (type.is_soft_reset() && !renegotiate_request(pkt))
            return false;
        return select_key_context(type, true).net_recv(std::move(pkt));
    }

    /**
        @brief pass received control channel network packets (ciphertext) into protocol object
        @param type Packet type discriminator
        @param net_buf Buffer containing the network packet
        @return @c true if successfully processed, @c false if not
        @note Implemented in terms of control_net_recv(const PacketType &type, BufferPtr &&net_bp)
    */
    bool control_net_recv(const PacketType &type, BufferAllocated &&net_buf)
    {
        return control_net_recv(type, BufferAllocatedRc::Create(std::move(net_buf)));
    }

    // encrypt a data channel packet using primary KeyContext
    void data_encrypt(BufferAllocated &in_out)
    {
        OVPN_LOG_DEBUG(debug_prefix() << " DATA ENCRYPT size=" << in_out.size());
        if (!primary)
            throw proto_error("data_encrypt: no primary key");
        primary->encrypt(in_out);
    }

    // decrypt a data channel packet (automatically select primary
    // or secondary KeyContext based on packet content)
    bool data_decrypt(const PacketType &type, BufferAllocated &in_out)
    {
        bool ret = false;

        OVPN_LOG_DEBUG(debug_prefix() << " DATA DECRYPT key_id=" << select_key_context(type, false).key_id() << " size=" << in_out.size());

        select_key_context(type, false).decrypt(in_out);

        // update time of most recent packet received
        if (in_out.size())
        {
            update_last_received();
            ret = true;
        }

        // discard keepalive packets
        if (proto_context_private::is_keepalive(in_out))
        {
            in_out.reset_size();
        }

        return ret;
    }

    // enter disconnected state
    void disconnect(const Error::Type reason)
    {
        if (primary)
            primary->invalidate(reason);
        if (secondary)
            secondary->invalidate(reason);
    }

    // normally used by UDP clients to tell the server that
    // they are disconnecting
    void send_explicit_exit_notify()
    {
#ifndef OPENVPN_DISABLE_EXPLICIT_EXIT // explicit exit should always be enabled in production
        if (!is_client() || !is_udp() || !primary)
        {
            return;
        }

        if (config->cc_exit_notify)
        {
            write_control_string(std::string("EXIT"));
            primary->flush();
        }
        else
        {
            primary->send_explicit_exit_notify();
        }
#endif // OPENVPN_DISABLE_EXPLICIT_EXIT
    }

    // should be called after a successful network packet transmit
    void update_last_sent()
    {
        keepalive_xmit = *now_ + config->keepalive_ping;
    }

    // Can we call data_encrypt or data_decrypt yet?
    // Returns true if primary data channel is in ACTIVE state.
    bool data_channel_ready() const
    {
        return primary && primary->data_channel_ready();
    }

    // total number of SSL/TLS negotiations during lifetime of ProtoContext object
    unsigned int negotiations() const
    {
        return n_key_ids;
    }

    // worst-case handshake time
    const Time::Duration &slowest_handshake()
    {
        return slowest_handshake_;
    }

    // was primary context invalidated by an exception?
    bool invalidated() const
    {
        return primary && primary->invalidated();
    }

    // reason for invalidation if invalidated() above returns true
    Error::Type invalidation_reason() const
    {
        return primary->invalidation_reason();
    }

    // Do late initialization of data channel, for example
    // on client after server push, or on server after client
    // capabilities are known.
    void init_data_channel()
    {
        dc_deferred = false;

        // initialize data channel (crypto & compression)
        if (primary)
            primary->init_data_channel();
        if (secondary)
            secondary->init_data_channel();
    }

    // Call on client with server-pushed options
    void process_push(const OptionList &opt, const ProtoContextCompressionOptions &pco)
    {
        // modify config with pushed options
        config->process_push(opt, pco);

        // in case keepalive parms were modified by push
        keepalive_parms_modified();
    }

    // Return the current transport alignment adjustment
    size_t align_adjust_hint() const
    {
        return config->enable_op32 ? 0 : 1;
    }

    // Return true if keepalive parameter(s) are enabled
    bool is_keepalive_enabled() const
    {
        return config->keepalive_ping.enabled()
               || config->keepalive_timeout.enabled();
    }

    // Disable keepalive for rest of session,
    // but return the previous keepalive parameters.
    void disable_keepalive(unsigned int &keepalive_ping,
                           unsigned int &keepalive_timeout)
    {
        keepalive_ping = config->keepalive_ping.enabled()
                             ? clamp_to_typerange<std::remove_reference_t<decltype(keepalive_ping)>>(config->keepalive_ping.to_seconds())
                             : 0;
        keepalive_timeout = config->keepalive_timeout.enabled()
                                ? clamp_to_typerange<std::remove_reference_t<decltype(keepalive_timeout)>>(config->keepalive_timeout.to_seconds())
                                : 0;
        config->keepalive_ping = Time::Duration::infinite();
        config->keepalive_timeout = Time::Duration::infinite();
        config->keepalive_timeout_early = Time::Duration::infinite();
        keepalive_parms_modified();
    }

    // Notify our component KeyContext when per-key Data Limits have been reached
    void data_limit_notify(const unsigned int key_id,
                           const DataLimit::Mode cdl_mode,
                           const DataLimit::State cdl_status)
    {
        if (primary && key_id == primary->key_id())
            primary->data_limit_notify(cdl_mode, cdl_status);
        else if (secondary && key_id == secondary->key_id())
            secondary->data_limit_notify(cdl_mode, cdl_status);
    }

    // access the data channel settings
    CryptoDCSettings &dc_settings()
    {
        return config->dc;
    }

    // reset the data channel factory
    void reset_dc_factory()
    {
        config->dc.reset();
    }

    // set the local peer ID (or -1 to disable)
    void set_local_peer_id(const int local_peer_id)
    {
        config->local_peer_id = local_peer_id;
    }

    // current time
    const Time &now() const
    {
        return *now_;
    }
    void update_now()
    {
        now_->update();
    }

    // frame
    const Frame &frame() const
    {
        return *config->frame;
    }
    const Frame::Ptr &frameptr() const
    {
        return config->frame;
    }

    // client or server?
    const Mode &mode() const
    {
        return mode_;
    }
    bool is_server() const
    {
        return mode_.is_server();
    }
    bool is_client() const
    {
        return mode_.is_client();
    }

    // tcp/udp mode
    bool is_tcp()
    {
        return config->protocol.is_tcp();
    }
    bool is_udp()
    {
        return config->protocol.is_udp();
    }

    // configuration
    const ProtoConfig &conf() const
    {
        return *config;
    }
    ProtoConfig &conf()
    {
        return *config;
    }
    ProtoConfig::Ptr conf_ptr() const
    {
        return config;
    }

    // stats
    SessionStats &stat() const
    {
        return *stats;
    }

    // debugging
    bool is_state_client_wait_reset_ack() const
    {
        return primary_state() == C_WAIT_RESET_ACK;
    }

  protected:
    int primary_state() const
    {
        if (primary)
            return primary->get_state();
        else
            return STATE_UNDEF;
    }

  private:
    // TLS wrapping mode for the control channel
    enum TLSWrapMode
    {
        TLS_PLAIN,
        TLS_AUTH,
        TLS_CRYPT,
        TLS_CRYPT_V2
    };

    static constexpr PacketIDControl::id_t EARLY_NEG_START = 0x0f000000;

    void reset_all()
    {
        if (primary)
            primary->rekey(CryptoDCInstance::DEACTIVATE_ALL);
        primary.reset();
        secondary.reset();
    }

    // Called on client to request username/password credentials.
    // delegated to the callback/parent
    void client_auth(Buffer &buf)
    {
        proto_callback->client_auth(buf);
    }

    void update_last_received()
    {
        keepalive_expire = *now_ + (data_channel_ready() ? config->keepalive_timeout : config->keepalive_timeout_early);
    }

    void net_send(const unsigned int key_id, const Packet &net_pkt)
    {
        proto_callback->control_net_send(net_pkt.buffer());
    }

    void app_recv(const unsigned int key_id, BufferPtr &&to_app_buf)
    {
        proto_callback->control_recv(std::move(to_app_buf));
    }

    // we're getting a request from peer to renegotiate.
    bool renegotiate_request(Packet &pkt)
    {
        // set up dynamic tls-crypt keys when the first rekeying happens
        // primary key_id 0 indicates that it is the first rekey
        if (conf().dynamic_tls_crypt_enabled() && primary && primary->key_id() == 0)
            set_dynamic_tls_crypt(conf(), primary);

        if (KeyContext::validate(pkt.buffer(), *this, now_))
        {
            new_secondary_key(false);
            return true;
        }
        else
            return false;
    }

    // select a KeyContext (primary or secondary) for received network packets
    KeyContext &select_key_context(const PacketType &type, const bool control)
    {
        const unsigned int flags = type.flags & (PacketType::DEFINED | PacketType::SECONDARY | PacketType::CONTROL);
        if (!control)
        {
            if (flags == (PacketType::DEFINED) && primary)
                return *primary;
            else if (flags == (PacketType::DEFINED | PacketType::SECONDARY) && secondary)
                return *secondary;
        }
        else
        {
            if (flags == (PacketType::DEFINED | PacketType::CONTROL) && primary)
            {
                return *primary;
            }
            else if (flags == (PacketType::DEFINED | PacketType::SECONDARY | PacketType::CONTROL)
                     && secondary)
            {
                return *secondary;
            }
        }
        throw select_key_context_error();
    }

    // Select a KeyContext (primary or secondary) for control channel sends.
    // Even after new key context goes active, we still wait for
    // KEV_BECOME_PRIMARY event (controlled by the become_primary duration
    // in Config) before we use it for app-level control-channel
    // transmissions.  Simulations have found this method to be more reliable
    // than the immediate rollover practiced by OpenVPN 2.x.
    KeyContext &select_control_send_context()
    {
        OVPN_LOG_VERBOSE(debug_prefix() << " CONTROL SEND");
        if (!primary)
            throw proto_error("select_control_send_context: no primary key");
        return *primary;
    }

    // Possibly send a keepalive message, and check for expiration
    // of session due to lack of received packets from peer.
    void keepalive_housekeeping()
    {
        const Time now = *now_;

        // check for keepalive timeouts
        if (now >= keepalive_xmit && primary)
        {
            primary->send_keepalive();
            update_last_sent();
        }
        if (now >= keepalive_expire)
        {
            // no contact with peer, disconnect
            stats->error(Error::KEEPALIVE_TIMEOUT);
            disconnect(Error::KEEPALIVE_TIMEOUT);
        }
    }

    // Process KEV_x events
    // Return true if any events were processed.
    bool process_events()
    {
        bool did_work = false;

        // primary
        if (primary && primary->event_pending())
        {
            process_primary_event();
            did_work = true;
        }

        // secondary
        if (secondary && secondary->event_pending())
        {
            process_secondary_event();
            did_work = true;
        }

        return did_work;
    }

    // Create a new secondary key.
    // initiator --
    //   false : remote renegotiation request
    //   true  : local renegotiation request
    void new_secondary_key(const bool initiator)
    {
        // Create the secondary
        secondary.reset(new KeyContext(*this, initiator));
        OVPN_LOG_VERBOSE(debug_prefix()
                         << " New KeyContext SECONDARY id=" << secondary->key_id()
                         << (initiator ? " local-triggered" : " remote-triggered"));
    }

    // Promote a newly renegotiated KeyContext to primary status.
    // This is usually triggered by become_primary variable (Time::Duration)
    // in Config.
    void promote_secondary_to_primary()
    {
        primary.swap(secondary);
        if (primary)
            primary->rekey(CryptoDCInstance::PRIMARY_SECONDARY_SWAP);
        if (secondary)
            secondary->prepare_expire();
        OVPN_LOG_VERBOSE(debug_prefix() << " PRIMARY_SECONDARY_SWAP");
    }

    void process_primary_event()
    {
        const KeyContext::EventType ev = primary->get_event();
        if (ev != KeyContext::KEV_NONE)
        {
            primary->reset_event();
            switch (ev)
            {
            case KeyContext::KEV_ACTIVE:
                OVPN_LOG_VERBOSE(debug_prefix() << " SESSION_ACTIVE");
                primary->rekey(CryptoDCInstance::ACTIVATE_PRIMARY);
                proto_callback->active(true);
                break;
            case KeyContext::KEV_RENEGOTIATE:
            case KeyContext::KEV_RENEGOTIATE_FORCE:
                renegotiate();
                break;
            case KeyContext::KEV_EXPIRE:
                if (secondary && !secondary->invalidated())
                    promote_secondary_to_primary();
                else
                {
                    stats->error(Error::PRIMARY_EXPIRE);
                    // primary context expired and no secondary context available
                    disconnect(Error::PRIMARY_EXPIRE);
                }
                break;
            case KeyContext::KEV_NEGOTIATE:
                stats->error(Error::HANDSHAKE_TIMEOUT);
                // primary negotiation failed
                disconnect(Error::HANDSHAKE_TIMEOUT);
                break;
            default:
                break;
            }
        }
        primary->set_next_event_if_unspecified();
    }

    void process_secondary_event()
    {
        const KeyContext::EventType ev = secondary->get_event();
        if (ev != KeyContext::KEV_NONE)
        {
            secondary->reset_event();
            switch (ev)
            {
            case KeyContext::KEV_ACTIVE:
                secondary->rekey(CryptoDCInstance::NEW_SECONDARY);
                if (primary)
                    primary->prepare_expire();
                proto_callback->active(false);
                break;
            case KeyContext::KEV_BECOME_PRIMARY:
                if (!secondary->invalidated())
                    promote_secondary_to_primary();
                break;
            case KeyContext::KEV_EXPIRE:
                secondary->rekey(CryptoDCInstance::DEACTIVATE_SECONDARY);
                secondary.reset();
                break;
            case KeyContext::KEV_RENEGOTIATE_QUEUE:
                if (primary)
                    primary->key_limit_reneg(KeyContext::KEV_RENEGOTIATE_FORCE,
                                             secondary->become_primary_time());
                break;
            case KeyContext::KEV_NEGOTIATE:
                stats->error(Error::HANDSHAKE_TIMEOUT);
                [[fallthrough]];
            case KeyContext::KEV_PRIMARY_PENDING:
            case KeyContext::KEV_RENEGOTIATE_FORCE:
                renegotiate();
                break;
            default:
                break;
            }
        }
        if (secondary)
            secondary->set_next_event_if_unspecified();
    }

    std::string debug_prefix()
    {
        std::string ret = openvpn::to_string(now_->raw());
        ret += is_server() ? " SERVER[" : " CLIENT[";
        if (primary)
            ret += openvpn::to_string(primary->key_id());
        if (secondary)
        {
            ret += '/';
            ret += openvpn::to_string(secondary->key_id());
        }
        ret += ']';
        return ret;
    }

    // key_id starts at 0, increments to KEY_ID_MASK, then recycles back to 1.
    // Therefore, if key_id is 0, it is the first key.
    unsigned int next_key_id()
    {
        ++n_key_ids;
        unsigned int ret = upcoming_key_id;
        if ((upcoming_key_id = (upcoming_key_id + 1) & KEY_ID_MASK) == 0)
            upcoming_key_id = 1;
        return ret;
    }

    // call whenever keepalive parms are modified,
    // to reset timers
    void keepalive_parms_modified()
    {
        update_last_received();

        // For keepalive_xmit timer, don't reschedule current cycle
        // unless it would fire earlier.  Subsequent cycles will
        // time according to new keepalive_ping value.
        const Time kx = *now_ + config->keepalive_ping;
        if (kx < keepalive_xmit)
            keepalive_xmit = kx;
    }

    void tls_crypt_append_wkc(BufferAllocated &dst)
    {
        if (!config->wkc.defined())
            throw proto_error("Client Key Wrapper undefined");
        dst.append(config->wkc);
    }

    // BEGIN ProtoContext data members

    /** the class that uses this class needs to be called back on a few things. Typically a class
     * that uses this class as field for composition. This parent/callback class needs to ensure that
     * it lives longer than this class, e.g. by having this class as field as this class blindly
     * assumes that this pointer is always valid for its lifetime
     */
    ProtoContextCallbackInterface *proto_callback;

    ProtoConfig::Ptr config;
    SessionStats::Ptr stats;

    size_t hmac_size;
    TLSWrapMode tls_wrap_mode;
    Mode mode_; // client or server
    unsigned int upcoming_key_id = 0;
    unsigned int n_key_ids;

    TimePtr now_;          // pointer to current time (a clone of config->now)
    Time keepalive_xmit;   // time in future when we will transmit a keepalive (subject to continuous change)
    Time keepalive_expire; // time in future when we must have received a packet from peer or we will timeout session

    Time::Duration slowest_handshake_; // longest time to reach a successful handshake

    OvpnHMACInstance::Ptr ta_hmac_send;
    OvpnHMACInstance::Ptr ta_hmac_recv;

    TLSCryptInstance::Ptr tls_crypt_send;
    TLSCryptInstance::Ptr tls_crypt_recv;

    TLSCryptInstance::Ptr tls_crypt_server;
    TLSCryptMetadata::Ptr tls_crypt_metadata;

    PacketIDControlSend ta_pid_send;
    PacketIDControlReceive ta_pid_recv;

    ProtoSessionID psid_self;
    ProtoSessionID psid_peer;

    KeyContext::Ptr primary;
    KeyContext::Ptr secondary;
    bool dc_deferred = false;

    // END ProtoContext data members
};

} // namespace openvpn

#endif // OPENVPN_SSL_PROTO_H
