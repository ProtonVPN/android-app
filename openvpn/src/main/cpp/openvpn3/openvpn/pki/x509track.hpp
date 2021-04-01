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

#ifndef OPENVPN_PKI_X509TRACK_H
#define OPENVPN_PKI_X509TRACK_H

#include <string>
#include <vector>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/options.hpp>
#include <openvpn/common/arraysize.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/to_string.hpp>

namespace openvpn {
  namespace X509Track {

    enum Type {
      UNDEF=-1,
      SERIAL,
      SERIAL_HEX,
      SHA1,
      CN,
      C,
      L,
      ST,
      O,
      OU,
      EMAIL,
      N_TYPES,
    };

    static const char *const names[] = { // CONST GLOBAL
      "SERIAL",
      "SERIAL_HEX",
      "SHA1",
      "CN",
      "C",
      "L",
      "ST",
      "O",
      "OU",
      "emailAddress",
    };

    OPENVPN_EXCEPTION(x509_track_error);

    inline const char *name(const Type type)
    {
      static_assert(N_TYPES == array_size(names), "x509 names array inconsistency");
      if (type >= 0 && type < N_TYPES)
	return names[type];
      else
	return "UNDEF";
    }

    inline Type parse_type(const std::string& name)
    {
      for (size_t i = 0; i < N_TYPES; ++i)
	if (name == names[i])
	  return Type(i);
      return UNDEF;
    }

    struct Config
    {
      Config(const Type type_arg, const bool full_chain_arg)
	: type(type_arg),
	  full_chain(full_chain_arg)
      {
      }

      Config(const std::string& spec)
      {
	full_chain = (spec.length() > 0 && spec[0] == '+');
	type = parse_type(spec.substr(full_chain ? 1 : 0));
	if (type == UNDEF)
	  throw Exception("cannot parse attribute '" + spec + "'");
      }

      std::string to_string() const
      {
	std::string ret;
	if (full_chain)
	  ret += '+';
	ret += name(type);
	return ret;
      }

      bool depth_match(const int depth) const
      {
	return !depth || full_chain;
      }

      Type type;
      bool full_chain;
    };

    struct ConfigSet : public std::vector<Config>
    {
      ConfigSet() {}

      ConfigSet(const OptionList& opt,
		const bool include_serial,
		const bool include_serial_hex)
      {
	const auto* xt = opt.get_index_ptr("x509-track");
	if (xt)
	  {
	    for (const auto &i : *xt)
	      {
		try {
		  const Option& o = opt[i];
		  o.touch();
		  emplace_back(o.get(1, 64));
		}
		catch (const std::exception& e)
		  {
		    throw x509_track_error(e.what());
		  }
	      }
	  }

	if (include_serial && !exists(SERIAL))
	  emplace_back(SERIAL, true);
	if (include_serial_hex && !exists(SERIAL_HEX))
	  emplace_back(SERIAL_HEX, true);
      }

      bool exists(const Type t) const
      {
	for (auto &c : *this)
	  if (c.type == t)
	    return true;
	return false;
      }

      std::string to_string() const
      {
	std::string ret;
	for (auto &c : *this)
	  {
	    ret += c.to_string();
	    ret += '\n';
	  }
	return ret;
      }
    };

    struct KeyValue
    {
      KeyValue(const Type type_arg,
	       const int depth_arg,
	       std::string value_arg)
	: type(type_arg),
	  depth(depth_arg),
	  value(std::move(value_arg))
      {
      }

      std::string to_string(const bool omi_form) const
      {
	std::string ret;
	ret.reserve(128);
	if (omi_form)
	  ret += ">CLIENT:ENV,";
	ret += key_name();
	ret += '=';
	ret += string::reduce_spaces(value, ' ');
	return ret;
      }

      std::string key_name() const
      {
	switch (type)
	  {
	  case SERIAL:
	    return "tls_serial_" + openvpn::to_string(depth);
	  case SERIAL_HEX:
	    return "tls_serial_hex_" + openvpn::to_string(depth);
	  default:
	    return "X509_" + openvpn::to_string(depth) + '_' + name(type);
	  }
      }

      Type type = UNDEF;
      int depth = 0;
      std::string value;
    };

    struct Set : public std::vector<KeyValue>
    {
      std::string to_string(const bool omi_form) const
      {
	std::string ret;
	ret.reserve(512);
	for (auto &kv : *this)
	  {
	    ret += kv.to_string(omi_form);
	    if (omi_form)
	      ret += '\r';
	    ret += '\n';
	  }
	return ret;
      }
    };

  }
}

#endif
