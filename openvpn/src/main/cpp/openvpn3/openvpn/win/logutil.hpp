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

#include <windows.h>

#include <string>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/wstring.hpp>
#include <openvpn/time/timestr.hpp>
#include <openvpn/win/winerr.hpp>
#include <openvpn/win/secattr.hpp>
#include <openvpn/win/scoped_handle.hpp>

namespace openvpn {
namespace Win {
namespace LogUtil {

inline void log(const HANDLE file, const std::string &str)
{
    DWORD n_written;
    const std::string line = date_time() + ' ' + str;
    ::WriteFile(file, line.c_str(), line.length(), &n_written, NULL);
}

inline ScopedHANDLE create_file(const std::string &fn,
                                const std::string &sddl_string,
                                bool append)
{
    SecurityAttributes sa(sddl_string, true, fn); // fn passed as title only
    const std::wstring wfn = wstring::from_utf8(fn);
    ScopedHANDLE file(::CreateFileW(
        wfn.c_str(),
        GENERIC_WRITE,
        FILE_SHARE_READ,
        &sa.sa,
        append ? OPEN_ALWAYS : CREATE_ALWAYS,
        FILE_ATTRIBUTE_NORMAL,
        NULL));
    if (!file.defined())
    {
        const Win::LastError err;
        OPENVPN_THROW_EXCEPTION("Win::LogFile: failed to open " << fn << " : " << err.message());
    }

    // append to logfile?
    if (append)
    {
        if (::SetFilePointer(file(), 0, NULL, FILE_END) == INVALID_SET_FILE_POINTER)
        {
            const Win::LastError err;
            OPENVPN_THROW_EXCEPTION("Win::LogFile: cannot append to " << fn << " : " << err.message());
        }
    }
    return file;
}

} // namespace LogUtil
} // namespace Win
} // namespace openvpn
