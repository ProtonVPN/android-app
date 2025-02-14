OpenVPN 3
=========

OpenVPN 3 is a C++ class library that implements the functionality of an
OpenVPN client, and is protocol-compatible with the OpenVPN 2.x branch.

OpenVPN 3 includes a minimal client wrapper (`cli`) that links in with
the library and provides basic command line functionality.

OpenVPN 3 is currently used in production as the core of the OpenVPN
Connect clients for iOS, Android, Linux, Windows, and Mac OS X.

NOTE: OpenVPN 3 does not currently implement server functionality.

[TOC]

OpenVPN 3 Client API
--------------------

OpenVPN 3 is organized as a C++ class library, and the API is defined in
[client/ovpncli.hpp](client/ovpncli.hpp).

A simple command-line wrapper for the API is provided in
[test/ovpncli/cli.cpp](test/ovpncli/cli.cpp).

Building the OpenVPN 3 client on Linux
--------------------------------------

These instructions were tested on Ubuntu 22.04.

Install essential dependencies:

    $ sudo apt install --no-install-recommends ca-certificates cmake g++ git iproute2 ninja-build pkg-config
    $ sudo apt install --no-install-recommends libasio-dev libcap-dev liblz4-dev libjsoncpp-dev libssl-dev libxxhash-dev

Potentially install optional dependencies:

    $ sudo apt install --no-install-recommends libmbedtls-dev liblzo2-dev python3-dev swig

Clone the OpenVPN 3 source repo:

    $ git clone https://github.com/OpenVPN/openvpn3.git

Build the OpenVPN 3 client wrapper (cli) with OpenSSL library:

    $ cd openvpn3 && mkdir build && cd build
    $ cmake -GNinja ..
    $ cmake --build .
    $ ctest # Run Unit Tests

To use mbedTLS, use:

    $ cmake -GNinja -DUSE_MBEDTLS=ON ..

Run OpenVPN 3 client:

    $ sudo test/ovpncli/ovpncli myprofile.ovpn route-nopull

Options used:

-   `myprofile.ovpn` : OpenVPN config file (must have .ovpn extension)
-   `route-nopull` : if you are connected via ssh, prevent ssh session
    lockout

### Using cli with ovpn-dco

ovpn-dco is a kernel module which optimises data channel encryption and
transport, providing better performance. The cli will detect when the
kernel module is available and enable dco automatically (use `--no-dco`
to disable this).

Download, build and install ovpn-dco:

    $ sudo apt install make
    $ git clone https://github.com/OpenVPN/ovpn-dco.git
    $ cd ovpn-dco
    $ make && sudo make install
    $ sudo modprobe ovpn-dco

Install core dependencies:

    $ sudo apt install libnl-genl-3-dev

Build cli with ovpn-dco support:

    $ cd $O3/core/build
    $ cmake -DCLI_OVPNDCO=ON .. && cmake --build .
    $ sudo test/ovpncli/ovpncli [--no-dco] myprofile.ovpn

Options:

-   `myprofile.ovpn` : OpenVPN config file (must have .ovpn extension)
-   `--no-dco` : disable data channel offload (optional)

Building the OpenVPN 3 client on macOS
--------------------------------------

OpenVPN 3 should be built in a non-root macOS account. Make sure that
Xcode is installed with optional command-line tools.

Create the directory `~/src`:

    $ mkdir -p ~/src

Clone the OpenVPN 3 repo:

    $ cd ~/src
    $ git clone https://github.com/OpenVPN/openvpn3.git openvpn3

Install the dependencies:

