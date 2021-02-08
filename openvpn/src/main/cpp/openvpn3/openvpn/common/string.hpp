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

// General purpose string-manipulation functions.

#ifndef OPENVPN_COMMON_STRING_H
#define OPENVPN_COMMON_STRING_H

#include <string>
#include <vector>
#include <cstring>
#include <cctype>
#include <algorithm>

#include <openvpn/common/platform.hpp>
#include <openvpn/common/size.hpp>

namespace openvpn {
  namespace string {
    // case insensitive compare functions

    inline int strcasecmp(const char *s1, const char *s2)
    {
#ifdef OPENVPN_PLATFORM_WIN
      return ::_stricmp(s1, s2);
#else
      return ::strcasecmp(s1, s2);
#endif
    }

    inline int strcasecmp(const std::string& s1, const char *s2)
    {
      return strcasecmp(s1.c_str(), s2);
    }

    inline int strcasecmp(const char *s1, const std::string& s2)
    {
      return strcasecmp(s1, s2.c_str());
    }

    inline int strcasecmp(const std::string& s1, const std::string& s2)
    {
      return strcasecmp(s1.c_str(), s2.c_str());
    }

    // Like strncpy but makes sure dest is always null terminated
    inline void strncpynt (char *dest, const char *src, size_t maxlen)
    {
      strncpy (dest, src, maxlen);
      if (maxlen > 0)
	dest[maxlen - 1] = 0;
    }

    // Copy string to dest, make sure dest is always null terminated,
    // and fill out trailing chars in dest with '\0' up to dest_size.
    inline void copy_fill (void *dest, const std::string& src, const size_t dest_size)
    {
      if (dest_size > 0)
	{
	  const size_t ncopy = std::min(dest_size - 1, src.length());
	  std::memcpy(dest, src.c_str(), ncopy);
	  std::memset(static_cast<unsigned char *>(dest) + ncopy, 0, dest_size - ncopy);
	}
    }

    inline bool is_true(const std::string& str)
    {
      return str == "1" || !strcasecmp(str.c_str(), "true");
    }

    template <typename STRING>
    inline bool starts_with(const STRING& str, const std::string& prefix)
    {
      const size_t len = str.length();
      const size_t plen = prefix.length();
      if (plen <= len)
	return std::memcmp(str.c_str(), prefix.c_str(), plen) == 0;
      else
	return false;
    }

    template <typename STRING>
    inline bool starts_with(const STRING& str, const char *prefix)
    {
      const size_t len = str.length();
      const size_t plen = std::strlen(prefix);
      if (plen <= len)
	return std::memcmp(str.c_str(), prefix, plen) == 0;
      else
	return false;
    }

    // Return true if str == prefix or if str starts with prefix + delim
    template <typename STRING>
    inline bool starts_with_delim(const STRING& str, const std::string& prefix, const char delim)
    {
      if (prefix.length() < str.length())
	return str[prefix.length()] == delim && string::starts_with(str, prefix);
      else
	return prefix == str;
    }

    template <typename STRING>
    inline bool ends_with(const STRING& str, const std::string& suffix)
    {
      const size_t len = str.length();
      const size_t slen = suffix.length();
      if (slen <= len)
	return std::memcmp(str.c_str() + (len-slen), suffix.c_str(), slen) == 0;
      else
	return false;
    }

    template <typename STRING>
    inline bool ends_with(const STRING& str, const char *suffix)
    {
      const size_t len = str.length();
      const size_t slen = std::strlen(suffix);
      if (slen <= len)
	return std::memcmp(str.c_str() + (len-slen), suffix, slen) == 0;
      else
	return false;
    }

    // return true if string ends with char c
    template <typename STRING>
    inline bool ends_with(const STRING& str, const char c)
    {
      return str.length() && str.back() == c;
    }

    // return true if string ends with a newline
    template <typename STRING>
    inline bool ends_with_newline(const STRING& str)
    {
      return ends_with(str, '\n');
    }

    // return true if string ends with a CR or LF
    template <typename STRING>
    inline bool ends_with_crlf(const STRING& str)
    {
      if (str.length())
	{
	  const char c = str.back();
	  return c == '\n' || c == '\r';
	}
      else
	return false;
    }

