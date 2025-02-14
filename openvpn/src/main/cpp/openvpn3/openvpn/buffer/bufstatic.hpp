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

// constant-length Buffer for writing that cannot be extended
template <std::size_t N>
class StaticBuffer : public Buffer
{
  public:
    StaticBuffer()
        : Buffer(data, N, false)
    {
    }

    StaticBuffer(const StaticBuffer &) = delete;
    StaticBuffer &operator=(const StaticBuffer &) = delete;

  private:
    unsigned char data[N];
};

} // namespace openvpn
