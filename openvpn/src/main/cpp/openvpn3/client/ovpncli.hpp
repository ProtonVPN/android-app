//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
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

// API for OpenVPN Client, may be used standalone or wrapped by swig.
// Use ovpncli.i to wrap the API for swig.
// The crux of the API is defined in OpenVPNClient (below)
// and TunBuilderBase.

#include <string>
#include <vector>
#include <utility>

#include <openvpn/tun/builder/base.hpp>
#include <openvpn/tun/extern/fw.hpp>
#include <openvpn/pki/epkibase.hpp>
#include <openvpn/transport/client/extern/fw.hpp>

namespace openvpn {
  class OptionList;
  class ProfileMerge;
  class Stop;

  namespace ClientAPI {
    // Represents an OpenVPN server and its friendly name
    // (client reads)
    struct ServerEntry
    {
      std::string server;
      std::string friendlyName;
    };

    // return properties of config
    // (client reads)
    struct EvalConfig
    {
      // true if error
      bool error = false;

      // if error, message given here
      std::string message;

      // this username must be used with profile
      std::string userlockedUsername;

      // profile name of config
      std::string profileName;

      // "friendly" name of config
      std::string friendlyName;

      // true: no creds required, false: username/password required
      bool autologin = false;

      // if true, this is an External PKI profile (no cert or key directives)
      bool externalPki = false;

      // static challenge, may be empty, ignored if autologin
      std::string staticChallenge;

      // true if static challenge response should be echoed to UI, ignored if autologin
      bool staticChallengeEcho = false;

      // true if this profile requires a private key password
      bool privateKeyPasswordRequired = false;

      // true if user is allowed to save authentication password in UI
      bool allowPasswordSave = false;

      // information about the first remote item in config
      std::string remoteHost;    // will be overridden by Config::serverOverride if defined
      std::string remotePort;
      std::string remoteProto;

      // optional list of user-selectable VPN servers
      std::vector<ServerEntry> serverList;

      // optional, values are "tap-windows6" and "wintun"
      std::string windowsDriver;
    };

    // used to pass credentials to VPN core
    // (client writes)
    struct ProvideCreds
    {
      std::string username;
      std::string password;

      // response to challenge
      std::string response;

      // Dynamic challenge/response cookie
      std::string dynamicChallengeCookie;

      // If true, on successful connect, we will replace the password
      // with the session ID we receive from the server (if provided).
      // If false, the password will be cached for future reconnects
      // and will not be replaced with a session ID, even if the
      // server provides one.
      bool replacePasswordWithSessionID = false;

      // If true, and if replacePasswordWithSessionID is true, and if
      // we actually receive a session ID from the server, cache
      // the user-provided password for future use before replacing
      // the active password with the session ID.
      bool cachePassword = false;
    };

    // used to get session token from VPN core
    // (client reads)
    struct SessionToken
    {
      std::string username;
      std::string session_id; // an OpenVPN Session ID, used as a proxy for password
    };

    // used to query challenge/response from user
    // (client reads)
    struct DynamicChallenge
    {
      std::string challenge;
      bool echo = false;
      bool responseRequired = false;

      std::string stateID;
    };

    // a basic key/value pair, used in Config below when OpenVPN profile is
    // passed as a dictionary
    struct KeyValue
    {
      KeyValue() {}

      KeyValue(std::string key_arg, std::string value_arg)
	: key(std::move(key_arg)),
	  value(std::move(value_arg)) {}

      std::string key;
      std::string value;
    };

    // OpenVPN config-file/profile
    // (client writes)
    struct Config
    {
      // OpenVPN profile as a string
      std::string content;

      // OpenVPN profile as series of key/value pairs (may be provided exclusively
      // or in addition to content string above).
      std::vector<KeyValue> contentList;

      // Set to identity OpenVPN GUI version.
      // Format should be "<gui_identifier><space><version>"
      // Passed to server as IV_GUI_VER.
      std::string guiVersion;

      // Set to a comma seperated list of supported SSO mechanisms that may
      // be signalled via INFO_PRE to the client.
      // "openurl" is to continue authentication by opening an url in a browser
      // "crtext" gives a challenge response in text format that needs to
      // responded via control channel. (
      // Passed to the server as IV_SSO
      std::string ssoMethods;

      // Override the string that is passed as IV_HWADDR to the server
      std::string hwAddrOverride;

      // Set the string that is passed to the server as IV_PLAT_VER
      std::string platformVersion;

      // Use a different server than that specified in "remote"
      // option of profile
      std::string serverOverride;

      // Use a different port than that specified in "remote"
      // option of profile
      std::string portOverride;

