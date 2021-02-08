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

// Basic exception handling.  Allow exception classes for specific errors
// to be easily defined, and allow exceptions to be thrown with a consise
// syntax that allows stringstream concatenation using <<

#ifndef OPENVPN_COMMON_EXCEPTION_H
#define OPENVPN_COMMON_EXCEPTION_H

#include <string>
#include <sstream>
#include <exception>
#include <utility>

#include <openvpn/common/stringize.hpp> // for OPENVPN_STRINGIZE
#include <openvpn/common/string.hpp>

#ifdef OPENVPN_DEBUG_EXCEPTION
  // well-known preprocessor hack to get __FILE__:__LINE__ rendered as a string
# define OPENVPN_FILE_LINE "/" __FILE__ ":" OPENVPN_STRINGIZE(__LINE__)
#else
# define OPENVPN_FILE_LINE
#endif

namespace openvpn {

  // string exception class, where the exception is described by a std::string
  class Exception : public std::exception
  {
  public:
    Exception(const std::string& err) noexcept : err_(err) {}
    Exception(std::string&& err) noexcept : err_(std::move(err)) {}
    virtual const char* what() const throw() { return err_.c_str(); }
    const std::string& err() const noexcept { return err_; }
    virtual ~Exception() throw() {}

    void add_label(const std::string& label)
    {
      err_ = label + ": " + err_;
    }

    void remove_label(const std::string& label)
    {
      const std::string head = label + ": ";
      if (string::starts_with(err_, head))
	err_ = err_.substr(head.length());
    }

  private:
    std::string err_;
  };

  // define a simple custom exception class with no extra info
# define OPENVPN_SIMPLE_EXCEPTION(C) \
  class C : public std::exception { \
  public: \
    virtual const char* what() const throw() { return #C OPENVPN_FILE_LINE; } \
  }

  // define a simple custom exception class with no extra info that inherits from a custom base
# define OPENVPN_SIMPLE_EXCEPTION_INHERIT(B, C)	\
  class C : public B { \
  public: \
    C() : B(#C OPENVPN_FILE_LINE) {} \
    virtual const char* what() const throw() { return #C OPENVPN_FILE_LINE; } \
  }

  // define a custom exception class that allows extra info
# define OPENVPN_EXCEPTION(C) \
  class C : public openvpn::Exception { \
  public: \
    C() : openvpn::Exception(#C OPENVPN_FILE_LINE) {} \
    C(const std::string err) : openvpn::Exception(#C OPENVPN_FILE_LINE ": " + err) {} \
  }

  // define a custom exception class that allows extra info, but does not emit a tag
# define OPENVPN_UNTAGGED_EXCEPTION(C) \
  class C : public openvpn::Exception { \
  public: \
    C(const std::string err) : openvpn::Exception(err) {} \
  }

  // define a custom exception class that allows extra info, and inherits from a custom base
# define OPENVPN_EXCEPTION_INHERIT(B, C) \
  class C : public B { \
  public: \
    C() : B(#C OPENVPN_FILE_LINE) {} \
    C(const std::string err) : B(#C OPENVPN_FILE_LINE ": " + err) {} \
  }

  // define a custom exception class that allows extra info, and inherits from a custom base,
  // but does not emit a tag
# define OPENVPN_UNTAGGED_EXCEPTION_INHERIT(B, C) \
  class C : public B { \
  public: \
    C(const std::string err) : B(err) {} \
  }

  // throw an Exception with stringstream concatenation allowed
# define OPENVPN_THROW_EXCEPTION(stuff) \
  do { \
    std::ostringstream _ovpn_exc; \
    _ovpn_exc << stuff; \
    throw openvpn::Exception(_ovpn_exc.str()); \
  } while (0)

  // throw an OPENVPN_EXCEPTION class with stringstream concatenation allowed
# define OPENVPN_THROW(exc, stuff) \
  do { \
    std::ostringstream _ovpn_exc; \
    _ovpn_exc << stuff; \
    throw exc(_ovpn_exc.str()); \
  } while (0)

  // properly rethrow an exception that might be derived from Exception
  inline void throw_ref(const std::exception& e)
  {
    const Exception* ex = dynamic_cast<const Exception*>(&e);
    if (ex)
      throw *ex;
    else
      throw e;
  }

} // namespace openvpn

#endif // OPENVPN_COMMON_EXCEPTION_H
