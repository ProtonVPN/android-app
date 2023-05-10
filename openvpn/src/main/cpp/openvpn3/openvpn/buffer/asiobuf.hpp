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

#ifndef OPENVPN_BUFFER_ASIOBUF_H
#define OPENVPN_BUFFER_ASIOBUF_H

#include <openvpn/io/io.hpp>

#include <openvpn/buffer/buffer.hpp>

namespace openvpn {
class AsioConstBufferSeq2
{
  public:
    AsioConstBufferSeq2(const Buffer &b1, const Buffer &b2)
        : buf{openvpn_io::const_buffer{b1.c_data(), b1.size()},
              openvpn_io::const_buffer{b2.c_data(), b2.size()}}
    {
    }

    // Implement the ConstBufferSequence requirements.
    typedef openvpn_io::const_buffer value_type;
    typedef const openvpn_io::const_buffer *const_iterator;
    const openvpn_io::const_buffer *begin() const
    {
        return buf;
    }
    const openvpn_io::const_buffer *end() const
    {
        return buf + 2;
    }

    size_t size() const
    {
        return openvpn_io::buffer_size(buf[0])
               + openvpn_io::buffer_size(buf[1]);
    }

  private:
    const openvpn_io::const_buffer buf[2];
};
} // namespace openvpn

#endif