      // Force a given transport protocol
      // Should be tcp, udp, or adaptive.
      std::string protoOverride;

      // IPv6 preference
      //  no      -- disable IPv6, so tunnel will be IPv4-only
      //  yes     -- request combined IPv4/IPv6 tunnel
      //  default (or empty string) -- leave decision to server
      std::string ipv6;

      // Connection timeout in seconds, or 0 to retry indefinitely
      int connTimeout = 0;

      // Keep tun interface active during pauses or reconnections
      bool tunPersist = false;

      // If true and a redirect-gateway profile doesn't also define
      // DNS servers, use the standard Google DNS servers.
      bool googleDnsFallback = false;

      // if true, do synchronous DNS lookup.
      bool synchronousDnsLookup = false;

      // Enable autologin sessions
      bool autologinSessions = true;

      // If true, consider AUTH_FAILED to be a non-fatal error,
      // and retry the connection after a pause.
      bool retryOnAuthFailed = false;

      // An ID used for get-certificate and RSA signing callbacks
      // for External PKI profiles.
      std::string externalPkiAlias;

      // If true, don't send client cert/key to peer.
      bool disableClientCert = false;

      // SSL library debug level
      int sslDebugLevel = 0;

      // Compression mode, one of:
      // yes -- allow compression on both uplink and downlink
      // asym -- allow compression on downlink only (i.e. server -> client)
      // no (default if empty) -- support compression stubs only
      std::string compressionMode;

      // private key password (optional)
      std::string privateKeyPassword;

      // Default key direction parameter for tls-auth (0, 1, or
      // -1 (bidirectional -- default)) if no key-direction parameter
      // defined in profile.  Generally should be -1 (bidirectional)
      // for compatibility with 2.x branch
      int defaultKeyDirection = -1;

      // If true, force ciphersuite to be one of:
      // 1. TLS_DHE_RSA_WITH_AES_256_CBC_SHA, or
      // 2. TLS_DHE_RSA_WITH_AES_128_CBC_SHA
      // and disable setting TLS minimum version.
      // This is intended for compatibility with legacy systems.
      bool forceAesCbcCiphersuites = false;

      // Override the minimum TLS version:
      //   disabled -- don't specify a minimum, and disable any minimum
      //               specified in profile
      //   default or ""  -- use profile minimum
      //   tls_1_0  -- use TLS 1.0 minimum (overrides profile)
      //   tls_1_1  -- use TLS 1.1 minimum (overrides profile)
      //   tls_1_2  -- use TLS 1.2 minimum (overrides profile)
      std::string tlsVersionMinOverride;

      // Override or default the tls-cert-profile setting:
      //   default or ""     -- use profile default
      //   legacy            -- allow 1024-bit RSA certs signed with SHA1
      //   preferred         -- require at least 2048-bit RSA certs signed
      //                        with SHA256 or higher
      //   suiteb            -- require NSA Suite-B
      //   legacy-default    -- use legacy as the default if profile
      //                        doesn't specify tls-cert-profile
      //   preferred-default -- use preferred as the default if profile
      //                        doesn't specify tls-cert-profile
      std::string tlsCertProfileOverride;

      // Overrides the list of tls ciphers like the tls-cipher option
      std::string tlsCipherList;

      // Overrides the list of TLS 1.3 ciphersuites like the tls-ciphersuites
      // option
      std::string tlsCiphersuitesList;

      // Pass custom key/value pairs to OpenVPN server.
      std::vector<KeyValue> peerInfo;

      // HTTP Proxy parameters (optional)
      std::string proxyHost;         // hostname or IP address of proxy
      std::string proxyPort;         // port number of proxy
      std::string proxyUsername;     // proxy credentials (optional)
      std::string proxyPassword;     // proxy credentials (optional)
      bool proxyAllowCleartextAuth = false;  // enables HTTP Basic auth

      // Custom proxy implementation
      bool altProxy = false;

      // Custom Data Channel Offload implementation
      bool dco = false;

      // pass through pushed "echo" directives via "ECHO" event
      bool echo = false;

      // pass through control channel INFO notifications via "INFO" event
      bool info = false;

      // Allow access to local LAN. This is for platforms like
      // Android that disable local LAN access by default.
      bool allowLocalLanAccess = false;

      // Periodic convenience clock tick in milliseconds.
      // Will call clock_tick() at a frequency defined by this parameter.
      // Set to 0 to disable.
      unsigned int clockTickMS = 0;

      // Gremlin configuration (requires that the core is built with OPENVPN_GREMLIN)
      std::string gremlinConfig;

      // Use wintun instead of tap-windows6 on Windows
      bool wintun = false;
    };

