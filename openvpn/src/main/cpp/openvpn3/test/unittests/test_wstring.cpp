//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2024- OpenVPN Inc.
//
//    SPDX-License-Identifier: MPL-2.0 OR AGPL-3.0-only WITH openvpn3-openssl-exception
//


#include "test_common.hpp"

#include <openvpn/common/wstring.hpp>

using namespace openvpn;

// Fixture for the tests below
class WStringTest : public testing::Test
{
  protected:
    const std::string jojo = "Jürgen Wößner";
    const std::string lev = "Лев Толстой";
    const std::string shigeru = "宮本茂";

    const std::array<std::wstring::value_type, 14> raw_jojo = {
        0x004a, 0x00fc, 0x0072, 0x0067, 0x0065, 0x006e, 0x0020, 0x0057, 0x00f6, 0x00df, 0x006e, 0x0065, 0x0072, 0x0000};
    const std::array<std::wstring::value_type, 12> raw_lev = {
        0x041b, 0x0435, 0x0432, 0x0020, 0x0422, 0x043e, 0x043b, 0x0441, 0x0442, 0x043e, 0x0439, 0x0000};
    const std::array<std::wstring::value_type, 4> raw_shigeru = {
        0x5bae, 0x672c, 0x8302, 0x0000};

    const std::wstring wide_jojo = raw_jojo.data();
    const std::wstring wide_lev = raw_lev.data();
    const std::wstring wide_shigeru = raw_shigeru.data();
};

TEST_F(WStringTest, FromUtf8)
{
    std::wstring utf16_jojo = wstring::from_utf8(jojo);
    EXPECT_EQ(utf16_jojo.size(), wide_jojo.size());
    EXPECT_TRUE(utf16_jojo == wide_jojo);

    std::wstring utf16_lev = wstring::from_utf8(lev);
    EXPECT_EQ(utf16_lev.size(), wide_lev.size());
    EXPECT_TRUE(utf16_lev == wide_lev);

    std::wstring utf16_shigeru = wstring::from_utf8(shigeru);
    EXPECT_EQ(utf16_shigeru.size(), wide_shigeru.size());
    EXPECT_TRUE(utf16_shigeru == wide_shigeru);
}

TEST_F(WStringTest, ToUtf8)
{
    std::string utf8_jojo = wstring::to_utf8(wide_jojo);
    EXPECT_EQ(utf8_jojo.size(), jojo.size());
    EXPECT_TRUE(utf8_jojo == jojo);

    std::string utf8_lev = wstring::to_utf8(wide_lev);
    EXPECT_EQ(utf8_lev.size(), lev.size());
    EXPECT_TRUE(utf8_lev == lev);

    std::string utf8_shigeru = wstring::to_utf8(wide_shigeru);
    EXPECT_EQ(utf8_shigeru.size(), shigeru.size());
    EXPECT_TRUE(utf8_shigeru == shigeru);
}

TEST_F(WStringTest, ToCArray)
{
    auto array_ptr = wstring::to_wchar_t(wide_jojo);
    EXPECT_TRUE(::wcslen(array_ptr.get()) == wide_jojo.size());
    EXPECT_TRUE(::wcscmp(array_ptr.get(), wide_jojo.c_str()) == 0);
    EXPECT_TRUE(std::wstring(array_ptr.get()) == wide_jojo);
}

TEST_F(WStringTest, MultiSzFromVector)
{
    std::vector<std::string> names{jojo, lev, shigeru};
    std::wstring multi_names = wstring::pack_string_vector(names);
    const auto jojo_ptr = multi_names.data();
    const auto lev_ptr = multi_names.data() + raw_jojo.size();
    const auto shigeru_ptr = multi_names.data() + raw_jojo.size() + raw_lev.size();
    EXPECT_EQ(multi_names.size(), raw_jojo.size() + raw_lev.size() + raw_shigeru.size() + 1);
    EXPECT_TRUE(std::memcmp(jojo_ptr, wide_jojo.c_str(), raw_jojo.size()) == 0);
    EXPECT_TRUE(std::memcmp(lev_ptr, wide_lev.c_str(), raw_lev.size()) == 0);
    EXPECT_TRUE(std::memcmp(shigeru_ptr, wide_shigeru.c_str(), raw_shigeru.size()) == 0);
    EXPECT_EQ(multi_names[multi_names.size()], L'\0');
}
