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
    virtual bool sign(const std::string &data, std::string &sig, const std::string &algorithm, const std::string &hashalg, const std::string &saltlen) = 0;

    virtual ~ExternalPKIBase()
    {
    }
};

class ExternalPKIImpl
{
  public:
    virtual ~ExternalPKIImpl() = default;
};
}; // namespace openvpn

#endif
