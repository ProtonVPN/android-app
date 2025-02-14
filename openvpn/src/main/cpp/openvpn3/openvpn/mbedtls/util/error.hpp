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

// mbed TLS exception class that allows a  error code
// to be represented.

#ifndef OPENVPN_MBEDTLS_UTIL_ERROR_H
#define OPENVPN_MBEDTLS_UTIL_ERROR_H

#include <string>

#include <mbedtls/ssl.h>
#include <mbedtls/pem.h>
#include <mbedtls/error.h>

#include <openvpn/common/exception.hpp>
#include <openvpn/error/error.hpp>
#include <openvpn/error/excode.hpp>
#include <openvpn/mbedtls/mbedtls_compat.hpp>

namespace openvpn {

// string exception class
class MbedTLSException : public ExceptionCode
{
  public:
    MbedTLSException()
        : errtxt("mbed TLS"), errnum(0)
    {
    }

    explicit MbedTLSException(const std::string &error_text)
        : errnum(0)
    {
        errtxt = "mbed TLS: " + error_text;
    }

    explicit MbedTLSException(const std::string &error_text, const Error::Type code, const bool fatal)
        : ExceptionCode(code, fatal), errnum(0)
    {
        errtxt = "mbed TLS: " + error_text;
    }

    explicit MbedTLSException(const std::string &error_text, const int mbedtls_errnum)
        : errnum(mbedtls_errnum)
    {
        errtxt = "mbed TLS: " + error_text + " : " + mbedtls_errtext(mbedtls_errnum);

        // cite forum URL for mbed TLS invalid date
        // TODO: Get a better URL for such knowledge information record
        if (mbedtls_errnum == MBEDTLS_ERR_X509_INVALID_DATE)
            errtxt += ", please see https://forums.openvpn.net/viewtopic.php?f=36&t=21873 for more info";

        // for certain mbed TLS errors, translate them to an OpenVPN error code,
        // so they can be propagated up to the higher levels (such as UI level)
        switch (errnum)
        {
        case MBEDTLS_ERR_X509_CERT_VERIFY_FAILED:
            set_code(Error::CERT_VERIFY_FAIL, true);
            break;
        case MBEDTLS_ERR_PK_PASSWORD_REQUIRED:
        case MBEDTLS_ERR_PK_PASSWORD_MISMATCH:
            set_code(Error::PEM_PASSWORD_FAIL, true);
            break;
        case MBEDTLS_ERR_SSL_BAD_PROTOCOL_VERSION:
            set_code(Error::TLS_VERSION_MIN, true);
            break;
        }
    }

    const char *what() const noexcept override
    {
        return errtxt.c_str();
    }
    std::string what_str() const
    {
        return errtxt;
    }

    int get_errnum() const
    {
        return errnum;
    }

    virtual ~MbedTLSException() noexcept = default;

    static std::string mbedtls_errtext(int errnum)
    {
        char buf[256];
        mbedtls_strerror(errnum, buf, sizeof(buf));
        return buf;
    }

    static std::string mbedtls_verify_flags_errtext(const uint32_t flags)
    {
        // get string rendition of flags
        const size_t BUF_SIZE = 1024;
        std::unique_ptr<char[]> buf(new char[BUF_SIZE]);
        buf[0] = '\0';
        mbedtls_x509_crt_verify_info(buf.get(), BUF_SIZE, "", flags);

        // postprocess string
        std::string ret;
        ret.reserve(std::strlen(buf.get()) + 64);
        bool newline = false;
        for (size_t i = 0; i < BUF_SIZE; ++i)
        {
            const char c = buf[i];
            if (c == '\0')
                break;
            else if (c == '\n')
                newline = true;
            else
            {
                if (newline)
                {
                    ret += ", ";
                    newline = false;
                }
                ret += c;
            }
        }
        return ret;
    }

  private:
    std::string errtxt;
    int errnum;
};
} // namespace openvpn

#endif
