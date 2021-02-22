//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
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

#ifndef OPENVPN_COMMON_WSTRING_H
#define OPENVPN_COMMON_WSTRING_H

#include <string>
#include <vector>
#include <locale>
#include <codecvt>
#include <memory>

namespace openvpn {
  namespace wstring {

    inline std::wstring from_utf8(const std::string& str)
    {
#ifdef __MINGW32__
      // https://sourceforge.net/p/mingw-w64/bugs/538/
      typedef std::codecvt_utf8<wchar_t, 0x10ffff, std::little_endian> cvt_type;
#else
      typedef std::codecvt_utf8<wchar_t> cvt_type;
#endif
      std::wstring_convert<cvt_type, wchar_t> cvt;
      return cvt.from_bytes(str);
    }

    inline std::string to_utf8(const std::wstring& wstr)
    {
#ifdef __MINGW32__
      typedef std::codecvt_utf8<wchar_t, 0x10ffff, std::little_endian> cvt_type;
#else
      typedef std::codecvt_utf8<wchar_t> cvt_type;
#endif
      std::wstring_convert<cvt_type, wchar_t> cvt;
      return cvt.to_bytes(wstr);
    }

    inline std::unique_ptr<wchar_t[]> to_wchar_t(const std::wstring& wstr)
    {
      const size_t len = wstr.length();
      std::unique_ptr<wchar_t[]> ret(new wchar_t[len+1]);
      size_t i;
      for (i = 0; i < len; ++i)
	ret[i] = wstr[i];
      ret[i] = L'\0';
      return ret;
    }

    // return value corresponds to the MULTI_SZ string format on Windows
    inline std::wstring pack_string_vector(const std::vector<std::string>& strvec)
    {
      std::wstring ret;
      for (auto &s : strvec)
	{
	  ret += from_utf8(s);
	  ret += L'\0';
	}
      return ret;
    }
  }
}

#endif
