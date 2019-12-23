#include "config.h"

#include <stdarg.h>
#include <stddef.h>
#include <setjmp.h>
#include <cmocka.h>
#include <cmocka_private.h>

#include <errno.h>
#include <sys/types.h>
#include <sys/stat.h>
#ifdef HAVE_UNISTD_H
#include <unistd.h>
#endif
#include <fcntl.h>

/**************************************
 *** assert_return_code
 **************************************/
static void test_assert_return_code(void **state)
{
    struct stat sb;
    int rc;

    (void)state; /* unused */

    rc = stat(".", &sb);
    assert_return_code(rc, 0);

#ifndef _MSC_VER
    assert_true(S_ISDIR(sb.st_mode));
#endif
}

int main(void) {
    const struct CMUnitTest tests[] = {
        cmocka_unit_test(test_assert_return_code),
    };

    return cmocka_run_group_tests(tests, NULL, NULL);
}
