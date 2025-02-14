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

#ifndef OPENVPN_COMMON_UMASK_H
#define OPENVPN_COMMON_UMASK_H

#include <sys/types.h>
#include <sys/stat.h>

namespace openvpn {
// Note: not thread safe, since umask() is
// documented to modify the process-wide file
// mode creation mask.
class UMask
{
  public:
    UMask(mode_t new_umask)
    {
        umask_save = ::umask(new_umask);
    }

    ~UMask()
    {
        ::umask(umask_save);
    }

  private:
    UMask(const UMask &) = delete;
    UMask &operator=(const UMask &) = delete;

    mode_t umask_save;
};

struct UMaskPrivate : public UMask
{
    UMaskPrivate()
        : UMask(077)
    {
    }
};

struct UMaskDaemon : public UMask
{
    UMaskDaemon()
        : UMask(S_IWOTH)
    {
    }
};
} // namespace openvpn
#endif
