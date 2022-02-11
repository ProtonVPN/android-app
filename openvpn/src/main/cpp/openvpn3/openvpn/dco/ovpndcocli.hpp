//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
//    Copyright (C) 2020-2020 Lev Stipakov <lev@openvpn.net>
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

// tun/transport client for ovpn-dco

#include <openvpn/dco/dcocli.hpp>
#include <openvpn/tun/client/tunconfigflags.hpp>

class OvpnDcoClient : public Client,
                      public KoRekey::Receiver,
                      public TransportClientParent,
                      public SessionStats::DCOTransportSource {
  friend class ClientConfig;
  friend class GeNL;

  OPENVPN_EXCEPTION(dcocli_error);

  typedef RCPtr<OvpnDcoClient> Ptr;
  typedef GeNL<OvpnDcoClient *> GeNLImpl;

  struct PacketFrom {
    typedef std::unique_ptr<PacketFrom> SPtr;
    BufferAllocated buf;
  };

public:
  static bool available(TunBuilderBase* tb) {
    if (tb)
      return tb->tun_builder_dco_available();
    else
      return GeNLImpl::available();
  }

  virtual void tun_start(const OptionList &opt, TransportClient &transcli,
                         CryptoDCSettings &dc_settings) override {
    // extract peer ID from pushed options
    try {
      const Option *o = opt.get_ptr("peer-id");
      if (o) {
        bool status = parse_number_validate<uint32_t>(
            o->get(1, 16), 16, 0, OVPN_PEER_ID_UNDEF - 1, &peer_id);
        if (!status)
          OPENVPN_THROW(dcocli_error, "Parse/range issue with pushed peer-id");
      } else {
        OPENVPN_THROW(dcocli_error, "No peer-id pushed by server");
      }
    } catch (const std::exception &e) {
      OPENVPN_THROW(dcocli_error, "Cannot extract peer-id: " << e.what());
    }

    // notify parent
    tun_parent->tun_pre_tun_config();

    // parse pushed options
    TunBuilderCapture::Ptr po;
    TunBuilderBase *builder;

    if (config->builder) {
      builder = config->builder;
    } else {
      po.reset(new TunBuilderCapture());
      builder = po.get();
    }

    TunProp::configure_builder(
        builder, state.get(), config->transport.stats.get(),
        server_endpoint_addr(), config->tun.tun_prop, opt, nullptr, false);

    if (po)
      OPENVPN_LOG("CAPTURED OPTIONS:" << std::endl << po->to_string());

    if (config->builder) {
      config->builder->tun_builder_dco_establish();
    } else {
      ActionList::Ptr add_cmds = new ActionList();
      remove_cmds.reset(new ActionListReversed());

      std::vector<IP::Route> rtvec;

      TUN_LINUX::tun_config(config->dev_name, *po, &rtvec, *add_cmds,
                            *remove_cmds, TunConfigFlags::ADD_BYPASS_ROUTES);

      // execute commands to bring up interface
      add_cmds->execute_log();
    }

    // Add a hook so ProtoContext will call back to
    // rekey() on rekey ops.
    dc_settings.set_factory(CryptoDCFactory::Ptr(new KoRekey::Factory(
        dc_settings.factory(), this, config->transport.frame)));

    // add peer in ovpn-dco
    add_peer(peer_id, state->vpn_ip4_gw.to_ipv4_zero(),
             state->vpn_ip6_gw.to_ipv6_zero());
    // signal that we are connected
    tun_parent->tun_connected();
  }

  virtual std::string tun_name() const override { return "ovpn-dco"; }

  virtual IP::Addr server_endpoint_addr() const override {
    if (transport)
      return transport->server_endpoint_addr();
    else
      return IP::Addr();
  }

  virtual unsigned short server_endpoint_port() const override {
    if (transport)
      return transport->server_endpoint_port();
    else
      return 0;
  }

  virtual Protocol transport_protocol() const override {
    return transport->transport_protocol();
  }

  virtual void transport_start() override {
    TransportClientFactory::Ptr transport_factory;

    if (!config->transport.protocol.is_tcp()) {
      UDPTransport::ClientConfig::Ptr udpconf =
          UDPTransport::ClientConfig::new_obj();
      udpconf->remote_list = config->transport.remote_list;
      udpconf->frame = config->transport.frame;
      udpconf->stats = config->transport.stats;
      udpconf->socket_protect = config->transport.socket_protect;
      udpconf->server_addr_float = config->transport.server_addr_float;
      transport_factory = udpconf;
    } else {
      TCPTransport::ClientConfig::Ptr tcpconf =
          TCPTransport::ClientConfig::new_obj();
      tcpconf->remote_list = config->transport.remote_list;
      tcpconf->frame = config->transport.frame;
      tcpconf->stats = config->transport.stats;
      tcpconf->socket_protect = config->transport.socket_protect;
      transport_factory = tcpconf;
    }

    config->transport.stats->dco_configure(this);

    transport = transport_factory->new_transport_client_obj(io_context, this);
    transport->transport_start();
  }

  virtual bool transport_send_const(const Buffer &buf) override {
    if (peer_id == OVPN_PEER_ID_UNDEF)
      return transport->transport_send_const(buf);

    if (config->builder) {
      Buffer tmp(buf);
      tmp.prepend(&peer_id, sizeof(peer_id));
      pipe->write_some(tmp.const_buffer());
    } else {
      genl->send_data(peer_id, buf.c_data(), buf.size());
    }

    return true;
  }

  virtual bool transport_send(BufferAllocated &buf) override {
    OPENVPN_THROW(dcocli_error,
                  "Non-const send expected for data channel only, but "
                  "ovpndcocli is not expected to handle data packets");
  }

  void get_remote_sockaddr(struct sockaddr_storage &sa, socklen_t &salen) {
    memset(&sa, 0, sizeof(sa));

    struct sockaddr_in *sa4 = (struct sockaddr_in *)&sa;
    struct sockaddr_in6 *sa6 = (struct sockaddr_in6 *)&sa;

    IP::Addr remote_addr = transport->server_endpoint_addr();
    if (remote_addr.version() == IP::Addr::V4) {
      salen = sizeof(*sa4);
      *sa4 =
          remote_addr.to_ipv4().to_sockaddr(transport->server_endpoint_port());
    } else {
      salen = sizeof(*sa6);
      *sa6 =
          remote_addr.to_ipv6().to_sockaddr(transport->server_endpoint_port());
    }
  }

  void del_peer(uint32_t peer_id) {
    TunBuilderBase *tb = config->builder;
    if (tb) {
      tb->tun_builder_dco_del_peer(peer_id);
      return;
    }

    genl->del_peer(peer_id);
  }

  void add_peer(uint32_t peer_id, IPv4::Addr ipv4, IPv6::Addr ipv6) {
    struct sockaddr_storage sa;
    socklen_t salen;

    get_remote_sockaddr(sa, salen);

    TunBuilderBase *tb = config->builder;
    if (tb) {
      tb->tun_builder_dco_new_peer(peer_id, transport->native_handle(),
                                   (struct sockaddr *)&sa, salen, ipv4, ipv6);

      queue_read_pipe(nullptr);
      return;
    }

    genl->new_peer(peer_id, transport->native_handle(), (struct sockaddr *)&sa,
                   salen, ipv4, ipv6);
  }

  void update_peer_stats(uint32_t peer_id, bool sync) {
    const SessionStats::DCOTransportSource::Data old_stats = last_stats;

    if (peer_id == OVPN_PEER_ID_UNDEF)
      return;

    TunBuilderBase *tb = config->builder;
    if (tb) {
      tb->tun_builder_dco_get_peer(peer_id, sync);
      queue_read_pipe(nullptr);
    } else {
      genl->get_peer(peer_id, sync);
    }

    last_delta = last_stats - old_stats;
  }

  virtual void resolve_callback(const openvpn_io::error_code &error,
                                results_type results) override {}

  virtual void stop_() override {
    if (!halt) {
      /* update stats before deleting peer in kernelspace */
      update_peer_stats(peer_id, true);

      halt = true;

      if (config->builder) {
        config->builder->tun_builder_teardown(true);
        if (pipe)
          pipe->close();
      } else {
        std::ostringstream os;
        if (genl)
          genl->stop();

        int res = TunNetlink::iface_del(os, config->dev_name);
        if (res != 0) {
          OPENVPN_LOG("ovpndcocli: error deleting iface ovpn:" << os.str());
        }
      }

      if (transport)
        transport->stop();
    }
  }

  virtual void rekey(const CryptoDCInstance::RekeyType rktype,
                     const KoRekey::Info &rkinfo) override {
    if (halt)
      return;

    if (config->builder)
      rekey_impl_tb(rktype, rkinfo);
    else
      rekey_impl(rktype, rkinfo);
  }

  void rekey_impl(const CryptoDCInstance::RekeyType rktype,
                  const KoRekey::Info &rkinfo) {
    KoRekey::OvpnDcoKey key(rktype, rkinfo);
    auto kc = key();

    switch (rktype) {
    case CryptoDCInstance::ACTIVATE_PRIMARY:
      genl->new_key(OVPN_KEY_SLOT_PRIMARY, kc);

      handle_keepalive();
      break;

    case CryptoDCInstance::NEW_SECONDARY:

      genl->new_key(OVPN_KEY_SLOT_SECONDARY, kc);
      break;

    case CryptoDCInstance::PRIMARY_SECONDARY_SWAP:

      genl->swap_keys(peer_id);
      break;

    case CryptoDCInstance::DEACTIVATE_SECONDARY:

      genl->del_key(peer_id, OVPN_KEY_SLOT_SECONDARY);
      break;

    case CryptoDCInstance::DEACTIVATE_ALL:
      // TODO: deactivate all keys
      OPENVPN_LOG("ovpndcocli: deactivate all keys");
      break;

    default:
      OPENVPN_LOG("ovpndcocli: unknown rekey type: " << rktype);
      break;
    }
  }

  void rekey_impl_tb(const CryptoDCInstance::RekeyType rktype,
                     const KoRekey::Info &rkinfo) {
    KoRekey::OvpnDcoKey key(rktype, rkinfo);
    auto kc = key();

    TunBuilderBase *tb = config->builder;

    switch (rktype) {
    case CryptoDCInstance::ACTIVATE_PRIMARY:
      tb->tun_builder_dco_new_key(OVPN_KEY_SLOT_PRIMARY, kc);

      handle_keepalive();
      break;

    case CryptoDCInstance::NEW_SECONDARY:
      tb->tun_builder_dco_new_key(OVPN_KEY_SLOT_SECONDARY, kc);
      break;

    case CryptoDCInstance::PRIMARY_SECONDARY_SWAP:
      tb->tun_builder_dco_swap_keys(peer_id);
      break;

    case CryptoDCInstance::DEACTIVATE_SECONDARY:
      tb->tun_builder_dco_del_key(peer_id, OVPN_KEY_SLOT_SECONDARY);
      break;

    case CryptoDCInstance::DEACTIVATE_ALL:
      // TODO: deactivate all keys
      OPENVPN_LOG("ovpndcocli: deactivate all keys");
      break;

    default:
      OPENVPN_LOG("ovpndcocli: unknown rekey type: " << rktype);
      break;
    }
  }

  virtual void transport_recv(BufferAllocated &buf) override {
    transport_parent->transport_recv(buf);
  }

  bool tun_read_handler(BufferAllocated &buf) {
    if (halt)
      return false;

    int8_t cmd = -1;
    buf.read(&cmd, sizeof(cmd));

    switch (cmd) {
    case OVPN_CMD_PACKET:
      transport_parent->transport_recv(buf);
      break;

    case OVPN_CMD_DEL_PEER: {
      uint32_t peer_id;
      buf.read(&peer_id, sizeof(peer_id));
      uint8_t reason;
      buf.read(&reason, sizeof(reason));

      std::ostringstream os;
      Error::Type err;

      switch (reason) {
      case OVPN_DEL_PEER_REASON_EXPIRED:
        err = Error::TRANSPORT_ERROR;
        os << "keepalive timeout";
        break;

      case OVPN_DEL_PEER_REASON_TRANSPORT_ERROR:
        err = Error::TRANSPORT_ERROR;
        os << "transport error";
        break;

      case OVPN_DEL_PEER_REASON_TEARDOWN:
        err = Error::TRANSPORT_ERROR;
        os << "peer deleted, id=" << peer_id << ", teardown";
        break;

      case OVPN_DEL_PEER_REASON_USERSPACE:
        // volountary delete - do not stop client
        OPENVPN_LOG("peer deleted, id=" << peer_id
                                        << ", requested by userspace");
        peer_id = OVPN_PEER_ID_UNDEF;
        return true;

      default:
        err = Error::TUN_HALT;
        os << "peer deleted, id=" << peer_id << ", reason=" << reason;
        break;
      }

      stop_();
      transport_parent->transport_error(err, os.str());
      break;
    }

    case OVPN_CMD_GET_PEER: {
      struct OvpnDcoPeer peer;
      buf.read(&peer, sizeof(peer));

      last_stats = SessionStats::DCOTransportSource::Data(peer.rx_bytes, peer.tx_bytes);
      break;
    }

    case -1:
      // consider all errors as fatal
      stop_();
      transport_parent->transport_error(Error::TUN_HALT, buf_to_string(buf));
      return false;
      break;

    default:
      OPENVPN_LOG("Unknown ovpn-dco cmd " << cmd);
      break;
    }

    return true;
  }

  virtual void transport_needs_send() override {
    transport_parent->transport_needs_send();
  }

  virtual void transport_error(const Error::Type fatal_err,
                               const std::string &err_text) override {
    transport_parent->transport_error(fatal_err, err_text);
  }

  virtual void proxy_error(const Error::Type fatal_err,
                           const std::string &err_text) override {
    transport_parent->proxy_error(fatal_err, err_text);
  }

  virtual bool transport_is_openvpn_protocol() override {
    return transport_parent->transport_is_openvpn_protocol();
  }

  virtual void transport_pre_resolve() override {
    transport_parent->transport_pre_resolve();
  }

  virtual void transport_wait_proxy() override {
    transport_parent->transport_wait_proxy();
  }

  virtual void transport_wait() override { transport_parent->transport_wait(); }

  virtual void transport_connecting() override {
    transport_parent->transport_connecting();
  }

  virtual bool is_keepalive_enabled() const override {
    return transport_parent->is_keepalive_enabled();
  }

  virtual void disable_keepalive(unsigned int &keepalive_ping,
                                 unsigned int &keepalive_timeout) override {
    transport_parent->disable_keepalive(keepalive_ping, keepalive_timeout);
  }

private:
  OvpnDcoClient(openvpn_io::io_context &io_context_arg,
                ClientConfig *config_arg, TransportClientParent *parent_arg)
      : Client(io_context_arg, config_arg, parent_arg) {
    TunBuilderBase *tb = config->builder;
    if (tb) {
      tb->tun_builder_new();
      // pipe fd which is used to communicate to kernel
      int fd = tb->tun_builder_dco_enable(config->dev_name);
      if (fd == -1) {
        stop_();
        transport_parent->transport_error(Error::TUN_IFACE_CREATE,
                                          "error creating ovpn-dco device");
        return;
      }

      pipe.reset(new openvpn_io::posix::stream_descriptor(io_context, fd));
      return;
    }

    std::ostringstream os;
    int res = TunNetlink::iface_new(os, config->dev_name, "ovpn-dco");
    if (res != 0) {
      stop_();
      transport_parent->transport_error(Error::TUN_IFACE_CREATE, os.str());
      return;
    }

    genl.reset(new GeNLImpl(
        io_context_arg, if_nametoindex(config_arg->dev_name.c_str()), this));
    genl->register_packet();
  }

  void handle_keepalive() {
    // since userspace doesn't know anything about presense or
    // absense of data channel traffic, ping should be handled in kernel
    if (transport_parent->is_keepalive_enabled()) {
      unsigned int keepalive_interval = 0;
      unsigned int keepalive_timeout = 0;

      // In addition to disabling userspace keepalive,
      // this call also assigns keepalive values to provided arguments
      // default keepalive values could be overwritten by config values,
      // which in turn could be overwritten by pushed options
      transport_parent->disable_keepalive(keepalive_interval,
                                          keepalive_timeout);

      // Allow overide of keepalive timeout
      if (config->ping_restart_override)
        keepalive_timeout = config->ping_restart_override;

      if (config->builder) {
        config->builder->tun_builder_dco_set_peer(peer_id, keepalive_interval,
                                                  keepalive_timeout);
      } else {
        // enable keepalive in kernel
        genl->set_peer(peer_id, keepalive_interval, keepalive_timeout);
      }
    }
  }

  void queue_read_pipe(PacketFrom *pkt) {
    if (!pkt) {
      pkt = new PacketFrom();
    }
    // good enough values for control channel packets
    pkt->buf.reset(512, 3072,
                   BufferAllocated::GROW | BufferAllocated::CONSTRUCT_ZERO |
                       BufferAllocated::DESTRUCT_ZERO);
    pipe->async_read_some(
        pkt->buf.mutable_buffer(),
        [self = Ptr(this),
         pkt = PacketFrom::SPtr(pkt)](const openvpn_io::error_code &error,
                                      const size_t bytes_recvd) mutable {
          if (!error) {
            pkt->buf.set_size(bytes_recvd);
            if (self->tun_read_handler(pkt->buf))
              self->queue_read_pipe(pkt.release());
          } else {
            if (!self->halt) {
              OPENVPN_LOG("ovpn-dco pipe read error: " << error.message());
              self->stop_();
              self->transport_parent->transport_error(Error::TUN_HALT,
                                                      error.message());
            }
          }
        });
  }

  virtual SessionStats::DCOTransportSource::Data dco_transport_stats_delta() override {
    if (halt) {
      /* retrieve the last stats update and erase it to avoid race conditions with other queries */
      SessionStats::DCOTransportSource::Data delta = last_delta;
      last_delta = SessionStats::DCOTransportSource::Data(0, 0);
      return delta;
    }

    update_peer_stats(peer_id, true);
    return last_delta;
  }

  // used to communicate to kernel via privileged process
  std::unique_ptr<openvpn_io::posix::stream_descriptor> pipe;

  GeNLImpl::Ptr genl;
  TransportClient::Ptr transport;
  SessionStats::DCOTransportSource::Data last_stats;
  SessionStats::DCOTransportSource::Data last_delta;
};
