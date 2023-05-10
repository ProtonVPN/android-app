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
