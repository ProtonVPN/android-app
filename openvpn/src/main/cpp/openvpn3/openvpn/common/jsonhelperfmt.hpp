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

#include <string>

#include <openvpn/common/stringtempl2.hpp>

namespace openvpn {
namespace json {

// format name.title but omit .title if title is empty
template <typename NAME, typename TITLE>
inline std::string fmt_name(const NAME &name, const TITLE &title)
{
    if (!StringTempl::empty(title))
        return StringTempl::to_string(title) + '.' + StringTempl::to_string(name);
    else
        return StringTempl::to_string(name);
}

// if title is not a number, treat as an ordinary string
template <typename TITLE,
          typename std::enable_if<!std::is_arithmetic<TITLE>::value, int>::type = 0>
inline std::string fmt_name_cast(const TITLE &title)
{
    return StringTempl::to_string(title);
}

// if title is a number, assume that it is referring to an array element
template <typename TITLE,
          typename std::enable_if<std::is_arithmetic<TITLE>::value, int>::type = 0>
inline std::string fmt_name_cast(const TITLE &title)
{
    return "element." + StringTempl::to_string(title);
}

} // namespace json
} // namespace openvpn
