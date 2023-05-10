cmake_minimum_required(VERSION 3.10)

set(CMAKE_CXX_STANDARD 17)

#cmake_policy(SET CMP0079 NEW)

set(CORE_DIR ${CMAKE_CURRENT_LIST_DIR}/..)


set(DEP_DIR ${CORE_DIR}/../deps CACHE PATH "Dependencies")
option(USE_MBEDTLS "Use mbed TLS instead of OpenSSL")

option(USE_WERROR "Treat compiler warnings as errors (-Werror)")

if (DEFINED ENV{DEP_DIR})
    message("Overriding DEP_DIR setting with environment variable $ENV{DEP_DIR}")
    set(DEP_DIR $ENV{DEP_DIR})
endif ()

# Include our DEP_DIR in path used to find libraries

if (APPLE)
    set(OPENVPN_PLAT osx)
elseif (WIN32)
    set(OPENVPN_PLAT amd64)
else ()
    set(OPENVPN_PLAT linux)
endif ()

function(add_ssl_library target)
    if (${USE_MBEDTLS})
        find_package(mbedTLS REQUIRED)
        set(SSL_LIBRARY mbedTLS::mbedTLS)
        target_compile_definitions(${target} PRIVATE -DUSE_MBEDTLS)
    else ()
        find_package(OpenSSL REQUIRED)
        SET(SSL_LIBRARY OpenSSL::SSL)
        target_compile_definitions(${target} PRIVATE -DUSE_OPENSSL)
    endif ()

    target_link_libraries(${target} ${SSL_LIBRARY})
endfunction()


function(add_core_dependencies target)
    set(PLAT ${OPENVPN_PLAT})

    target_include_directories(${target} PRIVATE ${CORE_DIR})

    target_compile_definitions(${target} PRIVATE
            -DASIO_STANDALONE
            -DUSE_ASIO
            -DHAVE_LZ4
            -DMBEDTLS_DEPRECATED_REMOVED
            )

    if (WIN32)
        target_compile_definitions(${target} PRIVATE
                -D_WIN32_WINNT=0x0600
                -DTAP_WIN_COMPONENT_ID=tap0901
                -D_CRT_SECURE_NO_WARNINGS
                -DASIO_DISABLE_LOCAL_SOCKETS
                )
        set(EXTRA_LIBS fwpuclnt.lib iphlpapi.lib wininet.lib setupapi.lib rpcrt4.lib wtsapi32.lib)
        if ("${CMAKE_GENERATOR_PLATFORM}" STREQUAL "ARM64")
            # by some reasons CMake doesn't add those for ARM64
            list(APPEND EXTRA_LIBS advapi32.lib Ole32.lib Shell32.lib)
        endif ()

        if (MSVC)
            target_compile_options(${target} PRIVATE "/bigobj")
        else ()
            find_package(Threads REQUIRED)
            target_compile_options(${target} PRIVATE "-Wa,-mbig-obj")
            list(APPEND EXTRA_LIBS ws2_32 wsock32 ${CMAKE_THREAD_LIBS_INIT})
            list(APPEND CMAKE_PREFIX_PATH
              ${DEP_DIR}/asio/asio
              ${DEP_DIR}
            )
        endif ()
    else ()
        list(APPEND CMAKE_PREFIX_PATH
                ${DEP_DIR}/asio/asio
                ${DEP_DIR}/lz4/lz4-${PLAT}
                ${DEP_DIR}/mbedtls/mbedtls-${PLAT}
                )
        list(APPEND CMAKE_LIBRARY_PATH
                ${DEP_DIR}/mbedtls/mbedtls-${PLAT}/library
                )
    endif ()

    # asio should go first since some of our code requires
    # a patched version. So we want to prefer its include
    # directories.
    find_package(asio REQUIRED)
    target_link_libraries(${target} asio::asio)

    find_package(lz4 REQUIRED)
    target_link_libraries(${target} lz4::lz4)

    add_ssl_library(${target})

    if (APPLE)
        find_library(coreFoundation CoreFoundation)
        find_library(iokit IOKit)
        find_library(coreServices CoreServices)
        find_library(systemConfiguration SystemConfiguration)
        target_link_libraries(${target} ${coreFoundation} ${iokit} ${coreServices} ${systemConfiguration} ${lz4})
    endif()

    if(UNIX)
        target_link_libraries(${target} pthread)
    endif()

    target_link_libraries(${target} ${EXTRA_LIBS})

    if (USE_WERROR)
        if (MSVC)
            target_compile_options(${target} PRIVATE /WX)
        else ()
            target_compile_options(${target} PRIVATE -Werror)
        endif ()
    endif ()

    if (MSVC)
        # I think this is too much currently
        # target_compile_options(${target} PRIVATE /W4)
    else()
        target_compile_options(${target} PRIVATE -Wall -Wsign-compare)
    endif()

endfunction()

function (add_json_library target)
  find_package(jsoncpp CONFIG)
  if (jsoncpp_FOUND AND TARGET JsonCpp::JsonCpp)
    target_link_libraries(${target} JsonCpp::JsonCpp)
    target_compile_definitions(${target} PRIVATE -DHAVE_JSONCPP)
  else()
    find_package(PkgConfig REQUIRED)
    if (MINGW)
      #  due to cmake bug, APPEND doesn't work for mingw
      # https://github.com/Kitware/CMake/commit/f92a4b23994fa7516f16fbb5b3c02caa07534b3f
      set(CMAKE_PREFIX_PATH ${DEP_DIR})
    endif ()
    pkg_check_modules(JSONCPP REQUIRED IMPORTED_TARGET jsoncpp)
    target_link_libraries(${target} PkgConfig::JSONCPP)
    target_compile_definitions(${target} PRIVATE -DHAVE_JSONCPP)
  endif ()
endfunction ()
