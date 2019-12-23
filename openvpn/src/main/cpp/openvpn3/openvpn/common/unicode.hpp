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

// General-purpose function for dealing with unicode.

#ifndef OPENVPN_COMMON_UNICODE_H
#define OPENVPN_COMMON_UNICODE_H

#include <string>
#include <cstring>           // for std::memcpy
#include <algorithm>         // for std::min
#include <memory>
#include <cctype>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/unicode-impl.hpp>
#include <openvpn/buffer/buffer.hpp>

namespace openvpn {
  namespace Unicode {

    OPENVPN_SIMPLE_EXCEPTION(unicode_src_overflow);
    OPENVPN_SIMPLE_EXCEPTION(unicode_dest_overflow);
    OPENVPN_SIMPLE_EXCEPTION(unicode_malformed);

    // Return true if the given buffer is a valid UTF-8 string.
    // Extra constraints:
    enum {
      UTF8_NO_CTRL  = (1<<30), // no control chars allowed
      UTF8_NO_SPACE = (1<<31), // no space chars allowed
    };
    inline bool is_valid_utf8_uchar_buf(const unsigned char *source,
					size_t size,
					const size_t max_len_flags=0) // OR max length (or 0 to disable) with UTF8_x flags above
    {
      const size_t max_len = max_len_flags & ((size_t)UTF8_NO_CTRL-1); // NOTE -- use smallest flag value here
      size_t unicode_len = 0;
      while (size)
	{
	  const unsigned char c = *source;
	  if (c == '\0')
	    return false;
	  const int length = trailingBytesForUTF8[c]+1;
	  if ((size_t)length > size)
	    return false;
	  if (!isLegalUTF8(source, length))
	    return false;
	  if (length == 1)
	    {
	      if ((max_len_flags & UTF8_NO_CTRL) && std::iscntrl(c))
		return false;
	      if ((max_len_flags & UTF8_NO_SPACE) && std::isspace(c))
		return false;
	    }

	  source += length;
	  size -= length;
	  ++unicode_len;
	  if (max_len && unicode_len > max_len)
	    return false;
	}
      return true;
    }

    template <typename STRING>
    inline bool is_valid_utf8(const STRING& str, const size_t max_len_flags=0)
    {
      return is_valid_utf8_uchar_buf((const unsigned char *)str.c_str(), str.length(), max_len_flags);
    }

    // Return the byte position in the string that corresponds with
    // the given character index.  Return values:
    enum {
      UTF8_GOOD=0, // succeeded, result in index
      UTF8_BAD,    // failed, string is not legal UTF8
      UTF8_RANGE,  // failed, index is beyond end of string
    };
    template <typename STRING>
    inline int utf8_index(STRING& str, size_t& index)
    {
      const size_t size = str.length();
      size_t upos = 0;
      size_t pos = 0;
      while (pos < size)
	{
	  const int len = trailingBytesForUTF8[(unsigned char)str[pos]]+1;
	  if (pos + len > size || !isLegalUTF8((const unsigned char *)&str[pos], len))
	    return UTF8_BAD;
	  if (upos >= index)
	    {
	      index = pos;
	      return UTF8_GOOD;
	    }
	  pos += len;
	  ++upos;
	}
      return UTF8_RANGE;
    }

    // Truncate a UTF8 string if its length exceeds max_len
    template <typename STRING>
    inline void utf8_truncate(STRING& str, size_t max_len)
    {
      const int status = utf8_index(str, max_len);
      if (status == UTF8_GOOD || status == UTF8_BAD)
	str = str.substr(0, max_len);
    }

