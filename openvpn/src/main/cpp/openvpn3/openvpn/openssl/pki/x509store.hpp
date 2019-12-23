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

// Wrap an OpenSSL X509Store object

#ifndef OPENVPN_OPENSSL_PKI_X509STORE_H
#define OPENVPN_OPENSSL_PKI_X509STORE_H

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/pki/cclist.hpp>
#include <openvpn/openssl/util/error.hpp>
#include <openvpn/openssl/pki/x509.hpp>
#include <openvpn/openssl/pki/crl.hpp>

namespace openvpn {
  namespace OpenSSLPKI {

    class X509Store : public RC<thread_unsafe_refcount>
    {
    public:
      OPENVPN_SIMPLE_EXCEPTION(x509_store_init_error);
      OPENVPN_SIMPLE_EXCEPTION(x509_store_add_cert_error);
      OPENVPN_SIMPLE_EXCEPTION(x509_store_add_crl_error);

      typedef CertCRLListTemplate<X509List, CRLList> CertCRLList;

      X509Store() : x509_store_(nullptr) {}

      explicit X509Store(const CertCRLList& cc)
      {
	init();

	// Load cert list
	{
	  for (X509List::const_iterator i = cc.certs.begin(); i != cc.certs.end(); ++i)
	    {
	      if (!X509_STORE_add_cert(x509_store_, (*i)->obj()))
		throw x509_store_add_cert_error();
	    }
	}

	// Load CRL list
	{
	  if (cc.crls.defined())
	    {
	      X509_STORE_set_flags(x509_store_, X509_V_FLAG_CRL_CHECK | X509_V_FLAG_CRL_CHECK_ALL);
	      for (CRLList::const_iterator i = cc.crls.begin(); i != cc.crls.end(); ++i)
		{
		  if (!X509_STORE_add_crl(x509_store_, (*i)->obj()))
		    throw x509_store_add_crl_error();
		}
	    }
	}
      }

      X509_STORE* obj() const { return x509_store_; }

      X509_STORE* move()
      {
	X509_STORE* ret = x509_store_;
	x509_store_ = nullptr;
	return ret;
      }

      ~X509Store()
      {
	if (x509_store_)
	  X509_STORE_free(x509_store_);
      }

    private:
      void init()
      {
	x509_store_ = X509_STORE_new();
	if (!x509_store_)
	  throw x509_store_init_error();
      }

      X509_STORE* x509_store_;
    };
  }
} // namespace openvpn

#endif // OPENVPN_OPENSSL_PKI_X509STORE_H
