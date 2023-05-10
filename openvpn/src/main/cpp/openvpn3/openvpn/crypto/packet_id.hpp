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

// Manage OpenVPN protocol Packet IDs for packet replay detection

#ifndef OPENVPN_CRYPTO_PACKET_ID_H
#define OPENVPN_CRYPTO_PACKET_ID_H

#include <string>
#include <cstring>
#include <sstream>
#include <cstdint> // for std::uint32_t

#include <openvpn/io/io.hpp>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/circ_list.hpp>
#include <openvpn/common/socktypes.hpp>
#include <openvpn/common/likely.hpp>
#include <openvpn/time/time.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/log/sessionstats.hpp>

namespace openvpn {
/*
 * Communicate packet-id over the wire.
 * A short packet-id is just a 32 bit
 * sequence number.  A long packet-id
 * includes a timestamp as well.
 *
 * Long packet-ids are used as IVs for
 * CFB/OFB ciphers.
 *
 * This data structure is always sent
 * over the net in network byte order,
 * by calling htonpid, ntohpid,
 * htontime, and ntohtime on the
 * data elements to change them
 * to and from standard sizes.
 *
 * In addition, time is converted to
 * a PacketID::net_time_t before sending,
 * since openvpn always
 * uses a 32-bit time_t but some
 * 64 bit platforms use a
 * 64 bit time_t.
 */
struct PacketID
{
    typedef std::uint32_t id_t;
    typedef std::uint32_t net_time_t;
    typedef Time::base_type time_t;

    enum
    {
        SHORT_FORM = 0, // short form of ID (4 bytes)
        LONG_FORM = 1,  // long form of ID (8 bytes)

        UNDEF = 0, // special undefined/null id_t value
    };

    id_t id;     // legal values are 1 through 2^32-1
    time_t time; // converted to PacketID::net_time_t before transmission

    static size_t size(const int form)
    {
        if (form == PacketID::LONG_FORM)
            return longidsize;
        else
            return shortidsize;
    }

    constexpr static size_t shortidsize = sizeof(id_t);
    constexpr static size_t longidsize = sizeof(id_t) + sizeof(net_time_t);

    bool is_valid() const
    {
        return id != UNDEF;
    }

    void reset()
    {
        id = id_t(0);
        time = time_t(0);
    }

    void read(Buffer &buf, const int form)
    {
        id_t net_id;
        net_time_t net_time;

        buf.read((unsigned char *)&net_id, sizeof(net_id));
        id = ntohl(net_id);

        if (form == LONG_FORM)
        {
            buf.read((unsigned char *)&net_time, sizeof(net_time));
            time = ntohl(net_time);
        }
        else
            time = time_t(0);
    }

    void write(Buffer &buf, const int form, const bool prepend) const
    {
        const id_t net_id = htonl(id);
        const net_time_t net_time = htonl(static_cast<uint32_t>(time & 0x00000000FFFFFFFF));
        // TODO: [OVPN3-931] Make our code handle rollover of this value gracefully as possible
        // since at the current time this will probably force a reconnect.

        if (prepend)
        {
            if (form == LONG_FORM)
                buf.prepend((unsigned char *)&net_time, sizeof(net_time));
            buf.prepend((unsigned char *)&net_id, sizeof(net_id));
        }
        else
        {
            buf.write((unsigned char *)&net_id, sizeof(net_id));
            if (form == LONG_FORM)
                buf.write((unsigned char *)&net_time, sizeof(net_time));
        }
    }

    std::string str() const
    {
        std::ostringstream os;
        os << std::hex << "[0x" << time << ", 0x" << id << "]";
        return os.str();
    }
};

struct PacketIDConstruct : public PacketID
{
    PacketIDConstruct(const PacketID::time_t v_time = PacketID::time_t(0), const PacketID::id_t v_id = PacketID::id_t(0))
    {
        id = v_id;
        time = v_time;
    }
};

class PacketIDSend
{
  public:
    OPENVPN_SIMPLE_EXCEPTION(packet_id_wrap);

    PacketIDSend()
    {
        init(PacketID::SHORT_FORM, 0);
    }

