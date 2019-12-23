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
#ifndef CMOCKA_H_
#define CMOCKA_H_

#ifdef _WIN32
# ifdef _MSC_VER

#define __func__ __FUNCTION__

# ifndef inline
#define inline __inline
# endif /* inline */

#  if _MSC_VER < 1500
#   ifdef __cplusplus
extern "C" {
#   endif   /* __cplusplus */
int __stdcall IsDebuggerPresent();
#   ifdef __cplusplus
} /* extern "C" */
#   endif   /* __cplusplus */
#  endif  /* _MSC_VER < 1500 */
# endif /* _MSC_VER */
#endif  /* _WIN32 */

/**
 * @defgroup cmocka The CMocka API
 *
 * These headers or their equivalents should be included prior to including
 * this header file.
 * @code
 * #include <stdarg.h>
 * #include <stddef.h>
 * #include <setjmp.h>
 * @endcode
 *
 * This allows test applications to use custom definitions of C standard
 * library functions and types.
 *
 * @{
 */

/* If __WORDSIZE is not set, try to figure it out and default to 32 bit. */
#ifndef __WORDSIZE
# if defined(__x86_64__) && !defined(__ILP32__)
#  define __WORDSIZE 64
# else
#  define __WORDSIZE 32
# endif
#endif

#ifdef DOXYGEN
/**
 * Largest integral type.  This type should be large enough to hold any
 * pointer or integer supported by the compiler.
 */
typedef uintmax_t LargestIntegralType;
#else /* DOXGEN */
#ifndef LargestIntegralType
# if __WORDSIZE == 64
#  define LargestIntegralType unsigned long int
# else
#  define LargestIntegralType unsigned long long int
# endif
#endif /* LargestIntegralType */
#endif /* DOXYGEN */

/* Printf format used to display LargestIntegralType. */
#ifndef LargestIntegralTypePrintfFormat
# ifdef _WIN32
#  define LargestIntegralTypePrintfFormat "0x%I64x"
# else
#  if __WORDSIZE == 64
#   define LargestIntegralTypePrintfFormat "%#lx"
#  else
#   define LargestIntegralTypePrintfFormat "%#llx"
#  endif
# endif /* _WIN32 */
#endif /* LargestIntegralTypePrintfFormat */

/* Perform an unsigned cast to LargestIntegralType. */
#define cast_to_largest_integral_type(value) \
    ((LargestIntegralType)(value))

/* Smallest integral type capable of holding a pointer. */
#if !defined(_UINTPTR_T) && !defined(_UINTPTR_T_DEFINED)
# if defined(_WIN32)
    /* WIN32 is an ILP32 platform */
    typedef unsigned int uintptr_t;
# elif defined(_WIN64)
    typedef unsigned long int uintptr_t
# else /* _WIN32 */

/* ILP32 and LP64 platforms */
#  ifdef __WORDSIZE /* glibc */
#   if __WORDSIZE == 64
      typedef unsigned long int uintptr_t;
#   else
      typedef unsigned int uintptr_t;
#   endif /* __WORDSIZE == 64 */
#  else /* __WORDSIZE */
#   if defined(_LP64) || defined(_I32LPx)
      typedef unsigned long int uintptr_t;
#   else
      typedef unsigned int uintptr_t;
#   endif
#  endif /* __WORDSIZE */
# endif /* _WIN32 */

# define _UINTPTR_T
# define _UINTPTR_T_DEFINED
#endif /* !defined(_UINTPTR_T) || !defined(_UINTPTR_T_DEFINED) */

/* Perform an unsigned cast to uintptr_t. */
#define cast_to_pointer_integral_type(value) \
    ((uintptr_t)((size_t)(value)))

/* Perform a cast of a pointer to LargestIntegralType */
#define cast_ptr_to_largest_integral_type(value) \
cast_to_largest_integral_type(cast_to_pointer_integral_type(value))

/* GCC have printf type attribute check.  */
#ifdef __GNUC__
#define CMOCKA_PRINTF_ATTRIBUTE(a,b) \
    __attribute__ ((__format__ (__printf__, a, b)))
#else
#define CMOCKA_PRINTF_ATTRIBUTE(a,b)
#endif /* __GNUC__ */

#if defined(__GNUC__)
#define CMOCKA_DEPRECATED __attribute__ ((deprecated))
#elif defined(_MSC_VER)
#define CMOCKA_DEPRECATED __declspec(deprecated)
#else
#define CMOCKA_DEPRECATED
#endif

/**
 * @defgroup cmocka_mock Mock Objects
 * @ingroup cmocka
 *
 * Mock objects mock objects are simulated objects that mimic the behavior of
 * real objects. Instead of calling the real objects, the tested object calls a
 * mock object that merely asserts that the correct methods were called, with
 * the expected parameters, in the correct order.
 *
 * <ul>
 * <li><strong>will_return(function, value)</strong> - The will_return() macro
 * pushes a value onto a stack of mock values. This macro is intended to be
 * used by the unit test itself, while programming the behaviour of the mocked
 * object.</li>
 *
 * <li><strong>mock()</strong> - the mock macro pops a value from a stack of
 * test values. The user of the mock() macro is the mocked object that uses it
 * to learn how it should behave.</li>
 * </ul>
 *
 * Because the will_return() and mock() are intended to be used in pairs, the
 * cmocka library would fail the test if there are more values pushed onto the
 * stack using will_return() than consumed with mock() and vice-versa.
 *
 * The following unit test stub illustrates how would a unit test instruct the
 * mock object to return a particular value:
 *
 * @code
 * will_return(chef_cook, "hotdog");
 * will_return(chef_cook, 0);
 * @endcode
 *
 * Now the mock object can check if the parameter it received is the parameter
 * which is expected by the test driver. This can be done the following way:
 *
 * @code
 * int chef_cook(const char *order, char **dish_out)
 * {
 *     check_expected(order);
 * }
 * @endcode
 *
 * For a complete example please at a look
 * <a href="http://git.cryptomilk.org/projects/cmocka.git/tree/example/chef_wrap/waiter_test_wrap.c">here</a>.
 *
 * @{
 */

#ifdef DOXYGEN
/**
 * @brief Retrieve a return value of the current function.
 *
 * @return The value which was stored to return by this function.
 *
 * @see will_return()
 */
LargestIntegralType mock(void);
#else
#define mock() _mock(__func__, __FILE__, __LINE__)
#endif

#ifdef DOXYGEN
/**
 * @brief Retrieve a typed return value of the current function.
 *
 * The value would be casted to type internally to avoid having the
 * caller to do the cast manually.
 *
 * @param[in]  #type  The expected type of the return value
 *
 * @return The value which was stored to return by this function.
 *
 * @code
 * int param;
 *
 * param = mock_type(int);
 * @endcode
 *
 * @see will_return()
 * @see mock()
 * @see mock_ptr_type()
 */
#type mock_type(#type);
#else
#define mock_type(type) ((type) mock())
#endif

#ifdef DOXYGEN
/**
 * @brief Retrieve a typed return value of the current function.
 *
 * The value would be casted to type internally to avoid having the
 * caller to do the cast manually but also casted to uintptr_t to make
 * sure the result has a valid size to be used as a pointer.
 *
 * @param[in]  #type  The expected type of the return value
 *
 * @return The value which was stored to return by this function.
 *
 * @code
 * char *param;
 *
 * param = mock_ptr_type(char *);
 * @endcode
 *
 * @see will_return()
 * @see mock()
 * @see mock_type()
 */
