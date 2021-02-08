cmake_minimum_required(VERSION 3.5)

project(tests)
set(CMAKE_CXX_STANDARD 14)

include(findcoredeps)
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

set(SOURCES

        ${TESTS_CRYPTO}
        )


if (${CMAKE_SYSTEM_NAME} STREQUAL "Linux")
    list(APPEND SOURCES test_sitnl.cpp)
endif ()

if (UNIX)
    list(APPEND SOURCES test_cpu_time.cpp)
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


add_core_dependencies(coreUnitTests)
target_link_libraries(coreUnitTests ${GTEST_LIB} ${EXTRA_LIBS})

target_compile_definitions(coreUnitTests PRIVATE ${CORE_TEST_DEFINES})
target_include_directories(coreUnitTests PRIVATE ${EXTRA_INCLUDES})

add_test(NAME CoreTests COMMAND coreUnitTests)
