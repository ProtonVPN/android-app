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

// execute a Windows command, capture the output

#ifndef OPENVPN_WIN_CALL_H
#define OPENVPN_WIN_CALL_H

#include <windows.h>
#include <shlobj.h>
#include <knownfolders.h>

#include <cstring>

#include <openvpn/common/uniqueptr.hpp>
#include <openvpn/win/scoped_handle.hpp>
#include <openvpn/win/unicode.hpp>

namespace openvpn {
namespace Win {

OPENVPN_EXCEPTION(win_call);

inline std::string call(const std::string &cmd)
{
    // split command name from args
    std::string name;
    std::string args;
    const size_t spcidx = cmd.find_first_of(" ");
    if (spcidx != std::string::npos)
    {
        name = cmd.substr(0, spcidx);
        if (spcidx + 1 < cmd.length())
            args = cmd.substr(spcidx + 1);
    }
    else
        name = cmd;

#if _WIN32_WINNT >= 0x0600
    // get system path (Vista and higher)
    wchar_t *syspath_ptr = nullptr;
    if (::SHGetKnownFolderPath(FOLDERID_System, 0, nullptr, &syspath_ptr) != S_OK)
        throw win_call("cannot get system path using SHGetKnownFolderPath");
    unique_ptr_del<wchar_t> syspath(syspath_ptr,
                                    [](wchar_t *p)
                                    { ::CoTaskMemFree(p); });
#define SYSPATH_FMT_CHAR L"s"
#define SYSPATH_LEN_METH(x) ::wcslen(x)
#else
    // get system path (XP and higher)
    std::unique_ptr<wchar_t[]> syspath(new wchar_t[MAX_PATH]);
    if (::SHGetFolderPathW(nullptr, CSIDL_SYSTEM, nullptr, 0, syspath.get()) != S_OK)
        throw win_call("cannot get system path using SHGetFolderPathW");
#define SYSPATH_FMT_CHAR L"s"
#define SYSPATH_LEN_METH(x) ::wcslen(x)
#endif

    // build command line
    const size_t wcmdlen = SYSPATH_LEN_METH(syspath.get()) + name.length() + args.length() + 64;
    std::unique_ptr<wchar_t[]> wcmd(new wchar_t[wcmdlen]);
    const char *spc = "";
    if (!args.empty())
        spc = " ";
    ::_snwprintf(wcmd.get(), wcmdlen, L"\"%" SYSPATH_FMT_CHAR L"\\%S.exe\"%S%S", syspath.get(), name.c_str(), spc, args.c_str());
    wcmd.get()[wcmdlen - 1] = 0;
    //::wprintf(L"CMD[%d]: %s\n", (int)::wcslen(wcmd.get()), wcmd.get());
#undef SYSPATH_FMT_CHAR
#undef SYSPATH_LEN_METH

    // Set the bInheritHandle flag so pipe handles are inherited.
    SECURITY_ATTRIBUTES saAttr;
    saAttr.nLength = sizeof(SECURITY_ATTRIBUTES);
    saAttr.bInheritHandle = TRUE;
    saAttr.lpSecurityDescriptor = nullptr;

    // Create a pipe for the child process's STDOUT.
    ScopedHANDLE cstdout_r; // child write side
    ScopedHANDLE cstdout_w; // parent read side
    if (!::CreatePipe(cstdout_r.ref(), cstdout_w.ref(), &saAttr, 0))
        throw win_call("cannot create pipe for child stdout");

    // Ensure the read handle to the pipe for STDOUT is not inherited.
    if (!::SetHandleInformation(cstdout_r(), HANDLE_FLAG_INHERIT, 0))
        throw win_call("SetHandleInformation failed for child stdout pipe");

    // Set up members of the PROCESS_INFORMATION structure.
    PROCESS_INFORMATION piProcInfo;
    ::ZeroMemory(&piProcInfo, sizeof(PROCESS_INFORMATION));

    // Set up members of the STARTUPINFO structure.
    // This structure specifies the STDIN and STDOUT handles for redirection.
    STARTUPINFOW siStartInfo;
    ::ZeroMemory(&siStartInfo, sizeof(STARTUPINFO));
    siStartInfo.cb = sizeof(STARTUPINFO);
    siStartInfo.hStdError = cstdout_w();
    siStartInfo.hStdOutput = cstdout_w();
    siStartInfo.hStdInput = nullptr;
    siStartInfo.dwFlags |= STARTF_USESTDHANDLES;

    // Create the child process.
    if (!::CreateProcessW(nullptr,
                          wcmd.get(),   // command line
                          nullptr,      // process security attributes
                          nullptr,      // primary thread security attributes
                          TRUE,         // handles are inherited
                          0,            // creation flags
                          nullptr,      // use parent's environment
                          nullptr,      // use parent's current directory
                          &siStartInfo, // STARTUPINFO pointer
                          &piProcInfo)) // receives PROCESS_INFORMATION
        throw win_call("cannot create process");

    // wrap handles to child process and its primary thread.
    ScopedHANDLE process_hand(piProcInfo.hProcess);
    ScopedHANDLE thread_hand(piProcInfo.hThread);

    // close child's end of stdout/stderr pipe
    cstdout_w.close();

    // read child's stdout
    const size_t outbuf_size = 512;
    std::unique_ptr<char[]> outbuf(new char[outbuf_size]);
    std::string out;
    while (true)
    {
        DWORD dwRead;
        if (!::ReadFile(cstdout_r(), outbuf.get(), outbuf_size, &dwRead, nullptr))
            break;
        if (dwRead == 0)
            break;
        out += std::string(outbuf.get(), 0, dwRead);
    }

    // decode output using console codepage, convert to utf16
    // console codepage, used to decode output
    UTF16 utf16output(Win::utf16(out, ::GetOEMCP()));

    // re-encode utf16 to utf8
    UTF8 utf8output(Win::utf8(utf16output.get()));
    out.assign(utf8output.get());

    // wait for child to exit
    if (::WaitForSingleObject(process_hand(), INFINITE) == WAIT_FAILED)
        throw win_call("WaitForSingleObject failed on child process handle");

    return out;
}
} // namespace Win
} // namespace openvpn

#endif
