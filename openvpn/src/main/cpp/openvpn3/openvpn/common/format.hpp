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

#ifndef OPENVPN_COMMON_FORMAT_H
#define OPENVPN_COMMON_FORMAT_H

#include <cstddef> // for std::nullptr_t
#include <string>
#include <sstream>
#include <ostream>
#include <type_traits>
#include <utility>

#include <openvpn/common/platform.hpp>
#include <openvpn/common/to_string.hpp>

namespace openvpn {

  // Concatenate arguments into a string:
  // print(args...)   -- concatenate
  // prints(args...)  -- concatenate but delimit args with space
  // printd(char delim, args...) -- concatenate but delimit args with delim

  namespace print_detail {
    template<typename T>
    inline void print(std::ostream& os, char delim, const T& first)
    {
      os << first;
    }

    template<typename T, typename... Args>
    inline void print(std::ostream& os, char delim, const T& first, Args... args)
    {
      os << first;
      if (delim)
	os << delim;
      print(os, delim, args...);
    }
  }

  template<typename... Args>
  inline std::string printd(char delim, Args... args)
  {
    std::ostringstream os;
    print_detail::print(os, delim, args...);
    return os.str();
  }

  template<typename... Args>
  inline std::string print(Args... args)
  {
    return printd(0, args...);
  }

  template<typename... Args>
  inline std::string prints(Args... args)
  {
    return printd(' ', args...);
  }

  // String formatting similar to sprintf.
  // %s formats any argument regardless of type.
  // %r formats any argument regardless of type and single-quotes it.
  // %R formats any argument regardless of type and double-quotes it.
  // %% formats '%'
  // printfmt(<format_string>, args...)

  namespace print_formatted_detail {
    template<typename T>
    class Output {};

    template<>
    class Output<std::string>
    {
    public:
      Output(const size_t reserve)
      {
	if (reserve)
	  str_.reserve(reserve);
      }

      // numeric types
      template <typename T,
		typename std::enable_if<std::is_arithmetic<T>::value, int>::type = 0>
      void append(T value)
      {
	str_ += openvpn::to_string(value);
      }

      // non-numeric types not specialized below
      template <typename T,
		typename std::enable_if<!std::is_arithmetic<T>::value, int>::type = 0>
      void append(const T& value)
      {
	std::ostringstream os;
	os << value;
	str_ += os.str();
      }

      // specialization for std::string
      void append(const std::string& value)
      {
	str_ += value;
      }

      // specialization for const char *
      void append(const char *value)
      {
	if (value)
	  str_ += value;
      }

      // specialization for char *
      void append(char *value)
      {
	if (value)
	  str_ += value;
      }

      // specialization for char
      void append(const char c)
      {
	str_ += c;
      }

      // specialization for bool
      void append(const bool value)
      {
	str_ += value ? "true" : "false";
      }

      // specialization for nullptr
      void append(std::nullptr_t)
      {
	str_ += "nullptr";
      }

      std::string str()
      {
	return std::move(str_);
      }

    private:
      std::string str_;
    };

    template<>
    class Output<std::ostringstream>
    {
    public:
      Output(const size_t reserve)
      {
	// fixme -- figure out how to reserve space in std::ostringstream
      }

      // general types
      template <typename T>
      void append(const T& value)
      {
	os_ << value;
      }

      // specialization for const char *
      void append(const char *value)
      {
	if (value)
	  os_ << value;
      }

      // specialization for char *
      void append(char *value)
      {
	if (value)
	  os_ << value;
      }

      // specialization for bool
      void append(const bool value)
      {
	if (value)
	  os_ << "true";
	else
	  os_ << "false";
      }

      // specialization for nullptr
      void append(std::nullptr_t)
      {
	os_ << "nullptr";
      }

      std::string str()
      {
	return os_.str();
      }

    private:
      std::ostringstream os_;
    };
  }

  template <typename OUTPUT>
  class PrintFormatted
  {
  public:
    PrintFormatted(const std::string& fmt_arg, const size_t reserve)
      : fmt(fmt_arg),
	fi(fmt.begin()),
	out(reserve),
	pct(false)
    {
    }

    void process()
    {
      process_finish();
    }

    template<typename T>
    void process(const T& last)
    {
      process_arg(last);
      process_finish();
    }

    template<typename T, typename... Args>
    void process(const T& first, Args... args)
    {
      process_arg(first);
      process(args...);
    }

    std::string str()
    {
      return out.str();
    }

  private:
    PrintFormatted(const PrintFormatted&) = delete;
    PrintFormatted& operator=(const PrintFormatted&) = delete;

    template<typename T>
    bool process_arg(const T& arg)
    {
      while (fi != fmt.end())
	{
	  const char c = *fi++;
	  if (pct)
	    {
	      pct = false;
	      const int quote = quote_delim(c);
	      if (quote >= 0)
		{
		  if (quote)
		    out.append((char)quote);
		  out.append(arg);
		  if (quote)
		    out.append((char)quote);
		  return true;
		}
	      else
		out.append(c);
	    }
	  else
	    {
	      if (c == '%')
		pct = true;
	      else
		out.append(c);
	    }
	}
      return false;
    }

    void process_finish()
    {
      // '?' printed for %s operators that don't match an argument
      while (process_arg("?"))
	;
    }

    static int quote_delim(const char fmt)
    {
      switch (fmt)
	{
	case 's':
	  return 0;
	case 'r':
	  return '\'';
	case 'R':
	  return '\"';
	default:
	  return -1;
	}
    }

    const std::string& fmt;
    std::string::const_iterator fi;
    print_formatted_detail::Output<OUTPUT> out;
    bool pct;
  };

  template<typename... Args>
  inline std::string printfmt(const std::string& fmt, Args... args)
  {
#ifdef OPENVPN_PLATFORM_ANDROID
    PrintFormatted<std::ostringstream> pf(fmt, 256);
#else
    PrintFormatted<std::string> pf(fmt, 256);
#endif
    pf.process(args...);
    return pf.str();
  }

# define OPENVPN_FMT(...) OPENVPN_LOG_STRING(printfmt(__VA_ARGS__))

} // namespace openvpn

#endif