    // used to communicate VPN events such as connect, disconnect, etc.
    // (client reads)
    struct Event
    {
      bool error = false;    // true if error (fatal or nonfatal)
      bool fatal = false;    // true if fatal error (will disconnect)
      std::string name;      // event name
      std::string info;      // additional event info
    };

    // used to communicate extra details about successful connection
    // (client reads)
    struct ConnectionInfo
    {
      bool defined = false;
      std::string user;
      std::string serverHost;
      std::string serverPort;
      std::string serverProto;
      std::string serverIp;
      std::string vpnIp4;
      std::string vpnIp6;
      std::string gw4;
      std::string gw6;
      std::string clientIp;
      std::string tunName;
    };

    // returned by some methods as a status/error indication
    // (client reads)
    struct Status
    {
      bool error = false;   // true if error
      std::string status;   // an optional short error label that identifies the error
      std::string message;  // if error, message given here
    };

    // used to pass log lines
    // (client reads)
    struct LogInfo
    {
      LogInfo() {}
      LogInfo(std::string str)
	: text(std::move(str)) {}
      std::string text;     // log output (usually but not always one line)
    };

    // receives log messages
    struct LogReceiver
    {
      virtual void log(const LogInfo&) = 0;
      virtual ~LogReceiver() {}
    };

    // used to pass stats for an interface
    struct InterfaceStats
    {
      long long bytesIn;
      long long packetsIn;
      long long errorsIn;
      long long bytesOut;
      long long packetsOut;
      long long errorsOut;
    };

    // used to pass basic transport stats
    struct TransportStats
    {
      long long bytesIn;
      long long bytesOut;
      long long packetsIn;
      long long packetsOut;

      // number of binary milliseconds (1/1024th of a second) since
      // last packet was received, or -1 if undefined
      int lastPacketReceived;
    };

    // return value of merge_config methods
    struct MergeConfig
    {
      std::string status;                   // ProfileMerge::Status codes rendered as string
      std::string errorText;                // error string (augments status)
      std::string basename;                 // profile basename
      std::string profileContent;           // unified profile
      std::vector<std::string> refPathList; // list of all reference paths successfully read
    };

    // base class for External PKI queries
    struct ExternalPKIRequestBase
    {
      bool error = false;        // true if error occurred (client writes)
      std::string errorText;     // text describing error (client writes)
      bool invalidAlias = false; // true if the error is caused by an invalid alias (client writes)
      std::string alias;         // the alias string, used to query cert/key (client reads)
    };

    // used to query for External PKI certificate
    struct ExternalPKICertRequest : public ExternalPKIRequestBase
    {
      // leaf cert
      std::string cert; // (client writes)

      // chain of intermediates and root (optional)
      std::string supportingChain; // (client writes)
    };

    // Used to request an RSA signature.
    // algorithm will determinate what signature is expected:
    // RSA_PKCS1_PADDING means that
    // data will be prefixed by an optional PKCS#1 digest prefix
    // per RFC 3447.
    // RSA_NO_PADDING mean so no padding should be done be the callee
    struct ExternalPKISignRequest : public ExternalPKIRequestBase
    {
      std::string data;  // data rendered as base64 (client reads)
      std::string sig;   // RSA signature, rendered as base64 (client writes)
      std::string algorithm;
    };

    // used to override "remote" directives
    struct RemoteOverride
    {
      // components of "remote" directive (client writes),
      std::string host;   // either one of host
      std::string ip;     //   or ip must be defined (or both)
      std::string port;
      std::string proto;
      std::string error;  // if non-empty, indicates an error
    };

    namespace Private {
      class ClientState;
    };

