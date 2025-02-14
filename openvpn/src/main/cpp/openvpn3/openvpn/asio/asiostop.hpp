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

#ifndef OPENVPN_ASIO_ASIOSTOP_H
#define OPENVPN_ASIO_ASIOSTOP_H

#include <openvpn/io/io.hpp>

#include <openvpn/common/stop.hpp>

namespace openvpn {
class AsioStopScope : public Stop::Scope
{
  public:
    AsioStopScope(openvpn_io::io_context &io_context,
                  Stop *stop,
                  std::function<void()> &&method)
        : Stop::Scope(stop, post_method(io_context, std::move(method)))
    {
    }

  private:
    static std::function<void()> post_method(openvpn_io::io_context &io_context, std::function<void()> &&method)
    {
        return [&io_context, method = std::move(method)]()
        { openvpn_io::post(io_context, std::move(method)); };
    }
};

} // namespace openvpn

#endif
