//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
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

#pragma once

#include <memory>
#include <sstream>
#include <vector>

#include <openvpn/buffer/asiobuf.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/size.hpp>
#include <openvpn/common/to_string.hpp>
#include <openvpn/time/time.hpp>
#include <openvpn/transport/client/transbase.hpp>
#include <openvpn/transport/dco.hpp>
#include <openvpn/tun/builder/capture.hpp>
#include <openvpn/tun/client/tunbase.hpp>

#ifndef ENABLE_OVPNDCOWIN
#include <openvpn/tun/linux/client/tunmethods.hpp>
#endif

#ifdef ENABLE_KOVPN
#include <openvpn/kovpn/kodevtun.hpp>
#include <openvpn/kovpn/kostats.hpp>
#include <openvpn/kovpn/kovpn.hpp>
#include <openvpn/kovpn/rps_xps.hpp>
#elif ENABLE_OVPNDCO
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/common/uniqueptr.hpp>
#include <openvpn/dco/key.hpp>
#include <openvpn/tun/linux/client/genl.hpp>
#include <openvpn/tun/linux/client/sitnl.hpp>
#elif ENABLE_OVPNDCOWIN
#include <openvpn/dco/key.hpp>
#include <ovpn-dco-win/uapi.h>
#else
#error either ENABLE_KOVPN, ENABLE_OVPNDCO or ENABLE_OVPNDCOWIN must be defined
#endif

#include <openvpn/dco/korekey.hpp>

// client-side DCO (Data Channel Offload) module for Linux/kovpn

