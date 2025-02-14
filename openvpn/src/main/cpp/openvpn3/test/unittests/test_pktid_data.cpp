#include "test_common.hpp"

#include <openvpn/crypto/packet_id_control.hpp>
#include <openvpn/crypto/packet_id_data.hpp>

using namespace openvpn;

struct PacketIDDataConstruct : public PacketIDData
{
    explicit PacketIDDataConstruct(const PacketIDData::data_id_t v_id = PacketIDData::data_id_t{0}, bool wide = false)
        : PacketIDData(wide)
    {
        id = v_id;
    }
};

template <typename PIDRecv>
void testcase(PIDRecv &pr,
              const std::time_t t,
              const PacketIDData::data_id_t pkt_id,
              const Error::Type expected_status)
{
    bool wide = pr.length() > 4;
    const PacketIDDataConstruct pid(pkt_id, wide);
    const Error::Type status = pr.do_test_add(pid, t);
    // OPENVPN_LOG("[" << t << "] id=" << pkt_id << ' ' << Error::name(status));
    ASSERT_EQ(status, expected_status);
}

void do_packet_id_recv_test_short_ids(bool usewide)
{
    typedef PacketIDDataReceiveType<3, 5> PIDRecv;
    SessionStats::Ptr stats(new SessionStats());
    PIDRecv pr;
    pr.init("test", 0, usewide);

    testcase(pr, 0, 0, Error::PKTID_INVALID);
    testcase(pr, 1, 1, Error::SUCCESS);
    testcase(pr, 1, 1, Error::PKTID_REPLAY); /* replay */
    testcase(pr, 2, 2, Error::SUCCESS);
    testcase(pr, 3, 4, Error::SUCCESS);
    testcase(pr, 4, 1, Error::PKTID_REPLAY); /* replay */
    testcase(pr, 5, 3, Error::SUCCESS);

    testcase(pr, 6, 8, Error::SUCCESS);
    testcase(pr, 10, 5, Error::SUCCESS);
    testcase(pr, 15, 7, Error::PKTID_EXPIRE); /* expire backtrack */

    testcase(pr, 20, 127, Error::SUCCESS);
    testcase(pr, 20, 127, Error::PKTID_REPLAY);
    testcase(pr, 21, 128, Error::SUCCESS);
    testcase(pr, 22, 64, Error::PKTID_BACKTRACK); /* large backtrack */
    testcase(pr, 23, 65, Error::SUCCESS);
    testcase(pr, 24, 66, Error::SUCCESS);

    testcase(pr, 30, 0, Error::PKTID_INVALID);
    testcase(pr, 33, 3, Error::PKTID_BACKTRACK); /* time backtrack */

    testcase(pr, 40, 0xfffffffe, Error::SUCCESS);
    testcase(pr, 41, 0xffffffff, Error::SUCCESS);
}

TEST(misc, do_packet_id_recv_test_long_ids)
{
    typedef PacketIDDataReceiveType<3, 5> PIDRecv;
    PIDRecv pr;
    SessionStats::Ptr stats{new SessionStats()};
    pr.init("test", 0, true);

    testcase(pr, 40, 0xfffffffe, Error::SUCCESS);
    testcase(pr, 41, 0xffffffff, Error::SUCCESS);
    testcase(pr, 42, 0x100000001, Error::SUCCESS);
    testcase(pr, 42, 0xffffff0d, Error::PKTID_BACKTRACK);
    testcase(pr, 50, 0x200000000, Error::SUCCESS);
    testcase(pr, 50, 0x500000000, Error::SUCCESS);
    testcase(pr, 50, 0x400000000, Error::PKTID_BACKTRACK);
    testcase(pr, 50, 0x399999999, Error::PKTID_BACKTRACK);
    testcase(pr, 50, 0x3ffffffff, Error::PKTID_BACKTRACK);
    testcase(pr, 50, 0x4ffffffff, Error::SUCCESS);
}


TEST(misc, pktid_test_data_32bit)
{
    do_packet_id_recv_test_short_ids(false);
}

TEST(misc, pktid_test_data_64bit)
{
    do_packet_id_recv_test_short_ids(true);
}


template <unsigned int ORDER, unsigned int EXPIRE>
void perfiter(const long n,
              const long range,
              const long step,
              const long iter_per_step_pre,
              long &count)
{
    typedef PacketIDDataReceiveType<ORDER, EXPIRE> PIDRecv;

    const long iter_per_step = iter_per_step_pre * step;
    // OPENVPN_LOG("ITER order=" << ORDER << " n=" << n << " range=" << range << " step=" << step << " iter_per_step="
    //			    << iter_per_step);

    constexpr std::time_t pkt_time = 1234;

    MTRand urand;
    std::vector<bool> bv(n);
    long high = 0;
    SessionStats::Ptr stats(new SessionStats());
    PIDRecv pr;
    pr.init("test", 0, false);

    for (long i = 1; i < n; i += step)
    {
        for (long j = 0; j < iter_per_step; ++j)
        {
            const long delta = long(urand.randrange32(static_cast<uint32_t>(range))) - range / 2;
            const long id = i + delta;
            if (id >= 0 && id < n)
            {
                if (id > high)
                    high = id;
                Error::Type expected = Error::SUCCESS;
                if (!id)
                    expected = Error::PKTID_INVALID;
                else if (high - id >= (const long)PIDRecv::REPLAY_WINDOW_SIZE)
                    expected = Error::PKTID_BACKTRACK;
                else if (bv[id])
                    expected = Error::PKTID_REPLAY;
                const PacketIDDataConstruct pid(id);
                const Error::Type result = pr.do_test_add(pid, pkt_time);
                ++count;
#define INFO "i=" << i << " id=" << id << " high=" << high << " result=" << Error::name(result) << " expected=" << Error::name(expected)
                // OPENVPN_LOG(INFO);
                ASSERT_EQ(result, expected) << INFO;
                if (expected == Error::SUCCESS)
                    bv[id] = true;
            }
        }
    }
}

