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

// Client tun interface for Linux.

#ifndef OPENVPN_TUN_LINUX_CLIENT_TUNCLI_H
#define OPENVPN_TUN_LINUX_CLIENT_TUNCLI_H

#include <openvpn/asio/asioerr.hpp>
#include <openvpn/common/cleanup.hpp>
#include <openvpn/common/scoped_fd.hpp>
#include <openvpn/tun/builder/setup.hpp>
#include <openvpn/tun/tunio.hpp>
#include <openvpn/tun/persist/tunpersist.hpp>
#include <openvpn/tun/linux/client/tunmethods.hpp>

namespace openvpn {
namespace TunLinux {

struct PacketFrom
{
    typedef std::unique_ptr<PacketFrom> SPtr;
    BufferAllocated buf;
};

template <typename ReadHandler>
class Tun : public TunIO<ReadHandler, PacketFrom, openvpn_io::posix::stream_descriptor>
{
    typedef TunIO<ReadHandler, PacketFrom, openvpn_io::posix::stream_descriptor> Base;

  public:
    typedef RCPtr<Tun> Ptr;

    Tun(openvpn_io::io_context &io_context,
        ReadHandler read_handler_arg,
        const Frame::Ptr &frame_arg,
        const SessionStats::Ptr &stats_arg,
        const int socket,
        const std::string &name)
        : Base(read_handler_arg, frame_arg, stats_arg)
    {
        Base::name_ = name;
        Base::retain_stream = true;
        Base::stream = new openvpn_io::posix::stream_descriptor(io_context, socket);
        OPENVPN_LOG_TUN(Base::name_ << " opened");
    }

    ~Tun()
    {
        Base::stop();
    }
};

typedef TunPersistTemplate<ScopedFD> TunPersist;

class ClientConfig : public TunClientFactory
{
  public:
    typedef RCPtr<ClientConfig> Ptr;

    std::string dev_name;
    int txqueuelen = 200;

    TunProp::Config tun_prop;

    bool generate_tun_builder_capture_event = false;

    int n_parallel = 8;
    Frame::Ptr frame;
    SessionStats::Ptr stats;

    TunBuilderSetup::Factory::Ptr tun_setup_factory;
    TunPersist::Ptr tun_persist;

    void load(const OptionList &opt)
    {
        // set a default MTU
        if (!tun_prop.mtu)
            tun_prop.mtu = TUN_MTU_DEFAULT;

        // parse "dev" option
        if (dev_name.empty())
        {
            const Option *dev = opt.get_ptr("dev");
            if (dev)
                dev_name = dev->get(1, 64);
        }
    }

    static Ptr new_obj()
    {
        return new ClientConfig;
    }

    virtual TunClient::Ptr new_tun_client_obj(openvpn_io::io_context &io_context,
                                              TunClientParent &parent,
                                              TransportClient *transcli);

    TunBuilderSetup::Base::Ptr new_setup_obj()
    {
        if (tun_setup_factory)
            return tun_setup_factory->new_setup_obj();
        else
            return new TunLinuxSetup::Setup<TUN_LINUX>();
    }

  private:
    ClientConfig()
    {
    }
};

class Client : public TunClient
{
    friend class ClientConfig;                                                      // calls constructor
    friend class TunIO<Client *, PacketFrom, openvpn_io::posix::stream_descriptor>; // calls tun_read_handler

    typedef Tun<Client *> TunImpl;

  public:
    virtual void tun_start(const OptionList &opt, TransportClient &transcli, CryptoDCSettings &) override
    {
        if (!impl)
        {
            halt = false;

            if (config->tun_persist)
            {
                OPENVPN_LOG("TunPersist: long-term session scope");
                tun_persist = config->tun_persist; // long-term persistent
            }
            else
            {
                OPENVPN_LOG("TunPersist: short-term connection scope");
                tun_persist.reset(new TunPersist(true, TunWrapObjRetain::NO_RETAIN, nullptr)); // short-term
            }

            try
            {
                const IP::Addr server_addr = transcli.server_endpoint_addr();

                int sd = -1;

                // Check if persisted tun session matches properties of to-be-created session
                if (tun_persist->use_persisted_tun(server_addr, config->tun_prop, opt))
                {
                    state = tun_persist->state();
                    sd = tun_persist->obj();
                    state = tun_persist->state();
                    OPENVPN_LOG("TunPersist: reused tun context");
                }
                else
                {
                    // notify parent
                    parent.tun_pre_tun_config();

                    // close old tun handle if persisted
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

                    OPENVPN_LOG("CAPTURED OPTIONS:" << std::endl
                                                    << po->to_string());

                    // create new tun setup object
                    tun_setup = config->new_setup_obj();

                    // create config object for tun setup layer
                    TunLinuxSetup::Setup<TUN_LINUX>::Config tsconf;
                    tsconf.layer = config->tun_prop.layer;
                    tsconf.dev_name = config->dev_name;
                    tsconf.txqueuelen = config->txqueuelen;
                    tsconf.add_bypass_routes_on_establish = true;

                    // open/config tun
                    {
                        std::ostringstream os;
                        auto os_print = Cleanup([&os]()
                                                { OPENVPN_LOG_STRING(os.str()); });
                        sd = tun_setup->establish(*po, &tsconf, nullptr, os);
                    }

#if defined(HAVE_JSON)
                    if (config->generate_tun_builder_capture_event)
                    {
                        // create an event with TunBuilderCapture data as JSON
                        parent.tun_event(new ClientEvent::InfoJSON("TUN_BUILDER_CAPTURE", po->to_json()));
                    }
#endif

                    // persist tun settings state
                    state->iface_name = tsconf.iface_name;
                    tun_persist->persist_tun_state(sd, state);

                    // enable tun_setup destructor
                    tun_persist->add_destructor(tun_setup);
                }

                // start tun
                impl.reset(new TunImpl(io_context,
                                       this,
                                       config->frame,
                                       config->stats,
                                       sd,
                                       state->iface_name));
                impl->start(config->n_parallel);

                // signal that we are connected
                parent.tun_connected();
            }
            catch (const std::exception &e)
            {
                if (tun_persist)
                    tun_persist->close();

                stop();
                parent.tun_error(Error::TUN_SETUP_FAILED, e.what());
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
          state(new TunProp::State()),
          halt(false)
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
    TunPersist::Ptr tun_persist;
    ClientConfig::Ptr config;
    TunClientParent &parent;
    TunImpl::Ptr impl;
    TunProp::State::Ptr state;
    TunBuilderSetup::Base::Ptr tun_setup;
    bool halt;
};

inline TunClient::Ptr ClientConfig::new_tun_client_obj(openvpn_io::io_context &io_context,
                                                       TunClientParent &parent,
                                                       TransportClient *transcli)
{
    return TunClient::Ptr(new Client(io_context, this, parent));
}

} // namespace TunLinux
} // namespace openvpn

#endif // OPENVPN_TUN_LINUX_CLIENT_TUNCLI_H