Ensure that [homebrew](<https://brew.sh/>) is set up.

    $  brew install asio cmake jsoncpp lz4 openssl pkg-config xxhash

Now build the OpenVPN 3 client executable:

On a ARM64 based Mac:

    $ cd ~/src/
    $ mkdir build-openvpn3
    $ cd build-openvpn3
    $ cmake -DOPENSSL_ROOT_DIR=/opt/homebrew/opt/openssl -DCMAKE_PREFIX_PATH=/opt/homebrew ~/src/openvpn3
    $ cmake --build .

For a build on a Intel based Mac:

    $ cd ~/src/
    $ mkdir build-openvpn3
    $ cd build-openvpn3
    $ cmake -DOPENSSL_ROOT_DIR=/usr/local/opt/openssl -DCMAKE_PREFIX_PATH=/usr/local/opt ~/src/openvpn3
    $ cmake --build .

This will build the OpenVPN 3 client library with a small client wrapper
(`ovpncli`) and the unit tests.

These build scripts will create binaries with the same architecture as
the host it is running on. The Mac OS X tuntap driver is not required,
as OpenVPN 3 can use the integrated utun interface if available.

To view the client wrapper options:

    $ ./test/ovpncli/ovpncli -h

To connect:

    $ ./test/ovpncli/ovpncli client.ovpn

Building the OpenVPN 3 client for Windows
-----------------------------------------

![image](https://github.com/OpenVPN/openvpn3/actions/workflows/msbuild.yml/badge.svg)

### Building with Visual Studio

Prerequisites:

-   Visual Studio 2019 or 2022
-   CMake
-   vcpkg
-   git

To build:

    > git clone https://github.com/OpenVPN/openvpn3.git core && cd core
    > set VCPKG_ROOT=<path to vcpkg checkout>
    > cmake --preset win-amd64-release
    > cmake --build --preset win-amd64-release --target ovpncli

### Building with MinGW

This build should work on both Windows and Linux.

Prerequisites:

-   mingw-w64
-   CMake
-   vcpkg
-   git

To build:

    $ git clone https://github.com/OpenVPN/openvpn3.git core && cd core
    $ export VCPKG_ROOT=<path to vcpkg checkout>
    $ cmake --preset mingw-x64-release
    $ cmake --build --preset mingw-x64-release --target ovpncli


Testing
-------

The OpenVPN 3 core includes a stress/performance test of the OpenVPN
protocol implementation. The test basically creates a virtualized lossy
network between two OpenVPN protocol objects, triggers TLS negotiations
between them, passes control/data channel messages, and measures the
ability of the OpenVPN protocol objects to perform and remain in a valid
state.

The OpenVPN protocol implementation that is being tested is here:
[openvpn/ssl/proto.hpp](openvpn/ssl/proto.hpp)

The test code itself is here:
[test/unittests/test\_proto.cpp](test/unittests/test_proto.cpp) It will
be built and run as part of the unit test suite.

The unit tests are based on Google Test framework. To run unit tests,
you need to install CMake and build Google Test.

Build and run tests on Linux:

    $ cd $O3/core/build
    $ cmake --build . -- test/unittests/coreUnitTests
    $ make test

Developer Guide
---------------

OpenVPN 3 is written in C++17 and developers who are moving from C to
C++ should take some time to familiarize themselves with key C++ design
patterns such as
[*RAII*](https://en.wikipedia.org/wiki/Resource_acquisition_is_initialization).

### OpenVPN 3 Client Core

OpenVPN 3 is designed as a class library, with an API that is
essentially defined inside of **namespace openvpn::ClientAPI** with headers and
implementation in [client](client) and header-only library files under
[openvpn](openvpn).

The concise definition of the client API is essentially
**class openvpn::ClientAPI::OpenVPNClient** in [client/ovpncli.hpp](client/ovpncli.hpp) with
several important extensions to the API found in:

-   **class openvpn::TunBuilderBase** in
    [openvpn/tun/builder/base.hpp](openvpn/tun/builder/base.hpp) ---
    Provides an abstraction layer defining the *tun* interface, and is
    especially useful for interfacing with an OS-layer VPN API.
-   **class openvpn::ExternalPKIBase** in
    [openvpn/pki/epkibase.hpp](openvpn/pki/epkibase.hpp) --- Provides a
    callback for external private key operations, and is useful for
    interfacing with an OS-layer Keychain such as the Keychain on iOS,
    Mac OS X, and Android, and the Crypto API on Windows.
-   **class openvpn::ClientAPI::LogReceiver** in [client/ovpncli.hpp](client/ovpncli.hpp) ---
    Provides an abstraction layer for the delivery of logging messages.

OpenVPN 3 includes a command-line reference client (`cli`) for testing
the API. See [test/ovpncli/cli.cpp](test/ovpncli/cli.cpp).

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

To start the client, first create a **openvpn::ProtoContext::ProtoConfig** object and
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

### Top Layer

The top layer of the OpenVPN 3 client is implemented in
[test/ovpncli/cli.cpp](test/ovpncli/cli.cpp) and
[openvpn/client/cliopt.hpp](openvpn/client/cliopt.hpp). Most of what
this code does is marshalling the configuration and dispatching the
higher-level objects that implement the OpenVPN client session.

### Connection

**class openvpn::ClientConnect** in
[openvpn/client/cliconnect.hpp](openvpn/client/cliconnect.hpp)
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

1.  **class openvpn::ClientConnect** in
    [openvpn/client/cliconnect.hpp](openvpn/client/cliconnect.hpp) ---
    The top-layer object in an OpenVPN client connection.
2.  **class openvpn::ClientProto::Session** in
    [openvpn/client/cliproto.hpp](openvpn/client/cliproto.hpp) --- The
    OpenVPN client protocol object that subinstantiates the transport
    and tun layer objects.
3.  **class openvpn::ProtoContext** in
    [openvpn/ssl/proto.hpp](openvpn/ssl/proto.hpp) --- The core OpenVPN
    protocol implementation that is common to both client and server.
4.  **openvpn::ProtoStackBase** (with **openvpn::Packet**) in
    [openvpn/ssl/protostack.hpp](openvpn/ssl/protostack.hpp) --- The
    bottom-layer class that implements the basic functionality of
    tunneling a protocol over a reliable or unreliable transport layer,
    but isn't specific to OpenVPN per-se.

### Transport Layer

OpenVPN 3 defines abstract base classes for Transport layer
implementations in
[openvpn/transport/client/transbase.hpp](openvpn/transport/client/transbase.hpp).

Currently, transport layer implementations are provided for:

-   **UDP** ---
    [openvpn/transport/client/udpcli.hpp](openvpn/transport/client/udpcli.hpp)
-   **TCP** ---
    [openvpn/transport/client/tcpcli.hpp](openvpn/transport/client/tcpcli.hpp)
-   **HTTP Proxy** ---
    [openvpn/transport/client/httpcli.hpp](openvpn/transport/client/httpcli.hpp)

### Tun Layer

OpenVPN 3 defines abstract base classes for Tun layer implementations in
[openvpn/tun/client/tunbase.hpp](openvpn/tun/client/tunbase.hpp).

There are two possible approaches to define a Tun layer implementation:

1.  Use a VPN API-centric model (such as for Android or iOS). These
    models derive from **class openvpn::TunBuilderBase** in
    [openvpn/tun/builder/base.hpp](openvpn/tun/builder/base.hpp)
2.  Use an OS-specific model such as:
    -   **Linux** ---
        [openvpn/tun/linux/client/tuncli.hpp](openvpn/tun/linux/client/tuncli.hpp)
    -   **Windows** ---
        [openvpn/tun/win/client/tuncli.hpp](openvpn/tun/win/client/tuncli.hpp)
    -   **Mac OS X** ---
        [openvpn/tun/mac/client/tuncli.hpp](openvpn/tun/mac/client/tuncli.hpp)

### Protocol Layer

The OpenVPN protocol is implemented in **class openvpn::ProtoContext** in
[openvpn/ssl/proto.hpp](openvpn/ssl/proto.hpp).

### Options Processing

The parsing and query of the OpenVPN config file is implemented by
**class openvpn::OptionList** in
[openvpn/common/options.hpp](openvpn/common/options.hpp).

Note that OpenVPN 3 always assumes an *inline* style of configuration,
where all certs, keys, etc. are defined inline rather than through an
external file reference.

For config files that do use external file references,
**class openvpn::ProfileMerge** in
[openvpn/options/merge.hpp](openvpn/options/merge.hpp) is provided to
merge those external file references into an inline form.

### Calling the Client API from other languages

The OpenVPN 3 client API, as defined by
**class openvpn::ClientAPI::OpenVPNClient** in
[client/ovpncli.hpp](client/ovpncli.hpp), can be wrapped by the
[Swig](http://www.swig.org/) tool to create bindings for other
languages.

For example, OpenVPN Connect for Android creates a Java binding of the
API using [client/ovpncli.i](client/ovpncli.i).

Security
--------

When developing security software in C++, it's very important to take
advantage of the language and OpenVPN library code to insulate code from
the kinds of bugs that can introduce security vulnerabilities.

Here is a brief set of guidelines:

-   When dealing with strings, use a `std::string` rather than a
    `char *`.

-   When dealing with binary data or buffers, always try to use a
    `openvpn::Buffer`, `openvpn::ConstBuffer`, `openvpn::BufferAllocatedRc`,
    or `openvpn::BufferPtr` object
    to provide managed access to the buffer, to protect against security
    bugs that arise when using raw buffer pointers. See
    [openvpn/buffer/buffer.hpp](openvpn/buffer/buffer.hpp) for the
    OpenVPN `Buffer` classes.

-   When it's necessary to have a pointer to an object, use
    `std::unique_ptr<>` for non-shared objects and reference-counted
    smart pointers for shared objects. For shared-pointers, OpenVPN code
    should use the smart pointer classes defined in
    [openvpn/common/rc.hpp](openvpn/common/rc.hpp). Please see the
    comments in this file for documentation.

-   Never use `malloc` or `free`. When allocating objects, use the C++
    `new` operator and then immediately construct a smart pointer to
    reference the object:

        std::unique_ptr<MyObject> ptr = new MyObject();
        ptr->method();

-   When interfacing with C functions that deal with raw pointers,
    memory allocation, etc., consider wrapping the functionality in C++.
    For an example, see `enum_dir()` in
    [openvpn/common/enumdir.hpp](openvpn/common/enumdir.hpp), a function
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
    declared `const`.

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

-   OpenVPN 3 is a *header-only* library, therefore all free functions
    outside of classes should have the `inline` attribute.

### Conventions

-   Use the **Asio** library for I/O and timers. Don't deal with
    sockets directly.

-   Never block. If you need to wait for something, use **Asio** timers
    or sockets.

-   Use the `OPENVPN_LOG()` macro to log stuff. Don't use `printf`.

-   Don't call crypto/ssl libraries directly. Instead use the
    abstraction layers ([openvpn/crypto](openvpn/crypto) and
    [openvpn/ssl](openvpn/ssl)) that allow OpenVPN to link with
    different crypto/ssl libraries (such as **OpenSSL** or **mbed
    TLS**).

-   Use openvpn::RandomAPI as a wrapper for random number generators
    ([openvpn/random/randapi.hpp](openvpn/random/randapi.hpp)).

-   If you need to deal with configuration file options, see
    class openvpn::OptionList in
    [openvpn/common/options.hpp](openvpn/common/options.hpp).

-   If you need to deal with time or time durations, use the classes
    under [openvpn/time](openvpn/time).

-   If you need to deal with IP addresses, see the comprehensive classes
    under [openvpn/addr](openvpn/addr).

-   In general, if you need a general-purpose library class or function,
    look under [openvpn/common](openvpn/common). Chances are good that
    it's already been implemented.

-   The OpenVPN 3 approach to errors is to count them, rather than
    unconditionally log them. If you need to add a new error counter,
    see [openvpn/error/error.hpp](openvpn/error/error.hpp).

-   If you need to create a new event type which can be transmitted as a
    notification back to the client API user, see
    [openvpn/client/clievent.hpp](openvpn/client/clievent.hpp).

-   Raw pointers or references can be okay when used by an object to
    point back to its parent (or container), if you can guarantee that
    the object will not outlive its parent. Backreferences to a parent
    object is also a common use case for weak pointers.

-   Use C++ exceptions for error handling and as an alternative to
    `goto`. See OpenVPN's general exception classes and macros in
    [openvpn/common/exception.hpp](openvpn/common/exception.hpp).

-   Use C++ destructors for automatic object cleanup, and so that thrown
    exceptions will not leak objects. Alternatively, use openvpn::Cleanup in
    [openvpn/common/cleanup.hpp](openvpn/common/cleanup.hpp) when you
    need to specify a code block to execute prior to scope exit. For
    example, ensure that the file `pid_fn` is deleted before scope exit:

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

### Threading

The OpenVPN 3 client core is designed to run in a single thread, with
the UI or controller driving the OpenVPN API running in a different
thread.

It's almost never necessary to create additional threads within the
OpenVPN 3 client core.

Contributing
------------

See [CONTRIBUTING.md](CONTRIBUTING.md).

License
-------

See [LICENSE.md](LICENSE.md).
