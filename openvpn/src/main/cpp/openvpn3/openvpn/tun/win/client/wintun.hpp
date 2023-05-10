#pragma once

#include <openvpn/tun/client/tunbase.hpp>
#include <openvpn/tun/persist/tunpersist.hpp>
#include <openvpn/tun/win/client/setupbase.hpp>
#include <openvpn/tun/win/client/clientconfig.hpp>
#include <openvpn/win/modname.hpp>

namespace openvpn {
namespace TunWin {

class WintunClient : public TunClient
{
    typedef RCPtr<WintunClient> Ptr;

  public:
    WintunClient(openvpn_io::io_context &io_context_arg,
                 ClientConfig *config_arg,
                 TunClientParent &parent_arg)
        : io_context(io_context_arg),
          config(config_arg),
          parent(parent_arg),
          state(new TunProp::State()),
          frame(config_arg->frame)
    {
    }

    // Inherited via TunClient
    void tun_start(const OptionList &opt, TransportClient &transcli, CryptoDCSettings &) override
    {
        halt = false;
        if (config->tun_persist)
            tun_persist = config->tun_persist; // long-term persistent
        else
            tun_persist.reset(new TunPersist(false, TunWrapObjRetain::NO_RETAIN, nullptr)); // short-term

        try
        {

            const IP::Addr server_addr = transcli.server_endpoint_addr();

            // Check if persisted tun session matches properties of to-be-created session
            if (tun_persist->use_persisted_tun(server_addr, config->tun_prop, opt))
            {
                state = tun_persist->state().state;
                ring_buffer = tun_persist->state().adapter_state;
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
                OPENVPN_LOG("CAPTURED OPTIONS:" << std::endl
                                                << po->to_string());

                // create new tun setup object
                tun_setup = config->new_setup_obj(io_context);

                ring_buffer.reset(new RingBuffer(io_context));

                // open/config TAP
                HANDLE th;
                {
                    std::ostringstream os;
                    auto os_print = Cleanup([&os]()
                                            { OPENVPN_LOG_STRING(os.str()); });
                    th = tun_setup->establish(*po, Win::module_name(), config->stop, os, ring_buffer);
                }

                // create ASIO wrapper for HANDLE
                TAPStream *ts = new TAPStream(io_context, th);

                // persist tun settings state
                if (tun_persist->persist_tun_state(ts, {state, ring_buffer}))
                    OPENVPN_LOG("TunPersist: saving tun context:" << std::endl
                                                                  << tun_persist->options());

                // enable tun_setup destructor
                tun_persist->add_destructor(tun_setup);

                // assert ownership over TAP device handle
                tun_setup->confirm();
            }

            openvpn_io::post(io_context, [self = Ptr(this)]()
                             { self->read(); });

            parent.tun_connected(); // signal that we are connected
        }
        catch (const std::exception &e)
        {
            stop();
            Error::Type err = Error::TUN_SETUP_FAILED;
            const ExceptionCode *ec = dynamic_cast<const ExceptionCode *>(&e);
            if (ec && ec->code_defined())
                err = ec->code();
            parent.tun_error(err, e.what());
        }
    }

    void stop() override
    {
        if (!halt)
        {
            halt = true;

            tun_persist.reset();
        }
    }

    void set_disconnect() override
    {
    }

    bool tun_send(BufferAllocated &buf) override
    {
        TUN_RING *receive_ring = ring_buffer->receive_ring();

        ULONG head = receive_ring->head.load(std::memory_order_acquire);
        if (head > WINTUN_RING_CAPACITY)
        {
            if (head == 0xFFFFFFFF)
                parent.tun_error(Error::TUN_WRITE_ERROR, "invalid ring head/tail or bogus packet received");
            return false;
        }

        ULONG tail = receive_ring->tail.load(std::memory_order_acquire);
        if (tail >= WINTUN_RING_CAPACITY)
            return false;

        ULONG aligned_packet_size = packet_align(sizeof(TUN_PACKET_HEADER) + buf.size());
        ULONG buf_space = wrap(head - tail - WINTUN_PACKET_ALIGN);
        if (aligned_packet_size > buf_space)
        {
            OPENVPN_LOG("ring is full");
            return false;
        }

        // copy packet size and data into ring
        TUN_PACKET *packet = (TUN_PACKET *)&receive_ring->data[tail];
        packet->size = buf.size();
        std::memcpy(packet->data, buf.data(), buf.size());

        // move ring tail
        receive_ring->tail.store(wrap(tail + aligned_packet_size), std::memory_order_release);
        if (receive_ring->alertable.load(std::memory_order_acquire) != 0)
            SetEvent(ring_buffer->receive_ring_tail_moved());

        return true;
    }

