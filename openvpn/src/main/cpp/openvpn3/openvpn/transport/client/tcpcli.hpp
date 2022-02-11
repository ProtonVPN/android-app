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

// TCP transport object specialized for client.

#ifndef OPENVPN_TRANSPORT_CLIENT_TCPCLI_H
#define OPENVPN_TRANSPORT_CLIENT_TCPCLI_H

#include <sstream>

#include <openvpn/io/io.hpp>

#include <openvpn/transport/tcplink.hpp>
#ifdef OPENVPN_TLS_LINK
#include <openvpn/transport/tlslink.hpp>
#endif
#include <openvpn/transport/client/transbase.hpp>
#include <openvpn/transport/socket_protect.hpp>
#include <openvpn/client/remotelist.hpp>

namespace openvpn {
  namespace TCPTransport {

    class ClientConfig : public TransportClientFactory
    {
    public:
      typedef RCPtr<ClientConfig> Ptr;

      RemoteList::Ptr remote_list;
      size_t free_list_max_size;
      Frame::Ptr frame;
      SessionStats::Ptr stats;

      SocketProtect* socket_protect;

#ifdef OPENVPN_TLS_LINK
      bool use_tls = false;
      std::string tls_ca;
#endif

#ifdef OPENVPN_GREMLIN
      Gremlin::Config::Ptr gremlin_config;
#endif

      static Ptr new_obj()
      {
	return new ClientConfig;
      }

      TransportClient::Ptr new_transport_client_obj(openvpn_io::io_context& io_context,
						    TransportClientParent* parent) override;

      void process_push(const OptionList& opt) override
      {
	remote_list->process_push(opt);
      }

    private:
      ClientConfig()
	: free_list_max_size(8),
	  socket_protect(nullptr)
      {}
    };

    class Client : public TransportClient, AsyncResolvableTCP
    {
      typedef RCPtr<Client> Ptr;

      typedef Link<openvpn_io::ip::tcp, Client*, false> LinkImpl;
#ifdef OPENVPN_TLS_LINK
      typedef TLSLink<openvpn_io::ip::tcp, Client*, false> LinkImplTLS;
#endif

      friend class ClientConfig;         // calls constructor
      friend LinkImpl::Base;             // calls tcp_read_handler

    public:
      void transport_start() override
      {
	if (!impl)
	  {
	    halt = false;
	    stop_requeueing = false;
	    if (config->remote_list->endpoint_available(&server_host,
							&server_port,
							&server_protocol))
	      {
		start_connect_();
	      }
	    else
	      {
		parent->transport_pre_resolve();

		async_resolve_name(server_host, server_port);
	      }
	  }
      }

      bool transport_send_const(const Buffer& buf) override
      {
	return send_const(buf);
      }

      bool transport_send(BufferAllocated& buf) override
      {
	return send(buf);
      }

      bool transport_send_queue_empty() override
      {
	if (impl)
	  return impl->send_queue_empty();
	else
	  return false;
      }

      bool transport_has_send_queue() override
      {
	return true;
      }

      unsigned int transport_send_queue_size() override
      {
	if (impl)
	  return impl->send_queue_size();
	else
	  return 0;
      }

      void reset_align_adjust(const size_t align_adjust) override
      {
	if (impl)
	  impl->reset_align_adjust(align_adjust);
      }

      void server_endpoint_info(std::string& host, std::string& port, std::string& proto, std::string& ip_addr) const override
      {
	host = server_host;
	port = server_port;
	const IP::Addr addr = server_endpoint_addr();
	proto = server_protocol.str();
	ip_addr = addr.to_string();
      }

      IP::Addr server_endpoint_addr() const override
      {
	return IP::Addr::from_asio(server_endpoint.address());
      }

      unsigned short server_endpoint_port() const override
      {
	return server_endpoint.port();
      }

      int native_handle() override
      {
	return socket.native_handle();
      }

      Protocol transport_protocol() const override
      {
	return server_protocol;
      }

      void stop() override { stop_(); }
      ~Client() override { stop_(); }

    private:
      Client(openvpn_io::io_context& io_context_arg,
	     ClientConfig* config_arg,
	     TransportClientParent* parent_arg)
	:  AsyncResolvableTCP(io_context_arg),
	   io_context(io_context_arg),
	   socket(io_context_arg),
	   config(config_arg),
	   parent(parent_arg),
	   resolver(io_context_arg),
	   halt(false),
	   stop_requeueing(false)
      {
      }

      void transport_reparent(TransportClientParent* parent_arg) override
      {
	parent = parent_arg;
      }

      void transport_stop_requeueing() override
      {
	stop_requeueing = true;
      }

      bool send_const(const Buffer& cbuf)
      {
	if (impl)
	  {
	    BufferAllocated buf(cbuf, 0);
	    return impl->send(buf);
	  }
	else
	  return false;
      }

      bool send(BufferAllocated& buf)
      {
	if (impl)
	  return impl->send(buf);
	else
	  return false;
      }

      void tcp_eof_handler() // called by LinkImpl::Base
      {
	config->stats->error(Error::NETWORK_EOF_ERROR);
	tcp_error_handler("NETWORK_EOF_ERROR");
      }

      bool tcp_read_handler(BufferAllocated& buf) // called by LinkImpl::Base
      {
	parent->transport_recv(buf);
	return !stop_requeueing;
      }

