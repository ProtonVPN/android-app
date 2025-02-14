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

// General purpose class for scope accounting.

#pragma once

namespace openvpn {

class UseCount
{
  public:
    /**
     * Temporarily increments the variable by one for the scope an instance
     * of this class is defined.
     * @param count
     */
    explicit UseCount(int &count)
        : count_(count)
    {
        ++count_;
    }

    /* make this class not copyable. */
    UseCount(const UseCount &) = delete;
    UseCount &operator=(UseCount &) = delete;

    ~UseCount()
    {
        --count_;
    }

  private:
    int &count_;
};

} // namespace openvpn