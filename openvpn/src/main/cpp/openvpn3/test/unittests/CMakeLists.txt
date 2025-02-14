##
##  Tunable test_proto parameters
##
set(TEST_PROTO_NTHREADS -1 CACHE STRING "test_proto - Override number of threads")
set(TEST_PROTO_RENEG 900 CACHE STRING "test_proto - Renegotiation interval")
set(TEST_PROTO_ITER 1000000 CACHE STRING "test_proto - Number of iterations")
set(TEST_PROTO_SITER 1 CACHE STRING "test_proto - Number of high-level iterations")
set(TEST_KEYCERT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../ssl" CACHE STRING "test_proto - Certificate/private keys for testing")
option(TEST_PROTO_VERBOSE "test/ssl/proto - Enable verbose logging" OFF)

if (${CMAKE_SYSTEM_NAME} STREQUAL "Linux")
    set(RUN_UT_AS_ROOT CACHE STRING "Program to use to change to root for running the sitnl tests")
else()
    unset(RUN_UT_AS_ROOT CACHE)
endif()

if (NOT BUILD_TESTING)
  return()
endif()

include(dlgoogletest)

set(CORE_TEST_DEFINES
        -DOPENVPN_FORCE_TUN_NULL
        -DUNIT_TEST
        -DUNITTEST_SOURCE_DIR=\"${CMAKE_CURRENT_SOURCE_DIR}/\"
        -DOPENVPN_RC_NOTIFY
        -DOPENVPN_ENABLE_ASSERT
        )

add_executable(coreUnitTests
        core_tests.cpp
        test_acc.cpp
        test_alignment.cpp
        test_acc_certcheck.cpp
        test_route_emulation.cpp
        test_log.cpp
        test_comp.cpp
        test_b64.cpp
        test_verify_x509_name.cpp
        test_ssl.cpp
        test_sslctx.cpp
        test_continuation.cpp
        test_pushlex.cpp
        test_crypto.cpp
        test_optfilt.cpp
        test_clamp_typerange.cpp
        test_pktstream.cpp
        test_remotelist.cpp
        test_relack.cpp
        test_http_proxy.cpp
        test_peer_fingerprint.cpp
        test_safestr.cpp
        test_numeric_cast.cpp
        test_dns.cpp
        test_header_deps.cpp
        test_capture.cpp
        test_cleanup.cpp
        test_crypto_hashstr.cpp
        test_csum.cpp
        test_format.cpp
        test_headredact.cpp
        test_hostport.cpp
        test_ip.cpp
        test_ostream_containers.cpp
        test_parseargv.cpp
        test_path.cpp
        test_pktid_control.cpp
        test_pktid_data.cpp
        test_prefixlen.cpp
        test_randapi.cpp
        test_rc.cpp
        test_route.cpp
        test_reliable.cpp
        test_splitlines.cpp
        test_loggingmixin.cpp
        test_statickey.cpp
        test_streq.cpp
        test_time.cpp
        test_make_rc.cpp
        test_typeindex.cpp
        test_tun_builder.cpp
        test_userpass.cpp
        test_validatecreds.cpp
        test_weak.cpp
        test_cliopt.cpp
        test_buffer.cpp
        test_proto.cpp
)

# The compile flags for test_proto.cpp, prefixed with PROTO_ to avoid collision with other defines
set(PROTO_COMPILE_DEFINITIONS PROTO_N_THREADS=${TEST_PROTO_NTHREADS})
list(APPEND PROTO_COMPILE_DEFINITIONS PROTO_RENEG=${TEST_PROTO_RENEG})
list(APPEND PROTO_COMPILE_DEFINITIONS PROTO_ITER=${TEST_PROTO_ITER})
list(APPEND PROTO_COMPILE_DEFINITIONS TEST_KEYCERT_DIR="${TEST_KEYCERT_DIR}/")

if (${TEST_PROTO_VERBOSE})
    list(APPEND PROTO_COMPILE_DEFINITIONS PROTO_VERBOSE)
endif()

# Warning: When defines are only set for one specific test, we need to make sure they do
# not affect any symbols used by other tests. So be careful before adding more.
SET_SOURCE_FILES_PROPERTIES(test_proto.cpp PROPERTIES COMPILE_DEFINITIONS
    "${PROTO_COMPILE_DEFINITIONS}"
    )

if (${USE_MBEDTLS})
    target_sources(coreUnitTests PRIVATE
            test_mbedtls_x509certinfo.cpp
            test_mbedtls_authcert.cpp
            )
else ()
    target_sources(coreUnitTests PRIVATE
            test_openssl_x509certinfo.cpp
            test_openssl_authcert.cpp
            test_opensslpki.cpp
            test_openssl_misc.cpp
            test_session_id.cpp
            )
endif ()

if (${CMAKE_SYSTEM_NAME} STREQUAL "Linux")
    target_link_libraries(coreUnitTests cap)
    target_sources(coreUnitTests PRIVATE test_sitnl.cpp)
endif ()

if (UNIX)
    target_sources(coreUnitTests PRIVATE
      # includes <arpa/inet.h>
      test_buffer_ip.cpp

      test_cpu_time.cpp

      # directly includes tempfile.hpp
      test_misc_unix.cpp

      # Uses Unix Pipe semantics
      test_pipe.cpp

      # for now, only for ovpn3 servers (i.e., pgserv)
      test_psid_cookie.cpp
      )
endif ()

if (WIN32)
    target_sources(coreUnitTests PRIVATE test_iphelper.cpp test_wstring.cpp)
endif ()

add_core_dependencies(coreUnitTests)
add_json_library(coreUnitTests)

# xxHash
target_compile_definitions(coreUnitTests PRIVATE -DHAVE_XXHASH)
if(NOT MSVC)
  list(APPEND CMAKE_PREFIX_PATH
    ${DEP_DIR}/xxHash
    ${DEP_DIR}
    )
endif()
find_package(xxHash REQUIRED)
target_link_libraries(coreUnitTests xxHash::xxhash)

find_package(LZO)
if (LZO_FOUND)
  target_compile_definitions(coreUnitTests PRIVATE -DHAVE_LZO)
  target_link_libraries(coreUnitTests lzo::lzo)
  message("lzo found, running lzo compression tests")
else ()
    message("lzo not found, skipping lzo compression tests")
endif ()

target_link_libraries(coreUnitTests ${GTEST_LIB} GTest::gmock rapidcheck rapidcheck_gtest ${EXTRA_LIBS})

target_compile_definitions(coreUnitTests PRIVATE ${CORE_TEST_DEFINES})
target_include_directories(coreUnitTests PRIVATE ${EXTRA_INCLUDES})

add_test(NAME CoreTests
    COMMAND ${RUN_UT_AS_ROOT} $<TARGET_FILE:coreUnitTests> --gtest_shuffle --gtest_output=xml:test_results/test_core_$<CONFIG>.xml
    WORKING_DIRECTORY ${CMAKE_BINARY_DIR})
