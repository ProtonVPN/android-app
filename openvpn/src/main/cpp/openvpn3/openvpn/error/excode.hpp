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

#ifndef OPENVPN_ERROR_EXCODE_H
#define OPENVPN_ERROR_EXCODE_H

#include <string>
#include <exception>

#include <openvpn/error/error.hpp>

namespace openvpn {

  // Define an exception object that allows an Error::Type code to be thrown
  class ExceptionCode : public std::exception
  {
    enum {
      FATAL_FLAG = 0x80000000
    };

  public:
    ExceptionCode()
      : code_(0) {}
    ExceptionCode(const Error::Type code)
      : code_(code) {}
    ExceptionCode(const Error::Type code, const bool fatal)
      : code_(mkcode(code, fatal)) {}

    void set_code(const Error::Type code)
    {
      code_ = code;
    }

    void set_code(const Error::Type code, const bool fatal)
    {
      code_ = mkcode(code, fatal);
    }

    Error::Type code() const { return Error::Type(code_ & ~FATAL_FLAG); }
    bool fatal() const { return (code_ & FATAL_FLAG) != 0; }

    bool code_defined() const { return code_ != 0; }

    virtual ~ExceptionCode() throw() {}

  private:
    static unsigned int mkcode(const Error::Type code, const bool fatal)
    {
      unsigned int ret = code;
      if (fatal)
	ret |= FATAL_FLAG;
      return ret;
    }

    unsigned int code_;
  };

  class ErrorCode : public ExceptionCode
  {
  public:
    ErrorCode(const Error::Type code, const bool fatal, const std::string& err)
      : ExceptionCode(code, fatal) , err_(err) {}

    virtual const char* what() const throw() { return err_.c_str(); }

    virtual ~ErrorCode() throw() {}

  private:
    std::string err_;
  };

}
#endif
