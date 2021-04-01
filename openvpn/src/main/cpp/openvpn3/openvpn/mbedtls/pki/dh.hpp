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

// Wrap a mbed TLS dhm_context object (Diffie Hellman parameters).

#ifndef OPENVPN_MBEDTLS_PKI_DH_H
#define OPENVPN_MBEDTLS_PKI_DH_H

#include <string>
#include <sstream>
#include <cstring>

#include <mbedtls/x509.h>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/mbedtls/util/error.hpp>

namespace openvpn {
  namespace MbedTLSPKI {

    class DH : public RC<thread_unsafe_refcount>
    {
    public:
      typedef RCPtr<DH> Ptr;

      DH() : dhc(nullptr) {}

      DH(const std::string& dh_txt, const std::string& title)
	: dhc(nullptr)
      {
	try {
	  parse(dh_txt, title);
	}
	catch (...)
	  {
	    dealloc();
	    throw;
	  }
      }

      void parse(const std::string& dh_txt, const std::string& title)
      {
	alloc();
	// dh_txt.length() is increased by 1 as it does not include the NULL-terminator
	// which mbedtls_dhm_parse_dhm() expects to see.
	const int status = mbedtls_dhm_parse_dhm(dhc,
					 (const unsigned char *)dh_txt.c_str(),
					 dh_txt.length() + 1);
	if (status < 0)
	  {
	    throw MbedTLSException("error parsing " + title + " DH parameters", status);
	  }
	if (status > 0)
	  {
	    std::ostringstream os;
	    os << status << " DH parameters in " << title << " failed to parse";
	    throw MbedTLSException(os.str());
	  }
	// store PEM data to allow extraction
	pem_dhc = dh_txt;
      }

      std::string extract() const
      {
	return std::string(pem_dhc);
      }

      mbedtls_dhm_context* get() const
      {
	return dhc;
      }

      ~DH()
      {
	dealloc();
      }

    private:
      void alloc()
      {
	if (!dhc)
	  {
	    dhc = new mbedtls_dhm_context;
	    mbedtls_dhm_init(dhc);
	  }
      }

      void dealloc()
      {
	if (dhc)
	  {
	    mbedtls_dhm_free(dhc);
	    delete dhc;
	    dhc = nullptr;
	  }
      }

      mbedtls_dhm_context *dhc;
      std::string pem_dhc;
    };
  }
}

#endif
