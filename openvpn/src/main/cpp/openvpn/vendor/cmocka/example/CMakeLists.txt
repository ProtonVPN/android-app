project(cmocka-examples C)

include_directories(
    ${CMAKE_BINARY_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMOCKA_PUBLIC_INCLUDE_DIRS}
)

set_source_files_properties(
    calculator.c
    allocate_module.c
    assert_module.c
    PROPERTIES
        COMPILE_DEFINITIONS UNIT_TESTING=1)


if (WIN32 OR CYGWIN OR MINGW)
    set(CMOCKA_DLL_LIB ${CMAKE_BINARY_DIR}/src)
    file(TO_NATIVE_PATH "${CMOCKA_DLL_PATH}" CMOCKA_DLL_PATH)
    set(DLL_PATH_ENV "${CMOCKA_DLL_PATH};$ENV{PATH}")

    #
    # IMPORTANT NOTE: The set_tests_properties(), below, internally
    # stores its name/value pairs with a semicolon delimiter.
    # because of this we must protect the semicolons in the path
    #
    string(REPLACE ";" "\\;" DLL_PATH_ENV "${DLL_PATH_ENV}")
endif (WIN32 OR CYGWIN OR MINGW)


### The most simple test
add_executable(simple_test simple_test.c)
target_link_libraries(simple_test ${CMOCKA_SHARED_LIBRARY})

add_test(simple_test ${CMAKE_CURRENT_BINARY_DIR}/simple_test)
if (WIN32 OR CYGWIN OR MINGW)
    set_tests_properties(simple_test PROPERTIES ENVIRONMENT "PATH=${DLL_PATH_ENV}")
endif (WIN32 OR CYGWIN OR MINGW)

### Calulator test
#TODO investigate dll jmp issue on MinGW
if (NOT MINGW OR WITH_STATIC_LIB)
    add_executable(calculator_test calculator.c calculator_test.c)
    add_test(calculator_test ${CMAKE_CURRENT_BINARY_DIR}/calculator_test)
    if (WIN32 OR CYGWIN)
        set_tests_properties(calculator_test PROPERTIES ENVIRONMENT "PATH=${DLL_PATH_ENV}")
    endif (WIN32 OR CYGWIN)

    if (MINGW)
        target_link_libraries(calculator_test ${CMOCKA_STATIC_LIBRARY})
    else (MINGW)
        target_link_libraries(calculator_test ${CMOCKA_SHARED_LIBRARY})
    endif (MINGW)

    if (WIN32 OR CYGWIN OR MINGW)
        set_tests_properties(calculator_test PROPERTIES ENVIRONMENT "PATH=${DLL_PATH_ENV}")
    endif (WIN32 OR CYGWIN OR MINGW)
endif (NOT MINGW OR WITH_STATIC_LIB)

### Allocate module test
add_executable(allocate_module_test allocate_module.c allocate_module_test.c)
target_link_libraries(allocate_module_test ${CMOCKA_SHARED_LIBRARY})

# This is a test that should detect leaks and overflows and will fail for that
add_test(allocate_module_test ${CMAKE_CURRENT_BINARY_DIR}/allocate_module_test)
if (WIN32 OR CYGWIN OR MINGW)
    set_tests_properties(allocate_module_test PROPERTIES ENVIRONMENT "PATH=${DLL_PATH_ENV}")
endif (WIN32 OR CYGWIN OR MINGW)

set_tests_properties(
    allocate_module_test
        PROPERTIES
            WILL_FAIL 1
)

### Assert macro test
add_executable(assert_macro_test assert_macro.c assert_macro_test.c)
target_link_libraries(assert_macro_test ${CMOCKA_SHARED_LIBRARY})

add_test(assert_macro_test ${CMAKE_CURRENT_BINARY_DIR}/assert_macro_test)
set_tests_properties(
    assert_macro_test
        PROPERTIES
        WILL_FAIL 1
)
if (WIN32 OR CYGWIN OR MINGW)
    set_tests_properties(assert_macro_test PROPERTIES ENVIRONMENT "PATH=${DLL_PATH_ENV}")
endif (WIN32 OR CYGWIN OR MINGW)

### Assert module test
add_executable(assert_module_test assert_module.c assert_module_test.c)
target_link_libraries(assert_module_test ${CMOCKA_SHARED_LIBRARY})

add_test(assert_module_test ${CMAKE_CURRENT_BINARY_DIR}/assert_module_test)
set_tests_properties(
    assert_module_test
        PROPERTIES
        WILL_FAIL 1
)
if (WIN32 OR CYGWIN OR MINGW)
    set_tests_properties(assert_module_test PROPERTIES ENVIRONMENT "PATH=${DLL_PATH_ENV}")
endif (WIN32 OR CYGWIN OR MINGW)

### Customer database test
add_executable(customer_database_test customer_database.c customer_database_test.c)
target_link_libraries(customer_database_test ${CMOCKA_SHARED_LIBRARY})

add_test(customer_database_test ${CMAKE_CURRENT_BINARY_DIR}/customer_database_test)
if (WIN32 OR CYGWIN OR MINGW)
    set_tests_properties(customer_database_test PROPERTIES ENVIRONMENT "PATH=${DLL_PATH_ENV}")
endif (WIN32 OR CYGWIN OR MINGW)

### Key Value Test
add_executable(key_value_test key_value.c key_value_test.c)
target_link_libraries(key_value_test ${CMOCKA_SHARED_LIBRARY})

add_test(key_value_test ${CMAKE_CURRENT_BINARY_DIR}/key_value_test)
if (WIN32 OR CYGWIN OR MINGW)
    set_tests_properties(key_value_test PROPERTIES ENVIRONMENT "PATH=${DLL_PATH_ENV}")
endif (WIN32 OR CYGWIN OR MINGW)

### Product database test
add_executable(product_database_test product_database.c product_database_test.c)
target_link_libraries(product_database_test ${CMOCKA_SHARED_LIBRARY})

add_test(product_database_test ${CMAKE_CURRENT_BINARY_DIR}/product_database_test)
set_tests_properties(
    product_database_test
        PROPERTIES
        PASS_REGULAR_EXPRESSION
        "\\[  FAILED  \\] 2 test"
)
if (WIN32 OR CYGWIN OR MINGW)
    set_tests_properties(product_database_test PROPERTIES ENVIRONMENT "PATH=${DLL_PATH_ENV}")
endif (WIN32 OR CYGWIN OR MINGW)

# TODO Execute "$CMAKE_LINKER --help" and check for --wrap
if (${CMAKE_C_COMPILER_ID} MATCHES "(GNU|Clang)" AND NOT APPLE)
    add_subdirectory(chef_wrap)
endif()
