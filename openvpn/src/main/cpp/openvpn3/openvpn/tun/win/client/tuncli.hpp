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

// Client tun interface for Windows

#ifndef OPENVPN_TUN_WIN_CLIENT_TUNCLI_H
#define OPENVPN_TUN_WIN_CLIENT_TUNCLI_H

#include <string>
#include <sstream>
#include <memory>

#include <openvpn/common/to_string.hpp>
#include <openvpn/asio/scoped_asio_stream.hpp>
#include <openvpn/common/cleanup.hpp>
#include <openvpn/time/asiotimer.hpp>
#include <openvpn/tun/client/tunbase.hpp>
#include <openvpn/tun/client/tunprop.hpp>
#include <openvpn/tun/client/dhcp_capture.hpp>
#include <openvpn/tun/persist/tunpersist.hpp>
#include <openvpn/tun/persist/tunwrapasio.hpp>
#include <openvpn/tun/tunio.hpp>
#include <openvpn/tun/win/client/clientconfig.hpp>
#include <openvpn/tun/win/client/tunsetup.hpp>
#include <openvpn/win/modname.hpp>
#include <openvpn/tun/win/client/wintun.hpp>

namespace openvpn {
  namespace TunWin {

    OPENVPN_EXCEPTION(tun_win_error);

    // struct used to pass received tun packets
    struct PacketFrom
    {
      typedef std::unique_ptr<PacketFrom> SPtr;
      BufferAllocated buf;
    };

    // tun interface wrapper for Windows
    template <typename ReadHandler, typename TunPersist>
    class Tun : public TunIO<ReadHandler, PacketFrom, TunWrapAsioStream<TunPersist> >
    {
      typedef TunIO<ReadHandler, PacketFrom, TunWrapAsioStream<TunPersist>  > Base;

    public:
      typedef RCPtr<Tun> Ptr;

      Tun(const typename TunPersist::Ptr& tun_persist,
	  const std::string& name,
	  const bool retain_stream,
	  ReadHandler read_handler,
	  const Frame::Ptr& frame,
	  const SessionStats::Ptr& stats)
	: Base(read_handler, frame, stats, Frame::READ_TUN)
      {
	Base::name_ = name;
	Base::retain_stream = retain_stream;
	Base::stream = new TunWrapAsioStream<TunPersist>(tun_persist);
      }
    };

    class Client : public TunClient
    {
      friend class ClientConfig;  // calls constructor
      friend class TunIO<Client*, PacketFrom, TunWrapAsioStream<TunPersist> >;  // calls tun_read_handler

      typedef Tun<Client*, TunPersist> TunImpl;

    public:
      typedef RCPtr<Client> Ptr;

      virtual void tun_start(const OptionList& opt, TransportClient& transcli, CryptoDCSettings&) override
      {
	if (!impl)
	  {
	    halt = false;
	    if (config->tun_persist)
	      tun_persist = config->tun_persist; // long-term persistent
	    else
	      tun_persist.reset(new TunPersist(false, false, nullptr)); // short-term

	    try {
	      const IP::Addr server_addr = transcli.server_endpoint_addr();

	      // Check if persisted tun session matches properties of to-be-created session
	      if (tun_persist->use_persisted_tun(server_addr, config->tun_prop, opt))
		{
		  state = tun_persist->state().state;
		  OPENVPN_LOG("TunPersist: reused tun context");
		}
	      else
		{
		  // notify parent
		  parent.tun_pre_tun_config();

		  // close old TAP handle if persisted
		  tun_persist->close();

		  // parse pushed options
		  TunBuilderCapture::Ptr po(new TunBuilderCapture());
		  TunProp::configure_builder(po.get(),
					     state.get(),
					     config->stats.get(),
					     server_addr,
					     config->tun_prop,
					     opt,
					     nullptr,
					     false);
		  OPENVPN_LOG("CAPTURED OPTIONS:" << std::endl << po->to_string());

		  // create new tun setup object
		  tun_setup = config->new_setup_obj(io_context);

		  // open/config TAP
		  HANDLE th;
		  {
		    std::ostringstream os;
		    auto os_print = Cleanup([&os](){ OPENVPN_LOG_STRING(os.str()); });
		    th = tun_setup->establish(*po, Win::module_name(), config->stop, os, NULL);
		  }

		  // create ASIO wrapper for HANDLE
		  TAPStream* ts = new TAPStream(io_context, th);

		  // persist tun settings state
		  if (tun_persist->persist_tun_state(ts, { state, nullptr }))
		    OPENVPN_LOG("TunPersist: saving tun context:" << std::endl << tun_persist->options());

		  // setup handler for external tun close
		  tun_setup->set_service_fail_handler([self=Ptr(this)]() {
		      if (!self->halt)
			self->parent.tun_error(Error::TUN_IFACE_DISABLED, "service failure");
		    });

		  // enable tun_setup destructor
		  tun_persist->add_destructor(tun_setup);

		  // assert ownership over TAP device handle
		  tun_setup->confirm();

		  // if layer 2, set up to capture DHCP messages over the tunnel
		  if (config->tun_prop.layer() == Layer::OSI_LAYER_2)
		    dhcp_capture.reset(new DHCPCapture(po));
		}

	      // configure tun interface packet forwarding
	      impl.reset(new TunImpl(tun_persist,
				     "TUN_WIN",
				     true,
				     this,
				     config->frame,
				     config->stats));
	      impl->start(config->n_parallel);

	      if (!dhcp_capture)
		parent.tun_connected(); // signal that we are connected
	    }
	    catch (const std::exception& e)
	      {
		if (tun_persist)
		  tun_persist->close();
		stop();
		Error::Type err = Error::TUN_SETUP_FAILED;
		const ExceptionCode *ec = dynamic_cast<const ExceptionCode *>(&e);
		if (ec && ec->code_defined())
		  err = ec->code();
		parent.tun_error(err, e.what());
	      }
	  }
      }

