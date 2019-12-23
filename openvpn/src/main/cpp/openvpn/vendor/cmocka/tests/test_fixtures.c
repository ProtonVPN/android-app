#include <stdarg.h>
#include <stddef.h>
#include <setjmp.h>
#include <cmocka.h>

#include <stdlib.h>

static int setup_only(void **state)
{
    *state = malloc(1);

    return 0;
}

static int teardown_only(void **state)
{
    free(*state);

    return 0;
}

static void malloc_setup_test(void **state)
{
    assert_non_null(*state);
    free(*state);
}

static void malloc_teardown_test(void **state)
{
    *state = malloc(1);
    assert_non_null(*state);
}

static int prestate_setup(void **state)
{
    int *val = (int *)*state, *a;

    a = malloc(sizeof(int));
    *a = *val + 1;
    *state = a;

    return 0;
}

static int prestate_teardown(void **state)
{
	free(*state);

	return 0;
}

static void prestate_setup_test(void **state)
{
    int *a = (int *)*state;

    assert_non_null(a);
    assert_int_equal(*a, 43);
}

static void prestate_test(void **state)
{
    int *a = (int *)*state;

    assert_non_null(a);
    assert_int_equal(*a, 42);
}

int main(void) {
    int prestate = 42;
    const struct CMUnitTest tests[] = {
        cmocka_unit_test_setup(malloc_setup_test, setup_only),
        cmocka_unit_test_setup(malloc_setup_test, setup_only),
        cmocka_unit_test_teardown(malloc_teardown_test, teardown_only),
        cmocka_unit_test_teardown(malloc_teardown_test, teardown_only),
        cmocka_unit_test_teardown(malloc_teardown_test, teardown_only),
        cmocka_unit_test_teardown(malloc_teardown_test, teardown_only),
        cmocka_unit_test_prestate(prestate_test, &prestate),
        cmocka_unit_test_prestate_setup_teardown(prestate_setup_test, prestate_setup, prestate_teardown, &prestate),
    };

    return cmocka_run_group_tests(tests, NULL, NULL);
}
