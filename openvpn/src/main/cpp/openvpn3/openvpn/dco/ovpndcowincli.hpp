//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2021-2022 OpenVPN Inc.
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

class OvpnDcoWinClient : public Client,
                         public KoRekey::Receiver,
                         public SessionStats::DCOTransportSource
{
    friend class ClientConfig;
    typedef RCPtr<OvpnDcoWinClient> Ptr;

  public:
    static bool available()
    {
        std::string path;
        TunWin::Util::TapNameGuidPair tap;
        TunWin::Type tun_type(TunWin::OvpnDco);
        TunWin::Util::TapNameGuidPairList guids(tun_type);
        Win::ScopedHANDLE hnd(TunWin::Util::tap_open(tun_type, guids, path, tap));
        return hnd.defined();
    }

    void transport_start() override
    {
        if (tun_persist)
            return;

        halt = false;
        RemoteList &rl = *config->transport.remote_list;
        if (rl.endpoint_available(&server_host, &server_port, &proto_))
        {
            // defer to let pending IO finish
            openvpn_io::post(io_context, [self = Ptr(this)]()
                             {
	  OPENVPN_ASYNC_HANDLER;
	  self->start_impl_(); });
        }
        else
        {
            transport_parent->transport_pre_resolve();
            async_resolve_name(server_host, server_port);
        }
    }

    bool transport_send_const(const Buffer &buf) override
    {
        if (halt)
            return false;
        return send_(buf);
    }

    bool transport_send(BufferAllocated &buf) override
    {
        return send_(buf);
    }

    void tun_start(const OptionList &opt,
                   TransportClient &transcli,
                   CryptoDCSettings &dc_settings) override
    {
        halt = false;

        const IP::Addr server_addr = transcli.server_endpoint_addr();

        // Check if persisted tun session matches properties of to-be-created session
        if (tun_persist->use_persisted_tun(server_addr, config->tun.tun_prop, opt))
        {
            state = tun_persist->state().state;

            OPENVPN_LOG("TunPersist: reused tun context");
        }
        else
        {
            // notify parent
            tun_parent->tun_pre_tun_config();

            OPENVPN_LOG("TunPersist: clear tun settings");
            std::ostringstream os;
            tun_persist->close_destructor();

            dco_ioctl_(OVPN_IOCTL_START_VPN);

            // parse pushed options
            TunBuilderCapture::Ptr po(new TunBuilderCapture());
            TunProp::configure_builder(po.get(),
                                       state.get(),
                                       nullptr,
                                       transcli.server_endpoint_addr(),
                                       config->tun.tun_prop,
                                       opt,
                                       nullptr,
                                       false);
            OPENVPN_LOG("CAPTURED OPTIONS:" << std::endl
                                            << po->to_string());

            tun_setup_->establish(*po, Win::module_name(), NULL, os, NULL);
            OPENVPN_LOG_STRING(os.str());

            // persist tun settings state
            if (tun_persist->persist_tun_state(handle_(),
                                               {state, tun_setup_->get_adapter_state()}))
                OPENVPN_LOG("TunPersist: saving tun context:" << std::endl
                                                              << tun_persist->options());

            handle_.release();

            // enable tun_setup destructor
            tun_persist->add_destructor(tun_setup_);

            // arm fail handler which is invoked when service process exits
            set_service_fail_handler();
        }

        set_keepalive_();

        // Add a hook so ProtoContext will call back to rekey() on rekey ops.
        dc_settings.set_factory(CryptoDCFactory::Ptr(new KoRekey::Factory(
            dc_settings.factory(), this, config->transport.frame)));

        tun_parent->tun_connected();
    }

    std::string tun_name() const override
    {
        return "ovpn-dco-win";
    }

    void set_service_fail_handler()
    {
        tun_setup_->set_service_fail_handler([self = Ptr(this)]()
                                             {
      if (!self->halt)
	self->tun_parent->tun_error(Error::TUN_IFACE_DISABLED, "service failure"); });
    }

    void adjust_mss(int mss) override
    {
        OVPN_SET_PEER peer{-1, -1, mss};
        dco_ioctl_(OVPN_IOCTL_SET_PEER, &peer, sizeof(peer));
    }

    IP::Addr server_endpoint_addr() const override
    {
        return IP::Addr::from_asio(endpoint_.address());
    }

    Protocol transport_protocol() const override
    {
        return proto_;
    }

    void rekey(const CryptoDCInstance::RekeyType rktype,
               const KoRekey::Info &rkinfo) override
    {
        if (halt)
            return;

        KoRekey::OvpnDcoKey key(rktype, rkinfo);

        switch (rktype)
        {
        case CryptoDCInstance::ACTIVATE_PRIMARY:
            add_crypto_(rktype, key());
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
                     ClientConfig *config,
                     TransportClientParent *parent)
        : Client(io_context, config, parent)
    {
    }

    void resolve_callback(const openvpn_io::error_code &error,
                          results_type results) override
    {
        if (halt)
            return;

        if (error)
        {
            std::ostringstream os;
            os << "DNS resolve error on '" << server_host << "' for "
               << proto_.str() << " session: " << error.message();

            stop();
            config->transport.stats->error(Error::RESOLVE_ERROR);
            transport_parent->transport_error(Error::UNDEF, os.str());
        }
        else
        {
            config->transport.remote_list->set_endpoint_range(results);
            start_impl_();
        }
    }

    TunWin::ScopedTAPStream handle_;

    void start_impl_()
    {
        if (halt)
            return;

        // create new tun setup object
        tun_setup_ = config->tun.new_setup_obj(io_context, config->allow_local_dns_resolvers);

        if (config->tun.tun_persist)
            tun_persist = config->tun.tun_persist; // long-term persistent
        else
            tun_persist.reset(new TunWin::DcoTunPersist(false, TunWrapObjRetain::NO_RETAIN, nullptr)); // short-term

        if (!tun_persist->obj_defined())
        {
            std::ostringstream os;
            HANDLE th = tun_setup_->get_handle(os);
            OPENVPN_LOG_STRING(os.str());
            if (th == INVALID_HANDLE_VALUE)
                return;

            handle_.reset(new TunWin::TAPStream(io_context, th));
        }
        else
        {
            tun_setup_->set_adapter_state(tun_persist->state().adapter_state);
        }

        tun_setup_->confirm();

        config->transport.remote_list->get_endpoint(endpoint_);

        if (config->transport.socket_protect)
        {
            /* socket descriptor is not used on dco-win */
            if (!config->transport.socket_protect->socket_protect(-1, server_endpoint_addr()))
            {
                config->transport.stats->error(Error::SOCKET_PROTECT_ERROR);
                stop();
                transport_parent->transport_error(Error::UNDEF, "socket_protect error (dco-win)");
                return;
            }
        }

        add_peer_([self = Ptr(this)]()
                  {
      if (!self->halt) {
	self->transport_parent->transport_connecting();
	/* above line might set halt to true in case of TCP reconnect */
	if (!self->halt)
	  self->queue_read_();
      } });

        config->transport.stats->dco_configure(this);
    }

    void queue_read_()
    {
        buf_.reset(0, 2048, 0);

        get_handle()->async_read_some(
            buf_.mutable_buffer_clamp(),
            [self = Ptr(this)](const openvpn_io::error_code &error,
                               const size_t bytes_recvd)
            {
            if (self->halt)
                return;
            if (!error)
            {
                self->buf_.set_size(bytes_recvd);
                self->transport_parent->transport_recv(self->buf_);
                if (!self->halt)
                    self->queue_read_();
            }
            else if (!self->halt)
            {
                self->stop_();
                self->transport_parent->transport_error(Error::TRANSPORT_ERROR,
                                                        error.message());
            }
            });
    }

    bool send_(const Buffer &buf)
    {
        openvpn_io::error_code error;
        get_handle()->write_some(buf.const_buffer(), error);
        if (error)
        {
            transport_parent->transport_error(Error::TRANSPORT_ERROR,
                                              error.message());
            stop_();
            return false;
        }
        return true;
    }

    void stop_() override
    {
        if (!halt)
        {
            get_stats_();

            halt = true;
            async_resolve_cancel();

            try
            {
                dco_ioctl_(OVPN_IOCTL_DEL_PEER);
            }
            catch (const ErrorCode &e)
            {
                // this is fine - stopped before we got driver handle
                if (e.code() != Error::TUN_SETUP_FAILED)
                    throw e;
            }

            handle_.close();

            tun_persist.reset();
        }
    }

    template <typename CB>
    void add_peer_(CB complete)
    {
        OVPN_NEW_PEER peer = {};

        peer.Proto = proto_.is_tcp() ? OVPN_PROTO_TCP : OVPN_PROTO_UDP;

        openvpn_io::ip::address addr = endpoint_.address();
        if (addr.is_v4())
        {
            peer.Remote.Addr4.sin_family = AF_INET;
            peer.Remote.Addr4.sin_port = ::htons(endpoint_.port());
            std::memcpy(&peer.Remote.Addr4.sin_addr,
                        addr.to_v4().to_bytes().data(),
                        sizeof(peer.Remote.Addr4.sin_addr));
            peer.Local.Addr4.sin_family = peer.Remote.Addr4.sin_family;
            peer.Local.Addr4.sin_port = 0;
        }
        else
        {
            peer.Remote.Addr6.sin6_family = AF_INET6;
            peer.Remote.Addr6.sin6_port = ::htons(endpoint_.port());
            std::memcpy(&peer.Remote.Addr6.sin6_addr,
                        addr.to_v6().to_bytes().data(),
                        sizeof(peer.Remote.Addr6.sin6_addr));
            peer.Local.Addr6.sin6_family = peer.Remote.Addr6.sin6_family;
            peer.Local.Addr6.sin6_port = 0;
        }

        openvpn_io::windows::overlapped_ptr ov{io_context,
                                               [self = Ptr(this), complete](const openvpn_io::error_code &ec,
                                                                            std::size_t len)
                                               {
            if (self->halt)
                return;
            if (!ec)
                complete();
            else
            {
                std::ostringstream errmsg;
                errmsg << "TCP connection error: " << ec.message();
                self->config->transport.stats->error(Error::TCP_CONNECT_ERROR);
                self->transport_parent->transport_error(Error::UNDEF, errmsg.str());
                self->stop_();
            }
                                               }};

        const DWORD ec = dco_ioctl_(OVPN_IOCTL_NEW_PEER, &peer, sizeof(peer), NULL, 0, &ov);
        if (ec == ERROR_SUCCESS)
            complete();
        else if (ec != ERROR_IO_PENDING)
        {
            std::ostringstream errmsg;
            errmsg << "failed to connect '" << server_host << "' " << endpoint_;
            config->transport.stats->error(Error::TCP_CONNECT_ERROR);
            transport_parent->transport_error(Error::UNDEF, errmsg.str());
            stop_();
        }
    }

    void set_keepalive_()
    {
        if (!transport_parent->is_keepalive_enabled())
            return;

        unsigned int keepalive_interval = 0;
        unsigned int keepalive_timeout = 0;

        // since userspace doesn't know anything about presense or
        // absense of data channel traffic, ping should be handled in kernel
        transport_parent->disable_keepalive(keepalive_interval, keepalive_timeout);

        if (config->ping_restart_override)
            keepalive_timeout = config->ping_restart_override;

        // enable keepalive in kernel
        OVPN_SET_PEER peer{static_cast<LONG>(keepalive_interval), static_cast<LONG>(keepalive_timeout), -1};
        dco_ioctl_(OVPN_IOCTL_SET_PEER, &peer, sizeof(peer));
    }

    void add_crypto_(const CryptoDCInstance::RekeyType type,
                     const KoRekey::KeyConfig *kc)
    {
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
        data.CipherAlg = (OVPN_CIPHER_ALG)kc->cipher_alg;
        data.KeySlot = (type == CryptoDCInstance::ACTIVATE_PRIMARY
                            ? OVPN_KEY_SLOT::OVPN_KEY_SLOT_PRIMARY
                            : OVPN_KEY_SLOT::OVPN_KEY_SLOT_SECONDARY);

        dco_ioctl_(OVPN_IOCTL_NEW_KEY, &data, sizeof(data));
    }

    void swap_keys_()
    {
        dco_ioctl_(OVPN_IOCTL_SWAP_KEYS);
    }

    void get_stats_()
    {
        const SessionStats::DCOTransportSource::Data old_stats = last_stats;

        try
        {
            OVPN_STATS stats{0};
            DWORD res = dco_ioctl_(OVPN_IOCTL_GET_STATS, 0, 0, &stats, sizeof(stats));
            if (res == ERROR_SUCCESS)
            {
                last_stats = SessionStats::DCOTransportSource::Data(stats.TransportBytesReceived, stats.TransportBytesSent, stats.TunBytesReceived, stats.TunBytesSent);
            }
        }
        catch (const ErrorCode &e)
        {
            // no device handle - ignore
            if (e.code() != Error::TUN_SETUP_FAILED)
                throw e;
        }

        last_delta = last_stats - old_stats;
    }

    DWORD dco_ioctl_(DWORD code,
                     LPVOID in_buf = NULL,
                     DWORD in_buf_size = 0,
                     LPVOID out_buf = NULL,
                     DWORD out_buf_size = 0,
                     openvpn_io::windows::overlapped_ptr *ov = nullptr)
    {
        static const std::map<const DWORD, const char *> code_str{
            {OVPN_IOCTL_NEW_PEER, "OVPN_IOCTL_NEW_PEER"},
            {OVPN_IOCTL_GET_STATS, "OVPN_IOCTL_GET_STATS"},
            {OVPN_IOCTL_NEW_KEY, "OVPN_IOCTL_NEW_KEY"},
            {OVPN_IOCTL_SWAP_KEYS, "OVPN_IOCTL_SWAP_KEYS"},
            {OVPN_IOCTL_SET_PEER, "OVPN_IOCTL_SET_PEER"},
            {OVPN_IOCTL_START_VPN, "OVPN_IOCTL_START_VPN"},
            {OVPN_IOCTL_DEL_PEER, "OVPN_IOCTL_DEL_PEER"},
        };

        auto handle = get_handle();
        if (handle == nullptr)
            throw ErrorCode(Error::TUN_SETUP_FAILED, false, "no device handle");

        HANDLE th(handle->native_handle());
        LPOVERLAPPED ov_ = (ov ? ov->get() : NULL);
        if (!DeviceIoControl(th, code, in_buf, in_buf_size, out_buf, out_buf_size, NULL, ov_))
        {
            const DWORD error_code = GetLastError();
            if (ov)
            {
                if (error_code == ERROR_IO_PENDING)
                {
                    ov->release();
                    return error_code;
                }
                openvpn_io::error_code error(error_code, openvpn_io::system_category());
                ov->complete(error, 0);
            }

            std::ostringstream os;
            os << "DeviceIoControl(" << code_str.at(code) << ")"
               << " failed with code " << error_code;
            throw ErrorCode(Error::TUN_SETUP_FAILED, true, os.str());
        }
        return ERROR_SUCCESS;
    }

    TunWin::TAPStream *get_handle()
    {
        if (tun_persist && tun_persist->obj_defined())
            return tun_persist->obj();
        else if (handle_.defined())
            return handle_();

        return nullptr;
    }

    virtual SessionStats::DCOTransportSource::Data dco_transport_stats_delta() override
    {
        if (halt)
        {
            /* retrieve the last stats update and erase it to avoid race conditions with other queries */
            SessionStats::DCOTransportSource::Data delta = last_delta;
            last_delta = SessionStats::DCOTransportSource::Data();
            return delta;
        }

        get_stats_();
        return last_delta;
    }

    TunWin::SetupBase::Ptr tun_setup_;
    BufferAllocated buf_;
    Protocol proto_;
    openvpn_io::ip::udp::endpoint endpoint_;

    TunWin::DcoTunPersist::Ptr tun_persist;

    SessionStats::DCOTransportSource::Data last_stats;
    SessionStats::DCOTransportSource::Data last_delta;
};
