//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU Affero General Public License Version 3
//    as published by the Free Software Foundation.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU Affero General Public License for more details.
//
//    You should have received a copy of the GNU Affero General Public License
//    along with this program in the COPYING file.
//    If not, see <http://www.gnu.org/licenses/>.

// Define OpenVPN error codes and a method to convert them to a string representation

#ifndef OPENVPN_ERROR_ERROR_H
#define OPENVPN_ERROR_ERROR_H

#include <openvpn/common/arraysize.hpp>

namespace openvpn {
  namespace Error {

    enum Type {
      SUCCESS=0,           // no error
      NETWORK_RECV_ERROR,  // errors receiving on network socket
      NETWORK_EOF_ERROR,   // EOF received on TCP network socket
      NETWORK_SEND_ERROR,  // errors sending on network socket
      NETWORK_UNAVAILABLE, // network unavailable
      DECRYPT_ERROR,       // data channel encrypt/decrypt error
      HMAC_ERROR,          // HMAC verification failure
      REPLAY_ERROR,        // error from PacketIDReceive
      BUFFER_ERROR,        // exception thrown in Buffer methods
      CC_ERROR,            // general control channel errors
      BAD_SRC_ADDR,        // packet from unknown source address
      COMPRESS_ERROR,      // compress/decompress errors on data channel
      RESOLVE_ERROR,       // DNS resolution error
      SOCKET_PROTECT_ERROR, // Error calling protect() method on socket
      TUN_READ_ERROR,      // read errors on tun/tap interface
      TUN_WRITE_ERROR,     // write errors on tun/tap interface
      TUN_FRAMING_ERROR,   // error with tun PF_INET/PF_INET6 prefix
      TUN_SETUP_FAILED,    // error setting up tun/tap interface
      TUN_IFACE_CREATE,    // error creating tun/tap interface
      TUN_IFACE_DISABLED,  // tun/tap interface is disabled
      TUN_ERROR,           // general tun error
      TUN_REGISTER_RINGS_ERROR, // error registering ring buffers with wintun
      TAP_NOT_SUPPORTED,   // dev tap is present in profile but not supported
      REROUTE_GW_NO_DNS,   // redirect-gateway specified without alt DNS servers
      TRANSPORT_ERROR,     // general transport error
      TCP_OVERFLOW,        // TCP output queue overflow
      TCP_SIZE_ERROR,      // bad embedded uint16_t TCP packet size
      TCP_CONNECT_ERROR,   // client error on TCP connect
      UDP_CONNECT_ERROR,   // client error on UDP connect
      SSL_ERROR,           // errors resulting from read/write on SSL object
      SSL_PARTIAL_WRITE,   // SSL object did not process all written cleartext
      SSL_CA_MD_TOO_WEAK,  // CA message digest is too weak
      SSL_CA_KEY_TOO_SMALL, // CA key is too small
      SSL_DH_KEY_TOO_SMALL, // DH key is too small
      ENCAPSULATION_ERROR, // exceptions thrown during packet encapsulation
      EPKI_CERT_ERROR,     // error obtaining certificate from External PKI provider
      EPKI_SIGN_ERROR,     // error obtaining RSA signature from External PKI provider
      HANDSHAKE_TIMEOUT,   // handshake failed to complete within given time frame
      KEEPALIVE_TIMEOUT,   // lost contact with peer
      INACTIVE_TIMEOUT,    // disconnected due to inactive timer
      CONNECTION_TIMEOUT,  // connection failed to establish within given time
      PRIMARY_EXPIRE,      // primary key context expired
      TLS_VERSION_MIN,     // peer cannot handshake at our minimum required TLS version
      TLS_AUTH_FAIL,       // tls-auth HMAC verification failed
      TLS_CRYPT_META_FAIL, // tls-crypt-v2 metadata verification failed
      CERT_VERIFY_FAIL,    // peer certificate verification failure
      PEM_PASSWORD_FAIL,   // incorrect or missing PEM private key decryption password
      AUTH_FAILED,         // general authentication failure
      CLIENT_HALT,         // HALT message from server received
      CLIENT_RESTART,      // RESTART message from server received
      TUN_HALT,            // halt command from tun interface
      RELAY,               // RELAY message from server received
      RELAY_ERROR,         // RELAY error
      N_PAUSE,             // Number of transitions to Pause state
      N_RECONNECT,         // Number of reconnections
      N_KEY_LIMIT_RENEG,   // Number of renegotiations triggered by per-key limits such as data or packet limits
      KEY_STATE_ERROR,     // Received packet didn't match expected key state
      PROXY_ERROR,         // HTTP proxy error
      PROXY_NEED_CREDS,    // HTTP proxy needs credentials

