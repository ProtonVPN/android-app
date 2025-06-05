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

// Client tun setup base class for Windows

#ifndef OPENVPN_TUN_WIN_CLIENT_SETUPBASE_H
#define OPENVPN_TUN_WIN_CLIENT_SETUPBASE_H

#include <windows.h> // for HANDLE

#include <functional>

#include <openvpn/io/io.hpp>

#include <openvpn/common/destruct.hpp>
#include <openvpn/common/stop.hpp>
#include <openvpn/tun/builder/capture.hpp>
#include <openvpn/tun/client/tunbase.hpp>
#include <openvpn/tun/win/ringbuffer.hpp>

namespace openvpn::TunWin {

struct SetupBase : public DestructorBase
{
    typedef RCPtr<SetupBase> Ptr;

    OPENVPN_EXCEPTION(tun_win_setup);

    virtual HANDLE get_handle(std::ostream &os) = 0;

    // clang-format off
    virtual HANDLE establish(const TunBuilderCapture &pull,
                             const std::wstring &openvpn_app_path,
                             Stop *stop,
                             std::ostream &os,
                             RingBuffer::Ptr rings) = 0;
    // clang-format on

    virtual bool l2_ready(const TunBuilderCapture &pull) = 0;

    // clang-format off
    virtual void l2_finish(const TunBuilderCapture &pull,
                           Stop *stop,
                           std::ostream &os) = 0;
    // clang-format on

    virtual void confirm()
    {
    }

    virtual void set_service_fail_handler(std::function<void()> &&handler)
    {
    }

    virtual Util::TapNameGuidPair get_adapter_state() = 0;

    virtual void set_adapter_state(const Util::TapNameGuidPair &state) = 0;

    virtual DWORD vpn_interface_index() const
    {
        return INVALID_ADAPTER_INDEX;
    }
};

struct SetupFactory : public RC<thread_unsafe_refcount>
{
    typedef RCPtr<SetupFactory> Ptr;

    // clang-format off
    virtual SetupBase::Ptr new_setup_obj(openvpn_io::io_context &io_context,
                                         const TunWin::Type tun_type,
                                         bool allow_local_dns_resolvers) = 0;
    // clang-format on
};
} // namespace openvpn::TunWin

#endif