      virtual bool tun_send(BufferAllocated& buf) override
      {
	return send(buf);
      }

      virtual std::string tun_name() const override
      {
	if (impl)
	  return impl->name();
	else
	  return "UNDEF_TUN";
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

      virtual void set_disconnect() override
      {
      }

      virtual void stop() override { stop_(); }
      virtual ~Client() { stop_(); }

    private:
      Client(openvpn_io::io_context& io_context_arg,
	     ClientConfig* config_arg,
	     TunClientParent& parent_arg)
	:  io_context(io_context_arg),
	   config(config_arg),
	   parent(parent_arg),
	   state(new TunProp::State()),
	   l2_timer(io_context_arg),
	   frame_context((*config_arg->frame)[Frame::READ_TUN]),
	   halt(false)
      {
      }

      bool send(Buffer& buf)
      {
	if (impl)
	  {
	    if (dhcp_capture)
	      dhcp_inspect(buf);

	    return impl->write(buf);
	  }
	else
	  return false;
#ifdef OPENVPN_DEBUG_TAPWIN
	tap_process_logging();
#endif
      }

      void tun_read_handler(PacketFrom::SPtr& pfp) // called by TunImpl
      {
	parent.tun_recv(pfp->buf);

#ifdef OPENVPN_DEBUG_TAPWIN
	tap_process_logging();
#endif
      }

      void tun_error_handler(const Error::Type errtype, // called by TunImpl
			     const openvpn_io::error_code* error)
      {
	if (errtype == Error::TUN_READ_ERROR && error && error->value() == 995)
	  parent.tun_error(Error::TUN_IFACE_DISABLED, "TAP adapter is disabled");
	else
	  parent.tun_error(Error::TUN_ERROR, "TUN I/O error");
      }

      void stop_()
      {
	if (!halt)
	  {
	    halt = true;

	    l2_timer.cancel();

	    // stop tun
	    if (impl)
	      impl->stop();
	    tun_persist.reset();
	  }
      }

      HANDLE tap_handle()
      {
	if (tun_persist)
	  {
	    TAPStream* stream = tun_persist->obj();
	    if (stream)
	      return stream->native_handle();
	  }
	return Win::Handle::undefined();
      }

      void tap_process_logging()
      {
	HANDLE h = tap_handle();
	if (Win::Handle::defined(h))
	  Util::tap_process_logging(h);
      }

      void dhcp_inspect(Buffer& buf)
      {
	try {
	  if (dhcp_capture->mod_reply(buf))
	    {
	      OPENVPN_LOG("DHCP PROPS:" << std::endl << dhcp_capture->get_props().to_string());
	      layer_2_schedule_timer(1);
	    }
	}
	catch (const std::exception& e)
	  {
	    stop();
	    parent.tun_error(Error::TUN_SETUP_FAILED, std::string("L2 exception: ") + e.what());
	  }
      }

      void layer_2_schedule_timer(const unsigned int seconds)
      {
	l2_timer.expires_after(Time::Duration::seconds(seconds));
	l2_timer.async_wait([self=Ptr(this)](const openvpn_io::error_code& error)
			    {
			      OPENVPN_ASYNC_HANDLER;
			      if (!error && !self->halt)
				self->layer_2_timer_callback();
			    });
      }

      // Normally called once per second by l2_timer while we are waiting
      // for layer 2 DHCP handshake to complete.
      void layer_2_timer_callback()
      {
	try {
	  if (dhcp_capture && tun_setup)
	    {
	      if (tun_setup->l2_ready(dhcp_capture->get_props()))
		{
		  std::ostringstream os;
		  tun_setup->l2_finish(dhcp_capture->get_props(), config->stop, os);
		  OPENVPN_LOG_STRING(os.str());
		  parent.tun_connected();
		  dhcp_capture.reset();
		}
	      else
		{
		  OPENVPN_LOG("L2: Waiting for DHCP handshake...");
		  layer_2_schedule_timer(1);
		}
	    }
	}
	catch (const std::exception& e)
	  {
	    stop();
	    parent.tun_error(Error::TUN_SETUP_FAILED, std::string("L2 exception: ") + e.what());
	  }
      }

      openvpn_io::io_context& io_context;
      TunPersist::Ptr tun_persist; // contains the TAP device HANDLE
      ClientConfig::Ptr config;
      TunClientParent& parent;
      TunImpl::Ptr impl;
      TunProp::State::Ptr state;
      TunWin::SetupBase::Ptr tun_setup;

      // Layer 2 DHCP stuff
      std::unique_ptr<DHCPCapture> dhcp_capture;
      AsioTimer l2_timer;

      Frame::Context& frame_context;

      bool halt;
    };

    inline TunClient::Ptr ClientConfig::new_tun_client_obj(openvpn_io::io_context& io_context,
							   TunClientParent& parent,
							   TransportClient* transcli)
    {
      if (tun_type == TunWin::Wintun)
	return TunClient::Ptr(new WintunClient(io_context, this, parent));
      else if (tun_type == TunWin::TapWindows6)
	return TunClient::Ptr(new Client(io_context, this, parent));
      else
	throw tun_win_error("unsupported tun driver");
    }

  }
} // namespace openvpn

#endif
