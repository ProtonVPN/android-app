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

#ifndef OPENVPN_COMMON_MODE_H
#define OPENVPN_COMMON_MODE_H

// A client/server mode class.

namespace openvpn {
  class Mode
  {
  public:
    enum Type {
      CLIENT,
      SERVER,
    };

    Mode() : type_(CLIENT) {}
    explicit Mode(const Type t) : type_(t) {}

    bool is_server() const { return type_ == SERVER; }
    bool is_client() const { return type_ == CLIENT; }

    bool operator==(const Mode& other)
    {
      return type_ == other.type_;
    }

    bool operator!=(const Mode& other)
    {
      return type_ != other.type_;
    }

    const char *str() const
    {
      switch (type_)
	{
	case CLIENT:
	  return "CLIENT";
	case SERVER:
	  return "SERVER";
	default:
	  return "UNDEF_MODE";
	}
    }

  private:
    Type type_;
  };
} // namespace openvpn

#endif // OPENVPN_COMMON_MODE_H
