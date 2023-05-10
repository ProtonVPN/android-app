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

// Parse the ns-cert-type option.

#ifndef OPENVPN_SSL_NSCERT_H
#define OPENVPN_SSL_NSCERT_H

#include <string>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/options.hpp>

namespace openvpn {
namespace NSCert {
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
        throw option_error("ns-cert-type must be 'client' or 'server'");
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
} // namespace NSCert
} // namespace openvpn

#endif
