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

// seed OpenSSL's random number generator with /dev/urandom

#include <openssl/rand.h>

#include <openvpn/random/devurand.hpp>

namespace openvpn {
inline void openssl_reseed_rng()
{
    unsigned char entropy[64];

    RandomAPI::Ptr rng(new DevURand);
    rng->rand_bytes(entropy, sizeof(entropy));

    RAND_seed(entropy, sizeof(entropy));
}
} // namespace openvpn
