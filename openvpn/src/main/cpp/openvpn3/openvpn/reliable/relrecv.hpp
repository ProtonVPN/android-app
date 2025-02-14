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

// Receiver side of reliability layer

#ifndef OPENVPN_RELIABLE_RELRECV_H
#define OPENVPN_RELIABLE_RELRECV_H

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/msgwin.hpp>
#include <openvpn/reliable/relcommon.hpp>

namespace openvpn {

template <typename PACKET>
class ReliableRecvTemplate
{
  public:
    OPENVPN_SIMPLE_EXCEPTION(rel_next_sequenced_not_ready);

    typedef reliable::id_t id_t;

    class Message : public ReliableMessageBase<PACKET>
    {
        friend class ReliableRecvTemplate;
    };

    ReliableRecvTemplate()
    {
    }
    ReliableRecvTemplate(const id_t span, id_t start_at = 0)
    {
        init(span, start_at);
    }

    void init(const id_t span, id_t start_at = 0)
    {
        window_.init(start_at, span);
    }

    // Call with unsequenced packet off of the wire.
    // Will return receive flags below.
    enum
    {
        ACK_TO_SENDER = (1 << 0), // ACK for this packet should be returned to sender
        IN_WINDOW = (1 << 1)      // Packet is in-window (otherwise, packet is dropped)
    };
    unsigned int receive(const PACKET &packet, const id_t id)
    {
        unsigned int rflags;
        if (window_.in_window(id))
        {
            Message &m = window_.ref_by_id(id);
            m.id_ = id;
            m.packet = packet;
            rflags = ACK_TO_SENDER | IN_WINDOW;
        }
        else
            rflags = window_.pre_window(id) ? ACK_TO_SENDER : 0;
        return rflags;
    }

    // Return true if next_sequenced() is ready to return next message
    bool ready() const
    {
        return window_.head_defined();
    }

    // Return next message in sequence.
    // Requires that ready() returns true.
    Message &next_sequenced()
    {
        return window_.ref_head();
    }

    // Call after message returned by receive is ready to
    // be disposed of.
    void advance()
    {
        window_.rm_head_nocheck();
    }

  private:
    MessageWindow<Message, id_t> window_;
};

} // namespace openvpn

#endif // OPENVPN_RELIABLE_RELRECV_H
