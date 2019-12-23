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

#ifndef OPENVPN_KOVPN_KOSTATS_H
#define OPENVPN_KOVPN_KOSTATS_H

#include <algorithm> // for std::min, std::max
#include <memory>
#include <atomic>

#include <sys/ioctl.h>

#include <openvpn/common/size.hpp>
#include <openvpn/common/arraysize.hpp>
#include <openvpn/common/core.hpp>
#include <openvpn/kovpn/kovpn.hpp>

namespace openvpn {
  namespace kostats_private {
#   include <kovpn/ovpnerrstr.c>
  }

  class KovpnStats
  {
  public:
    void set_fd(const int fd)
    {
      kovpn_fd.store(fd, std::memory_order_relaxed);
    }

    void output_stats(std::ostream& os) const
    {
      struct ovpn_stats stats;
      if (::ioctl(get_fd(), OVPN_DEV_STATS, &stats) < 0)
	return;
      os << "STAT.BYTES_IN," << (stats.rx_bytes + cc_rx_bytes.load(std::memory_order_relaxed)) << '\n';
      os << "STAT.BYTES_OUT," << stats.tx_bytes << '\n';
    }

    void output_percpu(std::ostream& os) const
    {
      std::unique_ptr<struct ovpn_percpu_stats> pcs;
      unsigned int stats_cap = std::max(16, n_cores());
      for (int i = 0; i < 2; ++i)
	{
	  const size_t pcs_size = sizeof(struct ovpn_percpu_stats) +
	                          sizeof(struct ovpn_percpu_stat) * stats_cap;
	  pcs.reset((struct ovpn_percpu_stats *) ::operator new(pcs_size));
	  pcs->total_stats = 0;
	  pcs->n_stats = stats_cap;
	  if (::ioctl(get_fd(), OVPN_PERCPU_STATS, (void *)pcs.get()) < 0)
	    return;
	  stats_cap = std::max(stats_cap, pcs->total_stats);
	  if (pcs->total_stats <= pcs->n_stats)
	    break;
	}
      const size_t n = std::min(pcs->total_stats, pcs->n_stats);
      for (size_t i = 0; i < n; ++i)
	{
	  const struct ovpn_percpu_stat *s = &pcs->stats[i];
	  if (s->rx_bytes || s->tx_bytes)
	    {
	      os << "KOVPN.STAT.CPU-" << i << ".BYTES_IN," << s->rx_bytes << '\n';
	      os << "KOVPN.STAT.CPU-" << i << ".BYTES_OUT," << s->tx_bytes << '\n';
	    }
	}
    }

    void output_err_counters(std::ostream& os) const
    {
      std::unique_ptr<struct ovpn_err_stats> esp;
      unsigned int stats_cap = 128;
      for (int i = 0; i < 2; ++i)
	{
	  const size_t es_size = sizeof(struct ovpn_err_stats) +
	                         sizeof(struct ovpn_err_stat) * stats_cap;
	  esp.reset((struct ovpn_err_stats *) ::operator new(es_size));
	  esp->total_stats = 0;
	  esp->n_stats = stats_cap;
	  if (::ioctl(get_fd(), OVPN_ERR_STATS, (void *)esp.get()) < 0)
	    return;
	  stats_cap = std::max(stats_cap, esp->total_stats);
	  if (esp->total_stats <= esp->n_stats)
	    break;
	}
      const size_t n = std::min(esp->total_stats, esp->n_stats);
      for (size_t i = 0; i < n; ++i)
	{
	  const struct ovpn_err_stat *s = &esp->stats[i];
	  os << "KOVPN";
	  const char *cat = cat_name(s->category);
	  if (cat)
	    {
	      os << '.';
	      os << cat;
	    }
	  const char *err = err_name(s->errcode);
	  if (err)
	    {
	      os << '.';
	      os << err;
	    }
	  os << ',' << s->count << '\n';
	}
    }

    void increment_cc_rx_bytes(const std::uint64_t value)
    {
      cc_rx_bytes.fetch_add(value, std::memory_order_relaxed);
    }

    static const char *errstr(const size_t i)
    {
      const char *ret = err_name(i);
      if (ret)
	return ret;
      else
	return "";
    }

  private:
    static const char *err_name(const size_t i)
    {
      if (i < array_size(kostats_private::ovpn_err_names))
	return kostats_private::ovpn_err_names[i];
      else
	return nullptr;
    }

    static const char *cat_name(const size_t i)
    {
      if (i < array_size(kostats_private::ovpn_errcat_names))
	return kostats_private::ovpn_errcat_names[i];
      else
	return nullptr;
    }

    int get_fd() const
    {
      return kovpn_fd.load(std::memory_order_relaxed);
    }

    std::atomic<int> kovpn_fd{-1};
    std::atomic<uint_fast64_t> cc_rx_bytes{0};
  };
}

#endif
