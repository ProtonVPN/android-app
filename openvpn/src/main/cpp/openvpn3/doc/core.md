# OpenVPN 3 Core Library

*This document is a work in progress. Eventually it should either grow to encompass all of
the OpenVPN 3 core library or it should be incorporated into a different core library doc.*

# Crypto Support - SSLAPI

The primary TLS/SSL abstraction in OpenVPN 3 core is `SSLAPI`. This interface implements a memory buffer
based TLS state machine. It includes member functions that allow the read and write of both ciphertext
and plaintext. This allows the encyphering and decyphering operations to be done separately from any
data transport operations.

## Interface

The `SSLAPI` interface is defined in `class SSLAPI` and currently looks like this:

    virtual void start_handshake() = 0;
    virtual ssize_t write_cleartext_unbuffered(const void *data, const size_t size) = 0;
    virtual ssize_t read_cleartext(void *data, const size_t capacity) = 0;
    virtual bool read_cleartext_ready() const = 0;
    virtual void write_ciphertext(const BufferPtr &buf) = 0;
    virtual void write_ciphertext_unbuffered(const unsigned char *data, const size_t size) = 0;
    virtual bool read_ciphertext_ready() const = 0;
    virtual BufferPtr read_ciphertext() = 0;
    virtual std::string ssl_handshake_details() const = 0;
    virtual bool export_keying_material(const std::string &label, unsigned char *dest, size_t size) = 0;
    virtual bool did_full_handshake() = 0;
    virtual const AuthCert::Ptr &auth_cert() const = 0;
    virtual void mark_no_cache() = 0;
    uint32_t get_tls_warnings() const

This interface will be documented via doxygen markup.

## Instance Creation

To create an instance of an object implementing this interface a few steps must be taken. First, pull in the
required headers. The unit tests, found in ovpn3/core/test/unittests in test_sslctx.cpp and a few other
places, can provide examples. Next one must create a configuration object, which is derived from the
`class SSLConfigAPI` interface.

    virtual void set_mode(const Mode &mode_arg) = 0;
    virtual const Mode &get_mode() const = 0;
    virtual void set_external_pki_callback(ExternalPKIBase *external_pki_arg) = 0;
    virtual void set_session_ticket_handler(TLSSessionTicketBase *session_ticket_handler) = 0;
    virtual void set_client_session_tickets(const bool v) = 0;
    virtual void enable_legacy_algorithms(const bool v) = 0;
    virtual void set_sni_handler(SNI::HandlerBase *sni_handler) = 0;
    virtual void set_sni_name(const std::string &sni_name_arg) = 0;
    virtual void set_private_key_password(const std::string &pwd) = 0;
    virtual void load_ca(const std::string &ca_txt, bool strict) = 0;
    virtual void load_crl(const std::string &crl_txt) = 0;
    virtual void load_cert(const std::string &cert_txt) = 0;
    virtual void load_cert(const std::string &cert_txt, const std::string &extra_certs_txt) = 0;
    virtual void load_private_key(const std::string &key_txt) = 0;
    virtual void load_dh(const std::string &dh_txt) = 0;
    virtual std::string extract_ca() const = 0;
    virtual std::string extract_crl() const = 0;
    virtual std::string extract_cert() const = 0;
    virtual std::vector<std::string> extract_extra_certs() const = 0;
    virtual std::string extract_private_key() const = 0;
    virtual std::string extract_dh() const = 0;
    virtual PKType::Type private_key_type() const = 0;
    virtual size_t private_key_length() const = 0;
    virtual void set_frame(const Frame::Ptr &frame_arg) = 0;
    virtual void set_debug_level(const int debug_level) = 0;
    virtual void set_flags(const unsigned int flags_arg) = 0;
    virtual void set_ns_cert_type(const NSCert::Type ns_cert_type_arg) = 0;
    virtual void set_remote_cert_tls(const KUParse::TLSWebType wt) = 0;
    virtual void set_tls_remote(const std::string &tls_remote_arg) = 0;
    virtual void set_tls_version_min(const TLSVersion::Type tvm) = 0;
    virtual void set_tls_version_min_override(const std::string &override) = 0;
    virtual void set_tls_cert_profile(const TLSCertProfile::Type type) = 0;
    virtual void set_tls_cert_profile_override(const std::string &override) = 0;
    virtual void set_local_cert_enabled(const bool v) = 0;
    virtual void set_x509_track(X509Track::ConfigSet x509_track_config_arg) = 0;
    virtual void set_rng(const StrongRandomAPI::Ptr &rng_arg) = 0;
    virtual void load(const OptionList &opt, const unsigned int lflags) = 0;
    virtual std::string validate_cert(const std::string &cert_txt) const = 0;
    virtual std::string validate_cert_list(const std::string &certs_txt) const = 0;
    virtual std::string validate_crl(const std::string &crl_txt) const = 0;
    virtual std::string validate_private_key(const std::string &key_txt) const = 0;
    virtual std::string validate_dh(const std::string &dh_txt) const = 0;
    virtual SSLFactoryAPI::Ptr new_factory() = 0;