namespace openvpn {
namespace DCOTransport {
enum {
  OVPN_PEER_ID_UNDEF = 0x00FFFFFF,
};

OPENVPN_EXCEPTION(dco_error);

class ClientConfig : public DCO,
                     public TransportClientFactory,
                     public TunClientFactory {
public:
  typedef RCPtr<ClientConfig> Ptr;

  std::string dev_name;

  DCO::TransportConfig transport;
  DCO::TunConfig tun;

  bool allow_local_dns_resolvers = false;

  unsigned int ping_restart_override = 0;

  void process_push(const OptionList& opt) override
  {
    transport.remote_list->process_push(opt);
  }

  virtual TunClientFactory::Ptr
  new_tun_factory(const DCO::TunConfig &conf, const OptionList &opt) override {
    tun = conf;

    // set a default MTU
    if (!tun.tun_prop.mtu)
      tun.tun_prop.mtu = 1500;

    // parse "dev" option
    {
      const Option *dev = opt.get_ptr("dev");
      if (dev)
        dev_name = dev->get(1, 64);
      else
        dev_name = "ovpnc";
    }

    // parse ping-restart-override
    ping_restart_override = opt.get_num<decltype(ping_restart_override)>(
        "ping-restart-override", 1, ping_restart_override, 0, 3600);

    return TunClientFactory::Ptr(this);
  }

  virtual TransportClientFactory::Ptr
  new_transport_factory(const DCO::TransportConfig &conf) override {
    transport = conf;
    return TransportClientFactory::Ptr(this);
  }

  virtual TunClient::Ptr new_tun_client_obj(openvpn_io::io_context &io_context,
                                            TunClientParent &parent,
                                            TransportClient *transcli) override;

  virtual TransportClient::Ptr
  new_transport_client_obj(openvpn_io::io_context &io_context,
                           TransportClientParent *parent) override;

  static DCO::Ptr new_controller(TunBuilderBase* tb)
  {
    auto ctrl = new ClientConfig();
    if (ctrl)
      ctrl->builder = tb;
    return ctrl;
  }

protected:
  ClientConfig() = default;
};

class Client : public TransportClient,
               public TunClient,
               public AsyncResolvableUDP {
  friend class ClientConfig;

  typedef RCPtr<Client> Ptr;

public:
  // transport methods

  virtual bool transport_send_queue_empty() override { return false; }

  virtual bool transport_has_send_queue() override { return false; }

  virtual unsigned int transport_send_queue_size() override { return 0; }

  virtual void reset_align_adjust(const size_t align_adjust) override {}

  virtual void transport_stop_requeueing() override {}

  virtual void server_endpoint_info(std::string &host, std::string &port,
                                    std::string &proto,
                                    std::string &ip_addr) const override {
    host = server_host;
    port = server_port;
    const IP::Addr addr = server_endpoint_addr();
    proto = config->transport.protocol.is_tcp() ? "TCP" : "UDP";
    proto += addr.version_string();
    proto += "-DCO";
    ip_addr = addr.to_string();
  }

  virtual void stop() override { stop_(); }

  // tun methods

  virtual void set_disconnect() override {}

  virtual bool
  tun_send(BufferAllocated &buf) override // return true if send succeeded
  {
    return false;
  }

  virtual std::string vpn_ip4() const override {
    if (state->vpn_ip4_addr.specified())
      return state->vpn_ip4_addr.to_string();
    else
      return "";
  }

  virtual std::string vpn_ip6() const override {
    if (state->vpn_ip6_addr.specified())
      return state->vpn_ip6_addr.to_string();
    else
      return "";
  }

  virtual std::string vpn_gw4() const override {
    if (state->vpn_ip4_gw.specified())
      return state->vpn_ip4_gw.to_string();
    else
      return "";
  }

  virtual std::string vpn_gw6() const override {
    if (state->vpn_ip6_gw.specified())
      return state->vpn_ip6_gw.to_string();
    else
      return "";
  }

protected:
  Client(openvpn_io::io_context &io_context_arg, ClientConfig *config_arg,
         TransportClientParent *parent_arg)
      : AsyncResolvableUDP(io_context_arg), io_context(io_context_arg),
        halt(false), state(new TunProp::State()), config(config_arg),
        transport_parent(parent_arg), tun_parent(nullptr),
        peer_id(OVPN_PEER_ID_UNDEF) {}

  virtual void transport_reparent(TransportClientParent *parent_arg) override {
    transport_parent = parent_arg;
  }

  virtual void stop_() = 0;

  openvpn_io::io_context &io_context;
  bool halt;

  TunProp::State::Ptr state;

  ClientConfig::Ptr config;
  TransportClientParent *transport_parent;
  TunClientParent *tun_parent;

  ActionList::Ptr remove_cmds;

  std::string server_host;
  std::string server_port;

  uint32_t peer_id;
};

#ifdef ENABLE_KOVPN
#include <openvpn/kovpn/kovpncli.hpp>
inline DCO::Ptr new_controller(TunBuilderBase*) { return KovpnClientConfig::new_controller(); }
inline TransportClient::Ptr
ClientConfig::new_transport_client_obj(openvpn_io::io_context &io_context,
                                       TransportClientParent *parent) {
  return TransportClient::Ptr(new KovpnClient(io_context, this, parent));
}
#elif ENABLE_OVPNDCO
#include <openvpn/dco/ovpndcocli.hpp>
inline DCO::Ptr new_controller(TunBuilderBase* tb) {
  if (!OvpnDcoClient::available(tb))
    return nullptr;

  CryptoAlgs::allow_dc_algs({
    CryptoAlgs::CHACHA20_POLY1305,
    CryptoAlgs::AES_128_GCM,
    CryptoAlgs::AES_192_GCM,
    CryptoAlgs::AES_256_GCM
  });
  return ClientConfig::new_controller(tb);
}
inline TransportClient::Ptr
ClientConfig::new_transport_client_obj(openvpn_io::io_context &io_context,
                                       TransportClientParent *parent) {
  return TransportClient::Ptr(new OvpnDcoClient(io_context, this, parent));
}
#elif ENABLE_OVPNDCOWIN
#include <openvpn/dco/ovpndcowincli.hpp>
inline DCO::Ptr new_controller(TunBuilderBase* tb) {
  if (!OvpnDcoWinClient::available())
    return nullptr;

  CryptoAlgs::allow_dc_algs({
    CryptoAlgs::AES_128_GCM,
    CryptoAlgs::AES_192_GCM,
    CryptoAlgs::AES_256_GCM
  });
  return ClientConfig::new_controller(nullptr);
}
inline TransportClient::Ptr
ClientConfig::new_transport_client_obj(openvpn_io::io_context& io_context,
                                       TransportClientParent* parent) {
  return TransportClient::Ptr(new OvpnDcoWinClient(io_context, this, parent));
}
#endif

inline TunClient::Ptr
ClientConfig::new_tun_client_obj(openvpn_io::io_context &io_context,
                                 TunClientParent &parent,
                                 TransportClient *transcli) {
  Client *cli = static_cast<Client *>(transcli);
  cli->tun_parent = &parent;
  return TunClient::Ptr(cli);
}
} // namespace DCOTransport
} // namespace openvpn
