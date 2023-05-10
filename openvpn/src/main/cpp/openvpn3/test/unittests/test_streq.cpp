#include "test_common.h"
#include <iostream>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>

#include <openvpn/common/strneq.hpp>

using namespace openvpn;

void test(const std::string &s1, const std::string &s2, const bool should_eq)
{
    const bool neq = crypto::str_neq(s1, s2);
    // OPENVPN_LOG("'" << s1 << "' cmp '" << s2 << "' " << (neq ? "NEQ" : "EQ"));
    ASSERT_NE(neq, should_eq);
}

TEST(misc, streq)
{
    test("", "", true);
    test("x", "", false);
    test("", "x", false);
    test("foo", "foo", true);
    test("foobar", "foo", false);
    test("foo", "foobar", false);
}

void test_timing()
{
    size_t count = 0;
    for (size_t i = 0; i < 1000000000; ++i)
        count += crypto::str_neq("foobarxxx", "foobar");
    OPENVPN_LOG(count);
}