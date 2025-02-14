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

#include <openvpn/common/endian_platform.hpp>

namespace openvpn::Endian {
#if defined(OPENVPN_LITTLE_ENDIAN)
inline size_t e16(const size_t v)
{
    return v;
}
inline size_t e16rev(const size_t v)
{
    return 15 - v;
}
inline size_t e4(const size_t v)
{
    return v;
}
inline size_t e4rev(const size_t v)
{
    return 3 - v;
}
inline size_t e2(const size_t v)
{
    return v;
}
inline size_t e2rev(const size_t v)
{
    return 1 - v;
}
#elif defined(OPENVPN_BIG_ENDIAN)
inline size_t e16rev(const size_t v)
{
    return v;
}
inline size_t e16(const size_t v)
{
    return 15 - v;
}
inline size_t e4rev(const size_t v)
{
    return v;
}
inline size_t e4(const size_t v)
{
    return 3 - v;
}
inline size_t e2rev(const size_t v)
{
    return v;
}
inline size_t e2(const size_t v)
{
    return 1 - v;
}
#else
#error One of OPENVPN_LITTLE_ENDIAN or OPENVPN_BIG_ENDIAN must be defined
#endif
} // namespace openvpn::Endian
