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

// Parse the tls-version-min option.

#pragma once

#include <string>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/options.hpp>

namespace openvpn {
  namespace TLSVersion {
    enum Type {
      UNDEF=0,
      V1_0,
      V1_1,
      V1_2,
      V1_3
    };

    inline const std::string to_string(const Type version)
    {
      switch (version)
	{
	case UNDEF:
	  return "UNDEF";
	case V1_0:
	  return "V1_0";
	case V1_1:
	  return "V1_1";
	case V1_2:
	  return "V1_2";
	case V1_3:
	  return "V1_3";
	default:
	  return "???";
	}
    }

    inline Type parse_tls_version_min(const std::string& ver,
				      const bool or_highest,
				      const Type max_version)
    {
      if (ver == "1.0" && V1_0 <= max_version)
	return V1_0;
      else if (ver == "1.1" && V1_1 <= max_version)
	return V1_1;
      else if (ver == "1.2" && V1_2 <= max_version)
        return V1_2;
      else if (ver == "1.3" && V1_3 <= max_version)
        return V1_2;
      else if (or_highest)
	return max_version;
      else
	throw option_error("tls-version-min: unrecognized TLS version");
    }

    inline Type parse_tls_version_min(const OptionList& opt,
				      const std::string& relay_prefix,
				      const Type max_version)
    {
      const Option* o = opt.get_ptr(relay_prefix + "tls-version-min");
      if (o)
	{
	  const std::string ver = o->get_optional(1, 16);
	  const bool or_highest = (o->get_optional(2, 16) == "or-highest");
	  return parse_tls_version_min(ver, or_highest, max_version);
	}
      return UNDEF;
    }

    inline void apply_override(Type& tvm, const std::string& override)
    {
      //const Type orig = tvm;
      if (override.empty() || override == "default")
	;
      else if (override == "disabled")
	tvm = UNDEF;
      else if (override == "tls_1_0")
	tvm = V1_0;
      else if (override == "tls_1_1")
	tvm = V1_1;
      else if (override == "tls_1_2")
	tvm = V1_2;
      else if (override == "tls_1_3")
        tvm = V1_3;
      else
	throw option_error("tls-version-min: unrecognized override string");

      //OPENVPN_LOG("*** TLS-version-min before=" << to_string(orig) << " override=" << override << " after=" << to_string(tvm)); // fixme
    }
  }
}
