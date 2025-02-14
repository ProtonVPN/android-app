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

#include <algorithm>

#include <openvpn/random/randapi.hpp>
#include <openvpn/addr/ip.hpp>

namespace openvpn::WS {

// These methods become no-ops unless building with a patched Asio.
// Define ASIO_RESOLVER_RESULTS_DATA_REQUIRED to force an error if these methods
// cannot be compiled.

#if defined(HAVE_ASIO_RESOLVER_RESULTS_DATA)

template <typename RESULTS>
inline void randomize_results(RESULTS &results, RandomAPI &prng)
{
    auto *data = results.data();
    if (!data)
        return;
    std::shuffle(data->begin(), data->end(), prng);
}

template <typename RESULTS>
inline void filter_by_ip_version(RESULTS &results, const IP::Addr::Version ip_ver)
{
    auto *data = results.data();
    if (!data)
        return;

    bool v4;
    switch (ip_ver)
    {
    case IP::Addr::V4:
        v4 = true;
        break;
    case IP::Addr::V6:
        v4 = false;
        break;
    default:
        return;
    }

    // the "auto" lambda parameter makes this C++14 code
    data->erase(std::remove_if(data->begin(),
                               data->end(),
                               [v4](auto &e)
                               { return e.endpoint().address().is_v4() != v4; }),
                data->end());
}

#elif defined(ASIO_RESOLVER_RESULTS_DATA_REQUIRED)
#error ASIO_RESOLVER_RESULTS_DATA_REQUIRED is defined but Asio results data are not available
#else

template <typename RESULTS>
inline void randomize_results(RESULTS &results, RandomAPI &prng)
{
}

template <typename RESULTS>
inline void filter_by_ip_version(RESULTS &results, const IP::Addr::Version ip_ver)
{
}

#endif

} // namespace openvpn::WS
