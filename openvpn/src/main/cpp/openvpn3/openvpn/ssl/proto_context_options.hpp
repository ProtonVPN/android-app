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

// Helper class to parse certain options needed by ProtoContext.

#ifndef OPENVPN_SSL_PROTO_CONTEXT_OPTIONS_H
#define OPENVPN_SSL_PROTO_CONTEXT_OPTIONS_H

#include <string>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/options.hpp>

namespace openvpn {
struct ProtoContextOptions : public RC<thread_safe_refcount>
{
    typedef RCPtr<ProtoContextOptions> Ptr;

    enum CompressionMode
    {
        COMPRESS_NO,
        COMPRESS_YES,
        COMPRESS_ASYM
    };

    ProtoContextOptions()
        : compression_mode(COMPRESS_NO)
    {
    }

    bool is_comp() const
    {
        return compression_mode != COMPRESS_NO;
    }
    bool is_comp_asym() const
    {
        return compression_mode == COMPRESS_ASYM;
    }

    void parse_compression_mode(const std::string &mode)
    {
        if (mode == "no")
            compression_mode = COMPRESS_NO;
        else if (mode == "yes")
            compression_mode = COMPRESS_YES;
        else if (mode == "asym")
            compression_mode = COMPRESS_ASYM;
        else
            OPENVPN_THROW(option_error, "error parsing compression mode: " << mode);
    }

    CompressionMode compression_mode;
};
} // namespace openvpn

#endif
