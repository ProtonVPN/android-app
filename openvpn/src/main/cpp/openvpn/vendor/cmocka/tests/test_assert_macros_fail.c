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
#ifdef HAVE_IO_H
#include <io.h>
#endif
#include <fcntl.h>

/**************************************
 *** assert_return_code
 **************************************/
static void test_assert_return_code_fail(void **state)
{
    int fd;

    (void)state; /* unused */

    fd = open("this_file_doesnt_exist.cmocka", 0);
    assert_return_code(fd, errno);

    if (fd >= 0) {
        close(fd);
    }
}

int main(void) {
    const struct CMUnitTest tests[] = {
        cmocka_unit_test(test_assert_return_code_fail),
    };

    return cmocka_run_group_tests(tests, NULL, NULL);
}
