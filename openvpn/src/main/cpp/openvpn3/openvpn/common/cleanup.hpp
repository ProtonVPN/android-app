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

    CleanupType(CleanupType &&) = default;

    ~CleanupType()
    {
        clean();
    }

  private:
    CleanupType(const CleanupType &) = delete;
    CleanupType &operator=(const CleanupType &) = delete;

    F clean;
};

template <typename F>
inline CleanupType<F> Cleanup(F method) noexcept
{
    return CleanupType<F>(std::move(method));
}

} // namespace openvpn

#endif
