include(FetchContent)

set(RC_ENABLE_GTEST
    ON
    CACHE BOOL "Rapidcheck GTest Support" FORCE)
FetchContent_Declare(
  rapidcheck
  GIT_REPOSITORY https://github.com/emil-e/rapidcheck.git
  GIT_TAG ff6af6fc683159deb51c543b065eba14dfcf329b # master Dec 14, 2023
)

if(MSVC)
  find_package(GTest CONFIG REQUIRED)
  set(GTEST_LIB GTest::gtest_main)
else()
  set(GTEST_LIB gtest_main)
  if(NOT OVPN_GTEST_VERSION)
    # renovate: datasource=github-releases depName=google/googletest
    set(OVPN_GTEST_VERSION v1.16.0)
  endif()

  FetchContent_Declare(
    googletest
    GIT_REPOSITORY https://github.com/google/googletest.git
    GIT_TAG ${OVPN_GTEST_VERSION})
  FetchContent_MakeAvailable(googletest)
endif()

FetchContent_MakeAvailable(rapidcheck)
