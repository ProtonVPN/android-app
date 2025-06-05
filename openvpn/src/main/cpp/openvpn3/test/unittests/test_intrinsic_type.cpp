
#include "test_common.hpp"

#include <openvpn/common/intrinsic_type.hpp>

using namespace openvpn;

template <typename BaseT>
using IntrinsicInt = IntrinsicType<BaseT, int>;

// clang-format off
struct Thing1 : IntrinsicType<Thing1, int> { using IntrinsicType<Thing1,  int>::IntrinsicType; };
struct Thing2 : IntrinsicInt<Thing2> { using IntrinsicInt<Thing2>::IntrinsicInt; };
// clang-format on

inline auto f(const Thing1 t)
{
    return Thing1(t.get() * 2);
}

inline auto f(const Thing2 t)
{
    return Thing2(t * 3);
}

template <typename ThingT>
auto g(const ThingT &t)
{
    return ThingT(t * 4);
}

TEST(IntrinsicType, overload1)
{
    // EXPECT_EQ(f(2), 4); // fails, no match
    // EXPECT_EQ(f(int(2)), int(4)); // fails, no match
    EXPECT_EQ(f(Thing1(2)), Thing1(4));
    EXPECT_EQ(f(Thing2(2)), Thing2(6));
}

TEST(IntrinsicType, template_inst)
{
    EXPECT_EQ(g(2), 8);
    EXPECT_EQ(g(Thing1(2)), Thing1(8));
    EXPECT_EQ(g(Thing2(2)), Thing2(8));
}

inline auto f(const int t)
{
    return t;
}

TEST(IntrinsicType, overload2)
{
    EXPECT_EQ(f(2), 2); // now it's fine
    EXPECT_EQ(f(f(Thing1(1))), Thing1(4));
    EXPECT_EQ(f(f(Thing2(1))), Thing2(9));
}

TEST(IntrinsicType, logic_not)
{
    auto a = Thing1(1);

    a = ~a;

    EXPECT_TRUE(a == Thing1(~1));
}

TEST(IntrinsicType, logic_or_0)
{
    auto a = Thing1(1);
    Thing1 b(2);

    EXPECT_FALSE(a == b);

    a |= b;

    EXPECT_TRUE(a == Thing1(3));
    EXPECT_TRUE(b == Thing1(2));
}

TEST(IntrinsicType, logic_or_1)
{
    auto a = Thing1(1);
    Thing1 b(2);
    constexpr Thing1 c(3);
    Thing1 d(4);

    EXPECT_FALSE(a == b);

    EXPECT_TRUE((std::is_same_v<decltype(a | b), Thing1>));

    EXPECT_TRUE((a | b) == Thing1(3));
    EXPECT_TRUE((a | b | c) == Thing1(3));
    EXPECT_TRUE((a | b | c | d) == Thing1(7));

    EXPECT_EQ(f(a | b), Thing1(6));
}

TEST(IntrinsicType, logic_and_0)
{
    auto a = Thing1(1);
    Thing1 b(2);

    EXPECT_FALSE(a == b);

    a &= b;

    EXPECT_TRUE(a == Thing1(0));
    EXPECT_TRUE(b == Thing1(2));
}

TEST(IntrinsicType, logic_and_1)
{
    Thing1 a(1);
    Thing1 b(2);
    constexpr Thing1 c(3);

    EXPECT_FALSE(a == b);

    EXPECT_TRUE((std::is_same_v<decltype(a & b), Thing1>));

    EXPECT_TRUE((a & b) == Thing1(0));
    EXPECT_TRUE((a & c) == Thing1(1));
    EXPECT_TRUE((b & c) == Thing1(2));

    EXPECT_EQ(f(a & c), Thing1(2));
}

TEST(IntrinsicType, math_add_0)
{
    auto a = Thing1(1);
    Thing1 b(2);

    EXPECT_FALSE(a == b);

    a += b;

    EXPECT_TRUE(a == Thing1(3));
    EXPECT_TRUE(b == Thing1(2));
}

TEST(IntrinsicType, math_add_1)
{
    Thing1 a(1);
    Thing1 b(2);
    constexpr Thing1 c(3);
    Thing1 d(4);

    EXPECT_FALSE(a == b);

    EXPECT_TRUE((std::is_same_v<decltype(a + b), Thing1>));

    EXPECT_EQ((a + b), Thing1(3));
    EXPECT_EQ((a + b + c), Thing1(6));
    EXPECT_EQ((a + b + c + d), Thing1(10));

    EXPECT_EQ(f(a + c), Thing1(8));
}

TEST(IntrinsicType, math_sub_0)
{
    auto a = Thing1(1);
    Thing1 b(2);

    EXPECT_FALSE(a == b);

    a -= b;

    EXPECT_TRUE(a == Thing1(-1));
    EXPECT_TRUE(b == Thing1(2));
}

TEST(IntrinsicType, math_sub_1)
{
    Thing1 a(1);
    Thing1 b(2);
    constexpr Thing1 c(3);
    Thing1 d(4);

    EXPECT_FALSE(a == b);

    EXPECT_TRUE((std::is_same_v<decltype(a - b), Thing1>));

    EXPECT_EQ(Thing1(a - b), Thing1(-1));
    EXPECT_TRUE((a - b - c) == Thing1(-4));
    EXPECT_TRUE((a - b - c - d) == Thing1(-8));

    EXPECT_EQ(f(a - c), Thing1(-4));
}

TEST(IntrinsicType, math_mul_0)
{
    auto a = Thing1(1);
    Thing1 b(2);

    EXPECT_FALSE(a == b);

    a *= b;

    EXPECT_EQ(a, Thing1(2));
    EXPECT_EQ(b, Thing1(2));
}

TEST(IntrinsicType, math_mul_1)
{
    Thing1 a(1);
    Thing1 b(2);
    constexpr Thing1 c(3);
    Thing1 d(4);

    EXPECT_FALSE(a == b);

    EXPECT_TRUE((std::is_same_v<decltype(a * b), Thing1>));

    EXPECT_EQ((a * b), Thing1(2));
    EXPECT_EQ((a * b * c), Thing1(6));
    EXPECT_EQ((a * b * c * d), Thing1(24));

    EXPECT_EQ(f(a * c), Thing1(6));
}

TEST(IntrinsicType, math_div_0)
{
    auto a = Thing1(4);
    Thing1 b(2);

    EXPECT_FALSE(a == b);

    a /= b;

    EXPECT_EQ(a, Thing1(2));
    EXPECT_EQ(b, Thing1(2));
}

TEST(IntrinsicType, math_div_1)
{
    Thing1 a(120);
    Thing1 b(2);
    constexpr Thing1 c(3);
    Thing1 d(4);

    EXPECT_FALSE(a == b);

    EXPECT_TRUE((std::is_same_v<decltype(a / b), Thing1>));

    EXPECT_EQ((a / b), Thing1(60));
    EXPECT_EQ((a / b / c), Thing1(20));
    EXPECT_EQ((a / b / c / d), Thing1(5));

    EXPECT_EQ(f(a / c), Thing1(80));
}
