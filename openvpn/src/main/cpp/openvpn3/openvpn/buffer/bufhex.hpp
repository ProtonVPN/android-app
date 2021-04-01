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

#ifndef OPENVPN_BUFFER_BUFHEX_H
#define OPENVPN_BUFFER_BUFHEX_H

#include <openvpn/common/hexstr.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/buffer/buffer.hpp>

namespace openvpn {
  namespace BufHex {

    OPENVPN_EXCEPTION(buf_hex);

    template <typename T>
    inline std::string render(const T obj)
    {
      const ConstBuffer buf((const unsigned char *)&obj, sizeof(obj), true);
      return render_hex_generic(buf);
    }

    template <typename T>
    inline T parse(const std::string& hex, const std::string& title)
    {
      T obj;
      Buffer buf((unsigned char *)&obj, sizeof(obj), false);
      try {
	parse_hex(buf, hex);
      }
      catch (const BufferException& e)
	{
	  OPENVPN_THROW(buf_hex, title << ": buffer issue: " << e.what());
	}
      if (buf.size() != sizeof(obj))
	OPENVPN_THROW(buf_hex, title << ": unexpected size");
      return obj;
    }

  }
}

#endif
