#include "test_common.h"

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/hexstr.hpp>
#include <openvpn/reliable/relack.hpp>

#include <iostream>
#include <string>
#include <sstream>
#include <deque>
#include <algorithm>
#include <limits>

#include <openvpn/common/exception.hpp>
#include <openvpn/random/mtrandapi.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/reliable/relrecv.hpp>
#include <openvpn/reliable/relsend.hpp>
#include <openvpn/reliable/relack.hpp>
#include <openvpn/crypto/packet_id.hpp>

using namespace openvpn;

TEST(reliable, ack)
{
    std::string expected{"0400000021000000160000000b00000001\n"};
    std::ostringstream actual;

    ReliableAck ack{};
    ack.push_back(1);
    ack.push_back(11);
    ack.push_back(22);
    ack.push_back(33);

    ack.push_back(0x44);
    ack.push_back(0x55);
    ack.push_back(0x66);
    ack.push_back(0x77);
    ack.push_back(0x88);
    ack.push_back(0x99);
    ack.push_back(0x100);

    BufferAllocated buf(256, 0);
    buf.init_headroom(128);
    ack.prepend(buf, false);
    actual << render_hex_generic(buf) << std::endl;
    EXPECT_EQ(actual.str(), expected);

    std::string expected2{"080000002100000100000000990000008800000077000000660000005500000044\n"};

    std::ostringstream actual2;

    BufferAllocated buf2(256, 0);
    buf2.init_headroom(128);
    ack.prepend(buf2, true);
    actual2 << render_hex_generic(buf2) << std::endl;
    EXPECT_EQ(actual2.str(), expected2);
}

TEST(reliable, ack_dup)
{
    std::ostringstream actual;
    ReliableAck ack{};

    ack.push_back(1);
    ack.push_back(2);
    ack.push_back(3);
    ack.push_back(4);

    std::string expected{"0400000004000000030000000200000001\n"};

    BufferAllocated buf(256, 0);
    buf.init_headroom(128);
    ack.prepend(buf, false);
    actual << render_hex_generic(buf) << std::endl;
    EXPECT_EQ(actual.str(), expected);
    EXPECT_EQ(ack.resend_size(), 4u);

    ack.push_back(3);
    ack.push_back(6);

    actual.str("");
    std::string expected2{"0400000002000000040000000600000003\n"};

    BufferAllocated buf2(256, 0);
    buf2.init_headroom(128);

    ack.prepend(buf2, false);
    actual << render_hex_generic(buf2) << std::endl;
    EXPECT_EQ(actual.str(), expected2);
    /* The duplicate 3 should have be there only once */
    EXPECT_EQ(ack.resend_size(), 5u);

    actual.str("");
    BufferAllocated buf3(256, 0);
    buf3.init_headroom(128);
    ack.prepend(buf3, false);

    actual << render_hex_generic(buf3) << std::endl;

    std::string expected3{"0400000002000000040000000300000006\n"};
    EXPECT_EQ(actual.str(), expected3);
}

TEST(reliable, simple_packet)
{
    std::ostringstream actual;
    ReliableAck ack{};

    BufferAllocated buf(256, 0);
    buf.init_headroom(128);

    ack.push_back(0);

    ack.prepend(buf, false);


    ack.push_back(1);
    ack.push_back(2);
    ack.push_back(3);
}


typedef PacketID::id_t id_t;

OPENVPN_SIMPLE_EXCEPTION(receive_sequence);

struct Packet
{
    Packet()
    {
    }
    explicit Packet(const BufferPtr &buf_arg)
        : buf(buf_arg)
    {
    }
    operator bool() const
    {
        return bool(buf);
    }
    void reset()
    {
        buf.reset();
    }

    BufferPtr buf;
};

typedef ReliableSendTemplate<Packet> ReliableSend;
typedef ReliableRecvTemplate<Packet> ReliableRecv;

