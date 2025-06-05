OpenVPN 3
=========

OpenVPN 3 is a C++ class library that implements the functionality of an
OpenVPN client, and is protocol-compatible with the OpenVPN 2.x branch.

OpenVPN 3 includes a minimal client wrapper (`cli`) that links in with
the library and provides basic command line functionality.

OpenVPN 3 is currently used in production as the core of the OpenVPN
Connect clients for iOS, Android, Linux, Windows, and Mac OS X.

> [!NOTE]
> OpenVPN 3 does not currently implement server functionality.

OpenVPN 3 Client API
--------------------

OpenVPN 3 is organized as a C++ class library, and the API is defined in
[client/ovpncli.hpp](client/ovpncli.hpp).

A simple command-line wrapper for the API is provided in
[test/ovpncli/cli.cpp](test/ovpncli/cli.cpp).

Please also refer to our Doxygen-generated [API documentation](https://openvpn.github.io/openvpn3/).

Building the OpenVPN 3 client on Linux
--------------------------------------

These instructions were tested on Ubuntu 24.04.

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

    $ cd openvpn3/build
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

On an ARM64 based Mac:

    $ cd ~/src/
    $ mkdir build-openvpn3
    $ cd build-openvpn3
    $ cmake -DOPENSSL_ROOT_DIR=/opt/homebrew/opt/openssl -DCMAKE_PREFIX_PATH=/opt/homebrew ~/src/openvpn3
    $ cmake --build .

For a build on an Intel based Mac:

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

-   Visual Studio 2022
-   CMake
-   vcpkg
-   git

To build:

    > git clone https://github.com/OpenVPN/openvpn3.git && cd openvpn3
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

    $ git clone https://github.com/OpenVPN/openvpn3.git && cd openvpn3
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

    $ cd openvpn3/build
    $ cmake --build . -- coreUnitTests
    $ make test

Contributing
------------

See [CONTRIBUTING.md](CONTRIBUTING.md).

License
-------

See [LICENSE.md](LICENSE.md).
