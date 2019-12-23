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

#ifndef OPENVPN_AUTH_VALIDATE_CREDS_H
#define OPENVPN_AUTH_VALIDATE_CREDS_H

#include <openvpn/common/unicode.hpp>

namespace openvpn {
  // Validate authentication credential.
  // Must be UTF-8.
  // Other checks on size and content below.
  // We don't check that the credential is non-empty.
  namespace ValidateCreds {

    enum Type {
      USERNAME,
      PASSWORD,
      RESPONSE
    };

    template <typename STRING>
    static bool is_valid(const Type type, const STRING& cred, const bool strict)
    {
      size_t max_len_flags;
      if (strict)
	{
	  // length <= 512 unicode chars, no control chars allowed
	  max_len_flags = 512 | Unicode::UTF8_NO_CTRL;
	}
      else
	{
	  switch (type)
	    {
	    case USERNAME:
	      // length <= 512 unicode chars, no control chars allowed
	      max_len_flags = 512 | Unicode::UTF8_NO_CTRL;
	      break;
	    case PASSWORD:
	    case RESPONSE:
	      // length <= 16384 unicode chars
	      max_len_flags = 16384;
	      break;
	    default:
	      return false;
	    }
	}
      return Unicode::is_valid_utf8(cred, max_len_flags);
    }
  }
}

#endif