      // key event errors
      KEV_NEGOTIATE_ERROR,
      KEV_PENDING_ERROR,
      N_KEV_EXPIRE,
      KEY_EXPANSION_ERROR,

      // Packet ID error detail
      PKTID_INVALID,
      PKTID_BACKTRACK,
      PKTID_EXPIRE,
      PKTID_REPLAY,
      PKTID_TIME_BACKTRACK,

      N_ERRORS,

      // undefined error
      UNDEF=SUCCESS,
    };

    inline const char *name(const size_t type)
    {
      static const char *names[] = {
	"SUCCESS",
	"NETWORK_RECV_ERROR",
	"NETWORK_EOF_ERROR",
	"NETWORK_SEND_ERROR",
	"NETWORK_UNAVAILABLE",
	"DECRYPT_ERROR",
	"HMAC_ERROR",
	"REPLAY_ERROR",
	"BUFFER_ERROR",
	"CC_ERROR",
	"BAD_SRC_ADDR",
	"COMPRESS_ERROR",
	"RESOLVE_ERROR",
	"SOCKET_PROTECT_ERROR",
	"TUN_READ_ERROR",
	"TUN_WRITE_ERROR",
	"TUN_FRAMING_ERROR",
	"TUN_SETUP_FAILED",
	"TUN_IFACE_CREATE",
	"TUN_IFACE_DISABLED",
	"TUN_ERROR",
	"TUN_REGISTER_RINGS_ERROR",
	"TAP_NOT_SUPPORTED",
	"REROUTE_GW_NO_DNS",
	"TRANSPORT_ERROR",
	"TCP_OVERFLOW",
	"TCP_SIZE_ERROR",
	"TCP_CONNECT_ERROR",
	"UDP_CONNECT_ERROR",
	"SSL_ERROR",
	"SSL_PARTIAL_WRITE",
	"SSL_CA_MD_TOO_WEAK",
	"SSL_CA_KEY_TOO_SMALL",
	"SSL_DH_KEY_TOO_SMALL",
	"ENCAPSULATION_ERROR",
	"EPKI_CERT_ERROR",
	"EPKI_SIGN_ERROR",
	"HANDSHAKE_TIMEOUT",
	"KEEPALIVE_TIMEOUT",
	"INACTIVE_TIMEOUT",
	"CONNECTION_TIMEOUT",
	"PRIMARY_EXPIRE",
	"TLS_VERSION_MIN",
	"TLS_AUTH_FAIL",
	"TLS_CRYPT_META_FAIL",
	"CERT_VERIFY_FAIL",
	"PEM_PASSWORD_FAIL",
	"AUTH_FAILED",
	"CLIENT_HALT",
	"CLIENT_RESTART",
	"TUN_HALT",
	"RELAY",
	"RELAY_ERROR",
	"N_PAUSE",
	"N_RECONNECT",
	"N_KEY_LIMIT_RENEG",
	"KEY_STATE_ERROR",
	"PROXY_ERROR",
	"PROXY_NEED_CREDS",
	"KEV_NEGOTIATE_ERROR",
	"KEV_PENDING_ERROR",
	"N_KEV_EXPIRE",
	"KEV_EXPANSION_ERROR",
	"PKTID_INVALID",
	"PKTID_BACKTRACK",
	"PKTID_EXPIRE",
	"PKTID_REPLAY",
	"PKTID_TIME_BACKTRACK",
      };

      static_assert(N_ERRORS == array_size(names), "error names array inconsistency");
      if (type < N_ERRORS)
	return names[type];
      else
	return "UNKNOWN_ERROR_TYPE";
    }
  }
} // namespace openvpn

#endif // OPENVPN_ERROR_ERROR_H
