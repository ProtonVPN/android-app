find_path(XXHASH_INCLUDE_DIR NAMES xxhash.h)

include(FindPackageHandleStandardArgs)
FIND_PACKAGE_HANDLE_STANDARD_ARGS(
  xxHash DEFAULT_MSG
  XXHASH_INCLUDE_DIR
  )

if(XXHASH_INCLUDE_DIR AND NOT TARGET xxHash::xxhash)
    add_library(xxHash::xxhash INTERFACE IMPORTED)
    set_target_properties(xxHash::xxhash PROPERTIES
      INTERFACE_INCLUDE_DIRECTORIES "${XXHASH_INCLUDE_DIR}")
endif()

mark_as_advanced(XXHASH_INCLUDE_DIR)
