//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012- OpenVPN Inc.
//
//    SPDX-License-Identifier: MPL-2.0 OR AGPL-3.0-only WITH openvpn3-openssl-exception
//

// scoped HANDLE for windows

#ifndef OPENVPN_WIN_SCOPED_HANDLE_H
#define OPENVPN_WIN_SCOPED_HANDLE_H

#include <windows.h>

#include <openvpn/common/size.hpp>
#include <openvpn/win/handle.hpp>

namespace openvpn::Win {
class ScopedHANDLE
{
    ScopedHANDLE(const ScopedHANDLE &) = delete;
    ScopedHANDLE &operator=(const ScopedHANDLE &) = delete;

  public:
    typedef HANDLE base_type;

    ScopedHANDLE()
        : handle(Handle::undefined())
    {
    }

    explicit ScopedHANDLE(HANDLE h)
        : handle(h)
    {
    }

    HANDLE release()
    {
        const HANDLE ret = handle;
        handle = nullptr;
        return ret;
    }

    bool defined() const
    {
        return Handle::defined(handle);
    }

    HANDLE operator()() const
    {
        return handle;
    }

    HANDLE *ref()
    {
        return &handle;
    }

    void reset(HANDLE h)
    {
        close();
        handle = h;
    }

    void reset()
    {
        close();
    }

    // unusual semantics: replace handle without closing it first
    void replace(HANDLE h)
    {
        handle = h;
    }

    bool close()
    {
        if (defined())
        {
            const BOOL ret = ::CloseHandle(handle);
            // OPENVPN_LOG("**** SH CLOSE hand=" << handle << " ret=" << ret);
            handle = nullptr;
            return ret != 0;
        }
        else
            return true;
    }

    ~ScopedHANDLE()
    {
        close();
    }

    ScopedHANDLE(ScopedHANDLE &&other) noexcept
    {
        handle = other.handle;
        other.handle = nullptr;
    }

    ScopedHANDLE &operator=(ScopedHANDLE &&other) noexcept
    {
        close();
        handle = other.handle;
        other.handle = nullptr;
        return *this;
    }

  private:
    HANDLE handle;
};

} // namespace openvpn::Win

#endif
