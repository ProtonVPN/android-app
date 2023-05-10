find_path(ASIO_INCLUDE_DIR NAMES asio.hpp)

include(FindPackageHandleStandardArgs)
FIND_PACKAGE_HANDLE_STANDARD_ARGS(
  asio DEFAULT_MSG
  ASIO_INCLUDE_DIR
  )

if(ASIO_INCLUDE_DIR AND NOT TARGET asio::asio)
    add_library(asio::asio INTERFACE IMPORTED)
    set_target_properties(asio::asio PROPERTIES
      INTERFACE_INCLUDE_DIRECTORIES "${ASIO_INCLUDE_DIR}")
endif()

mark_as_advanced(ASIO_INCLUDE_DIR)
