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
#include <database.h>

extern DatabaseConnection* connect_to_product_database(void);

/* Mock connect to database function.
 * NOTE: This mock function is very general could be shared between tests
 * that use the imaginary database.h module. */
DatabaseConnection* connect_to_database(const char * const url,
                                        const unsigned int port) {
    check_expected_ptr(url);
    check_expected(port);
    return (DatabaseConnection*)((size_t)mock());
}

static void test_connect_to_product_database(void **state) {
    (void) state; /* unused */

    expect_string(connect_to_database, url, "products.abcd.org");
    expect_value(connect_to_database, port, 322);
    will_return(connect_to_database, 0xDA7ABA53);
    assert_int_equal((size_t)connect_to_product_database(), 0xDA7ABA53);
}

/* This test will fail since the expected URL is different to the URL that is
 * passed to connect_to_database() by connect_to_product_database(). */
static void test_connect_to_product_database_bad_url(void **state) {
    (void) state; /* unused */

    expect_string(connect_to_database, url, "products.abcd.com");
    expect_value(connect_to_database, port, 322);
    will_return(connect_to_database, 0xDA7ABA53);
    assert_int_equal((size_t)connect_to_product_database(), 0xDA7ABA53);
}

/* This test will fail since the mock connect_to_database() will attempt to
 * retrieve a value for the parameter port which isn't specified by this
 * test function. */
static void test_connect_to_product_database_missing_parameter(void **state) {
    (void) state; /* unused */

    expect_string(connect_to_database, url, "products.abcd.org");
    will_return(connect_to_database, 0xDA7ABA53);
    assert_int_equal((size_t)connect_to_product_database(), 0xDA7ABA53);
}

int main(void) {
    const struct CMUnitTest tests[] = {
        cmocka_unit_test(test_connect_to_product_database),
        cmocka_unit_test(test_connect_to_product_database_bad_url),
        cmocka_unit_test(test_connect_to_product_database_missing_parameter),
    };
    return cmocka_run_group_tests(tests, NULL, NULL);
}
