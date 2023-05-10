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

// Basic file-handling methods.

#ifndef OPENVPN_COMMON_FILE_H
#define OPENVPN_COMMON_FILE_H

#include <string>
#include <fstream>
#include <iostream>
#include <cstdint> // for std::uint64_t

#include <openvpn/common/exception.hpp>
#include <openvpn/common/unicode.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/buffer/bufstr.hpp>
#include <openvpn/buffer/buflist.hpp>

#if defined(OPENVPN_PLATFORM_WIN)
#include <openvpn/win/unicode.hpp>
#endif

#if __cplusplus >= 201703L
#include <filesystem>
#endif

namespace openvpn {

OPENVPN_UNTAGGED_EXCEPTION(file_exception);
OPENVPN_UNTAGGED_EXCEPTION_INHERIT(file_exception, open_file_error);
OPENVPN_UNTAGGED_EXCEPTION_INHERIT(file_exception, file_too_large);
OPENVPN_UNTAGGED_EXCEPTION_INHERIT(file_exception, file_is_binary);
OPENVPN_UNTAGGED_EXCEPTION_INHERIT(file_exception, file_not_utf8);

// Read text from file via stream approach that doesn't require that we
// establish the length of the file in advance.
inline std::string read_text_simple(const std::string &filename)
{
    std::ifstream ifs(filename.c_str());
    if (!ifs)
        OPENVPN_THROW(open_file_error, "cannot open for read: " << filename);
    const std::string str((std::istreambuf_iterator<char>(ifs)), std::istreambuf_iterator<char>());
    if (!ifs)
        OPENVPN_THROW(open_file_error, "cannot read: " << filename);
    return str;
}

// Read a file (may be text or binary).
inline BufferPtr read_binary(const std::string &filename,
                             const std::uint64_t max_size = 0,
                             const unsigned int buffer_flags = 0)
{
#if defined(OPENVPN_PLATFORM_WIN)
    Win::UTF16 filenamew(Win::utf16(filename));
#if __cplusplus >= 201703L
    std::filesystem::path path(filenamew.get());
    std::ifstream ifs(path, std::ios::binary);
#elif _MSC_VER
    std::ifstream ifs(filenamew.get(), std::ios::binary);
#else
    std::ifstream ifs(filename.c_str(), std::ios::binary);
#endif // __cplusplus
#else
    std::ifstream ifs(filename.c_str(), std::ios::binary);
#endif // OPENVPN_PLATFORM_WIN

    if (!ifs)
        OPENVPN_THROW(open_file_error, "cannot open for read: " << filename);

    // get length of file
    ifs.seekg(0, std::ios::end);
    const std::streamsize length = ifs.tellg();
    if (max_size && std::uint64_t(length) > max_size)
        OPENVPN_THROW(file_too_large, "file too large [" << length << '/' << max_size << "]: " << filename);
    ifs.seekg(0, std::ios::beg);

    // allocate buffer
    BufferPtr b = new BufferAllocated(size_t(length), buffer_flags | BufferAllocated::ARRAY);

    // read data
    ifs.read((char *)b->data(), length);

    // check for errors
    if (ifs.gcount() != length)
        OPENVPN_THROW(open_file_error, "read length inconsistency: " << filename);
    if (!ifs)
        OPENVPN_THROW(open_file_error, "cannot read: " << filename);

    return b;
}

// Read a file (may be text or binary) without seeking to determine
// its length.
inline BufferPtr read_binary_linear(const std::string &filename,
                                    const std::uint64_t max_size = 0,
                                    const size_t block_size = 1024)
{
    std::ifstream ifs(filename.c_str(), std::ios::binary);
    if (!ifs)
        OPENVPN_THROW(open_file_error, "cannot open for read: " << filename);

    BufferList buflist;
    std::streamsize total_size = 0;
    while (true)
    {
        BufferPtr b = new BufferAllocated(block_size, 0);
        ifs.read((char *)b->data(), b->remaining());
        const std::streamsize size = ifs.gcount();
        if (size)
        {
            b->set_size(size);
            total_size += size;
            if (max_size && std::uint64_t(total_size) > max_size)
                OPENVPN_THROW(file_too_large, "file too large [" << total_size << '/' << max_size << "]: " << filename);
            buflist.push_back(std::move(b));
        }
        if (ifs.eof())
            break;
        if (!ifs)
            OPENVPN_THROW(open_file_error, "cannot read: " << filename);
    }
    return buflist.join();
}

// Read a text file as a std::string, throw error if file is binary
inline std::string read_text(const std::string &filename, const std::uint64_t max_size = 0)
{
    BufferPtr bp = read_binary(filename, max_size);
    if (bp->contains_null())
        OPENVPN_THROW(file_is_binary, "file is binary: " << filename);
    return std::string((const char *)bp->c_data(), bp->size());
}

// Read a UTF-8 file as a std::string, throw errors if file is binary or malformed UTF-8
inline std::string read_text_utf8(const std::string &filename, const std::uint64_t max_size = 0)
{
    BufferPtr bp = read_binary(filename, max_size);

    // check if binary
    if (bp->contains_null())
        OPENVPN_THROW(file_is_binary, "file is binary: " << filename);

    // remove Windows UTF-8 BOM if present
    if (bp->size() >= 3)
    {
        const unsigned char *data = bp->c_data();
        if (data[0] == 0xEF && data[1] == 0xBB && data[2] == 0xBF)
            bp->advance(3);
    }

    // verify that file is valid UTF-8
    if (!Unicode::is_valid_utf8_uchar_buf(bp->c_data(), bp->size()))
        OPENVPN_THROW(file_not_utf8, "file is not UTF8: " << filename);

    return std::string((const char *)bp->c_data(), bp->size());
}

// Read multi-line string from stdin
inline std::string read_stdin()
{
    std::string ret;
    std::string line;
    while (std::getline(std::cin, line))
    {
        ret += line;
        ret += '\n';
    }
    return ret;
}

// Write binary buffer to file
inline void write_binary(const std::string &filename, const Buffer &buf)
{
    std::ofstream ofs(filename.c_str(), std::ios::binary);
    if (!ofs)
        OPENVPN_THROW(open_file_error, "cannot open for write: " << filename);
    ofs.write((const char *)buf.c_data(), buf.size());
    if (!ofs)
        OPENVPN_THROW(open_file_error, "cannot write: " << filename);
}

// Write binary buffer list to file
template <typename BUFLIST>
inline void write_binary_list(const std::string &filename, const BUFLIST &buflist)
{
    std::ofstream ofs(filename.c_str(), std::ios::binary);
    if (!ofs)
        OPENVPN_THROW(open_file_error, "cannot open for write: " << filename);
    for (auto &buf : buflist)
    {
        ofs.write((const char *)buf->c_data(), buf->size());
        if (!ofs)
            OPENVPN_THROW(open_file_error, "cannot write: " << filename);
    }
}

// Write std::string to file
inline void write_string(const std::string &filename, const std::string &str)
{
    BufferPtr buf = buf_from_string(str);
    write_binary(filename, *buf);
}

} // namespace openvpn

#endif // OPENVPN_COMMON_FILE_H
