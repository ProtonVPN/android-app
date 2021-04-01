#!/bin/sh
set -eux

# Set defaults
PREFIX="${PREFIX:-${HOME}/opt}"

if [ -n "${MINGW-}" ]; then
    MAKEFLAGS=-j$(nproc) scripts/mingw/build
    exit 0
fi

download_asio () {
    if [ ! -d "download-cache/asio" ]; then
        git clone https://github.com/chriskohlhoff/asio.git \
            download-cache/asio
    else
        (
            cd download-cache/asio
            if [ "$(git log -1 --format=%H)" != "${ASIO_VERSION}" ]; then
                git checkout master
                git pull
                git checkout ${ASIO_VERSION}
            fi
        )
    fi
}

build_asio () {
    (
        if [ ! -L asio ]; then
            rm -Rf asio
            ln -s download-cache/asio asio
        fi
    )
}

download_lz4 () {
    if [ ! -f "download-cache/lz4-${LZ4_VERSION}.tar.gz" ]; then
        wget "https://github.com/lz4/lz4/archive/v${LZ4_VERSION}.tar.gz" \
            -O download-cache/lz4-${LZ4_VERSION}.tar.gz
    fi
}

build_lz4 () {
    if [ "$(cat ${PREFIX}/.lz4-version)" != "${LZ4_VERSION}" ]; then
        tar zxf download-cache/lz4-${LZ4_VERSION}.tar.gz
        (
            cd "lz4-${LZ4_VERSION}"
            make default CC=$CC CXX=$CXX
            make install PREFIX="${PREFIX}"
        )
        echo "${LZ4_VERSION}" > "${PREFIX}/.lz4-version"
    fi
}

download_mbedtls () {
    if [ ! -f "download-cache/mbedtls-${MBEDTLS_VERSION}-apache.tgz" ]; then
        wget -P download-cache/ \
            "https://tls.mbed.org/download/mbedtls-${MBEDTLS_VERSION}-apache.tgz"
    fi
}

build_mbedtls () {
    if [ "$(cat ${PREFIX}/.mbedtls-version)" != "${MBEDTLS_VERSION}" ]; then
        tar zxf download-cache/mbedtls-${MBEDTLS_VERSION}-apache.tgz
        (
            cd "mbedtls-${MBEDTLS_VERSION}"
            make CC=$CC CXX=$CXX
            make install DESTDIR="${PREFIX}"
        )
        echo "${MBEDTLS_VERSION}" > "${PREFIX}/.mbedtls-version"
    fi
}

download_openssl () {
    if [ ! -f "download-cache/openssl-${OPENSSL_VERSION}.tar.gz" ]; then
        wget -P download-cache/ \
            "https://www.openssl.org/source/openssl-${OPENSSL_VERSION}.tar.gz"
    fi
}

build_openssl_linux () {
    (
        cd "openssl-${OPENSSL_VERSION}/"
        ./config shared --prefix="${PREFIX}" --openssldir="${PREFIX}" -DPURIFY
        make all install_sw
    )
}

build_openssl_osx () {
    (
        cd "openssl-${OPENSSL_VERSION}/"
        ./Configure darwin64-x86_64-cc shared \
            --prefix="${PREFIX}" --openssldir="${PREFIX}" -DPURIFY
        make depend all install_sw
    )
}

build_openssl () {
    if [ "$(cat ${PREFIX}/.openssl-version)" != "${OPENSSL_VERSION}" ]; then
        tar zxf "download-cache/openssl-${OPENSSL_VERSION}.tar.gz"
        if [ "${TRAVIS_OS_NAME}" = "osx" ]; then
            build_openssl_osx
        elif [ "${TRAVIS_OS_NAME}" = "linux" ]; then
            build_openssl_linux
        fi
        echo "${OPENSSL_VERSION}" > "${PREFIX}/.openssl-version"
    fi
}

# Enable ccache
if [ "${TRAVIS_OS_NAME}" != "osx" ] && [ -z ${CHOST+x} ]; then
    # ccache not available on osx, see:
    # https://github.com/travis-ci/travis-ci/issues/5567
    # also ccache not enabled for cross builds
    mkdir -p "${HOME}/bin"
    ln -s "$(which ccache)" "${HOME}/bin/${CXX}"
    ln -s "$(which ccache)" "${HOME}/bin/${CC}"
    PATH="${HOME}/bin:${PATH}"
fi

# Download and build crypto lib
if [ "${SSLLIB}" = "openssl" ]; then
    download_openssl
    build_openssl
elif [ "${SSLLIB}" = "mbedtls" ]; then
    download_mbedtls
    build_mbedtls
else
    echo "Invalid crypto lib: ${SSLLIB}"
    exit 1
fi

download_asio
build_asio

download_lz4
build_lz4
