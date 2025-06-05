//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012- OpenVPN Inc.
//
//    SPDX-License-Identifier: MPL-2.0 OR AGPL-3.0-only WITH openvpn3-openssl-exception
//

#pragma once

#ifdef _WIN32

#include <string>
#include <vector>
#include <memory>

namespace openvpn::wstring {

/**
 * @brief Convert a UTF-8 string to UTF-16
 *
 * @param str           The UTF-8 string to be converted
 * @return std::wstring The converted UTF-16 string
 */
inline std::wstring from_utf8(const std::string &str)
{
    std::wstring wStr; // enable RVO
    if (str.empty())
        return wStr;
    const int str_size = static_cast<int>(str.size());
    const auto reqSize = ::MultiByteToWideChar(CP_UTF8, 0, str.data(), str_size, nullptr, 0);
    if (reqSize == 0)
        throw std::runtime_error("MultiByteToWideChar(1) failed with code: [" + std::to_string(::GetLastError()) + "]");
    wStr.resize(reqSize, L'\0'); // Allocate space
    if (::MultiByteToWideChar(CP_UTF8, 0, str.data(), str_size, wStr.data(), reqSize) == 0)
        throw std::runtime_error("MultiByteToWideChar(2) failed with code: [" + std::to_string(::GetLastError()) + "]");
    return wStr;
}

/**
 * @brief Convert a UTF-16 string to UTF-8
 *
 * @param wstr          The UTF-16 string to be converted
 * @return std::string  The converted UTF-8 string
 */
inline std::string to_utf8(const std::wstring &wstr)
{
    std::string str; // For RVO
    if (wstr.empty())
        return str;
    const int wstr_size = static_cast<int>(wstr.size());
    const auto reqSize = ::WideCharToMultiByte(CP_UTF8, 0, wstr.data(), wstr_size, nullptr, 0, nullptr, nullptr);
    if (reqSize == 0)
        throw std::runtime_error("WideCharToMultiByte(1) failed with code: [" + std::to_string(::GetLastError()) + "]");
    str.resize(reqSize, '\0'); // Allocate space
    if (::WideCharToMultiByte(CP_UTF8, 0, wstr.data(), wstr_size, str.data(), reqSize, nullptr, nullptr) == 0)
        throw std::runtime_error("WideCharToMultiByte(2) failed with code: [" + std::to_string(::GetLastError()) + "]");
    return str;
}

/**
 * @brief Convert a UTF-8 string vector to a UTF-16 MULTI_SZ string
 *
 * MULTI_SZ is a format used in the Windows Registry. It's a buffer
 * containing multiple NUL terminated strings concatenated with each
 * other, with an extra NUL at the end to signal termination of the
 * MULTI_SZ string itself.
 *
 * @param strvec        The vector of strings to convert
 * @return std::wstring The converted MULTI_SZ string
 */
inline std::wstring pack_string_vector(const std::vector<std::string> &strvec)
{
    if (strvec.empty())
    {
        return std::wstring(2, L'\0'); // empty MULTI_SZ
    }
    std::wstring ret;
    for (auto &s : strvec)
    {
        ret += from_utf8(s);
        ret += L'\0';
    }
    ret += L'\0';
    return ret;
}

#endif // #ifdef _WIN32
}
