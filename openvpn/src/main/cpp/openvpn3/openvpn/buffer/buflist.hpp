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

#ifndef OPENVPN_BUFFER_BUFLIST_H
#define OPENVPN_BUFFER_BUFLIST_H

#include <list>
#include <utility>

#include <openvpn/buffer/buffer.hpp>
#include <openvpn/buffer/bufstr.hpp>

namespace openvpn {

template <template <typename...> class COLLECTION>
struct BufferCollection : public COLLECTION<BufferPtr>
{
    using COLLECTION<BufferPtr>::size;
    using COLLECTION<BufferPtr>::front;
    using COLLECTION<BufferPtr>::empty;
    using COLLECTION<BufferPtr>::back;
    using COLLECTION<BufferPtr>::emplace_back;

    BufferPtr join(const size_t headroom,
                   const size_t tailroom,
                   const bool size_1_optim) const
    {
        // special optimization if list contains
        // a single element that satisfies our
        // headroom/tailroom constraints.
        if (size_1_optim
            && size() == 1
            && front()->offset() >= headroom
            && front()->remaining() >= tailroom)
            return front();

        // first pass -- measure total size
        const size_t size = join_size();

        // allocate buffer
        auto big = BufferAllocatedRc::Create(size + headroom + tailroom, 0);
        big->init_headroom(headroom);

        // second pass -- copy data
        for (auto &b : *this)
            big->write(b->c_data(), b->size());

        return big;
    }

    BufferPtr join() const
    {
        return join(0, 0, true);
    }

    size_t join_size() const
    {
        size_t size = 0;
        for (auto &b : *this)
            size += b->size();
        return size;
    }

    std::string to_string() const
    {
        BufferPtr bp = join();
        return buf_to_string(*bp);
    }

    BufferCollection copy() const
    {
        BufferCollection ret;
        for (auto &b : *this)
            ret.emplace_back(BufferAllocatedRc::Create(*b));
        return ret;
    }

    void put_consume(BufferAllocated &buf, const size_t tailroom = 0)
    {
        const size_t s = buf.size();
        if (!s)
            return;
        if (!empty())
        {
            // special optimization if buf data fits in
            // back() unused tail capacity -- if so, append
            // buf to existing back().
            BufferPtr &b = back();
            const size_t r = b->remaining(tailroom);
            if (s < r)
            {
                b->write(buf.read_alloc(s), s);
                return;
            }
        }
        emplace_back(BufferAllocatedRc::Create(std::move(buf)));
    }
};

typedef BufferCollection<std::list> BufferList;
typedef BufferCollection<std::vector> BufferVector;
} // namespace openvpn

#endif
