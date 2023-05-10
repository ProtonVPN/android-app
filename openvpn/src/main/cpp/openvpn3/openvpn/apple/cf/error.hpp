//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2022 OpenVPN Inc.
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

    virtual const char *what() const noexcept
    {
        return errtxt.c_str();
    }
    std::string what_str() const
    {
        return errtxt;
    }

    virtual ~CFException() noexcept
    {
    }

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
