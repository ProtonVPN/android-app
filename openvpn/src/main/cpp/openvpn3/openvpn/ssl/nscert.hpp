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

// Parse the ns-cert-type option.

#ifndef OPENVPN_SSL_NSCERT_H
#define OPENVPN_SSL_NSCERT_H

#include <string>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/options.hpp>

namespace openvpn::NSCert {
enum Type
{
    NONE,
    CLIENT,
    SERVER
};

inline Type ns_cert_type(const std::string &ct)
{
    if (ct == "server")
        return SERVER;
    else if (ct == "client")
        return CLIENT;
    else
        throw option_error(ERR_INVALID_OPTION_CRYPTO, "ns-cert-type must be 'client' or 'server'");
}

inline Type ns_cert_type(const OptionList &opt, const std::string &relay_prefix)
{
    const Option *o = opt.get_ptr(relay_prefix + "ns-cert-type");
    if (o)
    {
        const std::string ct = o->get_optional(1, 16);
        return ns_cert_type(ct);
    }
    return NONE;
}
} // namespace openvpn::NSCert

#endif