struct Message
{
    id_t id;
    BufferPtr buffer;
};

void print_msg(const Time::Duration t,
               const char *title,
               BufferPtr &buf,
               const id_t id,
               std::stringstream &case_detail)
{
    case_detail << t.raw() << ' ' << title
                << '[' << id << "] " << (char *)buf->data()
                << std::endl;
}


void test(MTRand &rand,
          const Time base,
          const Time::Duration end,
          const Time::Duration step,
          const Time::Duration end_sends,
          const id_t relsize,
          const size_t wiresize,
          const unsigned int reorder_prob,
          const unsigned int drop_prob,
          std::stringstream &case_detail)
{
    ReliableRecv recv(relsize);
    ReliableSend send(relsize);

    std::deque<Message> wire; // simulate transmission wire
    ReliableAck acklist{};    // back-channel for receiver to send packet ACKs back to sender

    Time retrans = Time::infinite();

    long count = 0;
    long iterations = 0;
    Time::Duration t;

    id_t send_id = 0;
    id_t rec_id = 0;

    for (t = Time::Duration(); t < end; t += step)
    {
        ++iterations;
        const Time now = base + t;

        // sender processes ACKs received from receiver
        while (!acklist.empty())
        {
            const id_t id = acklist.front();
            acklist.pop_front();
            if (rand.randrange(40)) // with small probability, simulate a dropped ACK
                                    // JMD_TODO: why wouldn't this have drop_prob probability
            {
                case_detail << t.raw() << " ACK [" << id << "]" << std::endl;
                send.ack(id);
            }
            else
            {
                case_detail << t.raw() << " Simulate dropped ACK [" << id << "]" << std::endl;
            }
        }

        // scan the sender history for un-ACKed packets that need to be retransmitted
        if (now >= retrans)
        {
            for (id_t i = send.head_id(); i < send.tail_id(); ++i)
            {
                ReliableSend::Message &m = send.ref_by_id(i);
                if (m.ready_retransmit(now))
                {
                    Message msg;
                    msg.buffer = m.packet.buf;
                    msg.id = m.id();
                    print_msg(t, "RESEND", msg.buffer, msg.id, case_detail);
                    wire.push_back(msg);

                    m.reset_retransmit(now, Time::Duration());

                    // reschedule for future retransmission test
                    const Time::Duration dur = send.until_retransmit(now);
                    retrans = now + dur;
                }
            }
        }

        // sender constructs a packet if send object is ready to accept it
        if (send.ready() && t < end_sends)
        {
            ++count;
            std::ostringstream os;
            os << "Test packet #" << count;
            const std::string s = os.str();
            BufferPtr buffer(new BufferAllocated((unsigned char *)s.c_str(), s.length() + 1, 0));
            ReliableSend::Message &m = send.send(now, Time::Duration());
            m.packet.buf = buffer;
            Message msg;
            msg.buffer = buffer;
            send_id = msg.id = m.id();
            print_msg(t, "SEND", msg.buffer, msg.id, case_detail);
            wire.push_back(msg);

            // at a future point in time, we will check the sender history for potential retransmits
            retrans = now + send.until_retransmit(now);

            // simulate packets being received out of order
            if (!rand.randrange(reorder_prob) && wire.size() >= 2)
            {
                const size_t i1 = rand.randrange(wire.size());
                const size_t i2 = rand.randrange(wire.size());
                if (i1 != i2)
                {
                    case_detail << t.raw()
                                << " Simulate packet reordering "
                                << i1 << " <-> " << i2 << std::endl;
                    std::swap(wire[i1], wire[i2]);
                }
            }
        }

        // simulate receiving packet
        while (wire.size() >= wiresize || (!wire.empty() && !rand.randrange(8)))
        {
            Message msg = wire.front();
            wire.pop_front();

            case_detail << t.raw() << " Received packet [" << msg.id << "]" << std::endl;

            // simulate dropped packet
            if (rand.randrange(drop_prob))
            {
                // pass packet to reliable sequencing object
                const unsigned int recv_flags = recv.receive(Packet(msg.buffer), msg.id);
                if (recv_flags & ReliableRecv::ACK_TO_SENDER)
                    acklist.push_back(msg.id);
            }
            else
            {
                case_detail << t.raw()
                            << " Simulate dropped packet [" << msg.id << "]" << std::endl;
            }
        }

        // is sequenced receive packet available?
        while (recv.ready())
        {
            ReliableRecv::Message &m = recv.next_sequenced();
            print_msg(t, "RECV", m.packet.buf, m.id(), case_detail);
            if (m.id() != rec_id)
                throw receive_sequence();
            else
                rec_id = m.id() + 1;

            recv.advance();
        }
    }
    case_detail << "Case Summary:\nrelsize=" << relsize
                << " wiresize=" << wiresize
                << " reorder=" << reorder_prob
                << " drop=" << drop_prob
                << " final_t=" << t.raw()
                << " iterations=" << iterations
                << " count=" << count
                << " [" << send_id << '/' << (rec_id ? rec_id - 1 : 0) << ']'
                << std::endl;
    if (send_id != (rec_id ? rec_id - 1 : 0))
        throw receive_sequence();
}

