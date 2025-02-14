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

#ifndef OPENVPN_BUFFER_BUFHEX_H
#define OPENVPN_BUFFER_BUFHEX_H

#include <openvpn/common/hexstr.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/buffer/buffer.hpp>

namespace openvpn::BufHex {

OPENVPN_EXCEPTION(buf_hex);

template <typename T>
inline std::string render(const T obj)
{
    const ConstBuffer buf((const unsigned char *)&obj, sizeof(obj), true);
    return render_hex_generic(buf);
}

template <typename T>
inline T parse(const std::string &hex, const std::string &title)
{
    T obj;
    Buffer buf((unsigned char *)&obj, sizeof(obj), false);
    try
    {
        parse_hex(buf, hex);
    }
    catch (const BufferException &e)
    {
        OPENVPN_THROW(buf_hex, title << ": buffer issue: " << e.what());
    }
    if (buf.size() != sizeof(obj))
        OPENVPN_THROW(buf_hex, title << ": unexpected size");
    return obj;
}

} // namespace openvpn::BufHex

#endif
