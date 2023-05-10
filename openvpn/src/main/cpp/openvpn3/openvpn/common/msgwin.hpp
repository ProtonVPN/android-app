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

#ifndef OPENVPN_COMMON_MSGWIN_H
#define OPENVPN_COMMON_MSGWIN_H

#include <deque>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>

// Fundamental, lowest-level object of OpenVPN protocol reliability layer

namespace openvpn {

// MessageWindow --
//   On receiving side: used to order packets which may be received out-of-order
//   On sending side: used to buffer unacknowledged packets
//   M : message class, must define default constructor, defined(), and erase() methods
//   id_t : sequence number object, usually unsigned int
template <typename M, typename id_t>
class MessageWindow
{
  public:
    OPENVPN_SIMPLE_EXCEPTION(message_window_ref_by_id);
    OPENVPN_SIMPLE_EXCEPTION(message_window_rm_head);

    MessageWindow()
        : head_id_(0), span_(0)
    {
    }

    MessageWindow(const id_t starting_head_id, const id_t span)
        : head_id_(starting_head_id), span_(span)
    {
    }

    void init(const id_t starting_head_id, const id_t span)
    {
        head_id_ = starting_head_id;
        span_ = span;
        q_.clear();
    }

    // Return true if id is within current window
    bool in_window(const id_t id) const
    {
        return id >= head_id_ && id < head_id_ + span_;
    }

    // Return true if id is before current window
    bool pre_window(const id_t id) const
    {
        return id < head_id_;
    }

    // Return a reference to M object at id, throw exception
    // if id is not in current window
    M &ref_by_id(const id_t id)
    {
        if (in_window(id))
        {
            grow(id);
            return q_[id - head_id_];
        }
        else
            throw message_window_ref_by_id();
    }

    // Remove the M object at id, is a no-op if
    // id not in window.  Do a purge() as a last
    // step to advance the head_id_ if it's now
    // pointing at undefined M objects.
    void rm_by_id(const id_t id)
    {
        if (in_window(id))
        {
            grow(id);
            M &m = q_[id - head_id_];
            m.erase();
        }
        purge();
    }

    // Return true if an object at head of queue is defined
    bool head_defined() const
    {
        return (!q_.empty() && q_.front().defined());
    }

    // Return the id that the object at the head of the queue
    // would have (even if it isn't defined yet).
    id_t head_id() const
    {
        return head_id_;
    }

    // Return the id of one past the end of the window
    id_t tail_id() const
    {
        return head_id_ + span_;
    }

    // Return the window size
    id_t span() const
    {
        return span_;
    }

    // Return a reference to the object at the front of the queue
    M &ref_head()
    {
        return q_.front();
    }

    // Remove the object at head of queue, throw an exception if undefined
    void rm_head()
    {
        if (head_defined())
            rm_head_nocheck();
        else
            throw message_window_rm_head();
    }

    // Remove the object at head of queue without error checking (other than
    // that provided by std::deque object).  Don't call this method unless
    // head_defined() returns true.
    void rm_head_nocheck()
    {
        q_.front().erase();
        q_.pop_front();
        ++head_id_;
    }

  private:
    // Expand the queue if necessary so that id maps
    // to an object in the queue
    void grow(const id_t id)
    {
        const size_t needed_index = id - head_id_;
        while (q_.size() <= needed_index)
            q_.push_back(M());
    }

    // Purge all undefined objects at the head of
    // the queue, advancing the head_id_
    void purge()
    {
        while (!q_.empty() && q_.front().erased())
        {
            q_.pop_front();
            ++head_id_;
        }
    }

    id_t head_id_; // id of msgs[0]
    id_t span_;
    std::deque<M> q_;
};

} // namespace openvpn

#endif // OPENVPN_COMMON_MSGWIN_H