template <unsigned int ORDER, unsigned int EXPIRE>
void perf(long &count)
{
    typedef PacketIDDataReceiveType<ORDER, EXPIRE> PIDRecv;

    perfiter<ORDER, EXPIRE>(20000, PIDRecv::REPLAY_WINDOW_SIZE * 3, 1, 10, count);
    perfiter<ORDER, EXPIRE>(20000, PIDRecv::REPLAY_WINDOW_SIZE * 3, PIDRecv::REPLAY_WINDOW_SIZE / 2, 10, count);
    perfiter<ORDER, EXPIRE>(20000, PIDRecv::REPLAY_WINDOW_SIZE * 2, 1, 10, count);
    perfiter<ORDER, EXPIRE>(20000, PIDRecv::REPLAY_WINDOW_SIZE * 2, PIDRecv::REPLAY_WINDOW_SIZE / 2, 10, count);
    perfiter<ORDER, EXPIRE>(20000, 16, 1, 10, count);
    perfiter<ORDER, EXPIRE>(20000, 16, PIDRecv::REPLAY_WINDOW_SIZE / 2, 10, count);
    perfiter<ORDER, EXPIRE>(20000, 4, 1, 10, count);
    perfiter<ORDER, EXPIRE>(20000, 4, PIDRecv::REPLAY_WINDOW_SIZE / 2, 10, count);
}


class PacketIDDataSendTest : public openvpn::PacketIDDataSend
{
  public:
    PacketIDDataSendTest(bool wide, std::uint64_t start)
        : openvpn::PacketIDDataSend(wide, 0)
    {
        pid_ = PacketIDDataConstruct{start, wide};
    }
};

TEST(misc, pktid_32_bit_overrun_32bit_counter)
{
    PacketIDDataSendTest pidsend{false, 0xfffffffc};

    auto ret = pidsend.next();
    EXPECT_EQ(ret.str(), "[0xfffffffd]");

    ret = pidsend.next();
    EXPECT_EQ(ret.str(), "[0xfffffffe]");

    EXPECT_THROW(ret = pidsend.next(), PacketIDDataSend::packet_id_wrap);
}


TEST(misc, pktid_32_bit_overrun_64bit_counter)
{
    PacketIDDataSendTest pidsend{true, 0xfffffffd};

    auto ret = pidsend.next();
    EXPECT_EQ(ret.str(), "[0xfffffffe]");

    ret = pidsend.next();
    EXPECT_EQ(ret.str(), "[0xffffffff]");

    ret = pidsend.next();
    EXPECT_EQ(ret.str(), "[0x100000000]");

    ret = pidsend.next();
    EXPECT_EQ(ret.str(), "[0x100000001]");
}


TEST(misc, pktid_64_bit_overrun_64bit_counter)
{
    PacketIDDataSendTest pidsend{true, 0xfffffffffffffffc};

    auto ret = pidsend.next();
    EXPECT_EQ(ret.str(), "[0xfffffffffffffffd]");

    ret = pidsend.next();
    EXPECT_EQ(ret.str(), "[0xfffffffffffffffe]");

    EXPECT_THROW(ret = pidsend.next(), PacketIDDataSend::packet_id_wrap);
}

TEST(misc, pktid_32_bit_warn)
{
    PacketIDDataSendTest pidsend{false, 0xfefffffe};

    EXPECT_FALSE(pidsend.wrap_warning());
    auto ret = pidsend.next();
    EXPECT_EQ(ret.str(), "[0xfeffffff]");
    EXPECT_FALSE(pidsend.wrap_warning());

    ret = pidsend.next();
    EXPECT_EQ(ret.str(), "[0xff000000]");
    EXPECT_TRUE(pidsend.wrap_warning());

    ret = pidsend.next();
    EXPECT_EQ(ret.str(), "[0xff000001]");
    EXPECT_TRUE(pidsend.wrap_warning());
}

TEST(misc, pktid_64_bit_warn_32bit)
{
    // Test we are not warning at 32bit
    PacketIDDataSendTest pidsend{true, 0xfefffffe};

    EXPECT_FALSE(pidsend.wrap_warning());
    auto ret = pidsend.next();
    EXPECT_EQ(ret.str(), "[0xfeffffff]");
    EXPECT_FALSE(pidsend.wrap_warning());

    ret = pidsend.next();
    EXPECT_EQ(ret.str(), "[0xff000000]");
    EXPECT_FALSE(pidsend.wrap_warning());

    ret = pidsend.next();
    EXPECT_EQ(ret.str(), "[0xff000001]");
    EXPECT_FALSE(pidsend.wrap_warning());
}


TEST(misc, pktid_data_perf)
{
    {
        long count = 0;
        perf<3, 5>(count);
        perf<6, 5>(count);
        perf<8, 5>(count);
        // ASSERT_EQ(4746439, count);
    }
}
