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

extern DatabaseConnection* connect_to_customer_database();
extern unsigned int get_customer_id_by_name(
    DatabaseConnection * const connection, const char * const customer_name);

/* Mock query database function. */
static unsigned int mock_query_database(DatabaseConnection* const connection,
                                        const char * const query_string,
                                        void *** const results) {
    (void) connection; /* unused */
    (void) query_string; /* unused */

    *results = (void **)mock_ptr_type(int *);
    return mock_ptr_type(int);
}

/* Mock of the connect to database function. */
DatabaseConnection* connect_to_database(const char * const database_url,
                                        const unsigned int port) {
    (void) database_url; /* unused */
    (void) port; /* unused */

    return (DatabaseConnection*)((size_t)mock());
}

static void test_connect_to_customer_database(void **state) {
    (void) state; /* unused */

    will_return(connect_to_database, 0x0DA7ABA53);

    assert_int_equal((size_t)connect_to_customer_database(), 0x0DA7ABA53);
}

/* This test fails as the mock function connect_to_database() will have no
 * value to return. */
#if 0
static void fail_connect_to_customer_database(void **state) {
    (void) state; /* unused */

    assert_true(connect_to_customer_database() ==
                (DatabaseConnection*)0x0DA7ABA53);
}
#endif

static void test_get_customer_id_by_name(void **state) {
    DatabaseConnection connection = {
        "somedatabase.somewhere.com", 12345678, mock_query_database
    };
    /* Return a single customer ID when mock_query_database() is called. */
    int customer_ids = 543;
    int rc;

    (void) state; /* unused */

    will_return(mock_query_database,
                cast_ptr_to_largest_integral_type(&customer_ids));
    will_return(mock_query_database, 1);

    rc = get_customer_id_by_name(&connection, "john doe");
    assert_int_equal(rc, 543);
}

int main(void) {
    const struct CMUnitTest tests[] = {
        cmocka_unit_test(test_connect_to_customer_database),
        cmocka_unit_test(test_get_customer_id_by_name),
    };
    return cmocka_run_group_tests(tests, NULL, NULL);
}
