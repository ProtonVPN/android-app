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

// Null tun interface object, intended for testing.

#ifndef OPENVPN_TUN_CLIENT_TUNNULL_H
#define OPENVPN_TUN_CLIENT_TUNNULL_H

#include <openvpn/tun/client/tunbase.hpp>

namespace openvpn::TunNull {

class ClientConfig : public TunClientFactory
{
  public:
    typedef RCPtr<ClientConfig> Ptr;

    Frame::Ptr frame;
    SessionStats::Ptr stats;

    static Ptr new_obj()
    {
        return new ClientConfig;
    }

    TunClient::Ptr new_tun_client_obj(openvpn_io::io_context &io_context,
                                      TunClientParent &parent,
                                      TransportClient *transcli) override;

    bool supports_proto_v3() override
    {
        return true;
    }

  private:
    ClientConfig() = default;
};

class Client : public TunClient
{
    friend class ClientConfig; // calls constructor

  public:
    void tun_start(const OptionList &opt, TransportClient &transcli, CryptoDCSettings &) override
    {
#ifdef TUN_NULL_EXIT
        throw ErrorCode(Error::TUN_SETUP_FAILED, true, "TUN_NULL_EXIT");
#else
        // signal that we are "connected"
        parent.tun_connected();
#endif
    }

    bool tun_send(BufferAllocated &buf) override
    {
        config->stats->inc_stat(SessionStats::TUN_BYTES_OUT, buf.size());
        config->stats->inc_stat(SessionStats::TUN_PACKETS_OUT, 1);
        return true;
    }

    std::string tun_name() const override
    {
        return "TUN_NULL";
    }

    std::string vpn_ip4() const override
    {
        return "";
    }

    std::string vpn_ip6() const override
    {
        return "";
    }

    int vpn_mtu() const override
    {
        return 0;
    }

    void set_disconnect() override
    {
    }

    void stop() override
    {
    }

  private:
    Client(openvpn_io::io_context &io_context_arg,
           ClientConfig *config_arg,
           TunClientParent &parent_arg)
        : config(config_arg),
          parent(parent_arg)
    {
    }

    ClientConfig::Ptr config;
    TunClientParent &parent;
};

inline TunClient::Ptr ClientConfig::new_tun_client_obj(openvpn_io::io_context &io_context,
                                                       TunClientParent &parent,
                                                       TransportClient *transcli)
{
    return TunClient::Ptr(new Client(io_context, this, parent));
}

} // namespace openvpn::TunNull

#endif // OPENVPN_TUN_CLIENT_TUNNULL_H
