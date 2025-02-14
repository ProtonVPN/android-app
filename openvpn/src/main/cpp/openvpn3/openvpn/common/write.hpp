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

#ifndef OPENVPN_COMMON_WRITE_H
#define OPENVPN_COMMON_WRITE_H

#include <unistd.h>
#include <stdlib.h> // defines std::abort()

namespace openvpn {
// like posix write() but retry if full buffer is not written
inline ssize_t write_retry(int fd, const void *buf, size_t count)
{
    size_t total = 0;
    while (true)
    {
        const ssize_t status = ::write(fd, buf, count);
        if (status < 0)
            return status;
        if (static_cast<size_t>(status) > count) // should never happen
            std::abort();
        total += status;
        count -= status;
        if (!count)
            break;
        buf = static_cast<const unsigned char *>(buf) + status;
    }
    return total;
}
} // namespace openvpn

#endif
