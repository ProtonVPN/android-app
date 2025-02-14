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

// Wrap an OpenSSL X509_CRL object

#pragma once

#include <string>
#include <vector>

#include <openssl/ssl.h>
#include <openssl/bio.h>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/openssl/util/error.hpp>

namespace openvpn::OpenSSLPKI {

class CRL
{
  public:
    CRL()
        : crl_(nullptr)
    {
    }

    explicit CRL(const std::string &crl_txt)
        : crl_(nullptr)
    {
        parse_pem(crl_txt);
    }

    CRL(const CRL &other)
        : crl_(dup(other.crl_))
    {
    }

    CRL(CRL &&other) noexcept
        : crl_(other.crl_)
    {
        other.crl_ = nullptr;
    }

    CRL &operator=(const CRL &other)
    {
        if (this != &other)
        {
            erase();
            crl_ = dup(other.crl_);
        }
        return *this;
    }

    CRL &operator=(CRL &&other) noexcept
    {
        if (this != &other)
        {
            erase();
            crl_ = other.crl_;
            other.crl_ = nullptr;
        }
        return *this;
    }

    bool defined() const
    {
        return crl_ != nullptr;
    }
    ::X509_CRL *obj() const
    {
        return crl_;
    }

    void parse_pem(const std::string &crl_txt)
    {
        BIO *bio = ::BIO_new_mem_buf(const_cast<char *>(crl_txt.c_str()), numeric_cast<int>(crl_txt.length()));
        if (!bio)
            throw OpenSSLException();

        ::X509_CRL *crl = ::PEM_read_bio_X509_CRL(bio, nullptr, nullptr, nullptr);
        ::BIO_free(bio);
        if (!crl)
            throw OpenSSLException("CRL::parse_pem");

        erase();
        crl_ = crl;
    }

    std::string render_pem() const
    {
        if (crl_)
        {
            BIO *bio = ::BIO_new(BIO_s_mem());
            const int ret = ::PEM_write_bio_X509_CRL(bio, crl_);
            if (ret == 0)
            {
                ::BIO_free(bio);
                throw OpenSSLException("CRL::render_pem");
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

    ~CRL()
    {
        erase();
    }

  private:
    void erase()
    {
        if (crl_)
            ::X509_CRL_free(crl_);
    }

    static X509_CRL *dup(const X509_CRL *crl)
    {
        if (crl)
            return ::X509_CRL_dup(const_cast<X509_CRL *>(crl));
        else
            return nullptr;
    }

    ::X509_CRL *crl_;
};

class CRLList : public std::vector<CRL>
{
  public:
    typedef X509 CRL;

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
