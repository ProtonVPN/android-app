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

#ifndef OPENVPN_COMMON_PEERCRED_H
#define OPENVPN_COMMON_PEERCRED_H

#include <unistd.h>
#include <sys/types.h>
#include <sys/socket.h>

#include <openvpn/common/platform.hpp>
#include <openvpn/common/exception.hpp>

#ifdef OPENVPN_PLATFORM_TYPE_APPLE
#include <sys/ucred.h>
#endif

namespace openvpn {
  namespace SockOpt {

    struct Creds
    {
      Creds(const int uid_arg=-1, const int gid_arg=-1, const int pid_arg=-1)
	: uid(uid_arg),
	  gid(gid_arg),
	  pid(pid_arg)
      {
      }

      bool root_or_self_uid() const
      {
	return !uid || uid == ::getuid();
      }

      bool root_uid() const
      {
	return !uid;
      }

      bool match_uid(const int other_uid) const
      {
	return uid >= 0 && uid == other_uid;
      }

      int uid;
      int gid;
      int pid;
    };

    // get credentials of process on other side of unix socket
    inline bool peercreds(const int fd, Creds& cr)
    {
#if defined(OPENVPN_PLATFORM_TYPE_APPLE)
      xucred cred;
      socklen_t credLen = sizeof(cred);
      if (::getsockopt(fd, SOL_LOCAL, LOCAL_PEERCRED, &cred, &credLen) != 0)
	return false;
      cr = Creds(cred.cr_uid, cred.cr_gid);
      return true;
#elif defined(OPENVPN_PLATFORM_LINUX)
      struct ucred uc;
      socklen_t uc_len = sizeof(uc);
      if (::getsockopt(fd, SOL_SOCKET, SO_PEERCRED, &uc, &uc_len) != 0)
	return false;
      cr = Creds(uc.uid, uc.gid, uc.pid);
      return true;
#else
#error no implementation for peercreds()
#endif
    }

  }
}

#endif
