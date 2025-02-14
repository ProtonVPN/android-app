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
//

#ifndef OPENVPN_SSL_IS_OPENVPN_PROTOCOL_H
#define OPENVPN_SSL_IS_OPENVPN_PROTOCOL_H

#include <cstddef> // for std::size_t

namespace openvpn {

/**
 * @brief  Given either the first 2 or 3 bytes of an initial client -> server
 *         data payload, return true if the protocol is that of an OpenVPN
 *         client attempting to connect with an OpenVPN server.
 *
 * @param  p   Buffer containing packet data.
 * @param  len Packet (buffer) length.
 *
 * @return true if we're dealing with an OpenVPN client, false otherwise.
 */
inline bool is_openvpn_protocol(const unsigned char *p, std::size_t len)
{
    static constexpr int P_CONTROL_HARD_RESET_CLIENT_V2 = 7;
    static constexpr int P_CONTROL_HARD_RESET_CLIENT_V3 = 10;
    static constexpr int P_OPCODE_SHIFT = 3;

    if (len >= 3)
    {
        int plen = (p[0] << 8) | p[1];

        if (p[2] == (P_CONTROL_HARD_RESET_CLIENT_V3 << P_OPCODE_SHIFT))
        {
            /* WKc is at least 290 byte (not including metadata):
             *
             * 16 bit len + 256 bit HMAC + 2048 bit Kc = 2320 bit
             *
             * This is increased by the normal length of client handshake +
             * tls-crypt overhead (32)
             *
             * For metadata tls-crypt-v2.txt does not explicitly specify
             * an upper limit but we also have TLS_CRYPT_V2_MAX_WKC_LEN
             * as 1024 bytes. We err on the safe side with 255 extra overhead
             *
             * We don't do the 2 byte check for tls-crypt-v2 because it is very
             * unrealistic to have only 2 bytes available.
             */
            return (plen >= 336 && plen < (1024 + 255));
        }

        /* For non tls-crypt2 we assume the packet length to valid between
         * 14 and 255 */
        return plen >= 14 && plen <= 255
               && (p[2] == (P_CONTROL_HARD_RESET_CLIENT_V2 << P_OPCODE_SHIFT));
    }

    if (len >= 2)
    {
        int plen = (p[0] << 8) | p[1];
        return plen >= 14 && plen <= 255;
    }

    return true;
}

} // namespace openvpn
#endif
