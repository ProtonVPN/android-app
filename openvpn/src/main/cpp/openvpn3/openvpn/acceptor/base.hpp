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

// multi-protocol acceptor classes that handle the protocol-specific
// details of accepting client connections.

#pragma once

#include <vector>
#include <utility>

#include <openvpn/io/io.hpp>

#include <openvpn/common/platform.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/common/size.hpp>
#include <openvpn/asio/asiopolysock.hpp>

#ifndef OPENVPN_ACCEPTOR_LISTENER_BASE_RC
#define OPENVPN_ACCEPTOR_LISTENER_BASE_RC RC<thread_unsafe_refcount>
#endif

namespace openvpn::Acceptor {

struct ListenerBase : public OPENVPN_ACCEPTOR_LISTENER_BASE_RC
{
    typedef RCPtr<ListenerBase> Ptr;

    virtual void handle_accept(AsioPolySock::Base::Ptr sock, const openvpn_io::error_code &error) = 0;
};

struct Base : public RC<thread_unsafe_refcount>
{
    typedef RCPtr<Base> Ptr;

    virtual void async_accept(ListenerBase *listener,
                              const size_t acceptor_index,
                              openvpn_io::io_context &io_context) = 0;
    virtual void close() = 0;
};

struct Item
{
    enum SSLMode
    {
        SSLOff,
        SSLOn,
#ifdef OPENVPN_POLYSOCK_SUPPORTS_ALT_ROUTING
        AltRouting,
#endif
    };

    Item(Base::Ptr acceptor_arg,
         const SSLMode ssl_mode_arg)
        : acceptor(std::move(acceptor_arg)),
          ssl_mode(ssl_mode_arg)
    {
    }

    Base::Ptr acceptor;
    SSLMode ssl_mode;
};

struct Set : public std::vector<Item>
{
    void close()
    {
        for (auto &i : *this)
            i.acceptor->close();
    }
};

} // namespace openvpn::Acceptor
