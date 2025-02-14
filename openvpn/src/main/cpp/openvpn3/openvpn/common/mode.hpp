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

#ifndef OPENVPN_COMMON_MODE_H
#define OPENVPN_COMMON_MODE_H

// A client/server mode class.

namespace openvpn {
class Mode
{
  public:
    enum Type
    {
        CLIENT,
        SERVER,
    };

    Mode()
        : type_(CLIENT)
    {
    }
    explicit Mode(const Type t)
        : type_(t)
    {
    }

    bool is_server() const
    {
        return type_ == SERVER;
    }
    bool is_client() const
    {
        return type_ == CLIENT;
    }

    bool operator==(const Mode &other)
    {
        return type_ == other.type_;
    }

    bool operator!=(const Mode &other)
    {
        return type_ != other.type_;
    }

    const char *str() const
    {
        switch (type_)
        {
        case CLIENT:
            return "CLIENT";
        case SERVER:
            return "SERVER";
        default:
            return "UNDEF_MODE";
        }
    }

  private:
    Type type_;
};
} // namespace openvpn

#endif // OPENVPN_COMMON_MODE_H
