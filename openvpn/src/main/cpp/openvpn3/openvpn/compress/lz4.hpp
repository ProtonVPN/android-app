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

#ifndef OPENVPN_COMPRESS_LZ4_H
#define OPENVPN_COMPRESS_LZ4_H

// Implement LZ4 compression.
// Should only be included by compress.hpp

#include <algorithm> // for std::max

#include <openvpn/common/numeric_util.hpp>

#include <lz4.h>

namespace openvpn {

class CompressLZ4Base : public Compress
{
  protected:
    CompressLZ4Base(const Frame::Ptr &frame, const SessionStats::Ptr &stats)
        : Compress(frame, stats)
    {
    }

    bool do_decompress(BufferAllocated &buf)
    {
        auto prepRes = frame->prepare(Frame::DECOMPRESS_WORK, work);
        if (!numeric_util::is_safe_conversion<const int>(prepRes))
            return false;

        // initialize work buffer
        auto payload_size = static_cast<const int>(prepRes);

        // do uncompress
        const int decomp_size = LZ4_decompress_safe((const char *)buf.c_data(),
                                                    (char *)work.data(),
                                                    (int)buf.size(),
                                                    payload_size);
        if (decomp_size < 0)
        {
            error(buf);
            return false;
        }
        OVPN_LOG_VERBOSE("LZ4 uncompress " << buf.size() << " -> " << decomp_size);
        work.set_size(decomp_size);
        buf.swap(work);
        return true;
    }

    bool do_compress(BufferAllocated &buf)
    {
        // initialize work buffer
        frame->prepare(Frame::COMPRESS_WORK, work);

        // verify that input data length is not too large
        if (lz4_extra_buffer(buf.size()) > work.max_size())
        {
            error(buf);
            return false;
        }

        // do compress
        const unsigned int comp_size = LZ4_compress_default((const char *)buf.c_data(),
                                                            (char *)work.data(),
                                                            (int)buf.size(),
                                                            (int)work.capacity());

        // did compression actually reduce data length?
        if (comp_size < buf.size())
        {
            if (comp_size <= 0)
            {
                error(buf);
                return false;
            }
            OVPN_LOG_VERBOSE("LZ4 compress " << buf.size() << " -> " << comp_size);
            work.set_size(comp_size);
            buf.swap(work);
            return true;
        }
        else
            return false;
    }

    // Worst case size expansion on compress.
    // Official LZ4 worst-case size expansion alg is
    // LZ4_COMPRESSBOUND macro in lz4.h.
    // However we optimize it slightly here to lose the integer division
    // when len < 65535.
    size_t lz4_extra_buffer(const size_t len)
    {
        if (likely(len < 65535))
            return len + len / 256 + 17;
        else
            return len + len / 255 + 16;
    }

    BufferAllocated work;
};

class CompressLZ4 : public CompressLZ4Base
{
    // magic number for LZ4 compression
    enum
    {
        LZ4_COMPRESS = 0x69,
    };

  public:
    CompressLZ4(const Frame::Ptr &frame, const SessionStats::Ptr &stats, const bool asym_arg)
        : CompressLZ4Base(frame, stats),
          asym(asym_arg)
    {
        OVPN_LOG_INFO("LZ4 init asym=" << asym_arg);
    }

    const char *name() const override
    {
        return "lz4";
    }

    void compress(BufferAllocated &buf, const bool hint) override
    {
        // skip null packets
        if (!buf.size())
            return;

        if (hint && !asym)
        {
            if (do_compress(buf))
            {
                do_swap(buf, LZ4_COMPRESS);
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
        case LZ4_COMPRESS:
            do_unswap(buf);
            do_decompress(buf);
            break;
        default:
            error(buf); // unknown op
        }
    }

  private:
    const bool asym;
};

class CompressLZ4v2 : public CompressLZ4Base
{
  public:
    CompressLZ4v2(const Frame::Ptr &frame, const SessionStats::Ptr &stats, const bool asym_arg)
        : CompressLZ4Base(frame, stats),
          asym(asym_arg)
    {
        OVPN_LOG_INFO("LZ4v2 init asym=" << asym_arg);
    }

    const char *name() const override
    {
        return "lz4v2";
    }

    void compress(BufferAllocated &buf, const bool hint) override
    {
        // skip null packets
        if (!buf.size())
            return;

        if (hint && !asym)
        {
            if (do_compress(buf))
            {
                v2_push(buf, OVPN_COMPv2_LZ4);
                return;
            }
        }

        // indicate that we didn't compress
        v2_push(buf, OVPN_COMPv2_NONE);
    }

    void decompress(BufferAllocated &buf) override
    {
        // skip null packets
        if (!buf.size())
            return;

        const int c = v2_pull(buf);
        switch (c)
        {
        case OVPN_COMPv2_NONE:
            break;
        case OVPN_COMPv2_LZ4:
            do_decompress(buf);
            break;
        default:
            error(buf); // unknown op
        }
    }

  private:
    const bool asym;
};

} // namespace openvpn

#endif
