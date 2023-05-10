//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2023 OpenVPN Inc.
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
//    If not, see <http://www.gnu.org/licenses/>.


#include "test_common.h"

#include <cstdint>

#include <openvpn/common/clamp_typerange.hpp>

using namespace openvpn::numeric_util;

TEST(clamp_to_typerange, same_type_nocast1)
{
    int32_t i32 = -1;
    auto result = clamp_to_typerange<int32_t>(i32);
    EXPECT_EQ(result, i32);
}

TEST(clamp_to_typerange, sign_mismatch_32_1)
{
    int32_t i32 = -1;
    auto result = clamp_to_typerange<uint32_t>(i32);
    EXPECT_EQ(result, 0);
}

TEST(clamp_to_typerange, sign_mismatch_32_2)
{
    uint32_t u32 = std::numeric_limits<uint32_t>::max();
    auto result = clamp_to_typerange<int32_t>(u32);
    EXPECT_EQ(result, std::numeric_limits<int32_t>::max());
}

TEST(clamp_to_typerange, sign_mismatch_32_3)
{
    uint32_t u32 = 0;
    auto result = clamp_to_typerange<int32_t>(u32);
    EXPECT_EQ(result, 0);
}

TEST(clamp_to_typerange, sign_mismatch_32_4)
{
    uint32_t u32 = 42;
    auto result = clamp_to_typerange<int32_t>(u32);
    EXPECT_EQ(result, 42);
}

TEST(clamp_to_typerange, sign_mismatch_32_5)
{
    uint32_t u32 = uint32_t(std::numeric_limits<int32_t>::max());
    auto result = clamp_to_typerange<int32_t>(u32);
    EXPECT_EQ(result, std::numeric_limits<int32_t>::max());
}

TEST(clamp_to_typerange, sign_mismatch_32_6)
{
    int32_t s32 = std::numeric_limits<int32_t>::max();
    auto result = clamp_to_typerange<uint8_t>(s32);
    EXPECT_EQ(result, std::numeric_limits<uint8_t>::max());
}

TEST(clamp_to_typerange, sign_mismatch_32_7)
{
    int32_t s32 = 42;
    auto result = clamp_to_typerange<uint8_t>(s32);
    EXPECT_EQ(result, 42);
}

TEST(clamp_to_typerange, s_range_mismatch_16_64_1)
{
    int64_t s64 = std::numeric_limits<int64_t>::max();
    auto result = clamp_to_typerange<int16_t>(s64);
    EXPECT_EQ(result, std::numeric_limits<int16_t>::max());
}

TEST(clamp_to_typerange, s_range_match_16_64_1)
{
    int64_t s64 = 0;
    auto result = clamp_to_typerange<int16_t>(s64);
    EXPECT_EQ(result, 0);
}

TEST(clamp_to_typerange, u_range_mismatch_16_64_1)
{
    uint64_t u64 = std::numeric_limits<uint64_t>::max();
    auto result = clamp_to_typerange<uint16_t>(u64);
    EXPECT_EQ(result, std::numeric_limits<uint16_t>::max());
}
