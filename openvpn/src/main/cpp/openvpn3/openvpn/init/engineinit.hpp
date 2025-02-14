//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012- OpenVPN Inc.
//
//    SPDX-License-Identifier: MPL-2.0 OR AGPL-3.0-only WITH openvpn3-openssl-exception
//

// process-wide initialization for underlying cryptographic engines

#ifndef OPENVPN_INIT_ENGINEINIT_H
#define OPENVPN_INIT_ENGINEINIT_H

#include <string>

#include <openvpn/common/arch.hpp>

#if defined(USE_OPENSSL)
#include <openvpn/openssl/util/engine.hpp>
#include <openvpn/openssl/ssl/sslctx.hpp>
#endif

#if defined(USE_MINICRYPTO) && (defined(OPENVPN_ARCH_x86_64) || defined(OPENVPN_ARCH_i386))
extern "C"
{
    void OPENSSL_cpuid_setup();
}
#endif

namespace openvpn {

inline void init_openssl(const std::string &engine)
{
#if defined(USE_OPENSSL)
    openssl_setup_engine(engine);
    OpenSSLContext::SSL::init_static();
#elif defined(USE_MINICRYPTO) && (defined(OPENVPN_ARCH_x86_64) || defined(OPENVPN_ARCH_i386))
    OPENSSL_cpuid_setup();
#endif
}

} // namespace openvpn
#endif
