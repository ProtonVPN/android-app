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

#ifndef OPENVPN_AUTH_VALIDATE_CREDS_H
#define OPENVPN_AUTH_VALIDATE_CREDS_H

#include <openvpn/common/unicode.hpp>

// Validate authentication credential.
// Must be UTF-8.
// Other checks on size and content below.
// We don't check that the credential is non-empty.
namespace openvpn::ValidateCreds {

enum Type
{
    USERNAME,
    PASSWORD,
    RESPONSE
};

template <typename STRING>
static bool is_valid(const Type type, const STRING &cred, const bool strict)
{
    size_t max_len_flags;
    if (strict)
    {
        // length <= 512 unicode chars, no control chars allowed
        max_len_flags = 512 | Unicode::UTF8_NO_CTRL;
    }
    else
    {
        switch (type)
        {
        case USERNAME:
            // length <= 512 unicode chars, no control chars allowed
            max_len_flags = 512 | Unicode::UTF8_NO_CTRL;
            break;
        case PASSWORD:
        case RESPONSE:
            // length <= 16384 unicode chars
            max_len_flags = 16384;
            break;
        default:
            return false;
        }
    }
    return Unicode::is_valid_utf8(cred, max_len_flags);
}
} // namespace openvpn::ValidateCreds

#endif
