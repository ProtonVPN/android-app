//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2018 OpenVPN Inc.
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

#include <Windows.h>
#include <Lmcons.h>
#include <wtsapi32.h>

#include <openvpn/win/winerr.hpp>

namespace openvpn {
  namespace Win {
    class ImpersonateAsUser {
    public:
      ImpersonateAsUser() : local_system(is_local_system_())
      {
	if (local_system)
	  OPENVPN_LOG("ImpersonateAsUser: running under SYSTEM account, need to impersonate");
	else
	  {
	    OPENVPN_LOG("ImpersonateAsUser: running under user account, no need to impersonate");
	    return;
	  }

        DWORD sessId = WTSGetActiveConsoleSessionId();
	if (sessId == 0xFFFFFFFF)
	  {
	    const Win::LastError err;
	    OPENVPN_LOG("ImpersonateAsUser: WTSGetActiveConsoleSessionId() failed: " << err.message());
	    return;
	  }

	HANDLE hToken;
	if (!WTSQueryUserToken(sessId, &hToken))
	  {
	    const Win::LastError err;
	    OPENVPN_LOG("ImpersonateAsUser: WTSQueryUserToken() failed: " << err.message());
	    return;
	  }

	if (!ImpersonateLoggedOnUser(hToken))
	  {
	    CloseHandle(hToken);

	    const Win::LastError err;
	    OPENVPN_LOG("ImpersonateAsUser: ImpersonateLoggedOnUser() failed: " << err.message());
	    return;
	  }

	CloseHandle(hToken);

	impersonated = true;

	char uname[UNLEN + 1];
	DWORD len = UNLEN + 1;
	GetUserNameA(uname, &len);
	OPENVPN_LOG("ImpersonateAsUser: impersonated as " << uname);
      }

      ~ImpersonateAsUser() {
	if (impersonated)
	  {
	    if (!RevertToSelf())
	      {
	        const Win::LastError err;
	        OPENVPN_LOG("ImpersonateAsUser: RevertToSelf() failed: " << err.message());
	      }
	  }
      }

      bool is_local_system() const
      {
	return local_system;
      }

    private:
      // https://stackoverflow.com/a/4024388/227024
      BOOL is_local_system_() const
      {
	HANDLE hToken;
	UCHAR bTokenUser[sizeof(TOKEN_USER) + 8 + 4 * SID_MAX_SUB_AUTHORITIES];
	PTOKEN_USER pTokenUser = (PTOKEN_USER)bTokenUser;
	ULONG cbTokenUser;
	SID_IDENTIFIER_AUTHORITY siaNT = SECURITY_NT_AUTHORITY;
	PSID pSystemSid;
	BOOL bSystem;

	// open process token
	if (!OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &hToken))
	  return FALSE;

	// retrieve user SID
	if (!GetTokenInformation(hToken, TokenUser, pTokenUser, sizeof(bTokenUser), &cbTokenUser))
	  {
	    CloseHandle(hToken);
	    return FALSE;
	  }

	CloseHandle(hToken);

	// allocate LocalSystem well-known SID
	if (!AllocateAndInitializeSid(&siaNT, 1, SECURITY_LOCAL_SYSTEM_RID,
	  0, 0, 0, 0, 0, 0, 0, &pSystemSid)) return FALSE;

	// compare the user SID from the token with the LocalSystem SID
	bSystem = EqualSid(pTokenUser->User.Sid, pSystemSid);

	FreeSid(pSystemSid);

	return bSystem;
      }

      bool impersonated = false;
      bool local_system = false;
    };
  }
}
