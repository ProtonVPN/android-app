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

// This is a "stub" compression object.  It acts like a compressor
// in the sense that it plays along with compression framing in
// the OpenVPN protocol, but it always sends packets with NO_COMPRESS
// or NO_COMPRESS_SWAP compression status.  While it's not designed
// to receive compressed packets, it will try to handle received LZO
// packets, but it will never send compressed packets.

#ifndef OPENVPN_COMPRESS_COMPSTUB_H
#define OPENVPN_COMPRESS_COMPSTUB_H

#ifndef NO_LZO
#include <openvpn/compress/lzoselect.hpp>
#endif

namespace openvpn {

class CompressStub : public Compress
{
  public:
    CompressStub(const Frame::Ptr &frame, const SessionStats::Ptr &stats, const bool support_swap_arg)
        : Compress(frame, stats),
          support_swap(support_swap_arg)
#ifndef NO_LZO
          ,
          lzo(frame, stats, false, true)
#endif
    {
        OVPN_LOG_INFO("Comp-stub init swap=" << support_swap_arg);
    }

    const char *name() const override
    {
        return "stub";
    }

    void compress(BufferAllocated &buf, const bool hint) override
    {
        // skip null packets
        if (!buf.size())
            return;

        // indicate that we didn't compress
        if (support_swap)
            do_swap(buf, NO_COMPRESS_SWAP);
        else
            buf.push_front(NO_COMPRESS);
    }

    void decompress(BufferAllocated &buf) override
    {
        // skip null packets
        if (!buf.size())
            return;

        const unsigned char c = buf.pop_front();
        switch (c)
        {
        case NO_COMPRESS_SWAP:
            do_unswap(buf);
        case NO_COMPRESS:
            break;
#ifndef NO_LZO
        // special mode to support older servers that ignore
        // compression handshake -- this will handle receiving
        // compressed packets even if we didn't ask for them
        case CompressLZO::LZO_COMPRESS:
            OVPN_LOG_VERBOSE("CompressStub: handled unsolicited LZO packet");
            lzo.decompress_work(buf);
            break;
#endif
        default:
            OVPN_LOG_VERBOSE("CompressStub: unable to handle op=" << int(c));
            error(buf);
        }
    }

  private:
    const bool support_swap;
#ifndef NO_LZO
    CompressLZO lzo;
#endif
};

// Compression stub using V2 protocol
class CompressStubV2 : public Compress
{
  public:
    CompressStubV2(const Frame::Ptr &frame, const SessionStats::Ptr &stats)
        : Compress(frame, stats)
    {
        OVPN_LOG_INFO("Comp-stubV2 init");
    }

    const char *name() const override
    {
        return "stubv2";
    }

    void compress(BufferAllocated &buf, const bool hint) override
    {
        // skip null packets
        if (!buf.size())
            return;

        // indicate that we didn't compress
        v2_push(buf, OVPN_COMPv2_NONE);
    }

    void decompress(BufferAllocated &buf) override
    {
        // skip null packets
        if (!buf.size())
            return;

        const int cop = v2_pull(buf);
        if (cop)
        {
            OVPN_LOG_VERBOSE("CompressStubV2: unable to handle op=" << cop);
            error(buf);
        }
    }
};

} // namespace openvpn

#endif // OPENVPN_COMPRESS_COMPSTUB_H
