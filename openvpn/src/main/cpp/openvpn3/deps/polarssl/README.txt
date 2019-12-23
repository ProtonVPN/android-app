Building PolarSSL for android.

First, build static OpenSSL for PolarSSL/OpenSSL bridge
(the build-openssl-small script may be used).

Next build libminicrypto.a from libcrypto.a :

  $O3/polarssl/build-mini-openssl ref

Finally, build PolarSSL:

  TARGET=android $O3/polarssl/build-polarssl
