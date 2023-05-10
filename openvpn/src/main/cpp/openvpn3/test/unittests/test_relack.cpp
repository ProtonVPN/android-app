//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2022 OpenVPN Inc.
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

#include <openvpn/reliable/relack.hpp>

using namespace openvpn;
using namespace openvpn::reliable;

TEST(relack, test_size_1)
{
    constexpr size_t ACK_CNT = 11;

    auto ra = ReliableAck{};
    for (auto i = id_t(1); i <= ACK_CNT; ++i)
        ra.push_back(i);
    EXPECT_EQ(ra.size(), ACK_CNT);
    EXPECT_EQ(ra.resend_size(), size_t{0});
}

TEST(relack, test_prepend_1)
{
    constexpr size_t ACK_CNT = 11;

    auto ra = ReliableAck{};
    for (auto i = id_t{1}; i <= ACK_CNT; ++i)
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
    void ack(id_t id)
    {
        mAcks.push_back(id);
    };
    std::vector<id_t> mAcks;
};

TEST(relack, test_ack_1)
{
    constexpr size_t ACK_CNT = 9;

    auto ra = ReliableAck{};
    for (auto i = id_t{1}; i <= ACK_CNT; ++i)
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
    for (auto i = id_t(1); i <= ACK_CNT; ++i)
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
