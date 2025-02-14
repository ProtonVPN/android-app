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

// Wrap the Apple Cryptographic Random API defined in <Security/SecRandom.h>
// so that it can be used as the primary source of cryptographic entropy by
// the OpenVPN core.

#ifndef OPENVPN_APPLECRYPTO_UTIL_RAND_H
#define OPENVPN_APPLECRYPTO_UTIL_RAND_H

#include <Security/SecRandom.h>

#include <openvpn/random/randapi.hpp>

namespace openvpn {
class AppleRandom : public StrongRandomAPI
{
  public:
    OPENVPN_EXCEPTION(rand_error_apple);

    typedef RCPtr<AppleRandom> Ptr;

    AppleRandom() = default;

    std::string name() const override
    {
        return "AppleRandom";
    }

    // Fill buffer with random bytes
    void rand_bytes(unsigned char *buf, size_t size) override
    {
        if (!rndbytes(buf, size))
            throw rand_error_apple("rand_bytes");
    }

    // Like rand_bytes, but don't throw exception.
    // Return true on successs, false on fail.
    bool rand_bytes_noexcept(unsigned char *buf, size_t size) override
    {
        return rndbytes(buf, size);
    }

  private:
    bool rndbytes(unsigned char *buf, size_t size)
    {
        return SecRandomCopyBytes(kSecRandomDefault, size, buf) ? false : true;
    }
};
} // namespace openvpn

#endif
