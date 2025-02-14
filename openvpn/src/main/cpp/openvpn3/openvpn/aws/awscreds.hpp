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

// AWS credentials

#pragma once

#include <string>

namespace openvpn::AWS {
struct Creds
{
    Creds()
    {
    }

    Creds(std::string access_key_arg,
          std::string secret_key_arg,
          std::string token_arg = "")
        : access_key(std::move(access_key_arg)),
          secret_key(std::move(secret_key_arg)),
          token(std::move(token_arg))
    {
    }

    // can be used to load from HTTP creds
    template <typename CREDS>
    Creds(const CREDS &creds)
        : access_key(creds.username),
          secret_key(creds.password)
    {
    }

    bool defined() const
    {
        return !access_key.empty() && !secret_key.empty();
    }

    std::string to_string() const
    {
        return "AWS::Creds[access_key=" + access_key + " len(secret_key)=" + std::to_string(secret_key.length()) + ']';
    }

    std::string access_key;
    std::string secret_key;
    std::string token;
};
} // namespace openvpn::AWS
