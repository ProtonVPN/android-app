cmake_minimum_required(VERSION 3.5)

project(omicli)
set(CMAKE_CXX_STANDARD 14)

include(findcoredeps)

add_executable(omicliagent openvpn.cpp)
add_core_dependencies(omicliagent)
add_json_library(omicliagent)
target_compile_definitions(omicliagent PRIVATE -DOPENVPN_COMMAND_AGENT -DOVPNAGENT_DISABLE_PATH_CHECK)

add_executable(omicli openvpn.cpp)
add_core_dependencies(omicli)
