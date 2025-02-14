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

#ifndef OPENVPN_COMMON_PEERCRED_H
#define OPENVPN_COMMON_PEERCRED_H

#include <unistd.h>
#include <sys/types.h>
#include <sys/socket.h>

#include <openvpn/common/platform.hpp>
#include <openvpn/common/exception.hpp>

#if defined(__APPLE__) || defined(__FreeBSD__)
#include <sys/ucred.h>
#endif

namespace openvpn::SockOpt {

struct Creds
{
    Creds(const int uid_arg = -1, const int gid_arg = -1, const int pid_arg = -1)
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

    bool match_uid(const uid_t other_uid) const
    {
        return uid >= 0 && uid == other_uid;
    }

    uid_t uid;
    uid_t gid;
    pid_t pid;
};

// get credentials of process on other side of unix socket
inline bool peercreds(const int fd, Creds &cr)
{
#if defined(__APPLE__) || defined(__FreeBSD__)
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

} // namespace openvpn::SockOpt

#endif
