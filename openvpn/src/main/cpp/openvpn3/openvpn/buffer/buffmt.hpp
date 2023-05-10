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

#include <utility>
#include <type_traits>

#include <openvpn/common/exception.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/common/hexstr.hpp>

namespace openvpn {
namespace BufferFormat {

template <typename T>
class UnsignedDecimal
{
  public:
    static void write(Buffer &buf, T value)
    {
        UnsignedDecimal dec(std::move(value));
        char ret[max_length()];
        for (size_t i = sizeof(ret); i-- > 0;)
        {
            ret[i] = dec.next();
            if (dec.is_zero())
            {
                buf.write(ret + i, sizeof(ret) - i);
                return;
            }
        }
        throw Exception("BufferFormat::UnsignedDecimal::write: overflow");
    }

    static constexpr size_t max_length()
    {
        return sizeof(T) * 3;
    }

  private:
    UnsignedDecimal(T &&value)
        : value_(std::move(value))
    {
        static_assert(std::is_unsigned<T>::value, "UnsignedDecimal: unsigned type required");
    }

    char next()
    {
        T d = value_ / T(10);
        T r = value_ % T(10);
        value_ = d;
        return static_cast<char>('0' + r);
    }

    bool is_zero() const
    {
        return !value_;
    }

    T value_;
};

template <typename T>
class Hex
{
  public:
    static void write(Buffer &buf, T value)
    {
        Hex hex(std::move(value));
        char ret[max_length()];
        for (size_t i = sizeof(ret); i-- > 0;)
        {
            ret[i] = hex.next();
            if (hex.is_zero())
            {
                buf.write(ret + i, sizeof(ret) - i);
                return;
            }
        }
        throw Exception("BufferFormat::Hex::write: overflow");
    }

    static constexpr size_t max_length()
    {
        return sizeof(T) * 2;
    }

  private:
    Hex(T &&value)
        : value_(std::move(value))
    {
        static_assert(std::is_unsigned<T>::value, "BufferFormat::Hex: unsigned type required");
    }

    char next()
    {
        T d = value_ / T(16);
        T r = value_ % T(16);
        value_ = d;
        return render_hex_char(r);
    }

    bool is_zero() const
    {
        return !value_;
    }

    T value_;
};

} // namespace BufferFormat
} // namespace openvpn
