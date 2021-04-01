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

#pragma once

// windows SECURITY_ATTRIBUTES utilities

#include <windows.h>
#include <sddl.h>

#include <openvpn/common/exception.hpp>
#include <openvpn/win/winerr.hpp>

namespace openvpn {
  namespace Win {

    struct SecurityAttributes
    {
      OPENVPN_EXCEPTION(win_sec_attr);

      SecurityAttributes(const std::string& sddl_string,
			 const bool inherit,
			 const std::string& title)
      {
	sa.nLength = sizeof(SECURITY_ATTRIBUTES);
	sa.bInheritHandle = inherit ? TRUE : FALSE;
	sa.lpSecurityDescriptor = nullptr;
	if (!sddl_string.empty())
	  {
	    if (!::ConvertStringSecurityDescriptorToSecurityDescriptorA(
	        sddl_string.c_str(),
		SDDL_REVISION_1,
		&sa.lpSecurityDescriptor,    // allocates memory
		NULL))
	      {
		const Win::LastError err;
		OPENVPN_THROW(win_sec_attr, "failed to create security descriptor for " << title << " : " << err.message());
	      }
	  }
      }

      ~SecurityAttributes()
      {
	::LocalFree(sa.lpSecurityDescriptor);
      }

      SECURITY_ATTRIBUTES sa;
    };

  }
}
