#include "test_common.h"

// this file allows checking headers for "out of tree" dependencies; manage.hpp
// is an example
#include <openvpn/server/manage.hpp>

using namespace openvpn;

TEST(misc, header_deps)
{
    // this is a compile test, no runtime value
    ASSERT_TRUE(true);
}
