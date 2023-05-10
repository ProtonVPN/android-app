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

// General-purpose base64 encode and decode.

#ifndef OPENVPN_COMMON_BASE64_H
#define OPENVPN_COMMON_BASE64_H

#include <string>
#include <cstring>   // for std::memset, std::strlen
#include <algorithm> // for std::min
#include <cstddef>   // for ptrdiff_t

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/extern.hpp>

namespace openvpn {

class Base64
{

    class ConstUCharWrap
    {
      public:
        using value_type = unsigned char;

      public:
        ConstUCharWrap(const value_type *data, size_t size)
            : data_(data),
              size_(size)
        {
        }

        size_t size() const
        {
            return size_;
        }
        value_type operator[](const size_t i) const
        {
            return data_[i];
        }


      private:
        const value_type *data_;
        size_t size_;
    };

  public:
    OPENVPN_SIMPLE_EXCEPTION(base64_decode_out_of_bound_error);

  private:
    // Minimal class to minic the container our decode method expects
    class UCharWrap
    {
      public:
        using value_type = unsigned char;

      public:
        UCharWrap(value_type *data, size_t size)
            : data(data), size(size), index(0)
        {
        }

        void push_back(value_type c)
        {
            if (index >= size)
                throw base64_decode_out_of_bound_error();

            data[index++] = c;
        }

        value_type *data;
        size_t size;
        size_t index;
    };

  public:
    OPENVPN_SIMPLE_EXCEPTION(base64_bad_map);
    OPENVPN_SIMPLE_EXCEPTION(base64_decode_error);

    // altmap is "+/=" by default
    // another possible encoding for URLs: "-_."
    Base64(const char *altmap = nullptr)
    {
        // build encoding map
        {
            unsigned int i;
            unsigned char j = 65;
            for (i = 0; i < 62; ++i)
            {
                enc[i] = j++;
                if (j == 91)
                    j = 97;
                else if (j == 123)
                    j = 48;
            }
            if (!altmap)
                altmap = "+/=";
            if (std::strlen(altmap) != 3)
                throw base64_bad_map();
            enc[62] = (unsigned char)altmap[0];
            enc[63] = (unsigned char)altmap[1];
            equal = (unsigned char)altmap[2];
        }

        // build decoding map
        {
            std::memset(dec, 0xFF, 128);
            for (unsigned int i = 0; i < 64; ++i)
            {
                const unsigned char c = enc[i];
                if (c >= 128)
                    throw base64_bad_map();
                dec[c] = (unsigned char)i;
            }
        }
    }

    static size_t decode_size_max(const size_t encode_size)
    {
        return encode_size;
    }

    static size_t encode_size_max(const size_t decode_size)
    {
        return decode_size * 4 / 3 + 4;
    }

    template <typename V>
    std::string encode(const V &data) const
    {
        char *s, *p;
        size_t i;
        unsigned int c;
        const size_t size = data.size();

        p = s = new char[encode_size_max(size)];
        for (i = 0; i < size;)
        {
            c = static_cast<unsigned char>(data[i++]) << 8;
            if (i < size)
                c += static_cast<unsigned char>(data[i]);
            i++;
            c <<= 8;
            if (i < size)
                c += static_cast<unsigned char>(data[i]);
            i++;
            p[0] = enc[(c & 0x00fc0000) >> 18];
            p[1] = enc[(c & 0x0003f000) >> 12];
            p[2] = enc[(c & 0x00000fc0) >> 6];
            p[3] = enc[c & 0x0000003f];
            if (i > size)
                p[3] = equal;
            if (i > size + 1)
                p[2] = equal;
            p += 4;
        }
        *p = '\0';
        const std::string ret(s);
        delete[] s;
        return ret;
    }

    std::string encode(const void *data, size_t size) const
    {
        return encode(ConstUCharWrap((const unsigned char *)data, size));
    }

    /**
     * Decodes data from the passed string and stores the
     * result in data
     * @param data 	Destination of the decoded data
     * @param len	Length of the region in data
     * @param str	Base64 string to decode
     * @return 		Number of bytes written to data
     */
    size_t decode(void *data, size_t len, const std::string &str) const
    {
        UCharWrap ret((unsigned char *)data, len);
        decode(ret, str);
        return ret.index;
    }

    std::string decode(const std::string &str) const
    {
        std::string ret;
        ret.reserve(str.length());
        decode(ret, str);
        return ret;
    }

    template <typename V>
    void decode(V &dest, const std::string &str) const
    {
        const char *endp = str.c_str() + str.length();
        for (const char *p = str.c_str(); p < endp; p += 4)
        {
            using vvalue_t = typename V::value_type;
            unsigned int marker;
            const unsigned int val = token_decode(p, std::min(endp - p, ptrdiff_t(4)), marker);
            dest.push_back(static_cast<vvalue_t>((val >> 16) & 0xff));
            if (marker < 2)
                dest.push_back(static_cast<vvalue_t>((val >> 8) & 0xff));
            if (marker < 1)
                dest.push_back(static_cast<vvalue_t>(val & 0xff));
        }
    }

    template <typename V>
    bool is_base64(const V &data, const size_t expected_decoded_length) const
    {
        const size_t size = data.size();
        if (size != encoded_len(expected_decoded_length))
            return false;
        const size_t eq_begin = size - num_eq(expected_decoded_length);
        for (size_t i = 0; i < size; ++i)
        {
            const char c = data[i];
            if (i < eq_begin)
            {
                if (!is_base64_char(c))
                    return false;
            }
            else
            {
                if (c != equal)
                    return false;
            }
        }
        return true;
    }

  private:
    bool is_base64_char(const char c) const
    {
        const size_t idx = c;
        return idx < 128 && dec[idx] != 0xFF;
    }

    unsigned int decode_base64_char(const char c) const
    {
        const size_t idx = c;
        if (idx >= 128)
            throw base64_decode_error();
        const unsigned int v = dec[idx];
        if (v == 0xFF)
            throw base64_decode_error();
        return v;
    }

    unsigned int token_decode(const char *token, const ptrdiff_t len, unsigned int &marker) const
    {
        size_t i;
        unsigned int val = 0;
        marker = 0; // number of equal chars seen
        if (len < 4)
            throw base64_decode_error();
        for (i = 0; i < 4; i++)
        {
            val <<= 6;
            if (token[i] == equal)
                marker++;
            else if (marker > 0)
                throw base64_decode_error();
            else
                val += decode_base64_char(token[i]);
        }
        if (marker > 2)
            throw base64_decode_error();
        return val;
    }

    static size_t encoded_len(const size_t decoded_len)
    {
        return (decoded_len * 4 / 3 + 3) & ~3;
    }

    static size_t num_eq(const size_t decoded_len)
    {
        return (-1 - decoded_len) % 3;
    }

    unsigned char enc[64];
    unsigned char dec[128];
    unsigned char equal;
};

// provide a static Base64 object

OPENVPN_EXTERN const Base64 *base64;         // GLOBAL
OPENVPN_EXTERN const Base64 *base64_urlsafe; // GLOBAL

inline void base64_init_static()
{
    if (!base64)
        base64 = new Base64();
    if (!base64_urlsafe)
        base64_urlsafe = new Base64("-_.");
}

inline void base64_uninit_static()
{
    if (base64)
    {
        delete base64;
        base64 = nullptr;
    }
    if (base64_urlsafe)
    {
        delete base64_urlsafe;
        base64_urlsafe = nullptr;
    }
}

} // namespace openvpn

#endif
