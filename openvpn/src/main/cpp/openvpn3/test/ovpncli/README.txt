Build on Mac:

  With MbedTLS:
    GCC_EXTRA="-ferror-limit=4" STRIP=1 MTLS=1 SNAP=1 LZ4=1 build cli

  With MbedTLS and Minicrypto:
    GCC_EXTRA="-ferror-limit=4" STRIP=1 MTLS=1 MINI=1 SNAP=1 LZ4=1 build cli

  With MbedTLS, Minicrypto, and C++11 for optimized move constructors:
    GCC_EXTRA="-ferror-limit=4 -std=c++11" STRIP=1 MTLS=1 MINI=1 SNAP=1 LZ4=1 build cli

  With OpenSSL:
    GCC_EXTRA="-ferror-limit=4" STRIP=1 OSSL=1 OPENSSL_SYS=1 SNAP=1 LZ4=1 build cli

  With MbedTLS/AppleCrypto hybrid:
    GCC_EXTRA="-ferror-limit=4" STRIP=1 HYBRID=1 SNAP=1 LZ4=1 build cli

Build on Linux:

  With MbedTLS:
    STRIP=1 SNAP=1 LZ4=1 MTLS=1 NOSSL=1 build cli

  With OpenSSL:
    STRIP=1 SNAP=1 LZ4=1 build cli
