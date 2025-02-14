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

// A null compression class.

#ifndef OPENVPN_COMPRESS_COMPNULL_H
#define OPENVPN_COMPRESS_COMPNULL_H

namespace openvpn {

class CompressNull : public Compress
{
  public:
    CompressNull(const Frame::Ptr &frame, const SessionStats::Ptr &stats)
        : Compress(frame, stats)
    {
    }

    const char *name() const override
    {
        return "null";
    }
    void compress(BufferAllocated &buf, const bool hint) override
    {
    }
    void decompress(BufferAllocated &buf) override
    {
    }
};

} // namespace openvpn

#endif // OPENVPN_COMPRESS_COMPNULL_H
