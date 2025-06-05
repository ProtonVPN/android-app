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
  public:
    ExceptionCode() = default;
    ExceptionCode(const Error::Type code)
        : code_(code)
    {
    }
    ExceptionCode(const Error::Type code, const bool fatal)
        : code_(code), fatal_(fatal)
    {
    }

    void set_code(const Error::Type code)
    {
        code_ = code;
    }

    void set_code(const Error::Type code, const bool fatal)
    {
        code_ = code;
        fatal_ = fatal;
    }

    Error::Type code() const
    {
        return code_;
    }
    bool fatal() const
    {
        return fatal_;
    }

    bool code_defined() const
    {
        return code_ != Error::SUCCESS;
    }

    //! Some errors may justify letting the underlying SSL library send out TLS alerts.
    bool is_tls_alert() const
    {
        return code() >= Error::TLS_VERSION_MIN && code() <= Error::TLS_ALERT_MISC;
    }

    virtual ~ExceptionCode() noexcept = default;

  private:
    Error::Type code_ = Error::SUCCESS;
    bool fatal_ = false;
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
