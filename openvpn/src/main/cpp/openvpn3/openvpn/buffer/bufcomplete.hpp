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

#ifndef OPENVPN_BUFFER_BUFCOMPLETE_H
#define OPENVPN_BUFFER_BUFCOMPLETE_H

#include <cstdint>   // for std::uint32_t, uint16_t, uint8_t
#include <algorithm> // for std::min

#include <openvpn/buffer/buffer.hpp>

namespace openvpn {

class BufferComplete
{
  public:
    virtual ~BufferComplete() = default;

    /* each advance/get method returns false if message is incomplete */
    bool advance(size_t size)
    {
        while (size)
        {
            if (!fetch_buffer())
                return false;
            const size_t s = std::min(size, buf.size());
            buf.advance(s);
            size -= s;
        }
        return true;
    }

    // assumes embedded big-endian uint16_t length in the stream
    bool advance_string()
    {
        std::uint8_t h, l;
        if (!get(h))
            return false;
        if (!get(l))
            return false;
        return advance(size_t(h) << 8 | size_t(l));
    }

    bool advance_to_null()
    {
        std::uint8_t c;
        while (get(c))
        {
            if (!c)
                return true;
        }
        return false;
    }

    bool get(std::uint8_t &c)
    {
        if (!fetch_buffer())
            return false;
        c = buf.pop_front();
        return true;
    }

    bool defined() const
    {
        return buf.defined();
    }

  protected:
    void reset_buf(const Buffer &buf_arg)
    {
        buf = buf_arg;
    }

    void reset_buf()
    {
        buf.reset_content();
    }

  private:
    virtual void next_buffer() = 0;

    bool fetch_buffer()
    {
        if (buf.defined())
            return true;
        next_buffer();
        return buf.defined();
    }

    Buffer buf;
};

} // namespace openvpn

#endif
