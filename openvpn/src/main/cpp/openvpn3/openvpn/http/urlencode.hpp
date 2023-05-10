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

#ifndef OPENVPN_HTTP_URLENCODE_H
#define OPENVPN_HTTP_URLENCODE_H

#include <string>
#include <vector>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/split.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/hexstr.hpp>
#include <openvpn/common/unicode.hpp>
#include <openvpn/http/parseutil.hpp>

namespace openvpn {
namespace URL {
OPENVPN_EXCEPTION(url_error);

inline std::string encode(const std::string &str)
{
    std::string ret;
    ret.reserve(str.length() * 2); // just a guess
    for (auto &c : str)
    {
        if (HTTP::Util::is_escaped(c))
        {
            ret += '%';
            ret += render_hex_number((unsigned char)c, true);
        }
        else
            ret += c;
    }
    return ret;
}

inline std::string decode(const std::string &encoded)
{
    enum State
    {
        TEXT,
        PERCENT,
        DIGIT
    };

    int value = 0;
    State state = TEXT;
    std::string ret;
    ret.reserve(encoded.size()); // just a guess

    for (auto &c : encoded)
    {
        switch (state)
        {
        case TEXT:
            {
                if (c == '%')
                    state = PERCENT;
                else
                    ret += c;
                break;
            }
        case PERCENT:
            {
                const int v = parse_hex_char(c);
                if (v < 0)
                    throw url_error(std::string("decode error after %: ") + encoded);
                value = v;
                state = DIGIT;
                break;
            }
        case DIGIT:
            {
                const int v = parse_hex_char(c);
                if (v < 0)
                    throw url_error(std::string("decode error after %: ") + encoded);
                ret += static_cast<unsigned char>((value * 16) + v);
                state = TEXT;
            }
        }
    }
    if (state != TEXT)
        throw url_error(std::string("decode error: %-encoding item not closed out: ") + encoded);
    if (!Unicode::is_valid_utf8(ret))
        throw url_error(std::string("not UTF-8: ") + encoded);
    return ret;
}

inline std::vector<std::string> decode_path(std::string path)
{
    std::vector<std::string> list;
    if (!path.empty() && path[0] == '/')
        path = path.substr(1);
    Split::by_char_void<decltype(list), NullLex, Split::NullLimit>(list, path, '/');
    for (auto &i : list)
        i = decode(i);
    return list;
}
} // namespace URL
} // namespace openvpn

#endif
