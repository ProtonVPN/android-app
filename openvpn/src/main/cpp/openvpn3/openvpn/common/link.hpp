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

#ifndef OPENVPN_COMMON_LINK_H
#define OPENVPN_COMMON_LINK_H

#include <utility>

namespace openvpn {

// Link creates a sender-receiver relationship between two objects.
// SEND is the sender interface, while RECV is the receiver interface.
// The receiver class should publicly inherit from Link and implement
// the receiver interface.
// Multiple inheritance of Link is intended for objects that play a
// receiver role for multiple interfaces (such as the server-side
// client instance object), and for this reason RECV common base
// classes such as RC should be virtually inherited.
// Usage of Link may create a memory reference cycle between
// Link-derived objects and the SEND object.  Therefore be sure
// to manually break cycles as part of an explicit stop() method.
template <typename SEND, typename RECV>
class Link : public RECV
{
  protected:
    Link()
    {
    }
    Link(typename SEND::Ptr send_arg)
        : send(std::move(send_arg))
    {
    }
    Link(SEND *send_arg)
        : send(send_arg)
    {
    }

    typename SEND::Ptr send;
};

} // namespace openvpn

#endif
