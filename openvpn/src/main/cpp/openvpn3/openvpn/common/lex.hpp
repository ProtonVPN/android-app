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

// A set of lexical analyzer classes.  These classes can be combined
// with the methods in split.hpp to create parsers.

#pragma once

#include <openvpn/common/string.hpp>

namespace openvpn {

// This class (or others that define an is_space method) is used as a
// template parameter to methods in split.hpp.
struct SpaceMatch
{
    static bool is_space(char c)
    {
        return string::is_space(c);
    }
};

// A basic lexical analyzer that understands quoting
class StandardLex
{
  public:
    void put(char c)
    {
        in_backslash_ = false;
        if (backslash_)
        {
            ch = c;
            backslash_ = false;
            in_backslash_ = true;
        }
        else if (c == '\\')
        {
            backslash_ = true;
            ch = -1;
        }
        else if (c == '\"')
        {
            in_quote_ = !in_quote_;
            ch = -1;
        }
        else
            ch = c;
    }

    bool available() const
    {
        return ch != -1;
    }
    int get() const
    {
        return ch;
    }
    void reset()
    {
        ch = -1;
    }

    bool in_quote() const
    {
        return in_quote_;
    }
    bool in_backslash() const
    {
        return in_backslash_;
    }

  private:
    bool in_quote_ = false;
    bool backslash_ = false;
    bool in_backslash_ = false;
    int ch = -1;
};

// A null lexical analyzer has no special understanding
// of any particular string character.
class NullLex
{
  public:
    void put(char c)
    {
        ch = c;
    }
    bool available() const
    {
        return ch != -1;
    }
    int get() const
    {
        return ch;
    }
    void reset()
    {
        ch = -1;
    }
    bool in_quote() const
    {
        return false;
    }
    bool in_backslash() const
    {
        return false;
    }

  private:
    int ch = -1;
};

} // namespace openvpn
