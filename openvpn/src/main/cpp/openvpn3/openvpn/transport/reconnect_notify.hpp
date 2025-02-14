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

#ifndef OPENVPN_TRANSPORT_RECONNECT_NOTIFY_H
#define OPENVPN_TRANSPORT_RECONNECT_NOTIFY_H

namespace openvpn {
class ReconnectNotify
{
  public:
    virtual ~ReconnectNotify() = default;

    // When a connection is close to timeout, the core will call this
    // method.  If it returns false, the core will disconnect with a
    // CONNECTION_TIMEOUT event.  If true, the core will enter a PAUSE
    // state.
    virtual bool pause_on_connection_timeout() = 0;
};
} // namespace openvpn

#endif
