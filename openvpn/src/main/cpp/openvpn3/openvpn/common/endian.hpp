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

#include <openvpn/common/endian_platform.hpp>

namespace openvpn {
namespace Endian {
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
} // namespace Endian
} // namespace openvpn
