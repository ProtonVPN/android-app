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

#include <openvpn/common/stringtempl2.hpp>

namespace openvpn::json {

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

} // namespace openvpn::json
