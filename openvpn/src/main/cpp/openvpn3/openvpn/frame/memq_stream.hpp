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

// A queue of buffers for handling streamed data such as data received
// from or to be sent to a TCP socket

#ifndef OPENVPN_FRAME_MEMQ_STREAM_H
#define OPENVPN_FRAME_MEMQ_STREAM_H

#include <algorithm>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/buffer/memq.hpp>
#include <openvpn/frame/frame.hpp>

namespace openvpn {

class MemQStream : public MemQBase
{
  public:
    OPENVPN_SIMPLE_EXCEPTION(frame_uninitialized);

    MemQStream()
    {
    }
    explicit MemQStream(const Frame::Ptr &frame)
        : frame_(frame)
    {
    }
    void set_frame(const Frame::Ptr &frame)
    {
        frame_ = frame;
    }

    size_t pending() const
    {
        return total_length();
    }

    void write(const unsigned char *data, size_t size)
    {
        if (frame_)
        {
            const Frame::Context &fc = (*frame_)[Frame::READ_BIO_MEMQ_STREAM];
            if (size)
            {
                ConstBuffer b(data, size, true);
                // Any residual space remaining in most recently pushed buffer?
                if (!q.empty())
                {
                    BufferPtr &qb = q.back();
                    const size_t write_size = std::min(b.size(), fc.remaining_payload(*qb));
                    const unsigned char *from = b.read_alloc(write_size);
                    qb->write(from, write_size);
                    length += write_size;
                }

                // Start a new buffer
                while (b.size())
                {
                    auto newbuf = BufferAllocatedRc::Create();
                    fc.prepare(*newbuf);
                    const size_t write_size = std::min(b.size(), fc.payload());
                    const unsigned char *from = b.read_alloc(write_size);
                    newbuf->write(from, write_size);
                    q.push_back(newbuf);
                    length += write_size;
                }
            }
        }
        else
            throw frame_uninitialized();
    }

    size_t read(unsigned char *data, size_t len)
    {
        Buffer b(data, len, false);
        while (!q.empty())
        {
            const size_t remaining = b.remaining();
            if (!remaining)
                break;
            BufferPtr &qf = q.front();
            const size_t read_size = std::min(remaining, qf->size());
            unsigned char *to = b.write_alloc(read_size);
            qf->read(to, read_size);
            length -= read_size;
            if (qf->empty())
                q.pop_front();
        }
        return b.size();
    }

  private:
    Frame::Ptr frame_;
};

} // namespace openvpn

#endif // OPENVPN_FRAME_MEMQ_STREAM_H
