#include "test_common.h"
#include <iostream>
#include <memory>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>

#include <openvpn/common/cleanup.hpp>

using namespace openvpn;

TEST(misc, cleanup)
{

    bool ran_cleanup = false;
    {
        auto c = Cleanup([&]()
                         { ran_cleanup = true; });
        static_assert(std::is_nothrow_move_constructible<decltype(c)>::value,
                      "Cleanup should be noexcept MoveConstructible");
    }
    ASSERT_TRUE(ran_cleanup) << "cleanup didn't run as expected";
}
