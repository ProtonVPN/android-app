//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
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

#include <iostream>
#include <algorithm>         // for std::min

#ifndef BLOCK_SIZE
#define BLOCK_SIZE 1500
#endif

#ifndef N_COMPRESS
#define N_COMPRESS 1
#endif

#ifndef N_EXPAND
#define N_EXPAND 1
#endif

#ifndef SUPPORT_SWAP
#define SUPPORT_SWAP false
#endif

// other defines:
//   must define TEST_x to define compressor/decompressor pair
//   OPENVPN_DEBUG_COMPRESS = 0|1|2

#include "test_common.h"

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/file.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/compress/compress.hpp>
#include <openvpn/compress/lzoasym.hpp>
#include <openvpn/frame/frame.hpp>

using namespace openvpn;

Frame::Ptr frame_init(const size_t payload)
{
  const size_t headroom = 512;
  const size_t tailroom = 512;
  const size_t align_block = 16;
  const unsigned int buffer_flags = 0;

  Frame::Ptr frame(new Frame(Frame::Context(headroom, payload, tailroom, 0, align_block, buffer_flags)));
  frame->standardize_capacity(~0);
  return frame;
}

class MySessionStats : public SessionStats
{
public:
  typedef RCPtr<MySessionStats> Ptr;

  MySessionStats()
  {
    std::memset(errors, 0, sizeof(errors));
  }

  virtual void error(const size_t err_type, const std::string* text=NULL)
  {
    if (err_type < Error::N_ERRORS)
      ++errors[err_type];
  }

  count_t get_error_count(const Error::Type type) const
  {
    if (type < Error::N_ERRORS)
      return errors[type];
    else
      return 0;
  }

private:
  count_t errors[Error::N_ERRORS];
};

inline void verify_eq(const Buffer& b1, const Buffer& b2)
{
   ASSERT_EQ(b1, b2) << "decompressed data doesn't match original data";
}

void test(const std::string& filename,
	  Compress& compressor,
	  Compress& decompressor,
	  const Frame& frame,
	  const size_t block_size,
	  const size_t n_compress,
	  const size_t n_expand_per_compress,
	  size_t& bytes,
	  size_t& compress_bytes)
{
  BufferPtr source_data = read_binary(filename);
  for (size_t offset = 0; offset < source_data->size(); offset += block_size)
    {
      const size_t length = std::min(block_size, source_data->size() - offset);
      BufferAllocated data_seg;
      frame.prepare(Frame::DECRYPT_WORK, data_seg);
      data_seg.write(source_data->data_raw() + offset, length);
      for (size_t compress_iter = 0; compress_iter < n_compress; ++compress_iter)
	{
	  BufferAllocated data1(data_seg);
	  bytes += data1.size();
	  compressor.compress(data1, true);
	  compress_bytes += data1.size();
	  if (n_expand_per_compress == 1)
	    {
	      decompressor.decompress(data1);
	      verify_eq(data_seg, data1);
	    }
	  else
	    {
	      for (size_t decompress_iter = 0; decompress_iter < n_expand_per_compress; ++decompress_iter)
		{
		  BufferAllocated data2(data1);
		  decompressor.decompress(data2);
		  verify_eq(data_seg, data2);
		}
	    }
	}
    }
}

void test_with_corpus(Compress& compressor,
		      Compress& decompressor,
		      const Frame& frame,
		      const size_t block_size,
		      const size_t n_compress,
		      const size_t n_expand_per_compress,
		      size_t& bytes,
		      size_t& compress_bytes)
{
  static const std::vector<std::string> filenames = {
    "comp-testdata/alice29.txt",
    "comp-testdata/asyoulik.txt",
    "comp-testdata/cp.html",
    "comp-testdata/fields.c",
    "comp-testdata/geo.protodata",
    "comp-testdata/grammar.lsp",
    "comp-testdata/house.jpg",
    "comp-testdata/html",
    "comp-testdata/html_x_4",
    "comp-testdata/kennedy.xls",
    "comp-testdata/kppkn.gtb",
    "comp-testdata/lcet10.txt",
    "comp-testdata/mapreduce-osdi-1.pdf",
    "comp-testdata/plrabn12.txt",
    "comp-testdata/ptt5",
    "comp-testdata/sum",
    "comp-testdata/urls.10K",
    "comp-testdata/xargs.1",
   };
  for (auto fn: filenames) {
      test(std::string(UNITTEST_SOURCE_DIR) + '/' + fn, compressor, decompressor, frame, block_size, n_compress, n_expand_per_compress, bytes,
	      compress_bytes);
  }
}

enum class comppair {
    lzo,
    lzoasym,
    snappy,
    lz4
};

void runTest(comppair alg, bool verbose=false)
{
    CompressContext::init_static();
    MySessionStats::Ptr stats(new MySessionStats);
    Frame::Ptr frame = frame_init(BLOCK_SIZE);


    Compress::Ptr compress;
    Compress::Ptr decompress;

    switch (alg) {
#if defined(HAVE_LZO)
    case comppair::lzoasym:
	compress.reset(new CompressLZO(frame, stats, SUPPORT_SWAP, false));
	decompress.reset(new CompressLZOAsym(frame, stats, SUPPORT_SWAP, false));
	break;

    case comppair::lzo:
	compress.reset(new CompressLZO(frame, stats, SUPPORT_SWAP, false));
	decompress = compress;
	break;

#endif
#if defined(HAVE_LZ4)
    case comppair::lz4:
	compress.reset(new CompressLZ4(frame, stats, false));
	decompress = compress;
	break;
#endif
#if defined(HAVE_SNAPPY)
    case comppair::snappy:
	compress.reset(new CompressSnappy(frame, stats, false));
	decompress = compress;
	break;
#endif
    default:
	ASSERT_TRUE(false) << "compressor/decompressor pair not supported";
    }
    size_t bytes = 0;
    size_t compress_bytes = 0;
    test_with_corpus(*compress, *decompress, *frame, BLOCK_SIZE, N_COMPRESS, N_EXPAND, bytes, compress_bytes);

    if (verbose)
	std::cout << "comp=" << compress->name() << '[' << N_COMPRESS << ']'
		  << " decomp=" << decompress->name() << '[' << N_EXPAND << ']'
		  << " blk=" << BLOCK_SIZE
		  << " bytes=" << bytes
		  << " comp-bytes=" << compress_bytes
		  << " comp-ratio=" << (float) compress_bytes / bytes
		  << std::endl;
}

namespace unittests
{
#if defined(HAVE_SNAPPY)
    TEST(Compression, snappy)
    {
        runTest(comppair::snappy);
    }
#endif
#if defined(HAVE_LZO)
    TEST(Compression, lzo)
    {
        runTest(comppair::lzo);
    }

    TEST(Compression, lzoasym)
    {
        runTest(comppair::lzoasym);
    }
#endif
#if defined(HAVE_LZ4)
    TEST(Compression, lz4)
    {
	runTest(comppair::lz4);
    }
#endif
}
