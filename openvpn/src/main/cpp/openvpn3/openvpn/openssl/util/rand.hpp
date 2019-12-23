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

// Wrap the OpenSSL Cryptographic Random API defined in <openssl/rand.h>
// so that it can be used as the primary source of cryptographic entropy by
// the OpenVPN core.

#ifndef OPENVPN_OPENSSL_UTIL_RAND_H
#define OPENVPN_OPENSSL_UTIL_RAND_H

#include <openssl/rand.h>

#include <openvpn/random/randapi.hpp>

namespace openvpn {
  class OpenSSLRandom : public RandomAPI
  {
  public:
    OPENVPN_EXCEPTION(rand_error_openssl);

    typedef RCPtr<OpenSSLRandom> Ptr;

    OpenSSLRandom(const bool prng)
    {
    }

    virtual std::string name() const
    {
      return "OpenSSLRandom";
    }

    // Return true if algorithm is crypto-strength
    virtual bool is_crypto() const
    {
      return true;
    }

    // Fill buffer with random bytes
    virtual void rand_bytes(unsigned char *buf, size_t size)
    {
      if (!rndbytes(buf, size))
	throw rand_error_openssl("rand_bytes");
    }

    // Like rand_bytes, but don't throw exception.
    // Return true on successs, false on fail.
    virtual bool rand_bytes_noexcept(unsigned char *buf, size_t size)
    {
      return rndbytes(buf, size);
    }

  private:
    bool rndbytes(unsigned char *buf, size_t size)
    {
      return RAND_bytes(buf, size) == 1;
    }
  };
}

#endif
