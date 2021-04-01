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

#ifndef OPENVPN_OPTIONS_SERVPUSH_H
#define OPENVPN_OPTIONS_SERVPUSH_H

#include <string>
#include <sstream>
#include <ostream>
#include <vector>
#include <utility> // for std::move

#include <openvpn/common/exception.hpp>
#include <openvpn/common/options.hpp>
#include <openvpn/common/jsonlib.hpp>

#ifdef HAVE_JSON
#include <openvpn/common/jsonhelper.hpp>
#endif

namespace openvpn {
  class ServerPushList : public std::vector<std::string>
  {
  public:
    void parse(const std::string& opt_name, const OptionList& opt)
    {
      const auto* push = opt.get_index_ptr(opt_name);
      if (push)
	{
	  reserve(size() + push->size());
	  for (auto &i : *push)
	    {
	      const Option& o = opt[i];
	      o.touch();
	      push_back(o.get(1, 512));
	    }
	}
    }

#ifdef HAVE_JSON
    // Parse JSON representation of a push list.
    // Each push list array element can be one of:
    // 1. simple JSON string,
    // 2. dictionary containing an "item" string, or
    // 3. dictionary containing an "item" array of strings.
    void parse(const std::string& title, const Json::Value& push_list) // push_list is JSON array
    {
      reserve(16); // arbitrary, just a guess
      const auto& ja = json::cast_array(push_list, false, title).array();
      for (size_t i = 0; i < ja.size(); ++i)
	{
	  const Json::Value& jv = ja[i];
	  if (jv.isString())
	    push_back(jv.asStringRef());
	  else if (jv.isObject())
	    {
	      const Json::Value& ji = jv["item"];
	      if (ji.isString())
		push_back(ji.asStringRef());
	      else if (ji.isArray())
		{
		  const auto& ia = ji.array();
		  for (size_t j = 0; j < ia.size(); ++j)
		    {
		      const Json::Value& iv = ia[j];
		      if (iv.isString())
			push_back(iv.asStringRef());
		      else
			throw json::json_parse(json::fmt_name(i, title) + " object contains 'item' array that includes non-string element at index=" + std::to_string(j));
		    }
		}
	      else
		throw json::json_parse(json::fmt_name(i, title) + " object must contain 'item' string or array");
	    }
	  else
	    throw json::json_parse(json::fmt_name(i, title) + " must be of type string or object");
	}
    }
#endif

    void extend(const std::vector<std::string>& other)
    {
      reserve(size() + other.size());
      for (auto &e : other)
	push_back(e);
    }

    // do a roundtrip to csv and back to OptionList
    OptionList to_option_list() const
    {
      std::ostringstream os;
      output_csv(os);
      return OptionList::parse_from_csv_static(os.str(), nullptr);
    }

    void output_csv(std::ostream& os) const
    {
      for (auto &e : *this)
	{
	  os << ',';
	  output_arg(e, os);
	}
    }

    static void output_arg(const std::string& e, std::ostream& os)
    {
      const bool must_quote = (e.find_first_of(',') != std::string::npos);
      Option::escape_string(os, e, must_quote);
    }
  };
}

#endif
