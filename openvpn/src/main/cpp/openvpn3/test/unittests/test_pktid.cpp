#include "test_common.h"

#include <openvpn/crypto/packet_id.hpp>

using namespace openvpn;

template <typename PIDRecv>
void testcase(PIDRecv &pr,
              const PacketID::time_t t,
              const PacketID::time_t pkt_time,
              const PacketID::id_t pkt_id,
              const Error::Type expected_status)
{
    const PacketIDConstruct pid(pkt_time, pkt_id);
    const Error::Type status = pr.do_test_add(pid, t, true);
    // OPENVPN_LOG("[" << t << "] id=" << pkt_id << " time=" << pkt_time << ' ' << Error::name(status));
    ASSERT_EQ(status, expected_status);
}

void test()
{
    typedef PacketIDReceiveType<3, 5> PIDRecv;
    SessionStats::Ptr stats(new SessionStats());
    PIDRecv pr;
    pr.init(PIDRecv::UDP_MODE, PacketID::SHORT_FORM, "test", 0, stats);

    testcase(pr, 0, 0, 0, Error::PKTID_INVALID);
    testcase(pr, 1, 0, 1, Error::SUCCESS);
    testcase(pr, 1, 0, 1, Error::PKTID_REPLAY); /* replay */
    testcase(pr, 2, 0, 2, Error::SUCCESS);
    testcase(pr, 3, 0, 4, Error::SUCCESS);
    testcase(pr, 4, 0, 1, Error::PKTID_REPLAY); /* replay */
    testcase(pr, 5, 0, 3, Error::SUCCESS);

    testcase(pr, 6, 0, 8, Error::SUCCESS);
    testcase(pr, 10, 0, 5, Error::SUCCESS);
    testcase(pr, 15, 0, 7, Error::PKTID_EXPIRE); /* expire backtrack */

    testcase(pr, 20, 0, 127, Error::SUCCESS);
    testcase(pr, 20, 0, 127, Error::PKTID_REPLAY);
    testcase(pr, 21, 0, 128, Error::SUCCESS);
    testcase(pr, 22, 0, 64, Error::PKTID_BACKTRACK); /* large backtrack */
    testcase(pr, 23, 0, 65, Error::SUCCESS);
    testcase(pr, 24, 0, 66, Error::SUCCESS);

    testcase(pr, 30, 10, 0, Error::PKTID_INVALID);
    testcase(pr, 31, 10, 2, Error::SUCCESS);
    testcase(pr, 32, 10, 1, Error::SUCCESS);
    testcase(pr, 33, 9, 3, Error::PKTID_TIME_BACKTRACK); /* time backtrack */
    testcase(pr, 33, 0, 3, Error::PKTID_TIME_BACKTRACK); /* time backtrack */

    testcase(pr, 40, 10, 0xfffffffe, Error::SUCCESS);
    testcase(pr, 41, 10, 0xffffffff, Error::SUCCESS);
    testcase(pr, 42, 10, 0, Error::PKTID_INVALID); /* wrap */

    testcase(pr, 50, 11, 1, Error::SUCCESS);
    testcase(pr, 51, 11, 2, Error::SUCCESS);
    testcase(pr, 52, 11, 3, Error::SUCCESS);
    testcase(pr, 53, 11, 3, Error::PKTID_REPLAY); /* replay */
    testcase(pr, 54, 11, 10, Error::SUCCESS);
    testcase(pr, 55, 11, 9, Error::SUCCESS);
    testcase(pr, 56, 11, 1, Error::PKTID_REPLAY); /* replay */
    testcase(pr, 57, 11, 8, Error::SUCCESS);
    testcase(pr, 58, 11, 4, Error::SUCCESS);
    testcase(pr, 63, 11, 5, Error::PKTID_EXPIRE); /* expire backtrack */

    testcase(pr, 70, 15, 1, Error::SUCCESS);
    testcase(pr, 70, 15, 3, Error::SUCCESS);
    testcase(pr, 70, 15, 2, Error::SUCCESS);

    testcase(pr, 80, 15, 50, Error::SUCCESS);
    testcase(pr, 80, 15, 80, Error::SUCCESS);
    testcase(pr, 81, 15, 127, Error::SUCCESS);
    testcase(pr, 82, 15, 128, Error::SUCCESS);
    testcase(pr, 83, 15, 64, Error::PKTID_BACKTRACK); /* large backtrack */
    testcase(pr, 84, 15, 65, Error::SUCCESS);
    testcase(pr, 85, 15, 66, Error::SUCCESS);
}

template <unsigned int ORDER, unsigned int EXPIRE>
void perfiter(const long n,
              const long range,
              const long step,
              const long iter_per_step_pre,
              long &count)
{
    typedef PacketIDReceiveType<ORDER, EXPIRE> PIDRecv;

    const long iter_per_step = iter_per_step_pre * step;
    // OPENVPN_LOG("ITER order=" << ORDER << " n=" << n << " range=" << range << " step=" << step << " iter_per_step="
    //			    << iter_per_step);

    constexpr PacketID::time_t pkt_time = 1234;

    MTRand urand;
    std::vector<bool> bv(n);
    long high = 0;
    SessionStats::Ptr stats(new SessionStats());
    PIDRecv pr;
    pr.init(PIDRecv::UDP_MODE, PacketID::SHORT_FORM, "test", 0, stats);

    for (long i = 1; i < n; i += step)
    {
        for (long j = 0; j < iter_per_step; ++j)
        {
            const long delta = long(urand.randrange32(range)) - range / 2;
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
                const PacketIDConstruct pid(0, id);
                const Error::Type result = pr.do_test_add(pid, pkt_time, true);
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
    typedef PacketIDReceiveType<ORDER, EXPIRE> PIDRecv;

    perfiter<ORDER, EXPIRE>(20000, PIDRecv::REPLAY_WINDOW_SIZE * 3, 1, 10, count);
    perfiter<ORDER, EXPIRE>(20000, PIDRecv::REPLAY_WINDOW_SIZE * 3, PIDRecv::REPLAY_WINDOW_SIZE / 2, 10, count);
    perfiter<ORDER, EXPIRE>(20000, PIDRecv::REPLAY_WINDOW_SIZE * 2, 1, 10, count);
    perfiter<ORDER, EXPIRE>(20000, PIDRecv::REPLAY_WINDOW_SIZE * 2, PIDRecv::REPLAY_WINDOW_SIZE / 2, 10, count);
    perfiter<ORDER, EXPIRE>(20000, 16, 1, 10, count);
    perfiter<ORDER, EXPIRE>(20000, 16, PIDRecv::REPLAY_WINDOW_SIZE / 2, 10, count);
    perfiter<ORDER, EXPIRE>(20000, 4, 1, 10, count);
    perfiter<ORDER, EXPIRE>(20000, 4, PIDRecv::REPLAY_WINDOW_SIZE / 2, 10, count);
}

TEST(misc, pktid)
{
    {
        long count = 0;
        perf<3, 5>(count);
        perf<6, 5>(count);
        perf<8, 5>(count);
        // ASSERT_EQ(4746439, count);
    }
    test();
}
