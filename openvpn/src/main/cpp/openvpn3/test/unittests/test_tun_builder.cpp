
#include "test_common.hpp"

#include <openvpn/tun/builder/capture.hpp>

TEST(tun_builder, to_string_zero)
{
    auto rb = openvpn::TunBuilderCapture::RouteBase();

    rb.address = "0.0.0.0";
    rb.prefix_length = 0;

    EXPECT_EQ(rb.to_string(), "0.0.0.0/0");
}