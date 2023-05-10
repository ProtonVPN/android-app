//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2022 OpenVPN Inc.
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

// Client tun interface for Mac OS X

#ifndef OPENVPN_TUN_MAC_CLIENT_TUNCLI_H
#define OPENVPN_TUN_MAC_CLIENT_TUNCLI_H

#include <string>
#include <sstream>
#include <memory>

#include <openvpn/common/to_string.hpp>
#include <openvpn/asio/scoped_asio_stream.hpp>
#include <openvpn/common/cleanup.hpp>
#include <openvpn/tun/client/tunbase.hpp>
#include <openvpn/tun/client/tunprop.hpp>
#include <openvpn/tun/persist/tunpersist.hpp>
#include <openvpn/tun/persist/tunwrapasio.hpp>
#include <openvpn/tun/tunio.hpp>
#include <openvpn/tun/tunmtu.hpp>
#include <openvpn/tun/mac/client/tunsetup.hpp>

#ifdef TEST_EER // test emulated exclude routes
#include <openvpn/client/cliemuexr.hpp>
#endif

namespace openvpn {
namespace TunMac {

OPENVPN_EXCEPTION(tun_mac_error);

// struct used to pass received tun packets
struct PacketFrom
{
    typedef std::unique_ptr<PacketFrom> SPtr;
    BufferAllocated buf;
};

// tun interface wrapper for Mac OS X
template <typename ReadHandler, typename TunPersist>
class Tun : public TunIO<ReadHandler, PacketFrom, TunWrapAsioStream<TunPersist>>
{
    typedef TunIO<ReadHandler, PacketFrom, TunWrapAsioStream<TunPersist>> Base;

  public:
    typedef RCPtr<Tun> Ptr;

    Tun(const typename TunPersist::Ptr &tun_persist,
        const std::string &name,
        const bool retain_stream,
        const bool tun_prefix,
        ReadHandler read_handler,
        const Frame::Ptr &frame,
        const SessionStats::Ptr &stats)
        : Base(read_handler, frame, stats)
    {
        Base::name_ = name;
        Base::retain_stream = retain_stream;
        Base::tun_prefix = tun_prefix;
        Base::stream = new TunWrapAsioStream<TunPersist>(tun_persist);
    }
};

// These types manage the underlying tun driver fd
typedef openvpn_io::posix::stream_descriptor TUNStream;
typedef ScopedAsioStream<TUNStream> ScopedTUNStream;
typedef TunPersistTemplate<ScopedTUNStream> TunPersist;

class Client;

class ClientConfig : public TunClientFactory
{
  public:
    typedef RCPtr<ClientConfig> Ptr;

    TunProp::Config tun_prop;
    int n_parallel = 8; // number of parallel async reads on tun socket

    Frame::Ptr frame;
    SessionStats::Ptr stats;

    TunPersist::Ptr tun_persist;

    Stop *stop = nullptr;

    TunBuilderSetup::Factory::Ptr tun_setup_factory;

    TunBuilderSetup::Base::Ptr new_setup_obj()
    {
        if (tun_setup_factory)
            return tun_setup_factory->new_setup_obj();
        else
            return new TunMac::Setup();
    }

    static Ptr new_obj()
    {
        return new ClientConfig;
    }

    virtual TunClient::Ptr new_tun_client_obj(openvpn_io::io_context &io_context,
                                              TunClientParent &parent,
                                              TransportClient *transcli);

    // return true if layer 2 tunnels are supported
    virtual bool layer_2_supported() const
    {
#if defined(MAC_TUNTAP_FALLBACK)
        return false; // change to true after TAP support is added
#else
        return false; // utun device doesn't support TAP
#endif
    }

    // called just prior to transmission of Disconnect event
    virtual void finalize(const bool disconnected)
    {
        if (disconnected)
            tun_persist.reset();
    }
};

class Client : public TunClient
{
    friend class ClientConfig;                                               // calls constructor
    friend class TunIO<Client *, PacketFrom, TunWrapAsioStream<TunPersist>>; // calls tun_read_handler

    typedef Tun<Client *, TunPersist> TunImpl;