    // Return a printable UTF-8 string, where bad UTF-8 chars and
    // control chars are mapped to '?'.
    // If max_len_flags > 0, print a maximum of max_len_flags chars.
    // If UTF8_PASS_FMT flag is set in max_len_flags, pass through \r\n\t
    enum {
      UTF8_PASS_FMT=(1<<31),
      UTF8_FILTER=(1<<30),
    };
    template <typename STRING>
    inline STRING utf8_printable(const STRING& str, size_t max_len_flags)
    {
      STRING ret;
      const size_t size = str.length();
      const size_t max_len = max_len_flags & ((size_t)UTF8_FILTER-1); // NOTE -- use smallest flag value here
      size_t upos = 0;
      size_t pos = 0;
      ret.reserve(std::min(str.length(), max_len) + 3); // add 3 for "..."
      while (pos < size)
	{
	  if (!max_len || upos < max_len)
	    {
	      unsigned char c = str[pos];
	      int len = trailingBytesForUTF8[c]+1;
	      if (pos + len <= size
		  && c >= 0x20 && c != 0x7F
		  && isLegalUTF8((const unsigned char *)&str[pos], len))
		{
		  // non-control, legal UTF-8
		  ret.append(str, pos, len);
		}
	      else
		{
		  // control char or bad UTF-8 char
		  if (c == '\r' || c == '\n' || c == '\t')
		    {
		      if (!(max_len_flags & UTF8_PASS_FMT))
			c = ' ';
		    }
		  else if (max_len_flags & UTF8_FILTER)
		    c = 0;
		  else
		    c = '?';
		  if (c)
		    ret += c;
		  len = 1;
		}
	      pos += len;
	      ++upos;
	    }
	  else
	    {
	      ret.append("...");
	      break;
	    }
	}
      return ret;
    }

    template <typename STRING>
    inline size_t utf8_length(const STRING& str)
    {
      const size_t size = str.length();
      size_t upos = 0;
      size_t pos = 0;
      while (pos < size)
	{
	  int len = std::min((int)trailingBytesForUTF8[(unsigned char)str[pos]]+1,
			     (int)size);
	  if (!isLegalUTF8((const unsigned char *)&str[pos], len))
	    len = 1;
	  pos += len;
	  ++upos;
	}
      return upos;
    }

    inline void conversion_result_throw(const ConversionResult res)
    {
      switch (res)
	{
	case conversionOK:
	  return;
	case sourceExhausted:
	  throw unicode_src_overflow();
	case targetExhausted:
	  throw unicode_dest_overflow();
	case sourceIllegal:
	  throw unicode_malformed();
	}
    }

    // Convert a UTF-8 string to UTF-16 little endian (no null termination in return)
    template <typename STRING>
    inline BufferPtr string_to_utf16(const STRING& str)
    {
      std::unique_ptr<UTF16[]> utf16_dest(new UTF16[str.length()]);
      const UTF8 *src = (UTF8 *)str.c_str();
      UTF16 *dest = utf16_dest.get();
      const ConversionResult res = ConvertUTF8toUTF16(&src, src + str.length(),
						      &dest, dest + str.length(),
						      lenientConversion);
      conversion_result_throw(res);
      BufferPtr ret(new BufferAllocated((dest - utf16_dest.get()) * 2, BufferAllocated::ARRAY));
      UTF8 *d = ret->data();
      for (const UTF16 *s = utf16_dest.get(); s < dest; ++s)
	{
	  *d++ = *s & 0xFF;
	  *d++ = (*s >> 8) & 0xFF;
	}
      return ret;
    }

    class UTF8Iterator
    {
    public:
      struct Char
      {
	unsigned int len;
	unsigned char data[4];
	bool valid;

	const bool is_valid() const
	{
	  return valid && len >= 1 && len <= sizeof(data);
	}

	std::string str(const char *malformed)
	{
	  if (is_valid())
	    return std::string((char *)data, len);
	  else
	    return malformed;
	}
      };

      UTF8Iterator(const std::string& str_arg)
	: str((unsigned char *)str_arg.c_str()),
	  size(str_arg.length())
      {
      }

      bool get(Char &c)
      {
	if (size)
	  {
	    unsigned int len = std::min((unsigned int)trailingBytesForUTF8[*str]+1,
					(unsigned int)size);
	    if (isLegalUTF8(str, len))
	      {
		c.valid = true;
		c.len = std::min(len, (unsigned int)sizeof(c.data));
		std::memcpy(c.data, str, c.len);
	      }
	    else
	      {
		c.valid = false;
		c.len = 1;
	      }
	    str += c.len;
	    size -= c.len;
	    return true;
	  }
	else
	  return false;
      }

    private:
      const unsigned char *str;
      unsigned int size;
    };
  }
}

#endif
