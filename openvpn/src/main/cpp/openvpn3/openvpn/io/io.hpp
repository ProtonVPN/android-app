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

// Select the i/o reactor

#ifndef OPENVPN_IO_IO_H
#define OPENVPN_IO_IO_H

#ifdef USE_ASIO
#include <asio.hpp>
#define openvpn_io asio
#endif

#endif
