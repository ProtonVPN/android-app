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

#pragma once

#include <string>

#ifdef USE_OPENSSL
#include <openvpn/openssl/util/init.hpp>
#endif

namespace openvpn {

// process-wide initialization for crypto subsystem
class crypto_init
{
#if defined(OPENSSL_NEEDS_INIT)
    openssl_init openssl_init_;
#endif
    /*
     * We add a dummy member so this class does not count as trivial
     * class. Otherwise it will trigger:
     *  warning: private field 'crypto_init_' is not used [-Wunused-private-field]
     */
    std::string dummy;
};

} // namespace openvpn
