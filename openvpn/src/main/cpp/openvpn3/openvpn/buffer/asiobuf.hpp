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
