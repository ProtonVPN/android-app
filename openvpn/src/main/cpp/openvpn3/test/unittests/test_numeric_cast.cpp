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
#include <cctype>

#include <openvpn/common/numeric_cast.hpp>


using namespace openvpn::numeric_util;


TEST(numeric_cast, same_type_nocast1)
{
    int32_t i32 = -1;
    auto result = numeric_cast<int32_t>(i32);
    EXPECT_EQ(result, i32);
}

TEST(numeric_cast, sign_mismatch_32_1)
{
    int32_t i32 = -1;
    EXPECT_THROW(numeric_cast<uint32_t>(i32), numeric_out_of_range);
}

TEST(numeric_cast, sign_mismatch_32_2)
{
    uint32_t u32 = std::numeric_limits<uint32_t>::max();
    EXPECT_THROW(numeric_cast<int32_t>(u32), numeric_out_of_range);
}

TEST(numeric_cast, sign_mismatch_32_3)
{
    uint32_t u32 = 0;
    auto result = numeric_cast<int32_t>(u32);
    EXPECT_EQ(result, 0);
}

TEST(numeric_cast, sign_mismatch_32_4)
{
    uint32_t u32 = 42;
    auto result = numeric_cast<int32_t>(u32);
    EXPECT_EQ(result, 42);
}

TEST(numeric_cast, sign_mismatch_32_5)
{
    uint32_t u32 = uint32_t(std::numeric_limits<int32_t>::max());
    auto result = numeric_cast<int32_t>(u32);
    EXPECT_EQ(result, std::numeric_limits<int32_t>::max());
}

TEST(numeric_cast, sign_mismatch_32_6)
{
    int32_t s32 = std::numeric_limits<int32_t>::max();
    EXPECT_THROW(numeric_cast<uint8_t>(s32), numeric_out_of_range);
}

TEST(numeric_cast, sign_mismatch_32_7)
{
    int32_t s32 = 42;
    auto result = numeric_cast<uint8_t>(s32);
    EXPECT_EQ(result, 42);
}

TEST(numeric_cast, s_range_mismatch_16_64_1)
{
    int64_t s64 = std::numeric_limits<int64_t>::max();
    EXPECT_THROW(numeric_cast<int16_t>(s64), numeric_out_of_range);
}

TEST(numeric_cast, s_range_match_16_64_1)
{
    int64_t s64 = 0;
    auto result = numeric_cast<int16_t>(s64);
    EXPECT_EQ(result, 0);
}

TEST(numeric_cast, u_range_mismatch_16_64_1)
{
    uint64_t u64 = std::numeric_limits<uint64_t>::max();
    EXPECT_THROW(numeric_cast<uint16_t>(u64), numeric_out_of_range);
}
