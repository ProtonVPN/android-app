//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
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

#ifndef OPENVPN_COMMON_GLOB_H
#define OPENVPN_COMMON_GLOB_H

#include <glob.h>

#include <cstring>
#include <string>

namespace openvpn {
  class Glob
  {
  public:
    Glob(const std::string& pattern, const int flags)
    {
      reset();
      status_ = ::glob(pattern.c_str(), flags, nullptr, &glob_);
    }

    int status() const
    {
      return status_;
    }

    size_t size() const
    {
      return glob_.gl_pathc;
    }

    const char* operator[](const size_t i) const
    {
      return glob_.gl_pathv[i];
    }

    ~Glob()
    {
      ::globfree(&glob_);
    }

  private:
    void reset()
    {
      std::memset(&glob_, 0, sizeof(glob_));
      status_ = 0;
    }

    Glob(const Glob&) = delete;
    Glob& operator=(const Glob&) = delete;

    ::glob_t glob_;
    int status_;
  };
}

#endif
