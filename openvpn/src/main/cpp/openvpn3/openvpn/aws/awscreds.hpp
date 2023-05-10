//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2022 OpenVPN Inc.
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

// AWS credentials

#pragma once

#include <string>

namespace openvpn {
namespace AWS {
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
} // namespace AWS
} // namespace openvpn
