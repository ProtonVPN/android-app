cmake_minimum_required(VERSION 3.5)
cmake_policy(SET CMP0048 NEW)

project(OpenVPN3-core VERSION 3)

set(CMAKE_MODULE_PATH ${CMAKE_CURRENT_SOURCE_DIR}/cmake
                      ${CMAKE_MODULE_PATH})
set(CMAKE_CXX_STANDARD 11)

include(findcoredeps)

add_subdirectory(client)
add_subdirectory(test/unittests)
add_subdirectory(test/ovpncli)
add_subdirectory(test/ssl)


if (WIN32)
    add_subdirectory(openvpn/omi)
    add_subdirectory(openvpn/ovpnagent/win)
endif ()

if (APPLE)
    add_subdirectory(openvpn/ovpnagent/mac)
endif ()

