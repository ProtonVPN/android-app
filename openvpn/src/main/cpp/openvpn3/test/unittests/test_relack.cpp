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


#include "test_common.hpp"

#include <openvpn/reliable/relack.hpp>

using namespace openvpn;
using namespace openvpn::reliable;
namespace orel = openvpn::reliable;

TEST(relack, test_size_1)
{
    constexpr size_t ACK_CNT = 11;

    auto ra = ReliableAck{};
    for (auto i = orel::id_t(1); i <= ACK_CNT; ++i)
        ra.push_back(i);
    EXPECT_EQ(ra.size(), ACK_CNT);
    EXPECT_EQ(ra.resend_size(), size_t{0});
}

TEST(relack, test_prepend_1)
{
    constexpr size_t ACK_CNT = 11;

    auto ra = ReliableAck{};
    for (auto i = orel::id_t{1}; i <= ACK_CNT; ++i)
        ra.push_back(i);
    EXPECT_EQ(ra.size(), ACK_CNT);

    constexpr size_t storageSize = 1024;
    unsigned char storage[storageSize];

    {
        auto buf = Buffer(storage, storageSize, false);
        buf.init_headroom(storageSize / 2);

        // Add 4 packets to a CONTROL packet, should reduce number by 4
        ra.prepend(buf, false);
        EXPECT_EQ(ra.size(), ACK_CNT - 4);
        EXPECT_EQ(ra.resend_size(), size_t{4});

        // Add packets to an ACK_V1 packet, should reduce number by up to 8
        ra.prepend(buf, true);
        EXPECT_EQ(ra.size(), size_t{0});
        EXPECT_EQ(ra.resend_size(), size_t{8});
    }

    {
        auto buf = Buffer(storage, storageSize, false);
        buf.init_headroom(storageSize / 2);

        ra.prepend(buf, false); // resending should not change array sizes
        EXPECT_EQ(ra.size(), size_t{0});
        EXPECT_EQ(ra.resend_size(), size_t{8});
    }

    {
        auto buf = Buffer(storage, storageSize, false);
        buf.init_headroom(storageSize / 2);

        ra.prepend(buf, false);
        EXPECT_EQ(ra.size(), size_t{0});
        EXPECT_EQ(ra.resend_size(), size_t{8});
    }
}

struct RelSendMck
{
    void ack(orel::id_t id)
    {
        mAcks.push_back(id);
    };
    std::vector<orel::id_t> mAcks;
};

TEST(relack, test_ack_1)
{
    constexpr size_t ACK_CNT = 9;

    auto ra = ReliableAck{};
    for (auto i = orel::id_t{1}; i <= ACK_CNT; ++i)
        ra.push_back(i);
    EXPECT_EQ(ra.size(), ACK_CNT);

    constexpr size_t storageSize = 1024;
    unsigned char storage[storageSize];

    auto buf = Buffer(storage, storageSize, false);
    buf.init_headroom(storageSize / 2);

    ra.prepend(buf, false);
    EXPECT_EQ(ra.size(), ACK_CNT - 4);

    RelSendMck send;
    ra.ack(send, buf, true);

    for (auto i = size_t{0}; i < send.mAcks.size(); ++i)
    {
        EXPECT_EQ(send.mAcks[i], 4 - i);
    }
}

TEST(relack, test_ack_2)
{
    constexpr size_t ACK_CNT = 9;

    auto ra = ReliableAck{};
    for (auto i = orel::id_t(1); i <= ACK_CNT; ++i)
        ra.push_back(i);
    EXPECT_EQ(ra.size(), ACK_CNT);

    constexpr size_t storageSize = 1024;
    unsigned char storage[storageSize];

    {
        auto buf = Buffer(storage, storageSize, false);
        buf.init_headroom(storageSize / 2);

        EXPECT_EQ(ra.size(), size_t{9});
        EXPECT_EQ(ra.resend_size(), size_t{0});

        ra.prepend(buf, true);
        EXPECT_EQ(ra.size(), size_t{1});
        EXPECT_EQ(ra.resend_size(), size_t{8});

        RelSendMck send{};
        auto num = ra.ack(send, buf, false);
        EXPECT_EQ(send.mAcks.size(), size_t{0});
        EXPECT_EQ(num, size_t{8});

        RelSendMck send2;
        ra.prepend(buf, true);
        num = ra.ack(send2, buf, true);
        EXPECT_EQ(num, size_t{8});
        EXPECT_EQ(send2.mAcks.size(), size_t{8});
    }
}
