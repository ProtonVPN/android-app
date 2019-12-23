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
#include "cmocka.h"
#include <stdio.h>

#ifdef _WIN32
/* Compatibility with the Windows standard C library. */
#define vsnprintf _vsnprintf
#endif /* _WIN32 */

#define array_length(x) (sizeof(x) / sizeof((x)[0]))

/* To simplify this code, these functions and data structures could have been
 * separated out from the application example.c into a header shared with
 * test application.  However, this example illustrates how it's possible to
 * test existing code with little modification. */

typedef int (*BinaryOperator)(int a, int b);

typedef struct OperatorFunction {
	const char* operator;
	BinaryOperator function;
} OperatorFunction;

extern int add(int a, int b);
extern int subtract(int a, int b);
extern int multiply(int a, int b);
extern int divide(int a, int b);
extern BinaryOperator find_operator_function_by_string(
        const size_t number_of_operator_functions,
        const OperatorFunction * const operator_functions,
        const char* const operator_string);
extern int perform_operation(
        int number_of_arguments, char *arguments[],
        const size_t number_of_operator_functions,
        const OperatorFunction * const operator_functions,
        int * const number_of_intermediate_values,
        int ** const intermediate_values, int * const error_occurred);
extern int example_main(int argc, char *argv[]);

int example_test_fprintf(FILE* const file, const char *format, ...) CMOCKA_PRINTF_ATTRIBUTE(2, 3);
int example_test_printf(const char *format, ...) CMOCKA_PRINTF_ATTRIBUTE(1, 2);

static char temporary_buffer[256];

/* A mock fprintf function that checks the value of strings printed to the
 * standard error stream. */
int example_test_fprintf(FILE* const file, const char *format, ...) {
	int return_value;
	va_list args;
	assert_true(file == stderr);
	va_start(args, format);
	return_value = vsnprintf(temporary_buffer, sizeof(temporary_buffer),
	                         format, args);
	check_expected_ptr(temporary_buffer);
	va_end(args);
	return return_value;
}

/* A mock printf function that checks the value of strings printed to the
 * standard output stream. */
int example_test_printf(const char *format, ...) {
	int return_value;
	va_list args;
	va_start(args, format);
	return_value = vsnprintf(temporary_buffer, sizeof(temporary_buffer),
	                         format, args);
	check_expected_ptr(temporary_buffer);
	va_end(args);
	return return_value;
}

/* A mock binary operator function. */
static int binary_operator(int a, int b) {
	check_expected(a);
	check_expected(b);
	return (int)mock();
}


/* Ensure add() adds two integers correctly. */
static void test_add(void **state) {
        (void) state; /* unused */

	assert_int_equal(add(3, 3), 6);
	assert_int_equal(add(3, -3), 0);
}

/* Ensure subtract() subtracts two integers correctly. */
static void test_subtract(void **state) {
        (void) state; /* unused */

	assert_int_equal(subtract(3, 3), 0);
	assert_int_equal(subtract(3, -3), 6);
}

/* Ensure multiple() mulitplies two integers correctly. */
static void test_multiply(void **state) {
        (void) state; /* unused */

	assert_int_equal(multiply(3, 3), 9);
	assert_int_equal(multiply(3, 0), 0);
}

/* Ensure divide() divides one integer by another correctly. */
static void test_divide(void **state) {
        (void) state; /* unused */

	assert_int_equal(divide(10, 2), 5);
	assert_int_equal(divide(2, 10), 0);
}

/* Ensure divide() asserts when trying to divide by zero. */
static void test_divide_by_zero(void **state) {
        (void) state; /* unused */

	expect_assert_failure(divide(100, 0));
}

/* Ensure find_operator_function_by_string() asserts when a NULL pointer is
 * specified as the table to search. */
static void test_find_operator_function_by_string_null_functions(void **state) {
        (void) state; /* unused */

	expect_assert_failure(find_operator_function_by_string(1, NULL, "test"));
}

/* Ensure find_operator_function_by_string() asserts when a NULL pointer is
 * specified as the string to search for. */
static void test_find_operator_function_by_string_null_string(void **state) {
	const OperatorFunction operator_functions[] = {
		{"+", binary_operator},
	};

        (void) state; /* unused */

	expect_assert_failure(find_operator_function_by_string(
	    array_length(operator_functions), operator_functions, NULL));
}

/* Ensure find_operator_function_by_string() returns NULL when a NULL pointer
 * is specified as the table to search when the table size is 0. */
static void test_find_operator_function_by_string_valid_null_functions(void **state) {
        (void) state; /* unused */

	assert_null(find_operator_function_by_string(0, NULL, "test"));
}

/* Ensure find_operator_function_by_string() returns NULL when searching for
 * an operator string that isn't in the specified table. */
static void test_find_operator_function_by_string_not_found(void **state) {
	const OperatorFunction operator_functions[] = {
		{"+", binary_operator},
		{"-", binary_operator},
		{"/", binary_operator},
	};

        (void) state; /* unused */

	assert_null(find_operator_function_by_string(
	        array_length(operator_functions), operator_functions, "test"));
}