    // Prepend leading characters (c) to str to obtain a minimum string length (min_len).
    // Useful for adding leading zeros to numeric values or formatting tables.
    inline std::string add_leading(const std::string& str, const size_t min_len, const char c)
    {
      if (min_len <= str.length())
	return str;
      size_t len = min_len - str.length();
      std::string ret;
      ret.reserve(min_len);
      while (len--)
	ret += c;
      ret += str;
      return ret;
    }

    // make sure that string ends with char c, if not append it
    inline std::string add_trailing_copy(const std::string& str, const char c)
    {
      if (ends_with(str, c))
	return str;
      else
	return str + c;
    }

    // make sure that string ends with char c, if not append it
    inline void add_trailing(std::string& str, const char c)
    {
      if (!ends_with(str, c))
	str += c;
    }

    // make sure that string ends with CRLF, if not append it
    inline void add_trailing_crlf(std::string& str)
    {
      if (ends_with(str, "\r\n"))
	;
      else if (ends_with(str, '\r'))
	str += '\n';
      else if (ends_with(str, '\n'))
	{
	  str.pop_back();
	  str += "\r\n";
	}
      else
	str += "\r\n";
    }

    // make sure that string ends with CRLF, if not append it
    inline std::string add_trailing_crlf_copy(std::string str)
    {
      add_trailing_crlf(str);
      return str;
    }

    // make sure that string ends with char c, if not append it (unless the string is empty)
    inline std::string add_trailing_unless_empty_copy(const std::string& str, const char c)
    {
      if (str.empty() || ends_with(str, c))
	return str;
      else
	return str + c;
    }

    // remove trailing \r or \n chars
    template <typename STRING>
    inline void trim_crlf(STRING& str)
    {
      while (ends_with_crlf(str))
	str.pop_back();
    }

    // remove trailing \r or \n chars
    inline std::string trim_crlf_copy(std::string str)
    {
      trim_crlf(str);
      return str;
    }

    // return true if str of size len contains an embedded null
    inline bool embedded_null(const char *str, size_t len)
    {
      while (len--)
	if (!*str++)
	  return true;
      return false;
    }

    // return the length of a string, omitting trailing nulls
    inline size_t len_without_trailing_nulls(const char *str, size_t len)
    {
      while (len > 0 && str[len-1] == '\0')
	--len;
      return len;
    }

    // return true if string contains at least one newline
    inline bool is_multiline(const std::string& str)
    {
      return str.find_first_of('\n') != std::string::npos;
    }

    // Return string up to a delimiter (without the delimiter).
    // Returns the entire string if no delimiter is found.
    inline std::string to_delim(const std::string& str, const char delim)
    {
      const size_t pos = str.find_first_of(delim);
      if (pos != std::string::npos)
	return str.substr(0, pos);
      else
	return str;
    }

    // return the first line (without newline) of a multi-line string
    inline std::string first_line(const std::string& str)
    {
      return to_delim(str, '\n');
    }

    // Define a common interpretation of what constitutes a space character.
    // Return true if c is a space char.
    inline bool is_space(const char c)
    {
      return std::isspace(static_cast<unsigned char>(c)) != 0;
    }

    inline bool is_digit(const char c)
    {
      return std::isdigit(static_cast<unsigned char>(c)) != 0;
    }

    inline bool is_alpha(const char c)
    {
      return std::isalpha(static_cast<unsigned char>(c)) != 0;
    }

    inline bool is_alphanumeric(const char c)
    {
      return std::isalnum(static_cast<unsigned char>(c)) != 0;
    }

    inline bool is_printable(const char c)
    {
      return std::isprint(static_cast<unsigned char>(c)) != 0;
    }

    inline bool is_printable(const unsigned char c)
    {
      return std::isprint(c) != 0;
    }

    inline bool is_ctrl(const char c)
    {
      return std::iscntrl(static_cast<unsigned char>(c)) != 0;
    }

    inline bool is_ctrl(const unsigned char c)
    {
      return std::iscntrl(c) != 0;
    }

