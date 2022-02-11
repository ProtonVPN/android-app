//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
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

#pragma once

#include <string>
#include <utility>

#include <openssl/ssl.h>
#include <openssl/bio.h>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/openssl/util/error.hpp>
#include <openvpn/pki/pktype.hpp>
#include <openvpn/crypto/definitions.hpp>
#include <openvpn/openssl/compat.hpp>

namespace openvpn {
  namespace OpenSSLPKI {

    class PKey
    {
    public:
      PKey()
	: pkey_(nullptr)
      {
      }

      PKey(const std::string& pkey_txt, const std::string& title, SSLLib::Ctx ctx)
	: pkey_(nullptr)
      {
	parse_pem(pkey_txt, title, ctx);
      }

      PKey(const PKey& other)
	: pkey_(dup(other.pkey_)),
	  priv_key_pwd(other.priv_key_pwd)
      {
      }

      PKey(PKey&& other) noexcept
	: pkey_(other.pkey_),
	  priv_key_pwd(std::move(other.priv_key_pwd))
      {
	other.pkey_ = nullptr;
      }

      PKey& operator=(const PKey& other)
      {
	if (this != &other)
	  {
	    erase();
	    pkey_ = dup(other.pkey_);
	    priv_key_pwd = other.priv_key_pwd;
	  }
	return *this;
      }

      PKey& operator=(PKey&& other) noexcept
      {
	if (this != &other)
	  {
	    erase();
	    pkey_ = other.pkey_;
	    other.pkey_ = nullptr;
	    priv_key_pwd = std::move(other.priv_key_pwd);
	  }
	return *this;
      }

      bool defined() const { return pkey_ != nullptr; }
      ::EVP_PKEY* obj() const { return pkey_; }

      PKType::Type key_type() const
      {
	switch (::EVP_PKEY_id(pkey_))
	{
	case EVP_PKEY_RSA:
	case EVP_PKEY_RSA2:
	  return PKType::PK_RSA;
	case EVP_PKEY_EC:
	  return PKType::PK_EC;
	case EVP_PKEY_DSA:
	case EVP_PKEY_DSA1:
	case EVP_PKEY_DSA2:
	case EVP_PKEY_DSA3:
	case EVP_PKEY_DSA4:
	  return PKType::PK_DSA;
	case EVP_PKEY_NONE:
	  return PKType::PK_NONE;
	default:
	  return PKType::PK_UNKNOWN;
	}
      }

      size_t key_length() const
      {
	int ret = ::i2d_PrivateKey(pkey_, NULL);
	if (ret < 0)
	  return 0;

	/* convert to bits */
	return ret * 8;
      }

      void set_private_key_password(const std::string& pwd)
      {
	priv_key_pwd = pwd;
      }

      void parse_pem(const std::string& pkey_txt, const std::string& title, SSLLib::Ctx libctx)
      {
	BIO *bio = ::BIO_new_mem_buf(const_cast<char *>(pkey_txt.c_str()), pkey_txt.length());
	if (!bio)
	  throw OpenSSLException();

	::EVP_PKEY *pkey = ::PEM_read_bio_PrivateKey_ex(bio, nullptr, pem_password_callback, this, libctx, nullptr);
	::BIO_free(bio);
	if (!pkey)
	  throw OpenSSLException(std::string("PKey::parse_pem: error in ") + title + std::string(":"));

	erase();
	pkey_ = pkey;
      }

      std::string render_pem() const
      {
	if (pkey_)
	  {
	    BIO *bio = ::BIO_new(BIO_s_mem());
	    const int ret = ::PEM_write_bio_PrivateKey(bio, pkey_, nullptr, nullptr, 0, nullptr, nullptr);
	    if (ret == 0)
	      {
		::BIO_free(bio);
		throw OpenSSLException("PKey::render_pem");
	      }

	    {
	      char *temp;
	      const size_t buf_len = ::BIO_get_mem_data(bio, &temp);
	      std::string ret = std::string(temp, buf_len);
	      ::BIO_free(bio);
	      return ret;
	    }
	  }
	else
	  return "";
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

      void erase()
      {
	if (pkey_)
	  ::EVP_PKEY_free(pkey_);
      }

#if OPENSSL_VERSION_NUMBER < 0x30000000L
      static ::EVP_PKEY *dup(const ::EVP_PKEY *pkey)
      {
	// No OpenSSL EVP_PKEY_dup method so we roll our own 
	if (pkey)
	  {
	    ::EVP_PKEY* pDupKey = ::EVP_PKEY_new();
	    ::RSA* pRSA = ::EVP_PKEY_get1_RSA(const_cast<::EVP_PKEY *>(pkey));
	    ::RSA* pRSADupKey = ::RSAPrivateKey_dup(pRSA);
	    ::RSA_free(pRSA);
	    ::EVP_PKEY_set1_RSA(pDupKey, pRSADupKey);
	    ::RSA_free(pRSADupKey);
	    return pDupKey;
	  }
	else
	  return nullptr;
      }
#else
      static ::EVP_PKEY *dup(const ::EVP_PKEY *pkey)
      {
	if (pkey)
	  return EVP_PKEY_dup(const_cast<EVP_PKEY*>(pkey));
	else
	  return nullptr;
      }
#endif

      ::EVP_PKEY *pkey_;
      std::string priv_key_pwd;
    };
  }
}
