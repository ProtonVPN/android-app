//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2018 OpenVPN Inc.
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

#ifndef OPENVPN_TRANSPORT_DCO_DCOCLI_H
#define OPENVPN_TRANSPORT_DCO_DCOCLI_H

#include <vector>
#include <memory>
#include <sstream>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/to_string.hpp>
#include <openvpn/buffer/asiobuf.hpp>
#include <openvpn/time/time.hpp>
#include <openvpn/transport/client/transbase.hpp>
#include <openvpn/tun/client/tunbase.hpp>
#include <openvpn/tun/builder/capture.hpp>
#include <openvpn/tun/linux/client/tuncli.hpp>
#include <openvpn/transport/dco.hpp>
#include <openvpn/kovpn/kovpn.hpp>
#include <openvpn/kovpn/kodev.hpp>
#include <openvpn/kovpn/korekey.hpp>
#include <openvpn/kovpn/kostats.hpp>
#include <openvpn/linux/procfs.hpp>
#include <openvpn/dco/ipcollbase.hpp>

#ifdef ENABLE_PG
#include <openvpn/kovpn/kodevtun.hpp>
#include <openvpn/kovpn/ipcoll.hpp>
#endif

// client-side DCO (Data Channel Offload) module for Linux/kovpn

namespace openvpn {
  namespace DCOTransport {

    OPENVPN_EXCEPTION(dco_error);

    class ClientConfig : public DCO,
			 public TransportClientFactory,
			 public TunClientFactory
    {
    public:
      typedef RCPtr<ClientConfig> Ptr;

      std::string dev_name;

      DCO::TransportConfig transport;
      DCO::TunConfig tun;

      int trunk_unit = -1;

      virtual TunClientFactory::Ptr new_tun_factory(const DCO::TunConfig& conf, const OptionList& opt) override
      {
	tun = conf;

	// set a default MTU
	if (!tun.tun_prop.mtu)
	  tun.tun_prop.mtu = 1500;

	// parse "dev" option
	{
	  const Option* dev = opt.get_ptr("dev");
	  if (dev)
	    dev_name = dev->get(1, 64);
	  else
	    dev_name = "ovpnc";
	}

	// parse trunk-unit
	trunk_unit = opt.get_num<decltype(trunk_unit)>("trunk-unit", 1, trunk_unit, 0, 511);

	return TunClientFactory::Ptr(this);
      }

      virtual TransportClientFactory::Ptr new_transport_factory(const DCO::TransportConfig& conf) override
      {
	transport = conf;
	return TransportClientFactory::Ptr(this);
      }

      virtual TunClient::Ptr new_tun_client_obj(openvpn_io::io_context& io_context,
						TunClientParent& parent,
						TransportClient* transcli) override;

      virtual TransportClient::Ptr new_transport_client_obj(openvpn_io::io_context& io_context,
							    TransportClientParent* parent) override;

      static DCO::Ptr new_controller()
      {
	return new ClientConfig();
      }

    private:
      ClientConfig() {}
    };

    class Client : public TransportClient,
		   public TunClient,
		   public KoRekey::Receiver,
		   public SessionStats::DCOTransportSource
    {
      friend class ClientConfig;

      typedef RCPtr<Client> Ptr;

      struct ProtoBase
      {
	ProtoBase() {}
	virtual IP::Addr server_endpoint_addr() const = 0;
	virtual void close() = 0;
	virtual ~ProtoBase() {}

	ProtoBase(const ProtoBase&) = delete;
	ProtoBase& operator=(const ProtoBase&) = delete;
      };

      struct UDP : public ProtoBase
      {
	UDP(openvpn_io::io_context& io_context)
	  : resolver(io_context),
	    socket(io_context)
	{
	}

	virtual IP::Addr server_endpoint_addr() const override
	{
	  return IP::Addr::from_asio(server_endpoint.address());
	}

	virtual void close() override
	{
	  socket.close();
	  resolver.cancel();
	}

	openvpn_io::ip::udp::resolver resolver;
	openvpn_io::ip::udp::socket socket;
	UDPTransport::AsioEndpoint server_endpoint;
      };

#ifdef ENABLE_PG
      typedef KoTun::Tun<Client*> TunImpl;
#else
      typedef KoTun::TunClient<Client*> TunImpl;
#endif

      // calls tun_read_handler and tun_error_handler
      friend TunImpl::Base;

    public:
      // transport methods

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
	devconf.dc.cpu_id = -1;

	// create kovpn tun socket
	impl.reset(new TunImpl(io_context,
			       devconf,
			       this,
			       config->transport.frame,
			       nullptr,
			       nullptr));

	// set kovpn stats hook
	config->transport.stats->dco_configure(this);

	// if trunking, set RPS/XPS on iface
	if (config->trunk_unit >= 0)
	  KoTun::KovpnBase::set_rps_xps(config->dev_name, devconf.dc.queue_index, config->tun.stop);

	if (devconf.dc.tcp)
	  transport_start_tcp();
	else
	  transport_start_udp();
      }

