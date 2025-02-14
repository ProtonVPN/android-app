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
    MemQBase()
        : length(0)
    {
    }

    size_t size() const
    {
        return q.size();
    }

    bool empty() const
    {
        return q.empty();
    }

    size_t total_length() const
    {
        return length;
    }

    void clear()
    {
        while (!q.empty())
            q.pop_back();
        length = 0;
    }

    void write_buf(const BufferPtr &bp)
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

    BufferPtr &peek()
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
