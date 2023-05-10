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

// String methods on Buffer objects

#pragma once

#include <cstring>

#include <openvpn/buffer/buffer.hpp>

namespace openvpn {
// return contents of Buffer as a std::string
inline std::string buf_to_string(const Buffer &buf)
{
    return std::string((const char *)buf.c_data(), buf.size());
}

// return contents of ConstBuffer as a std::string
inline std::string buf_to_string(const ConstBuffer &buf)
{
    return std::string((const char *)buf.c_data(), buf.size());
}

// write std::string to Buffer
inline void buf_write_string(Buffer &buf, const std::string &str)
{
    buf.write((unsigned char *)str.c_str(), str.length());
}

// write C string to buffer
inline void buf_write_string(Buffer &buf, const char *str)
{
    buf.write((unsigned char *)str, std::strlen(str));
}

// return BufferPtr from std::string
inline BufferPtr buf_from_string(const std::string &str)
{
    const size_t len = str.length();
    BufferPtr buf(new BufferAllocated(len, 0));
    buf->write((unsigned char *)str.c_str(), len);
    return buf;
}

// return BufferPtr from C string
inline BufferPtr buf_from_string(const char *str)
{
    const size_t len = std::strlen(str);
    BufferPtr buf(new BufferAllocated(len, 0));
    buf->write((unsigned char *)str, len);
    return buf;
}

// return BufferAllocated from std::string
inline BufferAllocated buf_alloc_from_string(const std::string &str)
{
    const size_t len = str.length();
    BufferAllocated buf(len, 0);
    buf.write((unsigned char *)str.c_str(), len);
    return buf;
}

// return BufferAllocated from C string
inline BufferAllocated buf_alloc_from_string(const char *str)
{
    const size_t len = std::strlen(str);
    BufferAllocated buf(len, 0);
    buf.write((unsigned char *)str, len);
    return buf;
}

// append str to buf
inline void buf_append_string(Buffer &buf, const std::string &str)
{
    buf.write((unsigned char *)str.c_str(), str.length());
}

// append str to buf
inline void buf_append_string(Buffer &buf, const char *str)
{
    buf.write((unsigned char *)str, std::strlen(str));
}

// Note: ConstBuffer deep links to str, so returned ConstBuffer
// is only defined while str is in scope.
inline ConstBuffer const_buf_from_string(const std::string &str)
{
    return ConstBuffer((const unsigned char *)str.c_str(), str.size(), true);
}

// Return a C string from buffer.
// Note: requires that the buffer be null-terminated.
inline const char *buf_c_str(const Buffer &buf)
{
    return (const char *)buf.c_data();
}

// Return true if std::string equals string in buffer
inline bool buf_eq_str(const Buffer &buf, const std::string &str)
{
    if (buf.size() != str.length())
        return false;
    return std::memcmp(buf.c_data(), str.c_str(), buf.size()) == 0;
}
} // namespace openvpn