  public:
    virtual void tun_start(const OptionList &opt, TransportClient &transcli, CryptoDCSettings &) override
    {
        if (!impl)
        {
            halt = false;
            if (config->tun_persist)
            {
                OPENVPN_LOG("TunPersist: long-term session scope");
                tun_persist = config->tun_persist;
            }
            else
            {
                OPENVPN_LOG("TunPersist: short-term connection scope");
                tun_persist.reset(new TunPersist(false, TunWrapObjRetain::NO_RETAIN, NULL));
            }

            try
            {
                const IP::Addr server_addr = transcli.server_endpoint_addr();

                // Check if persisted tun session matches properties of to-be-created session
                if (tun_persist->use_persisted_tun(server_addr, config->tun_prop, opt))
                {
                    state = tun_persist->state();
                    OPENVPN_LOG("TunPersist: reused tun context");
                }
                else
                {
                    OPENVPN_LOG("TunPersist: new tun context");

                    // notify parent
                    parent.tun_pre_tun_config();

                    // close old tun handle if persisted
                    tun_persist->close();

                    // emulated exclude routes
                    EmulateExcludeRouteFactory::Ptr eer_factory;
#ifdef TEST_EER
                    eer_factory.reset(new EmulateExcludeRouteFactoryImpl(true));
#endif
                    // parse pushed options
                    TunBuilderCapture::Ptr po(new TunBuilderCapture());
                    TunProp::configure_builder(po.get(),
                                               state.get(),
                                               config->stats.get(),
                                               server_addr,
                                               config->tun_prop,
                                               opt,
                                               eer_factory.get(),
                                               false);

                    // handle MTU default
                    if (!po->mtu)
                        po->mtu = TUN_MTU_DEFAULT;

                    OPENVPN_LOG("CAPTURED OPTIONS:" << std::endl
                                                    << po->to_string());

                    // create new tun setup object
                    tun_setup = config->new_setup_obj();

                    // create config object for tun setup layer
                    Setup::Config tsconf;
                    tsconf.iface_name = state->iface_name;
                    tsconf.layer = config->tun_prop.layer;

                    // open/config tun
                    int fd = -1;
                    {
                        std::ostringstream os;
                        auto os_print = Cleanup([&os]()
                                                { OPENVPN_LOG_STRING(os.str()); });
                        fd = tun_setup->establish(*po, &tsconf, config->stop, os);
                    }

                    // create ASIO wrapper for tun fd
                    TUNStream *ts = new TUNStream(io_context, fd);

                    // persist tun settings state
                    state->iface_name = tsconf.iface_name;
                    state->tun_prefix = tsconf.tun_prefix;
                    if (tun_persist->persist_tun_state(ts, state))
                        OPENVPN_LOG("TunPersist: saving tun context:" << std::endl
                                                                      << tun_persist->options());

                    // enable tun_setup destructor
                    tun_persist->add_destructor(tun_setup);
                }

                // configure tun interface packet forwarding
                impl.reset(new TunImpl(tun_persist,
                                       state->iface_name,
                                       true,
                                       state->tun_prefix,
                                       this,
                                       config->frame,
                                       config->stats));
                impl->start(config->n_parallel);

                // signal that we are connected
                parent.tun_connected();
            }
            catch (const std::exception &e)
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

    virtual bool tun_send(BufferAllocated &buf) override
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

    int vpn_mtu() const override
    {
        return state->mtu;
    }

    virtual void set_disconnect() override
    {
    }

    virtual void stop() override
    {
        stop_();
    }
    virtual ~Client()
    {
        stop_();
    }

  private:
    Client(openvpn_io::io_context &io_context_arg,
           ClientConfig *config_arg,
           TunClientParent &parent_arg)
        : io_context(io_context_arg),
          config(config_arg),
          parent(parent_arg),
          halt(false),
          state(new TunProp::State())
    {
    }

    bool send(Buffer &buf)
    {
        if (impl)
            return impl->write(buf);
        else
            return false;
    }

    void tun_read_handler(PacketFrom::SPtr &pfp) // called by TunImpl
    {
        parent.tun_recv(pfp->buf);
    }

    void tun_error_handler(const Error::Type errtype, // called by TunImpl
                           const openvpn_io::error_code *error)
    {
        parent.tun_error(Error::TUN_ERROR, "TUN I/O error");
    }

    void stop_()
    {
        if (!halt)
        {
            halt = true;

            // stop tun
            if (impl)
                impl->stop();
            tun_persist.reset();
        }
    }

    openvpn_io::io_context &io_context;
    TunPersist::Ptr tun_persist; // contains the tun device fd
    ClientConfig::Ptr config;
    TunClientParent &parent;
    TunImpl::Ptr impl;
    bool halt;
    TunProp::State::Ptr state;
    TunBuilderSetup::Base::Ptr tun_setup;
};

inline TunClient::Ptr ClientConfig::new_tun_client_obj(openvpn_io::io_context &io_context,
                                                       TunClientParent &parent,
                                                       TransportClient *transcli)
{
    return TunClient::Ptr(new Client(io_context, this, parent));
}

} // namespace TunMac
} // namespace openvpn

#endif
