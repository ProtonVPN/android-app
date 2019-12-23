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

extern void leak_memory();
extern void buffer_overflow();
extern void buffer_underflow();

/* Test case that fails as leak_memory() leaks a dynamically allocated block. */
static void leak_memory_test(void **state) {
    (void) state; /* unused */

    leak_memory();
}

/* Test case that fails as buffer_overflow() corrupts an allocated block. */
static void buffer_overflow_test(void **state) {
    (void) state; /* unused */

    buffer_overflow();
}

/* Test case that fails as buffer_underflow() corrupts an allocated block. */
static void buffer_underflow_test(void **state) {
    (void) state; /* unused */

    buffer_underflow();
}

int main(void) {
    const struct CMUnitTest tests[] = {
        cmocka_unit_test(leak_memory_test),
        cmocka_unit_test(buffer_overflow_test),
        cmocka_unit_test(buffer_underflow_test),
    };
    return cmocka_run_group_tests(tests, NULL, NULL);
}
