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
