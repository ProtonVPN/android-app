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

#ifndef OPENVPN_TUN_IPv6_SETTING_H
#define OPENVPN_TUN_IPv6_SETTING_H

#include <openvpn/common/exception.hpp>

namespace openvpn {
class TriStateSetting
{
  public:
    enum Type
    {
        No,
        Yes,
        Default,
    };

    TriStateSetting()
    {
    }

    explicit TriStateSetting(const Type t)
        : type_(t)
    {
    }

    Type operator()() const
    {
        return type_;
    }

    std::string to_string() const
    {
        switch (type_)
        {
        case No:
            return "no";
        case Yes:
            return "yes";
        case Default:
        default:
            return "default";
        }
    }

    static TriStateSetting parse(const std::string &str)
    {
        if (str == "no")
            return TriStateSetting(No);
        else if (str == "yes")
            return TriStateSetting(Yes);
        else if (str == "default")
            return TriStateSetting(Default);
        else
            throw Exception("IPv6Setting: unrecognized setting: '" + str + '\'');
    }

    bool operator==(const TriStateSetting &other) const
    {
        return type_ == other.type_;
    }

    bool operator!=(const TriStateSetting &other) const
    {
        return type_ != other.type_;
    }

  private:
    Type type_ = Default;
};
} // namespace openvpn

#endif
