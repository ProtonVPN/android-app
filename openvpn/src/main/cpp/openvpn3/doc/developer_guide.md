Developer Guide
---------------

OpenVPN 3 is written in C++20 and developers who are moving from C to
C++ should take some time to familiarize themselves with key C++ design
patterns such as
[*RAII*](https://en.wikipedia.org/wiki/Resource_acquisition_is_initialization).


OpenVPN 3 Client Core
=====================

OpenVPN 3 is designed as a class library, with an API that is
essentially defined inside of **namespace openvpn::ClientAPI** with headers and
implementation in `client/` and header-only library files under `openvpn/`.

The concise definition of the client API is essentially
**class openvpn::ClientAPI::OpenVPNClient** in with
several important extensions to the API found in:

-   **class openvpn::TunBuilderBase**  ---
    Provides an abstraction layer defining the *tun* interface, and is
    especially useful for interfacing with an OS-layer VPN API.
-   **class openvpn::ExternalPKIBase** --- Provides a
    callback for external private key operations, and is useful for
    interfacing with an OS-layer Keychain such as the Keychain on iOS,
    Mac OS X, and Android, and the Crypto API on Windows.
-   **class openvpn::ClientAPI::LogReceiver** ---
    Provides an abstraction layer for the delivery of logging messages.

OpenVPN 3 includes a command-line reference client (`cli`) for testing
the API. See test/ovpncli/cli.cpp.

The basic approach to building an OpenVPN 3 client is to define a client
class that derives from openvpn::ClientAPI::OpenVPNClient, then provide
implementations for callbacks including event and logging notifications:

    class Client : public ClientAPI::OpenVPNClient
    {
    public:
        virtual void event(const Event&) override {  // events delivered here
          ...
        }
        virtual void log(const LogInfo&) override {  // logging delivered here
          ...
        }

        ...
    };

To start the client, first create a openvpn::ProtoContext::ProtoConfig object and
initialize it with the OpenVPN config file and other options:

    ProtoContext::ProtoConfig config;
    config.content = <config_file_content_as_multiline_string>;
    ...

Next, create a client object and evaluate the configuration:

    Client client;
    ClientAPI::EvalConfig eval = client.eval_config(config);
    if (eval.error)
        throw ...;

Finally, in a new worker thread, start the connection:

    ClientAPI::Status connect_status = client.connect();

Note that `client.connect()` will not return until the session has
terminated.

## Top Layer

The top layer of the OpenVPN 3 client is implemented in
test/ovpncli/cli.cpp and openvpn/client/cliopt.hpp. Most of what
this code does is marshalling the configuration and dispatching the
higher-level objects that implement the OpenVPN client session.

## Connection

**class openvpn::ClientConnect**
implements the top-level connection logic for an OpenVPN client
connection. It is concerned with starting, stopping, pausing, and
resuming OpenVPN client connections. It deals with retrying a connection
and handles the connection timeout. It also deals with connection
exceptions and understands the difference between an exception that
should halt any further reconnection attempts (such as `AUTH_FAILED`),
and other exceptions such as network errors that would justify a retry.

Some of the methods in the class (such as `stop`, `pause`, and
`reconnect`) are often called by another thread that is controlling the
connection, therefore thread-safe methods are provided where the
thread-safe function posts a message to the actual connection thread.

In an OpenVPN client connection, the following object stack would be
used:

1.  **class openvpn::ClientConnect** ---
    The top-layer object in an OpenVPN client connection.
2.  **class openvpn::ClientProto::Session** --- The
    OpenVPN client protocol object that subinstantiates the transport
    and tun layer objects.
3.  **class openvpn::ProtoContext** --- The core OpenVPN
    protocol implementation that is common to both client and server.
4.  **openvpn::ProtoStackBase** (with **openvpn::ProtoContext::Packet**) ---
    The bottom-layer class that implements the basic functionality of
    tunneling a protocol over a reliable or unreliable transport layer,
    but isn't specific to OpenVPN per-se.

## Transport Layer

OpenVPN 3 defines abstract base classes for Transport layer
implementations in openvpn/transport/client/transbase.hpp.

Currently, transport layer implementations are provided for:

-   **UDP** --- openvpn/transport/client/udpcli.hpp
-   **TCP** --- openvpn/transport/client/tcpcli.hpp
-   **HTTP Proxy** --- openvpn/transport/client/httpcli.hpp

## Tun Layer

OpenVPN 3 defines abstract base classes for Tun layer implementations in
openvpn/tun/client/tunbase.hpp.

There are two possible approaches to define a Tun layer implementation:

1.  Use a VPN API-centric model (such as for Android or iOS). These
    models derive from **class openvpn::TunBuilderBase**.
2.  Use an OS-specific model such as:
    -   **Linux** --- openvpn/tun/linux/client/tuncli.hpp
    -   **Windows** --- openvpn/tun/win/client/tuncli.hpp
    -   **Mac OS X** --- openvpn/tun/mac/client/tuncli.hpp

## Protocol Layer

The OpenVPN protocol is implemented in **class openvpn::ProtoContext**.

## Options Processing

The parsing and query of the OpenVPN config file is implemented by
**class openvpn::OptionList**.

Note that OpenVPN 3 always assumes an *inline* style of configuration,
where all certs, keys, etc. are defined inline rather than through an
external file reference.

For config files that do use external file references,
**class openvpn::ProfileMerge** is provided to
merge those external file references into an inline form.

## Calling the Client API from other languages

The OpenVPN 3 client API, as defined by
**class openvpn::ClientAPI::OpenVPNClient** in
client/ovpncli.hpp, can be wrapped by the
[Swig](http://www.swig.org/) tool to create bindings for other
languages.

For example, OpenVPN Connect for Android creates a Java binding of the
API using client/ovpncli.i.

Crypto Support - SSLAPI
=======================

The primary TLS/SSL abstraction in OpenVPN 3 core is openvpn::SSLAPI.
This interface implements a memory buffer based TLS state machine.
It includes member functions that allow the read and write of both ciphertext
and plaintext. This allows the encyphering and decyphering operations to be
done separately from any data transport operations.

## Instance Creation

To create an instance of an object implementing this interface a few steps must be taken. First, pull in the
required headers. The unit tests, found in test/unittests/test_sslctx.cpp and a few other
places, can provide examples. Next one must create a configuration object, which is derived
from the openvpn::SSLConfigAPI interface.

Each supported SSL provider implementation provides a class derived from `SSLConfigAPI`,
typically aliased as `SSLLib::SSLAPI::Config` if one includes the helpful header
openvpn/ssl/sslchoose.hpp, which, with proper build time
definitions, will select the SSL provider for the current environment. For example:

    #include <openvpn/ssl/sslchoose.hpp>
    #include <openvpn/ssl/sslapi.hpp>

    SSLLib::SSLAPI::Config::Ptr config = new SSLLib::SSLAPI::Config;

Once the `config` object has been instantiated as above, it must be initialized, for example:

    config->set_mode(Mode(Mode::CLIENT));
    config->load_cert(cert_txt);
    config->load_private_key(pvt_key_txt);
    config->load_ca(cert_txt, false);

*Note: Behavior from SSL provider to provider will vary a bit, see the unit tests for more
sample code.*

The `_txt` symbols are PEM encoded certificates and keys. Once all the options are set,
the config instance is ready to be used to produce a factory instance:

    auto factory = config->new_factory();

Note that use of `auto` is safe here since `new_factory` returns an
openvpn::SSLFactoryAPI::Ptr and not a raw C++ pointer as `new` did for the
`SSLLib::SSLAPI::Config` previously.

Once the factory is instantiated it may be used to create an instance of an SSLAPI
implementation as follows:

    auto sslapi = factory->ssl();

The SSLAPI instance is now ready for use. The factory and configuration both maintain
some state information that the SSLAPI instance requires to function properly so
those must have their lifetime extended to at least as long as the SSLAPI object is
in use.

The `new_factory()` and `ssl()` member functions both return a reference counted smart
pointer, so cleanup of those resources will occur when that pointer and all assigned
from it go out of scope. The example code above also assigns the new config to a smart
pointer, which works properly since the config type is reference count enabled.


Security
========

When developing security software in C++, it's very important to take
advantage of the language and OpenVPN library code to insulate code from
the kinds of bugs that can introduce security vulnerabilities.

Here is a brief set of guidelines:

-   When dealing with strings, use a `std::string` rather than a
    `char *`. When a function only needs to inspect text without taking
    ownership, consider using `std::string_view` to avoid unnecessary copies.

-   When dealing with binary data or buffers, always try to use a
    openvpn::Buffer, openvpn::ConstBuffer, openvpn::BufferAllocatedRc,
    or openvpn::BufferPtr object
    to provide managed access to the buffer, to protect against security
    bugs that arise when using raw buffer pointers. See
    openvpn/buffer/buffer.hpp for the OpenVPN `Buffer` classes.

-   When it's necessary to have a pointer to an object, use
    `std::make_unique<>` for non-shared objects and reference-counted
    smart pointers for shared objects. For shared-pointers, OpenVPN code
    should use the smart pointer classes defined in
    openvpn/common/rc.hpp. Please see the
    comments in this file for documentation.

-   Never use `malloc` or `free`. When allocating objects, use `std::make_unique`
    or `std::make_shared` to allocate the object and create the smart pointer at
    the same time:

        auto ptr = std::make_unique<MyObject>();
        ptr->method();

    This is preferred over using `new` directly.

-   When interfacing with C functions that deal with raw pointers,
    memory allocation, etc., consider wrapping the functionality in C++.
    For an example, see openvpn::enum_dir(), a function
    that returns a list of files in a directory (Unix only) via a
    high-level string vector, while internally calling the low level
    libc methods `opendir`, `readdir`, and `closedir`. Notice how
    `unique_ptr_del` is used to wrap the `DIR` struct in a smart pointer
    with a custom deletion function.

-   When grabbing random entropy that is to be used for cryptographic
    purposes (i.e. for keys, tokens, etc.), always ensure that the RNG
    is crypto-grade by using **class openvpn::StrongRandomAPI** as the RNG type:

        StrongRandomAPI::Ptr rng;
        void set_rng(StrongRandomAPI::Ptr rng_arg) {
            rng = std::move(rng_arg);
        }

-   Any variable whose value is not expected to change should be
    declared `const`. When possible, prefer `constexpr`.

-   Don't use non-const global or static variables unless absolutely
    necessary.

-   When formatting strings, don't use `snprintf`. Instead, use
    `std::ostringstream` or build the string using the `+` `std::string`
    operator:

        std::string format_reconnecting(const int n_seconds) {
            return "Reconnecting in " + openvpn::to_string(n_seconds) + " seconds.";
        }

    or:

        std::string format_reconnecting(const int n_seconds) {
            std::ostringstream os;
            os << "Reconnecting in " << n_seconds << " seconds.";
            return os.str();
        }

Conventions
===========

-   Use the **Asio** library for I/O and timers. Don't deal with
    sockets directly.

-   Never block. If you need to wait for something, use **Asio** timers
    or sockets.

-   Use the OPENVPN_LOG() macro to log stuff. Don't use `printf`.

-   Don't call crypto/ssl libraries directly. Instead use the
    abstraction layers CryptoApi (see openvpn/crypto/cryptochoose.hpp) and
    openvpn::SSLAPI (see openvpn/ssl/sslchoose.hpp) that allow OpenVPN to
    link with different crypto/ssl libraries (such as **OpenSSL** or **mbed
    TLS**).

-   Use openvpn::RandomAPI as a wrapper for random number generators.

-   If you need to deal with configuration file options, see
    class openvpn::OptionList.

-   If you need to deal with time or time durations, use the classes
    under \ref openvpn/time.

-   If you need to deal with IP addresses, see the comprehensive classes
    under \ref openvpn/addr.

-   In general, if you need a general-purpose library class or function,
    look under \ref openvpn/common. Chances are good that
    it's already been implemented.

-   The OpenVPN 3 approach to errors is to count them, rather than
    unconditionally log them. If you need to add a new error counter,
    see openvpn/error/error.hpp.

-   If you need to create a new event type which can be transmitted as a
    notification back to the client API user, see openvpn/client/clievent.hpp.

-   Raw pointers or references can be okay when used by an object to
    point back to its parent (or container), if you can guarantee that
    the object will not outlive its parent. Backreferences to a parent
    object is also a common use case for weak pointers.

-   Use C++ exceptions for error handling and as an alternative to
    `goto`. See OpenVPN's general exception classes and macros in
    openvpn/common/exception.hpp.

-   Use C++ destructors for automatic object cleanup, and so that thrown
    exceptions will not leak objects. Alternatively, use openvpn::Cleanup
    when you need to specify a code block to execute prior to scope exit.
    For example, ensure that the file `pid_fn` is deleted before scope exit:

        auto clean = Cleanup([pid_fn]() {
            if (pid_fn)
                ::unlink(pid_fn);
        });

-   When calling global methods (such as libc `fork`), prepend `::` to
    the symbol name, e.g.:

        struct dirent *e;
        while ((e = ::readdir(dir.get())) != nullptr) {
            ...
        }

-   Use `nullptr` instead of `NULL`.

## Threading

The OpenVPN 3 client core is designed to run in a single thread, with
the UI or controller driving the OpenVPN API running in a different
thread.

It's almost never necessary to create additional threads within the
OpenVPN 3 client core.
