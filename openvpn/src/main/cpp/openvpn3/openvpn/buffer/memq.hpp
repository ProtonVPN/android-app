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

// A queue of buffers, implemented as std::deque<BufferPtr>.

#ifndef OPENVPN_BUFFER_MEMQ_H
#define OPENVPN_BUFFER_MEMQ_H

#include <deque>

#include <openvpn/common/size.hpp>
#include <openvpn/buffer/buffer.hpp>

namespace openvpn {

  class MemQBase
  {
  public:
    MemQBase() : length(0) {}

    size_t size() const
    {
      return q.size();
    }

    bool empty() const
    {
      return q.empty();
    }

    size_t total_length() const { return length; }

    void clear()
    {
      while (!q.empty())
	q.pop_back();
      length = 0;
    }

    void write_buf(const BufferPtr& bp)
    {
      q.push_back(bp);
      length += bp->size();
    }

    BufferPtr read_buf()
    {
      BufferPtr ret = q.front();
      q.pop_front();
      length -= ret->size();
      return ret;
    }

    BufferPtr& peek()
    {
      return q.front();
    }

    void pop()
    {
      length -= q.front()->size();
      q.pop_front();
    }

    void resize(const size_t cap)
    {
      q.resize(cap);
    }

  protected:
    typedef std::deque<BufferPtr> q_type;
    size_t length;
    q_type q;
  };

} // namespace openvpn

#endif // OPENVPN_BUFFER_MEMQ_H
