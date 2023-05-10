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
