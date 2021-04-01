//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
//    Copyright (C) 2020-2020 Lev Stipakov <lev@openvpn.net>
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

#pragma once

namespace openvpn {
namespace KoRekey {

struct KeyDirection {
  const unsigned char *cipher_key;
  const unsigned char *hmac_key; // only CBC
  unsigned char nonce_tail[12];  // only GCM
  unsigned int cipher_key_size;
  unsigned int hmac_key_size; // only CBC
};

struct KeyConfig {
  KeyDirection encrypt;
  KeyDirection decrypt;

  int key_id;
  int remote_peer_id;
  unsigned int cipher_alg;
  unsigned int hmac_alg; // only CBC
};

} // namespace KoRekey
} // namespace openvpn