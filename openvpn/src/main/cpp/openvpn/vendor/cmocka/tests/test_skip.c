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

/* A test case that does check if an int is equal. */
static void test_check_skip(void **state) {
    (void)state; /* unused */

    skip();

    assert_true(0);
}


int main(void) {
    const struct CMUnitTest tests[] = {
        cmocka_unit_test(test_check_skip),
    };

    return cmocka_run_group_tests(tests, NULL, NULL);
}

