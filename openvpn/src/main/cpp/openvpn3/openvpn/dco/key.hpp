//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012- OpenVPN Inc.
//
//    SPDX-License-Identifier: MPL-2.0 OR AGPL-3.0-only WITH openvpn3-openssl-exception
//    Copyright (C) 2020-2022 Lev Stipakov <lev@openvpn.net>
//


#pragma once

namespace openvpn::KoRekey {

struct KeyDirection
{
    const unsigned char *cipher_key;
    unsigned char nonce_tail[8]; // only AEAD
    unsigned int cipher_key_size;
};

struct KeyConfig
{
    KeyDirection encrypt;
    KeyDirection decrypt;

    int key_id;
    int remote_peer_id;
    unsigned int cipher_alg;
};

} // namespace openvpn::KoRekey