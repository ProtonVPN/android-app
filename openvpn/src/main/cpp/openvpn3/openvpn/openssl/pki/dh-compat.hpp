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

// Wrap an OpenSSL DH object

#pragma once

#include <string>

#include <openssl/ssl.h>
#include <openssl/bio.h>
#include <openssl/dh.h>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/openssl/util/error.hpp>

// workaround for bug in DHparams_dup macro on OpenSSL 0.9.8 and lower
#if SSLEAY_VERSION_NUMBER <= 0x00908000L
#undef CHECKED_PTR_OF
#define CHECKED_PTR_OF(type, p) ((char *)(1 ? p : (type *)0))
#endif

namespace openvpn::OpenSSLPKI {

namespace DH_private {
// defined outside of DH class to avoid symbol collision in way
// that DHparams_dup macro is defined
inline ::DH *dup(const ::DH *dh)
{
    if (dh)
        return DHparams_dup(const_cast<::DH *>(dh));
    else
        return nullptr;
}
} // namespace DH_private

class DH
{
  public:
    DH()
        : dh_(nullptr)
    {
    }

    explicit DH(const std::string &dh_txt)
        : dh_(nullptr)
    {
        parse_pem(dh_txt);
    }

    DH(const DH &other)
    {
        dup(other.dh_);
    }

    DH(DH &&other) noexcept
        : dh_(other.dh_)
    {
        other.dh_ = nullptr;
    }

    void operator=(const DH &other)
    {
        if (this != &other)
        {
            erase();
            dup(other.dh_);
        }
    }

    DH &operator=(DH &&other) noexcept
    {
        if (this != &other)
        {
            erase();
            dh_ = other.dh_;
            other.dh_ = nullptr;
        }
        return *this;
    }

    bool defined() const
    {
        return dh_ != nullptr;
    }
    ::DH *obj() const
    {
        return dh_;
    }

    void parse_pem(const std::string &dh_txt)
    {
        BIO *bio = ::BIO_new_mem_buf(const_cast<char *>(dh_txt.c_str()), dh_txt.length());
        if (!bio)
            throw OpenSSLException();

        ::DH *dh = ::PEM_read_bio_DHparams(bio, nullptr, nullptr, nullptr);
        ::BIO_free(bio);
        if (!dh)
            throw OpenSSLException("DH::parse_pem");

        erase();
        dh_ = dh;
    }

    std::string render_pem() const
    {
        if (dh_)
        {
            BIO *bio = ::BIO_new(BIO_s_mem());
            const int ret = ::PEM_write_bio_DHparams(bio, dh_);
            if (ret == 0)
            {
                ::BIO_free(bio);
                throw OpenSSLException("DH::render_pem");
            }

            {
                char *temp;
                const int buf_len = ::BIO_get_mem_data(bio, &temp);
                std::string ret = std::string(temp, buf_len);
                ::BIO_free(bio);
                return ret;
            }
        }
        else
            return "";
    }

    ~DH()
    {
        erase();
    }

  private:
    void erase()
    {
        if (dh_)
            ::DH_free(dh_);
    }

    void dup(const ::DH *dh)
    {
        dh_ = DH_private::dup(dh);
    }

    ::DH *dh_;
};
} // namespace openvpn::OpenSSLPKI
