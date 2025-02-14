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

#pragma once

#include <string>
#include <memory>

namespace openvpn {

/**
 * Abstract base class used to provide early rejection
 * of specific Common Names during SSL/TLS handshake.
 */
class CommonNameReject
{
  public:
    typedef std::unique_ptr<CommonNameReject> UPtr;

    /**
     * Should a leaf certificate having Common Name cn
     * be rejected during SSL/TLS handshake?
     *
     * @param cn Common Name
     * @return true if certificate should be rejected.
     */
    virtual bool reject(const std::string &cn) = 0;

    virtual ~CommonNameReject() = default;
};

} // namespace openvpn
