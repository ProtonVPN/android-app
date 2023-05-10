//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2022 OpenVPN Inc.
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

#include <ostream>

namespace openvpn {

namespace C2os {
// Support a coercion safe method to insert a container into an ostream.  The type T
// contained must be ostream'able.
//
// Note also that C2os::Container can be extended by passing in optional arguments
// to the Container ctor that could adjust the formatting (e.g., different
// delimiter, curlies vs square brackets, etc.)
template <typename C>
struct Container
{
    explicit Container(const C &container)
        : ref(container)
    {
    }
    const C &ref;
};

template <typename C>
inline const auto cast(const C &container)
{
    return Container<C>(container);
}

template <typename C>
inline std::ostream &operator<<(std::ostream &os, const Container<C> &container)
{
    constexpr char separator[] = ", ";
    const char *delimiter = "";
    os << "[";
    for (const auto &e : container.ref)
    {
        os << delimiter << e;
        delimiter = separator;
    }
    os << "]";
    return os;
}
} // namespace C2os
} // namespace openvpn
