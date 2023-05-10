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
#include <lmcons.h>
#include <wtsapi32.h>

#include <openvpn/win/winerr.hpp>

namespace openvpn {
namespace Win {

class Impersonate
{
  public:
    explicit Impersonate(bool as_local_system)
        : local_system_(is_local_system_())
    {
        if (as_local_system)
        {
            if (local_system_)
                OPENVPN_LOG("ImpersonateAsSystem: running under SYSTEM account, no need to impersonate");
            else
                impersonate_as_local_system();
        }
        else
        {
            if (local_system_)
                impersonate_as_user();
            else
                OPENVPN_LOG("ImpersonateAsUser: running under user account, no need to impersonate");
        }
    }

    ~Impersonate()
    {
        if (impersonated)
        {
            if (!RevertToSelf())
            {
                const Win::LastError err;
                OPENVPN_LOG("Impersonate: RevertToSelf() failed: " << err.message());
            }
        }
    }

    bool is_local_system() const
    {
        return local_system_;
    }

  private:
    void impersonate_as_local_system()
    {
        HANDLE thread_token, process_snapshot, winlogon_process, winlogon_token, duplicated_token;
        PROCESSENTRY32 entry = {};
        entry.dwSize = sizeof(PROCESSENTRY32);
        BOOL ret;
        DWORD pid = 0;
        TOKEN_PRIVILEGES privileges = {};
        privileges.PrivilegeCount = 1;
        privileges.Privileges->Attributes = SE_PRIVILEGE_ENABLED;

        if (!LookupPrivilegeValue(NULL, SE_DEBUG_NAME, &privileges.Privileges[0].Luid))
            return;

        if (!ImpersonateSelf(SecurityImpersonation))
            return;

        impersonated = true;

        if (!OpenThreadToken(GetCurrentThread(), TOKEN_ADJUST_PRIVILEGES, FALSE, &thread_token))
            return;
        if (!AdjustTokenPrivileges(thread_token, FALSE, &privileges, sizeof(privileges), NULL, NULL))
        {
            CloseHandle(thread_token);
            return;
        }
        CloseHandle(thread_token);

        process_snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
        if (process_snapshot == INVALID_HANDLE_VALUE)
            return;

        for (ret = Process32First(process_snapshot, &entry); ret; ret = Process32Next(process_snapshot, &entry))
        {
            if (!_stricmp(entry.szExeFile, "winlogon.exe"))
            {
                pid = entry.th32ProcessID;
                break;
            }
        }
        CloseHandle(process_snapshot);
        if (!pid)
            return;

        winlogon_process = OpenProcess(PROCESS_QUERY_INFORMATION, FALSE, pid);
        if (!winlogon_process)
            return;

        if (!OpenProcessToken(winlogon_process, TOKEN_IMPERSONATE | TOKEN_DUPLICATE, &winlogon_token))
        {
            CloseHandle(winlogon_process);
            return;
        }
        CloseHandle(winlogon_process);

        if (!DuplicateToken(winlogon_token, SecurityImpersonation, &duplicated_token))
        {
            CloseHandle(winlogon_token);
            return;
        }
        CloseHandle(winlogon_token);

        if (!SetThreadToken(NULL, duplicated_token))
        {
            CloseHandle(duplicated_token);
            return;
        }
        CloseHandle(duplicated_token);
    }

    void impersonate_as_user()
    {
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
        if (!AllocateAndInitializeSid(&siaNT, 1, SECURITY_LOCAL_SYSTEM_RID, 0, 0, 0, 0, 0, 0, 0, &pSystemSid))
            return FALSE;

        // compare the user SID from the token with the LocalSystem SID
        bSystem = EqualSid(pTokenUser->User.Sid, pSystemSid);

        FreeSid(pSystemSid);

        return bSystem;
    }

    bool local_system_ = false;
    bool impersonated = false;
};
} // namespace Win
} // namespace openvpn
