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

// AWS API CA

#pragma once

#include <openvpn/common/fileunix.hpp>
#include <openvpn/common/stat.hpp>

namespace openvpn {
namespace AWS {
inline std::string api_ca()
{
    // paths are copied from https://golang.org/src/crypto/x509/root_linux.go
    std::list<std::string> certs = {
        "/etc/ssl/certs/ca-certificates.crt",               // debian/ubuntu
        "/etc/pki/tls/certs/ca-bundle.crt",                 // fedora/rhel6
        "/etc/ssl/ca-bundle.pem",                           // opensuse,
        "/etc/pki/tls/cacert.pem"                           // openelec
        "/etc/pki/ca-trust/extracted/pem/tls-ca-bundle.pem" // centos/rhel7
        "/etc/ssl/cert.pem"                                 // alpine
    };
    for (const auto &cert : certs)
    {
        if (file_exists(cert))
            return read_text_unix(cert);
    }
    throw file_unix_error("No CA certificate files found in system paths");
}
} // namespace AWS
} // namespace openvpn
