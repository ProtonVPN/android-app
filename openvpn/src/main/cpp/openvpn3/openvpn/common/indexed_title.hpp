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

#include <openvpn/common/size.hpp>

namespace openvpn {

// TITLE class for representing an object name and index.
// Useful for referring to array indices when generating errors.
class IndexedTitle
{
  public:
    IndexedTitle(const char *title, const size_t index)
        : title_(title),
          index_(index)
    {
    }

    std::string to_string() const
    {
        return std::string(title_) + '.' + std::to_string(index_);
    }

    bool empty() const
    {
        return false;
    }

  private:
    const char *title_;
    size_t index_;
};

} // namespace openvpn
