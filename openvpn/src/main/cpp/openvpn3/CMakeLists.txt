cmake_minimum_required(VERSION 3.16...3.30)

project(OpenVPN3-core VERSION 3)

# AddressSanitize - use CXX=clang++ CC=clang cmake -DCMAKE_BUILD_TYPE=asan to build with ASAN
# export  UBSAN_OPTIONS=print_stacktrace=1 helps debugging these
set(CMAKE_C_FLAGS_ASAN
        "-fsanitize=address,undefined -fno-sanitize-recover=all -fno-optimize-sibling-calls -fsanitize-address-use-after-scope -fno-omit-frame-pointer -g -O1 -DINSTRUMENTATION_SLOWDOWN"
        CACHE STRING "Flags used by the C compiler during AddressSanitizer builds."
        FORCE)
set(CMAKE_CXX_FLAGS_ASAN
        "-fsanitize=address,undefined -fno-sanitize-recover=all -fno-optimize-sibling-calls -fsanitize-address-use-after-scope -fno-omit-frame-pointer -g -O1 -DINSTRUMENTATION_SLOWDOWN"
        CACHE STRING "Flags used by the C++ compiler during AddressSanitizer builds."
        FORCE)

set(CMAKE_LINKER_FLAGS_ASAN
        "-fsanitize=address,undefined -fno-sanitize-recover=all -fsanitize-address-use-after-scope"
        CACHE STRING "Flags used by the linker during AddressSanitizer builds."
        FORCE)

# Append /MP flag for MSVC
if (MSVC)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} /MP")
endif()

set(CMAKE_MODULE_PATH ${CMAKE_CURRENT_SOURCE_DIR}/cmake
                      ${CMAKE_MODULE_PATH})
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_C_STANDARD 99)

include(CTest)

include(findcoredeps)
include(ovpn-doxygen)

add_subdirectory(client)
add_subdirectory(test/unittests)
add_subdirectory(test/ovpncli)

add_subdirectory(openvpn/omi)
add_subdirectory(openvpn/ovpnagent/win)
add_subdirectory(openvpn/ovpnagent/mac)

if (ENABLE_DOXYGEN)
    # Exclude some project specific directories
    set(DOXYGEN_EXCLUDE_PATTERNS
        "${PROJECT_SOURCE_DIR}/README.md"
        "*/vcpkg_installed/*"
        "*/googletest-src/*"
        "*/rapidcheck-src/*"
        "${PROJECT_SOURCE_DIR}/deps/*"
        "*/test/unittests/comp-testdata/*"
        "*/test/unittests/input/*"
        "*/test/unittests/output/*"
        "*/test/unittests/userpass/*"
        )
    set(DOXYGEN_USE_MDFILE_AS_MAINPAGE "${PROJECT_SOURCE_DIR}/doc/developer_guide.md")

    configure_doxygen("OpenVPN 3 Core Library" "core" "doxygen/core")
endif ()
