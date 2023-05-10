//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2022 OpenVPN Inc.
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU Affero General Public License Version 3
//    as published by the Free Software Foundation.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU Affero General Public License for more details.
//
//    You should have received a copy of the GNU Affero General Public License
//    along with this program in the COPYING file.
//    If not, see <http://www.gnu.org/licenses/>.

#pragma once

#include <algorithm>

#include <openvpn/random/randapi.hpp>
#include <openvpn/addr/ip.hpp>

namespace openvpn {
namespace WS {

// These methods become no-ops unless building with a patched Asio and C++14 or higher.
// Define ASIO_RESOLVER_RESULTS_DATA_REQUIRED to force an error if these methods
// cannot be compiled.

#if defined(HAVE_ASIO_RESOLVER_RESULTS_DATA) && __cplusplus >= 201402L

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
#error ASIO_RESOLVER_RESULTS_DATA_REQUIRED is defined but Asio results data are not available or compiler is pre-C++14
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

} // namespace WS
} // namespace openvpn
