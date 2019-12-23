Building proto.cpp sample:

On Mac

  Build with MbedTLS client and server (no minicrypto ASM algs for MbedTLS):

    MTLS=1 build proto

  Build with MbedTLS client and server using 4 concurrent threads (no minicrypto ASM algs for MbedTLS):

    -DN_THREADS=4" MTLS=1 build proto

  Build with MbedTLS client and OpenSSL server (no minicrypto ASM algs for MbedTLS):

    MTLS=1 OSSL=1 OPENSSL_SYS=1 build proto

  Build with OpenSSL client and server:

    OSSL=1 OPENSSL_SYS=1 build proto

  Build with AppleSSL client and OpenSSL server:

    SSL_BOTH=1 OPENSSL_SYS=1 build proto

  Build with MbedTLS client and server + minicrypto lib:

    MTLS=1 MINI=1 build proto

  Build with MbedTLS client and server (no minicrypto ASM algs for MbedTLS),
  except substitute AppleSSL crypto algs for the client side:

    HYBRID=1 build proto

On Linux:

  Build with MbedTLS client and server (no ASM crypto algs):

    MTLS=1 NOSSL=1 build proto

  Build with OpenSSL client and server:

    OSSL=1 build proto

  Build with MbedTLS client and OpenSSL server:

    MTLS=1 OSSL=1 build proto

  Build with MbedTLS client and server (no ASM crypto algs)
  using Profile-Guided Optimization:

    PGEN=1 MTLS=1 NOSSL=1 build proto && ./proto && PUSE=1 MTLS=1 NOSSL=1 build proto

Variations:

  To simulate less data-channel activity and more SSL renegotiations
  (RENEG default is 900):

  GCC_EXTRA="-DRENEG=90" build proto

  For verbose output, lower the number of xmit/recv iterations by defining
  ITER to be 10000 or less, e.g.

    GCC_EXTRA="-DITER=1000" build proto

  Crypto self-test (MbedTLS must be built with DEBUG_BUILD=1 or SELF_TEST=1):

    ./proto test

Caveats:

 When using MbedTLS as both client and server, make sure to build
 MbedTLS on Mac OS X with OSX_SERVER=1.

Typical output:

  $ time ./proto
  *** app bytes=73301015 net_bytes=146383320 data_bytes=36327640 prog=0000218807/0000218806 D=12600/600/12600/800 N=1982/1982 SH=17800/17800 HE=3/6
  real	0m11.003s
  user	0m10.981s
  sys	0m0.004s
