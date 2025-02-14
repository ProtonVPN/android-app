
#include "test_common.hpp"

#include <openvpn/log/logger.hpp>

using namespace openvpn;
using namespace openvpn::logging;

TEST(LoggingMixin, is_shared)
{
    auto lm1 = LoggingMixin<1>();
    auto lm2 = LoggingMixin<1>();

    EXPECT_EQ(lm1.log_level(), lm2.log_level());
    lm1.set_log_level(lm1.log_level() + 1);
    EXPECT_EQ(lm1.log_level(), lm2.log_level());
}

TEST(LoggingMixin, is_not_shared)
{
    auto lm1 = LoggingMixin<1, 1, int>();
    auto lm2 = LoggingMixin<1, 1, float>();

    EXPECT_EQ(lm1.log_level(), lm2.log_level());
    lm1.set_log_level(lm1.log_level() + 1);
    EXPECT_NE(lm1.log_level(), lm2.log_level());
}