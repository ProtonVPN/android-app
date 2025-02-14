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

// Common reliability layer classes

#ifndef OPENVPN_RELIABLE_RELCOMMON_H
#define OPENVPN_RELIABLE_RELCOMMON_H

#include <openvpn/crypto/packet_id_control.hpp>

namespace openvpn {

namespace reliable {
typedef std::uint32_t id_t;
constexpr static std::size_t id_size = sizeof(id_t);

} // namespace reliable

template <typename PACKET>
class ReliableMessageBase
{
  public:
    typedef reliable::id_t id_t;

    ReliableMessageBase()
        : id_(0), erased_(false)
    {
    }
    bool defined() const
    {
        return bool(packet);
    }
    bool erased() const
    {
        return erased_;
    }

    void erase()
    {
        id_ = id_t(0);
        packet.reset();
        erased_ = true;
    }

    id_t id() const
    {
        return id_;
    }

    PACKET packet;

  protected:
    id_t id_;
    bool erased_;
};

} // namespace openvpn

#endif // OPENVPN_RELIABLE_RELCOMMON_H
