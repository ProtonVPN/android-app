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

#ifndef OPENVPN_COMMON_STAT_H
#define OPENVPN_COMMON_STAT_H

#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <cstdint>         // for std::uint64_t

namespace openvpn {

  // Return true if file exists
  inline bool file_exists(const std::string& filename)
  {
    if (filename.empty())
      return false;
    struct stat buffer;   
    return stat(filename.c_str(), &buffer) == 0; 
  }

  // Return file modification time (in seconds since unix epoch) or 0 on error
  inline time_t file_mod_time(const std::string& filename)
  {
    struct stat buffer;
    if (stat(filename.c_str(), &buffer) != 0)
      return 0;
    else
      return buffer.st_mtime;
  }

  // Return file modification time (in nanoseconds since unix epoch) or 0 on error
  inline std::uint64_t file_mod_time_nanoseconds(const std::string& filename)
  {
    typedef std::uint64_t T;
    struct stat s;
    if (::stat(filename.c_str(), &s) == 0)
      return T(s.st_mtim.tv_sec) * T(1000000000) + T(s.st_mtim.tv_nsec);
    else
      return 0;
  }

  // Return file modification time (in milliseconds since unix epoch) or 0 on error
  inline std::uint64_t file_mod_time_milliseconds(const std::string& filename)
  {
    return file_mod_time_nanoseconds(filename) / std::uint64_t(1000000);
  }

}

#endif
