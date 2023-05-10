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

// Parse the remote-cert-tls, remote-cert-ku, and remote-cert-eku options.

#ifndef OPENVPN_SSL_KUPARSE_H
#define OPENVPN_SSL_KUPARSE_H

#include <vector>
#include <string>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/hexstr.hpp>
#include <openvpn/common/options.hpp>

namespace openvpn {
namespace KUParse {
enum TLSWebType
{
    TLS_WEB_NONE,
    TLS_WEB_SERVER,
    TLS_WEB_CLIENT,
};

inline void remote_cert_tls(const TLSWebType wt, std::vector<unsigned int> &ku, std::string &eku)
{
    ku.clear();
    eku = "";

    switch (wt)
    {
    case TLS_WEB_NONE:
        break;
    case TLS_WEB_SERVER:
        ku.clear();
        ku.push_back(0xa0);
        ku.push_back(0x88);
        eku = "TLS Web Server Authentication";
        break;
    case TLS_WEB_CLIENT:
        ku.clear();
        ku.push_back(0x80);
        ku.push_back(0x08);
        ku.push_back(0x88);
        eku = "TLS Web Client Authentication";
        break;
    }
}

inline TLSWebType remote_cert_type(const std::string &ct)
{
    if (ct == "server")
        return TLS_WEB_SERVER;
    else if (ct == "client")
        return TLS_WEB_CLIENT;
    else
        throw option_error("remote-cert-tls must be 'client' or 'server'");
}

inline void remote_cert_tls(const std::string &ct,
                            std::vector<unsigned int> &ku,
                            std::string &eku)
{
    remote_cert_tls(remote_cert_type(ct), ku, eku);
}

inline void remote_cert_tls(const OptionList &opt,
                            const std::string &relay_prefix,
                            std::vector<unsigned int> &ku,
                            std::string &eku)
{
    TLSWebType wt = TLS_WEB_NONE;
    const Option *o = opt.get_ptr(relay_prefix + "remote-cert-tls");
    if (o)
    {
        const std::string ct = o->get_optional(1, 16);
        wt = remote_cert_type(ct);
    }
    remote_cert_tls(wt, ku, eku);
}

inline void remote_cert_ku(const OptionList &opt,
                           const std::string &relay_prefix,
                           std::vector<unsigned int> &ku)
{
    ku.clear();

    const Option *o = opt.get_ptr(relay_prefix + "remote-cert-ku");
    if (o)
    {
        if (o->empty())
            throw option_error("remote-cert-ku: no hex values specified");
        else if (o->size() >= 64)
            throw option_error("remote-cert-ku: too many parameters");
        else
        {
            try
            {
                for (size_t i = 1; i < o->size(); ++i)
                    ku.push_back(parse_hex_number<unsigned int>(o->get(i, 16)));
            }
            catch (parse_hex_error &)
            {
                throw option_error("remote-cert-ku: error parsing hex value list");
            }
        }
    }
}

inline void remote_cert_eku(const OptionList &opt,
                            const std::string &relay_prefix,
                            std::string &eku)
{
    eku = "";

    const Option *o = opt.get_ptr(relay_prefix + "remote-cert-eku");
    if (o)
        eku = o->get(1, 256);
}
} // namespace KUParse
} // namespace openvpn

#endif
