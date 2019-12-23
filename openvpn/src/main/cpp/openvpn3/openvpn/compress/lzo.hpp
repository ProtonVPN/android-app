//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2017 OpenVPN Inc.
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

#ifndef OPENVPN_COMPRESS_LZO_H
#define OPENVPN_COMPRESS_LZO_H

// Implement LZO compression.
// Should only be included by lzoselect.hpp

#include "lzo/lzoutil.h"
#include "lzo/lzo1x.h"

namespace openvpn {

  class CompressLZO : public Compress
  {
  public:
    // magic number for LZO compression
    enum {
      LZO_COMPRESS = 0x66,
      LZO_COMPRESS_SWAP = 0x67,
    };

    OPENVPN_SIMPLE_EXCEPTION(lzo_init_failed);

    CompressLZO(const Frame::Ptr& frame,
		const SessionStats::Ptr& stats,
		const bool support_swap_arg,
		const bool asym_arg)
      : Compress(frame, stats),
	support_swap(support_swap_arg),
	asym(asym_arg)
    {
      OPENVPN_LOG_COMPRESS("LZO init swap=" << support_swap_arg << " asym=" << asym_arg);
      lzo_workspace.init(LZO1X_1_15_MEM_COMPRESS, BufferAllocated::ARRAY);
    }

    static void init_static()
    {
      if (::lzo_init() != LZO_E_OK)
	throw lzo_init_failed();
    }

    virtual const char *name() const { return "lzo"; }

    void decompress_work(BufferAllocated& buf)
    {
      // initialize work buffer
      lzo_uint zlen = frame->prepare(Frame::DECOMPRESS_WORK, work);

      // do uncompress
      const int err = lzo1x_decompress_safe(buf.c_data(), buf.size(), work.data(), &zlen, lzo_workspace.data());
      if (err != LZO_E_OK)
	{
	  error(buf);
	  return;
	}
      OPENVPN_LOG_COMPRESS_VERBOSE("LZO uncompress " << buf.size() << " -> " << zlen);
      work.set_size(zlen);
      buf.swap(work);
    }

    virtual void compress(BufferAllocated& buf, const bool hint)
    {
      // skip null packets
      if (!buf.size())
	return;

      if (hint && !asym)
	{
	  // initialize work buffer
	  frame->prepare(Frame::COMPRESS_WORK, work);

	  // verify that input data length is not too large
	  if (lzo_extra_buffer(buf.size()) > work.max_size())
	    {
	      error(buf);
	      return;
	    }

	  // do compress
	  lzo_uint zlen = 0;
	  const int err = ::lzo1x_1_15_compress(buf.c_data(), buf.size(), work.data(), &zlen, lzo_workspace.data());

	  // check for errors
	  if (err != LZO_E_OK)
	    {
	      error(buf);
	      return;
	    }

	  // did compression actually reduce data length?
	  if (zlen < buf.size())
	    {
	      OPENVPN_LOG_COMPRESS_VERBOSE("LZO compress " << buf.size() << " -> " << zlen);
	      work.set_size(zlen);
	      if (support_swap)
		do_swap(work, LZO_COMPRESS_SWAP);
	      else
		work.push_front(LZO_COMPRESS);
	      buf.swap(work);
	      return;
	    }
	}

      // indicate that we didn't compress
      if (support_swap)
	do_swap(buf, NO_COMPRESS_SWAP);
      else
	buf.push_front(NO_COMPRESS);
    }

    virtual void decompress(BufferAllocated& buf)
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
	case LZO_COMPRESS_SWAP:
	  do_unswap(buf);
	case LZO_COMPRESS:
	  decompress_work(buf);
	  break;
	default: 
	  error(buf); // unknown op
	}
    }

  private:
    // worst case size expansion on compress
    size_t lzo_extra_buffer(const size_t len)
    {
      return len + len/8 + 128 + 3;
    }

    const bool support_swap;
    const bool asym;
    BufferAllocated work;
    BufferAllocated lzo_workspace;
  };

} // namespace openvpn

#endif // OPENVPN_COMPRESS_LZO_H
