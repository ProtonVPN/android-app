//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2022 OpenVPN Inc.
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
