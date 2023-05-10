// Test fast BufferFormat::ipv4/ipv6.
// Verify that formating exactly matches inet_ntop().

#include <arpa/inet.h>

#include "test_common.h"

#include <openvpn/buffer/bufstatic.hpp>
#include <openvpn/buffer/bufip.hpp>
#include <openvpn/buffer/bufstr.hpp>

using namespace openvpn;

static std::string to_hex(const std::uint32_t value)
{
    std::ostringstream os;
    os << std::hex << value;
    return os.str();
}

TEST(buffer_ip, unsigned_decimal)
{
    typedef BufferFormat::UnsignedDecimal<std::uint32_t> Decimal;
    for (int i = -1000; i < 1000; ++i)
    {
        StaticBuffer<Decimal::max_length()> buf;
        Decimal::write(buf, std::uint32_t(i));
        ASSERT_EQ(buf_to_string(buf), std::to_string(std::uint32_t(i)));
    }
}

TEST(buffer_ip, hex)
{
    typedef BufferFormat::Hex<std::uint32_t> Hex;
    for (int i = -1000; i < 1000; ++i)
    {
        StaticBuffer<10> buf;
        Hex::write(buf, std::uint32_t(i));
        ASSERT_EQ(buf_to_string(buf), to_hex(std::uint32_t(i)));
    }
}

#ifdef HAVE_VALGRIND
static constexpr int ITER = 10000;
#else
static constexpr int ITER = 1000000;
#endif

TEST(buffer_ip, ipv4)
{
    MTRand::Ptr prng(new MTRand());
    for (int count = 0; count < ITER; ++count)
    {
        std::uint32_t addr;
        prng->rand_fill(addr);

        StaticBuffer<16> buf;
        BufferFormat::ipv4(buf, addr);

        char in_buffer[16];
        if (::inet_ntop(AF_INET, &addr, in_buffer, sizeof(in_buffer)) == nullptr)
            throw Exception("inet_ntop failed for IPv4");

        ASSERT_EQ(std::string(in_buffer), buf_to_string(buf));
    }
}

TEST(buffer_ip, ipv6)
{
    MTRand::Ptr prng(new MTRand());
    for (int count = 0; count < ITER; ++count)
    {
        std::uint8_t addr[16];

        switch (prng->randrange32(3))
        {
        case 0:
            {
                if (prng->randbool())
                    prng->rand_bytes(addr, sizeof(addr));
                else
                    std::memset(addr, 0xff, sizeof(addr));

                size_t start = prng->randrange32(16);
                size_t end = prng->randrange32(16);
                if (end < start)
                    std::swap(start, end);
                if (prng->randbool())
                {
                    for (size_t i = start; i < end; ++i)
                        addr[i] = 0;
                }
                else
                {
                    for (size_t i = 0; i < 16; ++i)
                        if (i < start || i >= end)
                            addr[i] = 0;
                }
            }
            break;
        case 1:
            for (size_t i = 0; i < 16; ++i)
                addr[i] = prng->randbool() ? 0xff : 0;
            break;
        case 2:
            prng->rand_bytes(addr, sizeof(addr));
            break;
        }

        StaticBuffer<40> buf;
        BufferFormat::ipv6(buf, addr);

        char in_buffer[40];
        if (::inet_ntop(AF_INET6, addr, in_buffer, sizeof(in_buffer)) == nullptr)
            throw Exception("inet_ntop failed for IPv6");

        ASSERT_EQ(std::string(in_buffer), buf_to_string(buf));
    }
}