type mock_ptr_type(#type);
#else
#define mock_ptr_type(type) ((type) (uintptr_t) mock())
#endif


#ifdef DOXYGEN
/**
 * @brief Store a value to be returned by mock() later.
 *
 * @param[in]  #function  The function which should return the given value.
 *
 * @param[in]  value The value to be returned by mock().
 *
 * @code
 * int return_integer(void)
 * {
 *      return (int)mock();
 * }
 *
 * static void test_integer_return(void **state)
 * {
 *      will_return(return_integer, 42);
 *
 *      assert_int_equal(my_function_calling_return_integer(), 42);
 * }
 * @endcode
 *
 * @see mock()
 * @see will_return_count()
 */
void will_return(#function, LargestIntegralType value);
#else
#define will_return(function, value) \
    _will_return(#function, __FILE__, __LINE__, \
                 cast_to_largest_integral_type(value), 1)
#endif

#ifdef DOXYGEN
/**
 * @brief Store a value to be returned by mock() later.
 *
 * @param[in]  #function  The function which should return the given value.
 *
 * @param[in]  value The value to be returned by mock().
 *
 * @param[in]  count The parameter indicates the number of times the value should
 *                   be returned by mock(). If count is set to -1, the value
 *                   will always be returned but must be returned at least once.
 *                   If count is set to -2, the value will always be returned
 *                   by mock(), but is not required to be returned.
 *
 * @see mock()
 */
void will_return_count(#function, LargestIntegralType value, int count);
#else
#define will_return_count(function, value, count) \
    _will_return(#function, __FILE__, __LINE__, \
                 cast_to_largest_integral_type(value), count)
#endif

#ifdef DOXYGEN
/**
 * @brief Store a value that will be always returned by mock().
 *
 * @param[in]  #function  The function which should return the given value.
 *
 * @param[in]  #value The value to be returned by mock().
 *
 * This is equivalent to:
 * @code
 * will_return_count(function, value, -1);
 * @endcode
 *
 * @see will_return_count()
 * @see mock()
 */
void will_return_always(#function, LargestIntegralType value);
#else
#define will_return_always(function, value) \
    will_return_count(function, (value), -1)
#endif

#ifdef DOXYGEN
/**
 * @brief Store a value that may be always returned by mock().
 *
 * This stores a value which will always be returned by mock() but is not
 * required to be returned by at least one call to mock(). Therefore,
 * in contrast to will_return_always() which causes a test failure if it
 * is not returned at least once, will_return_maybe() will never cause a test
 * to fail if its value is not returned.
 *
 * @param[in]  #function  The function which should return the given value.
 *
 * @param[in]  #value The value to be returned by mock().
 *
 * This is equivalent to:
 * @code
 * will_return_count(function, value, -2);
 * @endcode
 *
 * @see will_return_count()
 * @see mock()
 */
void will_return_maybe(#function, LargestIntegralType value);
#else
#define will_return_maybe(function, value) \
    will_return_count(function, (value), -2)
#endif
/** @} */

/**
 * @defgroup cmocka_param Checking Parameters
 * @ingroup cmocka
 *
 * Functionality to store expected values for mock function parameters.
 *
 * In addition to storing the return values of mock functions, cmocka provides
 * functionality to store expected values for mock function parameters using
 * the expect_*() functions provided. A mock function parameter can then be
 * validated using the check_expected() macro.
 *
 * Successive calls to expect_*() macros for a parameter queues values to check
 * the specified parameter. check_expected() checks a function parameter
 * against the next value queued using expect_*(), if the parameter check fails
 * a test failure is signalled. In addition if check_expected() is called and
 * no more parameter values are queued a test failure occurs.
 *
 * The following test stub illustrates how to do this. First is the the function
 * we call in the test driver:
 *
 * @code
 * static void test_driver(void **state)
 * {
 *     expect_string(chef_cook, order, "hotdog");
 * }
 * @endcode
 *
 * Now the chef_cook function can check if the parameter we got passed is the
 * parameter which is expected by the test driver. This can be done the
 * following way:
 *
 * @code
 * int chef_cook(const char *order, char **dish_out)
 * {
 *     check_expected(order);
 * }
 * @endcode
 *
 * For a complete example please at a look at
 * <a href="http://git.cryptomilk.org/projects/cmocka.git/tree/example/chef_wrap/waiter_test_wrap.c">here</a>
 *
 * @{
 */

/*
 * Add a custom parameter checking function.  If the event parameter is NULL
 * the event structure is allocated internally by this function.  If event
 * parameter is provided it must be allocated on the heap and doesn't need to
 * be deallocated by the caller.
 */
#ifdef DOXYGEN
/**
 * @brief Add a custom parameter checking function.
 *
 * If the event parameter is NULL the event structure is allocated internally
 * by this function. If the parameter is provided it must be allocated on the
 * heap and doesn't need to be deallocated by the caller.
 *
 * @param[in]  #function  The function to add a custom parameter checking
 *                        function for.
 *
 * @param[in]  #parameter The parameters passed to the function.
 *
 * @param[in]  #check_function  The check function to call.
 *
 * @param[in]  check_data       The data to pass to the check function.
 */
void expect_check(#function, #parameter, #check_function, const void *check_data);
#else
#define expect_check(function, parameter, check_function, check_data) \
    _expect_check(#function, #parameter, __FILE__, __LINE__, check_function, \
                  cast_to_largest_integral_type(check_data), NULL, 1)
#endif

#ifdef DOXYGEN
/**
 * @brief Add an event to check if the parameter value is part of the provided
 *        array.
 *
 * The event is triggered by calling check_expected() in the mocked function.
 *
 * @param[in]  #function  The function to add the check for.
 *
 * @param[in]  #parameter The name of the parameter passed to the function.
 *
 * @param[in]  value_array[] The array to check for the value.
 *
 * @see check_expected().
 */
void expect_in_set(#function, #parameter, LargestIntegralType value_array[]);
#else
#define expect_in_set(function, parameter, value_array) \
    expect_in_set_count(function, parameter, value_array, 1)
#endif

#ifdef DOXYGEN
/**
 * @brief Add an event to check if the parameter value is part of the provided
 *        array.
 *
 * The event is triggered by calling check_expected() in the mocked function.
 *
 * @param[in]  #function  The function to add the check for.
 *
 * @param[in]  #parameter The name of the parameter passed to the function.
 *
 * @param[in]  value_array[] The array to check for the value.
 *
 * @param[in]  count  The count parameter returns the number of times the value
 *                    should be returned by check_expected(). If count is set
 *                    to -1 the value will always be returned.
 *
 * @see check_expected().
 */
void expect_in_set_count(#function, #parameter, LargestIntegralType value_array[], size_t count);
#else
#define expect_in_set_count(function, parameter, value_array, count) \
    _expect_in_set(#function, #parameter, __FILE__, __LINE__, value_array, \
                   sizeof(value_array) / sizeof((value_array)[0]), count)
#endif

#ifdef DOXYGEN
/**
 * @brief Add an event to check if the parameter value is not part of the
 *        provided array.
 *
 * The event is triggered by calling check_expected() in the mocked function.
 *
 * @param[in]  #function  The function to add the check for.
 *
 * @param[in]  #parameter The name of the parameter passed to the function.
 *
 * @param[in]  value_array[] The array to check for the value.
 *
 * @see check_expected().
 */
void expect_not_in_set(#function, #parameter, LargestIntegralType value_array[]);
#else
#define expect_not_in_set(function, parameter, value_array) \
    expect_not_in_set_count(function, parameter, value_array, 1)
#endif

#ifdef DOXYGEN
/**
 * @brief Add an event to check if the parameter value is not part of the
 *        provided array.
 *
 * The event is triggered by calling check_expected() in the mocked function.
 *
 * @param[in]  #function  The function to add the check for.
 *
 * @param[in]  #parameter The name of the parameter passed to the function.
 *
 * @param[in]  value_array[] The array to check for the value.
 *
 * @param[in]  count  The count parameter returns the number of times the value
 *                    should be returned by check_expected(). If count is set
 *                    to -1 the value will always be returned.
 *
 * @see check_expected().
 */
void expect_not_in_set_count(#function, #parameter, LargestIntegralType value_array[], size_t count);
#else
#define expect_not_in_set_count(function, parameter, value_array, count) \
    _expect_not_in_set( \
        #function, #parameter, __FILE__, __LINE__, value_array, \
        sizeof(value_array) / sizeof((value_array)[0]), count)
#endif


#ifdef DOXYGEN
/**
 * @brief Add an event to check a parameter is inside a numerical range.
 * The check would succeed if minimum <= value <= maximum.
 *
 * The event is triggered by calling check_expected() in the mocked function.
 *
 * @param[in]  #function  The function to add the check for.
 *
 * @param[in]  #parameter The name of the parameter passed to the function.
 *
 * @param[in]  minimum  The lower boundary of the interval to check against.
 *
 * @param[in]  maximum  The upper boundary of the interval to check against.
 *
 * @see check_expected().
 */
void expect_in_range(#function, #parameter, LargestIntegralType minimum, LargestIntegralType maximum);
#else
#define expect_in_range(function, parameter, minimum, maximum) \
    expect_in_range_count(function, parameter, minimum, maximum, 1)
#endif

#ifdef DOXYGEN
/**
 * @brief Add an event to repeatedly check a parameter is inside a
 * numerical range. The check would succeed if minimum <= value <= maximum.
 *
 * The event is triggered by calling check_expected() in the mocked function.
 *
 * @param[in]  #function  The function to add the check for.
 *
 * @param[in]  #parameter The name of the parameter passed to the function.
 *
 * @param[in]  minimum  The lower boundary of the interval to check against.
 *
 * @param[in]  maximum  The upper boundary of the interval to check against.
 *
 * @param[in]  count  The count parameter returns the number of times the value
 *                    should be returned by check_expected(). If count is set
 *                    to -1 the value will always be returned.
 *
 * @see check_expected().
 */
void expect_in_range_count(#function, #parameter, LargestIntegralType minimum, LargestIntegralType maximum, size_t count);
#else
#define expect_in_range_count(function, parameter, minimum, maximum, count) \
    _expect_in_range(#function, #parameter, __FILE__, __LINE__, minimum, \
                     maximum, count)
#endif

#ifdef DOXYGEN
/**
 * @brief Add an event to check a parameter is outside a numerical range.
 * The check would succeed if minimum > value > maximum.
 *
 * The event is triggered by calling check_expected() in the mocked function.
 *
 * @param[in]  #function  The function to add the check for.
 *
 * @param[in]  #parameter The name of the parameter passed to the function.
 *
 * @param[in]  minimum  The lower boundary of the interval to check against.
 *
 * @param[in]  maximum  The upper boundary of the interval to check against.
 *
 * @see check_expected().
 */
void expect_not_in_range(#function, #parameter, LargestIntegralType minimum, LargestIntegralType maximum);
#else
#define expect_not_in_range(function, parameter, minimum, maximum) \
    expect_not_in_range_count(function, parameter, minimum, maximum, 1)
#endif

#ifdef DOXYGEN
/**
 * @brief Add an event to repeatedly check a parameter is outside a
 * numerical range. The check would succeed if minimum > value > maximum.
 *
 * The event is triggered by calling check_expected() in the mocked function.
 *
 * @param[in]  #function  The function to add the check for.
 *
 * @param[in]  #parameter The name of the parameter passed to the function.
 *
 * @param[in]  minimum  The lower boundary of the interval to check against.
 *
 * @param[in]  maximum  The upper boundary of the interval to check against.
 *
 * @param[in]  count  The count parameter returns the number of times the value
 *                    should be returned by check_expected(). If count is set
 *                    to -1 the value will always be returned.
 *
 * @see check_expected().
 */
void expect_not_in_range_count(#function, #parameter, LargestIntegralType minimum, LargestIntegralType maximum, size_t count);
#else
#define expect_not_in_range_count(function, parameter, minimum, maximum, \
                                  count) \
    _expect_not_in_range(#function, #parameter, __FILE__, __LINE__, \
                         minimum, maximum, count)
#endif

#ifdef DOXYGEN
/**
 * @brief Add an event to check if a parameter is the given value.
 *
 * The event is triggered by calling check_expected() in the mocked function.
 *
 * @param[in]  #function  The function to add the check for.
 *
 * @param[in]  #parameter The name of the parameter passed to the function.
 *
 * @param[in]  value  The value to check.
 *
 * @see check_expected().
 */
void expect_value(#function, #parameter, LargestIntegralType value);
#else
#define expect_value(function, parameter, value) \
    expect_value_count(function, parameter, value, 1)
#endif

#ifdef DOXYGEN
/**
 * @brief Add an event to repeatedly check if a parameter is the given value.
 *
 * The event is triggered by calling check_expected() in the mocked function.
 *
 * @param[in]  #function  The function to add the check for.
 *
 * @param[in]  #parameter The name of the parameter passed to the function.
 *
 * @param[in]  value  The value to check.
 *
 * @param[in]  count  The count parameter returns the number of times the value
 *                    should be returned by check_expected(). If count is set
 *                    to -1 the value will always be returned.
 *
 * @see check_expected().
 */
void expect_value_count(#function, #parameter, LargestIntegralType value, size_t count);
#else
#define expect_value_count(function, parameter, value, count) \
    _expect_value(#function, #parameter, __FILE__, __LINE__, \
                  cast_to_largest_integral_type(value), count)
#endif

#ifdef DOXYGEN
/**
 * @brief Add an event to check if a parameter isn't the given value.
 *
 * The event is triggered by calling check_expected() in the mocked function.
 *
 * @param[in]  #function  The function to add the check for.
 *
 * @param[in]  #parameter The name of the parameter passed to the function.
 *
 * @param[in]  value  The value to check.
 *
 * @see check_expected().
 */
void expect_not_value(#function, #parameter, LargestIntegralType value);
#else
#define expect_not_value(function, parameter, value) \
    expect_not_value_count(function, parameter, value, 1)
#endif

#ifdef DOXYGEN
/**
 * @brief Add an event to repeatedly check if a parameter isn't the given value.
 *
 * The event is triggered by calling check_expected() in the mocked function.
 *
 * @param[in]  #function  The function to add the check for.
 *
 * @param[in]  #parameter The name of the parameter passed to the function.
 *
 * @param[in]  value  The value to check.
 *
 * @param[in]  count  The count parameter returns the number of times the value
 *                    should be returned by check_expected(). If count is set
 *                    to -1 the value will always be returned.
 *
 * @see check_expected().
 */
void expect_not_value_count(#function, #parameter, LargestIntegralType value, size_t count);
#else
#define expect_not_value_count(function, parameter, value, count) \
    _expect_not_value(#function, #parameter, __FILE__, __LINE__, \
                      cast_to_largest_integral_type(value), count)
#endif

#ifdef DOXYGEN
/**
 * @brief Add an event to check if the parameter value is equal to the
 *        provided string.
 *
 * The event is triggered by calling check_expected() in the mocked function.
 *
 * @param[in]  #function  The function to add the check for.
 *
 * @param[in]  #parameter The name of the parameter passed to the function.
 *
 * @param[in]  string   The string value to compare.
 *
 * @see check_expected().
 */
void expect_string(#function, #parameter, const char *string);
#else
#define expect_string(function, parameter, string) \
    expect_string_count(function, parameter, string, 1)
#endif

#ifdef DOXYGEN
/**
 * @brief Add an event to check if the parameter value is equal to the
 *        provided string.
 *
 * The event is triggered by calling check_expected() in the mocked function.
 *
 * @param[in]  #function  The function to add the check for.
 *
 * @param[in]  #parameter The name of the parameter passed to the function.
 *
 * @param[in]  string   The string value to compare.
 *
 * @param[in]  count  The count parameter returns the number of times the value
 *                    should be returned by check_expected(). If count is set
 *                    to -1 the value will always be returned.
 *
 * @see check_expected().
 */
void expect_string_count(#function, #parameter, const char *string, size_t count);
#else
#define expect_string_count(function, parameter, string, count) \
    _expect_string(#function, #parameter, __FILE__, __LINE__, \
                   (const char*)(string), count)
#endif

#ifdef DOXYGEN
/**
 * @brief Add an event to check if the parameter value isn't equal to the
 *        provided string.
 *
 * The event is triggered by calling check_expected() in the mocked function.
 *
 * @param[in]  #function  The function to add the check for.
 *
 * @param[in]  #parameter The name of the parameter passed to the function.
 *
 * @param[in]  string   The string value to compare.
 *
 * @see check_expected().
 */
void expect_not_string(#function, #parameter, const char *string);
#else
#define expect_not_string(function, parameter, string) \
    expect_not_string_count(function, parameter, string, 1)
#endif

#ifdef DOXYGEN
/**
 * @brief Add an event to check if the parameter value isn't equal to the
 *        provided string.
 *
 * The event is triggered by calling check_expected() in the mocked function.
 *
 * @param[in]  #function  The function to add the check for.
 *
 * @param[in]  #parameter The name of the parameter passed to the function.
 *
 * @param[in]  string   The string value to compare.
 *
 * @param[in]  count  The count parameter returns the number of times the value
 *                    should be returned by check_expected(). If count is set
 *                    to -1 the value will always be returned.
 *
 * @see check_expected().
 */
void expect_not_string_count(#function, #parameter, const char *string, size_t count);
#else
#define expect_not_string_count(function, parameter, string, count) \
    _expect_not_string(#function, #parameter, __FILE__, __LINE__, \
                       (const char*)(string), count)
#endif

#ifdef DOXYGEN
/**
 * @brief Add an event to check if the parameter does match an area of memory.
 *
 * The event is triggered by calling check_expected() in the mocked function.
 *
 * @param[in]  #function  The function to add the check for.
 *
 * @param[in]  #parameter The name of the parameter passed to the function.
 *
 * @param[in]  memory  The memory to compare.
 *
 * @param[in]  size  The size of the memory to compare.
 *
 * @see check_expected().
 */
void expect_memory(#function, #parameter, void *memory, size_t size);
#else
#define expect_memory(function, parameter, memory, size) \
    expect_memory_count(function, parameter, memory, size, 1)
#endif

#ifdef DOXYGEN
/**
 * @brief Add an event to repeatedly check if the parameter does match an area
 *        of memory.
 *
 * The event is triggered by calling check_expected() in the mocked function.
 *
 * @param[in]  #function  The function to add the check for.
 *
 * @param[in]  #parameter The name of the parameter passed to the function.
 *
 * @param[in]  memory  The memory to compare.
 *
 * @param[in]  size  The size of the memory to compare.
 *
 * @param[in]  count  The count parameter returns the number of times the value
 *                    should be returned by check_expected(). If count is set
 *                    to -1 the value will always be returned.
 *
 * @see check_expected().
 */
void expect_memory_count(#function, #parameter, void *memory, size_t size, size_t count);
#else
#define expect_memory_count(function, parameter, memory, size, count) \
    _expect_memory(#function, #parameter, __FILE__, __LINE__, \
                   (const void*)(memory), size, count)
#endif

#ifdef DOXYGEN
/**
 * @brief Add an event to check if the parameter doesn't match an area of
 *        memory.
 *
 * The event is triggered by calling check_expected() in the mocked function.
 *
 * @param[in]  #function  The function to add the check for.
 *
 * @param[in]  #parameter The name of the parameter passed to the function.
 *
 * @param[in]  memory  The memory to compare.
 *
 * @param[in]  size  The size of the memory to compare.
 *
 * @see check_expected().
 */
void expect_not_memory(#function, #parameter, void *memory, size_t size);
#else
#define expect_not_memory(function, parameter, memory, size) \
    expect_not_memory_count(function, parameter, memory, size, 1)
#endif

#ifdef DOXYGEN
/**
 * @brief Add an event to repeatedly check if the parameter doesn't match an
 *        area of memory.
 *
 * The event is triggered by calling check_expected() in the mocked function.
 *
 * @param[in]  #function  The function to add the check for.
 *
 * @param[in]  #parameter The name of the parameter passed to the function.
 *
 * @param[in]  memory  The memory to compare.
 *
 * @param[in]  size  The size of the memory to compare.
 *
 * @param[in]  count  The count parameter returns the number of times the value
 *                    should be returned by check_expected(). If count is set
 *                    to -1 the value will always be returned.
 *
 * @see check_expected().
 */
void expect_not_memory_count(#function, #parameter, void *memory, size_t size, size_t count);
#else
#define expect_not_memory_count(function, parameter, memory, size, count) \
    _expect_not_memory(#function, #parameter, __FILE__, __LINE__, \
                       (const void*)(memory), size, count)
#endif


#ifdef DOXYGEN
/**
 * @brief Add an event to check if a parameter (of any value) has been passed.
 *
 * The event is triggered by calling check_expected() in the mocked function.
 *
 * @param[in]  #function  The function to add the check for.
 *
 * @param[in]  #parameter The name of the parameter passed to the function.
 *
 * @see check_expected().
 */
void expect_any(#function, #parameter);
#else
#define expect_any(function, parameter) \
    expect_any_count(function, parameter, 1)
#endif

#ifdef DOXYGEN
/**
 * @brief Add an event to repeatedly check if a parameter (of any value) has
 *        been passed.
 *
 * The event is triggered by calling check_expected() in the mocked function.
 *
 * @param[in]  #function  The function to add the check for.
 *
 * @param[in]  #parameter The name of the parameter passed to the function.
 *
 * @param[in]  count  The count parameter returns the number of times the value
 *                    should be returned by check_expected(). If count is set
 *                    to -1 the value will always be returned.
 *
 * @see check_expected().
 */
void expect_any_count(#function, #parameter, size_t count);
#else
#define expect_any_count(function, parameter, count) \
    _expect_any(#function, #parameter, __FILE__, __LINE__, count)
#endif

#ifdef DOXYGEN
/**
 * @brief Determine whether a function parameter is correct.
 *
 * This ensures the next value queued by one of the expect_*() macros matches
 * the specified variable.
 *
 * This function needs to be called in the mock object.
 *
 * @param[in]  #parameter  The parameter to check.
 */
void check_expected(#parameter);
#else
#define check_expected(parameter) \
    _check_expected(__func__, #parameter, __FILE__, __LINE__, \
                    cast_to_largest_integral_type(parameter))
#endif

#ifdef DOXYGEN
/**
 * @brief Determine whether a function parameter is correct.
 *
 * This ensures the next value queued by one of the expect_*() macros matches
 * the specified variable.
 *
 * This function needs to be called in the mock object.
 *
 * @param[in]  #parameter  The pointer to check.
 */
void check_expected_ptr(#parameter);
#else
#define check_expected_ptr(parameter) \
    _check_expected(__func__, #parameter, __FILE__, __LINE__, \
                    cast_ptr_to_largest_integral_type(parameter))
#endif

/** @} */

/**
 * @defgroup cmocka_asserts Assert Macros
 * @ingroup cmocka
 *
 * This is a set of useful assert macros like the standard C libary's
 * assert(3) macro.
 *
 * On an assertion failure a cmocka assert macro will write the failure to the
 * standard error stream and signal a test failure. Due to limitations of the C
 * language the general C standard library assert() and cmocka's assert_true()
 * and assert_false() macros can only display the expression that caused the
 * assert failure. cmocka's type specific assert macros, assert_{type}_equal()
 * and assert_{type}_not_equal(), display the data that caused the assertion
 * failure which increases data visibility aiding debugging of failing test
 * cases.
 *
 * @{
 */

#ifdef DOXYGEN
/**
 * @brief Assert that the given expression is true.
 *
 * The function prints an error message to standard error and terminates the
 * test by calling fail() if expression is false (i.e., compares equal to
 * zero).
 *
 * @param[in]  expression  The expression to evaluate.
 *
 * @see assert_int_equal()
 * @see assert_string_equal()
 */
void assert_true(scalar expression);
#else
#define assert_true(c) _assert_true(cast_to_largest_integral_type(c), #c, \
                                    __FILE__, __LINE__)
#endif

#ifdef DOXYGEN
/**
 * @brief Assert that the given expression is false.
 *
 * The function prints an error message to standard error and terminates the
 * test by calling fail() if expression is true.
 *
 * @param[in]  expression  The expression to evaluate.
 *
 * @see assert_int_equal()
 * @see assert_string_equal()
 */
void assert_false(scalar expression);
#else
#define assert_false(c) _assert_true(!(cast_to_largest_integral_type(c)), #c, \
                                     __FILE__, __LINE__)
#endif

#ifdef DOXYGEN
/**
 * @brief Assert that the return_code is greater than or equal to 0.
 *
 * The function prints an error message to standard error and terminates the
 * test by calling fail() if the return code is smaller than 0. If the function
 * you check sets an errno if it fails you can pass it to the function and
 * it will be printed as part of the error message.
 *
 * @param[in]  rc       The return code to evaluate.
 *
 * @param[in]  error    Pass errno here or 0.
 */
void assert_return_code(int rc, int error);
#else
#define assert_return_code(rc, error) \
    _assert_return_code(cast_to_largest_integral_type(rc), \
                        sizeof(rc), \
                        cast_to_largest_integral_type(error), \
                        #rc, __FILE__, __LINE__)
#endif

#ifdef DOXYGEN
/**
 * @brief Assert that the given pointer is non-NULL.
 *
 * The function prints an error message to standard error and terminates the
 * test by calling fail() if the pointer is non-NULL.
 *
 * @param[in]  pointer  The pointer to evaluate.
 *
 * @see assert_null()
 */
void assert_non_null(void *pointer);
#else
#define assert_non_null(c) _assert_true(cast_ptr_to_largest_integral_type(c), #c, \
                                        __FILE__, __LINE__)
#endif

#ifdef DOXYGEN
/**
 * @brief Assert that the given pointer is NULL.
 *
 * The function prints an error message to standard error and terminates the
 * test by calling fail() if the pointer is non-NULL.
 *
 * @param[in]  pointer  The pointer to evaluate.
 *
 * @see assert_non_null()
 */
void assert_null(void *pointer);
#else
#define assert_null(c) _assert_true(!(cast_ptr_to_largest_integral_type(c)), #c, \
__FILE__, __LINE__)
#endif

#ifdef DOXYGEN
/**
 * @brief Assert that the two given pointers are equal.
 *
 * The function prints an error message and terminates the test by calling
 * fail() if the pointers are not equal.
 *
 * @param[in]  a        The first pointer to compare.
 *
 * @param[in]  b        The pointer to compare against the first one.
 */
void assert_ptr_equal(void *a, void *b);
#else
#define assert_ptr_equal(a, b) \
    _assert_int_equal(cast_ptr_to_largest_integral_type(a), \
                      cast_ptr_to_largest_integral_type(b), \
                      __FILE__, __LINE__)
#endif

#ifdef DOXYGEN
/**
 * @brief Assert that the two given pointers are not equal.
 *
 * The function prints an error message and terminates the test by calling
 * fail() if the pointers are equal.
 *
 * @param[in]  a        The first pointer to compare.
 *
 * @param[in]  b        The pointer to compare against the first one.
 */
void assert_ptr_not_equal(void *a, void *b);
#else
#define assert_ptr_not_equal(a, b) \
    _assert_int_not_equal(cast_ptr_to_largest_integral_type(a), \
                          cast_ptr_to_largest_integral_type(b), \
                          __FILE__, __LINE__)
#endif

#ifdef DOXYGEN
/**
 * @brief Assert that the two given integers are equal.
 *
 * The function prints an error message to standard error and terminates the
 * test by calling fail() if the integers are not equal.
 *
 * @param[in]  a  The first integer to compare.
 *
 * @param[in]  b  The integer to compare against the first one.
 */
void assert_int_equal(int a, int b);
#else
#define assert_int_equal(a, b) \
    _assert_int_equal(cast_to_largest_integral_type(a), \
                      cast_to_largest_integral_type(b), \
                      __FILE__, __LINE__)
#endif

#ifdef DOXYGEN
/**
 * @brief Assert that the two given integers are not equal.
 *
 * The function prints an error message to standard error and terminates the
 * test by calling fail() if the integers are equal.
 *
 * @param[in]  a  The first integer to compare.
 *
 * @param[in]  b  The integer to compare against the first one.
 *
 * @see assert_int_equal()
 */
void assert_int_not_equal(int a, int b);
#else
#define assert_int_not_equal(a, b) \
    _assert_int_not_equal(cast_to_largest_integral_type(a), \
                          cast_to_largest_integral_type(b), \
                          __FILE__, __LINE__)
#endif

#ifdef DOXYGEN
/**
 * @brief Assert that the two given strings are equal.
 *
 * The function prints an error message to standard error and terminates the
 * test by calling fail() if the strings are not equal.
 *
 * @param[in]  a  The string to check.
 *
 * @param[in]  b  The other string to compare.
 */
void assert_string_equal(const char *a, const char *b);
#else
#define assert_string_equal(a, b) \
    _assert_string_equal((const char*)(a), (const char*)(b), __FILE__, \
                         __LINE__)
#endif

#ifdef DOXYGEN
/**
 * @brief Assert that the two given strings are not equal.
 *
 * The function prints an error message to standard error and terminates the
 * test by calling fail() if the strings are equal.
 *
 * @param[in]  a  The string to check.
 *
 * @param[in]  b  The other string to compare.
 */
void assert_string_not_equal(const char *a, const char *b);
#else
#define assert_string_not_equal(a, b) \
    _assert_string_not_equal((const char*)(a), (const char*)(b), __FILE__, \
                             __LINE__)
#endif

#ifdef DOXYGEN
/**
 * @brief Assert that the two given areas of memory are equal, otherwise fail.
 *
 * The function prints an error message to standard error and terminates the
 * test by calling fail() if the memory is not equal.
 *
 * @param[in]  a  The first memory area to compare
 *                (interpreted as unsigned char).
 *
 * @param[in]  b  The second memory area to compare
 *                (interpreted as unsigned char).
 *
 * @param[in]  size  The first n bytes of the memory areas to compare.
 */
void assert_memory_equal(const void *a, const void *b, size_t size);
#else
#define assert_memory_equal(a, b, size) \
    _assert_memory_equal((const void*)(a), (const void*)(b), size, __FILE__, \
                         __LINE__)
#endif

#ifdef DOXYGEN
/**
 * @brief Assert that the two given areas of memory are not equal.
 *
 * The function prints an error message to standard error and terminates the
 * test by calling fail() if the memory is equal.
 *
 * @param[in]  a  The first memory area to compare
 *                (interpreted as unsigned char).
 *
 * @param[in]  b  The second memory area to compare
 *                (interpreted as unsigned char).
 *
 * @param[in]  size  The first n bytes of the memory areas to compare.
 */
void assert_memory_not_equal(const void *a, const void *b, size_t size);
#else
#define assert_memory_not_equal(a, b, size) \
    _assert_memory_not_equal((const void*)(a), (const void*)(b), size, \
                             __FILE__, __LINE__)
#endif

#ifdef DOXYGEN
/**
 * @brief Assert that the specified value is not smaller than the minimum
 * and and not greater than the maximum.
 *
 * The function prints an error message to standard error and terminates the
 * test by calling fail() if value is not in range.
 *
 * @param[in]  value  The value to check.
 *
 * @param[in]  minimum  The minimum value allowed.
 *
 * @param[in]  maximum  The maximum value allowed.
 */
void assert_in_range(LargestIntegralType value, LargestIntegralType minimum, LargestIntegralType maximum);
#else
#define assert_in_range(value, minimum, maximum) \
    _assert_in_range( \
        cast_to_largest_integral_type(value), \
        cast_to_largest_integral_type(minimum), \
        cast_to_largest_integral_type(maximum), __FILE__, __LINE__)
#endif

#ifdef DOXYGEN
/**
 * @brief Assert that the specified value is smaller than the minimum or
 * greater than the maximum.
 *
 * The function prints an error message to standard error and terminates the
 * test by calling fail() if value is in range.
 *
 * @param[in]  value  The value to check.
 *
 * @param[in]  minimum  The minimum value to compare.
 *
 * @param[in]  maximum  The maximum value to compare.
 */
void assert_not_in_range(LargestIntegralType value, LargestIntegralType minimum, LargestIntegralType maximum);
#else
#define assert_not_in_range(value, minimum, maximum) \
    _assert_not_in_range( \
        cast_to_largest_integral_type(value), \
        cast_to_largest_integral_type(minimum), \
        cast_to_largest_integral_type(maximum), __FILE__, __LINE__)
#endif

#ifdef DOXYGEN
/**
 * @brief Assert that the specified value is within a set.
 *
 * The function prints an error message to standard error and terminates the
 * test by calling fail() if value is not within a set.
 *
 * @param[in]  value  The value to look up
 *
 * @param[in]  values[]  The array to check for the value.
 *
 * @param[in]  count  The size of the values array.
 */
void assert_in_set(LargestIntegralType value, LargestIntegralType values[], size_t count);
#else
#define assert_in_set(value, values, number_of_values) \
    _assert_in_set(value, values, number_of_values, __FILE__, __LINE__)
#endif

#ifdef DOXYGEN
/**
 * @brief Assert that the specified value is not within a set.
 *
 * The function prints an error message to standard error and terminates the
 * test by calling fail() if value is within a set.
 *
 * @param[in]  value  The value to look up
 *
 * @param[in]  values[]  The array to check for the value.
 *
 * @param[in]  count  The size of the values array.
 */
void assert_not_in_set(LargestIntegralType value, LargestIntegralType values[], size_t count);
#else
#define assert_not_in_set(value, values, number_of_values) \
    _assert_not_in_set(value, values, number_of_values, __FILE__, __LINE__)
#endif

/** @} */

/**
 * @defgroup cmocka_call_order Call Ordering
 * @ingroup cmocka
 *
 * It is often beneficial to  make sure that functions are called in an
 * order. This is independent of mock returns and parameter checking as both
 * of the aforementioned do not check the order in which they are called from
 * different functions.
 *
 * <ul>
 * <li><strong>expect_function_call(function)</strong> - The
 * expect_function_call() macro pushes an expectation onto the stack of
 * expected calls.</li>
 *
 * <li><strong>function_called()</strong> - pops a value from the stack of
 * expected calls. function_called() is invoked within the mock object
 * that uses it.
 * </ul>
 *
 * expect_function_call() and function_called() are intended to be used in
 * pairs. Cmocka will fail a test if there are more or less expected calls
 * created (e.g. expect_function_call()) than consumed with function_called().
 * There are provisions such as ignore_function_calls() which allow this
 * restriction to be circumvented in tests where mock calls for the code under
 * test are not the focus of the test.
 *
 * The following example illustrates how a unit test instructs cmocka
 * to expect a function_called() from a particular mock,
 * <strong>chef_sing()</strong>:
 *
 * @code
 * void chef_sing(void);
 *
 * void code_under_test()
 * {
 *   chef_sing();
 * }
 *
 * void some_test(void **state)
 * {
 *     expect_function_call(chef_sing);
 *     code_under_test();
 * }
 * @endcode
 *
 * The implementation of the mock then must check whether it was meant to
 * be called by invoking <strong>function_called()</strong>:
 *
 * @code
 * void chef_sing()
 * {
 *     function_called();
 * }
 * @endcode
 *
 * @{
 */

#ifdef DOXYGEN
/**
 * @brief Check that current mocked function is being called in the expected
 *        order
 *
 * @see expect_function_call()
 */
void function_called(void);
#else
#define function_called() _function_called(__func__, __FILE__, __LINE__)
#endif

#ifdef DOXYGEN
/**
 * @brief Store expected call(s) to a mock to be checked by function_called()
 *        later.
 *
 * @param[in]  #function  The function which should should be called
 *
 * @param[in]  times number of times this mock must be called
 *
 * @see function_called()
 */
void expect_function_calls(#function, const int times);
#else
#define expect_function_calls(function, times) \
    _expect_function_call(#function, __FILE__, __LINE__, times)
#endif

#ifdef DOXYGEN
/**
 * @brief Store expected single call to a mock to be checked by
 *        function_called() later.
 *
 * @param[in]  #function  The function which should should be called
 *
 * @see function_called()
 */
void expect_function_call(#function);
#else
#define expect_function_call(function) \
    _expect_function_call(#function, __FILE__, __LINE__, 1)
#endif

#ifdef DOXYGEN
/**
 * @brief Expects function_called() from given mock at least once
 *
 * @param[in]  #function  The function which should should be called
 *
 * @see function_called()
 */
void expect_function_call_any(#function);
#else
#define expect_function_call_any(function) \
    _expect_function_call(#function, __FILE__, __LINE__, -1)
#endif

#ifdef DOXYGEN
/**
 * @brief Ignores function_called() invocations from given mock function.
 *
 * @param[in]  #function  The function which should should be called
 *
 * @see function_called()
 */
void ignore_function_calls(#function);
#else
#define ignore_function_calls(function) \
    _expect_function_call(#function, __FILE__, __LINE__, -2)
#endif

/** @} */

/**
 * @defgroup cmocka_exec Running Tests
 * @ingroup cmocka
 *
 * This is the way tests are executed with CMocka.
 *
 * The following example illustrates this macro's use with the unit_test macro.
 *
 * @code
 * void Test0(void **state);
 * void Test1(void **state);
 *
 * int main(void)
 * {
 *     const struct CMUnitTest tests[] = {
 *         cmocka_unit_test(Test0),
 *         cmocka_unit_test(Test1),
 *     };
 *
 *     return cmocka_run_group_tests(tests, NULL, NULL);
 * }
 * @endcode
 *
 * @{
 */

#ifdef DOXYGEN
/**
 * @brief Forces the test to fail immediately and quit.
 */
void fail(void);
#else
#define fail() _fail(__FILE__, __LINE__)
#endif

#ifdef DOXYGEN
/**
 * @brief Forces the test to not be executed, but marked as skipped
 */
void skip(void);
#else
#define skip() _skip(__FILE__, __LINE__)
#endif

#ifdef DOXYGEN
/**
 * @brief Forces the test to fail immediately and quit, printing the reason.
 *
 * @code
 * fail_msg("This is some error message for test");
 * @endcode
 *
 * or
 *
 * @code
 * char *error_msg = "This is some error message for test";
 * fail_msg("%s", error_msg);
 * @endcode
 */
void fail_msg(const char *msg, ...);
#else
#define fail_msg(msg, ...) do { \
    print_error("ERROR: " msg "\n", ##__VA_ARGS__); \
    fail(); \
} while (0)
#endif

#ifdef DOXYGEN
/**
 * @brief Generic method to run a single test.
 *
 * @deprecated This function was deprecated in favor of cmocka_run_group_tests
 *
 * @param[in]  #function The function to test.
 *
 * @return 0 on success, 1 if an error occured.
 *
 * @code
 * // A test case that does nothing and succeeds.
 * void null_test_success(void **state) {
 * }
 *
 * int main(void) {
 *      return run_test(null_test_success);
 * }
 * @endcode
 */
int run_test(#function);
#else
#define run_test(f) _run_test(#f, f, NULL, UNIT_TEST_FUNCTION_TYPE_TEST, NULL)
#endif

static inline void _unit_test_dummy(void **state) {
    (void)state;
}

/** Initializes a UnitTest structure.
 *
 * @deprecated This function was deprecated in favor of cmocka_unit_test
 */
#define unit_test(f) { #f, f, UNIT_TEST_FUNCTION_TYPE_TEST }

#define _unit_test_setup(test, setup) \
    { #test "_" #setup, setup, UNIT_TEST_FUNCTION_TYPE_SETUP }

/** Initializes a UnitTest structure with a setup function.
 *
 * @deprecated This function was deprecated in favor of cmocka_unit_test_setup
 */
#define unit_test_setup(test, setup) \
    _unit_test_setup(test, setup), \
    unit_test(test), \
    _unit_test_teardown(test, _unit_test_dummy)

#define _unit_test_teardown(test, teardown) \
    { #test "_" #teardown, teardown, UNIT_TEST_FUNCTION_TYPE_TEARDOWN }

/** Initializes a UnitTest structure with a teardown function.
 *
 * @deprecated This function was deprecated in favor of cmocka_unit_test_teardown
 */
#define unit_test_teardown(test, teardown) \
    _unit_test_setup(test, _unit_test_dummy), \
    unit_test(test), \
    _unit_test_teardown(test, teardown)

/** Initializes a UnitTest structure for a group setup function.
 *
 * @deprecated This function was deprecated in favor of cmocka_run_group_tests
 */
#define group_test_setup(setup) \
    { "group_" #setup, setup, UNIT_TEST_FUNCTION_TYPE_GROUP_SETUP }

/** Initializes a UnitTest structure for a group teardown function.
 *
 * @deprecated This function was deprecated in favor of cmocka_run_group_tests
 */
#define group_test_teardown(teardown) \
    { "group_" #teardown, teardown, UNIT_TEST_FUNCTION_TYPE_GROUP_TEARDOWN }

/**
 * Initialize an array of UnitTest structures with a setup function for a test
 * and a teardown function.  Either setup or teardown can be NULL.
 *
 * @deprecated This function was deprecated in favor of
 * cmocka_unit_test_setup_teardown
 */
#define unit_test_setup_teardown(test, setup, teardown) \
    _unit_test_setup(test, setup), \
    unit_test(test), \
    _unit_test_teardown(test, teardown)


/** Initializes a CMUnitTest structure. */
#define cmocka_unit_test(f) { #f, f, NULL, NULL, NULL }

/** Initializes a CMUnitTest structure with a setup function. */
#define cmocka_unit_test_setup(f, setup) { #f, f, setup, NULL, NULL }

/** Initializes a CMUnitTest structure with a teardown function. */
#define cmocka_unit_test_teardown(f, teardown) { #f, f, NULL, teardown, NULL }

/**
 * Initialize an array of CMUnitTest structures with a setup function for a test
 * and a teardown function. Either setup or teardown can be NULL.
 */
#define cmocka_unit_test_setup_teardown(f, setup, teardown) { #f, f, setup, teardown, NULL }

/**
 * Initialize a CMUnitTest structure with given initial state. It will be passed
 * to test function as an argument later. It can be used when test state does
 * not need special initialization or was initialized already.
 * @note If the group setup function initialized the state already, it won't be
 * overridden by the initial state defined here.
 */
#define cmocka_unit_test_prestate(f, state) { #f, f, NULL, NULL, state }

/**
 * Initialize a CMUnitTest structure with given initial state, setup and
 * teardown function. Any of these values can be NULL. Initial state is passed
 * later to setup function, or directly to test if none was given.
 * @note If the group setup function initialized the state already, it won't be
 * overridden by the initial state defined here.
 */
#define cmocka_unit_test_prestate_setup_teardown(f, setup, teardown, state) { #f, f, setup, teardown, state }

#define run_tests(tests) _run_tests(tests, sizeof(tests) / sizeof(tests)[0])
#define run_group_tests(tests) _run_group_tests(tests, sizeof(tests) / sizeof(tests)[0])

#ifdef DOXYGEN
/**
 * @brief Run tests specified by an array of CMUnitTest structures.
 *
 * @param[in]  group_tests[]  The array of unit tests to execute.
 *
 * @param[in]  group_setup    The setup function which should be called before
 *                            all unit tests are executed.
 *
 * @param[in]  group_teardown The teardown function to be called after all
 *                            tests have finished.
 *
 * @return 0 on success, or the number of failed tests.
 *
 * @code
 * static int setup(void **state) {
 *      int *answer = malloc(sizeof(int));
 *      if (*answer == NULL) {
 *          return -1;
 *      }
 *      *answer = 42;
 *
 *      *state = answer;
 *
 *      return 0;
 * }
 *
 * static int teardown(void **state) {
 *      free(*state);
 *
 *      return 0;
 * }
 *
 * static void null_test_success(void **state) {
 *     (void) state;
 * }
 *
 * static void int_test_success(void **state) {
 *      int *answer = *state;
 *      assert_int_equal(*answer, 42);
 * }
 *
 * int main(void) {
 *     const struct CMUnitTest tests[] = {
 *         cmocka_unit_test(null_test_success),
 *         cmocka_unit_test_setup_teardown(int_test_success, setup, teardown),
 *     };
 *
 *     return cmocka_run_group_tests(tests, NULL, NULL);
 * }
 * @endcode
 *
 * @see cmocka_unit_test
 * @see cmocka_unit_test_setup
 * @see cmocka_unit_test_teardown
 * @see cmocka_unit_test_setup_teardown
 */
int cmocka_run_group_tests(const struct CMUnitTest group_tests[],
                           CMFixtureFunction group_setup,
                           CMFixtureFunction group_teardown);
#else
# define cmocka_run_group_tests(group_tests, group_setup, group_teardown) \
        _cmocka_run_group_tests(#group_tests, group_tests, sizeof(group_tests) / sizeof(group_tests)[0], group_setup, group_teardown)
#endif

#ifdef DOXYGEN
/**
 * @brief Run tests specified by an array of CMUnitTest structures and specify
 *        a name.
 *
 * @param[in]  group_name     The name of the group test.
 *
 * @param[in]  group_tests[]  The array of unit tests to execute.
 *
 * @param[in]  group_setup    The setup function which should be called before
 *                            all unit tests are executed.
 *
 * @param[in]  group_teardown The teardown function to be called after all
 *                            tests have finished.
 *
 * @return 0 on success, or the number of failed tests.
 *
 * @code
 * static int setup(void **state) {
 *      int *answer = malloc(sizeof(int));
 *      if (*answer == NULL) {
 *          return -1;
 *      }
 *      *answer = 42;
 *
 *      *state = answer;
 *
 *      return 0;
 * }
 *
 * static int teardown(void **state) {
 *      free(*state);
 *
 *      return 0;
 * }
 *
 * static void null_test_success(void **state) {
 *     (void) state;
 * }
 *
 * static void int_test_success(void **state) {
 *      int *answer = *state;
 *      assert_int_equal(*answer, 42);
 * }
 *
 * int main(void) {
 *     const struct CMUnitTest tests[] = {
 *         cmocka_unit_test(null_test_success),
 *         cmocka_unit_test_setup_teardown(int_test_success, setup, teardown),
 *     };
 *
 *     return cmocka_run_group_tests_name("success_test", tests, NULL, NULL);
 * }
 * @endcode
 *
 * @see cmocka_unit_test
 * @see cmocka_unit_test_setup
 * @see cmocka_unit_test_teardown
 * @see cmocka_unit_test_setup_teardown
 */
int cmocka_run_group_tests_name(const char *group_name,
                                const struct CMUnitTest group_tests[],
                                CMFixtureFunction group_setup,
                                CMFixtureFunction group_teardown);
#else
# define cmocka_run_group_tests_name(group_name, group_tests, group_setup, group_teardown) \
        _cmocka_run_group_tests(group_name, group_tests, sizeof(group_tests) / sizeof(group_tests)[0], group_setup, group_teardown)
#endif

/** @} */

/**
 * @defgroup cmocka_alloc Dynamic Memory Allocation
 * @ingroup cmocka
 *
 * Memory leaks, buffer overflows and underflows can be checked using cmocka.
 *
 * To test for memory leaks, buffer overflows and underflows a module being
 * tested by cmocka should replace calls to malloc(), calloc() and free() to
 * test_malloc(), test_calloc() and test_free() respectively. Each time a block
 * is deallocated using test_free() it is checked for corruption, if a corrupt
 * block is found a test failure is signalled. All blocks allocated using the
 * test_*() allocation functions are tracked by the cmocka library. When a test
 * completes if any allocated blocks (memory leaks) remain they are reported
 * and a test failure is signalled.
 *
 * For simplicity cmocka currently executes all tests in one process. Therefore
 * all test cases in a test application share a single address space which
 * means memory corruption from a single test case could potentially cause the
 * test application to exit prematurely.
 *
 * @{
 */

#ifdef DOXYGEN
/**
 * @brief Test function overriding malloc.
 *
 * @param[in]  size  The bytes which should be allocated.
 *
 * @return A pointer to the allocated memory or NULL on error.
 *
 * @code
 * #ifdef UNIT_TESTING
 * extern void* _test_malloc(const size_t size, const char* file, const int line);
 *
 * #define malloc(size) _test_malloc(size, __FILE__, __LINE__)
 * #endif
 *
 * void leak_memory() {
 *     int * const temporary = (int*)malloc(sizeof(int));
 *     *temporary = 0;
 * }
 * @endcode
 *
 * @see malloc(3)
 */
void *test_malloc(size_t size);
#else
#define test_malloc(size) _test_malloc(size, __FILE__, __LINE__)
#endif

#ifdef DOXYGEN
/**
 * @brief Test function overriding calloc.
 *
 * The memory is set to zero.
 *
 * @param[in]  nmemb  The number of elements for an array to be allocated.
 *
 * @param[in]  size   The size in bytes of each array element to allocate.
 *
 * @return A pointer to the allocated memory, NULL on error.
 *
 * @see calloc(3)
 */
void *test_calloc(size_t nmemb, size_t size);
#else
#define test_calloc(num, size) _test_calloc(num, size, __FILE__, __LINE__)
#endif

#ifdef DOXYGEN
/**
 * @brief Test function overriding realloc which detects buffer overruns
 *        and memoery leaks.
 *
 * @param[in]  ptr   The memory block which should be changed.
 *
 * @param[in]  size  The bytes which should be allocated.
 *
 * @return           The newly allocated memory block, NULL on error.
 */
void *test_realloc(void *ptr, size_t size);
#else
#define test_realloc(ptr, size) _test_realloc(ptr, size, __FILE__, __LINE__)
#endif

#ifdef DOXYGEN
/**
 * @brief Test function overriding free(3).
 *
 * @param[in]  ptr  The pointer to the memory space to free.
 *
 * @see free(3).
 */
void test_free(void *ptr);
#else
#define test_free(ptr) _test_free(ptr, __FILE__, __LINE__)
#endif

/* Redirect malloc, calloc and free to the unit test allocators. */
#ifdef UNIT_TESTING
#define malloc test_malloc
#define realloc test_realloc
#define calloc test_calloc
#define free test_free
#endif /* UNIT_TESTING */

/** @} */


/**
 * @defgroup cmocka_mock_assert Standard Assertions
 * @ingroup cmocka
 *
 * How to handle assert(3) of the standard C library.
 *
 * Runtime assert macros like the standard C library's assert() should be
 * redefined in modules being tested to use cmocka's mock_assert() function.
 * Normally mock_assert() signals a test failure. If a function is called using
 * the expect_assert_failure() macro, any calls to mock_assert() within the
 * function will result in the execution of the test. If no calls to
 * mock_assert() occur during the function called via expect_assert_failure() a
 * test failure is signalled.
 *
 * @{
 */

/**
 * @brief Function to replace assert(3) in tested code.
 *
 * In conjuction with check_assert() it's possible to determine whether an
 * assert condition has failed without stopping a test.
 *
 * @param[in]  result  The expression to assert.
 *
 * @param[in]  expression  The expression as string.
 *
 * @param[in]  file  The file mock_assert() is called.
 *
 * @param[in]  line  The line mock_assert() is called.
 *
 * @code
 * #ifdef UNIT_TESTING
 * extern void mock_assert(const int result, const char* const expression,
 *                         const char * const file, const int line);
 *
 * #undef assert
 * #define assert(expression) \
 *     mock_assert((int)(expression), #expression, __FILE__, __LINE__);
 * #endif
 *
 * void increment_value(int * const value) {
 *     assert(value);
 *     (*value) ++;
 * }
 * @endcode
 *
 * @see assert(3)
 * @see expect_assert_failure
 */
void mock_assert(const int result, const char* const expression,
                 const char * const file, const int line);

#ifdef DOXYGEN
/**
 * @brief Ensure that mock_assert() is called.
 *
 * If mock_assert() is called the assert expression string is returned.
 *
 * @param[in]  fn_call  The function will will call mock_assert().
 *
 * @code
 * #define assert mock_assert
 *
 * void showmessage(const char *message) {
 *   assert(message);
 * }
 *
 * int main(int argc, const char* argv[]) {
 *   expect_assert_failure(show_message(NULL));
 *   printf("succeeded\n");
 *   return 0;
 * }
 * @endcode
 *
 */
void expect_assert_failure(function fn_call);
#else
#define expect_assert_failure(function_call) \
  { \
    const int result = setjmp(global_expect_assert_env); \
    global_expecting_assert = 1; \
    if (result) { \
      print_message("Expected assertion %s occurred\n", \
                    global_last_failed_assert); \
      global_expecting_assert = 0; \
    } else { \
      function_call ; \
      global_expecting_assert = 0; \
      print_error("Expected assert in %s\n", #function_call); \
      _fail(__FILE__, __LINE__); \
    } \
  }
#endif

/** @} */

/* Function prototype for setup, test and teardown functions. */
typedef void (*UnitTestFunction)(void **state);

/* Function that determines whether a function parameter value is correct. */
typedef int (*CheckParameterValue)(const LargestIntegralType value,
                                   const LargestIntegralType check_value_data);

/* Type of the unit test function. */
typedef enum UnitTestFunctionType {
    UNIT_TEST_FUNCTION_TYPE_TEST = 0,
    UNIT_TEST_FUNCTION_TYPE_SETUP,
    UNIT_TEST_FUNCTION_TYPE_TEARDOWN,
    UNIT_TEST_FUNCTION_TYPE_GROUP_SETUP,
    UNIT_TEST_FUNCTION_TYPE_GROUP_TEARDOWN,
} UnitTestFunctionType;

/*
 * Stores a unit test function with its name and type.
 * NOTE: Every setup function must be paired with a teardown function.  It's
 * possible to specify NULL function pointers.
 */
typedef struct UnitTest {
    const char* name;
    UnitTestFunction function;
    UnitTestFunctionType function_type;
} UnitTest;

typedef struct GroupTest {
    UnitTestFunction setup;
    UnitTestFunction teardown;
    const UnitTest *tests;
    const size_t number_of_tests;
} GroupTest;

/* Function prototype for test functions. */
typedef void (*CMUnitTestFunction)(void **state);

/* Function prototype for setup and teardown functions. */
typedef int (*CMFixtureFunction)(void **state);

struct CMUnitTest {
    const char *name;
    CMUnitTestFunction test_func;
    CMFixtureFunction setup_func;
    CMFixtureFunction teardown_func;
    void *initial_state;
};

/* Location within some source code. */
typedef struct SourceLocation {
    const char* file;
    int line;
} SourceLocation;

/* Event that's called to check a parameter value. */
typedef struct CheckParameterEvent {
    SourceLocation location;
    const char *parameter_name;
    CheckParameterValue check_value;
    LargestIntegralType check_value_data;
} CheckParameterEvent;

/* Used by expect_assert_failure() and mock_assert(). */
extern int global_expecting_assert;
extern jmp_buf global_expect_assert_env;
extern const char * global_last_failed_assert;

/* Retrieves a value for the given function, as set by "will_return". */
LargestIntegralType _mock(const char * const function, const char* const file,
                          const int line);

void _expect_function_call(
    const char * const function_name,
    const char * const file,
    const int line,
    const int count);

void _function_called(const char * const function, const char* const file,
                          const int line);

void _expect_check(
    const char* const function, const char* const parameter,
    const char* const file, const int line,
    const CheckParameterValue check_function,
    const LargestIntegralType check_data, CheckParameterEvent * const event,
    const int count);

void _expect_in_set(
    const char* const function, const char* const parameter,
    const char* const file, const int line, const LargestIntegralType values[],
    const size_t number_of_values, const int count);
void _expect_not_in_set(
    const char* const function, const char* const parameter,
    const char* const file, const int line, const LargestIntegralType values[],
    const size_t number_of_values, const int count);

void _expect_in_range(
    const char* const function, const char* const parameter,
    const char* const file, const int line,
    const LargestIntegralType minimum,
    const LargestIntegralType maximum, const int count);
void _expect_not_in_range(
    const char* const function, const char* const parameter,
    const char* const file, const int line,
    const LargestIntegralType minimum,
    const LargestIntegralType maximum, const int count);

void _expect_value(
    const char* const function, const char* const parameter,
    const char* const file, const int line, const LargestIntegralType value,
    const int count);
void _expect_not_value(
    const char* const function, const char* const parameter,
    const char* const file, const int line, const LargestIntegralType value,
    const int count);

void _expect_string(
    const char* const function, const char* const parameter,
    const char* const file, const int line, const char* string,
    const int count);
void _expect_not_string(
    const char* const function, const char* const parameter,
    const char* const file, const int line, const char* string,
    const int count);

void _expect_memory(
    const char* const function, const char* const parameter,
    const char* const file, const int line, const void* const memory,
    const size_t size, const int count);
void _expect_not_memory(
    const char* const function, const char* const parameter,
    const char* const file, const int line, const void* const memory,
    const size_t size, const int count);

void _expect_any(
    const char* const function, const char* const parameter,
    const char* const file, const int line, const int count);

void _check_expected(
    const char * const function_name, const char * const parameter_name,
    const char* file, const int line, const LargestIntegralType value);

void _will_return(const char * const function_name, const char * const file,
                  const int line, const LargestIntegralType value,
                  const int count);
void _assert_true(const LargestIntegralType result,
                  const char* const expression,
                  const char * const file, const int line);
void _assert_return_code(const LargestIntegralType result,
                         size_t rlen,
                         const LargestIntegralType error,
                         const char * const expression,
                         const char * const file,
                         const int line);
void _assert_int_equal(
    const LargestIntegralType a, const LargestIntegralType b,
    const char * const file, const int line);
void _assert_int_not_equal(
    const LargestIntegralType a, const LargestIntegralType b,
    const char * const file, const int line);
void _assert_string_equal(const char * const a, const char * const b,
                          const char * const file, const int line);
void _assert_string_not_equal(const char * const a, const char * const b,
                              const char *file, const int line);
void _assert_memory_equal(const void * const a, const void * const b,
                          const size_t size, const char* const file,
                          const int line);
void _assert_memory_not_equal(const void * const a, const void * const b,
                              const size_t size, const char* const file,
                              const int line);
void _assert_in_range(
    const LargestIntegralType value, const LargestIntegralType minimum,
    const LargestIntegralType maximum, const char* const file, const int line);
void _assert_not_in_range(
    const LargestIntegralType value, const LargestIntegralType minimum,
    const LargestIntegralType maximum, const char* const file, const int line);
void _assert_in_set(
    const LargestIntegralType value, const LargestIntegralType values[],
    const size_t number_of_values, const char* const file, const int line);
void _assert_not_in_set(
    const LargestIntegralType value, const LargestIntegralType values[],
    const size_t number_of_values, const char* const file, const int line);

void* _test_malloc(const size_t size, const char* file, const int line);
void* _test_realloc(void *ptr, const size_t size, const char* file, const int line);
void* _test_calloc(const size_t number_of_elements, const size_t size,
                   const char* file, const int line);
void _test_free(void* const ptr, const char* file, const int line);

void _fail(const char * const file, const int line);

void _skip(const char * const file, const int line);

int _run_test(
    const char * const function_name, const UnitTestFunction Function,
    void ** const volatile state, const UnitTestFunctionType function_type,
    const void* const heap_check_point);
CMOCKA_DEPRECATED int _run_tests(const UnitTest * const tests,
                                 const size_t number_of_tests);
CMOCKA_DEPRECATED int _run_group_tests(const UnitTest * const tests,
                                       const size_t number_of_tests);

/* Test runner */
int _cmocka_run_group_tests(const char *group_name,
                            const struct CMUnitTest * const tests,
                            const size_t num_tests,
                            CMFixtureFunction group_setup,
                            CMFixtureFunction group_teardown);

/* Standard output and error print methods. */
void print_message(const char* const format, ...) CMOCKA_PRINTF_ATTRIBUTE(1, 2);
void print_error(const char* const format, ...) CMOCKA_PRINTF_ATTRIBUTE(1, 2);
void vprint_message(const char* const format, va_list args) CMOCKA_PRINTF_ATTRIBUTE(1, 0);
void vprint_error(const char* const format, va_list args) CMOCKA_PRINTF_ATTRIBUTE(1, 0);

enum cm_message_output {
    CM_OUTPUT_STDOUT,
    CM_OUTPUT_SUBUNIT,
    CM_OUTPUT_TAP,
    CM_OUTPUT_XML,
};

/**
 * @brief Function to set the output format for a test.
 *
 * The ouput format for the test can either be set globally using this
 * function or overriden with environment variable CMOCKA_MESSAGE_OUTPUT.
 *
 * The environment variable can be set to either STDOUT, SUBUNIT, TAP or XML.
 *
 * @param[in] output    The output format to use for the test.
 *
 */
void cmocka_set_message_output(enum cm_message_output output);

/** @} */

#endif /* CMOCKA_H_ */
