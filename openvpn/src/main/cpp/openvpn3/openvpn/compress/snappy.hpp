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

#ifndef OPENVPN_COMPRESS_SNAPPY_H
#define OPENVPN_COMPRESS_SNAPPY_H

// Implement Snappy compression.
// Should only be included by compress.hpp

#include <snappy.h>

namespace openvpn {

class CompressSnappy : public Compress
{
    // magic number for Snappy compression
    enum
    {
        SNAPPY_COMPRESS = 0x68,
    };

  public:
    CompressSnappy(const Frame::Ptr &frame, const SessionStats::Ptr &stats, const bool asym_arg)
        : Compress(frame, stats),
          asym(asym_arg)
    {
        OVPN_LOG_INFO("SNAPPY init asym=" << asym_arg);
    }

    const char *name() const override
    {
        return "snappy";
    }

    void compress(BufferAllocated &buf, const bool hint) override
    {
        // skip null packets
        if (!buf.size())
            return;

        if (hint && !asym)
        {
            // initialize work buffer
            frame->prepare(Frame::COMPRESS_WORK, work);

            // verify that input data length is not too large
            if (snappy::MaxCompressedLength(buf.size()) > work.max_size())
            {
                error(buf);
                return;
            }

            // do compress
            size_t comp_size;
            snappy::RawCompress((const char *)buf.c_data(), buf.size(), (char *)work.data(), &comp_size);

            // did compression actually reduce data length?
            if (comp_size < buf.size())
            {
                OVPN_LOG_VERBOSE("SNAPPY compress " << buf.size() << " -> " << comp_size);
                work.set_size(comp_size);
                do_swap(work, SNAPPY_COMPRESS);
                buf.swap(work);
                return;
            }
        }

        // indicate that we didn't compress
        do_swap(buf, NO_COMPRESS_SWAP);
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
            break;
        case SNAPPY_COMPRESS:
            {
                do_unswap(buf);

                // initialize work buffer
                const size_t payload_size = frame->prepare(Frame::DECOMPRESS_WORK, work);

                // do uncompress
                size_t decomp_size;
                if (!snappy::GetUncompressedLength((const char *)buf.c_data(), buf.size(), &decomp_size)
                    || decomp_size > payload_size)
                {
                    error(buf);
                    break;
                }
                if (!snappy::RawUncompress((const char *)buf.c_data(), buf.size(), (char *)work.data()))
                {
                    error(buf);
                    break;
                }
                OVPN_LOG_VERBOSE("SNAPPY uncompress " << buf.size() << " -> " << decomp_size);
                work.set_size(decomp_size);
                buf.swap(work);
            }
            break;
        default:
            error(buf); // unknown op
            break;
        }
    }

  private:
    const bool asym;
    BufferAllocated work;
};

} // namespace openvpn

#endif // OPENVPN_COMPRESS_SNAPPY_H
