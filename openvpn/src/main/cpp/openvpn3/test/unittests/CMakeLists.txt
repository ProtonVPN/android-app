
# current latest GTEST version
set(OVPN_GTEST_VERSION release-1.11.0)
# use an older version of gtest for GCC < 5.1.0; 1.10.1 is the latest
# version that works with the 4.8.x compilers
if (CMAKE_COMPILER_IS_GNUCC AND CMAKE_CXX_COMPILER_VERSION VERSION_LESS 5.1.0)
  set(OVPN_GTEST_VERSION release-1.10.0)
  # Note: not all the test compilation units hit the
  # "'is_trivially_copy_constructible' is not a member of 'std'"
  # issue; only the following do (with line number as of this
  # writing and the include on the line):
  # core_tests.cpp:21:  // #include <gtest/gtest.h>
  # test_b64.cpp:22:  // #include "test_common.h"
  # test_comp.cpp:45:  // #include "test_common.h"
  # test_log.cpp:22:  // #include "test_common.h"
  # test_route_emulation.cpp:23:  // #include "test_common.h"
  # test_verify_x509_name.cpp:25:  // #include "test_common.h"
  #
  # Note further: test_common.h includes gtest/gtest.h; but that is
  # not a sufficient condition to hit the bust.  For example,
  # test_continuation.cpp includes test_common.h but does not manifest
  # the issue.
endif()

include(dlgoogletest)

# Extra includes/libraries that are currently only use by the core unit test
FIND_PATH(LZO_INCLUDE_DIR NAMES lzo/lzo1x.h)
FIND_LIBRARY(LZO_LIBRARIES NAMES lzo2)

if (LZO_INCLUDE_DIR AND LZO_LIBRARIES)
    list(APPEND CORE_TEST_DEFINES -DHAVE_LZO)
    list(APPEND EXTRA_LIBS ${LZO_LIBRARIES})
    list(APPEND EXTRA_INCLUDES ${LZO_INCLUDE_DIR})
    message("lzo found, running lzo compression tests")
else ()
    message("lzo not found, skipping lzo compression tests")
endif ()

set(CORE_TEST_DEFINES
        -DOPENVPN_FORCE_TUN_NULL
        -DUNIT_TEST
        -DUNITTEST_SOURCE_DIR=\"${CMAKE_CURRENT_SOURCE_DIR}/\"
        )

add_executable(coreUnitTests
        core_tests.cpp
        test_route_emulation.cpp
        test_log.cpp
        test_comp.cpp
        test_b64.cpp
        test_verify_x509_name.cpp
        test_ssl.cpp
        test_continuation.cpp
        test_crypto.cpp
        test_optfilt.cpp
        test_pktstream.cpp
        test_remotelist.cpp
        test_http_proxy.cpp
        test_peer_fingerprint.cpp
        test_safestr.cpp
        )

if (${USE_MBEDTLS})
    target_sources(coreUnitTests PRIVATE
            test_mbedtls_x509certinfo.cpp
            )
else ()
    target_sources(coreUnitTests PRIVATE
            test_openssl_x509certinfo.cpp
            )
endif ()

if (${CMAKE_SYSTEM_NAME} STREQUAL "Linux")
    list(APPEND EXTRA_LIBS -lcap)

    target_sources(coreUnitTests PRIVATE test_sitnl.cpp)
endif ()

if (UNIX)
    target_sources(coreUnitTests PRIVATE test_cpu_time.cpp)
endif ()

if (WIN32)
    target_sources(coreUnitTests PRIVATE test_iphelper.cpp)
endif ()

if (WIN32)
    target_sources(coreUnitTests PRIVATE test_iphelper.cpp)
endif ()

add_core_dependencies(coreUnitTests)
target_link_libraries(coreUnitTests ${GTEST_LIB} ${EXTRA_LIBS})

target_compile_definitions(coreUnitTests PRIVATE ${CORE_TEST_DEFINES})
target_include_directories(coreUnitTests PRIVATE ${EXTRA_INCLUDES})

add_test(NAME CoreTests COMMAND coreUnitTests)
