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

#include <fcntl.h> // Definition of AT_* constants */
#include <sys/stat.h>
#include <cstdint> // for std::uint64_t
#include <cerrno>

#include <string>

#include <openvpn/common/platform.hpp>

namespace openvpn {

#if defined(OPENVPN_PLATFORM_LINUX)

inline int update_file_mod_time_nanoseconds(const std::string &filename,
                                            const std::uint64_t nanoseconds_since_epooch)
{
    struct timespec times[2];
    times[0].tv_sec = nanoseconds_since_epooch / std::uint64_t(1000000000);
    times[0].tv_nsec = nanoseconds_since_epooch % std::uint64_t(1000000000);
    times[1] = times[0];
    if (::utimensat(AT_FDCWD, filename.c_str(), times, 0) == -1)
        return errno;
    return 0;
}

inline int update_file_mod_time_nanoseconds(const int fd,
                                            const std::uint64_t nanoseconds_since_epooch)
{
    struct timespec times[2];
    times[0].tv_sec = nanoseconds_since_epooch / std::uint64_t(1000000000);
    times[0].tv_nsec = nanoseconds_since_epooch % std::uint64_t(1000000000);
    times[1] = times[0];
    if (::futimens(fd, times) == -1)
        return errno;
    return 0;
}

#else

inline int update_file_mod_time_nanoseconds(const std::string &filename,
                                            const std::uint64_t nanoseconds_since_epooch)
{
    return 0;
}

inline int update_file_mod_time_nanoseconds(const int fd,
                                            const std::uint64_t nanoseconds_since_epooch)
{
    return 0;
}

#endif

} // namespace openvpn
