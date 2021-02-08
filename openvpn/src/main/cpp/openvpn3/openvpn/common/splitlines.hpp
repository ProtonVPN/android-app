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

// General purpose class to split a multi-line string into lines.

#ifndef OPENVPN_COMMON_SPLITLINES_H
#define OPENVPN_COMMON_SPLITLINES_H

#include <utility>

#include <openvpn/common/string.hpp>

namespace openvpn {
  template <typename STRING>
  class SplitLinesType
  {
  public:
    // Note: string/buffer passed to constructor is not locally stored,
    // so it must remain in scope and not be modified during the lifetime
    // of the SplitLines object.
    SplitLinesType(const STRING& str, const size_t max_line_len_arg=0)
      : data((const char *)str.c_str()),
	size(str.length()),
	max_line_len(max_line_len_arg)
    {
    }

    bool operator()(const bool trim=true)
    {
      line.clear();
      overflow = false;
      const size_t overflow_index = index + max_line_len;
      while (index < size)
	{
	  if (max_line_len && index >= overflow_index)
	    {
	      overflow = true;
	      return true;
	    }
	  const char c = data[index++];
	  line += c;
	  if (c == '\n' || index >= size)
	    {
	      if (trim)
		string::trim_crlf(line);
	      return true;
	    }
	}
      return false;
    }

    bool line_overflow() const
    {
      return overflow;
    }

    std::string& line_ref()
    {
      return line;
    }

    const std::string& line_ref() const
    {
      return line;
    }

    std::string line_move()
    {
      return std::move(line);
    }

    enum Status {
      S_OKAY,
      S_EOF,
      S_ERROR
    };

    Status next(std::string& ln, const bool trim=true)
    {
      const bool s = (*this)(trim);
      if (!s)
	return S_EOF;
      if (overflow)
	return S_ERROR;
      ln = std::move(line);
      return S_OKAY;
    }

  private:
    const char *data;
    size_t size;
    size_t max_line_len;
    size_t index = 0;
    std::string line;
    bool overflow = false;
  };

  typedef SplitLinesType<std::string> SplitLines;
}

#endif
