//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2017 OpenVPN Inc.
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

#ifndef OPENVPN_BUFFER_BUFLIMIT_H
#define OPENVPN_BUFFER_BUFLIMIT_H

#include <openvpn/buffer/buffer.hpp>

namespace openvpn {

  template <typename T>
  class BufferLimit
  {
  public:
    BufferLimit()
    {
      set_max(0, 0);
      reset();
    }

    BufferLimit(const T max_lines_arg,
		const T max_bytes_arg)
    {
      set_max(max_lines_arg, max_bytes_arg);
      reset();
    }

    void set_max(const T max_lines_arg,
		 const T max_bytes_arg)
    {
      max_lines = max_lines_arg;
      max_bytes = max_bytes_arg;
    }

    void reset()
    {
      n_bytes = n_lines = 0;
    }

    void add(const Buffer& buf)
    {
      T size = (T)buf.size();
      n_bytes += size;
      if (max_bytes && n_bytes > max_bytes)
	bytes_exceeded();
      if (max_lines)
	{
	  const unsigned char *p = buf.c_data();
	  while (size--)
	    {
	      const unsigned char c = *p++;
	      if (c == '\n')
		{
		  ++n_lines;
		  if (n_lines > max_lines)
		    lines_exceeded();
		}
	    }
	}
    }

    virtual void bytes_exceeded() = 0;
    virtual void lines_exceeded() = 0;

  protected:
    T max_lines;
    T max_bytes;
    T n_bytes;
    T n_lines;
  };

}

#endif
