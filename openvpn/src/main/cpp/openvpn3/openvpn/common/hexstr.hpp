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

// A collection of functions for rendering and parsing hexadecimal strings

#ifndef OPENVPN_COMMON_HEXSTR_H
#define OPENVPN_COMMON_HEXSTR_H

#include <string>
#include <iomanip>
#include <sstream>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/string.hpp>

namespace openvpn {

  /**
   *  Renders an integer value within the hexadecimal range (0-15)
   *  to a hexadecimal character.
   *
   *  @param c    Integer to render as a hexadecimal character.
   *  @param caps Boolean (default false) which sets the outout to
   *              be either lower case (false) or upper case (true).
   *
   *  @return Returns a char with the hexadecimal representation of
   *          the input value.  If the value is out-of-range (outside
   *          of 0-15), it will be replaced with a questionmark (?).
   */
  inline char render_hex_char(const int c, const bool caps=false)
  {
    if (c < 10)
      return '0' + c;
    else if (c < 16)
      return (caps ? 'A' : 'a') - 10 + c;
    else
      return '?';
  }


  /**
   *  Parses a character in the range {0..9,A-F,a-f} to an
   *  integer value.  Used to convert hexadecimal character to integer.
   *  Only a single character is parsed by this function.
   *
   *  @param c   Character to be be parsed.
   *
   *  @return Returns an integer value of the hexadecimal input.  If the
   *          input character is invalid, outside of {0..9,A-F,a-f}, it will
   *          return -1.
   */
  inline int parse_hex_char(const char c)
  {
    if (c >= '0' && c <= '9')
      return c - '0';
    else if (c >= 'a' && c <= 'f')
      return c - 'a' + 10;
    else if (c >= 'A' && c <= 'F')
      return c - 'A' + 10;
    else
      return -1;
  }


  /**
   *  Class which Renders a single byte as hexadecimal
   */
  class RenderHexByte
  {
  public:
    /**
     *  Initializes a new object
     *
     *  @param byte  Unsigned char (one byte) to be processed
     *  @param caps  Boolean (default false) which sets the outout to
     *               be either lower case (false) or upper case (true).
     */
    RenderHexByte(const unsigned char byte, const bool caps=false)
    {
      c[0] = render_hex_char(byte >> 4, caps);
      c[1] = render_hex_char(byte & 0x0F, caps);
    }

    char char1() const { return c[0]; }
    char char2() const { return c[1]; }

    /**
     *  Retrieve the hexadecimal representation of the value.
     *  Warning: The result is a non-NULL terminated string.
     *
     *  @return Returns a non-NULL terminated 2 byte string with the hexadecimal
     *          representation of the initial value.  The return value is guaranteed
     *          to always be 2 bytes.
     */
    const char *str2() const { return c; } // Note: length=2, NOT null terminated

  private:
    char c[2];
  };


  /**
   *  Render a byte buffer (unsigned char *) as a hexadecimal string.
   *
   *  @param data  Unsigned char pointer to buffer to render.
   *  @param size  size_t of the number of bytes to parse from the buffer.
   *  @param caps  Boolean (default false) which sets the outout to
   *               be either lower case (false) or upper case (true).
   *
   *  @return Returns a std::string of the complete hexadecimal representation
   */
  inline std::string render_hex(const unsigned char *data, size_t size, const bool caps=false)
  {
    if (!data)
      return "NULL";
    std::string ret;
    ret.reserve(size*2+1);
    while (size--)
      {
	const RenderHexByte b(*data++, caps);
	ret += b.char1();
	ret += b.char2();
      }
    return ret;
  }


  /**
   *  Render a byte buffer (void *) as a hexadecimal string.
   *
   *  @param data  Void pointer to buffer to render.
   *  @param size  size_t of the number of bytes to parse from the buffer.
   *  @param caps  Boolean (default false) which sets the outout to
   *               be either lower case (false) or upper case (true).
   *
   *  @return Returns a std::string of the complete hexadecimal representation.
   */
  inline std::string render_hex(const void *data, const size_t size, const bool caps=false)
  {
    return render_hex((const unsigned char *)data, size, caps);
  }


