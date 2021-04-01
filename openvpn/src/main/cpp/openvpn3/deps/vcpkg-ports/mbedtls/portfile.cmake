include(vcpkg_common_functions)

set(VCPKG_LIBRARY_LINKAGE static)

vcpkg_from_github(
    OUT_SOURCE_PATH SOURCE_PATH
    REPO ARMmbed/mbedtls
    REF mbedtls-2.7.12
    SHA512 bfad5588804e52827ecba81ca030fe570c9772f633fbf470d71a781db4366541da69b85ee10941bf500a987c4da825caa049afc2c0e6ec0ecc55d50efd74e5a6
    HEAD_REF master
    PATCHES
        ..\\..\\mbedtls\\patches\\0001-relax-x509-date-format-check.patch
        ..\\..\\mbedtls\\patches\\0002-Enable-allowing-unsupported-critical-extensions-in-r.patch
        ..\\..\\mbedtls\\patches\\0003-fix-gcc-android-build.patch
)

vcpkg_configure_cmake(
    SOURCE_PATH ${SOURCE_PATH}
    PREFER_NINJA
    OPTIONS
        -DENABLE_TESTING=OFF
        -DENABLE_PROGRAMS=OFF
)

vcpkg_install_cmake()

file(REMOVE_RECURSE ${CURRENT_PACKAGES_DIR}/debug/include)

file(INSTALL ${SOURCE_PATH}/LICENSE DESTINATION ${CURRENT_PACKAGES_DIR}/share/mbedtls RENAME copyright)

vcpkg_copy_pdbs()
