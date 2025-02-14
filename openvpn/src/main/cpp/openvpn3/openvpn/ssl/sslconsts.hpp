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

#ifndef OPENVPN_SSL_SSLCONSTS_H
#define OPENVPN_SSL_SSLCONSTS_H

namespace openvpn::SSLConst {

// Special return values from SSL read/write methods
enum
{
    // Indicates that no cleartext data is available now (until
    // more ciphertext is pushed into the SSL engine).
    SHOULD_RETRY = -1,

    // Return value from read_cleartext indicating that peer
    // has sent a Close Notify message.
    PEER_CLOSE_NOTIFY = -2,
};

// SSL config flags
enum
{
    // Show SSL status and cert chain in verify method
    LOG_VERIFY_STATUS = (1 << 0),

    // Disable peer verification
    NO_VERIFY_PEER = (1 << 1),

    // [client only] Enable client-side SNI (Server Name Indication)
    // when hostname is provided
    ENABLE_CLIENT_SNI = (1 << 2),

    // [client only] Don't require that the hostname matches
    // the common name in the certificate.
    NO_VERIFY_HOSTNAME = (1 << 3),

    // [server only] Don't automatically fail connections on
    // bad peer cert.  Succeed the connection, but pass the
    // fail status data via AuthCert so the higher layers
    // can handle it.
    DEFERRED_CERT_VERIFY = (1 << 4),

    // [server only] When running as a server, require that
    // clients that connect to us have their certificate
    // purpose set to server.
    SERVER_TO_SERVER = (1 << 5),

    // Peer certificate is optional
    PEER_CERT_OPTIONAL = (1 << 6),

    // [server only] Send a list of client CAs to the client
    SEND_CLIENT_CA_LIST = (1 << 7),

    // Verify peer by fingerprint, makes CA optional
    VERIFY_PEER_FINGERPRINT = (1 << 8),

    // last flag marker
    LAST = (1 << 9)
};

// filter all but SSL flags
inline unsigned int ssl_flags(const unsigned int flags)
{
    return flags & (LAST - 1);
}
} // namespace openvpn::SSLConst

#endif
