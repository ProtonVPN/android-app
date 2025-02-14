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

// A null Asio unit of work, that prevents the Asio event loop from
// exiting.

#ifndef OPENVPN_ASIO_ASIOWORK_H
#define OPENVPN_ASIO_ASIOWORK_H

#include <openvpn/io/io.hpp>

namespace openvpn {
class AsioWork
{
  public:
    AsioWork(openvpn_io::io_context &io_context)
        : work(openvpn_io::make_work_guard(io_context))
    {
    }

  private:
    openvpn_io::executor_work_guard<openvpn_io::io_context::executor_type> work;
};
} // namespace openvpn

#endif
