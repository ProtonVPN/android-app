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

// Abstract base classes for client tun interface objects.

#ifndef OPENVPN_TUN_CLIENT_TUNBASE_H
#define OPENVPN_TUN_CLIENT_TUNBASE_H

#include <string>

#include <openvpn/io/io.hpp>

#include <openvpn/common/rc.hpp>
#include <openvpn/common/options.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/transport/client/transbase.hpp>
#include <openvpn/client/clievent.hpp>

namespace openvpn {

// Base class for objects that implement a client tun interface.
struct TunClient : public virtual RC<thread_unsafe_refcount>
{
    typedef RCPtr<TunClient> Ptr;

    virtual void tun_start(const OptionList &, TransportClient &, CryptoDCSettings &) = 0;
    virtual void stop() = 0;
    virtual void set_disconnect() = 0;
    virtual bool tun_send(BufferAllocated &buf) = 0; // return true if send succeeded

    virtual std::string tun_name() const = 0;

    virtual std::string vpn_ip4() const = 0; // VPN IP addresses
    virtual std::string vpn_ip6() const = 0;

    virtual std::string vpn_gw4() const
    {
        return std::string();
    } // VPN gateways
    virtual std::string vpn_gw6() const
    {
        return std::string();
    }

    virtual int vpn_mtu() const = 0;

    virtual void adjust_mss(int mss){};
};

// Base class for parent of tun interface object, used to
// communicate received data packets, exceptions,
// special events, and progress notifications.
struct TunClientParent
{
    virtual void tun_recv(BufferAllocated &buf) = 0;
    virtual void tun_error(const Error::Type fatal_err, const std::string &err_text) = 0;

    // progress notifications
    virtual void tun_pre_tun_config() = 0;
    virtual void tun_pre_route_config() = 0;
    virtual void tun_connected() = 0;

    // allow tunclient to generate events
    virtual void tun_event(ClientEvent::Base::Ptr ev)
    {
    }
};

// Factory for tun interface objects.
struct TunClientFactory : public virtual RC<thread_unsafe_refcount>
{
    typedef RCPtr<TunClientFactory> Ptr;

    virtual TunClient::Ptr new_tun_client_obj(openvpn_io::io_context &io_context, TunClientParent &parent, TransportClient *transcli) = 0;

    // return true if layer 2 tunnels are supported
    virtual bool layer_2_supported() const
    {
        return false;
    }

    // Called on TunClient close, after TunClient::stop has been called.
    // disconnected ->
    //   true: this is the final disconnect, or
    //   false: we are in a pause/reconnecting state.
    virtual void finalize(const bool disconnected)
    {
    }
};

} // namespace openvpn

#endif // OPENVPN_TUN_CLIENT_TUNBASE_H
