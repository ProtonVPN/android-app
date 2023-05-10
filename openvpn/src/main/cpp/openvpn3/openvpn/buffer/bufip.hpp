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

// Fast formatting of IP addresses to a Buffer object.
// Formatting should be indistinguishable from inet_ntop().

#pragma once

#include <openvpn/buffer/bufstr.hpp>
#include <openvpn/buffer/buffmt.hpp>
#include <openvpn/common/socktypes.hpp> // for ntohs

namespace openvpn {
namespace BufferFormat {

static inline void ipv4(Buffer &buf, const std::uint32_t addr) // addr is big-endian
{
    typedef BufferFormat::UnsignedDecimal<std::uint32_t> Decimal;

    Decimal::write(buf, addr & 0xff);
    buf.push_back('.');
    Decimal::write(buf, (addr >> 8) & 0xff);
    buf.push_back('.');
    Decimal::write(buf, (addr >> 16) & 0xff);
    buf.push_back('.');
    Decimal::write(buf, addr >> 24);
}

static inline void ipv6(Buffer &buf, const void *addr)
{
    typedef BufferFormat::Hex<std::uint16_t> Hex;

    // address the IPv6 address as an array of 8 hextets
    const std::uint16_t *a = static_cast<const std::uint16_t *>(addr);

    // first pass -- look for any extended zero hextet series
    size_t zero_start = 0;
    size_t zero_extent = 0;
    {
        bool zero = false;
        size_t start = 0;
        for (size_t i = 0; i < 8; ++i)
        {
            if (zero)
            {
                if (a[i])
                {
                    const size_t extent = i - start;
                    if (extent > zero_extent)
                    {
                        zero_start = start;
                        zero_extent = extent;
                    }
                    zero = false;
                }
            }
            else
            {
                if (!a[i])
                {
                    start = i;
                    zero = true;
                }
            }
        }

        // zero residual state?
        if (zero)
        {
            const size_t extent = 8 - start;
            if (extent > zero_extent)
            {
                zero_start = start;
                zero_extent = extent;
            }
        }
    }

    // special case for IPv4
    if (zero_start == 0)
    {
        if (zero_extent == 5 && a[5] == 0xffff)
        {
            buf_append_string(buf, "::ffff:");
            ipv4(buf, *reinterpret_cast<const std::uint32_t *>(a + 6));
            return;
        }
        else if (zero_extent == 6)
        {
            buf_append_string(buf, "::");
            ipv4(buf, *reinterpret_cast<const std::uint32_t *>(a + 6));
            return;
        }
    }

    // second pass -- now write the hextets
    {
        enum State
        {
            INITIAL,
            ZERO,
            NORMAL,
        };

        State state = INITIAL;
        for (size_t i = 0; i < 8; ++i)
        {
            const std::uint16_t hextet = ntohs(a[i]);
            if (i == zero_start && zero_extent >= 2)
                state = ZERO;
            switch (state)
            {
            case INITIAL:
                Hex::write(buf, hextet);
                state = NORMAL;
                break;
            case ZERO:
                if (!hextet)
                    break;
                buf.push_back(':');
                state = NORMAL;
                // fallthrough
            case NORMAL:
                buf.push_back(':');
                Hex::write(buf, hextet);
                break;
            }
        }

        // process residual state
        if (state == ZERO)
            buf_append_string(buf, "::");
    }
}
} // namespace BufferFormat
} // namespace openvpn
