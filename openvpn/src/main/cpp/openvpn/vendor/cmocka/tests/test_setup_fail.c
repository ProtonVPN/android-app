#define UNIT_TESTING 1

#include <stdarg.h>
#include <stddef.h>
#include <setjmp.h>
#include <cmocka.h>

static int setup_fail(void **state) {
    *state = NULL;

    /* We need to fail in setup */
    return -1;
}

static void int_test_ignored(void **state) {
    /* should not be called */
    assert_non_null(*state);
}

static int setup_ok(void **state) {
    int *answer;

    answer = malloc(sizeof(int));
    if (answer == NULL) {
        return -1;
    }
    *answer = 42;

    *state = answer;

    return 0;
}

/* A test case that does check if an int is equal. */
static void int_test_success(void **state) {
    int *answer = *state;

    assert_int_equal(*answer, 42);
}

static int teardown(void **state) {
    free(*state);

    return 0;
}

int main(void) {
    const struct CMUnitTest tests[] = {
        cmocka_unit_test_setup_teardown(int_test_ignored, setup_fail, teardown),
        cmocka_unit_test_setup_teardown(int_test_success, setup_ok, teardown),
    };

    return cmocka_run_group_tests(tests, NULL, NULL);
}
