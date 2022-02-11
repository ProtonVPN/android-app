//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2019 OpenVPN Inc.
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

//#define OPENVPN_BUFFER_ABORT

#include <algorithm>

#include "test_common.h"

#include <openvpn/common/options.hpp>
#include <openvpn/random/mtrandapi.hpp>

#include <openvpn/options/continuation_fragment.hpp>
#include <openvpn/options/continuation.hpp>

using namespace openvpn;

static void require_equal(const OptionList& opt1, const OptionList& opt2, const std::string& title)
{
  if (opt1 != opt2)
    {
      OPENVPN_LOG(title);
      ASSERT_EQ(opt1.render(Option::RENDER_BRACKET), opt2.render(Option::RENDER_BRACKET));
    }
}

static void require_equal(const Buffer& buf1, const Buffer& buf2, const std::string& title)
{
  if (buf1 != buf2)
    {
      OPENVPN_LOG(title);
      ASSERT_EQ(buf_to_string(buf1), buf_to_string(buf2));
    }
}

// push continuation mode
enum PCMode {
  NO_PC,
  PC_1,
  PC_2,
};

static std::string get_csv(Buffer buf, const PCMode pc_mode)
{
  // verify PUSH_REPLY then remove it
  if (!string::starts_with(buf, "PUSH_REPLY,"))
    throw Exception("expected that buffer would begin with PUSH_REPLY");
  buf.advance(11);

  // possibly remove push-continuation options from tail of buffer
  if (pc_mode == PC_1)
    {
      if (!string::ends_with(buf, ",push-continuation 1"))
	throw Exception("expected that buffer would end with push-continuation 1");
      buf.set_size(buf.size() - 20);
    }
  else if (pc_mode == PC_2)
    {
      if (!string::ends_with(buf, ",push-continuation 2"))
	throw Exception("expected that buffer would end with push-continuation 2");
      buf.set_size(buf.size() - 20);
    }

  return buf_to_string(buf);
}

static std::string get_csv_from_frag(Buffer buf, const size_t index, const size_t size)
{
  if (size < 2)
    return get_csv(buf, NO_PC);
  else if (index == size - 1)
    return get_csv(buf, PC_1);
  else
    return get_csv(buf, PC_2);
}

static std::string random_term(RandomAPI& prng)
{
  static const std::string rchrs = "012abcABC,\"\\";

  std::string ret;
  const int len = prng.randrange32(1, 16);
  ret.reserve(len);
  for (int i = 0; i < len; ++i)
    ret += rchrs[prng.randrange32(rchrs.size())];
  return ret;
}

static Option random_opt(RandomAPI& prng)
{
  Option ret;
  const int len = prng.randrange32(1, 4);
  ret.reserve(len);
  for (int i = 0; i < len; ++i)
    ret.push_back(random_term(prng));
  return ret;
}

static OptionList random_optionlist(RandomAPI& prng)
{
  static const int sizes[3] = {10, 100, 1000};

  OptionList ret;
  const int len = prng.randrange32(1, sizes[prng.randrange32(3)]);
  ret.reserve(len);
  for (int i = 0; i < len; ++i)
    ret.push_back(random_opt(prng));
  return ret;
}

static void test_roundtrip(const OptionList& opt_orig)
{
  // first render to CSV
  BufferAllocated buf(opt_orig.size() * 128, BufferAllocated::GROW);
  buf_append_string(buf, "PUSH_REPLY,");
  buf_append_string(buf, opt_orig.render_csv());

  // parse back to OptionList and verify round trip
  const OptionList opt = OptionList::parse_from_csv_static_nomap(get_csv(buf, NO_PC), nullptr);
  require_equal(opt_orig, opt, "TEST_ROUNDTRIP #1");

  // fragment into multiple buffers using push-continuation
  const PushContinuationFragment frag(buf);

  // parse fragments separately and verify with original
  OptionList new_opt;
  for (size_t i = 0; i < frag.size(); ++i)
    new_opt.parse_from_csv(get_csv_from_frag(*frag[i], i, frag.size()), nullptr);
  require_equal(opt_orig, new_opt, "TEST_ROUNDTRIP #2");

  // test client-side continuation parser
  OptionListContinuation cc;
  for (size_t i = 0; i < frag.size(); ++i)
    {
      const OptionList cli_opt = OptionList::parse_from_csv_static(get_csv(*frag[i], NO_PC), nullptr);
      cc.add(cli_opt, nullptr);
      ASSERT_TRUE(cc.partial());
      ASSERT_EQ(cc.complete(), i == frag.size() - 1);
    }

  // remove client-side push-continuation directives before comparison
  cc.erase(std::remove_if(cc.begin(), cc.end(),
			  [](const Option& o)
			  {
			    return o.size() >= 1 && o.ref(0) == "push-continuation";
			  }),
	   cc.end());
  require_equal(opt_orig, cc, "TEST_ROUNDTRIP #3");

  // defragment back to original form
  BufferPtr defrag = PushContinuationFragment::defragment(frag);
  require_equal(buf, *defrag, "TEST_ROUNDTRIP #4");
}

// test roundtrip for random configurations
TEST(continuation, test1)
{
  RandomAPI::Ptr prng(new MTRand);

  // Note: this code runs ~100x slower with valgrind
  const int n = 100;

  for (int i = 0; i < n; ++i)
    {
      const OptionList opt = random_optionlist(*prng);
      test_roundtrip(opt);
    }
}

// test maximum fragment sizes and optionally generate
// push-list for further testing
TEST(continuation, test2)
{
  BufferAllocated buf(65536, BufferAllocated::GROW);
  buf_append_string(buf, "PUSH_REPLY,route-gateway 10.213.0.1,ifconfig 10.213.0.48 255.255.0.0,ifconfig-ipv6 fdab::48/64 fdab::1,client-ip 192.168.4.1,ping 1,ping-restart 8,reneg-sec 60,cipher AES-128-GCM,compress stub-v2,peer-id 4,topology subnet,explicit-exit-notify");

  // pack the buffers, so several reach the maximum
  // fragment size of PushContinuationFragment::FRAGMENT_SIZE
  for (int i = 0; i < 1000; ++i)
    {
      if (i % 100 == 0)
	buf_append_string(buf, ",echo rogue-agent-neptune-" + std::to_string(i/100));
      buf_append_string(buf, ",echo test-" + std::to_string(i));
    }

  // fragment into multiple buffers using push-continuation
  const PushContinuationFragment frag(buf);
  int count = 0;
  for (auto &e : frag)
    {
     //OPENVPN_LOG(e->size());
     if (e->size() == PushContinuationFragment::FRAGMENT_SIZE)
       ++count;
    }

  // 8 buffers should have reached maximum size
  ASSERT_EQ(count, 8);

  // defragment the buffer
  BufferPtr defrag = PushContinuationFragment::defragment(frag);
  const OptionList opt = OptionList::parse_from_csv_static_nomap(get_csv(*defrag, NO_PC), nullptr);

#if 0
  // dump for inclusion in JSON push list
  for (const auto &e : opt)
    {
      OPENVPN_LOG("    \"" << e.render(0) << "\",");
    }
#endif
}
