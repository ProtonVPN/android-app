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

// AWS API CA

#pragma once

#include <filesystem>
#include <openvpn/common/fileunix.hpp>
#include <openvpn/common/stat.hpp>

namespace openvpn::AWS {
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
        std::error_code ec;
        if (std::filesystem::exists(cert, ec))
            return read_text_unix(cert);
    }
    throw file_unix_error("No CA certificate files found in system paths");
}
} // namespace openvpn::AWS
