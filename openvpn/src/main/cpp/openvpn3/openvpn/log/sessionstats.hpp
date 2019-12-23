//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2017 OpenVPN Inc.
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

    enum Stats {
      // operating stats
      BYTES_IN = 0,        // network bytes in
      BYTES_OUT,           // network bytes out
      PACKETS_IN,          // network packets in
      PACKETS_OUT,         // network packets out
      TUN_BYTES_IN,        // tun/tap bytes in
      TUN_BYTES_OUT,       // tun/tap bytes out
      TUN_PACKETS_IN,      // tun/tap packets in
      TUN_PACKETS_OUT,     // tun/tap packets out
      N_STATS,
    };

    SessionStats()
      : verbose_(false)
    {
      std::memset((void *)stats_, 0, sizeof(stats_));
    }

    virtual void error(const size_t type, const std::string* text=nullptr) {}

    // if true, clients may provide additional detail to error() method above
    // via text argument.
    bool verbose() const { return verbose_; }

#ifdef OPENVPN_STATS_VIRTUAL
    virtual
#endif
    void inc_stat(const size_t type, const count_t value)
    {
      if (type < N_STATS)
	stats_[type] += value;
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

      if (type < N_STATS)
	return names[type];
      else
	return "UNKNOWN_STAT_TYPE";
    }

    void update_last_packet_received(const Time& now)
    {
      last_packet_received_ = now;
    }

    const Time& last_packet_received() const { return last_packet_received_; }

    struct DCOTransportSource : public virtual RC<thread_unsafe_refcount>
    {
      typedef RCPtr<DCOTransportSource> Ptr;

      struct Data {
	count_t bytes_in;
	count_t bytes_out;

	Data()
	  : bytes_in(0),
	    bytes_out(0)
	{
	}

	Data(count_t bytes_in_arg, count_t bytes_out_arg)
	  : bytes_in(bytes_in_arg),
	    bytes_out(bytes_out_arg)
	{
	}

	Data operator-(const Data& rhs) const
	{
	  Data data;
	  if (bytes_in > rhs.bytes_in)
	    data.bytes_in = bytes_in - rhs.bytes_in;
	  if (bytes_out > rhs.bytes_out)
	    data.bytes_out = bytes_out - rhs.bytes_out;
	  return data;
	}
      };

      virtual Data dco_transport_stats_delta() = 0;
    };

    void dco_configure(SessionStats::DCOTransportSource* source)
    {
      dco_.reset(source);
    }

    void dco_update()
    {
      if (dco_)
	{
	  const DCOTransportSource::Data data = dco_->dco_transport_stats_delta();
	  stats_[BYTES_IN] += data.bytes_in;
	  stats_[BYTES_OUT] += data.bytes_out;
	}
    }

  protected:
    void session_stats_set_verbose(const bool v) { verbose_ = v; }

  private:
    bool verbose_;
    Time last_packet_received_;
    DCOTransportSource::Ptr dco_;
    volatile count_t stats_[N_STATS];
  };

} // namespace openvpn

#endif // OPENVPN_LOG_SESSIONSTATS_H
