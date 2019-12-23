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

// test certificate subject and common name against tls_remote parameter

#ifndef OPENVPN_SSL_TLS_REMOTE_H
#define OPENVPN_SSL_TLS_REMOTE_H

#include <cstring>
#include <string>

#include <openvpn/common/string.hpp>

namespace openvpn {
  namespace TLSRemote {
    inline bool test(const std::string& tls_remote, const std::string& subject, const std::string& common_name)
    {
      return tls_remote == subject || string::starts_with(common_name, tls_remote);
    }

    inline void log(const std::string& tls_remote, const std::string& subject, const std::string& common_name)
    {
      OPENVPN_LOG("tls-remote validation" << std::endl << "  tls-remote: '" << tls_remote << '\'' << std::endl << "  Subj: '" << subject << '\'' << std::endl << "  CN: '" << common_name << '\'');
    }

    // modifies x509 name in a way that is compatible with
    // name remapping behavior on OpenVPN 2.x
    inline std::string sanitize_x509_name(const std::string& str)
    {
      std::string ret;
      bool leading_dash = true;
      ret.reserve(str.length());
      for (size_t i = 0; i < str.length(); ++i)
	{
	  const char c = str[i];
	  if (c == '-' && leading_dash)
	    {
	      ret += '_';
	      continue;
	    }
	  leading_dash = false;
	  if ((c >= 'a' && c <= 'z')
	      || (c >= 'A' && c <= 'Z')
	      || (c >= '0' && c <= '9')
	      || c == '_' || c == '-' || c == '.'
	      || c == '@' || c == ':' || c == '/'
	      || c == '=')
	    ret += c;
	  else
	    ret += '_';
	}
      return ret;
    }

    // modifies common name in a way that is compatible with
    // name remapping behavior on OpenVPN 2.x
    inline std::string sanitize_common_name(const std::string& str)
    {
      std::string ret;
      ret.reserve(str.length());
      for (size_t i = 0; i < str.length(); ++i)
	{
	  const char c = str[i];
	  if ((c >= 'a' && c <= 'z')
	      || (c >= 'A' && c <= 'Z')
	      || (c >= '0' && c <= '9')
	      || c == '_' || c == '-' || c == '.'
	      || c == '@' || c == '/')
	    ret += c;
	  else
	    ret += '_';
	}
      return ret;
    }
  }
}

#endif
