#!/bin/sh
set -eux

PREFIX="${PREFIX:-${HOME}/opt}"
RUN_COVERITY_SCAN="${RUN_COVERITY_SCAN:-0}"

if [ -n "${MINGW-}" ]; then
    exit 0
fi

if [ "${TRAVIS_OS_NAME}" = "linux" ]; then
    export LD_LIBRARY_PATH="${PREFIX}/lib:${LD_LIBRARY_PATH:-}"
fi

if [ "${TRAVIS_OS_NAME}" = "osx"   ]; then
    export DYLD_LIBRARY_PATH="${PREFIX}/lib:${DYLD_LIBRARY_PATH:-}"
fi


if [ "${SSLLIB}" = "openssl" ]; then
    SSL_LIBS="${OPENSSL_LIBS}"
    SSL_CFLAGS="-DUSE_OPENSSL"
elif [ "${SSLLIB}" = "mbedtls" ]; then
    SSL_LIBS="${MBEDTLS_LIBS}"
    SSL_CFLAGS="-DUSE_MBEDTLS"
else
    echo "Invalid crypto lib: ${SSLLIB}"
    exit 1
fi

LIBS="${SSL_LIBS} -llz4"
CXXFLAGS="-O3 -std=c++14 -Wall -pthread \
          -DOPENVPN_SHOW_SESSION_TOKEN -DHAVE_LZ4 \
          -DUSE_ASIO -DASIO_STANDALONE -DASIO_NO_DEPRECATED ${SSL_CFLAGS}"

if [ "${CC}" = "gcc" ]; then
    CXXFLAGS="${CXXFLAGS} -fwhole-program -flto=4"
fi

INCLUDEDIRS="-I../../asio/asio/include -I${PREFIX}/include -I../../"
LDFLAGS="-L${PREFIX}/lib"

if [ "${TRAVIS_OS_NAME}" = "linux" ]; then
    LDFLAGS="${LDFLAGS} -Wl,--no-as-needed"
fi

if [ "${TRAVIS_OS_NAME}" = "osx" ]; then
    CXXFLAGS="${CXXFLAGS} -stdlib=libc++ -arch x86_64"
    LIBS="${LIBS} -framework Security \
                  -framework CoreFoundation \
                  -framework SystemConfiguration \
                  -framework IOKit \
                  -framework ApplicationServices"
fi

(
    cd test/ovpncli
    ${CXX} ${CXXFLAGS} ${INCLUDEDIRS} ${LDFLAGS} cli.cpp -o cli ${LIBS}
)

(
    cd test/ssl
    ${CXX} ${CXXFLAGS} -DNOERR ${INCLUDEDIRS} ${LDFLAGS} proto.cpp -o proto ${LIBS}
    ./proto
)

if [ "${RUN_COVERITY_SCAN}" = "1" -a "${TRAVIS_BRANCH}" = "${COVERITY_BRANCH}" ]; then
	unset LD_LIBRARY_PATH #don't mess up SSL for curl/wget

	export COVERITY_SCAN_PROJECT_NAME="OpenVPN/openvpn3"
	export COVERITY_SCAN_BRANCH_PATTERN="${COVERITY_BRANCH}"
	export COVERITY_SCAN_NOTIFICATION_EMAIL="scan-reports@openvpn.net"
	export COVERITY_SCAN_BUILD_COMMAND_PREPEND="cd test/ssl"
	export COVERITY_SCAN_BUILD_COMMAND="${CXX} ${CXXFLAGS} ${INCLUDEDIRS} \
                                        ${LDFLAGS} proto.cpp -o proto ${LIBS}"

	# Ignore exit code, script exits with 1 if we're not on the right branch
	curl -s "https://scan.coverity.com/scripts/travisci_build_coverity_scan.sh" | bash || true
fi
