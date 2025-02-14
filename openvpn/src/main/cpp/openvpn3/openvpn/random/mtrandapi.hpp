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

// Non-cryptographic random number generator

#ifndef OPENVPN_RANDOM_MTRANDAPI_H
#define OPENVPN_RANDOM_MTRANDAPI_H

#include <random>

#include <openvpn/common/size.hpp>
#include <openvpn/random/randapi.hpp>
#include <openvpn/random/randbytestore.hpp>

namespace openvpn {

class MTRand : public WeakRandomAPI
{
  public:
    OPENVPN_EXCEPTION(mtrand_error);

    typedef RCPtr<MTRand> Ptr;
    typedef std::mt19937_64 rand_type;

    MTRand(RandomAPI &seed)
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
    std::string name() const override
    {
        return "MTRand";
    }

    // Fill buffer with random bytes
    void rand_bytes(unsigned char *buf, size_t size) override
    {
        if (!rndbytes(buf, size))
            throw mtrand_error("rand_bytes failed");
    }

    // Like rand_bytes, but don't throw exception.
    // Return true on successs, false on fail.
    bool rand_bytes_noexcept(unsigned char *buf, size_t size) override
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

    static rand_type::result_type gen_seed(RandomAPI &seed)
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

} // namespace openvpn

#endif