    // Top-level OpenVPN client class.
    class OpenVPNClient : public TunBuilderBase,            // expose tun builder virtual methods
			  public LogReceiver,               // log message notification
			  public ExternalTun::Factory,      // low-level tun override
			  public ExternalTransport::Factory,// low-level transport override
			  private ExternalPKIBase
    {
    public:
      OpenVPNClient();
      virtual ~OpenVPNClient();

      // Read an OpenVPN profile that might contain external
      // file references, returning a unified profile.
      static MergeConfig merge_config_static(const std::string& path, bool follow_references);

      // Read an OpenVPN profile that might contain external
      // file references, returning a unified profile.
      static MergeConfig merge_config_string_static(const std::string& config_content);

      // Parse profile and determine needed credentials statically.
      static EvalConfig eval_config_static(const Config& config);

      // Maximum size of profile that should be allowed
      static long max_profile_size();

      // Parse a dynamic challenge cookie, placing the result in dc.
      // Return true on success or false if parse error.
      static bool parse_dynamic_challenge(const std::string& cookie, DynamicChallenge& dc);

      // Parse OpenVPN configuration file.
      EvalConfig eval_config(const Config&);

      // Provide credentials and other options.  Call before connect().
      Status provide_creds(const ProvideCreds&);

      // Callback to "protect" a socket from being routed through the tunnel.
      // Will be called from the thread executing connect().
      // The remote and ipv6 are the remote host this socket will connect to
      virtual bool socket_protect(int socket, std::string remote, bool ipv6);

      // Primary VPN client connect method, doesn't return until disconnect.
      // Should be called by a worker thread.  This method will make callbacks
      // to event() and log() functions.  Make sure to call eval_config()
      // and possibly provide_creds() as well before this function.
      Status connect();

      // Return information about the most recent connection.  Should be called
      // after an event of type "CONNECTED".
      ConnectionInfo connection_info();

      // Writes current session token to tok and returns true.
      // If session token is unavailable, false is returned and
      // tok is unmodified.
      bool session_token(SessionToken& tok);

      // Stop the client.  Only meaningful when connect() is running.
      // May be called asynchronously from a different thread
      // when connect() is running.
      void stop();

      // Pause the client -- useful to avoid continuous reconnection attempts
      // when network is down.  May be called from a different thread
      // when connect() is running.
      void pause(const std::string& reason);

      // Resume the client after it has been paused.  May be called from a
      // different thread when connect() is running.
      void resume();

      // Do a disconnect/reconnect cycle n seconds from now.  May be called
      // from a different thread when connect() is running.
      void reconnect(int seconds);

      // When a connection is close to timeout, the core will call this
      // method.  If it returns false, the core will disconnect with a
      // CONNECTION_TIMEOUT event.  If true, the core will enter a PAUSE
      // state.
      virtual bool pause_on_connection_timeout() = 0;

      // Get stats/error info.  May be called from a different thread
      // when connect() is running.

      // number of stats
      static int stats_n();

      // return a stats name, index should be >= 0 and < stats_n()
      static std::string stats_name(int index);

      // return a stats value, index should be >= 0 and < stats_n()
      long long stats_value(int index) const;

      // return all stats in a bundle
      std::vector<long long> stats_bundle() const;

      // return tun stats only
      InterfaceStats tun_stats() const;

      // return transport stats only
      TransportStats transport_stats() const;

      // post control channel message
      void post_cc_msg(const std::string& msg);

      // Callback for delivering events during connect() call.
      // Will be called from the thread executing connect().
      virtual void event(const Event&) = 0;

      // Callback for logging.
      // Will be called from the thread executing connect().
      virtual void log(const LogInfo&) = 0;

      // External PKI callbacks
      // Will be called from the thread executing connect().
      virtual void external_pki_cert_request(ExternalPKICertRequest&) = 0;
      virtual void external_pki_sign_request(ExternalPKISignRequest&) = 0;

      // Remote override callback (disabled by default).
      virtual bool remote_override_enabled();
      virtual void remote_override(RemoteOverride&);

      // Periodic convenience clock tick, controlled by Config::clockTickMS
      virtual void clock_tick();

      // Do a crypto library self test
      static std::string crypto_self_test();

      // Returns date/time of app expiration as a unix time value
      static int app_expire();

      // Returns platform description string
      static std::string platform();

      // Returns core copyright
      static std::string copyright();

      // Hide protected methods/data from SWIG
#ifdef SWIGJAVA
    private:
#else
    protected:
#endif

      Status do_connect();

      virtual void connect_attach();
      virtual void connect_pre_run();
      virtual void connect_run();
      virtual void connect_session_stop();

      virtual Stop* get_async_stop();

      Private::ClientState* state;

    private:
      void connect_setup(Status&, bool&);
      void do_connect_async();
      static Status status_from_exception(const std::exception&);
      static void parse_config(const Config&, EvalConfig&, OptionList&);
      void parse_extras(const Config&, EvalConfig&);
      void external_pki_error(const ExternalPKIRequestBase&, const size_t);
      void process_epki_cert_chain(const ExternalPKICertRequest&);
      void check_app_expired();
      static MergeConfig build_merge_config(const ProfileMerge&);

      friend class MyClientEvents;
      void on_disconnect();

      // from ExternalPKIBase
      virtual bool sign(const std::string& data, std::string& sig, const std::string& algorithm);

      // disable copy and assignment
      OpenVPNClient(const OpenVPNClient&) = delete;
      OpenVPNClient& operator=(const OpenVPNClient&) = delete;
    };

  }
}
