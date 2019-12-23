/* Use the unit test allocators */
#define UNIT_TESTING 1

#include <stdarg.h>
#include <stddef.h>
#include <setjmp.h>
#include <cmocka.h>

static int group_setup(void **state)
{
    int *answer = malloc(sizeof(int));
    assert_non_null(answer);
    *answer = 42;

    *state = answer;
    return 0;
}

static int group_teardown(void **state)
{
    int *answer = (int *)*state;

    free(answer);
    return 0;
}

static void test_value_equal(void **state)
{
    int a = *((int *)*state);

    assert_int_equal(a, 42);
}

static void test_value_range(void **state)
{
    int a = *((int *)*state);

    assert_in_range(a, 0, 100);
}

int main(void) {
    int prestate = 1337;
    const struct CMUnitTest tests[] = {
        cmocka_unit_test(test_value_equal),
        cmocka_unit_test(test_value_range),
	cmocka_unit_test_prestate(test_value_equal, &prestate),
    };

    return cmocka_run_group_tests(tests, group_setup, group_teardown);
}
