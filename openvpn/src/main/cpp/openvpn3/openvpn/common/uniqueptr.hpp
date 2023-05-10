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

#ifndef OPENVPN_COMMON_UNIQUEPTR_H
#define OPENVPN_COMMON_UNIQUEPTR_H

#include <memory>
#include <functional>
#include <type_traits>

namespace openvpn {
template <typename T>
using unique_ptr_del = std::unique_ptr<T, std::function<void(T *)>>;

// The unique_ptr_slab variation of the std::unique_ptr<T> addresses the issue of
// new/delete mismatches in code that allocates a _memory slab_ with the global
// _operator_ new but de-allocates an _object_ with a delete _expression_.  The use
// case that manifests the mismatch is as follows: Allocate a slab of memory that has
// a C struct at the head of the slab, with a "my_type mt[0];" as the head's last
// member.  The slab is cast to the type of the C struct, but sized to contain N
// my_type items.
//
// The object based de-allocation is the behavior of the std::default_delete<T>
// template; it is used by the std::unique_ptr<T> if the user does not specify an
// alternative deleter.  The unique_ptr_slab resolves the mismatch with an alternative
// deleter that de-allocates the _memory slab_ with the global _operator_ delete.
template <typename T>
void delete_slab(T *ptr)
{
    ::operator delete(const_cast<typename std::remove_cv<T>::type *>(ptr));
}

template <typename T>
class slab_deleter
{
  public:
    slab_deleter()
    {
    }
    void operator()(T *ptr)
    {
        delete_slab(ptr);
    }
};

template <typename T>
using unique_ptr_slab = std::unique_ptr<T, slab_deleter<T>>;
} // namespace openvpn

#endif
