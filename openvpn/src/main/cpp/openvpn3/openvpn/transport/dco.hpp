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

#ifndef OPENVPN_TRANSPORT_DCO_H
#define OPENVPN_TRANSPORT_DCO_H

#include <string>

#include <openvpn/common/rc.hpp>
#include <openvpn/common/options.hpp>
#include <openvpn/common/stop.hpp>
#include <openvpn/client/remotelist.hpp>
#include <openvpn/frame/frame.hpp>
#include <openvpn/log/sessionstats.hpp>
#include <openvpn/transport/protocol.hpp>
#include <openvpn/transport/client/transbase.hpp>
#include <openvpn/tun/layer.hpp>
#include <openvpn/tun/client/tunbase.hpp>
#include <openvpn/tun/client/tunprop.hpp>

#if defined(OPENVPN_PLATFORM_WIN)
#include <openvpn/tun/win/client/tunsetup.hpp>
#include <openvpn/tun/win/client/clientconfig.hpp>
#endif

namespace openvpn {
struct DCO : public virtual RC<thread_unsafe_refcount>
{
    typedef RCPtr<DCO> Ptr;

    struct TransportConfig
    {
        TransportConfig()
            : server_addr_float(false)
        {
        }

        Protocol protocol;
        RemoteList::Ptr remote_list;
        bool server_addr_float;
        Frame::Ptr frame;
        SessionStats::Ptr stats;
        SocketProtect *socket_protect = nullptr;
    };

    struct TunConfig
    {
        TunConfig() = default;

#if defined(OPENVPN_PLATFORM_WIN)
        TunWin::SetupFactory::Ptr setup_factory;

        TunWin::SetupBase::Ptr new_setup_obj(openvpn_io::io_context &io_context, bool allow_local_dns_resolvers)
        {
            if (setup_factory)
                return setup_factory->new_setup_obj(io_context, TunWin::OvpnDco, allow_local_dns_resolvers);
            else
                return new TunWin::Setup(io_context, TunWin::OvpnDco, allow_local_dns_resolvers);
        }

        TunWin::DcoTunPersist::Ptr tun_persist;
#endif

        TunProp::Config tun_prop;
        Stop *stop = nullptr;
    };

    virtual TunClientFactory::Ptr new_tun_factory(const TunConfig &conf, const OptionList &opt) = 0;
    virtual TransportClientFactory::Ptr new_transport_factory(const TransportConfig &conf) = 0;

    TunBuilderBase *builder = nullptr;
};
} // namespace openvpn

#endif
