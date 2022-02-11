
The proto test utility can be tweaked with build time options changing
the behaviour.  These are set via CMake variables.

* TEST_PROTO_NTHREADS - Running test threads (default 1)
  The number of test client/server pairs running in parallel.

  $ cd $O3/core/build && cmake -DTEST_PROTO_NTHREADS=4 ..
  $ cmake --build . -- test/ssl/proto

* TEST_PROTO_RENEG - Rengotiation (default 900)
  To simulate less data-channel activity and more SSL renegotiations

  $ cd $O3/core/build && cmake -DTEST_PROTO_RENEG=90 ..
  $ cmake --build . -- test/ssl/proto

* TEST_PROTO_ITER - Iterations (default 1000000)
  For verbose output, lower the number of xmit/recv iterations by defining
  TEST_PROTO_ITER to be 10000 or less, e.g.

  $ cd $O3/core/build && cmake -DTEST_PROTO_ITER=1000 ..
  $ cmake --build . -- test/ssl/proto

* TEST_PROTO_SITER - High-level Session Iterations (default 1)

  $ cd $O3/core/build && cmake -DTEST_PROTO_SITER=2 ..
  $ cmake --build . -- test/ssl/proto

* TEST_PROTO_VERBOSE - Verbose log output (off)
  This will dump details of the protocol traffic as the test runs.  This
  is a boolean flag.

  $ cd $O3/core/build && cmake -DTEST_PROTO_VERBOSE=ON ..
  $ cmake --build . -- test/ssl/proto


* Mbed TLS specific - run the crypto library self-test

  $ cd $O3/core/build/test/ssl && ./proto test

Caveats:

 When using MbedTLS as both client and server, make sure to build
 MbedTLS on Mac OS X with OSX_SERVER=1.

Typical output:

  $ cd $O3/core/test/ssl
  $ time ./proto
  *** app bytes=73301015 net_bytes=146383320 data_bytes=36327640 prog=0000218807/0000218806 D=12600/600/12600/800 N=1982/1982 SH=17800/17800 HE=3/6
  real	0m11.003s
  user	0m10.981s
  sys	0m0.004s
