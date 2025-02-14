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

// A queue of datagram buffers for handling sequences of UDP packets

#ifndef OPENVPN_FRAME_MEMQ_DGRAM_H
#define OPENVPN_FRAME_MEMQ_DGRAM_H

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/buffer/memq.hpp>
#include <openvpn/frame/frame.hpp>

namespace openvpn {

class MemQDgram : public MemQBase
{
  public:
    OPENVPN_SIMPLE_EXCEPTION(frame_uninitialized);

    MemQDgram()
    {
    }
    explicit MemQDgram(const Frame::Ptr &frame)
        : frame_(frame)
    {
    }
    void set_frame(const Frame::Ptr &frame)
    {
        frame_ = frame;
    }

    size_t pending() const
    {
        return empty() ? 0 : q.front()->size();
    }

    void write(const unsigned char *data, size_t size)
    {
        if (frame_)
        {
            const Frame::Context &fc = (*frame_)[Frame::READ_BIO_MEMQ_STREAM];
            q.push_back(fc.copy(data, size));
            length += size;
        }
        else
            throw frame_uninitialized();
    }

    size_t read(unsigned char *data, size_t len)
    {
        BufferPtr &b = q.front();
        if (len > b->size())
            len = b->size();
        b->read(data, len);
        if (b->empty())
            q.pop_front();
        length -= len;
        return len;
    }

  private:
    Frame::Ptr frame_;
};

} // namespace openvpn

#endif // OPENVPN_FRAME_MEMQ_DGRAM_H
