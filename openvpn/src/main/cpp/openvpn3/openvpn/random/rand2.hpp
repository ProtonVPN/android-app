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

#include <utility>

#include <openvpn/random/randapi.hpp>

namespace openvpn {

// By convention, rng is crypto-strength while prng is not.
struct Rand2
{
    Rand2()
    {
    }

    Rand2(StrongRandomAPI::Ptr rng_arg,
          RandomAPI::Ptr prng_arg)
        : rng(std::move(rng_arg)),
          prng(std::move(prng_arg))
    {
    }

    Rand2(StrongRandomAPI::Ptr rng_arg)
        : rng(rng_arg),
          prng(rng_arg)
    {
    }

    bool defined() const
    {
        return rng && prng;
    }

    StrongRandomAPI::Ptr rng;
    RandomAPI::Ptr prng;
};

} // namespace openvpn