    // return true if string conforms to regex \w*
    inline bool is_word(const std::string& str)
    {
      for (auto &c : str)
	if (!(is_alphanumeric(c) || c == '_'))
	  return false;
      return true;
    }

    // return true if all string characters are printable (or if string is empty)
    inline bool is_printable(const std::string& str)
    {
      for (auto &c : str)
	if (!is_printable(c))
	  return false;
      return true;
    }

    // return true if str contains at least one non-space control char
    inline bool contains_non_space_ctrl(const std::string& str)
    {
      for (auto &c : str)
	if ((!is_space(c) && is_ctrl(c)) || c == 127)
	  return true;
      return false;
    }

    // return true if str contains at least one space char
    inline bool contains_space(const std::string& str)
    {
      for (std::string::const_iterator i = str.begin(); i != str.end(); ++i)
	if (is_space(*i))
	  return true;
      return false;
    }

    // remove all spaces in string
    inline std::string remove_spaces(const std::string& str)
    {
      std::string ret;
      for (std::string::const_iterator i = str.begin(); i != str.end(); ++i)
	{
	  char c = *i;
	  if (!is_space(c))
	    ret += c;
	}
      return ret;
    }

    // replace all spaces in string with rep
    inline std::string replace_spaces(const std::string& str, const char rep)
    {
      std::string ret;
      for (std::string::const_iterator i = str.begin(); i != str.end(); ++i)
	{
	  char c = *i;
	  if (is_space(c))
	    c = rep;
	  ret += c;
	}
      return ret;
    }

    // replace all spaces in string with rep, reducing instances of multiple
    // consecutive spaces to a single instance of rep and removing leading
    // and trailing spaces
    inline std::string reduce_spaces(const std::string& str, const char rep)
    {
      std::string ret;
      bool last_space = true;
      for (std::string::const_iterator i = str.begin(); i != str.end(); ++i)
	{
	  char c = *i;
	  const bool space = is_space(c);
	  if (is_space(c))
	    c = rep;
	  if (!(space && last_space))
	    ret += c;
	  last_space = space;
	}
      if (last_space && !ret.empty())
	ret.pop_back();
      return ret;
    }

    // generate a string with n instances of char c
    inline std::string repeat(const char c, int n)
    {
      std::string ret;
      ret.reserve(n);
      while (n-- > 0)
	ret += c;
      return ret;
    }

    // generate a string with spaces
    inline std::string spaces(int n)
    {
      return repeat(' ', n);
    }

    // indent a multiline string
    inline std::string indent(const std::string& str, const int first, const int remaining)
    {
      std::string ret;
      int n_spaces = first;
      for (auto &c : str)
	{
	  if (n_spaces)
	    ret += spaces(n_spaces);
	  n_spaces = 0;
	  ret += c;
	  if (c == '\n')
	    n_spaces = remaining;
	}
      return ret;
    }

    // replace instances of char 'from' in string with char 'to'
    inline std::string replace_copy(const std::string& str, const char from, const char to)
    {
      std::string ret;
      ret.reserve(str.length());
      for (auto &c : str)
	ret.push_back(c == from ? to : c);
      return ret;
    }

    // return true if str is empty or contains only space chars
    inline bool is_empty(const std::string& str)
    {
      for (const auto& c : str)
	if (!is_space(c))
	  return false;
      return true;
    }

    // return true if str is empty or contains only space chars
    inline bool is_empty(const char *str)
    {
      if (!str)
	return true;
      char c;
      while ((c = *str++) != '\0')
	if (!is_space(c))
	  return false;
      return true;
    }

    // convert \n to \r\n
    inline std::string unix2dos(const std::string& str, const bool force_eol=false)
    {
      std::string ret;
      bool last_char_was_cr = false;

      ret.reserve(str.length() + str.length() / 8);
      for (std::string::const_iterator i = str.begin(); i != str.end(); ++i)
	{
	  const char c = *i;
	  if (c == '\n' && !last_char_was_cr)
	    ret += '\r';
	  ret += c;
	  last_char_was_cr = (c == '\r');
	}
      if (force_eol)
	add_trailing_crlf(ret);
      return ret;
    }

