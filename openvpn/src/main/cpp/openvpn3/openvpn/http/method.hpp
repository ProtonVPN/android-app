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

#ifndef OPENVPN_HTTP_METHOD_H
#define OPENVPN_HTTP_METHOD_H

namespace openvpn::HTTP::Method {
enum Type
{
    OTHER,
    GET,
    POST,
};

Type parse(const std::string &methstr)
{
    if (methstr == "GET")
        return GET;
    else if (methstr == "POST")
        return POST;
    else
        return OTHER;
}
} // namespace openvpn::HTTP::Method

#endif
