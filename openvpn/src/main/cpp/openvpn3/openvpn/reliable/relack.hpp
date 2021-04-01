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

// Handle ACK tracking for reliability layer

#ifndef OPENVPN_RELIABLE_RELACK_H
#define OPENVPN_RELIABLE_RELACK_H

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
    typedef reliable::id_t id_t;

    ReliableAck(const size_t max_ack_list)
      : max_ack_list_(max_ack_list ? max_ack_list : std::numeric_limits<size_t>::max()) {}

    size_t size() const        { return data.size(); }
    bool empty() const         { return data.empty(); }
    void push_back(id_t value) { data.push_back(value); }
    id_t front() const         { return data.front(); }
    void pop_front()           { data.pop_front(); }

    // Called to read incoming ACK IDs from buf and mark them as ACKed in rel_send.
    // If live is false, read the ACK IDs, but don't modify rel_send.
    // Return the number of ACK IDs read.
    template <typename REL_SEND>
    static size_t ack(REL_SEND& rel_send, Buffer& buf, const bool live)
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

    static size_t ack_skip(Buffer& buf)
    {
      const size_t len = buf.pop_front();
      for (size_t i = 0; i < len; ++i)
	read_id(buf);
      return len;
    }

    // copy ACKs from buffer to self
    void read(Buffer& buf)
    {
      const size_t len = buf.pop_front();
      for (size_t i = 0; i < len; ++i)
	{
	  const id_t id = read_id(buf);
	  data.push_back(id);
	}
    }

    // called to write outgoing ACKs to buf
    void prepend(Buffer& buf)
    {
      const size_t len = std::min(data.size(), max_ack_list_);
      for (size_t i = len; i > 0; --i)
	{
	  prepend_id(buf, data[i-1]);
	}
      buf.push_front((unsigned char)len);
      data.erase (data.begin(), data.begin()+len);
    }

    static void prepend_id(Buffer& buf, const id_t id)
    {
      const id_t net_id = htonl(id);
      buf.prepend ((unsigned char *)&net_id, sizeof (net_id));
    }

    static id_t read_id(Buffer& buf)
    {
      id_t net_id;
      buf.read ((unsigned char *)&net_id, sizeof (net_id));
      return ntohl(net_id);
    }

  private:
    size_t max_ack_list_; // Maximum number of ACKs placed in a single message by prepend_acklist()
    std::deque<id_t> data;
  };

} // namespace openvpn

#endif // OPENVPN_RELIABLE_RELACK_H
