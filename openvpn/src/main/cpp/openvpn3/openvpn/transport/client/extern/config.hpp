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

#ifndef OPENVPN_TRANSPORT_CLIENT_EXTERN_CONFIG_H
#define OPENVPN_TRANSPORT_CLIENT_EXTERN_CONFIG_H

#include <sstream>
#include <openvpn/transport/client/transbase.hpp>
#include <openvpn/transport/socket_protect.hpp>
#include <openvpn/client/remotelist.hpp>

namespace openvpn::ExternalTransport {
struct Config
{
    Protocol protocol;
    RemoteList::Ptr remote_list;
    bool server_addr_float = false;
    bool synchronous_dns_lookup = false;
    Frame::Ptr frame;
    SessionStats::Ptr stats;
    SocketProtect *socket_protect = nullptr;
};
} // namespace openvpn::ExternalTransport

#endif
