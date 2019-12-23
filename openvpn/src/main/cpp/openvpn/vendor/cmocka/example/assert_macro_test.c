/*
 * Copyright 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include <stdarg.h>
#include <stddef.h>
#include <setjmp.h>
#include <cmocka.h>

#include "assert_macro.h"

/* This test will fail since the string returned by get_status_code_string(0)
 * doesn't match "Connection timed out". */
static void get_status_code_string_test(void **state) {
    (void) state; /* unused */

    assert_string_equal(get_status_code_string(0), "Address not found");
    assert_string_equal(get_status_code_string(1), "Connection timed out");
}

/* This test will fail since the status code of "Connection timed out" isn't 1 */
static void string_to_status_code_test(void **state) {
    (void) state; /* unused */

    assert_int_equal(string_to_status_code("Address not found"), 0);
    assert_int_equal(string_to_status_code("Connection timed out"), 1);
}

int main(void) {
    const struct CMUnitTest tests[] = {
        cmocka_unit_test(get_status_code_string_test),
        cmocka_unit_test(string_to_status_code_test),
    };
    return cmocka_run_group_tests(tests, NULL, NULL);
}
