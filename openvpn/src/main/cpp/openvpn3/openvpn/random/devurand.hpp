//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2022 OpenVPN Inc.
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

#ifndef OPENVPN_RANDOM_DEVURAND_H
#define OPENVPN_RANDOM_DEVURAND_H

#include <sys/types.h> // for open()
#include <sys/stat.h>  // for open()
#include <fcntl.h>     // for open()

#include <unistd.h> // for read()

#include <openvpn/common/scoped_fd.hpp>
#include <openvpn/random/randapi.hpp>

namespace openvpn {

class DevURand : public RandomAPI
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
    virtual std::string name() const
    {
        return "DevURand";
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
            throw dev_urand_error("rand_bytes failed");
    }

    // Like rand_bytes, but don't throw exception.
    // Return true on successs, false on fail.
    virtual bool rand_bytes_noexcept(unsigned char *buf, size_t size)
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
