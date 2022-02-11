include(vcpkg_common_functions)

vcpkg_from_github(
    OUT_SOURCE_PATH SOURCE_PATH
    REPO OpenVPN/ovpn-dco-win
    REF 6750053de7ccb1ff627c80544e611929748352e6
    SHA512 ed6b3718cee563ef00955d98922d9a633d0698b13c7b1c34c1a0bbcad974e3cf4636401e169e2e5d642ed39ef3f057574c061abb4c83f12f3e684a85decd965b
    HEAD_REF master
)

file(COPY ${SOURCE_PATH}/uapi.h DESTINATION ${CURRENT_PACKAGES_DIR}/include/ovpn-dco-win)


file(INSTALL
    ${SOURCE_PATH}/COPYRIGHT.MIT
    DESTINATION ${CURRENT_PACKAGES_DIR}/share/ovpn-dco-win RENAME copyright)
