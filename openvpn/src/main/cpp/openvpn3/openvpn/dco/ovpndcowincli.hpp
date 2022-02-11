//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2021 OpenVPN Inc.
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

class OvpnDcoWinClient : public Client, public KoRekey::Receiver {
  friend class ClientConfig;
  typedef RCPtr<OvpnDcoWinClient> Ptr;

public:
  static bool available() {
    std::string path;
    TunWin::Util::TapNameGuidPair tap;
    TunWin::Type tun_type(TunWin::OvpnDco);
    TunWin::Util::TapNameGuidPairList guids(tun_type);
    Win::ScopedHANDLE hnd(TunWin::Util::tap_open(tun_type, guids, path, tap));
    return hnd.defined();
  }

  void transport_start() override {
    if (handle_)
      return;

    halt = false;
    RemoteList& rl = *config->transport.remote_list;
    if (rl.endpoint_available(&server_host, &server_port, &proto_))
      {
	start_impl_();
      }
    else
      {
	transport_parent->transport_pre_resolve();
	async_resolve_name(server_host, server_port);
      }
  }

  bool transport_send_const(const Buffer &buf) override {
    return send_(buf);
  }

  bool transport_send(BufferAllocated &buf) override {
    return send_(buf);
  }

  void tun_start(const OptionList& opt, TransportClient& transcli,
                 CryptoDCSettings& dc_settings) override {
    halt = false;

    tun_parent->tun_pre_tun_config();

    // parse pushed options
    po_ = new TunBuilderCapture();
    TunProp::configure_builder(po_.get(), state.get(), nullptr,
                               transcli.server_endpoint_addr(),
                               config->tun.tun_prop, opt, nullptr, false);
    OPENVPN_LOG("CAPTURED OPTIONS:" << std::endl << po_->to_string());

    // Add a hook so ProtoContext will call back to rekey() on rekey ops.
    dc_settings.set_factory(CryptoDCFactory::Ptr(new KoRekey::Factory(
        dc_settings.factory(), this, config->transport.frame)));

    tun_parent->tun_connected();
  }

  std::string tun_name() const override { return "ovpn-dco-win"; }

  IP::Addr server_endpoint_addr() const override {
    return IP::Addr::from_asio(endpoint_.address());
  }

  Protocol transport_protocol() const override { return proto_; }

  void rekey(const CryptoDCInstance::RekeyType rktype,
             const KoRekey::Info &rkinfo) override {
    if (halt)
      return;

    KoRekey::OvpnDcoKey key(rktype, rkinfo);

    switch (rktype) {
    case CryptoDCInstance::ACTIVATE_PRIMARY:
      add_keepalive_();
      add_crypto_(rktype, key());
      start_vpn_();
      break;

    case CryptoDCInstance::NEW_SECONDARY:
      add_crypto_(rktype, key());
      break;

    case CryptoDCInstance::PRIMARY_SECONDARY_SWAP:
      swap_keys_();
      break;

    case CryptoDCInstance::DEACTIVATE_SECONDARY:
      break;

    case CryptoDCInstance::DEACTIVATE_ALL:
      break;

    default:
      OPENVPN_LOG("ovpndcocli: unknown rekey type: " << rktype);
      break;
    }
  }

protected:
  OvpnDcoWinClient(openvpn_io::io_context &io_context,
                   ClientConfig *config, TransportClientParent *parent)
      : Client(io_context, config, parent) {}

  void resolve_callback(const openvpn_io::error_code& error,
                        results_type results) override {
    if (halt)
      return;

    if (error) {
      std::ostringstream os;
      os << "DNS resolve error on '" << server_host << "' for "
	  << proto_.str() << " session: " << error.message();

      stop();
      config->transport.stats->error(Error::RESOLVE_ERROR);
      transport_parent->transport_error(Error::UNDEF, os.str());
    } else {
      config->transport.remote_list->set_endpoint_range(results);
      start_impl_();
    }
  }

  void start_impl_() {
    if (halt)
      return;

    // create new tun setup object
    tun_setup_ = config->tun.new_setup_obj(io_context, config->allow_local_dns_resolvers);

    std::ostringstream os;
    HANDLE th = tun_setup_->get_handle(os);
    OPENVPN_LOG_STRING(os.str());
    if (th == INVALID_HANDLE_VALUE)
      return;

    handle_ = std::make_unique<asio::windows::stream_handle>(io_context, th);

    tun_setup_->confirm();
    tun_setup_->set_service_fail_handler([self=Ptr(this)]() {
	if (!self->halt)
	  self->tun_parent->tun_error(Error::TUN_IFACE_DISABLED, "service failure");
      }
    );

    config->transport.remote_list->get_endpoint(endpoint_);
    add_peer_([self=Ptr(this)]() {
      if (!self->halt) {
	self->transport_parent->transport_connecting();
	self->queue_read_();
      }
    });
  }

