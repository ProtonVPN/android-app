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

// Wrap an OpenSSL X509 object

#pragma once

#include <string>
#include <vector>

#include <openssl/ssl.h>
#include <openssl/bio.h>

#include <openvpn/common/size.hpp>
#include <openvpn/common/numeric_cast.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/openssl/util/error.hpp>

namespace openvpn::OpenSSLPKI {

class X509
{
    using X509_unique_ptr = std::unique_ptr<::X509, decltype(&::X509_free)>;

  public:
    X509() = default;

    X509(const std::string &cert_txt, const std::string &title)
    {
        parse_pem(cert_txt, title);
    }

    explicit X509(::X509 *x509, const bool create = true)
    {
        if (create)
            x509_ = {x509, ::X509_free};
        else
            x509_ = {dup(x509), ::X509_free};
    }

    X509(const X509 &other)
        : x509_{dup(other.x509_.get()), ::X509_free}
    {
    }

    X509(X509 &&other) noexcept
        : x509_(std::move(other.x509_))
    {
    }

    X509 &operator=(const X509 &other)
    {
        if (this != &other)
        {
            x509_ = dup(other.x509_);
        }
        return *this;
    }

    X509 &operator=(X509 &&other) noexcept
    {
        if (this != &other)
        {
            x509_ = std::move(other.x509_);
        }
        return *this;
    }

    bool defined() const
    {
        return static_cast<bool>(x509_);
    }

    ::X509 *obj() const
    {
        return x509_.get();
    }

    [[nodiscard]] ::X509 *obj_dup() const
    {
        return dup(x509_.get());
    }

    void parse_pem(const std::string &cert_txt, const std::string &title)
    {
        BIO *bio = ::BIO_new_mem_buf(const_cast<char *>(cert_txt.c_str()), numeric_cast<int>(cert_txt.length()));
        if (!bio)
            throw OpenSSLException();

        ::X509 *cert = ::PEM_read_bio_X509(bio, nullptr, nullptr, nullptr);
        ::BIO_free(bio);
        if (!cert)
            throw OpenSSLException(std::string("X509::parse_pem: error in ") + title + std::string(":"));

        x509_ = {cert, X509_free};
    }

    [[nodiscard]] std::string render_pem() const
    {
        if (x509_)
        {
            BIO *bio = ::BIO_new(BIO_s_mem());
            const int ret = ::PEM_write_bio_X509(bio, x509_.get());
            if (ret == 0)
            {
                ::BIO_free(bio);
                throw OpenSSLException("X509::render_pem");
            }

            {
                char *temp;
                const auto buf_len = ::BIO_get_mem_data(bio, &temp);
                std::string ret = std::string(temp, buf_len);
                ::BIO_free(bio);
                return ret;
            }
        }
        else
            return "";
    }



  private:
    static X509_unique_ptr dup(const X509_unique_ptr &x509)
    {
        if (x509)
        {
            ::X509 *dup = ::X509_dup(const_cast<::X509 *>(x509.get()));
            return {dup, ::X509_free};
        }
        else
        {
            return {nullptr, ::X509_free};
        }
    }
    static ::X509 *dup(const ::X509 *x509)
    {
        if (x509)
            return ::X509_dup(const_cast<::X509 *>(x509));
        else
            return nullptr;
    }

    X509_unique_ptr x509_{nullptr, ::X509_free};
};

class X509List : public std::vector<X509>
{
  public:
    typedef X509 Item;

    bool defined() const
    {
        return !empty();
    }

    std::string render_pem() const
    {
        std::string ret;
        for (const auto &e : *this)
            ret += e.render_pem();
        return ret;
    }
};
} // namespace openvpn::OpenSSLPKI
