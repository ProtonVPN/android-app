project(cmocka-headers C)

set(cmocka_HDRS
  cmocka.h
  cmocka_pbc.h
)

install(
  FILES
    ${cmocka_HDRS}
  DESTINATION
    ${INCLUDE_INSTALL_DIR}
  COMPONENT
    headers
)

if (WITH_CMOCKERY_SUPPORT)
    install(
      FILES
        cmockery/cmockery.h
        cmockery/pbc.h
      DESTINATION
        ${INCLUDE_INSTALL_DIR}/cmockery
      COMPONENT
        headers
    )
endif()
