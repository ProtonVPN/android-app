if (MSVC)
    find_package(GTest CONFIG REQUIRED)
    set(GTEST_LIB GTest::gtest_main)
else()

set(GTEST_LIB gtest_main)

# Google Test Unit testing
# Download and unpack googletest at configure time

# Ensure that this only downloaded and added once
#include_guard(GLOBAL)
# Unfortunately include_guard requires cmake >= 3.10
include(mypragmaonce)

my_pragma_once()

if(NOT OVPN_GTEST_VERSION)
    set(OVPN_GTEST_VERSION release-1.11.0)
endif()

configure_file(${CMAKE_CURRENT_LIST_DIR}/CMakeLists.txt.in googletest-download/CMakeLists.txt)
execute_process(COMMAND ${CMAKE_COMMAND} -G "${CMAKE_GENERATOR}" .
        RESULT_VARIABLE result
        WORKING_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}/googletest-download )
if(result)
    message(FATAL_ERROR "CMake step for googletest failed: ${result}")
endif()
execute_process(COMMAND ${CMAKE_COMMAND} --build .
        RESULT_VARIABLE result
        WORKING_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}/googletest-download )
if(result)
    message(FATAL_ERROR "Build step for googletest failed: ${result}")
endif()

# Prevent overriding the parent project's compiler/linker
# settings on Windows
set(gtest_force_shared_crt ON CACHE BOOL "" FORCE)

# Add googletest directly to our build. This defines
# the gtest and gtest_main targets.
add_subdirectory(${CMAKE_CURRENT_BINARY_DIR}/googletest-src
        ${CMAKE_CURRENT_BINARY_DIR}/googletest-build
        EXCLUDE_FROM_ALL)

endif ()
