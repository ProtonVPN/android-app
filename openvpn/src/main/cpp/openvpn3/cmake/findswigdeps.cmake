find_package(PythonInterp)
find_package(PythonLibs)

FIND_PACKAGE(SWIG 3.0)

# We test building this library with python instead of java since that is easier to do and both languages should work

if (PYTHONLIBS_FOUND AND SWIG_FOUND)
    if (NOT WIN32)
        set(BUILD_SWIG_LIB TRUE)
    elseif("${CMAKE_EXE_LINKER_FLAGS}" MATCHES "x64")
        set(BUILD_SWIG_LIB TRUE)
    else()
        MESSAGE(INFO " Skipping swig builds on non-x64 Windows: ${CMAKE_EXE_LINKER_FLAGS}")
    endif()
else()
    MESSAGE(INFO " Python libraries or swig not found, skipping swig builds")
endif()