    PacketIDSend(const int form, PacketID::id_t startid)
    {
        init(PacketID::SHORT_FORM, startid);
    }

    /**
     * @param form PacketID::LONG_FORM or PacketID::SHORT_FORM
     * @param initial id for the sending
     */
    void init(const int form, PacketID::id_t startid)
    {
        pid_.id = PacketID::id_t(startid);
        pid_.time = PacketID::time_t(0);
        form_ = form;
    }

    void init(const int form)
    {
        init(form, 0);
    }

    PacketID next(const PacketID::time_t now)
    {
        PacketID ret;
        if (!pid_.time)
            pid_.time = now;
        ret.id = ++pid_.id;
        if (unlikely(!pid_.id)) // wraparound
        {
            if (form_ != PacketID::LONG_FORM)
                throw packet_id_wrap();
            pid_.time = now;
            ret.id = pid_.id = 1;
        }
        ret.time = pid_.time;
        return ret;
    }

    void write_next(Buffer &buf, const bool prepend, const PacketID::time_t now)
    {
        const PacketID pid = next(now);
        pid.write(buf, form_, prepend);
    }

    /*
     * In TLS mode, when a packet ID gets to this level,
     * start thinking about triggering a new
     * SSL/TLS handshake.
     */
    bool wrap_warning() const
    {
        const PacketID::id_t wrap_at = 0xFF000000;
        return pid_.id >= wrap_at;
    }

    std::string str() const
    {
        std::string ret;
        ret = pid_.str();
        if (form_ == PacketID::LONG_FORM)
            ret += 'L';
        return ret;
    }

  private:
    PacketID pid_;
    int form_;
};

/*
 * This is the data structure we keep on the receiving side,
 * to check that no packet-id (i.e. sequence number + optional timestamp)
 * is accepted more than once.
 *
 * Replay window sizing in bytes = 2^REPLAY_WINDOW_ORDER.
 * PKTID_RECV_EXPIRE is backtrack expire in seconds.
 */
template <unsigned int REPLAY_WINDOW_ORDER,
          unsigned int PKTID_RECV_EXPIRE>
class PacketIDReceiveType
{
  public:
    static constexpr unsigned int REPLAY_WINDOW_BYTES = 1 << REPLAY_WINDOW_ORDER;
    static constexpr unsigned int REPLAY_WINDOW_SIZE = REPLAY_WINDOW_BYTES * 8;

    // mode
    enum
    {
        UDP_MODE = 0,
        TCP_MODE = 1
    };

    OPENVPN_SIMPLE_EXCEPTION(packet_id_not_initialized);

    // TODO: [OVPN3-933] Consider RAII'ifying this code
    PacketIDReceiveType()
        : initialized_(false)
    {
    }

    void init(const int mode_arg,
              const int form_arg,
              const char *name_arg,
              const int unit_arg,
              const SessionStats::Ptr &stats_arg)
    {
        initialized_ = true;
        base = 0;
        extent = 0;
        expire = 0;
        id_high = 0;
        time_high = 0;
        id_floor = 0;
        max_backtrack = 0;
        mode = mode_arg;
        form = form_arg;
        unit = unit_arg;
        name = name_arg;
        stats = stats_arg;
        std::memset(history, 0, sizeof(history));
    }

    bool initialized() const
    {
        return initialized_;
    }

    bool test_add(const PacketID &pin,
                  const PacketID::time_t now,
                  const bool mod) // don't modify history unless mod is true
    {
        const Error::Type err = do_test_add(pin, now, mod);
        if (unlikely(err != Error::SUCCESS))
        {
            stats->error(err);
            return false;
        }
        else
            return true;
    }