  void queue_read_() {
    buf_.reset(0, 2048, 0);

    handle_->async_read_some(
      buf_.mutable_buffer_clamp(),
      [self = Ptr(this)](const openvpn_io::error_code &error,
			 const size_t bytes_recvd) {
	if (!error) {
	  self->buf_.set_size(bytes_recvd);
	  self->transport_parent->transport_recv(self->buf_);
	  self->queue_read_();
	} else if (!self->halt) {
	  self->stop_();
	  self->transport_parent->transport_error(Error::TRANSPORT_ERROR,
						  error.message());
	}
      }
    );
  }

  bool send_(const Buffer &buf) {
    openvpn_io::error_code error;
    handle_->write_some(buf.const_buffer(), error);
    if (error) {
      transport_parent->transport_error(Error::TRANSPORT_ERROR,
					error.message());
      stop_();
      return false;
    }
    return true;
  }

  void stop_() override {
    if (!halt) {
      std::ostringstream os;
      halt = true;
      async_resolve_cancel();
      tun_setup_->destroy(os);
      handle_.reset();
      OPENVPN_LOG_STRING(os.str());
    }
  }

  template <typename CB>
  void add_peer_(CB complete) {
    OVPN_NEW_PEER peer = {};

    peer.Proto = proto_.is_tcp() ? OVPN_PROTO_TCP : OVPN_PROTO_UDP;

    openvpn_io::ip::address addr = endpoint_.address();
    if (addr.is_v4()) {
      peer.Remote.Addr4.sin_family = AF_INET;
      peer.Remote.Addr4.sin_port = ::htons(endpoint_.port());
      std::memcpy(&peer.Remote.Addr4.sin_addr, addr.to_v4().to_bytes().data(),
		  sizeof(peer.Remote.Addr4.sin_addr));
      peer.Local.Addr4.sin_family = peer.Remote.Addr4.sin_family;
      peer.Local.Addr4.sin_port = peer.Remote.Addr4.sin_port;
    } else {
      peer.Remote.Addr6.sin6_family = AF_INET6;
      peer.Remote.Addr6.sin6_port = ::htons(endpoint_.port());
      std::memcpy(&peer.Remote.Addr6.sin6_addr, addr.to_v6().to_bytes().data(),
		  sizeof(peer.Remote.Addr6.sin6_addr));
      peer.Local.Addr6.sin6_family = peer.Remote.Addr6.sin6_family;
      peer.Local.Addr6.sin6_port = peer.Remote.Addr6.sin6_port;
    }

    openvpn_io::windows::overlapped_ptr ov {io_context,
      [self=Ptr(this), complete](const openvpn_io::error_code& ec,
				 std::size_t len) {
	if (self->halt)
	  return;
	if (!ec)
	  complete();
	else {
	  std::ostringstream errmsg;
	  errmsg << "TCP connection error: " << ec.message();
	  self->config->transport.stats->error(Error::TCP_CONNECT_ERROR);
	  self->transport_parent->transport_error(Error::UNDEF, errmsg.str());
	  self->stop_();
	}
      }
    };

    const DWORD ec = dco_ioctl_(OVPN_IOCTL_NEW_PEER, &peer, sizeof(peer), &ov);
    if (ec == ERROR_SUCCESS)
      complete();
    else if (ec != ERROR_IO_PENDING) {
      std::ostringstream errmsg;
      errmsg << "failed to connect '" << server_host << "' " << endpoint_;
      config->transport.stats->error(Error::TCP_CONNECT_ERROR);
      transport_parent->transport_error(Error::UNDEF, errmsg.str());
      stop_();
    }
  }