      // VPN IP collision detection for multi-channel trunking
      static void set_vpn_ip_collision(IPCollisionDetectBase* vpn_ip_collision_arg)
      {
	vpn_ip_collision = vpn_ip_collision_arg;
      }

      virtual bool transport_send_const(const Buffer& buf) override
      {
	return send(buf);
      }

      virtual bool transport_send(BufferAllocated& buf) override
      {
	return send(buf);
      }

      virtual bool transport_send_queue_empty() override
      {
	return false;
      }

      virtual bool transport_has_send_queue() override
      {
	return false;
      }

      virtual unsigned int transport_send_queue_size() override
      {
	return 0;
      }

      virtual void reset_align_adjust(const size_t align_adjust) override
      {
      }

      virtual void transport_stop_requeueing() override
      {
      }

      virtual void server_endpoint_info(std::string& host, std::string& port, std::string& proto, std::string& ip_addr) const override
      {
	host = server_host;
	port = server_port;
	const IP::Addr addr = server_endpoint_addr();
	proto = "UDP";
	proto += addr.version_string();
	proto += "-DCO";
	ip_addr = addr.to_string();
      }

      virtual IP::Addr server_endpoint_addr() const override
      {
	if (proto)
	  return proto->server_endpoint_addr();
	else
	  return IP::Addr();
      }

      virtual Protocol transport_protocol() const override
      {
	switch (server_endpoint_addr().version())
	  {
	  case IP::Addr::V4:
	    return Protocol(Protocol::UDPv4);
	  case IP::Addr::V6:
	    return Protocol(Protocol::UDPv6);
	  default:
	    return Protocol();
	  }
      }

      virtual ~Client() override
      {
	stop_();
      }

      // tun methods

      virtual void tun_start(const OptionList& opt,
			     TransportClient& transcli,
			     CryptoDCSettings& dc_settings) override
      {
	if (halt || !tun_parent)
	  OPENVPN_THROW(dco_error, "tun_start called on halted/undefined instance");

	try {
	  const IP::Addr server_addr = server_endpoint_addr();

	  // get the iface name
	  state->iface_name = config->dev_name;

	  // notify parent
	  tun_parent->tun_pre_tun_config();

	  // parse pushed options
	  TunBuilderCapture::Ptr po(new TunBuilderCapture());
	  TunProp::configure_builder(po.get(),
				     state.get(),
				     config->transport.stats.get(),
				     server_addr,
				     config->tun.tun_prop,
				     opt,
				     nullptr,
				     false);

	  OPENVPN_LOG("CAPTURED OPTIONS:" << std::endl << po->to_string());

	  // add/remove command lists
	  ActionList::Ptr add_cmds = new ActionList();
	  remove_cmds.reset(new ActionListReversed());

	  // configure tun properties
	  std::vector<IP::Route> rtvec;
	  if (config->trunk_unit >= 0)
	    {
	      // VPN IP collision detection, will throw on collision
	      if (vpn_ip_collision)
		detect_vpn_ip_collision(*vpn_ip_collision, *po, config->trunk_unit, *remove_cmds);

	      // trunk setup
	      TunLinux::iface_config(state->iface_name,
				     config->trunk_unit,
				     *po,
				     nullptr,
				     *add_cmds,
				     *remove_cmds);

	      // Note that in trunking mode, kovpn must be
	      // configured for source routing.
	      add_vpn_ips_as_source_routes(*po, rtvec, IP::Addr::V4);
	      add_vpn_ips_as_source_routes(*po, rtvec, IP::Addr::V6);
	    }
	  else
	    {
	      // non-trunk setup
	      TunLinux::tun_config(state->iface_name,
				   *po,
				   &rtvec,
				   *add_cmds,
				   *remove_cmds);
	    }

	  // Add routes to DCO implementation
	  impl->peer_add_routes(peer_id, rtvec);

	  // execute commands to bring up interface
	  add_cmds->execute_log();

	  // Add a hook so ProtoContext will call back to
	  // rekey() on rekey ops.
	  dc_settings.set_factory(CryptoDCFactory::Ptr(new KoRekey::Factory(dc_settings.factory(), this, config->transport.frame)));

	  // signal that we are connected
	  tun_parent->tun_connected();
	}
	catch (const IPCollisionDetectBase::ip_collision& e)
	  {
	    // on VPN IP address collision, just reconnect to get a new address
	    stop_();
	    tun_parent->tun_error(Error::TUN_ERROR, e.what());
	  }
	catch (const std::exception& e)
	  {
	    stop_();
	    tun_parent->tun_error(Error::TUN_SETUP_FAILED, e.what());
	  }
      }

