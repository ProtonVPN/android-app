#header-only library
include(vcpkg_common_functions)

vcpkg_from_github(
    OUT_SOURCE_PATH SOURCE_PATH
    REPO chriskohlhoff/asio
    REF asio-1-18-1
    SHA512 c84e6fca448ed419a976756840f3f4543291a5a7d4f62d4de7c06945b2cd9ececca6633049ad5e36367d60f67a4f2735be017445514ae9fa9497d4af2a4d48f8
    HEAD_REF master
    PATCHES
        ..\\..\\asio\\patches\\0001-Added-Apple-NAT64-support-when-both-ASIO_HAS_GETADDR.patch
        ..\\..\\asio\\patches\\0002-Added-randomize-method-to-asio-ip-tcp-resolver-resul.patch
        ..\\..\\asio\\patches\\0003-Added-user-code-hook-async_connect_post_open-to-be-c.patch
        ..\\..\\asio\\patches\\0004-error_code.ipp-Use-English-for-Windows-error-message.patch
)

# Always use "ASIO_STANDALONE" to avoid boost dependency
file(READ "${SOURCE_PATH}/asio/include/asio/detail/config.hpp" _contents)
string(REPLACE "defined(ASIO_STANDALONE)" "!defined(VCPKG_DISABLE_ASIO_STANDALONE)" _contents "${_contents}")
file(WRITE "${SOURCE_PATH}/asio/include/asio/detail/config.hpp" "${_contents}")

# CMake install
file(COPY ${CMAKE_CURRENT_LIST_DIR}/CMakeLists.txt DESTINATION ${SOURCE_PATH})
vcpkg_configure_cmake(
    SOURCE_PATH ${SOURCE_PATH}
    PREFER_NINJA
)
vcpkg_install_cmake()

vcpkg_fixup_cmake_targets(CONFIG_PATH "share/asio")
file(INSTALL
    ${CMAKE_CURRENT_LIST_DIR}/asio-config.cmake
    DESTINATION ${CURRENT_PACKAGES_DIR}/share/asio/
)
file(REMOVE_RECURSE ${CURRENT_PACKAGES_DIR}/debug)

# Handle copyright
file(INSTALL ${SOURCE_PATH}/asio/LICENSE_1_0.txt DESTINATION ${CURRENT_PACKAGES_DIR}/share/${PORT} RENAME copyright)

