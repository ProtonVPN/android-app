//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012- OpenVPN Inc.
//
//    SPDX-License-Identifier: MPL-2.0 OR AGPL-3.0-only WITH openvpn3-openssl-exception
//

// A class that handles statistics tracking in an OpenVPN session

#ifndef OPENVPN_LOG_SESSIONSTATS_H
#define OPENVPN_LOG_SESSIONSTATS_H

#include <cstring>

#include <openvpn/common/size.hpp>
#include <openvpn/common/count.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/error/error.hpp>
#include <openvpn/time/time.hpp>

namespace openvpn {

class SessionStats : public RC<thread_safe_refcount>
{
  public:
    typedef RCPtr<SessionStats> Ptr;
    using inc_callback_t = std::function<void(const count_t value)>;

    enum Stats : unsigned int
    {
        // operating stats
        BYTES_IN = 0,    // network bytes in
        BYTES_OUT,       // network bytes out
        PACKETS_IN,      // network packets in
        PACKETS_OUT,     // network packets out
        TUN_BYTES_IN,    // tun/tap bytes in
        TUN_BYTES_OUT,   // tun/tap bytes out
        TUN_PACKETS_IN,  // tun/tap packets in
        TUN_PACKETS_OUT, // tun/tap packets out
        N_STATS,
    };

    SessionStats()
        : verbose_(false)
    {
        std::memset((void *)stats_, 0, sizeof(stats_));
    }

    virtual void error(const size_t type, const std::string *text = nullptr)
    {
    }

    // if true, clients may provide additional detail to error() method above
    // via text argument.
    bool verbose() const
    {
        return verbose_;
    }

#ifdef OPENVPN_STATS_VIRTUAL
    virtual
#endif
        void
        inc_stat(const size_t type, const count_t value)
    {
        if (type < N_STATS)
        {
            stats_[type] = stats_[type] + value;
            if (auto lock = inc_callbacks_[type].lock())
                std::invoke(*lock, value);
        }
    }

    count_t get_stat(const size_t type) const
    {
        if (type < N_STATS)
            return stats_[type];
        else
            return 0;
    }

    count_t get_stat_fast(const size_t type) const
    {
        return stats_[type];
    }

    static const char *stat_name(const size_t type)
    {
        static const char *names[] = {
            "BYTES_IN",
            "BYTES_OUT",
            "PACKETS_IN",
            "PACKETS_OUT",
            "TUN_BYTES_IN",
            "TUN_BYTES_OUT",
            "TUN_PACKETS_IN",
            "TUN_PACKETS_OUT",
        };

        static_assert(N_STATS == array_size(names), "stats names array inconsistency");
        if (type < N_STATS)
            return names[type];
        else
            return "UNKNOWN_STAT_TYPE";
    }

    void update_last_packet_received(const Time &now)
    {
        last_packet_received_ = now;
    }

    const Time &last_packet_received() const
    {
        return last_packet_received_;
    }

    struct DCOTransportSource : public virtual RC<thread_unsafe_refcount>
    {
        typedef RCPtr<DCOTransportSource> Ptr;

        struct Data
        {
            count_t transport_bytes_in = 0;
            count_t transport_bytes_out = 0;
            count_t tun_bytes_in = 0;
            count_t tun_bytes_out = 0;

            count_t transport_pkts_in = 0;
            count_t transport_pkts_out = 0;
            count_t tun_pkts_in = 0;
            count_t tun_pkts_out = 0;

            Data() = default;

            Data(count_t transport_bytes_in_arg, count_t transport_bytes_out_arg)
                : transport_bytes_in(transport_bytes_in_arg),
                  transport_bytes_out(transport_bytes_out_arg)
            {
            }

            Data(count_t transport_bytes_in_arg, count_t transport_bytes_out_arg, count_t tun_bytes_in_arg, count_t tun_bytes_out_arg)
                : transport_bytes_in(transport_bytes_in_arg),
                  transport_bytes_out(transport_bytes_out_arg),
                  tun_bytes_in(tun_bytes_in_arg), tun_bytes_out(tun_bytes_out_arg)
            {
            }

            Data(count_t transport_bytes_in_arg,
                 count_t transport_bytes_out_arg,
                 count_t tun_bytes_in_arg,
                 count_t tun_bytes_out_arg,
                 count_t transport_pkts_in_arg,
                 count_t transport_pkts_out_arg,
                 count_t tun_pkts_in_arg,
                 count_t tun_pkts_out_arg)

