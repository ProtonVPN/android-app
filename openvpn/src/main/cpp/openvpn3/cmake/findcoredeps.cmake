cmake_minimum_required(VERSION 3.13...3.28)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

set(CORE_DIR ${CMAKE_CURRENT_LIST_DIR}/..)

set(DEP_DIR ${CORE_DIR}/../deps CACHE PATH "Dependencies")
option(USE_MBEDTLS "Use mbed TLS instead of OpenSSL")

option(USE_WERROR "Treat compiler warnings as errors (-Werror)")
option(USE_WCONVERSION "Enable -Wconversion")

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
    find_package(PkgConfig REQUIRED)
    if (${USE_MBEDTLS})
        # mbedtls3.6 for Fedora 41+
        pkg_search_module(mbedTLS IMPORTED_TARGET mbedtls3.6 mbedtls)
        if (mbedTLS_FOUND)
            # Only added as Requires.Private, so we need to look them up ourselves
            pkg_search_module(mbedCrypto REQUIRED IMPORTED_TARGET mbedcrypto3.6 mbedcrypto)
            pkg_search_module(mbedX509 REQUIRED IMPORTED_TARGET mbedx5093.6 mbedx509)
            set(SSL_LIBRARY PkgConfig::mbedTLS PkgConfig::mbedCrypto PkgConfig::mbedX509)
        else ()
            # mbedTLS 2.x doesn't have pkg-config files
            find_package(mbedTLS REQUIRED)
            set(SSL_LIBRARY mbedTLS::mbedTLS)
        endif ()
        target_compile_definitions(${target} PRIVATE -DUSE_MBEDTLS)
    else ()
        pkg_search_module(OpenSSL REQUIRED IMPORTED_TARGET openssl)
        SET(SSL_LIBRARY PkgConfig::OpenSSL)
        target_compile_definitions(${target} PRIVATE -DUSE_OPENSSL)
    endif ()

    target_link_libraries(${target} ${SSL_LIBRARY})
endfunction()


function(add_core_dependencies target)
    # It would be nice if we could just do organise the files that make up the OpenVPN3 core library in
    # a static library called openvpn3 or similar and be able to do
    #      target_link_libraries(${target} openvpn3)
    # here.
    #
    # Unfortunately, too much currently depends on per-target compile flags and defines like #define OPENVPN_LOG
    # that require compilation of all core files as part of the target that uses the core library.
    #
    # If even with this approach of adding some extra files to the sources of a target, we need to careful that
    # we are depending on definitions that are can be defined differently in another compilation unit.
    #
    # Until we refactor these problematic defines to be in a common header (like config.h in autoconf land)
    # or in set then for the whole target in the cmake files (instead of using #define xy in the top of a cpp file)
    # we have to live with this restriction.
    #
    # The unit test work around this problem by always including test_common.h as very first include in every
    # file that double as a config.h equivalent.
    add_corelibrary_dependencies(${target})
    target_sources(${target} PRIVATE ${CORE_DIR}/openvpn/crypto/data_epoch.cpp)
endfunction()

function(add_corelibrary_dependencies target)
    set(PLAT ${OPENVPN_PLAT})

    target_include_directories(${target} PRIVATE ${CORE_DIR})

    target_compile_definitions(${target} PRIVATE
            -DASIO_STANDALONE
            -DUSE_ASIO
            -DHAVE_LZ4
            #-DMBEDTLS_DEPRECATED_REMOVED  # with mbed TLS 3.0 we currently still need the deprecated APIs
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
            target_link_options(${target} PRIVATE -Werror)
        endif ()
    endif ()

    if (MSVC)
        # C4200: nonstandard extension used : zero-sized array in struct/union
        # C4146: unary minus operator applied to unsigned type, result still unsigned
        target_compile_options(${target} PRIVATE /W3 /wd4200 /wd4146)
    else()
        target_compile_options(${target} PRIVATE -Wall -Wsign-compare -Wnon-virtual-dtor)
        if (USE_WCONVERSION)
            target_compile_options(${target} PRIVATE -Wconversion -Wno-sign-conversion)
        endif()
        if (CMAKE_CXX_COMPILER_ID STREQUAL "GNU")
            # disable noisy warnings
            target_compile_options(${target} PRIVATE -Wno-maybe-uninitialized)
            # https://gcc.gnu.org/bugzilla/show_bug.cgi?id=105329
            if (CMAKE_CXX_COMPILER_VERSION MATCHES "^12")
                target_compile_options(${target} PRIVATE -Wno-restrict)
            endif()
        endif()
        if (CMAKE_CXX_COMPILER_ID STREQUAL "Clang")
            # display all warnings
            target_compile_options(${target} PRIVATE -ferror-limit=0)
        endif()
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

function (add_libcap target)
    find_package(PkgConfig REQUIRED)
    pkg_search_module(Libcap REQUIRED IMPORTED_TARGET libcap)
    target_link_libraries(${target} PkgConfig::Libcap)
endfunction ()
