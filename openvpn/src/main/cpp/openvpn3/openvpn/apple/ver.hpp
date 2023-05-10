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

#ifndef OPENVPN_APPLE_VER_H
#define OPENVPN_APPLE_VER_H

#include <errno.h>
#include <sys/sysctl.h>

#include <string>
#include <sstream>
#include <vector>

#include <openvpn/common/split.hpp>
#include <openvpn/common/number.hpp>

namespace openvpn {
class AppleVersion
{
  public:
    int major() const
    {
        return ver[0];
    }
    int minor() const
    {
        return ver[1];
    }
    int build() const
    {
        return ver[2];
    }

    std::string to_string() const
    {
        std::ostringstream os;
        os << major() << '.' << minor() << '.' << build();
        return os.str();
    }

  protected:
    AppleVersion()
    {
        reset();
    }

    // verstr should be in the form major.minor.build
    void init(const std::string &verstr)
    {
        typedef std::vector<std::string> StringList;
        reset();
        StringList sl;
        sl.reserve(3);
        Split::by_char_void<StringList, NullLex, Split::NullLimit>(sl, verstr, '.');
        for (size_t i = 0; i < 3; ++i)
        {
            if (i < sl.size())
                parse_number(sl[i], ver[i]);
        }
    }

  private:
    void reset()
    {
        ver[0] = ver[1] = ver[2] = -1;
    }

    int ver[3];
};
} // namespace openvpn

#endif
