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

#include <openvpn/common/alignment.hpp>

#include <cstdint>
#include <cstdlib>
#include <ctime>

using namespace openvpn;

TEST(AlignmentSafeExtractTest, ExtractIntFromMisalignedData)
{
    int i = 1;
    unsigned char data[32];
    data[0] = 0xF0;
    memcpy(data + 1, &i, sizeof(i));
    int value = alignment_safe_extract<int>(data + 1);
    EXPECT_EQ(value, i);
}

TEST(AlignmentSafeExtractTest, ExtractDoubleFromMisalignedData)
{
    double d = 98.6;
    unsigned char data[32];
    data[0] = 0xF0;
    memcpy(data + 1, &d, sizeof(d));
    double value = alignment_safe_extract<double>(data + 1);
    EXPECT_EQ(value, d);
}

TEST(AlignmentSafeExtractTest, ExtractStructFromMisalignedData)
{
    struct TestStruct
    {
        int a = 42;
        float b = 98.6f;
    } test_struct;

    unsigned char data[32];
    data[0] = 0xF0;
    memcpy(data + 1, &test_struct, sizeof(test_struct));
    TestStruct value = alignment_safe_extract<TestStruct>(data + 1);
    EXPECT_EQ(value.a, test_struct.a);
    EXPECT_EQ(value.b, test_struct.b);
}
