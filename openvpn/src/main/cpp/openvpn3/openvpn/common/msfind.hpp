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


// map/set find

#pragma once

#include <utility>

namespace openvpn::MSF {

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
    return ms.contains(k);
}

// Convert an ordinary, dereferenceable iterator to an MSF::Iter
template <typename ITERATOR>
inline auto iter(ITERATOR i)
{
    return Iter<ITERATOR>(std::move(i));
}
} // namespace openvpn::MSF
