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

#ifndef OPENVPN_PKI_EPKIBASE_H
#define OPENVPN_PKI_EPKIBASE_H

#include <string>

namespace openvpn {

// Abstract base class used to provide an interface where core SSL implementation
// can use an external private key.
class ExternalPKIBase
{
  public:
    // Sign data (base64) and return signature as sig (base64).
    // Return true on success or false on error.
    virtual bool sign(const std::string &alias, const std::string &data, std::string &sig, const std::string &algorithm, const std::string &hashalg, const std::string &saltlen) = 0;

    virtual ~ExternalPKIBase() = default;
};

class ExternalPKIImpl
{
  public:
    virtual ~ExternalPKIImpl() = default;
};
}; // namespace openvpn

#endif
