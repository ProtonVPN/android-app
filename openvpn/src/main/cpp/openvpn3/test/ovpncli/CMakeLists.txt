
option(CLI_NULLTUN "Build ovpncli variant with null tun" OFF)
option(CLI_KOVPN "Build ovpncli variant with kovpn kernel moduke support" OFF)
option(CLI_OVPNDCO "Build ovpncli with ovpn-dco kernel module support" OFF)
option(CLI_OVPNDCOWIN "Build ovpncli with ovpn-dco-win driver support" OFF)

set(KOVPN_SOURCE "${CORE_DIR}/../kovpn" CACHE PATH "ovpn-dco kernel module source tree")
set(OVPN_DCO_SOURCE "${CORE_DIR}/../ovpn-dco" CACHE PATH "ovpn-dco kernel module source tree")

add_executable(ovpncli cli.cpp)

add_core_dependencies(ovpncli)

if (${CLI_NULLTUN})
    add_executable(ovpnclinull cli.cpp)
    add_core_dependencies(ovpnclinull)
    target_compile_definitions(ovpnclinull PRIVATE -DOPENVPN_FORCE_TUN_NULL)
endif ()

if (${CLI_KOVPN})
    add_executable(ovpnclikovpn cli.cpp)
    add_core_dependencies(ovpnclikovpn)

    target_compile_definitions(ovpnclikovpn PRIVATE -DENABLE_KOVPN)

    include(kovpn)
    add_kovpn_dependency(ovpnclikovpn)
    target_include_directories(ovpnclikovpn PRIVATE ${KOVPN_SOURCE})
endif()

if (${CLI_OVPNDCO})
    target_compile_definitions(ovpncli PRIVATE -DENABLE_OVPNDCO)

    find_package(PkgConfig)
    pkg_search_module(LIBNL REQUIRED libnl-genl-3.0)

    target_include_directories(ovpncli PRIVATE ${OVPN_DCO_SOURCE}/include/uapi ${LIBNL_INCLUDE_DIRS})
    target_link_libraries(ovpncli ${LIBNL_LIBRARIES})
endif()

if (WIN32)
    add_executable(ovpncliagent cli.cpp)
    add_core_dependencies(ovpncliagent)
    add_json_library(ovpncliagent)
    target_compile_definitions(ovpncliagent PRIVATE OPENVPN_COMMAND_AGENT
                                                    OVPNAGENT_DISABLE_PATH_CHECK)

    if (${CLI_OVPNDCOWIN})
        find_path(OVPN_DCO_WIN_INCLUDE_DIRS "ovpn-dco-win/uapi.h")

        target_compile_definitions(ovpncliagent PRIVATE ENABLE_OVPNDCOWIN)
        target_include_directories(ovpncliagent PRIVATE ${OVPN_DCO_WIN_INCLUDE_DIRS})

        target_compile_definitions(ovpncli PRIVATE ENABLE_OVPNDCOWIN)
        target_include_directories(ovpncli PRIVATE ${OVPN_DCO_WIN_INCLUDE_DIRS})
    endif()
endif ()

if (APPLE)
    add_executable(ovpncliagent cli.cpp)
    add_core_dependencies(ovpncliagent)
    add_json_library(ovpncliagent)
    target_compile_definitions(ovpncliagent PRIVATE -DOPENVPN_COMMAND_AGENT)
endif ()
