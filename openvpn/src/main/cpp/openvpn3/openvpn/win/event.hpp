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
//

#ifndef OPENVPN_WIN_EVENT_H
#define OPENVPN_WIN_EVENT_H

#include <windows.h>

#include <string>

#include <openvpn/buffer/bufhex.hpp>
#include <openvpn/win/winerr.hpp>
#include <openvpn/win/scoped_handle.hpp>

namespace openvpn::Win {

// Wrap a standard Windows Event object
class Event
{
  public:
    explicit Event(BOOL manual_reset = TRUE)
    {
        event.reset(::CreateEvent(NULL, manual_reset, FALSE, NULL));
        if (!event.defined())
        {
            const Win::LastError err;
            OPENVPN_THROW_EXCEPTION("Win::Event: cannot create Windows event: " << err.message());
        }
    }

    std::string duplicate_local()
    {
        HANDLE new_handle;
        if (!::DuplicateHandle(GetCurrentProcess(),
                               event(),
                               GetCurrentProcess(),
                               &new_handle,
                               0,
                               FALSE,
                               DUPLICATE_SAME_ACCESS))
        {
            const Win::LastError err;
            OPENVPN_THROW_EXCEPTION("Win::Event: DuplicateHandle failed: " << err.message());
        }
        return BufHex::render(new_handle);
    }

    void signal_event()
    {
        if (event.defined())
        {
            ::SetEvent(event());
            event.close();
        }
    }

    void release_event()
    {
        event.close();
    }

    HANDLE operator()() const
    {
        return event();
    }

    void reset(HANDLE h)
    {
        event.reset(h);
    }

  private:
    ScopedHANDLE event;
};

// Windows event object that automatically signals in the destructor
struct DestroyEvent : public Event
{
    ~DestroyEvent()
    {
        signal_event();
    }
};

} // namespace openvpn::Win

#endif
