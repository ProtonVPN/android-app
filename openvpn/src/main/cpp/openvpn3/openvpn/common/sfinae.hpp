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

namespace openvpn::SFINAE {

/// \cond KNOWN_WARNINGS
/// error: Detected potential recursive class relation between class openvpn::SFINAE::Rank and base class openvpn::SFINAE::Rank< I - 1 >!
template <int I>
struct Rank : Rank<I - 1>
{
};
/// \endcond
template <>
struct Rank<0>
{
};

} // namespace openvpn::SFINAE