      virtual void set_disconnect() override
      {
      }

      virtual bool tun_send(BufferAllocated& buf) override // return true if send succeeded
      {
	return false;
      }

      virtual std::string tun_name() const override
      {
	if (impl)
	  return impl->name();
	else
	  return "UNDEF_DCO";
      }

      virtual std::string vpn_ip4() const override
      {
	if (state->vpn_ip4_addr.specified())
	  return state->vpn_ip4_addr.to_string();
	else
	  return "";
      }

      virtual std::string vpn_ip6() const override
      {
	if (state->vpn_ip6_addr.specified())
	  return state->vpn_ip6_addr.to_string();
	else
	  return "";
      }

      virtual std::string vpn_gw4() const override
      {
	if (state->vpn_ip4_gw.specified())
	  return state->vpn_ip4_gw.to_string();
	else
	  return "";
      }

      virtual std::string vpn_gw6() const override
      {
	if (state->vpn_ip6_gw.specified())
	  return state->vpn_ip6_gw.to_string();
	else
	  return "";
      }

      // KoRekey::Receiver methods

      virtual void rekey(const CryptoDCInstance::RekeyType rktype,
			 const KoRekey::Info& rkinfo) override
      {
	if (halt)
	  return;

	KoRekey::Key key(rktype, rkinfo, peer_id, false);
	impl->peer_keys_reset(key());
	if (transport_parent->is_keepalive_enabled())
	  {
	    struct ovpn_peer_keepalive ka;

	    // Disable userspace keepalive, get the userspace
	    // keepalive parameters, and enable kovpn keepalive.
	    ka.peer_id = peer_id;
	    transport_parent->disable_keepalive(ka.keepalive_ping,
						ka.keepalive_timeout);

	    // Modify the peer
	    impl->peer_set_keepalive(&ka);
	  }
      }

      virtual void explicit_exit_notify() override
      {
	impl->peer_xmit_explicit_exit_notify(peer_id);
      }

      // shared methods

      virtual void stop() override
      {
	stop_();
      }

    private:
      Client(openvpn_io::io_context& io_context_arg,
	     ClientConfig* config_arg,
	     TransportClientParent* parent_arg)
	: io_context(io_context_arg),
	  halt(false),
	  state(new TunProp::State()),
	  config(config_arg),
	  transport_parent(parent_arg),
	  tun_parent(nullptr),
	  peer_id(-1)
      {
      }

      virtual void transport_reparent(TransportClientParent* parent_arg)
      {
	transport_parent = parent_arg;
      }

      void transport_start_udp()
      {
	proto.reset(new UDP(io_context));
	if (config->transport.remote_list->endpoint_available(&server_host, &server_port, nullptr))
	  {
	    start_connect_udp();
	  }
	else
	  {
	    transport_parent->transport_pre_resolve();
	    udp().resolver.async_resolve(server_host, server_port,
					 [self=Ptr(this)](const openvpn_io::error_code& error, openvpn_io::ip::udp::resolver::results_type results)
					 {
					   self->do_resolve_udp(error, results);
					 });
	  }
      }

      // called after DNS resolution has succeeded or failed
      void do_resolve_udp(const openvpn_io::error_code& error,
			  openvpn_io::ip::udp::resolver::results_type results)
      {
	if (!halt)
	  {
	    if (!error)
	      {
		// save resolved endpoint list in remote_list
		config->transport.remote_list->set_endpoint_range(results);
		start_connect_udp();
	      }
	    else
	      {
		std::ostringstream os;
		os << "DNS resolve error on '" << server_host << "' for UDP session: " << error.message();
		config->transport.stats->error(Error::RESOLVE_ERROR);
		stop_();
		transport_parent->transport_error(Error::UNDEF, os.str());
	      }
	  }
      }

      // do UDP connect
      void start_connect_udp()
      {
	config->transport.remote_list->get_endpoint(udp().server_endpoint);
	OPENVPN_LOG("Contacting " << udp().server_endpoint << " via UDP");
	transport_parent->transport_wait();
	transport_parent->ip_hole_punch(server_endpoint_addr());
	udp().socket.open(udp().server_endpoint.protocol());
	udp().socket.async_connect(udp().server_endpoint, [self=Ptr(this)](const openvpn_io::error_code& error)
                                                          {
                                                            self->start_impl_udp(error);
                                                          });
      }

