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

// Verify a PKCS7 signature

#ifndef OPENVPN_OPENSSL_SIGN_PKCS7VERIFY_H
#define OPENVPN_OPENSSL_SIGN_PKCS7VERIFY_H

#include <string>

#include <openssl/ssl.h>
#include <openssl/bio.h>
#include <openssl/pkcs7.h>

#include <openvpn/common/cleanup.hpp>
#include <openvpn/openssl/pki/x509.hpp>
#include <openvpn/openssl/util/error.hpp>

namespace openvpn {
  namespace OpenSSLSign {
    /*
     * Verify PKCS7 signature.
     * On success, return.
     * On fail, throw exception.
     */
    inline void verify_pkcs7(const std::list<OpenSSLPKI::X509>& certs,
			     const std::string& sig,
			     const std::string& data)
    {
      STACK_OF(X509) *x509_stack = nullptr;
      BIO *in = nullptr;
      PKCS7 *p7 = nullptr;

      auto clean = Cleanup([&]() {
	  if (x509_stack)
	    sk_X509_free(x509_stack);
	  if (in)
	    BIO_free(in);
	  if (p7)
	    PKCS7_free(p7);
	});

      /* create x509_stack from cert */
      x509_stack = sk_X509_new_null();
      for (const auto& cert : certs)
	sk_X509_push(x509_stack, cert.obj());

      /* get signature */
      in = BIO_new_mem_buf(sig.c_str(), sig.length());
      p7 = PEM_read_bio_PKCS7(in, NULL, NULL, NULL);
      if (!p7)
	throw OpenSSLException("OpenSSLSign::verify_pkcs7: failed to parse pkcs7 signature");
      BIO_free(in);
      in = nullptr;

      /* get data */
      in = BIO_new_mem_buf(data.c_str(), data.length());

      /* OpenSSL 1.0.2e and higher no longer allows calling PKCS7_verify
	 with both data and content.  Empty out the content. */
      p7->d.sign->contents->d.ptr = 0;

      /* do the verify */
      if (PKCS7_verify(p7, x509_stack, NULL, in, NULL, PKCS7_NOVERIFY) != 1)
	throw OpenSSLException("OpenSSLSign::verify_pkcs7: verification failed");
    }
  }
}

#endif
