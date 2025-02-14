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

// Helper class to parse certain options needed by ProtoContext.

#ifndef OPENVPN_SSL_PROTO_CONTEXT_OPTIONS_H
#define OPENVPN_SSL_PROTO_CONTEXT_OPTIONS_H

#include <string>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/options.hpp>

namespace openvpn {
struct ProtoContextCompressionOptions : public RC<thread_safe_refcount>
{
    typedef RCPtr<ProtoContextCompressionOptions> Ptr;

    enum CompressionMode
    {
        COMPRESS_NO,
        COMPRESS_YES,
        COMPRESS_ASYM
    };

    ProtoContextCompressionOptions()
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
            OPENVPN_THROW_ARG1(option_error, ERR_INVALID_OPTION_VAL, "error parsing compression mode: " << mode);
    }

    CompressionMode compression_mode;
};
} // namespace openvpn

#endif
