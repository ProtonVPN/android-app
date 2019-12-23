project(tests C)

include_directories(
  ${CMAKE_BINARY_DIR}
  ${CMAKE_CURRENT_SOURCE_DIR}
  ${CMAKE_SOURCE_DIR}/include
)

set(CMOCKA_TESTS
    test_alloc
    test_group_setup_assert
    test_group_setup_fail
    test_fixtures
    test_group_fixtures
    test_assert_macros
    test_assert_macros_fail
    test_exception_handler
    test_basics
    test_skip
    test_setup_fail
    test_ordering
    test_ordering_fail
    test_returns
    test_returns_fail)

foreach(_CMOCKA_TEST ${CMOCKA_TESTS})
    add_cmocka_test(${_CMOCKA_TEST} ${_CMOCKA_TEST}.c ${CMOCKA_STATIC_LIBRARY})
endforeach()

### Special Cases
if (${CMAKE_C_COMPILER_ID} MATCHES "(GNU|Clang)")
    set_source_files_properties(test_cmockery.c PROPERTIES COMPILE_FLAGS "-Wno-deprecated-declarations")
endif()
add_cmocka_test(test_cmockery test_cmockery.c ${CMOCKA_STATIC_LIBRARY})

### Exceptions

# test_skip
set_tests_properties(
    test_skip
        PROPERTIES
        PASS_REGULAR_EXPRESSION
        "\\[  SKIPPED \\] test_check_skip"
)

# test_assert_macros_fail
set_tests_properties(
    test_assert_macros_fail
        PROPERTIES
        PASS_REGULAR_EXPRESSION
        "\\[  FAILED  \\] 1 test"
)

# test_ordering ensure proper failures
set_tests_properties(
    test_ordering_fail
        PROPERTIES
        PASS_REGULAR_EXPRESSION
        "\\[  FAILED  \\] 7 test"
)

# test_returns_fail ensure proper failures
set_tests_properties(
    test_returns_fail
        PROPERTIES
        PASS_REGULAR_EXPRESSION
        "\\[  FAILED  \\] 3 test"
)

# test_exception_handler
if (WIN32)
    set_tests_properties(
        test_exception_handler
            PROPERTIES
            PASS_REGULAR_EXPRESSION
            "EXCEPTION_ACCESS_VIOLATION occurred at")
else()
    set_tests_properties(
        test_exception_handler
            PROPERTIES
            PASS_REGULAR_EXPRESSION
            "Test failed with exception: (Segmentation fault|Segmentation Fault|11)"
    )
endif (WIN32)

set_tests_properties(
    test_setup_fail
        PROPERTIES
        WILL_FAIL
        1
)

set_tests_properties(
    test_group_setup_assert
        PROPERTIES
        WILL_FAIL
        1
)

set_tests_properties(
    test_group_setup_fail
        PROPERTIES
        WILL_FAIL
        1
)

add_test (test_setup_fail_1_failed test_setup_fail)
set_tests_properties(
    test_setup_fail_1_failed
        PROPERTIES
        PASS_REGULAR_EXPRESSION
        "\\[  ERROR   \\] int_test_ignored"
)

add_test (test_setup_fail_1_passed test_setup_fail)
set_tests_properties(
    test_setup_fail_1_passed
        PROPERTIES
        PASS_REGULAR_EXPRESSION
        "\\[  PASSED  \\] 1 test\\(s\\)."
)

add_test (test_setup_fail_match_failed test_setup_fail)
set_tests_properties(
    test_setup_fail_match_failed
        PROPERTIES
        PASS_REGULAR_EXPRESSION
        "\\[  ERROR   \\] int_test_ignored"
)

add_test (test_setup_fail_match_passed test_setup_fail)
set_tests_properties(
    test_setup_fail_match_passed
        PROPERTIES
        PASS_REGULAR_EXPRESSION
        "\\[       OK \\] int_test_success"
)

### Output formats

# test output of success, failure, skip, fixture failure
set(OUTPUT_TESTS
    test_basics
    test_assert_macros_fail
    test_skip
    test_setup_fail)

set(TEST_OUTPUT_FMTS
    tap
    subunit
    xml)

set(test_basics_tap_out
    "^\t1\\.\\.2[ \n\r]+\tok 1 - null_test_success[ \n\r]+\tok 2 - int_test_success[ \n\r]+ok - tests")
set(test_assert_macros_fail_tap_out
    "^\t1\\.\\.1[ \n\r]+\tnot ok 1 - test_assert_return_code_fail[ \n\r]+\t#[^\n\r]+[\n\r]\t#[^\n\r]+[\n\r]not ok - tests")

set(test_skip_tap_out
    "not ok 1 # SKIP")
set(test_setup_fail_tap_out
    "not ok 1 - int_test_ignored Could not run the test - check test fixtures")

set(test_basics_subunit_out
    "^test: null_test_success[ \n\r]+success: null_test_success")
set(test_assert_macros_fail_subunit_out
    "failure: test_assert_return_code_fail \\[")
set(test_skip_subunit_out
    "^test: test_check_skip[ \n\r]+skip: test_check_skip")
set(test_setup_fail_subunit_out
    "error: int_test_ignored \\[ Could not run the test - check test fixtures \\]")

set(test_basics_xml_out
    "<testsuite name=\"tests\" time=\"[0-9.]+\" tests=\"2\" failures=\"0\" errors=\"0\" skipped=\"0\" >[ \n\r]+<testcase name=\"null_test_success\" time=\"[0-9.]+\" >.*</testcase>")
set(test_assert_macros_fail_xml_out
    "<testcase name=\"test_assert_return_code_fail\" time=\"[0-9.]+\" >[ \n\r]+<failure>")
set(test_skip_xml_out
    "<testcase name=\"test_check_skip\" time=\"[0-9.]+\" >[ \n\r]+<skipped/>")
set(test_setup_fail_xml_out
    "<testcase name=\"int_test_ignored\" time=\"[0-9.]+\" >[ \n\r]+<failure><!\\[CDATA\\[Test setup failed\\]\\]></failure>")

foreach(_TEST_OUTPUT_FMT ${TEST_OUTPUT_FMTS})
    foreach(_OUTPUT_TEST ${OUTPUT_TESTS})
        set(TEST_NAME ${_OUTPUT_TEST}_${_TEST_OUTPUT_FMT})
        add_test(${TEST_NAME} ${_OUTPUT_TEST})

        set_property(
            TEST
            ${TEST_NAME}
            PROPERTY
                ENVIRONMENT CMOCKA_MESSAGE_OUTPUT=${_TEST_OUTPUT_FMT}
            )

            set_tests_properties(
                    ${TEST_NAME}
                    PROPERTIES
                    PASS_REGULAR_EXPRESSION
                    ${${TEST_NAME}_out}
            )
    endforeach()
endforeach()
