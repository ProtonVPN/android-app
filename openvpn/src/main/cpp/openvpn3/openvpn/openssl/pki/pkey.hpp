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

// Wrap an OpenSSL EVP_PKEY object

#ifndef OPENVPN_OPENSSL_PKI_PKEY_H
#define OPENVPN_OPENSSL_PKI_PKEY_H

#include <string>

#include <openssl/ssl.h>
#include <openssl/bio.h>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/openssl/util/error.hpp>

namespace openvpn {
  namespace OpenSSLPKI {

    class PKey
    {
    public:
      PKey() : pkey_(nullptr) {}

      PKey(const std::string& pkey_txt, const std::string& title)
	: pkey_(nullptr)
      {
	parse_pem(pkey_txt, title);
      }

      PKey(const PKey& other)
	: pkey_(nullptr)
      {
	assign(other.pkey_);
      }

      void operator=(const PKey& other)
      {
	assign(other.pkey_);
	priv_key_pwd = other.priv_key_pwd;
      }

      bool defined() const { return pkey_ != nullptr; }
      EVP_PKEY* obj() const { return pkey_; }

      SSLConfigAPI::PKType key_type() const
      {
	switch (EVP_PKEY_id(pkey_))
	{
	case EVP_PKEY_RSA:
	case EVP_PKEY_RSA2:
	  return SSLConfigAPI::PK_RSA;
	case EVP_PKEY_EC:
	  return SSLConfigAPI::PK_EC;
	case EVP_PKEY_DSA:
	case EVP_PKEY_DSA1:
	case EVP_PKEY_DSA2:
	case EVP_PKEY_DSA3:
	case EVP_PKEY_DSA4:
	  return SSLConfigAPI::PK_DSA;
	case EVP_PKEY_NONE:
	  return SSLConfigAPI::PK_NONE;
	default:
	  return SSLConfigAPI::PK_UNKNOWN;
	}
      }

      size_t key_length() const
      {
	int ret = i2d_PrivateKey(pkey_, NULL);
	if (ret < 0)
	  return 0;

	/* convert to bits */
	return ret * 8;
      }

      void set_private_key_password(const std::string& pwd)
      {
	priv_key_pwd = pwd;
      }

      void parse_pem(const std::string& pkey_txt, const std::string& title)
      {
	BIO *bio = BIO_new_mem_buf(const_cast<char *>(pkey_txt.c_str()), pkey_txt.length());
	if (!bio)
	  throw OpenSSLException();

	EVP_PKEY *pkey = PEM_read_bio_PrivateKey(bio, nullptr, pem_password_callback, this);
	BIO_free(bio);
	if (!pkey)
	  throw OpenSSLException(std::string("PKey::parse_pem: error in ") + title + std::string(":"));

	erase();
	pkey_ = pkey;
      }

      std::string render_pem() const
      {
	if (pkey_)
	  {
	    BIO *bio = BIO_new(BIO_s_mem());
	    const int ret = PEM_write_bio_PrivateKey(bio, pkey_, nullptr, nullptr, 0, nullptr, nullptr);
	    if (ret == 0)
	      {
		BIO_free(bio);
		throw OpenSSLException("PKey::render_pem");
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

      void erase()
      {
	if (pkey_)
	  {
	    EVP_PKEY_free(pkey_);
	    pkey_ = nullptr;
	  }
      }

      ~PKey()
      {
	erase();
      }

    private:
      static int pem_password_callback (char *buf, int size, int rwflag, void *userdata)
      {
	// get this
	const PKey* self = (PKey*) userdata;	
	if (buf)
	  {
	    string::strncpynt(buf, self->priv_key_pwd.c_str(), size);
	    return std::strlen(buf);
	  }
	return 0;
      }

      static EVP_PKEY *dup(const EVP_PKEY *pkey)
      {
	// No OpenSSL EVP_PKEY_dup method so we roll our own 
	if (pkey)
	  {
	    EVP_PKEY* pDupKey = EVP_PKEY_new();
	    RSA* pRSA = EVP_PKEY_get1_RSA(const_cast<EVP_PKEY *>(pkey));
	    RSA* pRSADupKey = RSAPrivateKey_dup(pRSA);
	    RSA_free(pRSA);
	    EVP_PKEY_set1_RSA(pDupKey, pRSADupKey);
	    RSA_free(pRSADupKey);
	    return pDupKey;
	  }
	else
	  return nullptr;
      }

      void assign(const EVP_PKEY *pkey)
      {
	erase();
	pkey_ = dup(pkey);
      }

      std::string priv_key_pwd;
      EVP_PKEY *pkey_;
    };
  }
} // namespace openvpn

#endif // OPENVPN_OPENSSL_PKI_PKEY_H
