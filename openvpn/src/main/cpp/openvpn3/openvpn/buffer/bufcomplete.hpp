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

#ifndef OPENVPN_BUFFER_BUFCOMPLETE_H
#define OPENVPN_BUFFER_BUFCOMPLETE_H

#include <cstdint>    // for std::uint32_t, uint16_t, uint8_t
#include <algorithm>  // for std::min

#include <openvpn/buffer/buffer.hpp>

namespace openvpn {

  class BufferComplete
  {
  public:
    /* each advance/get method returns false if message is incomplete */

    bool advance(size_t size)
    {
      while (size)
	{
	  if (!fetch_buffer())
	    return false;
	  const size_t s = std::min(size, buf.size());
	  buf.advance(s);
	  size -= s;
	}
      return true;
    }

    // assumes embedded big-endian uint16_t length in the stream
    bool advance_string()
    {
      std::uint8_t h, l;
      if (!get(h))
	return false;
      if (!get(l))
	return false;
      return advance(size_t(h) << 8 | size_t(l));
    }

    bool advance_to_null()
    {
      std::uint8_t c;
      while (get(c))
	{
	  if (!c)
	    return true;
	}
      return false;
    }

    bool get(std::uint8_t& c)
    {
      if (!fetch_buffer())
	return false;
      c = buf.pop_front();
      return true;
    }

    bool defined() const
    {
      return buf.defined();
    }

  protected:
    void reset_buf(const Buffer& buf_arg)
    {
      buf = buf_arg;
    }

    void reset_buf()
    {
      buf.reset_content();
    }

  private:
    virtual void next_buffer() = 0;

    bool fetch_buffer()
    {
      if (buf.defined())
	return true;
      next_buffer();
      return buf.defined();
    }

    Buffer buf;
  };

}

#endif