      void tcp_write_queue_needs_send() // called by LinkImpl::Base
      {
	parent->transport_needs_send();
      }

      void tcp_error_handler(const char *error) // called by LinkImpl::Base
      {
	std::ostringstream os;
	os << "Transport error on '" << server_host << ": " << error;
	stop();
	parent->transport_error(Error::TRANSPORT_ERROR, os.str());
      }

      void stop_()
      {
	if (!halt)
	  {
	    halt = true;
	    if (impl)
	      impl->stop();

	    socket.close();
	    resolver.cancel();
	    async_resolve_cancel();
	  }
      }

      // do DNS resolve
      void resolve_callback(const openvpn_io::error_code& error,
			    results_type results) override
      {
	if (!halt)
	  {
	    if (!error)
	      {
		// save resolved endpoint list in remote_list
		config->remote_list->set_endpoint_range(results);
		start_connect_();
	      }
	    else
	      {
		std::ostringstream os;
		os << "DNS resolve error on '" << server_host << "' for " << server_protocol.str() << " session: " << error.message();
		config->stats->error(Error::RESOLVE_ERROR);
		stop();
		parent->transport_error(Error::UNDEF, os.str());
	      }
	  }
      }

      // do TCP connect
      void start_connect_()
      {
	config->remote_list->get_endpoint(server_endpoint);
	OPENVPN_LOG("Contacting " << server_endpoint << " via "
		    << server_protocol.str());
	parent->transport_wait();
	socket.open(server_endpoint.protocol());

	if (config->socket_protect)
	  {
	    if (!config->socket_protect->socket_protect(socket.native_handle(), server_endpoint_addr()))
	      {
		config->stats->error(Error::SOCKET_PROTECT_ERROR);
		stop();
		parent->transport_error(Error::UNDEF, "socket_protect error (" + std::string(server_protocol.str()) + ")");
		return;
	      }
	  }

	socket.set_option(openvpn_io::ip::tcp::no_delay(true));
	socket.async_connect(server_endpoint, [self=Ptr(this)](const openvpn_io::error_code& error)
                                              {
                                                OPENVPN_ASYNC_HANDLER;
                                                self->start_impl_(error);
                                              });
      }

      // start I/O on TCP socket
      void start_impl_(const openvpn_io::error_code& error)
      {
	if (!halt)
	  {
	    if (!error)
	      {
#ifdef OPENVPN_TLS_LINK
		if (config->use_tls)
		{
		  int flags = SSLConst::LOG_VERIFY_STATUS|SSLConst::ENABLE_CLIENT_SNI;
		  SSLLib::SSLAPI::Config::Ptr ssl_conf;
		  ssl_conf.reset(new SSLLib::SSLAPI::Config());
		  ssl_conf->set_mode(Mode(Mode::CLIENT));
		  ssl_conf->set_local_cert_enabled(false);
		  ssl_conf->set_frame(config->frame);
		  ssl_conf->set_rng(new SSLLib::RandomAPI(false));

		  if (!config->tls_ca.empty())
		  {
		    ssl_conf->load_ca(config->tls_ca, true);
		  }
		  else
		  {
		    flags |= SSLConst::NO_VERIFY_PEER;
		  }

		  ssl_conf->set_flags(flags);
		  ssl_factory = ssl_conf->new_factory();

		  impl.reset(new LinkImplTLS(this,
					     io_context,
					     socket,
					     0,
					     config->free_list_max_size,
					     config->frame,
					     config->stats,
					     ssl_factory));
		}
		else
#endif
		  impl.reset(new LinkImpl(this,
					  socket,
					  0, // send_queue_max_size is unlimited because we regulate size in cliproto.hpp
					  config->free_list_max_size,
					  (*config->frame)[Frame::READ_LINK_TCP],
					  config->stats));

#ifdef OPENVPN_GREMLIN
		impl->gremlin_config(config->gremlin_config);
#endif
		impl->start();
		if (!parent->transport_is_openvpn_protocol())
		  impl->set_raw_mode(true);
		parent->transport_connecting();
	      }
	    else
	      {
		std::ostringstream os;
		os << server_protocol.str() << " connect error on '" << server_host << ':' << server_port << "' (" << server_endpoint << "): " << error.message();
		config->stats->error(Error::TCP_CONNECT_ERROR);
		stop();
		parent->transport_error(Error::UNDEF, os.str());
	      }
	  }
      }

      std::string server_host;
      std::string server_port;
      Protocol server_protocol;

      openvpn_io::io_context& io_context;
      openvpn_io::ip::tcp::socket socket;
      ClientConfig::Ptr config;
      TransportClientParent* parent;
      LinkBase::Ptr impl;
      openvpn_io::ip::tcp::resolver resolver;
      LinkImpl::Base::protocol::endpoint server_endpoint;
      bool halt;
      bool stop_requeueing;

#ifdef OPENVPN_TLS_LINK
      SSLFactoryAPI::Ptr ssl_factory;
#endif
    };

    inline TransportClient::Ptr ClientConfig::new_transport_client_obj(openvpn_io::io_context& io_context,
								       TransportClientParent* parent)
    {
      return TransportClient::Ptr(new Client(io_context, this, parent));
    }
  }
} // namespace openvpn

#endif
