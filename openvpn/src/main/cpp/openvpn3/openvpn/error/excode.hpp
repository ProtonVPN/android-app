//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012- OpenVPN Inc.
//
//    SPDX-License-Identifier: MPL-2.0 OR AGPL-3.0-only WITH openvpn3-openssl-exception
//

#ifndef OPENVPN_ERROR_EXCODE_H
#define OPENVPN_ERROR_EXCODE_H

#include <string>
#include <exception>

#include <openvpn/error/error.hpp>

namespace openvpn {

// Define an exception object that allows an Error::Type code to be thrown
class ExceptionCode : public std::exception
{
    enum
    {
        FATAL_FLAG = 0x80000000
    };

  public:
    ExceptionCode()
        : code_(0)
    {
    }
    ExceptionCode(const Error::Type code)
        : code_(code)
    {
    }
    ExceptionCode(const Error::Type code, const bool fatal)
        : code_(mkcode(code, fatal))
    {
    }

    void set_code(const Error::Type code)
    {
        code_ = code;
    }

    void set_code(const Error::Type code, const bool fatal)
    {
        code_ = mkcode(code, fatal);
    }

    Error::Type code() const
    {
        return Error::Type(code_ & ~FATAL_FLAG);
    }
    bool fatal() const
    {
        return (code_ & FATAL_FLAG) != 0;
    }

    bool code_defined() const
    {
        return code_ != 0;
    }

    //! Some errors may justify letting the underlying SSL library send out TLS alerts.
    bool is_tls_alert() const
    {
        return code() >= Error::TLS_VERSION_MIN && code() <= Error::TLS_ALERT_MISC;
    }

    virtual ~ExceptionCode() noexcept = default;

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
    ErrorCode(const Error::Type code, const bool fatal, const std::string &err)
        : ExceptionCode(code, fatal), err_(err)
    {
    }

    const char *what() const noexcept override
    {
        return err_.c_str();
    }

    virtual ~ErrorCode() noexcept = default;

  private:
    std::string err_;
};

} // namespace openvpn
#endif
