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

#ifndef OPENVPN_TUN_IPv6_SETTING_H
#define OPENVPN_TUN_IPv6_SETTING_H

#include <openvpn/common/exception.hpp>

namespace openvpn {
  class TriStateSetting
  {
  public:
    enum Type {
      No,
      Yes,
      Default,
    };

    TriStateSetting()
    {
    }

    explicit TriStateSetting(const Type t)
      : type_(t)
    {
    }

    Type operator()() const { return type_; }

    std::string to_string() const
    {
      switch (type_)
	{
	case No:
	  return "no";
	case Yes:
	  return "yes";
	case Default:
	default:
	  return "default";
	}
    }

    static TriStateSetting parse(const std::string& str)
    {
      if (str == "no")
	return TriStateSetting(No);
      else if (str == "yes")
	return TriStateSetting(Yes);
      else if (str == "default")
	return TriStateSetting(Default);
      else
	throw Exception("IPv6Setting: unrecognized setting: '" + str + '\'');
    }

    bool operator==(const TriStateSetting& other) const
    {
      return type_ == other.type_;
    }

    bool operator!=(const TriStateSetting& other) const
    {
      return type_ != other.type_;
    }

  private:
    Type type_ = Default;
  };
}

#endif
