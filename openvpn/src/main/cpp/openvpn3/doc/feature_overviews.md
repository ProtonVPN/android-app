# Feature Overview

*This document is a work in progress. It is intended to give quick pointers for
newly added features.*

## Device Management

*TODO: general overview*

### Device Verification Certificate Check

The device verification certificate check is intended to provide customers with a means to
ensure the connecting device is the same device as was originally provisioned by the
controlling organization. This is done by routing a normal TLS handshake via the
AppControlChannel plumbing using a set of certs and keys that are meant to be tamper
resistant to a greater degree than the certs we use to connect the tunnel.

To implement this functionality several modules have been created:

- openvpn::SslApiBuilder implements a wrapper that instantiates and owns the SSLAPI
  object and the prerequisites it requires. It accepts a ref-counted pointer to the
  `SSLLib::SSLAPI::Config` object that has been initialized for the desired functionality.
  It might be useful outside it's use in the handshaker at some point, and if so we can
  move it to its own header.
- openvpn::AccHandshaker uses openvpn::SslApiBuilder and implements the read/write logic
  required to perform the handshake operation.
- openvpn::ClientProto::Session has been extended with a member of type
  openvpn::AccHandshaker and gains a few member functions to glue the ACC certcheck
  traffic into the `AccHandshaker` read/write logic. The
  only exposed API (`start_acc_certcheck()`) is not really for direct client use but
  is rather called by a more exposed interface in another class. Member
  openvpn::ClientProto::Session::start_acc_certcheck() does set up the
  handshake logic and then sends the start of the client hello upstream via the ACC
  when it's called via the `ProtoContext` interface.
- The `ClientAPI::OpenVPNClient` class gets a new member function
  openvpn::ClientAPI::OpenVPNClient::start_cert_check() which
  delegates via a few layers of abstraction to eventually call
  openvpn::ClientProto::Session::start_acc_certcheck()
  which as discussed before will configure the handshake mechanism and then begins
  the handshake process. The server side should ensure it is ready for the handshake
  before directing the connected client app to start.

For the MVP the start_cert_check API accepts encoded certs and keys as std::string, in PEM format:

    class OpenVPNClient ...

    void start_cert_check(const std::string &client_cert,
                          const std::string &clientkey,
                          const std::optional<const std::string> &ca = std::nullopt);

Alternatively one can use the EPKI enabled API:

    void start_cert_check_epki(const std::string &alias);

Both found in client/ovpncli.hpp.

Unit tests for the various subsystems can be found in
test/unittests/test_acc_certcheck.cpp.

Note: The assurance provided by this feature is limited by the security of the
certificate storage employed.
