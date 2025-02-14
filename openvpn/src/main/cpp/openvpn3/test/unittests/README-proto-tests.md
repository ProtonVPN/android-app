OpenVPN protocol unit tests
===========================

The `protoUnitTest` utility can be tweaked with build time options
changing the behaviour. These are set via CMake variables.

-   `TEST_PROTO_NTHREADS` - Running test threads (default `1`)

    The number of test client/server pairs running in parallel.

        $ cd $O3/core/build && cmake -DTEST_PROTO_NTHREADS=4 ..
        $ cmake --build . -- test/unittests/coreUnitTests
        $ ./test/unittests/coreUnitTests --gtest_filter="ProtoUnitTest*"

-   `TEST_PROTO_RENEG` - Rengotiation (default `900`)

    To simulate less data-channel activity and more SSL renegotiations

        $ cd $O3/core/build && cmake -DTEST_PROTO_RENEG=90 ..
        $ cmake --build . -- test/unittests/coreUnitTests
        $ ./test/unittests/coreUnitTests --gtest_filter="ProtoUnitTest*"

-   `TEST_PROTO_ITER` - Iterations (default `1000000`)

    For verbose output, lower the number of xmit/recv iterations by
    defining `TEST_PROTO_ITER` to be `10000` or less, e.g.

        $ cd $O3/core/build && cmake -DTEST_PROTO_ITER=1000 ..
        $ cmake --build . -- test/unittests/coreUnitTests
        $ ./test/unittests/coreUnitTests --gtest_filter="ProtoUnitTest*"

-   `TEST_PROTO_SITER` - High-level Session Iterations (default `1`)

        $ cd $O3/core/build && cmake -DTEST_PROTO_SITER=2 ..
        $ cmake --build . -- test/unittests/coreUnitTests
        $ ./test/unittests/coreUnitTests --gtest_filter="ProtoUnitTest*"

-   `TEST_PROTO_VERBOSE` - Verbose log output (`OFF`)

    This will dump details of the protocol traffic as the test runs.
    This is a boolean flag.

        $ cd $O3/core/build && cmake -DTEST_PROTO_VERBOSE=ON ..
        $ cmake --build . -- test/unittests/coreUnitTests
        $ ./test/unittests/coreUnitTests --gtest_filter="ProtoUnitTest*"

Mbed TLS specific
-----------------

### Caveats

When using MbedTLS as both client and server, make sure to build MbedTLS
on Mac OS X with `OSX_SERVER=1`.

Typical output
--------------

    $ cd $O3/core/build
    $ cmake ..
    $ cmake --build . -- test/unittests/coreUnitTests
    $ time ./test/unittests/coreUnitTests --gtest_filter="ProtoUnitTest*"
    Note: Google Test filter = ProtoUnitTest*
    [==========] Running 3 tests from 1 test suite.
    [----------] Global test environment set-up.
    [----------] 3 tests from ProtoUnitTest
    [ RUN      ] ProtoUnitTest.base_single_thread_tls_ekm
    *** app bytes=127295616 net_bytes=196526809 data_bytes=416060253 prog=0000378853/0000378854 D=12700/600/12600/800 N=109/109 SH=13000/13000 HE=0/0
    [       OK ] ProtoUnitTest.base_single_thread_no_tls_ekm (27925 ms)
    [ RUN      ] ProtoUnitTest.base_multiple_thread
    *** app bytes=128091600 net_bytes=197573369 data_bytes=416200172 prog=0000381223/0000381222 D=10600/600/10600/600 N=109/109 SH=13100/12900 HE=0/0
    [       OK ] ProtoUnitTest.base_multiple_thread (28577 ms)
    [----------] 3 tests from ProtoUnitTest (84258 ms total)

    [----------] Global test environment tear-down
    [==========] 3 tests from 1 test suite ran. (84258 ms total)
    [  PASSED  ] 3 tests.

    real	1m24,399s
    user	1m23,736s
    sys	0m0,616s
