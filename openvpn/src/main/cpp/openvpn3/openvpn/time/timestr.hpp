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

// Function to return the current date/time as a string.

#ifndef OPENVPN_TIME_TIMESTR_H
#define OPENVPN_TIME_TIMESTR_H

#include <string>
#include <cstring> // for std::strlen and std::memset
#include <time.h>
#include <stdio.h>
#include <ctype.h>
#include <cstdint> // for std::uint64_t

#include <openvpn/common/platform.hpp>
#include <openvpn/common/size.hpp>
#include <openvpn/common/string.hpp>

#if defined(OPENVPN_PLATFORM_WIN)
#include <windows.h>
#else
#include <sys/time.h>
#endif

namespace openvpn {

#if defined(OPENVPN_PLATFORM_WIN)

inline std::string date_time(const time_t now)
{
    struct tm *lt = localtime(&now);
    char *ret = asctime(lt);
    const size_t len = std::strlen(ret);
    if (len > 0 && ret[len - 1] == '\n')
        ret[len - 1] = '\0';
    return ret;
}

inline std::string date_time()
{
    const time_t now = time(NULL);
    return date_time(now);
}

inline std::string date_time_store_time_t(time_t &save)
{
    save = time(NULL);
    return date_time(save);
}

#else

inline std::string date_time(const time_t t)
{
    struct tm lt;
    char buf[64];

    std::memset(&lt, 0, sizeof(lt));
    if (!localtime_r(&t, &lt))
        return "LOCALTIME_ERROR";
    if (!asctime_r(&lt, buf))
        return "ASCTIME_ERROR";
    const size_t len = std::strlen(buf);
    if (len > 0 && buf[len - 1] == '\n')
        buf[len - 1] = '\0';
    return std::string(buf);
}

inline std::string date_time_utc(const time_t t)
{
    struct tm lt;
    char buf[64];

    std::memset(&lt, 0, sizeof(lt));
    if (!gmtime_r(&t, &lt))
        return "GMTIME_ERROR";
    if (!asctime_r(&lt, buf))
        return "ASCTIME_ERROR";
    const size_t len = std::strlen(buf);
    if (len > 0 && buf[len - 1] == '\n')
        buf[len - 1] = '\0';
    return std::string(buf);
}

// msecs == false : Tue Feb 17 01:24:30 2015
// msecs == true  : Tue Feb 17 01:24:30.123 2015
inline std::string date_time(const struct timeval *tv, const bool msecs)
{
    const std::string dt = date_time(tv->tv_sec);
    if (msecs)
    {
        // find correct position in string to insert milliseconds
        const size_t pos = dt.find_last_of(':');
        if (pos != std::string::npos
            && pos + 3 < dt.length()
            && string::is_digit(dt[pos + 1])
            && string::is_digit(dt[pos + 2])
            && string::is_space(dt[pos + 3]))
        {
            char ms[5];
            ::snprintf(ms, sizeof(ms), ".%03u", static_cast<unsigned int>(tv->tv_usec / 1000));
            return dt.substr(0, pos + 3) + ms + dt.substr(pos + 3);
        }
    }
    return dt;
}

inline std::string nanosec_time_to_string(const std::uint64_t ns_time)
{
    const std::uint64_t sec = ns_time / std::uint64_t(1000000000);
    const std::uint64_t ns = ns_time % std::uint64_t(1000000000);

    const std::string dt = date_time_utc(sec);

    // find correct position in string to insert nanoseconds
    const size_t pos = dt.find_last_of(':');
    if (pos != std::string::npos
        && pos + 3 < dt.length()
        && string::is_digit(dt[pos + 1])
        && string::is_digit(dt[pos + 2])
        && string::is_space(dt[pos + 3]))
    {
        char ms[11];
        ::snprintf(ms, sizeof(ms), ".%09u", (unsigned int)ns);
        return dt.substr(0, pos + 3) + ms + dt.substr(pos + 3);
    }
    return dt;
}

inline std::string date_time()
{
    struct timeval tv;
    if (::gettimeofday(&tv, nullptr) < 0)
    {
        tv.tv_sec = 0;
        tv.tv_usec = 0;
    }
    return date_time(&tv, true);
}

inline std::string date_time_store_time_t(time_t &save)
{
    struct timeval tv;
    if (::gettimeofday(&tv, nullptr) < 0)
    {
        tv.tv_sec = 0;
        tv.tv_usec = 0;
    }
    save = tv.tv_sec;
    return date_time(&tv, true);
}

#endif

inline std::string date_time_rfc822(const time_t t)
{
    struct tm lt;
    char buf[64];

#if defined(OPENVPN_PLATFORM_WIN)
    if (gmtime_s(&lt, &t))
        return "";
    // MinGW doesn't yet support %T, so use %H:%M:%S
    // https://sourceforge.net/p/mingw-w64/bugs/793/
    if (!strftime(buf, sizeof(buf), "%a, %d %b %Y %H:%M:%S GMT", &lt))
        return "";
#else
    if (!gmtime_r(&t, &lt))
        return "";
    if (!strftime(buf, sizeof(buf), "%a, %d %b %Y %T %Z", &lt))
        return "";
#endif
    return std::string(buf);
}

inline std::string date_time_rfc822()
{
    return date_time_rfc822(::time(nullptr));
}

} // namespace openvpn

#endif
