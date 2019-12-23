option(WITH_STATIC_LIB "Build with a static library" OFF)
option(WITH_CMOCKERY_SUPPORT "Install a cmockery header" OFF)
option(UNIT_TESTING "Build with unit testing" OFF)

if (UNIT_TESTING)
    set(WITH_STATIC_LIB ON)
endif()
