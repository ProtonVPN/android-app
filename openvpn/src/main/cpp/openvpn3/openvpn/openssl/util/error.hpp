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

// OpenSSL exception class that allows a full OpenSSL error stack
// to be represented.

#ifndef OPENVPN_OPENSSL_UTIL_ERROR_H
#define OPENVPN_OPENSSL_UTIL_ERROR_H

#include <string>
#include <openssl/err.h>
#include <openssl/ssl.h>

#include <openvpn/common/exception.hpp>
#include <openvpn/error/error.hpp>
#include <openvpn/error/excode.hpp>

namespace openvpn {

// string exception class
class OpenSSLException : public ExceptionCode
{
  public:
    OPENVPN_EXCEPTION(ssl_exception_index);

    enum
    {
        MAX_ERRORS = 8
    };

    OpenSSLException()
    {
        ssl_err = -1;
        init_error("OpenSSL");
    }

    explicit OpenSSLException(const std::string &error_text) noexcept
    {
        ssl_err = -1;
        init_error(error_text.c_str());
    }

    explicit OpenSSLException(const int ssl_error)
    {
        init_ssl_error(ssl_error, "OpenSSL");
    }

    explicit OpenSSLException(const std::string &error_text, const int ssl_error)
    {
        init_ssl_error(ssl_error, error_text.c_str());
    }

    const char *what() const noexcept override
    {
        return errtxt.c_str();
    }
    std::string what_str() const
    {
        return errtxt;
    }

    size_t len() const
    {
        return n_err;
    }
    unsigned long operator[](const size_t i) const
    {
        if (i < n_err)
            return errstack[i];
        else
            throw ssl_exception_index();
    }

    int ssl_error() const
    {
        return ssl_err;
    }

    virtual ~OpenSSLException() noexcept = default;

    static const char *ssl_error_text(const int ssl_error, bool *unknown = nullptr)
    {
        switch (ssl_error)
        {
        case SSL_ERROR_NONE:
            return "SSL_ERROR_NONE";
        case SSL_ERROR_ZERO_RETURN:
            return "SSL_ERROR_ZERO_RETURN";
        case SSL_ERROR_WANT_READ:
            return "SSL_ERROR_WANT_READ";
        case SSL_ERROR_WANT_WRITE:
            return "SSL_ERROR_WANT_WRITE";
        case SSL_ERROR_WANT_CONNECT:
            return "SSL_ERROR_WANT_CONNECT";
        case SSL_ERROR_WANT_ACCEPT:
            return "SSL_ERROR_WANT_ACCEPT";
        case SSL_ERROR_WANT_X509_LOOKUP:
            return "SSL_ERROR_WANT_X509_LOOKUP";
        case SSL_ERROR_SYSCALL:
            return "SSL_ERROR_SYSCALL";
        case SSL_ERROR_SSL:
            return "SSL_ERROR_SSL";
        default:
            if (unknown)
                *unknown = true;
            return "(unknown SSL error)";
        }
    }