    Error::Type do_test_add(const PacketID &pin,
                            const PacketID::time_t now,
                            const bool mod) // don't modify history unless mod is true
    {
        // make sure we were initialized
        if (unlikely(!initialized_))
            throw packet_id_not_initialized();

        // expire backtracks at or below id_floor after PKTID_RECV_EXPIRE time
        if (unlikely(now >= expire))
            id_floor = id_high;
        expire = now + PKTID_RECV_EXPIRE;

        // ID must not be zero
        if (unlikely(!pin.is_valid()))
            return Error::PKTID_INVALID;

        // time changed?
        if (unlikely(pin.time != time_high))
        {
            if (pin.time > time_high)
            {
                // time moved forward, accept
                if (!mod)
                    return Error::SUCCESS;
                base = 0;
                extent = 0;
                id_high = 0;
                time_high = pin.time;
                id_floor = 0;
            }
            else
            {
                // time moved backward, reject
                return Error::PKTID_TIME_BACKTRACK;
            }
        }

        if (likely(pin.id == id_high + 1))
        {
            // well-formed ID sequence (incremented by 1)
            if (!mod)
                return Error::SUCCESS;
            base = REPLAY_INDEX(-1);
            history[base / 8] |= static_cast<uint8_t>(1 << (base % 8));
            if (extent < REPLAY_WINDOW_SIZE)
                ++extent;
            id_high = pin.id;
        }
        else if (pin.id > id_high)
        {
            // ID jumped forward by more than one
            if (!mod)
                return Error::SUCCESS;
            const unsigned int delta = pin.id - id_high;
            if (delta < REPLAY_WINDOW_SIZE)
            {
                base = REPLAY_INDEX(-delta);
                history[base / 8] |= static_cast<uint8_t>(1 << (base % 8));
                extent += delta;
                if (extent > REPLAY_WINDOW_SIZE)
                    extent = REPLAY_WINDOW_SIZE;
                for (unsigned i = 1; i < delta; ++i)
                {
                    const unsigned int newbase = REPLAY_INDEX(i);
                    history[newbase / 8] &= static_cast<uint8_t>(~(1 << (newbase % 8)));
                }
            }
            else
            {
                base = 0;
                extent = REPLAY_WINDOW_SIZE;
                std::memset(history, 0, sizeof(history));
                history[0] = 1;
            }
            id_high = pin.id;
        }
        else
        {
            // ID backtrack
            const unsigned int delta = id_high - pin.id;
            if (delta > max_backtrack)
                max_backtrack = delta;
            if (delta < extent)
            {
                if (pin.id > id_floor)
                {
                    const unsigned int ri = REPLAY_INDEX(delta);
                    std::uint8_t *p = &history[ri / 8];
                    const std::uint8_t mask = static_cast<uint8_t>(1 << (ri % 8));
                    if (*p & mask)
                        return Error::PKTID_REPLAY;
                    if (!mod)
                        return Error::SUCCESS;
                    *p |= mask;
                }
                else
                    return Error::PKTID_EXPIRE;
            }
            else
                return Error::PKTID_BACKTRACK;
        }

        return Error::SUCCESS;
    }

    PacketID read_next(Buffer &buf) const
    {
        if (!initialized_)
            throw packet_id_not_initialized();
        PacketID pid;
        pid.read(buf, form);
        return pid;
    }

    std::string str() const
    {
        std::ostringstream os;
        os << "[e=" << extent << " f=" << id_floor << " h=" << time_high << '/' << id_high << ']';
        return os.str();
    }

  private:
    unsigned int REPLAY_INDEX(const int i) const
    {
        return (base + i) & (REPLAY_WINDOW_SIZE - 1);
    }

    bool initialized_;

    unsigned int base;          // bit position of deque base in history
    unsigned int extent;        // extent (in bits) of deque in history
    PacketID::time_t expire;    // expiration of history
    PacketID::id_t id_high;     // highest sequence number received
    PacketID::time_t time_high; // highest time stamp received
    PacketID::id_t id_floor;    // we will only accept backtrack IDs > id_floor
    unsigned int max_backtrack;

    int mode;         // UDP_MODE or TCP_MODE
    int form;         // PacketID::LONG_FORM or PacketID::SHORT_FORM
    int unit;         // unit number of this object (for debugging)
    std::string name; // name of this object (for debugging)

    SessionStats::Ptr stats;

    std::uint8_t history[REPLAY_WINDOW_BYTES]; /* "sliding window" bitmask of recent packet IDs received */
};

// Our standard packet ID window with order=8 (window size=2048).
// and recv expire=30 seconds.
typedef PacketIDReceiveType<8, 30> PacketIDReceive;

} // namespace openvpn

#endif // OPENVPN_CRYPTO_PACKET_ID_H
