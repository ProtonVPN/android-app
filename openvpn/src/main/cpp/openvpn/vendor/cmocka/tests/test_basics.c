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

/* Use the unit test allocators */
#define UNIT_TESTING 1

#include <stdarg.h>
#include <stddef.h>
#include <setjmp.h>
#include <cmocka.h>

static int setup(void **state) {
    int *answer = malloc(sizeof(int));

    assert_non_null(answer);
    *answer = 42;

    *state = answer;

    return 0;
}

static int teardown(void **state) {
    free(*state);

    return 0;
}

/* A test case that does nothing and succeeds. */
static void null_test_success(void **state) {
    (void) state;
}

/* A test case that does check if an int is equal. */
static void int_test_success(void **state) {
    int *answer = *state;

    assert_int_equal(*answer, 42);
}


int main(void) {
    const struct CMUnitTest tests[] = {
        cmocka_unit_test(null_test_success),
        cmocka_unit_test_setup_teardown(int_test_success, setup, teardown),
    };

    return cmocka_run_group_tests(tests, NULL, NULL);
}
