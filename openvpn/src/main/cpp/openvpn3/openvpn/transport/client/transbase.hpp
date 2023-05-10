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

// Abstract base classes for client transport objects that implement UDP, TCP,
// HTTP Proxy, etc.

#ifndef OPENVPN_TRANSPORT_CLIENT_TRANSBASE_H
#define OPENVPN_TRANSPORT_CLIENT_TRANSBASE_H

#include <string>

#include <openvpn/io/io.hpp>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/addr/ip.hpp>
#include <openvpn/error/error.hpp>
#include <openvpn/crypto/cryptodc.hpp>
#include <openvpn/transport/protocol.hpp>

namespace openvpn {
struct TransportClientParent;

// Base class for client transport object.
struct TransportClient : public virtual RC<thread_unsafe_refcount>
{
    typedef RCPtr<TransportClient> Ptr;

    virtual void transport_start() = 0;
    virtual void stop() = 0;
    virtual bool transport_send_const(const Buffer &buf) = 0;
    virtual bool transport_send(BufferAllocated &buf) = 0;
    virtual bool transport_send_queue_empty() = 0;
    virtual bool transport_has_send_queue() = 0;
    virtual void transport_stop_requeueing() = 0;
    virtual unsigned int transport_send_queue_size() = 0;
    virtual void reset_align_adjust(const size_t align_adjust) = 0;
    virtual IP::Addr server_endpoint_addr() const = 0;
    virtual unsigned short server_endpoint_port() const
    {
        return 0;
    }
    virtual int native_handle()
    {
        return 0;
    }
    // clang-format off
    virtual void server_endpoint_info(std::string &host,
                                      std::string &port,
                                      std::string &proto,
                                      std::string &ip_addr) const = 0;
    // clang-format on
    virtual Protocol transport_protocol() const = 0;
    virtual void transport_reparent(TransportClientParent *parent) = 0;
};

// Base class for parent of client transport object, used by client transport
// objects to communicate received data packets, exceptions, and progress
// notifications.
struct TransportClientParent
{
    virtual void transport_recv(BufferAllocated &buf) = 0;
    virtual void transport_needs_send() = 0; // notification that send queue is empty
    virtual void transport_error(const Error::Type fatal_err, const std::string &err_text) = 0;
    virtual void proxy_error(const Error::Type fatal_err, const std::string &err_text) = 0;

    // Return true if we are transporting OpenVPN protocol
    virtual bool transport_is_openvpn_protocol() = 0;

    // progress notifications
    virtual void transport_pre_resolve() = 0;
    virtual void transport_wait_proxy() = 0;
    virtual void transport_wait() = 0;
    virtual void transport_connecting() = 0;

    // Return true if keepalive parameter(s) are enabled.
    virtual bool is_keepalive_enabled() const = 0;

    // clang-format off
    // Disable keepalive for rest of session, but fetch
    // the keepalive parameters (in seconds).
    virtual void disable_keepalive(unsigned int &keepalive_ping,
                                   unsigned int &keepalive_timeout) = 0;
    // clang-format on

    virtual ~TransportClientParent()
    {
    }
};

// Factory for client transport object.
struct TransportClientFactory : public virtual RC<thread_unsafe_refcount>
{
    typedef RCPtr<TransportClientFactory> Ptr;

    // clang-format off
    virtual TransportClient::Ptr new_transport_client_obj(openvpn_io::io_context &io_context,
                                                          TransportClientParent *parent) = 0;
    // clang-format on

    virtual bool is_relay()
    {
        return false;
    }
    virtual void process_push(const OptionList &)
    {
        return;
    }
};

} // namespace openvpn

#endif // OPENVPN_TRANSPORT_CLIENT_TRANSBASE_H
