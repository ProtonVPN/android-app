//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2017 OpenVPN Inc.
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

// windows SECURITY_ATTRIBUTES utilities

#ifndef OPENVPN_WIN_MODNAME_H
#define OPENVPN_WIN_MODNAME_H

#include <windows.h>

#include <string>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/wstring.hpp>
#include <openvpn/win/winerr.hpp>

namespace openvpn {
  namespace Win {

      inline std::wstring module_name()
      {
	// get path to our binary
	wchar_t path[MAX_PATH];
	if (!::GetModuleFileNameW(NULL, path, MAX_PATH))
	  {
	    const Win::LastError err;
	    OPENVPN_THROW_EXCEPTION("GetModuleFileNameW failed: " << err.message());
	  }
	return std::wstring(path);
      }

    inline std::string module_name_utf8()
    {
      return wstring::to_utf8(module_name());
    }
  }
}

#endif
