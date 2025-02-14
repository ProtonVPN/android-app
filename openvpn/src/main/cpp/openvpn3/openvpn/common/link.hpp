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
    Link() = default;

    Link(typename SEND::Ptr send_arg)
        : send(std::move(send_arg))
    {
    }

    Link(SEND *send_arg)
        : send(send_arg)
    {
        static_assert(std::is_base_of_v<RC<thread_unsafe_refcount>, SEND> || std::is_base_of_v<RC<thread_safe_refcount>, SEND>,
                      "Using a raw pointer to initialise Link requires an intrusive pointer");
    }

    typename SEND::Ptr send;
};

} // namespace openvpn

#endif