  /**
   *  Variant of @render_hex(const unsiged char *,...) which adds a
   *  separator between each byte
   *
   *  @param data  Unsigned char pointer to buffer to render.
   *  @param size  size_t of the number of bytes to parse from the buffer.
   *  @param sep   A single character to use as the separator.
   *  @param caps  Boolean (default false) which sets the outout to
   *               be either lower case (false) or upper case (true).
   *
   *  @return Returns a std::string of the complete hexadecimal representation
   *          with each byte separated by a given character.
   */
  inline std::string render_hex_sep(const unsigned char *data, size_t size, const char sep, const bool caps=false)
  {
    if (!data)
      return "NULL";
    std::string ret;
    ret.reserve(size*3);
    bool prsep = false;
    while (size--)
      {
	if (prsep)
	  ret += sep;
	const RenderHexByte b(*data++, caps);
	ret += b.char1();
	ret += b.char2();
	prsep = true;
      }
    return ret;
  }

  /**
   *  Variant of @render_hex(const void *,...) which adds a
   *  separator between each byte

   *  @param data  Void pointer to buffer to render.
   *  @param size  size_t of the number of bytes to parse from the buffer.
   *  @param sep   A single character to use as the separator.
   *  @param caps  Boolean (default false) which sets the outout to
   *               be either lower case (false) or upper case (true).
   *
   *  @return Returns a std::string of the complete hexadecimal representation
   *          with each byte separated by a given character.
   */
  inline std::string render_hex_sep(const void *data, const size_t size, const char sep, const bool caps=false)
  {
    return render_hex_sep((const unsigned char *)data, size, sep, caps);
  }


  /**
   *  Render a std::vector<T> container as a hexadecimal string.
   *  T must be a data type compatible with
   *  RenderHexByte(const unsigned char,...)
   *
   *  @param data   std::vector<T> containing the data to render
   *  @param caps   Boolean (default false) which sets the outout to
   *                be either lower case (false) or upper case (true).
   *
   *  @return Returns a std::string of the complete hexadecimal representation.
   */
  template <typename V>
  inline std::string render_hex_generic(const V& data, const bool caps=false)
  {
    std::string ret;
    ret.reserve(data.size()*2+1);
    for (size_t i = 0; i < data.size(); ++i)
      {
	const RenderHexByte b(data[i], caps);
	ret += b.char1();
	ret += b.char2();
      }
    return ret;
  }


  /**
   *  Renders a combined hexadecimal and character dump of a buffer,
   *  with the typical 16 bytes split between hexadecimal and character
   *  separation per line.
   *
   *  @param  data  Unsigned char pointer to the buffer to dump.
   *  @param  size  Size of the buffer to render.
   *
   *  @return Returns a string containing a preformatted output of the
   *          hexadecimal dump.
   */
  inline std::string dump_hex(const unsigned char *data, size_t size)
  {
    if (!data)
      return "NULL\n";
    const unsigned int mask = 0x0F; // N bytes per line - 1
    std::ostringstream os;
    os << std::hex;
    std::string chars;
    size_t i;
    for (i = 0; i < size; ++i)
      {
	if (!(i & mask))
	  {
	    if (i)
	      {
		os << "  " << chars << std::endl;
		chars.clear();
	      }
	    os << std::setfill(' ') << std::setw(8) << i << ":";
	  }
	const unsigned char c = data[i];
	os << ' ' << std::setfill('0') << std::setw(2) << (unsigned int)c;
	if (string::is_printable(c))
	  chars += c;
	else
	  chars += '.';
      }
    if (i)
      os << string::spaces(2 + (((i-1) & mask) ^ mask) * 3) << chars << std::endl;
    return os.str();
  }

  /**
   *  Renders a combined hexadecimal and character dump of a buffer,
   *  with the typical 16 bytes split between hexadecimal and character
   *  separation per line.
   *
   *  @param  data  Void pointer to the buffer to dump.
   *  @param  size  Size of the buffer to render.
   *
   *  @return Returns a string containing a preformatted output of the
   *          hexadecimal dump.
   */
  inline std::string dump_hex(void* data, size_t size)
  {
    return dump_hex((const unsigned char *)data, size);
  }

  /**
   *  Renders a combined hexadecimal and character dump of a std::string buffer,
   *  with the typical 16 bytes split between hexadecimal and character
   *  separation per line.
   *
   *  @param  data  std::string containing the buffer to render
   *
   *  @return Returns a string containing a preformatted output of the
   *          hexadecimal dump.
   */
  inline std::string dump_hex(const std::string& str)
  {
    return dump_hex((const unsigned char *)str.c_str(), str.length());
  }


  /**
   *  Renders a combined hexadecimal and character dump of a std::vector<T>
   *  based buffer, with the typical 16 bytes split between hexadecimal and
   *  character separation per line.
   *
   *  @param  data  std::vector<T> containing the buffer to render
   *
   *  @return Returns a string containing a preformatted output of the
   *          hexadecimal dump.
   */
  template <typename V>
  inline std::string dump_hex(const V& data)
  {
    return dump_hex(data.c_data(), data.size());
  }

