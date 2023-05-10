include(findcoredeps)
include(findswigdeps)

if (${BUILD_SWIG_LIB})
    add_custom_command(
            OUTPUT ovpncli_wrap.cxx ovpncli_wrap.h
            COMMENT "Generating ovpncli Python swig files"
            COMMAND ${SWIG_EXECUTABLE} -c++ -python -threads -DSWIG_PYTHON_2_UNICODE -outcurrentdir -I${CORE_DIR} ${CMAKE_CURRENT_SOURCE_DIR}/ovpncli.i
            DEPENDS ovpncli.i
    )

    add_library(ovpnclilib SHARED
            ovpncli.cpp
            ovpncli_wrap.cxx
            ovpncli_wrap.h
            )

    add_core_dependencies(ovpnclilib)
    target_link_libraries(ovpnclilib ${PYTHON_LIBRARIES})
    target_include_directories(ovpnclilib PRIVATE ${PYTHON_INCLUDE_DIRS} ${CMAKE_CURRENT_SOURCE_DIR})

    # Use proper python library name to generate _ovpncli.so/dylib/dll
    set_target_properties(ovpnclilib PROPERTIES OUTPUT_NAME "_ovpncli")
    set_target_properties(ovpnclilib PROPERTIES PREFIX "")

    if (NOT WIN32)
        # Swig generates code with deprecated python declarations
        set_source_files_properties(ovpncli_wrap.cxx PROPERTIES COMPILE_FLAGS "-Wno-deprecated-declarations -Wno-sometimes-uninitialized -Wno-class-memaccess -Wno-unknown-warning-option")
    endif()
endif ()
