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


// map/set find

#pragma once

#include <utility>

namespace openvpn {
namespace MSF {

template <typename ITERATOR>
class Iter : public ITERATOR
{
  public:
    template <typename MAP_SET>
    Iter(const MAP_SET &ms, ITERATOR &&iter)
        : ITERATOR(std::move(iter)),
          exists_(*this != ms.end())
    {
    }

    Iter(ITERATOR &&iter)
        : ITERATOR(std::move(iter)),
          exists_(true)
    {
    }

    explicit operator bool() const
    {
        return exists_;
    }

  private:
    bool exists_;
};

// Like ordinary map/set find, but returns an iterator
// that defines an operator bool() method for testing if
// the iterator is defined, so instead of:
//
//   if (iter != map.end())
//     do_stuff();
//
// you can say:
//
//   if (iter)
//     do_stuff();
//
template <typename MAP_SET, typename KEY>
inline auto find(MAP_SET &ms, const KEY &k)
{
    return Iter<decltype(ms.find(k))>(ms, ms.find(k));
}

// Does key exist in map/set?
template <typename MAP_SET, typename KEY>
inline bool exists(MAP_SET &ms, const KEY &k)
{
    return ms.find(k) != ms.end();
}

// Convert an ordinary, dereferenceable iterator to an MSF::Iter
template <typename ITERATOR>
inline auto iter(ITERATOR i)
{
    return Iter<ITERATOR>(std::move(i));
}
} // namespace MSF
} // namespace openvpn
