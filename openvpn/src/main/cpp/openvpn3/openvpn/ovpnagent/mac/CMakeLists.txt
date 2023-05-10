if (NOT APPLE)
  return()
endif()

add_executable(agent_macos ovpnagent.cpp)
add_core_dependencies(agent_macos)
add_json_library(agent_macos)

# Avoid deprecated warning for using daemon
target_compile_options(agent_macos PRIVATE -Wno-deprecated-declarations)
