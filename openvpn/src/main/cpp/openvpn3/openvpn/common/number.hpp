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

// General purpose methods for dealing with numbers.

#ifndef OPENVPN_COMMON_NUMBER_H
#define OPENVPN_COMMON_NUMBER_H

#include <string>
#include <limits>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>

namespace openvpn {

OPENVPN_EXCEPTION(number_parse_exception);

// Parse the number of type T in str, returning
// value in retval.  Returns true on success.
// Note -- currently doesn't detect overflow.
// If nondigit_term is true, any non-digit char
// can terminate the numerical value.  Otherwise,
// only '\0' can terminate the value.
template <typename T>
inline bool parse_number(const char *str,
                         T &retval,
                         const bool nondigit_term = false)
{
    if (!str[0])
        return false; // empty string
    bool neg = false;
    size_t i = 0;
    if (std::numeric_limits<T>::min() < 0 && str[0] == '-')
    {
        neg = true;
        i = 1;
    }
    T ret = T(0);
    while (true)
    {
        const char c = str[i++];
        if (c >= '0' && c <= '9')
        {
            ret *= T(10);
            ret += T(c - '0');
        }
        else if (!c || nondigit_term)
        {
            retval = neg ? -ret : ret;
            return true;
        }
        else
            return false; // non-digit
    }
}

// like parse_number above, but accepts std::string
template <typename T>
inline bool parse_number(const std::string &str, T &retval)
{
    return parse_number<T>(str.c_str(), retval);
}

template <typename T>
inline T parse_number_throw(const std::string &str, const std::string &error)
{
    T ret;
    if (parse_number<T>(str.c_str(), ret))
        return ret;
    else
        throw number_parse_exception(error);
}

template <typename T>
inline T parse_number_throw(const std::string &str, const char *error)
{
    T ret;
    if (parse_number<T>(str.c_str(), ret))
        return ret;
    else
        throw number_parse_exception(std::string(error));
}

template <typename T>
inline T parse_number_throw(const char *str, const char *error)
{
    T ret;
    if (parse_number<T>(str, ret))
        return ret;
    else
        throw number_parse_exception(std::string(error));
}

template <typename T>
inline bool parse_number_validate(const std::string &numstr,
                                  const size_t max_len,
                                  const T minimum,
                                  const T maximum,
                                  T *value_return = nullptr)
{
    if (numstr.length() <= max_len)
    {
        T value;
        if (parse_number<T>(numstr.c_str(), value))
        {
            if (value >= minimum && value <= maximum)
            {
                if (value_return)
                    *value_return = value;
                return true;
            }
        }
    }
    return false;
}

inline bool is_number(const char *str)
{
    char c;
    bool found_digit = false;
    while ((c = *str++))
    {
        if (c >= '0' && c <= '9')
            found_digit = true;
        else
            return false;
    }
    return found_digit;
}

} // namespace openvpn

#endif // OPENVPN_COMMON_NUMBER_H
