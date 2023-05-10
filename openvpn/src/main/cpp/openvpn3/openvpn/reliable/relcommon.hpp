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

// Common reliability layer classes

#ifndef OPENVPN_RELIABLE_RELCOMMON_H
#define OPENVPN_RELIABLE_RELCOMMON_H

#include <openvpn/crypto/packet_id.hpp>

namespace openvpn {

namespace reliable {
typedef PacketID::id_t id_t;
}

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
