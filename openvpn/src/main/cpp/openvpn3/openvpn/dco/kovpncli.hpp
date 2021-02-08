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

#if defined(USE_TUN_BUILDER)
#error kovpn does not work with tunbuilder
#endif

class KovpnClient : public Client,
                    public KoRekey::Receiver,
                    public SessionStats::DCOTransportSource
{
    friend class ClientConfig;

    typedef RCPtr<KovpnClient> Ptr;

#if defined(ENABLE_PG)
    typedef KoTun::Tun<KovpnClient *> TunImpl;
#else
    typedef KoTun::TunClient<KovpnClient *> TunImpl;
#endif

    // calls tun_read_handler and tun_error_handler
    friend TunImpl::Base;

public:
    // transport methods

    virtual bool transport_send_const(const Buffer &buf) override
    {
        return send(buf);
    }

    virtual bool transport_send(BufferAllocated &buf) override
    {
        return send(buf);
    }

    virtual void transport_start() override
    {
        if (halt)
            OPENVPN_THROW(dco_error, "transport_start called on halted instance");

        KoTun::DevConf devconf;

        if (config->transport.protocol.is_udp())
            devconf.dc.tcp = false;
        else if (config->transport.protocol.is_tcp())
            devconf.dc.tcp = true;
        else
            OPENVPN_THROW(dco_error, "protocol " << config->transport.protocol.str() << " not implemented");

        // config settings
        devconf.set_dev_name(config->dev_name);
        devconf.dc.max_peers = 1;
        devconf.dc.max_dev_queues = 1;
        devconf.dc.dev_tx_queue_len = 4096;
        devconf.dc.max_tun_queue_len = 4096;
        devconf.dc.max_tcp_send_queue_len = 64;
        devconf.dc.peer_lookup = OVPN_PEER_LOOKUP_NONE;
        devconf.dc.cpu_affinity = OVPN_CPU_AFFINITY_UNDEF;


        // create kovpn tun socket (implementation in kodevtun.hpp)
        impl.reset(new TunImpl(io_context,
                               devconf,
                               this,
                               config->transport.frame,
                               nullptr,
                               nullptr));

        // set kovpn stats hook
        config->transport.stats->dco_configure(this);

        // if trunking, set RPS/XPS on iface
        if (config->config_rps_xps)
            config->config_rps_xps->set(config->dev_name, devconf.dc.queue_index, config->tun.stop);

        if (devconf.dc.tcp)
            transport_start_tcp();
        else
            transport_start_udp();
    }

    virtual ~KovpnClient() override
    {
        stop_();
    }

    // tun methods

    virtual std::string tun_name() const override
    {
        if (impl)
            return impl->name();
        else
            return "UNDEF_DCO";
    }

    virtual void tun_start(const OptionList &opt,
                           TransportClient &transcli,
                           CryptoDCSettings &dc_settings) override
    {
        if (halt || !tun_parent)
            OPENVPN_THROW(dco_error, "tun_start called on halted/undefined instance");

        try
        {
            const IP::Addr server_addr = server_endpoint_addr();

            // get the iface name
            state->iface_name = config->dev_name;

            // notify parent
            tun_parent->tun_pre_tun_config();

            // parse pushed options
            TunBuilderCapture::Ptr po;

            po.reset(new TunBuilderCapture());
            TunBuilderBase *builder = po.get();

            TunProp::configure_builder(builder,
                                       state.get(),
                                       config->transport.stats.get(),
                                       server_addr,
                                       config->tun.tun_prop,
                                       opt,
                                       nullptr,
                                       false);

            if (po)
                OPENVPN_LOG("CAPTURED OPTIONS:" << std::endl
                                                << po->to_string());

#ifdef ENABLE_PG
            if (config->trunk_unit >= 0)
            {
                // trunk setup
                ovpn_peer_assign_route_id kri;
                std::memset(&kri, 0, sizeof(kri));
                kri.peer_id = peer_id;
                kri.route_id = config->trunk_unit;
                kri.allow_incoming = true;
                kri.snat_flags = OVPN_SNAT_DEFAULT_ON | OVPN_SNAT_REQUIRED;

                // SNAT via VPN IPv4 addresses received from server
                {
                    const TunBuilderCapture::RouteAddress *ra = po->vpn_ip(IP::Addr::V4);
                    if (ra)
                        kri.snat.a4 = IP::Addr(ra->address, "server-assigned-vpn4-addr", IP::Addr::V4).to_ipv4().to_in_addr();
                }

                // SNAT via VPN IPv6 addresses received from server
                {
                    const TunBuilderCapture::RouteAddress *ra = po->vpn_ip(IP::Addr::V6);
                    if (ra)
                        kri.snat.a6 = IP::Addr(ra->address, "server-assigned-vpn6-addr", IP::Addr::V4).to_ipv6().to_in6_addr();
                }

                // kovpn route ID setup
                KoTun::API::peer_assign_route_id(impl->native_handle(), &kri);
            }
            else
#endif // ENABLE_PG
            // po is defined when builder is nullptr
            {
                // add/remove command lists
                ActionList::Ptr add_cmds = new ActionList();
                remove_cmds.reset(new ActionListReversed());

                // configure tun properties
                std::vector<IP::Route> rtvec;

                // non-trunk setup
                TUN_LINUX::tun_config(state->iface_name, *po, &rtvec, *add_cmds,
                                      *remove_cmds, false);

                // Add routes to DCO implementation
                impl->peer_add_routes(peer_id, rtvec);

                // execute commands to bring up interface
                add_cmds->execute_log();
            }

            // Add a hook so ProtoContext will call back to
            // rekey() on rekey ops.
            dc_settings.set_factory(CryptoDCFactory::Ptr(new KoRekey::Factory(dc_settings.factory(), this, config->transport.frame)));

            // signal that we are connected
            tun_parent->tun_connected();
        }
        catch (const std::exception &e)
        {
            stop_();
            tun_parent->tun_error(Error::TUN_SETUP_FAILED, e.what());
        }
    }

    // KoRekey::Receiver methods

    virtual void rekey(const CryptoDCInstance::RekeyType rktype,
                       const KoRekey::Info &rkinfo) override
    {
        if (halt)
            return;

        KoRekey::KovpnKey key(rktype, rkinfo, peer_id, false);
        impl->peer_keys_reset(key());
        if (transport_parent->is_keepalive_enabled())
        {
            struct ovpn_peer_keepalive ka;

            // Disable userspace keepalive, get the userspace
            // keepalive parameters, and enable kovpn keepalive.
            ka.peer_id = peer_id;
            transport_parent->disable_keepalive(ka.keepalive_ping,
                                                ka.keepalive_timeout);

            // Allow overide of keepalive timeout
            if (config->ping_restart_override)
                ka.keepalive_timeout = config->ping_restart_override;

            // Modify the peer
            impl->peer_set_keepalive(&ka);
        }
    }

    virtual void explicit_exit_notify() override
    {
        impl->peer_xmit_explicit_exit_notify(peer_id);
    }

    virtual void stop_() override
    {
        if (!halt)
        {
            halt = true;
            config->transport.stats->dco_update(); // final update
            config->transport.stats->dco_configure(nullptr);
            if (remove_cmds)
                remove_cmds->execute_log();
            if (impl)
                impl->stop();
            if (proto)
                proto->close();
        }
    }

protected:
    KovpnClient(openvpn_io::io_context &io_context_arg,
                ClientConfig *config_arg,
                TransportClientParent *parent_arg)
        : Client(io_context_arg, config_arg, parent_arg) {}

    // start I/O on UDP socket
    virtual void start_impl_udp(const openvpn_io::error_code &error) override
    {
        if (!halt)
        {
            if (!error)
            {
                // attach UDP socket to kovpn
                peer_id = impl->peer_new_udp_client(udp().socket.native_handle(), 0, 0);

                // queue reads on tun
                impl->start(8); // parallel reads
                transport_parent->transport_connecting();
            }
            else
            {
                std::ostringstream os;
                os << "UDP connect error on '" << server_host << ':' << server_port << "' (" << udp().server_endpoint << "): " << error.message();
                config->transport.stats->error(Error::UDP_CONNECT_ERROR);
                stop_();
                transport_parent->transport_error(Error::UNDEF, os.str());
            }
        }
    }

    void tun_read_handler(KoTun::PacketFrom::SPtr &pfp) // called by TunImpl
    {
        if (halt)
            return;

        try
        {
            const struct ovpn_tun_head *th = (const struct ovpn_tun_head *)pfp->buf.read_alloc(sizeof(struct ovpn_tun_head));
            switch (th->type)
            {
            case OVPN_TH_TRANS_BY_PEER_ID:
            {
                if (peer_id < 0 || th->peer_id != static_cast<uint32_t>(peer_id))
                {
                    OPENVPN_LOG("dcocli: OVPN_TH_TRANS_BY_PEER_ID unrecognized peer_id=" << th->peer_id);
                    return;
                }

                transport_parent->transport_recv(pfp->buf);
                cc_rx_bytes += pfp->buf.size();
                break;
            }
            case OVPN_TH_NOTIFY_STATUS:
            {
                const struct ovpn_tun_head_status *thn = (const struct ovpn_tun_head_status *)th;

                if (peer_id < 0 || thn->head.peer_id != static_cast<uint32_t>(peer_id))
                {
                    OPENVPN_LOG("dcocli: OVPN_TH_NOTIFY_STATUS unrecognized peer_id=" << thn->head.peer_id);
                    return;
                }

                const bool stop = (thn->head.status != OVPN_STATUS_ACTIVE);
                OPENVPN_LOG("dcocli: status=" << int(thn->head.status) << " peer_id=" << peer_id << " rx_bytes=" << thn->rx_bytes << " tx_bytes=" << thn->tx_bytes); // fixme
                if (stop)
                    throw Exception("stop status=" + to_string(thn->head.status));
                break;
            }
            default:
                OPENVPN_LOG("dcocli: unknown ovpn_tun_head type=" << (int)th->type);
                break;
            }
        }
        catch (const std::exception &e)
        {
            const std::string msg = std::string("dcocli: tun_read_handler: ") + e.what();
            OPENVPN_LOG(msg);
            stop_();
            transport_parent->transport_error(Error::TRANSPORT_ERROR, msg);
        }
    }

    void tun_error_handler(const Error::Type errtype, // called by TunImpl
                           const openvpn_io::error_code *error)
    {
        OPENVPN_LOG("TUN error");
        stop_();
    }

    bool send(const Buffer &buf)
    {
        struct ovpn_tun_head head;
        std::memset(&head, 0, sizeof(head));
        head.type = OVPN_TH_TRANS_BY_PEER_ID;
        head.peer_id = peer_id;
        return impl->write_seq(AsioConstBufferSeq2(Buffer(reinterpret_cast<Buffer::type>(&head), sizeof(head), true),
                                                   buf));
    }

    UDP &udp()
    {
        return *static_cast<UDP *>(proto.get());
    }

    // override for SessionStats::DCOTransportSource
    virtual SessionStats::DCOTransportSource::Data dco_transport_stats_delta() override
    {
        if (impl)
        {
            struct ovpn_peer_status ops;
            ops.peer_id = peer_id;
            if (impl->peer_get_status(&ops))
            {
                const SessionStats::DCOTransportSource::Data data(ops.rx_bytes + cc_rx_bytes, ops.tx_bytes);
                const SessionStats::DCOTransportSource::Data delta = data - last_stats;
                last_stats = data;
                return delta;
            }
        }
        return SessionStats::DCOTransportSource::Data();
    }

    TunImpl::Ptr impl;
    SessionStats::DCOTransportSource::Data last_stats;
    __u64 cc_rx_bytes = 0;
};
