if (NOT BUILD_TESTING)
  return()
endif()

# current latest GTEST version
set(OVPN_GTEST_VERSION release-1.11.0)
include(dlgoogletest)

set(CORE_TEST_DEFINES
        -DOPENVPN_FORCE_TUN_NULL
        -DUNIT_TEST
        -DUNITTEST_SOURCE_DIR=\"${CMAKE_CURRENT_SOURCE_DIR}/\"
        -DOPENVPN_RC_NOTIFY
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
        test_pktid.cpp
        test_prefixlen.cpp
        test_rc.cpp
        test_route.cpp
	test_reliable.cpp
        test_statickey.cpp
        test_streq.cpp
        test_time.cpp
        test_typeindex.cpp
        test_validatecreds.cpp
        test_weak.cpp
        test_cliopt.cpp
        test_buffer.cpp
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
      )
endif ()

if (WIN32)
    target_sources(coreUnitTests PRIVATE test_iphelper.cpp)
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

target_link_libraries(coreUnitTests ${GTEST_LIB} ${EXTRA_LIBS})

target_compile_definitions(coreUnitTests PRIVATE ${CORE_TEST_DEFINES})
target_include_directories(coreUnitTests PRIVATE ${EXTRA_INCLUDES})

add_test(NAME CoreTests COMMAND coreUnitTests)
