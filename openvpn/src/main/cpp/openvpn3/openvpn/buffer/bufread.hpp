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

#ifndef OPENVPN_BUFFER_BUFREAD_H
#define OPENVPN_BUFFER_BUFREAD_H

#include <unistd.h>
#include <errno.h>

#include <string>
#include <cstring>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/strerror.hpp>
#include <openvpn/buffer/buflist.hpp>

namespace openvpn {
OPENVPN_EXCEPTION(buf_read_error);

inline bool buf_read(const int fd, Buffer &buf, const std::string &title)
{
    const ssize_t status = ::read(fd, buf.data_end(), buf.remaining(0));
    if (status < 0)
    {
        const int eno = errno;
        OPENVPN_THROW(buf_read_error, "on " << title << " : " << strerror_str(eno));
    }
    else if (!status)
        return false;
    buf.inc_size(status);
    return true;
}

inline BufferList buf_read(const int fd, const std::string &title)
{
    BufferList buflist;
    while (true)
    {
        BufferAllocated buf(1024);
        if (!buf_read(fd, buf, title))
            break;
        buflist.put_consume(buf);
    }
    return buflist;
}
} // namespace openvpn

#endif