  /**
   *  Declaration of a hexadecimal parsing error exception class
   */
  OPENVPN_SIMPLE_EXCEPTION(parse_hex_error);


  /**
   *  Parses a std::string containing a hexadecimal value into
   *  a std::vector<T>.
   *
   *  @param dest  std::vector<T> destination buffer to use.
   *  @param str   std::string& containing the hexadecimal string to parse.
   *
   *  @return Returns nothing on success.  Will throw a parse_hex_error
   *          exception if the input is invalid/not parseable as a hexadecimal
   *          number.
   */
  template <typename V>
  inline void parse_hex(V& dest, const std::string& str)
  {
    const int len = int(str.length());
    int i;
    for (i = 0; i <= len - 2; i += 2)
      {
	const int high = parse_hex_char(str[i]);
	const int low = parse_hex_char(str[i+1]);
	if (high == -1 || low == -1)
	  throw parse_hex_error();
	dest.push_back((high<<4) + low);
      }
    if (i != len)
      throw parse_hex_error(); // straggler char      
  }


  /**
   *  Parses a char buffer (C string) containing a hexadecimal
   *  string into a templated (T) variable.  The input buffer
   *  MUST be NULL terminated.
   *
   *  WARNING: There are _NO_ overflow checks.
   *
   *  @param str    Char pointer (char *) to the buffer to be parsed.
   *  @param retval Return buffer where the parsed value is stored.
   *
   *  @return Returns true on successful parsing, otherwise false.
   */
  template <typename T>
  inline bool parse_hex_number(const char *str, T& retval)
  {
    if (!str[0])
      return false; // empty string
    size_t i = 0;
    T ret = T(0);
    while (true)
      {
	const char c = str[i++];
	const int hd = parse_hex_char(c);
	if (hd >= 0)
	  {
	    ret *= T(16);
	    ret += T(hd);
	  }
	else if (!c)
	  {
	    retval = ret;
	    return true;
	  }
	else
	  return false; // non-hex-digit
      }
  }


  /**
   *  Variant of @parse_hex_number(const char *, ...) which takes a std::string
   *  as the input.
   *
   *  @param str    std::string containing the hexadecimal string to be parsed.
   *  @param retval Return buffer where the parsed value is stored.
   *
   *  @return Returns true on successful parsing, otherwise false.
   */
  template <typename T>
  inline bool parse_hex_number(const std::string& str, T& retval)
  {
    return parse_hex_number(str.c_str(), retval);
  }


  /**
   *  Parses a std::string containing a hexadecimal
   *  string into a templated (T) variable.
   *
   *  NOTE:  Currently doesn't detect overflow
   *
   *  @param str    std::string containing the hexadecimal
   *                string to be parsed.
   *
   *  @return Returns a template T variable containing the
   *          parsed value on success.  Will throw the parse_hex_error
   *          exception on parsing errors.
   *
   */
  template <typename T>
  inline T parse_hex_number(const std::string& str)
  {
    T ret;
    if (!parse_hex_number<T>(str.c_str(), ret))
      throw parse_hex_error();
    return ret;
  }

  /**
   *  Renders a templated T variable containing a numeric value
   *  into a std::string containing a hexadecimal representation.
   *
   *  @param value  Numeric (T) value to represent as hexadecimal.
   *  @param caps   Boolean (default false) which sets the outout to
   *                be either lower case (false) or upper case (true).
   *
   *  @return Retuns a std::string containing the hexadecimal
   *          representation on succes.  Will throw a parse_hex_error
   *          exception on parsing errors.
   */
  template <typename T>
  std::string render_hex_number(T value, const bool caps=false)
  {
    unsigned char buf[sizeof(T)];
    for (size_t i = sizeof(T); i --> 0 ;)
      {
	buf[i] = value & 0xFF;
	value >>= 8;
      }
    return render_hex(buf, sizeof(T), caps);
  }


  /**
   *  Renders a single byte as a hexadecimal string
   *
   *  @param value  Unsigned char (byte) to be represented as hexadecimal.
   *  @param caps   Boolean (default false) which sets the outout to
   *                be either lower case (false) or upper case (true).
   *
   *  @return Returns a std::string with the hexadecimal representation
   *          of the input value.  The result will always contain only
   *          two characters.
   */
  inline std::string render_hex_number(unsigned char uc, const bool caps=false)
  {
    RenderHexByte b(uc, caps);
    return std::string(b.str2(), 2);
  }

} // namespace openvpn

#endif // OPENVPN_COMMON_HEXSTR_H