                : transport_bytes_in(transport_bytes_in_arg),
                  transport_bytes_out(transport_bytes_out_arg),
                  tun_bytes_in(tun_bytes_in_arg),
                  tun_bytes_out(tun_bytes_out_arg),
                  transport_pkts_in(transport_pkts_in_arg),
                  transport_pkts_out(transport_pkts_out_arg),
                  tun_pkts_in(tun_pkts_in_arg),
                  tun_pkts_out(tun_pkts_out_arg)
            {
            }

            Data operator-(const Data &rhs) const
            {
                Data data;
                if (transport_bytes_in > rhs.transport_bytes_in)
                    data.transport_bytes_in = transport_bytes_in - rhs.transport_bytes_in;
                if (transport_bytes_out > rhs.transport_bytes_out)
                    data.transport_bytes_out = transport_bytes_out - rhs.transport_bytes_out;
                if (tun_bytes_in > rhs.tun_bytes_in)
                    data.tun_bytes_in = tun_bytes_in - rhs.tun_bytes_in;
                if (tun_bytes_out > rhs.tun_bytes_out)
                    data.tun_bytes_out = tun_bytes_out - rhs.tun_bytes_out;
                if (transport_pkts_in > rhs.transport_pkts_in)
                    data.transport_pkts_in = transport_pkts_in - rhs.transport_pkts_in;
                if (transport_pkts_out > rhs.transport_pkts_out)
                    data.transport_pkts_out = transport_pkts_out - rhs.transport_pkts_out;
                if (tun_pkts_in > rhs.tun_pkts_in)
                    data.tun_pkts_in = tun_pkts_in - rhs.tun_pkts_in;
                if (tun_pkts_out > rhs.tun_pkts_out)
                    data.tun_pkts_out = tun_pkts_out - rhs.tun_pkts_out;
                return data;
            }
        };

        virtual Data dco_transport_stats_delta() = 0;
    };

    void dco_configure(SessionStats::DCOTransportSource *source)
    {
        dco_.reset(source);
    }

    bool dco_update()
    {
        if (dco_)
        {
            const DCOTransportSource::Data data = dco_->dco_transport_stats_delta();

            if (data.transport_bytes_in > 0)
            {
                update_last_packet_received(Time::now());
            }

            // Not using += because volatile compound assignment has been deprecated.
            stats_[BYTES_IN] = stats_[BYTES_IN] + data.transport_bytes_in;
            stats_[BYTES_OUT] = stats_[BYTES_OUT] + data.transport_bytes_out;
            stats_[TUN_BYTES_IN] = stats_[TUN_BYTES_IN] + data.tun_bytes_in;
            stats_[TUN_BYTES_OUT] = stats_[TUN_BYTES_OUT] + data.tun_bytes_out;
            stats_[PACKETS_IN] = stats_[PACKETS_IN] + data.transport_pkts_in;
            stats_[PACKETS_OUT] = stats_[PACKETS_OUT] + data.transport_pkts_out;
            stats_[TUN_PACKETS_IN] = stats_[TUN_PACKETS_IN] + data.tun_pkts_in;
            stats_[TUN_PACKETS_OUT] = stats_[TUN_PACKETS_OUT] + data.tun_pkts_out;

            return true;
        }

        return false;
    }

    /**
     * @brief Sets a callback to be triggered upon increment of stats
     *
     * The callback can be removed by client code by deleting the returned shared pointer
     *
     * @param stat Type of stat to be tracked
     * @param callback Notification callback
     * @return Shared pointer which maintains the lifetime of the callback
     */
    [[nodiscard]] std::shared_ptr<inc_callback_t> set_inc_callback(Stats stat, inc_callback_t callback)
    {
        auto cb_ptr = std::make_shared<inc_callback_t>(callback);
        inc_callbacks_[stat] = cb_ptr;
        return cb_ptr;
    }

  protected:
    void session_stats_set_verbose(const bool v)
    {
        verbose_ = v;
    }

  private:
    bool verbose_;
    Time last_packet_received_;
    DCOTransportSource::Ptr dco_;
    volatile count_t stats_[N_STATS];
    std::array<std::weak_ptr<inc_callback_t>, N_STATS> inc_callbacks_;
};

} // namespace openvpn

#endif // OPENVPN_LOG_SESSIONSTATS_H
