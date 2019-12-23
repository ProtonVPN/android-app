cmake_minimum_required(VERSION 2.6)
project(POLARSSL C)

enable_testing()

if(CMAKE_COMPILER_IS_GNUCC)
  # JY Added
  set(CMAKE_OSX_ARCHITECTURES "")
  set(CMAKE_OSX_DEPLOYMENT_TARGET "")
  set(CMAKE_OSX_SYSROOT "")
  set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} $ENV{LIB_FPIC} $ENV{LIB_OPT_LEVEL} $ENV{PLATFORM_FLAGS} $ENV{OTHER_COMPILER_FLAGS} -Wall -W -Wdeclaration-after-statement")
  if (NOT "$ENV{GCC_CMD}" STREQUAL "")
    set(CMAKE_C_COMPILER "$ENV{GCC_CMD}")
  endif()
  if (NOT "$ENV{GPP_CMD}" STREQUAL "")
    set(CMAKE_CXX_COMPILER "$ENV{GPP_CMD}")
  endif()
  if (NOT "$ENV{AR_CMD}" STREQUAL "")
    set(CMAKE_AR "$ENV{AR_CMD}")
  endif()
  if (NOT "$ENV{RANLIB_CMD}" STREQUAL "")
    set(CMAKE_RANLIB "$ENV{RANLIB_CMD}")
  endif()

  # JY Commented out
  #set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O2 -Wall -Wextra -W -Wdeclaration-after-statement")
  #set(CMAKE_C_FLAGS_DEBUG "-g3 -O0")
  #set(CMAKE_C_FLAGS_COVERAGE "-g3 -O0 -fprofile-arcs -ftest-coverage -lgcov")
endif(CMAKE_COMPILER_IS_GNUCC)
 
if(CMAKE_BUILD_TYPE STREQUAL "Coverage")
  if(CMAKE_COMPILER_IS_GNUCC)
    set(CMAKE_SHARED_LINKER_FLAGS "-fprofile-arcs -ftest-coverage")
  endif(CMAKE_COMPILER_IS_GNUCC)
endif(CMAKE_BUILD_TYPE STREQUAL "Coverage")

option(USE_PKCS11_HELPER_LIBRARY "Build PolarSSL with the pkcs11-helper library." OFF)

option(ENABLE_ZLIB_SUPPORT "Build PolarSSL with zlib library." OFF)

# JY added
if(MINICRYPTO)
  if(MINICRYPTO_DIR)
    add_library(minicrypto STATIC IMPORTED)
    set_property(TARGET minicrypto PROPERTY IMPORTED_LOCATION "${MINICRYPTO_DIR}/libminicrypto.a")
  endif()
  if(OSSLCRYPTO_DIR)
    add_library(crypto STATIC IMPORTED)
    set_property(TARGET crypto PROPERTY IMPORTED_LOCATION "${OSSLCRYPTO_DIR}/libcrypto.a")
  endif()
endif()

# include full testing infrastructure (JY added)
if(ENABLE_TESTING)
  enable_testing()
endif()

if(LIB_INSTALL_DIR)
else()
set(LIB_INSTALL_DIR lib)
endif()

include_directories(include/)

if(ENABLE_ZLIB_SUPPORT)
  find_package(ZLIB)

  if(ZLIB_FOUND)
    include_directories(ZLIB_INCLUDE_DIR)
  endif(ZLIB_FOUND)
endif(ENABLE_ZLIB_SUPPORT)

add_subdirectory(library)
add_subdirectory(include)

# include full testing infrastructure (JY modified)
if(ENABLE_TESTING)
  if(CMAKE_COMPILER_IS_GNUCC)
    add_subdirectory(tests)
  endif(CMAKE_COMPILER_IS_GNUCC)
  add_subdirectory(programs)
endif()

ADD_CUSTOM_TARGET(apidoc
                  COMMAND doxygen doxygen/polarssl.doxyfile
                  WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR})
