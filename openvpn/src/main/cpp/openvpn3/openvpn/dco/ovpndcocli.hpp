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

class OvpnDcoClient : public Client, public KoRekey::Receiver {
  friend class ClientConfig;
  friend class GeNL;

  typedef RCPtr<OvpnDcoClient> Ptr;
  typedef GeNL<OvpnDcoClient *> GeNLImpl;

  struct PacketFrom {
    typedef std::unique_ptr<PacketFrom> SPtr;
    BufferAllocated buf;
  };

public:
  virtual void tun_start(const OptionList &opt, TransportClient &transcli,
                         CryptoDCSettings &dc_settings) override {
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
                            *remove_cmds, true);

      // execute commands to bring up interface
      add_cmds->execute_log();
    }

    // Add a hook so ProtoContext will call back to
    // rekey() on rekey ops.
    dc_settings.set_factory(CryptoDCFactory::Ptr(new KoRekey::Factory(
        dc_settings.factory(), this, config->transport.frame)));

    // signal that we are connected
    tun_parent->tun_connected();
  }

  virtual std::string tun_name() const override { return "ovpn-dco"; }

  virtual void transport_start() override { transport_start_udp(); }

  virtual bool transport_send_const(const Buffer &buf) override {
    return send(buf);
  }

  virtual bool transport_send(BufferAllocated &buf) override {
    return send(buf);
  }

  bool send(const Buffer &buf) {
    if (config->builder) {
      pipe->write_some(buf.const_buffer());
    } else {
      genl->send_data(buf.c_data(), buf.size());
    }
    return true;
  }

  virtual void start_impl_udp(const openvpn_io::error_code &error) override {
    if (halt)
      return;

    if (!error) {
      auto &sock = udp().socket;
      auto local = sock.local_endpoint();
      auto remote = sock.remote_endpoint();

      TunBuilderBase *tb = config->builder;
      if (tb) {
        tb->tun_builder_new();
        // pipe fd which is used to communicate to kernel
        int fd =
            tb->tun_builder_dco_enable(sock.native_handle(), config->dev_name);
        if (fd == -1) {
          stop_();
          transport_parent->transport_error(Error::TUN_IFACE_CREATE,
                                            "error creating ovpn-dco device");
          return;
        }
        pipe.reset(new openvpn_io::posix::stream_descriptor(io_context, fd));
        tb->tun_builder_dco_new_peer(local.address().to_string(), local.port(),
                                     remote.address().to_string(),
                                     remote.port());

        queue_read_pipe(nullptr);

        transport_parent->transport_connecting();
      } else {
        std::ostringstream os;
        int res = TunNetlink::iface_new(os, config->dev_name, "ovpn-dco");
        if (res != 0) {
          stop_();
          transport_parent->transport_error(Error::TUN_IFACE_CREATE, os.str());
        } else {
          genl.reset(new GeNLImpl(
              io_context, if_nametoindex(config->dev_name.c_str()), this));

          genl->start_vpn(sock.native_handle());
          genl->new_peer(local, remote);

          transport_parent->transport_connecting();
        }
      }
    } else {
      std::ostringstream os;
      os << "UDP connect error on '" << server_host << ':' << server_port
         << "' (" << udp().server_endpoint << "): " << error.message();
      config->transport.stats->error(Error::UDP_CONNECT_ERROR);
      stop_();
      transport_parent->transport_error(Error::UNDEF, os.str());
    }
  }

  virtual void stop_() override {
    if (!halt) {
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
      genl->swap_keys();
      break;

    case CryptoDCInstance::DEACTIVATE_SECONDARY:
      genl->del_key(OVPN_KEY_SLOT_SECONDARY);
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
      tb->tun_builder_dco_swap_keys();
      break;

    case CryptoDCInstance::DEACTIVATE_SECONDARY:
      tb->tun_builder_dco_del_key(OVPN_KEY_SLOT_SECONDARY);
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
      stop_();
      int8_t reason = -1;
      buf.read(&reason, sizeof(reason));
      switch (reason) {
      case OVPN_DEL_PEER_REASON_EXPIRED:
        transport_parent->transport_error(Error::TRANSPORT_ERROR,
                                          "keepalive timeout");
        break;

      default:
        std::ostringstream os;
        os << "peer deleted, reason " << reason;
        transport_parent->transport_error(Error::TUN_HALT, os.str());
        break;
      }
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

private:
  OvpnDcoClient(openvpn_io::io_context &io_context_arg,
                ClientConfig *config_arg, TransportClientParent *parent_arg)
      : Client(io_context_arg, config_arg, parent_arg) {}

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
        config->builder->tun_builder_dco_set_peer(keepalive_interval,
                                                  keepalive_timeout);
      } else {
        // enable keepalive in kernel
        genl->set_peer(keepalive_interval, keepalive_timeout);
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

  // used to communicate to kernel via privileged process
  std::unique_ptr<openvpn_io::posix::stream_descriptor> pipe;

  GeNLImpl::Ptr genl;
};