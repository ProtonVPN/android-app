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

#ifndef OPENVPN_APPLECRYPTO_CF_ERROR_H
#define OPENVPN_APPLECRYPTO_CF_ERROR_H

#include <string>

#include <CoreFoundation/CFBase.h>

#include <openvpn/common/exception.hpp>

// An exception object that encapsulates Apple Core Foundation errors.

namespace openvpn {

// string exception class
class CFException : public std::exception
{
  public:
    explicit CFException(const std::string &text)
    {
        errtxt = text;
    }

    CFException(const std::string &text, const OSStatus status)
    {
        set_errtxt(text, status);
    }

    const char *what() const noexcept override
    {
        return errtxt.c_str();
    }
    std::string what_str() const
    {
        return errtxt;
    }

    virtual ~CFException() noexcept = default;

  private:
    void set_errtxt(const std::string &text, const OSStatus status)
    {
        std::ostringstream s;
        s << text << ": OSX Error code=" << status;
        errtxt = s.str();
    }

    std::string errtxt;
};

} // namespace openvpn

#endif // OPENVPN_APPLECRYPTO_CF_ERROR_H
