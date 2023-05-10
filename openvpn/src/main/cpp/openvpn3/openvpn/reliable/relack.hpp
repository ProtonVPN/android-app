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

// Handle ACK tracking for reliability layer

#pragma once


#include <deque>
#include <algorithm>
#include <limits>

#include <openvpn/common/socktypes.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/crypto/packet_id.hpp>
#include <openvpn/reliable/relcommon.hpp>

namespace openvpn {

class ReliableAck
{
  public:
    static constexpr size_t maximum_acks_ack_v1 = 8;
    static constexpr size_t maximum_acks_control_v1 = 4;
    typedef reliable::id_t id_t;

    explicit ReliableAck() = default;
    size_t size() const;
    bool empty() const;
    bool acks_ready() const;
    void push_back(id_t value)
    {
        data.push_back(value);
    }
    id_t front() const
    {
        return data.front();
    }
    void pop_front()
    {
        data.pop_front();
    }

    // Called to read incoming ACK IDs from buf and mark them as ACKed in rel_send.
    // If live is false, read the ACK IDs, but don't modify rel_send.
    // Return the number of ACK IDs read.
    template <typename REL_SEND>
    static size_t ack(REL_SEND &rel_send, Buffer &buf, const bool live)
    {
        const size_t len = buf.pop_front();
        for (size_t i = 0; i < len; ++i)
        {
            const id_t id = read_id(buf);
            if (live)
                rel_send.ack(id);
        }
        return len;
    }

    static size_t ack_skip(Buffer &buf)
    {
        const size_t len = buf.pop_front();
        for (size_t i = 0; i < len; ++i)
            read_id(buf);
        return len;
    }

    // copy ACKs from buffer to self
    void read(Buffer &buf)
    {
        const size_t len = buf.pop_front();
        for (size_t i = 0; i < len; ++i)
        {
            const id_t id = read_id(buf);
            data.push_back(id);
        }
    }

    /**
     * handles the reACK logic and reACK/ACK list manipulation. pulls as many repeated ACKs as we can fit
     * into the packet from the reACK queue, and pushes the fresh never-been-ACKed ids into the other end
     * of the reACK queue. Enforces a limit on the size of the reACK queue and may discard reACKs sometimes.
     *
     */
    void prepend(Buffer &buf, bool ackv1)
    {
        size_t max_acks = ackv1 ? maximum_acks_ack_v1 : maximum_acks_control_v1;

        size_t acks_added = 0;

        while (acks_added < max_acks && data.size() > 0)
        {
            auto ack = data.front();
            data.pop_front();

            prepend_id(buf, ack);
            acks_added++;
            add_ack_to_reack(ack);
        }

        /* the already pushed acks are in front of the re_acks list, so we
         * skip over them */
        while (acks_added < re_acks.size() && acks_added < max_acks)
        {
            prepend_id(buf, re_acks[acks_added]);
            acks_added++;
        }
        buf.push_front((unsigned char)acks_added);
    }

    static void prepend_id(Buffer &buf, const id_t id)
    {
        const id_t net_id = htonl(id);
        buf.prepend((unsigned char *)&net_id, sizeof(net_id));
    }

    static id_t read_id(Buffer &buf)
    {
        id_t net_id;
        buf.read((unsigned char *)&net_id, sizeof(net_id));
        return ntohl(net_id);
    }

    size_t resend_size();

  private:
    void add_ack_to_reack(id_t ack);

    std::deque<id_t> data;
    std::deque<id_t> re_acks;
};

/**
 * Returns the number of outstanding ACKs that have been sent.
 */
inline size_t ReliableAck::size() const
{
    return data.size();
}

/**
 * Returns true if all outstanding ACKs are empty, otherwise false. Acks
 * that can be resend are ignored.
 *
 * @return Returns true if all ACKs are empty, otherwise false;
 *
 */
inline bool ReliableAck::empty() const
{
    return data.empty();
}

/**
 * Returns true if either outstanding acks are present or ACKs for resending
 * are present.
 *
 */
inline bool ReliableAck::acks_ready() const
{
    return !data.empty() || !re_acks.empty();
}

inline void ReliableAck::add_ack_to_reack(id_t ack)
{
    /* Check if the element is already in the array to avoid duplicates */
    for (auto it = re_acks.begin(); it != re_acks.end(); it++)
    {
        if (*it == ack)
        {
            re_acks.erase(it);
            break;
        }
    }

    re_acks.push_front(ack);
    if (re_acks.size() > maximum_acks_ack_v1)
    {
        re_acks.pop_back();
    }
}

inline size_t ReliableAck::resend_size()
{
    return re_acks.size();
}

} // namespace openvpn
