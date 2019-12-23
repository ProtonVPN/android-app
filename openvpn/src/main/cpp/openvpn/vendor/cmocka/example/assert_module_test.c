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

#include "assert_module.h"

extern void increment_value(int * const value);

/* This test case will fail but the assert is caught by run_tests() and the
 * next test is executed. */
static void increment_value_fail(void **state) {
    (void) state;

    increment_value(NULL);
}

/* This test case succeeds since increment_value() asserts on the NULL
 * pointer. */
static void increment_value_assert(void **state) {
    (void) state;

    expect_assert_failure(increment_value(NULL));
}

/* This test case fails since decrement_value() doesn't assert on a NULL
 * pointer. */
static void decrement_value_fail(void **state) {
    (void) state;

    expect_assert_failure(decrement_value(NULL));
}

int main(void) {
    const struct CMUnitTest tests[] = {
        cmocka_unit_test(increment_value_fail),
        cmocka_unit_test(increment_value_assert),
        cmocka_unit_test(decrement_value_fail),
    };
    return cmocka_run_group_tests(tests, NULL, NULL);
}