    // Split a string on sep delimiter.  The size of the
    // returned string vector will be at least 1 and at
    // most maxsplit + 1 (unless maxsplit is passed as -1).
    inline std::vector<std::string> split(const std::string& str,
					  const char sep,
					  const int maxsplit = -1)
    {
      std::vector<std::string> ret;
      int nterms = 0;
      std::string term;

      if (maxsplit >= 0)
	ret.reserve(maxsplit + 1);

      for (const auto c : str)
	{
	  if (c == sep && (maxsplit < 0 || nterms < maxsplit))
	    {
	      ret.push_back(std::move(term));
	      ++nterms;
	      term.clear();
	    }
	  else
	    term += c;
	}
      ret.push_back(std::move(term));
      return ret;
    }

    inline std::string join(const std::vector<std::string>& strings,
			    const std::string& delim,
			    const bool tail=false)
    {
      std::string ret;
      bool first = true;
      for (const auto &s : strings)
	{
	  if (!first)
	    ret += delim;
	  ret += s;
	  first = false;
	}
      if (tail && !ret.empty())
	ret += delim;
      return ret;
    }

    inline std::vector<std::string> from_argv(int argc, char *argv[], const bool skip_first)
    {
      std::vector<std::string> ret;
      for (int i = (skip_first ? 1 : 0); i < argc; ++i)
	ret.emplace_back(argv[i]);
      return ret;
    }

    inline std::string trim_left_copy(const std::string& str)
    {
      for (size_t i = 0; i < str.length(); ++i)
	{
	  if (!is_space(str[i]))
	    return str.substr(i);
	}
      return std::string();
    }

    inline std::string trim_copy(const std::string& str)
    {
      for (size_t i = 0; i < str.length(); ++i)
	{
	  if (!is_space(str[i]))
	    {
	      size_t last_nonspace = i;
	      for (size_t j = i + 1; j < str.length(); ++j)
		{
		  if (!is_space(str[j]))
		    last_nonspace = j;
		}
	      return str.substr(i, last_nonspace - i + 1);
	    }
	}
      return std::string();
    }

    inline std::string to_upper_copy(const std::string& str)
    {
      std::string ret;
      ret.reserve(str.length()+1);
      for (const auto &c : str)
	ret.push_back(std::toupper(static_cast<unsigned char>(c)));
      return ret;
    }

    inline std::string to_lower_copy(const std::string& str)
    {
      std::string ret;
      ret.reserve(str.length()+1);
      for (const auto &c : str)
	ret.push_back(std::tolower(static_cast<unsigned char>(c)));
      return ret;
    }

    inline void trim(std::string& str)
    {
      str = trim_copy(str);
    }

    inline void trim_left(std::string& str)
    {
      str = trim_left_copy(str);
    }

    inline void to_lower(std::string& str)
    {
      str = to_lower_copy(str);
    }

    inline void to_upper(std::string& str)
    {
      str = to_upper_copy(str);
    }

    // Replace any subsequence of consecutive space chars containing
    // at least one newline with a single newline char.  If string
    // is non-empty and doesn't include a trailing newline, add one.
    inline std::string remove_blanks(const std::string& str)
    {
      std::string ret;
      ret.reserve(str.length()+1);

      std::string spaces;
      bool in_space = false;
      bool has_nl = false;

      for (auto &c : str)
	{
	  const bool s = is_space(c);
	reprocess:
	  if (in_space)
	    {
	      if (s)
		{
		  if (c == '\n')
		    has_nl = true;
		  spaces += c;
		}
	      else
		{
		  if (has_nl)
		    ret += '\n';
		  else
		    ret += spaces;
		  in_space = false;
		  has_nl = false;
		  spaces.clear();
		  goto reprocess;
		}
	    }
	  else
	    {
	      if (s)
		{
		  in_space = true;
		  goto reprocess;
		}
	      else
		ret += c;
	    }
	}
      if (!ret.empty() && !ends_with_newline(ret))
	ret += '\n';
      return ret;
    }

  } // namespace string

} // namespace openvpn

#endif // OPENVPN_COMMON_STRING_H
