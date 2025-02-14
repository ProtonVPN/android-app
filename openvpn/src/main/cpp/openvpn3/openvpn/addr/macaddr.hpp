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

#ifndef OPENVPN_ADDR_MACADDR_H
#define OPENVPN_ADDR_MACADDR_H

#include <string>
#include <array>

#include <openvpn/common/ostream.hpp>
#include <openvpn/common/hexstr.hpp>

namespace openvpn {

// Fundamental class for representing an ethernet MAC address.

class MACAddr
{
  public:
    MACAddr() = default;

    MACAddr(const unsigned char *addr)
    {
        reset(addr);
    }

    void reset(const unsigned char *addr)
    {
        std::copy_n(addr, addr_.size(), addr_.begin());
    }

    std::string to_string() const
    {
        return render_hex_sep(addr_.data(), addr_.size(), ':');
    }

  private:
    std::array<unsigned char, 6> addr_{};
};

OPENVPN_OSTREAM(MACAddr, to_string)

} // namespace openvpn

#endif // OPENVPN_ADDR_MACADDR_H
