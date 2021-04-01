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

#ifndef OPENVPN_TIME_DURHELPER_H
#define OPENVPN_TIME_DURHELPER_H

#include <openvpn/common/options.hpp>
#include <openvpn/time/time.hpp>
#include <openvpn/random/randapi.hpp>

namespace openvpn {
  inline void set_duration_parm(Time::Duration& dur,
				const std::string& name,
				const std::string& valstr,
				const unsigned int min_value,
				const bool x2, // multiply result by 2
				const bool ms) // values are in milliseconds rather than seconds
  {
    const unsigned int maxdur = ms ? 1000*60*60*24 : 60*60*24*7; // maximum duration -- milliseconds: 1 day, seconds: 7 days
    unsigned int value = 0;
    const bool status = parse_number<unsigned int>(valstr, value);
    if (!status)
      OPENVPN_THROW(option_error, name << ": error parsing number of " << (ms ? "milliseconds" : "seconds"));
    if (x2)
      value *= 2;
    if (value == 0 || value > maxdur)
      value = maxdur;
    if (value < min_value)
      value = min_value;
    dur = ms ? Time::Duration::milliseconds(value) : Time::Duration::seconds(value);
  }

  inline const Option* load_duration_parm(Time::Duration& dur,
					  const std::string& name,
					  const OptionList& opt,
					  const unsigned int min_value,
					  const bool x2,
					  const bool allow_ms)
  {
    // look for milliseconds given as <name>-ms
    if (allow_ms)
      {
	const Option *o = opt.get_ptr(name + "-ms");
	if (o)
	  {
	    set_duration_parm(dur, name, o->get(1, 16), min_value, x2, true);
	    return o;
	  }
      }

    // look for seconds given as <name>
    {
      const Option *o = opt.get_ptr(name);
      if (o)
	set_duration_parm(dur, name, o->get(1, 16), allow_ms ? 1 : min_value, x2, false);
      return o;
    }
  }

  inline Time::Duration load_duration_default(const std::string& name,
					      const OptionList& opt,
					      const Time::Duration& default_duration,
					      const unsigned int min_value,
					      const bool x2,
					      const bool allow_ms)
  {
    Time::Duration ret(default_duration);
    load_duration_parm(ret, name, opt, min_value, x2, allow_ms);
    return ret;
  }

  inline Time::Duration skew_duration(const Time::Duration& dur,
				      const Time::Duration& min,
				      const unsigned int flux_order,
				      RandomAPI& rng)
  {
    const unsigned int range = 1 << flux_order;
    const int delta = int(rng.rand_get<unsigned int>() & (range-1)) - int(range>>1);
    const Time::Duration ret = dur + delta;
    if (ret >= min)
      return ret;
    else
      return min;
  }
}

#endif