/* Ensure find_operator_function_by_string() returns the correct function when
 * searching for an operator string that is in the specified table. */
static void test_find_operator_function_by_string_found(void **state) {
	const OperatorFunction operator_functions[] = {
		{"+", (BinaryOperator)0x12345678},
		{"-", (BinaryOperator)0xDEADBEEF},
		{"/", (BinaryOperator)0xABADCAFE},
	};

        (void) state; /* unused */

	assert_int_equal(
            cast_ptr_to_largest_integral_type(
                find_operator_function_by_string(array_length(operator_functions),
                                                 operator_functions,
                                                 "-")),
	    0xDEADBEEF);
}

/* Ensure perform_operation() asserts when a NULL arguments array is specified. */
static void test_perform_operation_null_args(void **state) {
	const OperatorFunction operator_functions[] = {
		{"+", binary_operator},
	};
	int number_of_intermediate_values;
	int *intermediate_values;
	int error_occurred;

        (void) state; /* unused */

	expect_assert_failure(perform_operation(
	    1, NULL, array_length(operator_functions), operator_functions,
	    &number_of_intermediate_values, &intermediate_values,
	    &error_occurred));
}

/* Ensure perform_operation() asserts when a NULL operator_functions array is
 * specified. */
static void test_perform_operation_null_operator_functions(void **state) {
	const char *args[] = {
		"1", "+", "2", "*", "4"
	};
	int number_of_intermediate_values;
	int *intermediate_values;
	int error_occurred;

        (void) state; /* unused */

	expect_assert_failure(perform_operation(
	    array_length(args), (char **) args, 1, NULL, &number_of_intermediate_values,
	    &intermediate_values, &error_occurred));
}

/* Ensure perform_operation() asserts when a NULL pointer is specified for
 * number_of_intermediate_values. */
static void test_perform_operation_null_number_of_intermediate_values(void **state) {
	const OperatorFunction operator_functions[] = {
		{"+", binary_operator},
	};
	const char *args[] = {
		"1", "+", "2", "*", "4"
	};
	int *intermediate_values;
	int error_occurred;

        (void) state; /* unused */

	expect_assert_failure(perform_operation(
	    array_length(args), (char **) args, 1, operator_functions, NULL,
	    &intermediate_values, &error_occurred));
}

/* Ensure perform_operation() asserts when a NULL pointer is specified for
 * intermediate_values. */
static void test_perform_operation_null_intermediate_values(void **state) {
	const OperatorFunction operator_functions[] = {
		{"+", binary_operator},
	};
	const char *args[] = {
		"1", "+", "2", "*", "4"
	};
	int number_of_intermediate_values;
	int error_occurred;

        (void) state; /* unused */

	expect_assert_failure(perform_operation(
	    array_length(args), (char **) args, array_length(operator_functions),
	    operator_functions, &number_of_intermediate_values, NULL,
	    &error_occurred));
}

/* Ensure perform_operation() returns 0 when no arguments are specified. */
static void test_perform_operation_no_arguments(void **state) {
	int number_of_intermediate_values;
	int *intermediate_values;
	int error_occurred;

        (void) state; /* unused */

	assert_int_equal(perform_operation(
	    0, NULL, 0, NULL, &number_of_intermediate_values, &intermediate_values,
	    &error_occurred), 0);
	assert_int_equal(error_occurred, 0);
}

/* Ensure perform_operation() returns an error if the first argument isn't
 * an integer string. */
static void test_perform_operation_first_arg_not_integer(void **state) {
	const OperatorFunction operator_functions[] = {
		{"+", binary_operator},
	};
	const char *args[] = {
		"test", "+", "2", "*", "4"
	};
	int number_of_intermediate_values;
	int *intermediate_values;
	int error_occurred;

        (void) state; /* unused */

	expect_string(example_test_fprintf, temporary_buffer,
	              "Unable to parse integer from argument test\n");

	assert_int_equal(perform_operation(
	    array_length(args), (char **) args, array_length(operator_functions),
	    operator_functions, &number_of_intermediate_values,
	    &intermediate_values, &error_occurred), 0);
	assert_int_equal(error_occurred, 1);
}

/* Ensure perform_operation() returns an error when parsing an unknown
 * operator. */
static void test_perform_operation_unknown_operator(void **state) {
	const OperatorFunction operator_functions[] = {
		{"+", binary_operator},
	};
	const char *args[] = {
		"1", "*", "2", "*", "4"
	};
	int number_of_intermediate_values;
	int *intermediate_values;
	int error_occurred;

        (void) state; /* unused */

	expect_string(example_test_fprintf, temporary_buffer,
	              "Unknown operator *, argument 1\n");

	assert_int_equal(perform_operation(
	    array_length(args), (char **) args, array_length(operator_functions),
	    operator_functions, &number_of_intermediate_values,
	    &intermediate_values, &error_occurred), 0);
	assert_int_equal(error_occurred, 1);
}

/* Ensure perform_operation() returns an error when nothing follows an
 * operator. */