  private:
    void init_error(const char *error_text)
    {
        const char *prefix = ": ";
        std::ostringstream tmp;
        char buf[256];

        tmp << error_text;

        n_err = 0;
        while (unsigned long err = ERR_get_error())
        {
            if (n_err < MAX_ERRORS)
                errstack[n_err++] = err;
            ERR_error_string_n(err, buf, sizeof(buf));
            auto reason = ERR_GET_REASON(err);
            tmp << prefix << buf;
            if (reason >= SSL_AD_REASON_OFFSET)
            {
                tmp << "[" << SSL_alert_desc_string_long(reason - SSL_AD_REASON_OFFSET) << "]";
            }

            prefix = " / ";

            // for certain OpenSSL errors, translate them to an OpenVPN error code,
            // so they can be propagated up to the higher levels (such as UI level)

            switch (reason)
            {
            case SSL_R_CERTIFICATE_VERIFY_FAILED:
                set_code(Error::CERT_VERIFY_FAIL, true);
                break;
            case PEM_R_BAD_PASSWORD_READ:
            case PEM_R_BAD_DECRYPT:
                set_code(Error::PEM_PASSWORD_FAIL, true);
                break;
            case SSL_R_UNSUPPORTED_PROTOCOL:
                set_code(Error::TLS_VERSION_MIN, true);
                break;
            case SSL_R_CA_MD_TOO_WEAK:
                set_code(Error::SSL_CA_MD_TOO_WEAK, true);
                break;
            case SSL_R_CA_KEY_TOO_SMALL:
                set_code(Error::SSL_CA_KEY_TOO_SMALL, true);
                break;
#if defined(SSL_R_LEGACY_SIGALG_DISALLOWED_OR_UNSUPPORTED)
                /* This error code has been added in OpenSSL 3.0.8 */
            case SSL_R_LEGACY_SIGALG_DISALLOWED_OR_UNSUPPORTED:
                set_code(Error::TLS_SIGALG_DISALLOWED_OR_UNSUPPORTED, true);
                break;
#endif
            case SSL_R_DH_KEY_TOO_SMALL:
                set_code(Error::SSL_DH_KEY_TOO_SMALL, true);
                break;
            case SSL_R_TLSV1_ALERT_PROTOCOL_VERSION:
                set_code(Error::TLS_ALERT_PROTOCOL_VERSION, true);
                break;
            case SSL_R_TLSV1_ALERT_UNKNOWN_CA:
                set_code(Error::TLS_ALERT_UNKNOWN_CA, true);
                break;
            case SSL_R_SSLV3_ALERT_HANDSHAKE_FAILURE:
                set_code(Error::TLS_ALERT_HANDSHAKE_FAILURE, true);
                break;
            case SSL_R_TLSV13_ALERT_CERTIFICATE_REQUIRED:
                set_code(Error::TLS_ALERT_CERTIFICATE_REQUIRED, true);
                break;
            case SSL_R_SSLV3_ALERT_CERTIFICATE_EXPIRED:
                set_code(Error::TLS_ALERT_CERTIFICATE_EXPIRED, true);
                break;
            case SSL_R_SSLV3_ALERT_CERTIFICATE_REVOKED:
                set_code(Error::TLS_ALERT_CERTIFICATE_REVOKED, true);
                break;
            case SSL_R_SSLV3_ALERT_BAD_CERTIFICATE:
                set_code(Error::TLS_ALERT_BAD_CERTIFICATE, true);
                break;
            case SSL_R_SSLV3_ALERT_UNSUPPORTED_CERTIFICATE:
                set_code(Error::TLS_ALERT_UNSUPPORTED_CERTIFICATE, true);
                break;
            default:
                if (reason > SSL_AD_REASON_OFFSET)
                {
                    /* all TLS alerts use TLS alert code + SSL_AD_REASON_OFFSET in OpenSSL */
                    set_code(Error::TLS_ALERT_MISC, true);
                }
            }
        }
        errtxt = tmp.str();
    }

    void init_ssl_error(const int ssl_error, const char *error_text)
    {
        bool unknown = false;
        ssl_err = ssl_error;
        const char *text = ssl_error_text(ssl_error, &unknown);
        if (unknown || ssl_error == SSL_ERROR_SYSCALL || ssl_error == SSL_ERROR_SSL)
        {
            init_error(error_text);
            errtxt += " (";
            errtxt += text;
            errtxt += ")";
        }
        else
        {
            errtxt = error_text;
            errtxt += ": ";
            errtxt += text;
        }
    }

    size_t n_err;
    unsigned long errstack[MAX_ERRORS];
    std::string errtxt;
    int ssl_err;
};

// return an OpenSSL error string

inline std::string openssl_error()
{
    OpenSSLException err;
    return err.what_str();
}

inline std::string openssl_error(const int ssl_error)
{
    OpenSSLException err(ssl_error);
    return err.what_str();
}

inline void openssl_clear_error_stack()
{
    while (ERR_get_error())
        ;
}

} // namespace openvpn

#endif // OPENVPN_OPENSSL_UTIL_ERROR_H
