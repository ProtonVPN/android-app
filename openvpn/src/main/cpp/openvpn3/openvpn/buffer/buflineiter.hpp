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

#pragma once

#include <openvpn/buffer/buffer.hpp>

namespace openvpn {

// Iterate over the lines in a buffer by returning
// a sub-buffer for each line.  Zero-copy.
class BufferLineIterator
{
  public:
    BufferLineIterator(const ConstBuffer &buf)
        : src(buf)
    {
    }

    // Returns a zero-length buffer at end of iteration
    ConstBuffer next()
    {
        return src.read_alloc_buf(line_len());
    }

  private:
    size_t line_len() const
    {
        const unsigned char *const data = src.c_data();
        size_t i = 0;
        while (i < src.size())
            if (data[i++] == '\n')
                break;
        return i;
    }

    ConstBuffer src;
};

} // namespace openvpn
