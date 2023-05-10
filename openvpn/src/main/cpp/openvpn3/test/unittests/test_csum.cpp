#include <iostream>
#include "test_common.h"

#include <openvpn/common/size.hpp>

#include <openvpn/buffer/buffer.hpp>
#include <openvpn/ip/csum.hpp>
#include <openvpn/random/mtrandapi.hpp>

using namespace openvpn;

std::uint16_t ip_checksum_slow(const void *ip, size_t size)
{
    const std::uint16_t *buf = (const std::uint16_t *)ip;
    std::uint32_t cksum = 0;

    while (size >= sizeof(std::uint16_t))
    {
        cksum += *buf++;
        size -= sizeof(std::uint16_t);
    }

    if (size)
        cksum += *(uint8_t *)buf;

    cksum = (cksum >> 16) + (cksum & 0xffff);
    cksum += (cksum >> 16);
    return ~cksum;
}

TEST(misc, stress_csum)
{
    RandomAPI::Ptr prng(new MTRand);
    BufferAllocated buf(256, 0);

    for (long i = 0; i < 1000000; ++i)
    {
        buf.init_headroom(0);
        const size_t size = 16 + (prng->rand_get<std::uint8_t>() & 127);
        std::uint8_t *raw = buf.write_alloc(size);
        prng->rand_bytes(raw, size);
        const std::uint16_t orig_csum = IPChecksum::checksum(raw, size);
        ASSERT_EQ(orig_csum, ip_checksum_slow(raw, size)) << "checksum algorithm inconsistency #1";

        std::uint8_t old_prefix[16];
        std::memcpy(old_prefix, raw, 16);
        const int n = (prng->rand_get<std::uint8_t>() & 7);
        for (int j = 0; j < n; ++j)
        {
            const size_t idx = (prng->rand_get<std::uint8_t>() & 15);
            const std::uint8_t newval = prng->rand_get<std::uint8_t>();
            raw[idx] = newval;
        }
        const std::uint16_t updated_csum = IPChecksum::cfold(IPChecksum::diff16(old_prefix,
                                                                                raw,
                                                                                IPChecksum::cunfold(orig_csum)));
        const std::uint16_t verify_csum = IPChecksum::checksum(raw, size);
        ASSERT_EQ(verify_csum, ip_checksum_slow(raw, size)) << "checksum algorithm inconsistency #2";
        ASSERT_EQ(updated_csum, verify_csum)
            << i
            << " size=" << size << " n=" << n
            << " orig=" << orig_csum
            << " updated=" << updated_csum
            << " verify=" << verify_csum
            << std::endl;
    }
}