struct test_params
{
    int test_case;
    id_t relsize;
    size_t wiresize;
    unsigned int reorder_prob;
    unsigned int drop_prob;
};

TEST(reliable, simulation)
{
    MTRand rand;
    std::vector<test_params> sim_cases = {
        {1, 4, 4, 10, 16},
        {2, 2, 4, 10, 16},
        {3, 4, 8, 10, 16},
        {4, 4, 4, 2, 2},
    };
    const Time::Duration end = Time::Duration::seconds(1000);
    const Time::Duration step = Time::Duration::binary_ms(100);
    const Time::Duration end_sends = end - Time::Duration::seconds(5);
    for (auto &sim_case : sim_cases)
    {
        const Time base = Time::now();
        std::stringstream case_detail;
        try
        {
            case_detail << "Test case " << sim_case.test_case << std::endl;
            test(rand,
                 base,
                 end,
                 step,
                 end_sends,
                 sim_case.relsize,
                 sim_case.wiresize,
                 sim_case.reorder_prob,
                 sim_case.drop_prob,
                 case_detail);
        }
        catch (const std::exception &e)
        {
            ASSERT_TRUE(false) << "Exception: " << e.what() << "\nDetail:\n"
                               << case_detail.rdbuf();
        }
    }
}

/*
// following are adapted from the original unit tests in common; preserved here
// to show the ranges of test parameters, relsize, wiresize, reorder_prob, and
// drop_prob that the original author inttended

TEST(reliable, test1)
{
  MTRand rand;
  const Time base = Time::now();
  const Time::Duration end = Time::Duration::seconds(10000);
  const Time::Duration step = Time::Duration::binary_ms(100);
  const Time::Duration end_sends = end - Time::Duration::seconds(10);
  test(rand, base, end, step, end_sends, 4, 4, 10, 16);
}

TEST(reliable, test2)
{
  MTRand rand;
  const Time base = Time::now();
  const Time::Duration end = Time::Duration::seconds(1000);
  const Time::Duration step = Time::Duration::binary_ms(100);
  const Time::Duration end_sends = end - Time::Duration::seconds(10);

  for (id_t relsize = 2; relsize <= 8; relsize += 2)
    for (size_t wiresize = 2; wiresize <= 8; wiresize += 2)
      for (unsigned int reorder_prob = 2; reorder_prob <= 64; reorder_prob *= 2)
    for (unsigned int drop_prob = 2; drop_prob <= 64; drop_prob *= 2)
      test(rand, base, end, step, end_sends, relsize, wiresize, reorder_prob, drop_prob);
}
*/
