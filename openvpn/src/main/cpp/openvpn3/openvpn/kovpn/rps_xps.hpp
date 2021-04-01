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

#pragma once

#include <string>
#include <algorithm>

#include <openvpn/common/string.hpp>
#include <openvpn/common/number.hpp>
#include <openvpn/common/options.hpp>
#include <openvpn/common/core.hpp>
#include <openvpn/common/hexstr.hpp>
#include <openvpn/common/enumdir.hpp>
#include <openvpn/linux/procfs.hpp>

namespace openvpn {

  // Set RPS/XPS on interface.
  // These settings are documented in <linux-kernel>/Documentation/networking/scaling.txt
  class Configure_RPS_XPS
  {
  public:
    Configure_RPS_XPS()
    {
    }

    Configure_RPS_XPS(const OptionList& opt)
    {
      rps_cpus = opt.get_default("rps-cpus", 1, 256, rps_cpus);
      rps_flow_cnt = opt.get_default("rps-flow-cnt", 1, 256, rps_flow_cnt);
      xps_cpus = opt.get_default("xps-cpus", 1, 256, xps_cpus);
    }

    void set_all(const std::string& dev_name, Stop* async_stop) const
    {
      const bool dir_exists = enum_dir(fmt_qdir(dev_name), [&](std::string fn) {
        if (!(fn.length() >= 4
	      && (fn[0] =='r' || fn[0] == 't')
	      && fn[1] == 'x'
	      && fn[2] == '-'))
	  return;
	const unsigned int dqi = parse_number_throw<unsigned int>(fn.substr(3), "Configure_RPS_XPS: error parsing queue index");
	if (fn[0] == 'r')
	  set_rx(dev_name, dqi, async_stop);
	else if (fn[0] == 't')
	  set_tx(dev_name, dqi, async_stop);
      });
      if (!dir_exists)
	throw Exception("Configure_RPS_XPS: error locating device " + dev_name);
    }

    void set(const std::string& dev_name, const unsigned int dev_queue_index, Stop* async_stop) const
    {
      set_rx(dev_name, dev_queue_index, async_stop);
      set_tx(dev_name, dev_queue_index, async_stop);
    }

    void set_rx(const std::string& dev_name, const unsigned int dev_queue_index, Stop* async_stop) const
    {
      write_cpu_bits(fmt_qfn(dev_name, "rx", dev_queue_index, "rps_cpus"), rps_cpus, async_stop);
      ProcFS::write_sys(fmt_qfn(dev_name, "rx", dev_queue_index, "rps_flow_cnt"), rps_flow_cnt, async_stop);
    }

    void set_tx(const std::string& dev_name, const unsigned int dev_queue_index, Stop* async_stop) const
    {
      write_cpu_bits(fmt_qfn(dev_name, "tx", dev_queue_index, "xps_cpus"), xps_cpus, async_stop);
    }

#ifndef UNIT_TEST
  private:
#endif
    static std::string fmt_qfn(const std::string& dev, const std::string& type, int qnum, const std::string& bn)
    {
      return fmt_qdir(dev) + '/' + type + "-" + std::to_string(qnum) + '/' + bn;
    }

    static std::string fmt_qdir(const std::string& dev)
    {
      return "/sys/class/net/" + dev + "/queues";
    }

    static void write_cpu_bits(const std::string& fn, const std::string& param, Stop* async_stop)
    {
      if (param == "ALL")
	ProcFS::write_sys(fn, make_bit_string(n_cores()), async_stop);
      else
	ProcFS::write_sys(fn, param, async_stop);
    }

    // generate a variable length hex string with 1 bit
    // for each CPU, in the format expected by rps_cpus and xps_cpus
    // under /sys/class/net
    static std::string make_bit_string(const unsigned int n_cpus)
    {
      const unsigned int max_cpus = 1024;
      const unsigned int n = std::min(n_cpus, max_cpus); // sanity check
      const unsigned int q = n >> 2;
      const unsigned int r = n & 3;
      const unsigned int lead = (1 << r) - 1;

      std::string ret;
      if (lead || !q)
	ret += render_hex_char(lead);
      ret += string::repeat('f', q);
      return ret;
    }

    // defaults
    std::string rps_cpus{"0"};        // hex
    std::string rps_flow_cnt{"0"};    // dec
    std::string xps_cpus{"0"};        // hex
  };

}
