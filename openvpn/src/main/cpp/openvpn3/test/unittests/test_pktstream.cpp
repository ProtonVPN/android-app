//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2021 OpenVPN Inc.
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

#include "test_common.h"

#include <openvpn/random/mtrandapi.hpp>
#include <openvpn/transport/pktstream.hpp>

using namespace openvpn;

// Return a random value in the range [1,512] but with
// the subrange [1, 16] having elevated probability.
static size_t rand_size(RandomAPI& prng)
{
  if (prng.randbool())
    return prng.randrange32(1, 16);
  else
    return prng.randrange32(1, 512);
}

TEST(pktstream, test_1)
{
#ifdef HAVE_VALGRIND
  const int n_iter = 1000;
#else
  const int n_iter = 1000000;
#endif

  const Frame::Context fc(256, 512, 256, 0, sizeof(size_t), 0);
  const Frame::Context fc_big(256, 4096, 256, 0, sizeof(size_t), 0);

  OPENVPN_LOG("FC " << fc.info());
  OPENVPN_LOG("FC BIG " << fc_big.info());

  MTRand::Ptr prng(new MTRand());

  size_t count = 0;

  for (int iter = 0; iter < n_iter; ++iter)
    {
      // build big
      BufferAllocated big;
      fc_big.prepare(big);
      size_t nbig = 0;

      {
	BufferAllocated src;
	while (true)
	  {
	    fc.prepare(src);
	    const size_t r = rand_size(*prng);
	    for (size_t i = 0; i < r; ++i)
	      src.push_back('a' + (i % 26));
	    PacketStream::prepend_size(src);
	    if (src.size() > fc_big.remaining_payload(big))
	      break;
	    big.write(src.data(), src.size());
	    ++nbig;
	  }
      }

      // save big
      const Buffer bigorig(big);

      // deconstruct big
      BufferAllocated bigcmp;
      fc_big.prepare(bigcmp);
      size_t ncmp = 0;

      {
	PacketStream pktstream;
	BufferAllocated in;
	while (big.size())
	  {
	    const size_t bytes = std::min(big.size(), rand_size(*prng));
	    fc.prepare(in);
	    in.write(big.data(), bytes);
	    big.advance(bytes);
	    BufferAllocated out;
	    while (in.size())
	      {
		pktstream.put(in, fc);
		if (pktstream.ready())
		  {
		    pktstream.get(out);
		    PacketStream::prepend_size(out);
		    bigcmp.write(out.data(), out.size());
		    ++ncmp;
		  }
	      }
	  }
      }

      // sum byte count
      count += bigorig.size();

      // check result
      ASSERT_EQ(nbig, ncmp);
      ASSERT_EQ(bigorig, bigcmp);
    }

  OPENVPN_LOG("count=" << count);
}

static void validate_size(const Frame::Context& fc, const size_t size, const bool expect_throw)
{
  bool actual_throw = false;
  try {
    PacketStream::validate_size(size, fc);
  }
  catch (PacketStream::embedded_packet_size_error&)
    {
      actual_throw = true;
    }
  if (expect_throw != actual_throw)
    THROW_FMT("validate_size: bad throw, expect=%s, actual=%s, FC=%s size=%s",
	      expect_throw,
	      actual_throw,
	      fc.info(),
	      size);
}

TEST(pktstream, test_2)
{
  const size_t payload = 2048;
  const size_t headroom = 16;
  const size_t tailroom = 0;
  const size_t align_block = 16;
  const Frame::Context fixed(headroom, payload, tailroom, 0, align_block, 0);
  const Frame::Context grow(headroom, payload, tailroom, 0, align_block, BufferAllocated::GROW);
  validate_size(fixed, 2048, false); // succeeds
  validate_size(fixed, 2049, true);  // exceeded payload, throw
  validate_size(grow, 2048, false);  // succeeds
  validate_size(grow, 2049, false);  // exceeded payload, but okay with growable buffer
}
