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

// Wrap a mbed TLS x509_crt object

#ifndef OPENVPN_MBEDTLS_PKI_X509CERT_H
#define OPENVPN_MBEDTLS_PKI_X509CERT_H

#include <string>
#include <sstream>
#include <cstring>
#include <iostream>

#include <mbedtls/x509.h>
#include <mbedtls/pem.h>
#include <mbedtls/base64.h>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/mbedtls/util/error.hpp>

namespace openvpn {
  namespace MbedTLSPKI {

    class X509Cert : public RC<thread_unsafe_refcount>
    {
    public:
      typedef RCPtr<X509Cert> Ptr;

      X509Cert() : chain(nullptr) {}

      X509Cert(const std::string& cert_txt, const std::string& title, const bool strict)
	: chain(nullptr)
      {
	try {
	  parse(cert_txt, title, strict);
	}
	catch (...)
	  {
	    dealloc();
	    throw;
	  }
      }

      void parse(const std::string& cert_txt, const std::string& title, const bool strict)
      {
	alloc();

	if (cert_txt.empty())
	  throw MbedTLSException(title + " certificate is undefined");

	// cert_txt.length() is increased by 1 as it does not include the NULL-terminator
	// which mbedtls_x509_crt_parse() expects to see.
	const int status = mbedtls_x509_crt_parse(chain,
						  (const unsigned char *)cert_txt.c_str(),
						  cert_txt.length() + 1);
	if (status < 0)
	  {
	    throw MbedTLSException("error parsing " + title + " certificate", status);
	  }
	if (status > 0)
	  {
	    std::ostringstream os;
	    os << status << " certificate(s) in " << title << " bundle failed to parse";
	    if (strict)
	      throw MbedTLSException(os.str());
	    else
	      OPENVPN_LOG("MBEDTLS: " << os.str());
	  }
      }

      static std::string der_to_pem(const unsigned char* der, size_t der_size)
      {
	size_t olen = 0;
	int ret;

	ret = mbedtls_pem_write_buffer(begin_cert, end_cert, der,
				       der_size, NULL, 0, &olen);
	if (ret != MBEDTLS_ERR_BASE64_BUFFER_TOO_SMALL)
	  throw MbedTLSException("X509Cert::extract: can't calculate PEM size");

	BufferAllocated buff(olen, 0);

	ret = mbedtls_pem_write_buffer(begin_cert, end_cert, der,
				       der_size, buff.data(), buff.max_size(), &olen);
	if (ret)
	  throw MbedTLSException("X509Cert::extract: can't write PEM buffer");

	return std::string((const char *)buff.data());
      }

      std::string extract() const
      {
	return der_to_pem(chain->raw.p, chain->raw.len);
      }

      std::vector<std::string> extract_extra_certs() const
      {
	std::vector<std::string> extra_certs;

	/* extra certificates are appended to the main one */
	for (mbedtls_x509_crt *cert = chain->next; cert; cert = cert->next)
	  {
	    extra_certs.push_back(der_to_pem(cert->raw.p, cert->raw.len));
	  }
	return extra_certs;
      }

      mbedtls_x509_crt* get() const
      {
	return chain;
      }

      virtual ~X509Cert()
      {
	dealloc();
      }

    protected:
      void alloc()
      {
	if (!chain)
	  {
	    chain = new mbedtls_x509_crt;
	    mbedtls_x509_crt_init(chain);
	  }
      }

      mbedtls_x509_crt *chain;

    private:
      void dealloc()
      {
	if (chain)
	  {
	    mbedtls_x509_crt_free(chain);
	    delete chain;
	    chain = nullptr;
	  }
      }

      constexpr static const char* begin_cert = "-----BEGIN CERTIFICATE-----\n";;
      constexpr static const char* end_cert  = "-----END CERTIFICATE-----\n";;
    };
  }
}

#endif
