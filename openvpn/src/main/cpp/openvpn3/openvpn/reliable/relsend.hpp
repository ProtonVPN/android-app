//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
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

// Sender side of reliability layer

#ifndef OPENVPN_RELIABLE_RELSEND_H
#define OPENVPN_RELIABLE_RELSEND_H

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/msgwin.hpp>
#include <openvpn/time/time.hpp>
#include <openvpn/reliable/relcommon.hpp>

namespace openvpn {

  template <typename PACKET>
  class ReliableSendTemplate
  {
  public:
    typedef reliable::id_t id_t;

    class Message : public ReliableMessageBase<PACKET>
    {
      friend class ReliableSendTemplate;
      using ReliableMessageBase<PACKET>::defined;

    public:
      bool ready_retransmit(const Time& now) const
      {
	return defined() && now >= retransmit_at_;
      }

      Time::Duration until_retransmit(const Time& now) const
      {
	Time::Duration ret;
	if (now < retransmit_at_)
	  ret = retransmit_at_ - now;
	return ret;
      }

      void reset_retransmit(const Time& now, const Time::Duration& tls_timeout)
      {
	retransmit_at_ = now + tls_timeout;
      }

    private:
      Time retransmit_at_;
    };

    ReliableSendTemplate() : next(0) {}
    ReliableSendTemplate(const id_t span) { init(span); }

    void init(const id_t span)
    {
      next = 0;
      window_.init(next, span);
    }

    // Return the id that the object at the head of the queue
    // would have (even if it isn't defined yet).
    id_t head_id() const { return window_.head_id(); }

    // Return the ID of one past the end of the window
    id_t tail_id() const { return window_.tail_id(); }

    // Return the window size
    id_t span() const { return window_.span(); }

    // Return a reference to M object at id, throw exception
    // if id is not in current window
    Message& ref_by_id(const id_t id)
    {
      return window_.ref_by_id(id);
    }

    // Return the shortest duration for any pending retransmissions
    Time::Duration until_retransmit(const Time& now)
    {
      Time::Duration ret = Time::Duration::infinite();
      for (id_t i = head_id(); i < tail_id(); ++i)
	{
	  const Message& msg = ref_by_id(i);
	  if (msg.defined())
	    {
	      Time::Duration ut = msg.until_retransmit(now);
	      if (ut < ret)
		ret = ut;
	    }
	}
      return ret;
    }

    // Return number of unacknowleged packets in send queue
    unsigned int n_unacked()
    {
      unsigned int ret = 0;
      for (id_t i = head_id(); i < tail_id(); ++i)
	{
	  if (ref_by_id(i).defined())
	    ++ret;
	}
      return ret;
    }

    // Return a fresh Message object that can be used to
    // construct the next packet in the sequence.  Don't call
    // unless ready() returns true.
    Message& send(const Time& now, const Time::Duration& tls_timeout)
    {
      Message& msg = window_.ref_by_id(next);
      msg.id_ = next++;
      msg.reset_retransmit(now, tls_timeout);
      return msg;
    }

    // Return true if send queue is ready to receive another packet
    bool ready() const { return window_.in_window(next); }

    // Remove a message from send queue that has been acknowledged
    void ack(const id_t id) { window_.rm_by_id(id); }

  private:
    id_t next;
    MessageWindow<Message, id_t> window_;
  };

} // namespace openvpn

#endif // OPENVPN_RELIABLE_RELSEND_H
