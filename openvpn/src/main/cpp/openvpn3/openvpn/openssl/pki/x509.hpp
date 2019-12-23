//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2017 OpenVPN Inc.
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

// Wrap an OpenSSL X509 object

#ifndef OPENVPN_OPENSSL_PKI_X509_H
#define OPENVPN_OPENSSL_PKI_X509_H

#include <string>
#include <vector>

#include <openssl/ssl.h>
#include <openssl/bio.h>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/openssl/util/error.hpp>

namespace openvpn {
  namespace OpenSSLPKI {

    class X509;

    class X509Base
    {
    public:
      X509Base() : x509_(nullptr) {}
      explicit X509Base(::X509 *x509) : x509_(x509) {}

      bool defined() const { return x509_ != nullptr; }
      ::X509* obj() const { return x509_; }
      ::X509* obj_dup() const { return dup(x509_); }

      std::string render_pem() const
      {
	if (x509_)
	  {
	    BIO *bio = BIO_new(BIO_s_mem());
	    const int ret = PEM_write_bio_X509(bio, x509_);
	    if (ret == 0)
	      {
		BIO_free(bio);
		throw OpenSSLException("X509::render_pem");
	      }

	    {
	      char *temp;
	      const int buf_len = BIO_get_mem_data(bio, &temp);
	      std::string ret = std::string(temp, buf_len);
	      BIO_free(bio);
	      return ret;
	    }
	  }
	else
	  return "";
      }

    private:
      static ::X509 *dup(const ::X509 *x509)
      {
	if (x509)
	  return X509_dup(const_cast< ::X509 * >(x509));
	else
	  return nullptr;
      }

      friend class X509;
      ::X509 *x509_;
    };

    class X509 : public X509Base, public RC<thread_unsafe_refcount>
    {
    public:
      X509() {}

      X509(const std::string& cert_txt, const std::string& title)
      {
	parse_pem(cert_txt, title);
      }

      X509(const X509& other)
      {
	assign(other.x509_);
      }

      void operator=(const X509& other)
      {
	assign(other.x509_);
      }

      void parse_pem(const std::string& cert_txt, const std::string& title)
      {
	BIO *bio = BIO_new_mem_buf(const_cast<char *>(cert_txt.c_str()), cert_txt.length());
	if (!bio)
	  throw OpenSSLException();

	::X509 *cert = PEM_read_bio_X509(bio, nullptr, nullptr, nullptr);
	BIO_free(bio);
	if (!cert)
	  throw OpenSSLException(std::string("X509::parse_pem: error in ") + title + std::string(":"));

	erase();
	x509_ = cert;
      }

      void erase()
      {
	if (x509_)
	  {
	    X509_free(x509_);
	    x509_ = nullptr;
	  }
      }

      ~X509()
      {
	erase();
      }

    private:
      void assign(const ::X509 *x509)
      {
	erase();
	x509_ = dup(x509);
      }
    };

    typedef RCPtr<X509> X509Ptr;

    class X509List : public std::vector<X509Ptr>
    {
    public:
      typedef X509 Item;
      typedef X509Ptr ItemPtr;

      bool defined() const { return !empty(); }

      std::string render_pem() const
      {
	std::string ret;
	for (const_iterator i = begin(); i != end(); ++i)
	  ret += (*i)->render_pem();
	return ret;
      }
    };
  }
} // namespace openvpn

#endif // OPENVPN_OPENSSL_PKI_X509_H
