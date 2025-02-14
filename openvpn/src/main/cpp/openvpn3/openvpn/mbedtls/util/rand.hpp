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

// Wrap the mbed TLS Cryptographic Random API defined in <mbedtls/ctr_drbg.h>
// so that it can be used as the primary source of cryptographic entropy by
// the OpenVPN core.

#ifndef OPENVPN_MBEDTLS_UTIL_RAND_H
#define OPENVPN_MBEDTLS_UTIL_RAND_H

#include <mbedtls/entropy.h>
#if MBEDTLS_VERSION_NUMBER < 0x03000000
#include <mbedtls/entropy_poll.h>
#endif
#include <mbedtls/ctr_drbg.h>

#include <openvpn/random/randapi.hpp>
#include <openvpn/mbedtls/util/error.hpp>

namespace openvpn {

class MbedTLSRandom : public StrongRandomAPI
{
  public:
    OPENVPN_EXCEPTION(rand_error_mbedtls);

    typedef RCPtr<MbedTLSRandom> Ptr;


    MbedTLSRandom(StrongRandomAPI::Ptr entropy_source)
        : entropy(std::move(entropy_source))
    {
        // Init RNG context
        mbedtls_ctr_drbg_init(&ctx);

        // Seed RNG
        const int errnum = mbedtls_ctr_drbg_seed(&ctx, entropy_poll, entropy.get(), nullptr, 0);
        if (errnum < 0)
            throw MbedTLSException("mbedtls_ctr_drbg_seed", errnum);
    }

    MbedTLSRandom()
        : MbedTLSRandom(StrongRandomAPI::Ptr())
    {
    }

    virtual ~MbedTLSRandom()
    {
        // Free RNG context
        mbedtls_ctr_drbg_free(&ctx);
    }

    // Random algorithm name
    std::string name() const override
    {
        const std::string n = "mbedTLS-CTR_DRBG";
        if (entropy)
            return n + '+' + entropy->name();
        else
            return n;
    }

    // Fill buffer with random bytes
    void rand_bytes(unsigned char *buf, size_t size) override
    {
        const int errnum = rndbytes(buf, size);
        if (errnum < 0)
            throw MbedTLSException("mbedtls_ctr_drbg_random", errnum);
    }

    // Like rand_bytes, but don't throw exception.
    // Return true on successs, false on fail.
    bool rand_bytes_noexcept(unsigned char *buf, size_t size) override
    {
        return rndbytes(buf, size) >= 0;
    }

    /**
     * function to get the mbedtls_ctr_drbg_context. This is needed for the pk_parse
     * methods in mbed TLS 3.0 that require a random number generator to avoid side
     * channel attacks when loading private keys. The returned context is tied
     * to the internal state of this random number generator.
     */
    mbedtls_ctr_drbg_context *get_ctr_drbg_ctx()
    {
        return &ctx;
    }

  private:
    int rndbytes(unsigned char *buf, size_t size)
    {
        return mbedtls_ctr_drbg_random(&ctx, buf, size);
    }

    static int entropy_poll(void *arg, unsigned char *output, size_t len)
    {
        if (arg)
        {
            RandomAPI *entropy = (RandomAPI *)arg;
            if (entropy->rand_bytes_noexcept(output, len))
                return 0;
            else
                return MBEDTLS_ERR_ENTROPY_SOURCE_FAILED;
        }
        else
        {
#ifndef OPENVPN_DISABLE_MBEDTLS_PLATFORM_ENTROPY_POLL
            size_t olen;
            return mbedtls_platform_entropy_poll(nullptr, output, len, &olen);
#else
            return MBEDTLS_ERR_ENTROPY_SOURCE_FAILED;
#endif
        }
    }

    mbedtls_ctr_drbg_context ctx;
    RandomAPI::Ptr entropy;
};

} // namespace openvpn

#endif