      // start I/O on UDP socket
      void start_impl_udp(const openvpn_io::error_code& error)
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

      void transport_start_tcp()
      {
	OPENVPN_THROW(dco_error, "TCP not implemented yet"); // fixme for DCO
      }

      void tun_read_handler(KoTun::PacketFrom::SPtr& pfp) // called by TunImpl
      {
	if (halt)
	  return;

	try {
	  const struct ovpn_tun_head *th = (const struct ovpn_tun_head *)pfp->buf.read_alloc(sizeof(struct ovpn_tun_head));
	  switch (th->type)
	    {
	    case OVPN_TH_TRANS_BY_PEER_ID:
	      {
		if (peer_id < 0 || th->peer_id != peer_id)
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

		if (peer_id < 0 || thn->head.peer_id != peer_id)
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
	catch (const std::exception& e)
	  {
	    const std::string msg = std::string("dcocli: tun_read_handler: ") + e.what();
	    OPENVPN_LOG(msg);
	    stop_();
	    transport_parent->transport_error(Error::TRANSPORT_ERROR, msg);
	  }
      }

      void tun_error_handler(const Error::Type errtype, // called by TunImpl
			     const openvpn_io::error_code* error)
      {
	OPENVPN_LOG("TUN error");
	stop_();
      }

      bool send(const Buffer& buf)
      {
	struct ovpn_tun_head head;
	std::memset(&head, 0, sizeof(head));
	head.type = OVPN_TH_TRANS_BY_PEER_ID;
	head.peer_id = peer_id;
	return impl->write_seq(AsioConstBufferSeq2(Buffer(reinterpret_cast<Buffer::type>(&head), sizeof(head), true),
						   buf));
      }

      void stop_()
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

      UDP& udp()
      {
	return *static_cast<UDP*>(proto.get());
      }

      static void add_vpn_ips_as_source_routes(const TunBuilderCapture& pull,
					       std::vector<IP::Route>& rtvec,
					       const IP::Addr::Version ver)
      {
	const TunBuilderCapture::RouteAddress *ra = pull.vpn_ip(ver);
	if (ra)
	  rtvec.push_back(IP::route_from_string_prefix(ra->address,
						       IP::Addr::version_size(ver),
						       "DCOTransport::Client::add_vpn_ips_as_source_routes",
						       ver));
      }

      // Throw an exception of type IPCollisionDetectBase::ip_collision
      // if VPN IP is already in use by another client thread.
      // This is intended to force a reconnect and obtain a
      // new non-colliding address.
      static void detect_vpn_ip_collision(IPCollisionDetectBase& ipcoll,
					  const TunBuilderCapture& pull,
					  unsigned int unit,
					  ActionList& remove)
      {
	const TunBuilderCapture::RouteAddress* local4 = pull.vpn_ipv4();
	const TunBuilderCapture::RouteAddress* local6 = pull.vpn_ipv6();
	if (local4)
	  ipcoll.add(local4->address, unit, remove);
	if (local6)
	  ipcoll.add(local6->address, unit, remove);
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

      openvpn_io::io_context& io_context;
      bool halt;

      TunProp::State::Ptr state;

      ClientConfig::Ptr config;
      TransportClientParent* transport_parent;
      TunClientParent* tun_parent;

      std::unique_ptr<ProtoBase> proto;

      ActionList::Ptr remove_cmds;

      std::string server_host;
      std::string server_port;

      TunImpl::Ptr impl;
      int peer_id;

      SessionStats::DCOTransportSource::Data last_stats;
      __u64 cc_rx_bytes = 0;

      static IPCollisionDetectBase* vpn_ip_collision;
    };

    inline DCO::Ptr new_controller()
    {
      return ClientConfig::new_controller();
    }

    inline TransportClient::Ptr ClientConfig::new_transport_client_obj(openvpn_io::io_context& io_context,
								       TransportClientParent* parent)
    {
      return TransportClient::Ptr(new Client(io_context, this, parent));
    }

    inline TunClient::Ptr ClientConfig::new_tun_client_obj(openvpn_io::io_context& io_context,
							   TunClientParent& parent,
							   TransportClient* transcli)
    {
      Client* cli = static_cast<Client*>(transcli);
      cli->tun_parent = &parent;
      return TunClient::Ptr(cli);
    }

    IPCollisionDetectBase* Client::vpn_ip_collision; // GLOBAL
  }
};

#endif
