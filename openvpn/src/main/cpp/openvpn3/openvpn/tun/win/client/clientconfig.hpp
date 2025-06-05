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

#pragma once

#include <openvpn/asio/scoped_asio_stream.hpp>
#include <openvpn/tun/client/tunbase.hpp>
#include <openvpn/tun/client/tunprop.hpp>
#include <openvpn/tun/persist/tunpersist.hpp>
#include <openvpn/tun/win/client/tunsetup.hpp>

namespace openvpn::TunWin {

// These types manage the underlying TAP driver HANDLE
typedef openvpn_io::windows::stream_handle TAPStream;
typedef ScopedAsioStream<TAPStream> ScopedTAPStream;

template <typename ADAPTER_STATE>
struct TunPersistState
{
    TunProp::State::Ptr state;
    ADAPTER_STATE adapter_state;

    void reset()
    {
        state.reset();
        adapter_state.reset();
    }
};
typedef TunPersistTemplate<ScopedTAPStream, TunPersistState<RingBuffer::Ptr>> TunPersist;
typedef TunPersistTemplate<ScopedTAPStream, TunPersistState<Util::TapNameGuidPair>> DcoTunPersist;

class ClientConfig : public TunClientFactory
{
    friend class Client; // accesses wfp

  public:
    typedef RCPtr<ClientConfig> Ptr;

    TunProp::Config tun_prop;
    int n_parallel = 8; // number of parallel async reads on tun socket
    TunWin::Type tun_type = TunWin::TapWindows6;
    bool allow_local_dns_resolvers = false;

    Frame::Ptr frame;
    SessionStats::Ptr stats;

    Stop *stop = nullptr;

    TunPersist::Ptr tun_persist;

    TunWin::SetupFactory::Ptr tun_setup_factory;

    TunWin::SetupBase::Ptr new_setup_obj(openvpn_io::io_context &io_context)
    {
        if (tun_setup_factory)
            return tun_setup_factory->new_setup_obj(io_context, tun_type, allow_local_dns_resolvers);
        else
            return new TunWin::Setup(io_context, tun_type, allow_local_dns_resolvers);
    }

    static Ptr new_obj()
    {
        return new ClientConfig;
    }

    TunClient::Ptr new_tun_client_obj(openvpn_io::io_context &io_context,
                                      TunClientParent &parent,
                                      TransportClient *transcli) override;

    bool supports_epoch_data() override
    {
        return tun_type != TunWin::OvpnDco;
    }

    void finalize(const bool disconnected) override
    {
        if (disconnected)
            tun_persist.reset();
    }

    bool layer_2_supported() const override
    {
        return true;
    }
};
} // namespace openvpn::TunWin