    std::string tun_name() const override
    {
        return "wintun";
    }

    std::string vpn_ip4() const override
    {
        if (state->vpn_ip4_addr.specified())
            return state->vpn_ip4_addr.to_string();
        else
            return "";
    }

    std::string vpn_ip6() const override
    {
        if (state->vpn_ip6_addr.specified())
            return state->vpn_ip6_addr.to_string();
        else
            return "";
    }

    std::string vpn_gw4() const override
    {
        if (state->vpn_ip4_gw.specified())
            return state->vpn_ip4_gw.to_string();
        else
            return "";
    }

    std::string vpn_gw6() const override
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

  private:
    void read()
    {
        TUN_RING *send_ring = ring_buffer->send_ring();

        if (halt)
            return;

        ULONG head = send_ring->head.load(std::memory_order_acquire);
        if (head >= WINTUN_RING_CAPACITY)
        {
            parent.tun_error(Error::TUN_ERROR, "ring head exceeds ring capacity");
            return;
        }

        ULONG tail = send_ring->tail.load(std::memory_order_acquire);
        if (tail >= WINTUN_RING_CAPACITY)
        {
            parent.tun_error(Error::TUN_ERROR, "ring tail exceeds ring capacity");
            return;
        }

        while (true)
        {
            // tail has moved?
            if (head == tail)
            {
                ring_buffer->send_tail_moved_asio_event().async_wait([self = Ptr(this)](const openvpn_io::error_code &error)
                                                                     {
		  if (!error)
		    self->read();
		  else
		    {
		      if (!self->halt)
			self->parent.tun_error(Error::TUN_ERROR, "error waiting on ring send tail moved");
		    } });
                return;
            }

            // read buffer content
            ULONG content_len = wrap(tail - head);
            if (content_len < sizeof(TUN_PACKET_HEADER))
            {
                parent.tun_error(Error::TUN_ERROR, "incomplete packet header in send ring");
                return;
            }

            TUN_PACKET *packet = (TUN_PACKET *)&send_ring->data[head];
            if (packet->size > WINTUN_MAX_PACKET_SIZE)
            {
                parent.tun_error(Error::TUN_ERROR, "packet too big in send ring");
                return;
            }

            ULONG aligned_packet_size = packet_align(sizeof(TUN_PACKET_HEADER) + packet->size);
            if (aligned_packet_size > content_len)
            {
                parent.tun_error(Error::TUN_ERROR, "incomplete packet in send ring");
                return;
            }

            frame->prepare(Frame::READ_TUN, buf);

            buf.write(packet->data, packet->size);

            head = wrap(head + aligned_packet_size);
            send_ring->head.store(head, std::memory_order_release);

            parent.tun_recv(buf);

            if (halt)
                return;
        }
    }

    struct TUN_PACKET_HEADER
    {
        uint32_t size;
    };

    struct TUN_PACKET
    {
        uint32_t size;
        UCHAR data[WINTUN_MAX_PACKET_SIZE];
    };

    ULONG packet_align(ULONG size)
    {
        return (size + (WINTUN_PACKET_ALIGN - 1)) & ~(WINTUN_PACKET_ALIGN - 1);
    }

    ULONG wrap(ULONG value)
    {
        return value & (WINTUN_RING_CAPACITY - 1);
    }

    openvpn_io::io_context &io_context;
    TunPersist::Ptr tun_persist; // contains the TAP device HANDLE
    ClientConfig::Ptr config;
    TunClientParent &parent;
    TunProp::State::Ptr state;
    TunWin::SetupBase::Ptr tun_setup;

    BufferAllocated buf;

    Frame::Ptr frame;

    bool halt = false;

    ScopedHANDLE driver_handle;

    RingBuffer::Ptr ring_buffer;
};
} // namespace TunWin
} // namespace openvpn
