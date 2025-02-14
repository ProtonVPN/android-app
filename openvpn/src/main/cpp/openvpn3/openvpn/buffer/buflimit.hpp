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

#ifndef OPENVPN_BUFFER_BUFLIMIT_H
#define OPENVPN_BUFFER_BUFLIMIT_H

#include <openvpn/buffer/buffer.hpp>

namespace openvpn {

template <typename T>
class BufferLimit
{
  public:
    BufferLimit()
    {
        set_max(0, 0);
        reset();
    }

    BufferLimit(const T max_lines_arg,
                const T max_bytes_arg)
    {
        set_max(max_lines_arg, max_bytes_arg);
        reset();
    }

    virtual ~BufferLimit() = default;

    void set_max(const T max_lines_arg,
                 const T max_bytes_arg)
    {
        max_lines = max_lines_arg;
        max_bytes = max_bytes_arg;
    }

    void reset()
    {
        n_bytes = n_lines = 0;
    }

    void add(const Buffer &buf)
    {
        T size = (T)buf.size();
        n_bytes += size;
        if (max_bytes && n_bytes > max_bytes)
            bytes_exceeded();
        if (max_lines)
        {
            const unsigned char *p = buf.c_data();
            while (size--)
            {
                const unsigned char c = *p++;
                if (c == '\n')
                {
                    ++n_lines;
                    if (n_lines > max_lines)
                        lines_exceeded();
                }
            }
        }
    }

    virtual void bytes_exceeded() = 0;
    virtual void lines_exceeded() = 0;

  protected:
    T max_lines;
    T max_bytes;
    T n_bytes;
    T n_lines;
};

} // namespace openvpn

#endif