  void add_keepalive_() {
    if (!transport_parent->is_keepalive_enabled())
      return;

    unsigned int keepalive_interval = 0;
    unsigned int keepalive_timeout = 0;

    // since userspace doesn't know anything about presense or
    // absense of data channel traffic, ping should be handled in kernel
    transport_parent->disable_keepalive(keepalive_interval,
					keepalive_timeout);

    if (config->ping_restart_override)
      keepalive_timeout = config->ping_restart_override;

    // enable keepalive in kernel
    OVPN_SET_PEER peer = {};
    peer.KeepaliveInterval = static_cast<LONG>(keepalive_interval);
    peer.KeepaliveTimeout  = static_cast<LONG>(keepalive_timeout);

    dco_ioctl_(OVPN_IOCTL_SET_PEER, &peer, sizeof(peer));
  }

  void add_crypto_(const CryptoDCInstance::RekeyType type,
                   const KoRekey::KeyConfig *kc) {
    if (kc->cipher_alg != OVPN_CIPHER_ALG_AES_GCM) {
      OPENVPN_LOG("unsupported cipher for DCO");
      throw dco_error();
    }

    OVPN_CRYPTO_DATA data;
    ZeroMemory(&data, sizeof(data));

    const size_t nonce_tail_len = sizeof(kc->encrypt.nonce_tail);

    data.Encrypt.KeyLen = kc->encrypt.cipher_key_size;
    std::memcpy(data.Encrypt.Key, kc->encrypt.cipher_key, data.Encrypt.KeyLen);
    std::memcpy(data.Encrypt.NonceTail, kc->encrypt.nonce_tail, nonce_tail_len);

    data.Decrypt.KeyLen = kc->decrypt.cipher_key_size;
    std::memcpy(data.Decrypt.Key, kc->decrypt.cipher_key, data.Decrypt.KeyLen);
    std::memcpy(data.Decrypt.NonceTail, kc->decrypt.nonce_tail, nonce_tail_len);

    data.KeyId = kc->key_id;
    data.PeerId = kc->remote_peer_id;
    data.CipherAlg = OVPN_CIPHER_ALG::OVPN_CIPHER_ALG_AES_GCM;
    data.KeySlot = (type == CryptoDCInstance::ACTIVATE_PRIMARY
      ? OVPN_KEY_SLOT::OVPN_KEY_SLOT_PRIMARY
      : OVPN_KEY_SLOT::OVPN_KEY_SLOT_SECONDARY);

    dco_ioctl_(OVPN_IOCTL_NEW_KEY, &data, sizeof(data));
  }

  void start_vpn_() {
    dco_ioctl_(OVPN_IOCTL_START_VPN);

    std::ostringstream os;
    tun_setup_->establish(*po_, Win::module_name(), NULL, os, NULL);
    OPENVPN_LOG_STRING(os.str());

  }

  void swap_keys_() { dco_ioctl_(OVPN_IOCTL_SWAP_KEYS); }

  DWORD dco_ioctl_(DWORD code, LPVOID data = NULL, DWORD size = 0,
		   openvpn_io::windows::overlapped_ptr* ov = nullptr) {
    static const std::map<const DWORD, const char*> code_str {
      { OVPN_IOCTL_NEW_PEER,  "OVPN_IOCTL_NEW_PEER"  },
      { OVPN_IOCTL_GET_STATS, "OVPN_IOCTL_GET_STATS" },
      { OVPN_IOCTL_NEW_KEY,   "OVPN_IOCTL_NEW_KEY"   },
      { OVPN_IOCTL_SWAP_KEYS, "OVPN_IOCTL_SWAP_KEYS" },
      { OVPN_IOCTL_SET_PEER,  "OVPN_IOCTL_SET_PEER"  },
      { OVPN_IOCTL_START_VPN, "OVPN_IOCTL_START_VPN" },
    };

    HANDLE th(handle_->native_handle());
    LPOVERLAPPED ov_ = (ov ? ov->get() : NULL);
    if (!DeviceIoControl(th, code, data, size, NULL, 0, NULL, ov_)) {
      const DWORD error_code = GetLastError();
      if (ov) {
	if (error_code == ERROR_IO_PENDING) {
	  ov->release();
	  return error_code;
	}
	openvpn_io::error_code error(error_code, openvpn_io::system_category());
	ov->complete(error, 0);
      }

      OPENVPN_LOG("DeviceIoControl(" << code_str.at(code) << ")"
		  << " failed with code " << error_code);
      throw dco_error();
    }
    return ERROR_SUCCESS;
  }

  std::unique_ptr<openvpn_io::windows::stream_handle> handle_;
  TunBuilderCapture::Ptr po_;
  TunWin::SetupBase::Ptr tun_setup_;
  BufferAllocated buf_;
  Protocol proto_;
  openvpn_io::ip::udp::endpoint endpoint_;
};
