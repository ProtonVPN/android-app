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

#ifndef OPENVPN_RANDOM_DEVURAND_H
#define OPENVPN_RANDOM_DEVURAND_H

#include <sys/types.h> // for open()
#include <sys/stat.h>  // for open()
#include <fcntl.h>     // for open()

#include <unistd.h> // for read()

#include <openvpn/common/scoped_fd.hpp>
#include <openvpn/random/randapi.hpp>

namespace openvpn {

class DevURand : public StrongRandomAPI
{
  public:
    OPENVPN_EXCEPTION(dev_urand_error);

    typedef RCPtr<DevURand> Ptr;

    DevURand()
        : dev_urandom(::open("/dev/urandom", O_RDONLY))
    {
        if (!dev_urandom.defined())
            throw dev_urand_error("init failed");
    }

    // Random algorithm name
    std::string name() const override
    {
        return "DevURand";
    }

    // Fill buffer with random bytes
    void rand_bytes(unsigned char *buf, size_t size) override
    {
        if (!rndbytes(buf, size))
            throw dev_urand_error("rand_bytes failed");
    }

    // Like rand_bytes, but don't throw exception.
    // Return true on successs, false on fail.
    bool rand_bytes_noexcept(unsigned char *buf, size_t size) override
    {
        return rndbytes(buf, size);
    }

  private:
    bool rndbytes(unsigned char *buf, ssize_t size)
    {
        const ssize_t actual = ::read(dev_urandom(), buf, size);
        return size == actual;
    }

    ScopedFD dev_urandom;
};

} // namespace openvpn

#endif
