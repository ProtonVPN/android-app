//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2017 OpenVPN Inc.
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

#ifndef OPENVPN_COMMON_CLEANUP_H
#define OPENVPN_COMMON_CLEANUP_H

#include <utility>

namespace openvpn {

  template <typename F>
  class CleanupType
  {
  public:
    CleanupType(F method) noexcept
      : clean(std::move(method))
    {
    }

    CleanupType(CleanupType&&) = default;

    ~CleanupType()
    {
      clean();
    }

  private:
    CleanupType(const CleanupType&) = delete;
    CleanupType& operator=(const CleanupType&) = delete;

    F clean;
  };

  template <typename F>
  inline CleanupType<F> Cleanup(F method) noexcept
  {
    return CleanupType<F>(std::move(method));
  }

}

#endif