This interface will be documented via doxygen markup.

Each supported SSL provider implementation provides a class derived from `SSLConfigAPI`, typically aliased
as `SSLLib::SSLAPI::Config` if one includes the helpful header sslchoose.hpp, which, with proper build time
definitions, will select the SSL provider for the current environment. For example:

    #include <openvpn/ssl/sslchoose.hpp>
    #include <openvpn/ssl/sslapi.hpp>

    SSLLib::SSLAPI::Config::Ptr config = new SSLLib::SSLAPI::Config;

Once the `config` object has been instantiated as above, it must be initialized, for example:

    config->set_mode(Mode(Mode::CLIENT));
    config->load_cert(cert_txt);
    config->load_private_key(pvt_key_txt);
    config->load_ca(cert_txt, false);

*Note: Behavior from SSL provider to provider will vary a bit, see the unit tests for more sample code.*

The _txt symbols are PEM encoded certificates and keys. Once all the options are set, the config
instance is ready to be used to produce a factory instance:

    auto factory = config->new_factory();

Note that use of `auto` is safe here since `new_factory` returns a `openvpn::SSLFactoryAPI::Ptr` and
not a raw C++ pointer as `new` did for the `SSLLib::SSLAPI::Config` previously.

Once the factory is instantiated it may be used to create an instance of an SSLAPI implementation
as follows:

    auto sslapi = factory->ssl();

The SSLAPI instance is now ready for use. The factory and configuration both maintain some state information
that the SSLAPI instance requires to function properly so those must have their lifetime extended to at least
as long as the SSLAPI object is in use.

The `new_factory()` and `ssl()` member functions both return a reference counted smart pointer, so cleanup
of those resources will occur when that pointer and all assigned from it go out of scope. The example code
above also assigns the new config to a smart pointer, which works properly since the config type is
reference count enabled.

## Wrappers and Helpers

*TODO*

## External PKI

*TODO*

# App Control Channel

*TODO*

## Protocol

*TODO*

## Device Management

*TODO*

### Device Verification Certificate Check

The device verification certificate check is intended to provide customers with a means to
ensure the connecting device is the same device as was originally provisioned by the
controlling organization. This is done by routing a normal TLS handshake via the
AppControlChannel plumbing using a set of certs and keys that are meant to be tamper
resistant to a greater degree than the certs we use to connect the tunnel.

To implement this functionality several modules have been created:

- SslApiBuilder in ovpn3/core/openvpn/client/acc_certcheck.hpp implements a wrapper that
instantiates and owns the SSLAPI object and the prerequisites it requires. It accepts a
ref counted pointer to the SSLLib::SSLAPI::Config object that has been initialized for the
desired functionality. It might be useful outside it's use in the handshaker at some point,
and if so we can move it to its own header.
- AccHandshaker uses SslApiBuilder and implements the read/write logic required to perform
the handshake operation.
- ClientProto::Session::ProtoContext has been extended with a member of type AccHandshaker
and gains a few member functions to glue the ACC certcheck traffic into the AccHandshaker
read / write logic. This is all hidden inside ProtoContest and should be of little concern
to users of the functionality. The only exposed API (start_acc_certcheck) is not really
for direct client use but is rather called by a more exposed interface in another class.
Member start_acc_certcheck does set up the handshake logic and then sends the start of the
client hello upstream via the ACC when it's called via the ProtoContext interface.
- ClientAPI::OpenVPNClient gains a member function 'start_cert_check' which delegates via a
few layers of abstraction to eventually call CliProto::Session::ProtoContext::start_acc_certcheck,
which as discussed before will configure the handshake mechanism and then begins the handshake
process. The server side should ensure it is ready for the handshake before directing the
connected client app to start.

For the MVP the start_cert_check API accepts encoded certs and keys as std::string, in PEM format:

    class OpenVPNClient ...

    void start_cert_check(const std::string &client_cert,
                          const std::string &clientkey,
                          const std::optional<const std::string> &ca = std::nullopt);

Alternatively one can use the EPKI enabled API:

    void start_cert_check_epki(const std::string &alias);

Both found in ovpn3/core/client/ovpncli.hpp

Unit tests for the various subsystems can be found here: ovpn3/core/test/unittests/test_acc_certcheck.cpp

Note: The assurance provided by this feature is limited by the security of the certificate storage employed.


