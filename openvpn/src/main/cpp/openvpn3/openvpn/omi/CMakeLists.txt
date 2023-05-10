if (NOT WIN32)
  return()
endif()

option(CLI_OVPNDCOWIN "Build ovpncli with ovpn-dco-win driver support" OFF)

add_executable(omicliagent openvpn.cpp)
add_core_dependencies(omicliagent)
add_json_library(omicliagent)
target_compile_definitions(omicliagent PRIVATE -DOPENVPN_COMMAND_AGENT -DOVPNAGENT_DISABLE_PATH_CHECK)

add_executable(omicli openvpn.cpp)
add_core_dependencies(omicli)

if (CLI_OVPNDCOWIN)
  target_compile_definitions(omicliagent PRIVATE ENABLE_OVPNDCOWIN)
  target_compile_definitions(omicli PRIVATE ENABLE_OVPNDCOWIN)
  target_link_libraries(omicliagent "bcrypt.lib")
  target_link_libraries(omicli "bcrypt.lib")
endif ()
