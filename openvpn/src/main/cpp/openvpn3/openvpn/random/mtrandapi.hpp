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

// Non-cryptographic random number generator

#ifndef OPENVPN_RANDOM_MTRANDAPI_H
#define OPENVPN_RANDOM_MTRANDAPI_H

#include <random>

#include <openvpn/common/size.hpp>
#include <openvpn/random/randapi.hpp>
#include <openvpn/random/randbytestore.hpp>

namespace openvpn {

  class MTRand : public RandomAPI
  {
  public:
    OPENVPN_EXCEPTION(mtrand_error);

    typedef RCPtr<MTRand> Ptr;
    typedef std::mt19937_64 rand_type;

    MTRand(RandomAPI& seed)
      : rng(gen_seed(seed))
    {
    }

    MTRand()
      : rng(gen_seed())
    {
    }

    MTRand(const rand_type::result_type seed)
      : rng(seed)
    {
    }

    // Random algorithm name
    virtual std::string name() const
    {
      return "MTRand";
    }

    // Return true if algorithm is crypto-strength
    virtual bool is_crypto() const
    {
      return false;
    }

    // Fill buffer with random bytes
    virtual void rand_bytes(unsigned char *buf, size_t size)
    {
      if (!rndbytes(buf, size))
	throw mtrand_error("rand_bytes failed");
    }

    // Like rand_bytes, but don't throw exception.
    // Return true on successs, false on fail.
    virtual bool rand_bytes_noexcept(unsigned char *buf, size_t size)
    {
      return rndbytes(buf, size);
    }

    rand_type::result_type rand()
    {
      return rng();
    }

  private:
    bool rndbytes(unsigned char *buf, size_t size)
    {
      while (size--)
	*buf++ = rbs.get_byte(rng);
      return true;
    }

    static rand_type::result_type gen_seed(RandomAPI& seed)
    {
      return seed.rand_get<rand_type::result_type>();
    }

    static rand_type::result_type gen_seed()
    {
      std::random_device rd;
      RandomByteStore<decltype(rd)> rbs;
      rand_type::result_type ret;
      rbs.fill(ret, rd);
      return ret;
    }

    rand_type rng;
    RandomByteStore<rand_type> rbs;
  };

}

#endif