static void test_perform_operation_missing_argument(void **state) {
	const OperatorFunction operator_functions[] = {
		{"+", binary_operator},
	};
	const char *args[] = {
		"1", "+",
	};
	int number_of_intermediate_values;
	int *intermediate_values;
	int error_occurred;

        (void) state; /* unused */

	expect_string(example_test_fprintf, temporary_buffer,
	              "Binary operator + missing argument\n");

	assert_int_equal(perform_operation(
	    array_length(args), (char **) args, array_length(operator_functions),
	    operator_functions, &number_of_intermediate_values,
	    &intermediate_values, &error_occurred), 0);
	assert_int_equal(error_occurred, 1);
}

/* Ensure perform_operation() returns an error when an integer doesn't follow
 * an operator. */
static void test_perform_operation_no_integer_after_operator(void **state) {
	const OperatorFunction operator_functions[] = {
		{"+", binary_operator},
	};
	const char *args[] = {
		"1", "+", "test",
	};
	int number_of_intermediate_values;
	int *intermediate_values;
	int error_occurred;

        (void) state; /* unused */

	expect_string(example_test_fprintf, temporary_buffer,
	              "Unable to parse integer test of argument 2\n");

	assert_int_equal(perform_operation(
	    array_length(args), (char **) args, array_length(operator_functions),
	    operator_functions, &number_of_intermediate_values,
	    &intermediate_values, &error_occurred), 0);
	assert_int_equal(error_occurred, 1);
}


/* Ensure perform_operation() succeeds given valid input parameters. */
static void test_perform_operation(void **state) {
	const OperatorFunction operator_functions[] = {
		{"+", binary_operator},
		{"*", binary_operator},
	};
	const char *args[] = {
		"1", "+", "3", "*", "10",
	};
	int number_of_intermediate_values;
	int *intermediate_values = NULL;
	int error_occurred;

        (void) state; /* unused */

	/* Setup return values of mock operator functions. */
	/* Addition. */
	expect_value(binary_operator, a, 1);
	expect_value(binary_operator, b, 3);
	will_return(binary_operator, 4);

	/* Multiplication. */
	expect_value(binary_operator, a, 4);
	expect_value(binary_operator, b, 10);
	will_return(binary_operator, 40);

	assert_int_equal(perform_operation(
	    array_length(args), (char **) args, array_length(operator_functions),
	    operator_functions, &number_of_intermediate_values,
	    &intermediate_values, &error_occurred), 40);
	assert_int_equal(error_occurred, 0);

	assert_non_null(intermediate_values);
	assert_int_equal(intermediate_values[0], 4);
	assert_int_equal(intermediate_values[1], 40);
	test_free(intermediate_values);
}


/* Ensure main() in example.c succeeds given no arguments. */
static void test_example_main_no_args(void **state) {
	const char *args[] = {
		"example",
	};

        (void) state; /* unused */

	assert_int_equal(example_main(array_length(args), (char **) args), 0);
}



/* Ensure main() in example.c succeeds given valid input arguments. */
static void test_example_main(void **state) {
	const char *args[] = {
		"example", "1", "+", "3", "*", "10",
	};

        (void) state; /* unused */

	expect_string(example_test_printf, temporary_buffer, "1\n");
	expect_string(example_test_printf, temporary_buffer, "  + 3 = 4\n");
	expect_string(example_test_printf, temporary_buffer, "  * 10 = 40\n");
	expect_string(example_test_printf, temporary_buffer, "= 40\n");

	assert_int_equal(example_main(array_length(args), (char **) args), 0);
}


int main(void) {
	const struct CMUnitTest tests[] = {
		cmocka_unit_test(test_add),
		cmocka_unit_test(test_subtract),
		cmocka_unit_test(test_multiply),
		cmocka_unit_test(test_divide),
		cmocka_unit_test(test_divide_by_zero),
		cmocka_unit_test(test_find_operator_function_by_string_null_functions),
		cmocka_unit_test(test_find_operator_function_by_string_null_string),
		cmocka_unit_test(test_find_operator_function_by_string_valid_null_functions),
		cmocka_unit_test(test_find_operator_function_by_string_not_found),
		cmocka_unit_test(test_find_operator_function_by_string_found),
		cmocka_unit_test(test_perform_operation_null_args),
		cmocka_unit_test(test_perform_operation_null_operator_functions),
		cmocka_unit_test(test_perform_operation_null_number_of_intermediate_values),
		cmocka_unit_test(test_perform_operation_null_intermediate_values),
		cmocka_unit_test(test_perform_operation_no_arguments),
		cmocka_unit_test(test_perform_operation_first_arg_not_integer),
		cmocka_unit_test(test_perform_operation_unknown_operator),
		cmocka_unit_test(test_perform_operation_missing_argument),
		cmocka_unit_test(test_perform_operation_no_integer_after_operator),
		cmocka_unit_test(test_perform_operation),
		cmocka_unit_test(test_example_main_no_args),
		cmocka_unit_test(test_example_main),
	};
	return cmocka_run_group_tests(tests, NULL, NULL);
}
