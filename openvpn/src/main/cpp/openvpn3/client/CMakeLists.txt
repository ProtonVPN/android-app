include(findcoredeps)
include(findswigdeps)

if (BUILD_SWIG_LIB)
    set_property(SOURCE ovpncli.i PROPERTY CPLUSPLUS ON)
    if (NOT MSVC OR CMAKE_CXX_COMPILER_ID MATCHES "Clang")
        # Swig generates code with deprecated python declarations
        set_property(SOURCE ovpncli.i PROPERTY GENERATED_COMPILE_OPTIONS -Wno-deprecated-declarations -Wno-sometimes-uninitialized -Wno-class-memaccess -Wno-unused-function -Wno-unknown-warning-option)
    endif()

    swig_add_library(ovpnclilib
        TYPE SHARED
        LANGUAGE python
        SOURCES ovpncli.cpp ovpncli.i
        )
    set_property(TARGET ovpnclilib PROPERTY SWIG_COMPILE_DEFINITIONS SWIG_PYTHON_2_UNICODE)
    set_property(TARGET ovpnclilib PROPERTY SWIG_COMPILE_OPTIONS -threads)
    set_property(TARGET ovpnclilib PROPERTY SWIG_INCLUDE_DIRECTORIES ${CORE_DIR})

    add_core_dependencies(ovpnclilib)
    target_link_libraries(ovpnclilib ${Python3_LIBRARIES})
    target_include_directories(ovpnclilib PRIVATE ${Python3_INCLUDE_DIRS} ${CMAKE_CURRENT_SOURCE_DIR})

    # Use proper python library name to generate _ovpncli.so/dylib/dll
    set_target_properties(ovpnclilib PROPERTIES OUTPUT_NAME "_ovpncli")
    set_target_properties(ovpnclilib PROPERTIES PREFIX "")

endif ()
