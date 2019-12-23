/*
 * Copyright 2008 Google Inc.
 * Copyright 2014-2015 Andreas Schneider <asn@cryptomilk.org>
 * Copyright 2015      Jakub Hrozek <jakub.hrozek@posteo.se>
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
#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#ifdef HAVE_MALLOC_H
#include <malloc.h>
#endif

#ifdef HAVE_INTTYPES_H
#include <inttypes.h>
#endif

#ifdef HAVE_SIGNAL_H
#include <signal.h>
#endif

#ifdef HAVE_STRINGS_H
#include <strings.h>
#endif

#include <stdint.h>
#include <setjmp.h>
#include <stdarg.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

/*
 * This allows to add a platform specific header file. Some embedded platforms
 * sometimes miss certain types and definitions.
 *
 * Example:
 *
 * typedef unsigned long int uintptr_t
 * #define _UINTPTR_T 1
 * #define _UINTPTR_T_DEFINED 1
 */
#ifdef CMOCKA_PLATFORM_INCLUDE
# include "cmocka_platform.h"
#endif /* CMOCKA_PLATFORM_INCLUDE */

#include <cmocka.h>
#include <cmocka_private.h>

/* Size of guard bytes around dynamically allocated blocks. */
#define MALLOC_GUARD_SIZE 16
/* Pattern used to initialize guard blocks. */
#define MALLOC_GUARD_PATTERN 0xEF
/* Pattern used to initialize memory allocated with test_malloc(). */
#define MALLOC_ALLOC_PATTERN 0xBA
#define MALLOC_FREE_PATTERN 0xCD
/* Alignment of allocated blocks.  NOTE: This must be base2. */
#define MALLOC_ALIGNMENT sizeof(size_t)

/* Printf formatting for source code locations. */
#define SOURCE_LOCATION_FORMAT "%s:%u"

#if defined(HAVE_GCC_THREAD_LOCAL_STORAGE)
# define CMOCKA_THREAD __thread
#elif defined(HAVE_MSVC_THREAD_LOCAL_STORAGE)
# define CMOCKA_THREAD __declspec(thread)
#else
# define CMOCKA_THREAD
#endif

#ifdef HAVE_CLOCK_GETTIME_REALTIME
#define CMOCKA_CLOCK_GETTIME(clock_id, ts) clock_gettime((clock_id), (ts))
#else
#define CMOCKA_CLOCK_GETTIME(clock_id, ts)
#endif

/**
 * POSIX has sigsetjmp/siglongjmp, while Windows only has setjmp/longjmp.
 */
#ifdef HAVE_SIGLONGJMP
# define cm_jmp_buf             sigjmp_buf
# define cm_setjmp(env)         sigsetjmp(env, 1)
# define cm_longjmp(env, val)   siglongjmp(env, val)
#else
# define cm_jmp_buf             jmp_buf
# define cm_setjmp(env)         setjmp(env)
# define cm_longjmp(env, val)   longjmp(env, val)
#endif


/*
 * Declare and initialize the pointer member of ValuePointer variable name
 * with ptr.
 */
#define declare_initialize_value_pointer_pointer(name, ptr) \
    ValuePointer name ; \
    name.value = 0; \
    name.x.pointer = (void*)(ptr)

/*
 * Declare and initialize the value member of ValuePointer variable name
 * with val.
 */
#define declare_initialize_value_pointer_value(name, val) \
    ValuePointer name ; \
    name.value = val

/* Cast a LargestIntegralType to pointer_type via a ValuePointer. */
#define cast_largest_integral_type_to_pointer( \
    pointer_type, largest_integral_type) \
    ((pointer_type)((ValuePointer*)&(largest_integral_type))->x.pointer)

/* Used to cast LargetIntegralType to void* and vice versa. */
typedef union ValuePointer {
    LargestIntegralType value;
    struct {
#if defined(WORDS_BIGENDIAN) && (WORDS_SIZEOF_VOID_P == 4)
        unsigned int padding;
#endif
        void *pointer;
    } x;
} ValuePointer;

/* Doubly linked list node. */
typedef struct ListNode {
    const void *value;
    int refcount;
    struct ListNode *next;
    struct ListNode *prev;
} ListNode;

/* Debug information for malloc(). */
typedef struct MallocBlockInfo {
    void* block;              /* Address of the block returned by malloc(). */
    size_t allocated_size;    /* Total size of the allocated block. */
    size_t size;              /* Request block size. */
    SourceLocation location;  /* Where the block was allocated. */
    ListNode node;            /* Node within list of all allocated blocks. */
} MallocBlockInfo;

/* State of each test. */
typedef struct TestState {
    const ListNode *check_point; /* Check point of the test if there's a */
                                 /* setup function. */
    void *state;                 /* State associated with the test. */
} TestState;

/* Determines whether two values are the same. */
typedef int (*EqualityFunction)(const void *left, const void *right);

/* Value of a symbol and the place it was declared. */
typedef struct SymbolValue {
    SourceLocation location;
    LargestIntegralType value;
} SymbolValue;

/*
 * Contains a list of values for a symbol.
 * NOTE: Each structure referenced by symbol_values_list_head must have a
 * SourceLocation as its' first member.
 */
typedef struct SymbolMapValue {
    const char *symbol_name;
    ListNode symbol_values_list_head;
} SymbolMapValue;

/* Where a particular ordering was located and its symbol name */
typedef struct FuncOrderingValue {
    SourceLocation location;
    const char * function;
} FuncOrderingValue;

/* Used by list_free() to deallocate values referenced by list nodes. */
typedef void (*CleanupListValue)(const void *value, void *cleanup_value_data);

/* Structure used to check the range of integer types.a */
typedef struct CheckIntegerRange {
    CheckParameterEvent event;
    LargestIntegralType minimum;
    LargestIntegralType maximum;
} CheckIntegerRange;

/* Structure used to check whether an integer value is in a set. */
typedef struct CheckIntegerSet {
    CheckParameterEvent event;
    const LargestIntegralType *set;
    size_t size_of_set;
} CheckIntegerSet;

/* Used to check whether a parameter matches the area of memory referenced by
 * this structure.  */
typedef struct CheckMemoryData {
    CheckParameterEvent event;
    const void *memory;
    size_t size;
} CheckMemoryData;

static ListNode* list_initialize(ListNode * const node);
static ListNode* list_add(ListNode * const head, ListNode *new_node);
static ListNode* list_add_value(ListNode * const head, const void *value,
                                     const int count);
static ListNode* list_remove(
    ListNode * const node, const CleanupListValue cleanup_value,
    void * const cleanup_value_data);
static void list_remove_free(
    ListNode * const node, const CleanupListValue cleanup_value,
    void * const cleanup_value_data);
static int list_empty(const ListNode * const head);
static int list_find(
    ListNode * const head, const void *value,
    const EqualityFunction equal_func, ListNode **output);
static int list_first(ListNode * const head, ListNode **output);
static ListNode* list_free(
    ListNode * const head, const CleanupListValue cleanup_value,
    void * const cleanup_value_data);

static void add_symbol_value(
    ListNode * const symbol_map_head, const char * const symbol_names[],
    const size_t number_of_symbol_names, const void* value, const int count);
static int get_symbol_value(
    ListNode * const symbol_map_head, const char * const symbol_names[],
    const size_t number_of_symbol_names, void **output);
static void free_value(const void *value, void *cleanup_value_data);
static void free_symbol_map_value(
    const void *value, void *cleanup_value_data);
static void remove_always_return_values(ListNode * const map_head,
                                        const size_t number_of_symbol_names);

static int check_for_leftover_values_list(const ListNode * head,
    const char * const error_message);

static int check_for_leftover_values(
    const ListNode * const map_head, const char * const error_message,
    const size_t number_of_symbol_names);

static void remove_always_return_values_from_list(ListNode * const map_head);

/*
 * This must be called at the beginning of a test to initialize some data
 * structures.
 */
static void initialize_testing(const char *test_name);

/* This must be called at the end of a test to free() allocated structures. */
static void teardown_testing(const char *test_name);

static enum cm_message_output cm_get_output(void);

static int cm_error_message_enabled = 1;
static CMOCKA_THREAD char *cm_error_message;

void cm_print_error(const char * const format, ...) CMOCKA_PRINTF_ATTRIBUTE(1, 2);

/*
 * Keeps track of the calling context returned by setenv() so that the fail()
 * method can jump out of a test.
 */
static CMOCKA_THREAD cm_jmp_buf global_run_test_env;
static CMOCKA_THREAD int global_running_test = 0;

/* Keeps track of the calling context returned by setenv() so that */
/* mock_assert() can optionally jump back to expect_assert_failure(). */
jmp_buf global_expect_assert_env;
int global_expecting_assert = 0;
const char *global_last_failed_assert = NULL;
static int global_skip_test;

/* Keeps a map of the values that functions will have to return to provide */
/* mocked interfaces. */
static CMOCKA_THREAD ListNode global_function_result_map_head;
/* Location of the last mock value returned was declared. */
static CMOCKA_THREAD SourceLocation global_last_mock_value_location;

/* Keeps a map of the values that functions expect as parameters to their
 * mocked interfaces. */
static CMOCKA_THREAD ListNode global_function_parameter_map_head;
/* Location of last parameter value checked was declared. */
static CMOCKA_THREAD SourceLocation global_last_parameter_location;

/* List (acting as FIFO) of call ordering. */
static CMOCKA_THREAD ListNode global_call_ordering_head;
/* Location of last call ordering that was declared. */
static CMOCKA_THREAD SourceLocation global_last_call_ordering_location;

/* List of all currently allocated blocks. */
static CMOCKA_THREAD ListNode global_allocated_blocks;

static enum cm_message_output global_msg_output = CM_OUTPUT_STDOUT;

#ifndef _WIN32
/* Signals caught by exception_handler(). */
static const int exception_signals[] = {
    SIGFPE,
    SIGILL,
    SIGSEGV,
#ifdef SIGBUS
    SIGBUS,
#endif
#ifdef SIGSYS
    SIGSYS,
#endif
};

/* Default signal functions that should be restored after a test is complete. */
typedef void (*SignalFunction)(int signal);
static SignalFunction default_signal_functions[
    ARRAY_SIZE(exception_signals)];

#else /* _WIN32 */

/* The default exception filter. */
static LPTOP_LEVEL_EXCEPTION_FILTER previous_exception_filter;

/* Fatal exceptions. */
typedef struct ExceptionCodeInfo {
    DWORD code;
    const char* description;
} ExceptionCodeInfo;

#define EXCEPTION_CODE_INFO(exception_code) {exception_code, #exception_code}

static const ExceptionCodeInfo exception_codes[] = {
    EXCEPTION_CODE_INFO(EXCEPTION_ACCESS_VIOLATION),
    EXCEPTION_CODE_INFO(EXCEPTION_ARRAY_BOUNDS_EXCEEDED),
    EXCEPTION_CODE_INFO(EXCEPTION_DATATYPE_MISALIGNMENT),
    EXCEPTION_CODE_INFO(EXCEPTION_FLT_DENORMAL_OPERAND),
    EXCEPTION_CODE_INFO(EXCEPTION_FLT_DIVIDE_BY_ZERO),
    EXCEPTION_CODE_INFO(EXCEPTION_FLT_INEXACT_RESULT),
    EXCEPTION_CODE_INFO(EXCEPTION_FLT_INVALID_OPERATION),
    EXCEPTION_CODE_INFO(EXCEPTION_FLT_OVERFLOW),
    EXCEPTION_CODE_INFO(EXCEPTION_FLT_STACK_CHECK),
    EXCEPTION_CODE_INFO(EXCEPTION_FLT_UNDERFLOW),
    EXCEPTION_CODE_INFO(EXCEPTION_GUARD_PAGE),
    EXCEPTION_CODE_INFO(EXCEPTION_ILLEGAL_INSTRUCTION),
    EXCEPTION_CODE_INFO(EXCEPTION_INT_DIVIDE_BY_ZERO),
    EXCEPTION_CODE_INFO(EXCEPTION_INT_OVERFLOW),
    EXCEPTION_CODE_INFO(EXCEPTION_INVALID_DISPOSITION),
    EXCEPTION_CODE_INFO(EXCEPTION_INVALID_HANDLE),
    EXCEPTION_CODE_INFO(EXCEPTION_IN_PAGE_ERROR),
    EXCEPTION_CODE_INFO(EXCEPTION_NONCONTINUABLE_EXCEPTION),
    EXCEPTION_CODE_INFO(EXCEPTION_PRIV_INSTRUCTION),
    EXCEPTION_CODE_INFO(EXCEPTION_STACK_OVERFLOW),
};
#endif /* !_WIN32 */

enum CMUnitTestStatus {
    CM_TEST_NOT_STARTED,
    CM_TEST_PASSED,
    CM_TEST_FAILED,
    CM_TEST_ERROR,
    CM_TEST_SKIPPED,
};

struct CMUnitTestState {
    const ListNode *check_point; /* Check point of the test if there's a setup function. */
    const struct CMUnitTest *test; /* Point to array element in the tests we get passed */
    void *state; /* State associated with the test */
    const char *error_message; /* The error messages by the test */
    enum CMUnitTestStatus status; /* PASSED, FAILED, ABORT ... */
    double runtime; /* Time calculations */
};

/* Exit the currently executing test. */
static void exit_test(const int quit_application)
{
    const char *abort_test = getenv("CMOCKA_TEST_ABORT");

    if (abort_test != NULL && abort_test[0] == '1') {
        print_error("%s", cm_error_message);
        abort();
    } else if (global_running_test) {
        cm_longjmp(global_run_test_env, 1);
    } else if (quit_application) {
        exit(-1);
    }
}

void _skip(const char * const file, const int line)
{
    cm_print_error(SOURCE_LOCATION_FORMAT ": Skipped!\n", file, line);
    global_skip_test = 1;
    exit_test(1);
}

/* Initialize a SourceLocation structure. */
static void initialize_source_location(SourceLocation * const location) {
    assert_non_null(location);
    location->file = NULL;
    location->line = 0;
}


/* Determine whether a source location is currently set. */
static int source_location_is_set(const SourceLocation * const location) {
    assert_non_null(location);
    return location->file && location->line;
}


/* Set a source location. */
static void set_source_location(
    SourceLocation * const location, const char * const file,
    const int line) {
    assert_non_null(location);
    location->file = file;
    location->line = line;
}


/* Create function results and expected parameter lists. */
void initialize_testing(const char *test_name) {
    (void)test_name;
    list_initialize(&global_function_result_map_head);
    initialize_source_location(&global_last_mock_value_location);
    list_initialize(&global_function_parameter_map_head);
    initialize_source_location(&global_last_parameter_location);
    list_initialize(&global_call_ordering_head);
    initialize_source_location(&global_last_parameter_location);
}


static void fail_if_leftover_values(const char *test_name) {
    int error_occurred = 0;
    (void)test_name;
    remove_always_return_values(&global_function_result_map_head, 1);
    if (check_for_leftover_values(
            &global_function_result_map_head,
            "%s() has remaining non-returned values.\n", 1)) {
        error_occurred = 1;
    }

    remove_always_return_values(&global_function_parameter_map_head, 2);
    if (check_for_leftover_values(
            &global_function_parameter_map_head,
            "%s parameter still has values that haven't been checked.\n", 2)) {
        error_occurred = 1;
    }

    remove_always_return_values_from_list(&global_call_ordering_head);
    if (check_for_leftover_values_list(&global_call_ordering_head,
        "%s function was expected to be called but was not not.\n")) {
        error_occurred = 1;
    }
    if (error_occurred) {
        exit_test(1);
    }
}


static void teardown_testing(const char *test_name) {
    (void)test_name;
    list_free(&global_function_result_map_head, free_symbol_map_value,
              (void*)0);
    initialize_source_location(&global_last_mock_value_location);
    list_free(&global_function_parameter_map_head, free_symbol_map_value,
              (void*)1);
    initialize_source_location(&global_last_parameter_location);
    list_free(&global_call_ordering_head, free_value,
              (void*)0);
    initialize_source_location(&global_last_call_ordering_location);
}

/* Initialize a list node. */
static ListNode* list_initialize(ListNode * const node) {
    node->value = NULL;
    node->next = node;
    node->prev = node;
    node->refcount = 1;
    return node;
}


/*
 * Adds a value at the tail of a given list.
 * The node referencing the value is allocated from the heap.
 */
static ListNode* list_add_value(ListNode * const head, const void *value,
                                     const int refcount) {
    ListNode * const new_node = (ListNode*)malloc(sizeof(ListNode));
    assert_non_null(head);
    assert_non_null(value);
    new_node->value = value;
    new_node->refcount = refcount;
    return list_add(head, new_node);
}


/* Add new_node to the end of the list. */
static ListNode* list_add(ListNode * const head, ListNode *new_node) {
    assert_non_null(head);
    assert_non_null(new_node);
    new_node->next = head;
    new_node->prev = head->prev;
    head->prev->next = new_node;
    head->prev = new_node;
    return new_node;
}


/* Remove a node from a list. */
static ListNode* list_remove(
        ListNode * const node, const CleanupListValue cleanup_value,
        void * const cleanup_value_data) {
    assert_non_null(node);
    node->prev->next = node->next;
    node->next->prev = node->prev;
    if (cleanup_value) {
        cleanup_value(node->value, cleanup_value_data);
    }
    return node;
}


/* Remove a list node from a list and free the node. */
static void list_remove_free(
        ListNode * const node, const CleanupListValue cleanup_value,
        void * const cleanup_value_data) {
    assert_non_null(node);
    free(list_remove(node, cleanup_value, cleanup_value_data));
}


/*
 * Frees memory kept by a linked list The cleanup_value function is called for
 * every "value" field of nodes in the list, except for the head.  In addition
 * to each list value, cleanup_value_data is passed to each call to
 * cleanup_value.  The head of the list is not deallocated.
 */
static ListNode* list_free(
        ListNode * const head, const CleanupListValue cleanup_value,
        void * const cleanup_value_data) {
    assert_non_null(head);
    while (!list_empty(head)) {
        list_remove_free(head->next, cleanup_value, cleanup_value_data);
    }
    return head;
}


/* Determine whether a list is empty. */
static int list_empty(const ListNode * const head) {
    assert_non_null(head);
    return head->next == head;
}


/*
 * Find a value in the list using the equal_func to compare each node with the
 * value.
 */
static int list_find(ListNode * const head, const void *value,
                     const EqualityFunction equal_func, ListNode **output) {
    ListNode *current;
    assert_non_null(head);
    for (current = head->next; current != head; current = current->next) {
        if (equal_func(current->value, value)) {
            *output = current;
            return 1;
        }
    }
    return 0;
}

/* Returns the first node of a list */
static int list_first(ListNode * const head, ListNode **output) {
    ListNode *target_node;
    assert_non_null(head);
    if (list_empty(head)) {
        return 0;
    }
    target_node = head->next;
    *output = target_node;
    return 1;
}


/* Deallocate a value referenced by a list. */
static void free_value(const void *value, void *cleanup_value_data) {
    (void)cleanup_value_data;
    assert_non_null(value);
    free((void*)value);
}


/* Releases memory associated to a symbol_map_value. */
static void free_symbol_map_value(const void *value,
                                  void *cleanup_value_data) {
    SymbolMapValue * const map_value = (SymbolMapValue*)value;
    const LargestIntegralType children = cast_ptr_to_largest_integral_type(cleanup_value_data);
    assert_non_null(value);
    list_free(&map_value->symbol_values_list_head,
              children ? free_symbol_map_value : free_value,
              (void *) ((uintptr_t)children - 1));
    free(map_value);
}


/*
 * Determine whether a symbol name referenced by a symbol_map_value matches the
 * specified function name.
 */
static int symbol_names_match(const void *map_value, const void *symbol) {
    return !strcmp(((SymbolMapValue*)map_value)->symbol_name,
                   (const char*)symbol);
}

/*
 * Adds a value to the queue of values associated with the given hierarchy of
 * symbols.  It's assumed value is allocated from the heap.
 */
static void add_symbol_value(ListNode * const symbol_map_head,
                             const char * const symbol_names[],
                             const size_t number_of_symbol_names,
                             const void* value, const int refcount) {
    const char* symbol_name;
    ListNode *target_node;
    SymbolMapValue *target_map_value;
    assert_non_null(symbol_map_head);
    assert_non_null(symbol_names);
    assert_true(number_of_symbol_names);
    symbol_name = symbol_names[0];

    if (!list_find(symbol_map_head, symbol_name, symbol_names_match,
                   &target_node)) {
        SymbolMapValue * const new_symbol_map_value =
            (SymbolMapValue*)malloc(sizeof(*new_symbol_map_value));
        new_symbol_map_value->symbol_name = symbol_name;
        list_initialize(&new_symbol_map_value->symbol_values_list_head);
        target_node = list_add_value(symbol_map_head, new_symbol_map_value,
                                          1);
    }

    target_map_value = (SymbolMapValue*)target_node->value;
    if (number_of_symbol_names == 1) {
            list_add_value(&target_map_value->symbol_values_list_head,
                                value, refcount);
    } else {
        add_symbol_value(&target_map_value->symbol_values_list_head,
                         &symbol_names[1], number_of_symbol_names - 1, value,
                         refcount);
    }
}


/*
 * Gets the next value associated with the given hierarchy of symbols.
 * The value is returned as an output parameter with the function returning the
 * node's old refcount value if a value is found, 0 otherwise.  This means that
 * a return value of 1 indicates the node was just removed from the list.
 */
static int get_symbol_value(
        ListNode * const head, const char * const symbol_names[],
        const size_t number_of_symbol_names, void **output) {
    const char* symbol_name;
    ListNode *target_node;
    assert_non_null(head);
    assert_non_null(symbol_names);
    assert_true(number_of_symbol_names);
    assert_non_null(output);
    symbol_name = symbol_names[0];

    if (list_find(head, symbol_name, symbol_names_match, &target_node)) {
        SymbolMapValue *map_value;
        ListNode *child_list;
        int return_value = 0;
        assert_non_null(target_node);
        assert_non_null(target_node->value);

        map_value = (SymbolMapValue*)target_node->value;
        child_list = &map_value->symbol_values_list_head;

        if (number_of_symbol_names == 1) {
            ListNode *value_node = NULL;
            return_value = list_first(child_list, &value_node);
            assert_true(return_value);
            *output = (void*) value_node->value;
            return_value = value_node->refcount;
            if (value_node->refcount - 1 == 0) {
                list_remove_free(value_node, NULL, NULL);
            } else if (value_node->refcount > -2) {
                --value_node->refcount;
            }
        } else {
            return_value = get_symbol_value(
                child_list, &symbol_names[1], number_of_symbol_names - 1,
                output);
        }
        if (list_empty(child_list)) {
            list_remove_free(target_node, free_symbol_map_value, (void*)0);
        }
        return return_value;
    } else {
        cm_print_error("No entries for symbol %s.\n", symbol_name);
    }
    return 0;
}

/**
 * Taverse a list of nodes and remove first symbol value in list that has a
 * refcount < -1 (i.e. should always be returned and has been returned at
 * least once).
 */

static void remove_always_return_values_from_list(ListNode * const map_head)
{
    ListNode * current = NULL;
    ListNode * next = NULL;
    assert_non_null(map_head);

    for (current = map_head->next, next = current->next;
            current != map_head;
            current = next, next = current->next) {
        if (current->refcount < -1) {
            list_remove_free(current, free_value, NULL);
        }
    }
}

/*
 * Traverse down a tree of symbol values and remove the first symbol value
 * in each branch that has a refcount < -1 (i.e should always be returned
 * and has been returned at least once).
 */
static void remove_always_return_values(ListNode * const map_head,
                                        const size_t number_of_symbol_names) {
    ListNode *current;
    assert_non_null(map_head);
    assert_true(number_of_symbol_names);
    current = map_head->next;
    while (current != map_head) {
        SymbolMapValue * const value = (SymbolMapValue*)current->value;
        ListNode * const next = current->next;
        ListNode *child_list;
        assert_non_null(value);
        child_list = &value->symbol_values_list_head;

        if (!list_empty(child_list)) {
            if (number_of_symbol_names == 1) {
                ListNode * const child_node = child_list->next;
                /* If this item has been returned more than once, free it. */
                if (child_node->refcount < -1) {
                    list_remove_free(child_node, free_value, NULL);
                }
            } else {
                remove_always_return_values(child_list,
                                            number_of_symbol_names - 1);
            }
        }

        if (list_empty(child_list)) {
            list_remove_free(current, free_value, NULL);
        }
        current = next;
    }
}

static int check_for_leftover_values_list(const ListNode * head,
                                          const char * const error_message)
{
    ListNode *child_node;
    int leftover_count = 0;
    if (!list_empty(head))
    {
        for (child_node = head->next; child_node != head;
                 child_node = child_node->next, ++leftover_count) {
            const FuncOrderingValue *const o =
                    (const FuncOrderingValue*) child_node->value;
            cm_print_error(error_message, o->function);
            cm_print_error(SOURCE_LOCATION_FORMAT
                    ": note: remaining item was declared here\n",
                    o->location.file, o->location.line);
        }
    }
    return leftover_count;
}

/*
 * Checks if there are any leftover values set up by the test that were never
 * retrieved through execution, and fail the test if that is the case.
 */
static int check_for_leftover_values(
        const ListNode * const map_head, const char * const error_message,
        const size_t number_of_symbol_names) {
    const ListNode *current;
    int symbols_with_leftover_values = 0;
    assert_non_null(map_head);
    assert_true(number_of_symbol_names);

    for (current = map_head->next; current != map_head;
         current = current->next) {
        const SymbolMapValue * const value =
            (SymbolMapValue*)current->value;
        const ListNode *child_list;
        assert_non_null(value);
        child_list = &value->symbol_values_list_head;

        if (!list_empty(child_list)) {
            if (number_of_symbol_names == 1) {
                const ListNode *child_node;
                cm_print_error(error_message, value->symbol_name);

                for (child_node = child_list->next; child_node != child_list;
                     child_node = child_node->next) {
                    const SourceLocation * const location =
                        (const SourceLocation*)child_node->value;
                    cm_print_error(SOURCE_LOCATION_FORMAT
                                   ": note: remaining item was declared here\n",
                                   location->file, location->line);
                }
            } else {
                cm_print_error("%s.", value->symbol_name);
                check_for_leftover_values(child_list, error_message,
                                          number_of_symbol_names - 1);
            }
            symbols_with_leftover_values ++;
        }
    }
    return symbols_with_leftover_values;
}


/* Get the next return value for the specified mock function. */
LargestIntegralType _mock(const char * const function, const char* const file,
                          const int line) {
    void *result;
    const int rc = get_symbol_value(&global_function_result_map_head,
                                    &function, 1, &result);
    if (rc) {
        SymbolValue * const symbol = (SymbolValue*)result;
        const LargestIntegralType value = symbol->value;
        global_last_mock_value_location = symbol->location;
        if (rc == 1) {
            free(symbol);
        }
        return value;
    } else {
        cm_print_error(SOURCE_LOCATION_FORMAT ": error: Could not get value "
                       "to mock function %s\n", file, line, function);
        if (source_location_is_set(&global_last_mock_value_location)) {
            cm_print_error(SOURCE_LOCATION_FORMAT
                           ": note: Previously returned mock value was declared here\n",
                           global_last_mock_value_location.file,
                           global_last_mock_value_location.line);
        } else {
            cm_print_error("There were no previously returned mock values for "
                           "this test.\n");
        }
        exit_test(1);
    }
    return 0;
}

/* Ensure that function is being called in proper order */
void _function_called(const char *const function,
                      const char *const file,
                      const int line)
{
    ListNode *first_value_node = NULL;
    ListNode *value_node = NULL;
    FuncOrderingValue *expected_call;
    int rc;

    rc = list_first(&global_call_ordering_head, &value_node);
    first_value_node = value_node;
    if (rc) {
        int cmp;

        expected_call = (FuncOrderingValue *)value_node->value;
        cmp = strcmp(expected_call->function, function);
        if (value_node->refcount < -1) {
            /*
             * Search through value nodes until either function is found or
             * encounter a non-zero refcount greater than -2
             */
            if (cmp != 0) {
                value_node = value_node->next;
                expected_call = (FuncOrderingValue *)value_node->value;

                cmp = strcmp(expected_call->function, function);
                while (value_node->refcount < -1 &&
                       cmp != 0 &&
                       value_node != first_value_node->prev) {
                    value_node = value_node->next;
                    if (value_node == NULL) {
                        break;
                    }
                    expected_call = (FuncOrderingValue *)value_node->value;
                    if (expected_call == NULL) {
                        continue;
                    }
                    cmp = strcmp(expected_call->function, function);
                }

                if (value_node == first_value_node->prev) {
                    cm_print_error(SOURCE_LOCATION_FORMAT
                                   ": error: No expected mock calls matching "
                                   "called() invocation in %s",
                                   file, line,
                                   function);
                    exit_test(1);
                }
            }
        }

        if (cmp == 0) {
            if (value_node->refcount > -2 && --value_node->refcount == 0) {
                list_remove_free(value_node, free_value, NULL);
            }
        } else {
            cm_print_error(SOURCE_LOCATION_FORMAT
                           ": error: Expected call to %s but received called() "
                           "in %s\n",
                           file, line,
                           expected_call->function,
                           function);
            exit_test(1);
        }
    } else {
        cm_print_error(SOURCE_LOCATION_FORMAT
                       ": error: No mock calls expected but called() was "
                       "invoked in %s\n",
                       file, line,
                       function);
        exit_test(1);
    }
}

/* Add a return value for the specified mock function name. */
void _will_return(const char * const function_name, const char * const file,
                  const int line, const LargestIntegralType value,
                  const int count) {
    SymbolValue * const return_value =
        (SymbolValue*)malloc(sizeof(*return_value));
    assert_true(count != 0);
    return_value->value = value;
    set_source_location(&return_value->location, file, line);
    add_symbol_value(&global_function_result_map_head, &function_name, 1,
                     return_value, count);
}


/*
 * Add a custom parameter checking function.  If the event parameter is NULL
 * the event structure is allocated internally by this function.  If event
 * parameter is provided it must be allocated on the heap and doesn't need to
 * be deallocated by the caller.
 */
void _expect_check(
        const char* const function, const char* const parameter,
        const char* const file, const int line,
        const CheckParameterValue check_function,
        const LargestIntegralType check_data,
        CheckParameterEvent * const event, const int count) {
    CheckParameterEvent * const check =
        event ? event : (CheckParameterEvent*)malloc(sizeof(*check));
    const char* symbols[] = {function, parameter};
    check->parameter_name = parameter;
    check->check_value = check_function;
    check->check_value_data = check_data;
    set_source_location(&check->location, file, line);
    add_symbol_value(&global_function_parameter_map_head, symbols, 2, check,
                     count);
}

/*
 * Add an call expectations that a particular function is called correctly.
 * This is used for code under test that makes calls to several functions
 * in depended upon components (mocks).
 */

void _expect_function_call(
    const char * const function_name,
    const char * const file,
    const int line,
    const int count)
{
    FuncOrderingValue *ordering;

    assert_non_null(function_name);
    assert_non_null(file);
    assert_true(count != 0);

    ordering = (FuncOrderingValue *)malloc(sizeof(*ordering));

    set_source_location(&ordering->location, file, line);
    ordering->function = function_name;

    list_add_value(&global_call_ordering_head, ordering, count);
}

/* Returns 1 if the specified values are equal.  If the values are not equal
 * an error is displayed and 0 is returned. */
static int values_equal_display_error(const LargestIntegralType left,
                                      const LargestIntegralType right) {
    const int equal = left == right;
    if (!equal) {
        cm_print_error(LargestIntegralTypePrintfFormat " != "
                       LargestIntegralTypePrintfFormat "\n", left, right);
    }
    return equal;
}

/*
 * Returns 1 if the specified values are not equal.  If the values are equal
 * an error is displayed and 0 is returned. */
static int values_not_equal_display_error(const LargestIntegralType left,
                                          const LargestIntegralType right) {
    const int not_equal = left != right;
    if (!not_equal) {
        cm_print_error(LargestIntegralTypePrintfFormat " == "
                       LargestIntegralTypePrintfFormat "\n", left, right);
    }
    return not_equal;
}


/*
 * Determine whether value is contained within check_integer_set.
 * If invert is 0 and the value is in the set 1 is returned, otherwise 0 is
 * returned and an error is displayed.  If invert is 1 and the value is not
 * in the set 1 is returned, otherwise 0 is returned and an error is
 * displayed.
 */
static int value_in_set_display_error(
        const LargestIntegralType value,
        const CheckIntegerSet * const check_integer_set, const int invert) {
    int succeeded = invert;
    assert_non_null(check_integer_set);
    {
        const LargestIntegralType * const set = check_integer_set->set;
        const size_t size_of_set = check_integer_set->size_of_set;
        size_t i;
        for (i = 0; i < size_of_set; i++) {
            if (set[i] == value) {
                /* If invert = 0 and item is found, succeeded = 1. */
                /* If invert = 1 and item is found, succeeded = 0. */
                succeeded = !succeeded;
                break;
            }
        }
        if (succeeded) {
            return 1;
        }
        cm_print_error("%" PRIu64 " is %sin the set (", value,
                       invert ? "" : "not ");
        for (i = 0; i < size_of_set; i++) {
            cm_print_error("%" PRIu64 ", ", set[i]);
        }
        cm_print_error(")\n");
    }
    return 0;
}


/*
 * Determine whether a value is within the specified range.  If the value is
 * within the specified range 1 is returned.  If the value isn't within the
 * specified range an error is displayed and 0 is returned.
 */
static int integer_in_range_display_error(
        const LargestIntegralType value, const LargestIntegralType range_min,
        const LargestIntegralType range_max) {
    if (value >= range_min && value <= range_max) {
        return 1;
    }
    cm_print_error("%" PRIu64 " is not within the range %" PRIu64 "-%" PRIu64 "\n",
                   value, range_min, range_max);
    return 0;
}


/*
 * Determine whether a value is within the specified range.  If the value
 * is not within the range 1 is returned.  If the value is within the
 * specified range an error is displayed and zero is returned.
 */
static int integer_not_in_range_display_error(
        const LargestIntegralType value, const LargestIntegralType range_min,
        const LargestIntegralType range_max) {
    if (value < range_min || value > range_max) {
        return 1;
    }
    cm_print_error("%" PRIu64 " is within the range %" PRIu64 "-%" PRIu64 "\n",
                   value, range_min, range_max);
    return 0;
}


/*
 * Determine whether the specified strings are equal.  If the strings are equal
 * 1 is returned.  If they're not equal an error is displayed and 0 is
 * returned.
 */
static int string_equal_display_error(
        const char * const left, const char * const right) {
    if (strcmp(left, right) == 0) {
        return 1;
    }
    cm_print_error("\"%s\" != \"%s\"\n", left, right);
    return 0;
}


/*
 * Determine whether the specified strings are equal.  If the strings are not
 * equal 1 is returned.  If they're not equal an error is displayed and 0 is
 * returned
 */
static int string_not_equal_display_error(
        const char * const left, const char * const right) {
    if (strcmp(left, right) != 0) {
        return 1;
    }
    cm_print_error("\"%s\" == \"%s\"\n", left, right);
    return 0;
}


/*
 * Determine whether the specified areas of memory are equal.  If they're equal
 * 1 is returned otherwise an error is displayed and 0 is returned.
 */
static int memory_equal_display_error(const char* const a, const char* const b,
                                      const size_t size) {
    int differences = 0;
    size_t i;
    for (i = 0; i < size; i++) {
        const char l = a[i];
        const char r = b[i];
        if (l != r) {
            cm_print_error("difference at offset %" PRIdS " 0x%02x 0x%02x\n",
                           i, l, r);
            differences ++;
        }
    }
    if (differences) {
        cm_print_error("%d bytes of %p and %p differ\n",
                       differences, (void *)a, (void *)b);
        return 0;
    }
    return 1;
}


/*
 * Determine whether the specified areas of memory are not equal.  If they're
 * not equal 1 is returned otherwise an error is displayed and 0 is
 * returned.
 */
static int memory_not_equal_display_error(
        const char* const a, const char* const b, const size_t size) {
    size_t same = 0;
    size_t i;
    for (i = 0; i < size; i++) {
        const char l = a[i];
        const char r = b[i];
        if (l == r) {
            same ++;
        }
    }
    if (same == size) {
        cm_print_error("%"PRIdS "bytes of %p and %p the same\n",
                       same, (void *)a, (void *)b);
        return 0;
    }
    return 1;
}


/* CheckParameterValue callback to check whether a value is within a set. */
static int check_in_set(const LargestIntegralType value,
                        const LargestIntegralType check_value_data) {
    return value_in_set_display_error(value,
        cast_largest_integral_type_to_pointer(CheckIntegerSet*,
                                              check_value_data), 0);
}


/* CheckParameterValue callback to check whether a value isn't within a set. */
static int check_not_in_set(const LargestIntegralType value,
                            const LargestIntegralType check_value_data) {
    return value_in_set_display_error(value,
        cast_largest_integral_type_to_pointer(CheckIntegerSet*,
                                              check_value_data), 1);
}


/* Create the callback data for check_in_set() or check_not_in_set() and
 * register a check event. */
static void expect_set(
        const char* const function, const char* const parameter,
        const char* const file, const int line,
        const LargestIntegralType values[], const size_t number_of_values,
        const CheckParameterValue check_function, const int count) {
    CheckIntegerSet * const check_integer_set =
        (CheckIntegerSet*)malloc(sizeof(*check_integer_set) +
               (sizeof(values[0]) * number_of_values));
    LargestIntegralType * const set = (LargestIntegralType*)(
        check_integer_set + 1);
    declare_initialize_value_pointer_pointer(check_data, check_integer_set);
    assert_non_null(values);
    assert_true(number_of_values);
    memcpy(set, values, number_of_values * sizeof(values[0]));
    check_integer_set->set = set;
    check_integer_set->size_of_set = number_of_values;
    _expect_check(
        function, parameter, file, line, check_function,
        check_data.value, &check_integer_set->event, count);
}


/* Add an event to check whether a value is in a set. */
void _expect_in_set(
        const char* const function, const char* const parameter,
        const char* const file, const int line,
        const LargestIntegralType values[], const size_t number_of_values,
        const int count) {
    expect_set(function, parameter, file, line, values, number_of_values,
               check_in_set, count);
}


/* Add an event to check whether a value isn't in a set. */
void _expect_not_in_set(
        const char* const function, const char* const parameter,
        const char* const file, const int line,
        const LargestIntegralType values[], const size_t number_of_values,
        const int count) {
    expect_set(function, parameter, file, line, values, number_of_values,
               check_not_in_set, count);
}


/* CheckParameterValue callback to check whether a value is within a range. */
static int check_in_range(const LargestIntegralType value,
                          const LargestIntegralType check_value_data) {
    CheckIntegerRange * const check_integer_range =
        cast_largest_integral_type_to_pointer(CheckIntegerRange*,
                                              check_value_data);
    assert_non_null(check_integer_range);
    return integer_in_range_display_error(value, check_integer_range->minimum,
                                          check_integer_range->maximum);
}


/* CheckParameterValue callback to check whether a value is not within a range. */
static int check_not_in_range(const LargestIntegralType value,
                              const LargestIntegralType check_value_data) {
    CheckIntegerRange * const check_integer_range =
        cast_largest_integral_type_to_pointer(CheckIntegerRange*,
                                              check_value_data);
    assert_non_null(check_integer_range);
    return integer_not_in_range_display_error(
        value, check_integer_range->minimum, check_integer_range->maximum);
}


/* Create the callback data for check_in_range() or check_not_in_range() and
 * register a check event. */
static void expect_range(
        const char* const function, const char* const parameter,
        const char* const file, const int line,
        const LargestIntegralType minimum, const LargestIntegralType maximum,
        const CheckParameterValue check_function, const int count) {
    CheckIntegerRange * const check_integer_range =
        (CheckIntegerRange*)malloc(sizeof(*check_integer_range));
    declare_initialize_value_pointer_pointer(check_data, check_integer_range);
    check_integer_range->minimum = minimum;
    check_integer_range->maximum = maximum;
    _expect_check(function, parameter, file, line, check_function,
                  check_data.value, &check_integer_range->event, count);
}


/* Add an event to determine whether a parameter is within a range. */
void _expect_in_range(
        const char* const function, const char* const parameter,
        const char* const file, const int line,
        const LargestIntegralType minimum, const LargestIntegralType maximum,
        const int count) {
    expect_range(function, parameter, file, line, minimum, maximum,
                 check_in_range, count);
}


/* Add an event to determine whether a parameter is not within a range. */
void _expect_not_in_range(
        const char* const function, const char* const parameter,
        const char* const file, const int line,
        const LargestIntegralType minimum, const LargestIntegralType maximum,
        const int count) {
    expect_range(function, parameter, file, line, minimum, maximum,
                 check_not_in_range, count);
}


/* CheckParameterValue callback to check whether a value is equal to an
 * expected value. */
static int check_value(const LargestIntegralType value,
                       const LargestIntegralType check_value_data) {
    return values_equal_display_error(value, check_value_data);
}


/* Add an event to check a parameter equals an expected value. */
void _expect_value(
        const char* const function, const char* const parameter,
        const char* const file, const int line,
        const LargestIntegralType value, const int count) {
    _expect_check(function, parameter, file, line, check_value, value, NULL,
                  count);
}


/* CheckParameterValue callback to check whether a value is not equal to an
 * expected value. */
static int check_not_value(const LargestIntegralType value,
                           const LargestIntegralType check_value_data) {
    return values_not_equal_display_error(value, check_value_data);
}


/* Add an event to check a parameter is not equal to an expected value. */
void _expect_not_value(
        const char* const function, const char* const parameter,
        const char* const file, const int line,
        const LargestIntegralType value, const int count) {
    _expect_check(function, parameter, file, line, check_not_value, value,
                  NULL, count);
}


/* CheckParameterValue callback to check whether a parameter equals a string. */
static int check_string(const LargestIntegralType value,
                        const LargestIntegralType check_value_data) {
    return string_equal_display_error(
        cast_largest_integral_type_to_pointer(char*, value),
        cast_largest_integral_type_to_pointer(char*, check_value_data));
}


/* Add an event to check whether a parameter is equal to a string. */
void _expect_string(
        const char* const function, const char* const parameter,
        const char* const file, const int line, const char* string,
        const int count) {
    declare_initialize_value_pointer_pointer(string_pointer,
                                             discard_const(string));
    _expect_check(function, parameter, file, line, check_string,
                  string_pointer.value, NULL, count);
}


/* CheckParameterValue callback to check whether a parameter is not equals to
 * a string. */
static int check_not_string(const LargestIntegralType value,
                            const LargestIntegralType check_value_data) {
    return string_not_equal_display_error(
        cast_largest_integral_type_to_pointer(char*, value),
        cast_largest_integral_type_to_pointer(char*, check_value_data));
}


/* Add an event to check whether a parameter is not equal to a string. */
void _expect_not_string(
        const char* const function, const char* const parameter,
        const char* const file, const int line, const char* string,
        const int count) {
    declare_initialize_value_pointer_pointer(string_pointer,
                                             discard_const(string));
    _expect_check(function, parameter, file, line, check_not_string,
                  string_pointer.value, NULL, count);
}

/* CheckParameterValue callback to check whether a parameter equals an area of
 * memory. */
static int check_memory(const LargestIntegralType value,
                        const LargestIntegralType check_value_data) {
    CheckMemoryData * const check = cast_largest_integral_type_to_pointer(
        CheckMemoryData*, check_value_data);
    assert_non_null(check);
    return memory_equal_display_error(
        cast_largest_integral_type_to_pointer(const char*, value),
        (const char*)check->memory, check->size);
}


/* Create the callback data for check_memory() or check_not_memory() and
 * register a check event. */
static void expect_memory_setup(
        const char* const function, const char* const parameter,
        const char* const file, const int line,
        const void * const memory, const size_t size,
        const CheckParameterValue check_function, const int count) {
    CheckMemoryData * const check_data =
        (CheckMemoryData*)malloc(sizeof(*check_data) + size);
    void * const mem = (void*)(check_data + 1);
    declare_initialize_value_pointer_pointer(check_data_pointer, check_data);
    assert_non_null(memory);
    assert_true(size);
    memcpy(mem, memory, size);
    check_data->memory = mem;
    check_data->size = size;
    _expect_check(function, parameter, file, line, check_function,
                  check_data_pointer.value, &check_data->event, count);
}


/* Add an event to check whether a parameter matches an area of memory. */
void _expect_memory(
        const char* const function, const char* const parameter,
        const char* const file, const int line, const void* const memory,
        const size_t size, const int count) {
    expect_memory_setup(function, parameter, file, line, memory, size,
                        check_memory, count);
}


/* CheckParameterValue callback to check whether a parameter is not equal to
 * an area of memory. */
static int check_not_memory(const LargestIntegralType value,
                            const LargestIntegralType check_value_data) {
    CheckMemoryData * const check = cast_largest_integral_type_to_pointer(
        CheckMemoryData*, check_value_data);
    assert_non_null(check);
    return memory_not_equal_display_error(
        cast_largest_integral_type_to_pointer(const char*, value),
        (const char*)check->memory,
        check->size);
}


/* Add an event to check whether a parameter doesn't match an area of memory. */
void _expect_not_memory(
        const char* const function, const char* const parameter,
        const char* const file, const int line, const void* const memory,
        const size_t size, const int count) {
    expect_memory_setup(function, parameter, file, line, memory, size,
                        check_not_memory, count);
}


/* CheckParameterValue callback that always returns 1. */
static int check_any(const LargestIntegralType value,
                     const LargestIntegralType check_value_data) {
    (void)value;
    (void)check_value_data;
    return 1;
}


/* Add an event to allow any value for a parameter. */
void _expect_any(
        const char* const function, const char* const parameter,
        const char* const file, const int line, const int count) {
    _expect_check(function, parameter, file, line, check_any, 0, NULL,
                  count);
}


void _check_expected(
        const char * const function_name, const char * const parameter_name,
        const char* file, const int line, const LargestIntegralType value) {
    void *result;
    const char* symbols[] = {function_name, parameter_name};
    const int rc = get_symbol_value(&global_function_parameter_map_head,
                                    symbols, 2, &result);
    if (rc) {
        CheckParameterEvent * const check = (CheckParameterEvent*)result;
        int check_succeeded;
        global_last_parameter_location = check->location;
        check_succeeded = check->check_value(value, check->check_value_data);
        if (rc == 1) {
            free(check);
        }
        if (!check_succeeded) {
            cm_print_error(SOURCE_LOCATION_FORMAT
                           ": error: Check of parameter %s, function %s failed\n"
                           SOURCE_LOCATION_FORMAT
                           ": note: Expected parameter declared here\n",
                           file, line,
                           parameter_name, function_name,
                           global_last_parameter_location.file,
                           global_last_parameter_location.line);
            _fail(file, line);
        }
    } else {
        cm_print_error(SOURCE_LOCATION_FORMAT ": error: Could not get value "
                    "to check parameter %s of function %s\n", file, line,
                    parameter_name, function_name);
        if (source_location_is_set(&global_last_parameter_location)) {
            cm_print_error(SOURCE_LOCATION_FORMAT
                        ": note: Previously declared parameter value was declared here\n",
                        global_last_parameter_location.file,
                        global_last_parameter_location.line);
        } else {
            cm_print_error("There were no previously declared parameter values "
                        "for this test.\n");
        }
        exit_test(1);
    }
}


/* Replacement for assert. */
void mock_assert(const int result, const char* const expression,
                 const char* const file, const int line) {
    if (!result) {
        if (global_expecting_assert) {
            global_last_failed_assert = expression;
            longjmp(global_expect_assert_env, result);
        } else {
            cm_print_error("ASSERT: %s\n", expression);
            _fail(file, line);
        }
    }
}


void _assert_true(const LargestIntegralType result,
                  const char * const expression,
                  const char * const file, const int line) {
    if (!result) {
        cm_print_error("%s\n", expression);
        _fail(file, line);
    }
}

void _assert_return_code(const LargestIntegralType result,
                         size_t rlen,
                         const LargestIntegralType error,
                         const char * const expression,
                         const char * const file,
                         const int line)
{
    LargestIntegralType valmax;


    switch (rlen) {
    case 1:
        valmax = 255;
        break;
    case 2:
        valmax = 32767;
        break;
    case 4:
        valmax = 2147483647;
        break;
    case 8:
    default:
        if (rlen > sizeof(valmax)) {
            valmax = 2147483647;
        } else {
            valmax = 9223372036854775807L;
        }
        break;
    }

    if (result > valmax - 1) {
        if (error > 0) {
            cm_print_error("%s < 0, errno(%" PRIu64 "): %s\n",
                           expression, error, strerror((int)error));
        } else {
            cm_print_error("%s < 0\n", expression);
        }
        _fail(file, line);
    }
}

void _assert_int_equal(
        const LargestIntegralType a, const LargestIntegralType b,
        const char * const file, const int line) {
    if (!values_equal_display_error(a, b)) {
        _fail(file, line);
    }
}


void _assert_int_not_equal(
        const LargestIntegralType a, const LargestIntegralType b,
        const char * const file, const int line) {
    if (!values_not_equal_display_error(a, b)) {
        _fail(file, line);
    }
}


void _assert_string_equal(const char * const a, const char * const b,
                          const char * const file, const int line) {
    if (!string_equal_display_error(a, b)) {
        _fail(file, line);
    }
}


void _assert_string_not_equal(const char * const a, const char * const b,
                              const char *file, const int line) {
    if (!string_not_equal_display_error(a, b)) {
        _fail(file, line);
    }
}


void _assert_memory_equal(const void * const a, const void * const b,
                          const size_t size, const char* const file,
                          const int line) {
    if (!memory_equal_display_error((const char*)a, (const char*)b, size)) {
        _fail(file, line);
    }
}


void _assert_memory_not_equal(const void * const a, const void * const b,
                              const size_t size, const char* const file,
                              const int line) {
    if (!memory_not_equal_display_error((const char*)a, (const char*)b,
                                        size)) {
        _fail(file, line);
    }
}


void _assert_in_range(
        const LargestIntegralType value, const LargestIntegralType minimum,
        const LargestIntegralType maximum, const char* const file,
        const int line) {
    if (!integer_in_range_display_error(value, minimum, maximum)) {
        _fail(file, line);
    }
}

void _assert_not_in_range(
        const LargestIntegralType value, const LargestIntegralType minimum,
        const LargestIntegralType maximum, const char* const file,
        const int line) {
    if (!integer_not_in_range_display_error(value, minimum, maximum)) {
        _fail(file, line);
    }
}

void _assert_in_set(const LargestIntegralType value,
                    const LargestIntegralType values[],
                    const size_t number_of_values, const char* const file,
                    const int line) {
    CheckIntegerSet check_integer_set;
    check_integer_set.set = values;
    check_integer_set.size_of_set = number_of_values;
    if (!value_in_set_display_error(value, &check_integer_set, 0)) {
        _fail(file, line);
    }
}

void _assert_not_in_set(const LargestIntegralType value,
                        const LargestIntegralType values[],
                        const size_t number_of_values, const char* const file,
                        const int line) {
    CheckIntegerSet check_integer_set;
    check_integer_set.set = values;
    check_integer_set.size_of_set = number_of_values;
    if (!value_in_set_display_error(value, &check_integer_set, 1)) {
        _fail(file, line);
    }
}


/* Get the list of allocated blocks. */
static ListNode* get_allocated_blocks_list() {
    /* If it initialized, initialize the list of allocated blocks. */
    if (!global_allocated_blocks.value) {
        list_initialize(&global_allocated_blocks);
        global_allocated_blocks.value = (void*)1;
    }
    return &global_allocated_blocks;
}

static void *libc_malloc(size_t size)
{
#undef malloc
    return malloc(size);
#define malloc test_malloc
}

static void libc_free(void *ptr)
{
#undef free
    free(ptr);
#define free test_free
}

static void *libc_realloc(void *ptr, size_t size)
{
#undef realloc
    return realloc(ptr, size);
#define realloc test_realloc
}

static void vcm_print_error(const char* const format,
                            va_list args) CMOCKA_PRINTF_ATTRIBUTE(1, 0);

/* It's important to use the libc malloc and free here otherwise
 * the automatic free of leaked blocks can reap the error messages
 */
static void vcm_print_error(const char* const format, va_list args)
{
    char buffer[1024];
    size_t msg_len = 0;
    va_list ap;
    int len;

    len = vsnprintf(buffer, sizeof(buffer), format, args);
    if (len < 0) {
        /* TODO */
        return;
    }

    if (cm_error_message == NULL) {
        /* CREATE MESSAGE */

        cm_error_message = libc_malloc(len + 1);
        if (cm_error_message == NULL) {
            /* TODO */
            return;
        }
    } else {
        /* APPEND MESSAGE */
        char *tmp;

        msg_len = strlen(cm_error_message);
        tmp = libc_realloc(cm_error_message, msg_len + len + 1);
        if (tmp == NULL) {
            return;
        }
        cm_error_message = tmp;
    }

    if (((size_t)len) < sizeof(buffer)) {
        /* Use len + 1 to also copy '\0' */
        memcpy(cm_error_message + msg_len, buffer, len + 1);
    } else {
        va_copy(ap, args);
        vsnprintf(cm_error_message + msg_len, len, format, ap);
        va_end(ap);
    }
}

static void vcm_free_error(char *err_msg)
{
    libc_free(err_msg);
}

/* Use the real malloc in this function. */
#undef malloc
void* _test_malloc(const size_t size, const char* file, const int line) {
    char* ptr;
    MallocBlockInfo *block_info;
    ListNode * const block_list = get_allocated_blocks_list();
    const size_t allocate_size = size + (MALLOC_GUARD_SIZE * 2) +
        sizeof(*block_info) + MALLOC_ALIGNMENT;
    char* const block = (char*)malloc(allocate_size);
    assert_non_null(block);

    /* Calculate the returned address. */
    ptr = (char*)(((size_t)block + MALLOC_GUARD_SIZE + sizeof(*block_info) +
                  MALLOC_ALIGNMENT) & ~(MALLOC_ALIGNMENT - 1));

    /* Initialize the guard blocks. */
    memset(ptr - MALLOC_GUARD_SIZE, MALLOC_GUARD_PATTERN, MALLOC_GUARD_SIZE);
    memset(ptr + size, MALLOC_GUARD_PATTERN, MALLOC_GUARD_SIZE);
    memset(ptr, MALLOC_ALLOC_PATTERN, size);

    block_info = (MallocBlockInfo*)(ptr - (MALLOC_GUARD_SIZE +
                                             sizeof(*block_info)));
    set_source_location(&block_info->location, file, line);
    block_info->allocated_size = allocate_size;
    block_info->size = size;
    block_info->block = block;
    block_info->node.value = block_info;
    list_add(block_list, &block_info->node);
    return ptr;
}
#define malloc test_malloc


void* _test_calloc(const size_t number_of_elements, const size_t size,
                   const char* file, const int line) {
    void* const ptr = _test_malloc(number_of_elements * size, file, line);
    if (ptr) {
        memset(ptr, 0, number_of_elements * size);
    }
    return ptr;
}


/* Use the real free in this function. */
#undef free
void _test_free(void* const ptr, const char* file, const int line) {
    unsigned int i;
    char *block = discard_const_p(char, ptr);
    MallocBlockInfo *block_info;

    if (ptr == NULL) {
        return;
    }

    _assert_true(cast_ptr_to_largest_integral_type(ptr), "ptr", file, line);
    block_info = (MallocBlockInfo*)(block - (MALLOC_GUARD_SIZE +
                                               sizeof(*block_info)));
    /* Check the guard blocks. */
    {
        char *guards[2] = {block - MALLOC_GUARD_SIZE,
                           block + block_info->size};
        for (i = 0; i < ARRAY_SIZE(guards); i++) {
            unsigned int j;
            char * const guard = guards[i];
            for (j = 0; j < MALLOC_GUARD_SIZE; j++) {
                const char diff = guard[j] - MALLOC_GUARD_PATTERN;
                if (diff) {
                    cm_print_error(SOURCE_LOCATION_FORMAT
                                   ": error: Guard block of %p size=%lu is corrupt\n"
                                   SOURCE_LOCATION_FORMAT ": note: allocated here at %p\n",
                                   file, line,
                                   ptr, (unsigned long)block_info->size,
                                   block_info->location.file, block_info->location.line,
                                   (void *)&guard[j]);
                    _fail(file, line);
                }
            }
        }
    }
    list_remove(&block_info->node, NULL, NULL);

    block = discard_const_p(char, block_info->block);
    memset(block, MALLOC_FREE_PATTERN, block_info->allocated_size);
    free(block);
}
#define free test_free

#undef realloc
void *_test_realloc(void *ptr,
                   const size_t size,
                   const char *file,
                   const int line)
{
    MallocBlockInfo *block_info;
    char *block = ptr;
    size_t block_size = size;
    void *new;

    if (ptr == NULL) {
        return _test_malloc(size, file, line);
    }

    if (size == 0) {
        _test_free(ptr, file, line);
        return NULL;
    }

    block_info = (MallocBlockInfo*)(block - (MALLOC_GUARD_SIZE +
                                             sizeof(*block_info)));

    new = _test_malloc(size, file, line);
    if (new == NULL) {
        return NULL;
    }

    if (block_info->size < size) {
        block_size = block_info->size;
    }

    memcpy(new, ptr, block_size);

    /* Free previous memory */
    _test_free(ptr, file, line);

    return new;
}
#define realloc test_realloc

/* Crudely checkpoint the current heap state. */
static const ListNode* check_point_allocated_blocks() {
    return get_allocated_blocks_list()->prev;
}


/* Display the blocks allocated after the specified check point.  This
 * function returns the number of blocks displayed. */
static int display_allocated_blocks(const ListNode * const check_point) {
    const ListNode * const head = get_allocated_blocks_list();
    const ListNode *node;
    int allocated_blocks = 0;
    assert_non_null(check_point);
    assert_non_null(check_point->next);

    for (node = check_point->next; node != head; node = node->next) {
        const MallocBlockInfo * const block_info =
            (const MallocBlockInfo*)node->value;
        assert_non_null(block_info);

        if (!allocated_blocks) {
            cm_print_error("Blocks allocated...\n");
        }
        cm_print_error(SOURCE_LOCATION_FORMAT ": note: block %p allocated here\n",
                       block_info->location.file,
                       block_info->location.line,
                       block_info->block);
        allocated_blocks ++;
    }
    return allocated_blocks;
}


/* Free all blocks allocated after the specified check point. */
static void free_allocated_blocks(const ListNode * const check_point) {
    const ListNode * const head = get_allocated_blocks_list();
    const ListNode *node;
    assert_non_null(check_point);

    node = check_point->next;
    assert_non_null(node);

    while (node != head) {
        MallocBlockInfo * const block_info = (MallocBlockInfo*)node->value;
        node = node->next;
        free(discard_const_p(char, block_info) + sizeof(*block_info) + MALLOC_GUARD_SIZE);
    }
}


/* Fail if any any blocks are allocated after the specified check point. */
static void fail_if_blocks_allocated(const ListNode * const check_point,
                                     const char * const test_name) {
    const int allocated_blocks = display_allocated_blocks(check_point);
    if (allocated_blocks) {
        free_allocated_blocks(check_point);
        cm_print_error("ERROR: %s leaked %d block(s)\n", test_name,
                       allocated_blocks);
        exit_test(1);
    }
}


void _fail(const char * const file, const int line) {
    enum cm_message_output output = cm_get_output();

    switch(output) {
        case CM_OUTPUT_STDOUT:
            cm_print_error("[   LINE   ] --- " SOURCE_LOCATION_FORMAT ": error: Failure!", file, line);
            break;
        default:
            cm_print_error(SOURCE_LOCATION_FORMAT ": error: Failure!", file, line);
            break;
    }
    exit_test(1);
}


#ifndef _WIN32
static void exception_handler(int sig) {
    const char *sig_strerror = "";

#ifdef HAVE_STRSIGNAL
    sig_strerror = strsignal(sig);
#endif

    cm_print_error("Test failed with exception: %s(%d)",
                   sig_strerror, sig);
    exit_test(1);
}

#else /* _WIN32 */

static LONG WINAPI exception_filter(EXCEPTION_POINTERS *exception_pointers) {
    EXCEPTION_RECORD * const exception_record =
        exception_pointers->ExceptionRecord;
    const DWORD code = exception_record->ExceptionCode;
    unsigned int i;
    for (i = 0; i < ARRAY_SIZE(exception_codes); i++) {
        const ExceptionCodeInfo * const code_info = &exception_codes[i];
        if (code == code_info->code) {
            static int shown_debug_message = 0;
            fflush(stdout);
            cm_print_error("%s occurred at %p.\n", code_info->description,
                        exception_record->ExceptionAddress);
            if (!shown_debug_message) {
                cm_print_error(
                    "\n"
                    "To debug in Visual Studio...\n"
                    "1. Select menu item File->Open Project\n"
                    "2. Change 'Files of type' to 'Executable Files'\n"
                    "3. Open this executable.\n"
                    "4. Select menu item Debug->Start\n"
                    "\n"
                    "Alternatively, set the environment variable \n"
                    "UNIT_TESTING_DEBUG to 1 and rebuild this executable, \n"
                    "then click 'Debug' in the popup dialog box.\n"
                    "\n");
                shown_debug_message = 1;
            }
            exit_test(0);
            return EXCEPTION_EXECUTE_HANDLER;
        }
    }
    return EXCEPTION_CONTINUE_SEARCH;
}
#endif /* !_WIN32 */

void cm_print_error(const char * const format, ...)
{
    va_list args;
    va_start(args, format);
    if (cm_error_message_enabled) {
        vcm_print_error(format, args);
    } else {
        vprint_error(format, args);
    }
    va_end(args);
}

/* Standard output and error print methods. */
void vprint_message(const char* const format, va_list args) {
    char buffer[1024];
    vsnprintf(buffer, sizeof(buffer), format, args);
    printf("%s", buffer);
    fflush(stdout);
#ifdef _WIN32
    OutputDebugString(buffer);
#endif /* _WIN32 */
}


void vprint_error(const char* const format, va_list args) {
    char buffer[1024];
    vsnprintf(buffer, sizeof(buffer), format, args);
    fprintf(stderr, "%s", buffer);
    fflush(stderr);
#ifdef _WIN32
    OutputDebugString(buffer);
#endif /* _WIN32 */
}


void print_message(const char* const format, ...) {
    va_list args;
    va_start(args, format);
    vprint_message(format, args);
    va_end(args);
}


void print_error(const char* const format, ...) {
    va_list args;
    va_start(args, format);
    vprint_error(format, args);
    va_end(args);
}

/* New formatter */
static enum cm_message_output cm_get_output(void)
{
    enum cm_message_output output = global_msg_output;
    char *env;

    env = getenv("CMOCKA_MESSAGE_OUTPUT");
    if (env != NULL) {
        if (strcasecmp(env, "STDOUT") == 0) {
            output = CM_OUTPUT_STDOUT;
        } else if (strcasecmp(env, "SUBUNIT") == 0) {
            output = CM_OUTPUT_SUBUNIT;
        } else if (strcasecmp(env, "TAP") == 0) {
            output = CM_OUTPUT_TAP;
        } else if (strcasecmp(env, "XML") == 0) {
            output = CM_OUTPUT_XML;
        }
    }

    return output;
}

enum cm_printf_type {
    PRINTF_TEST_START,
    PRINTF_TEST_SUCCESS,
    PRINTF_TEST_FAILURE,
    PRINTF_TEST_ERROR,
    PRINTF_TEST_SKIPPED,
};

static void cmprintf_group_finish_xml(const char *group_name,
                                      size_t total_executed,
                                      size_t total_failed,
                                      size_t total_errors,
                                      size_t total_skipped,
                                      double total_runtime,
                                      struct CMUnitTestState *cm_tests)
{
    FILE *fp = stdout;
    int file_opened = 0;
    char *env;
    size_t i;

    env = getenv("CMOCKA_XML_FILE");
    if (env != NULL) {
        char buf[1024];
        snprintf(buf, sizeof(buf), "%s", env);

        fp = fopen(buf, "r");
        if (fp == NULL) {
            fp = fopen(buf, "w");
            if (fp != NULL) {
                file_opened = 1;
            } else {
                fp = stderr;
            }
        } else {
            fclose(fp);
            fp = stderr;
        }
    }

    fprintf(fp, "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
    fprintf(fp, "<testsuites>\n");
    fprintf(fp, "  <testsuite name=\"%s\" time=\"%.3f\" "
                "tests=\"%u\" failures=\"%u\" errors=\"%u\" skipped=\"%u\" >\n",
                group_name,
                total_runtime * 1000, /* miliseconds */
                (unsigned)total_executed,
                (unsigned)total_failed,
                (unsigned)total_errors,
                (unsigned)total_skipped);

    for (i = 0; i < total_executed; i++) {
        struct CMUnitTestState *cmtest = &cm_tests[i];

        fprintf(fp, "    <testcase name=\"%s\" time=\"%.3f\" >\n",
                cmtest->test->name, cmtest->runtime * 1000);

        switch (cmtest->status) {
        case CM_TEST_ERROR:
        case CM_TEST_FAILED:
            if (cmtest->error_message != NULL) {
                fprintf(fp, "      <failure><![CDATA[%s]]></failure>\n",
                        cmtest->error_message);
            } else {
                fprintf(fp, "      <failure message=\"Unknown error\" />\n");
            }
            break;
        case CM_TEST_SKIPPED:
            fprintf(fp, "      <skipped/>\n");
            break;

        case CM_TEST_PASSED:
        case CM_TEST_NOT_STARTED:
            break;
        }

        fprintf(fp, "    </testcase>\n");
    }

    fprintf(fp, "  </testsuite>\n");
    fprintf(fp, "</testsuites>\n");

    if (file_opened) {
        fclose(fp);
    }
}

static void cmprintf_group_start_standard(const size_t num_tests)
{
    print_message("[==========] Running %u test(s).\n",
                  (unsigned)num_tests);
}

static void cmprintf_group_finish_standard(size_t total_executed,
                                           size_t total_passed,
                                           size_t total_failed,
                                           size_t total_errors,
                                           size_t total_skipped,
                                           struct CMUnitTestState *cm_tests)
{
    size_t i;

    print_message("[==========] %u test(s) run.\n", (unsigned)total_executed);
    print_error("[  PASSED  ] %u test(s).\n",
                (unsigned)(total_passed));

    if (total_skipped) {
        print_error("[  SKIPPED ] %"PRIdS " test(s), listed below:\n", total_skipped);
        for (i = 0; i < total_executed; i++) {
            struct CMUnitTestState *cmtest = &cm_tests[i];

            if (cmtest->status == CM_TEST_SKIPPED) {
                print_error("[  SKIPPED ] %s\n", cmtest->test->name);
            }
        }
        print_error("\n %u SKIPPED TEST(S)\n", (unsigned)(total_skipped));
    }

    if (total_failed) {
        print_error("[  FAILED  ] %"PRIdS " test(s), listed below:\n", total_failed);
        for (i = 0; i < total_executed; i++) {
            struct CMUnitTestState *cmtest = &cm_tests[i];

            if (cmtest->status == CM_TEST_FAILED) {
                print_error("[  FAILED  ] %s\n", cmtest->test->name);
            }
        }
        print_error("\n %u FAILED TEST(S)\n",
                    (unsigned)(total_failed + total_errors));
    }
}

static void cmprintf_standard(enum cm_printf_type type,
                              const char *test_name,
                              const char *error_message)
{
    switch (type) {
    case PRINTF_TEST_START:
        print_message("[ RUN      ] %s\n", test_name);
        break;
    case PRINTF_TEST_SUCCESS:
        print_message("[       OK ] %s\n", test_name);
        break;
    case PRINTF_TEST_FAILURE:
        if (error_message != NULL) {
            print_error("[  ERROR   ] --- %s\n", error_message);
        }
        print_message("[  FAILED  ] %s\n", test_name);
        break;
    case PRINTF_TEST_SKIPPED:
        print_message("[  SKIPPED ] %s\n", test_name);
        break;
    case PRINTF_TEST_ERROR:
        if (error_message != NULL) {
            print_error("%s\n", error_message);
        }
        print_error("[  ERROR   ] %s\n", test_name);
        break;
    }
}

static void cmprintf_group_start_tap(const size_t num_tests)
{
    print_message("\t1..%u\n", (unsigned)num_tests);
}

static void cmprintf_group_finish_tap(const char *group_name,
                                      size_t total_executed,
                                      size_t total_passed,
                                      size_t total_skipped)
{
    const char *status = "not ok";
    if (total_passed + total_skipped == total_executed) {
        status = "ok";
    }
    print_message("%s - %s\n", status, group_name);
}

static void cmprintf_tap(enum cm_printf_type type,
                         uint32_t test_number,
                         const char *test_name,
                         const char *error_message)
{
    switch (type) {
    case PRINTF_TEST_START:
        break;
    case PRINTF_TEST_SUCCESS:
        print_message("\tok %u - %s\n", (unsigned)test_number, test_name);
        break;
    case PRINTF_TEST_FAILURE:
        print_message("\tnot ok %u - %s\n", (unsigned)test_number, test_name);
        if (error_message != NULL) {
            char *msg;
            char *p;

            msg = strdup(error_message);
            if (msg == NULL) {
                return;
            }
            p = msg;

            while (p[0] != '\0') {
                char *q = p;

                p = strchr(q, '\n');
                if (p != NULL) {
                    p[0] = '\0';
                }

                print_message("\t# %s\n", q);

                if (p == NULL) {
                    break;
                }
                p++;
            }
            libc_free(msg);
        }
        break;
    case PRINTF_TEST_SKIPPED:
        print_message("\tnot ok %u # SKIP %s\n", (unsigned)test_number, test_name);
        break;
    case PRINTF_TEST_ERROR:
        print_message("\tnot ok %u - %s %s\n",
                      (unsigned)test_number, test_name, error_message);
        break;
    }
}

static void cmprintf_subunit(enum cm_printf_type type,
                             const char *test_name,
                             const char *error_message)
{
    switch (type) {
    case PRINTF_TEST_START:
        print_message("test: %s\n", test_name);
        break;
    case PRINTF_TEST_SUCCESS:
        print_message("success: %s\n", test_name);
        break;
    case PRINTF_TEST_FAILURE:
        print_message("failure: %s", test_name);
        if (error_message != NULL) {
            print_message(" [\n%s]\n", error_message);
        }
        break;
    case PRINTF_TEST_SKIPPED:
        print_message("skip: %s\n", test_name);
        break;
    case PRINTF_TEST_ERROR:
        print_message("error: %s [ %s ]\n", test_name, error_message);
        break;
    }
}

static void cmprintf_group_start(const size_t num_tests)
{
    enum cm_message_output output;

    output = cm_get_output();

    switch (output) {
    case CM_OUTPUT_STDOUT:
        cmprintf_group_start_standard(num_tests);
        break;
    case CM_OUTPUT_SUBUNIT:
        break;
    case CM_OUTPUT_TAP:
        cmprintf_group_start_tap(num_tests);
        break;
    case CM_OUTPUT_XML:
        break;
    }
}

static void cmprintf_group_finish(const char *group_name,
                                  size_t total_executed,
                                  size_t total_passed,
                                  size_t total_failed,
                                  size_t total_errors,
                                  size_t total_skipped,
                                  double total_runtime,
                                  struct CMUnitTestState *cm_tests)
{
    enum cm_message_output output;

    output = cm_get_output();

    switch (output) {
    case CM_OUTPUT_STDOUT:
        cmprintf_group_finish_standard(total_executed,
                                    total_passed,
                                    total_failed,
                                    total_errors,
                                    total_skipped,
                                    cm_tests);
        break;
    case CM_OUTPUT_SUBUNIT:
        break;
    case CM_OUTPUT_TAP:
        cmprintf_group_finish_tap(group_name, total_executed, total_passed, total_skipped);
        break;
    case CM_OUTPUT_XML:
        cmprintf_group_finish_xml(group_name,
                                  total_executed,
                                  total_failed,
                                  total_errors,
                                  total_skipped,
                                  total_runtime,
                                  cm_tests);
        break;
    }
}

static void cmprintf(enum cm_printf_type type,
                     size_t test_number,
                     const char *test_name,
                     const char *error_message)
{
    enum cm_message_output output;

    output = cm_get_output();

    switch (output) {
    case CM_OUTPUT_STDOUT:
        cmprintf_standard(type, test_name, error_message);
        break;
    case CM_OUTPUT_SUBUNIT:
        cmprintf_subunit(type, test_name, error_message);
        break;
    case CM_OUTPUT_TAP:
        cmprintf_tap(type, test_number, test_name, error_message);
        break;
    case CM_OUTPUT_XML:
        break;
    }
}

void cmocka_set_message_output(enum cm_message_output output)
{
    global_msg_output = output;
}

/****************************************************************************
 * TIME CALCULATIONS
 ****************************************************************************/

#ifdef HAVE_STRUCT_TIMESPEC
static struct timespec cm_tspecdiff(struct timespec time1,
                                    struct timespec time0)
{
    struct timespec ret;
    int xsec = 0;
    int sign = 1;

    if (time0.tv_nsec > time1.tv_nsec) {
        xsec = (int) ((time0.tv_nsec - time1.tv_nsec) / (1E9 + 1));
        time0.tv_nsec -= (long int) (1E9 * xsec);
        time0.tv_sec += xsec;
    }

    if ((time1.tv_nsec - time0.tv_nsec) > 1E9) {
        xsec = (int) ((time1.tv_nsec - time0.tv_nsec) / 1E9);
        time0.tv_nsec += (long int) (1E9 * xsec);
        time0.tv_sec -= xsec;
    }

    ret.tv_sec = time1.tv_sec - time0.tv_sec;
    ret.tv_nsec = time1.tv_nsec - time0.tv_nsec;

    if (time1.tv_sec < time0.tv_sec) {
        sign = -1;
    }

    ret.tv_sec = ret.tv_sec * sign;

    return ret;
}

static double cm_secdiff(struct timespec clock1, struct timespec clock0)
{
    double ret;
    struct timespec diff;

    diff = cm_tspecdiff(clock1, clock0);

    ret = diff.tv_sec;
    ret += (double) diff.tv_nsec / (double) 1E9;

    return ret;
}
#endif /* HAVE_STRUCT_TIMESPEC */

/****************************************************************************
 * CMOCKA TEST RUNNER
 ****************************************************************************/
static int cmocka_run_one_test_or_fixture(const char *function_name,
                                          CMUnitTestFunction test_func,
                                          CMFixtureFunction setup_func,
                                          CMFixtureFunction teardown_func,
                                          void ** const volatile state,
                                          const void *const heap_check_point)
{
    const ListNode * const volatile check_point = (const ListNode*)
        (heap_check_point != NULL ?
         heap_check_point : check_point_allocated_blocks());
    int handle_exceptions = 1;
    void *current_state = NULL;
    int rc = 0;

    /* FIXME check only one test or fixture is set */

    /* Detect if we should handle exceptions */
#ifdef _WIN32
    handle_exceptions = !IsDebuggerPresent();
#endif /* _WIN32 */
#ifdef UNIT_TESTING_DEBUG
    handle_exceptions = 0;
#endif /* UNIT_TESTING_DEBUG */


    if (handle_exceptions) {
#ifndef _WIN32
        unsigned int i;
        for (i = 0; i < ARRAY_SIZE(exception_signals); i++) {
            default_signal_functions[i] = signal(
                    exception_signals[i], exception_handler);
        }
#else /* _WIN32 */
        previous_exception_filter = SetUnhandledExceptionFilter(
                exception_filter);
#endif /* !_WIN32 */
    }

    /* Init the test structure */
    initialize_testing(function_name);

    global_running_test = 1;

    if (cm_setjmp(global_run_test_env) == 0) {
        if (test_func != NULL) {
            test_func(state != NULL ? state : &current_state);

            fail_if_blocks_allocated(check_point, function_name);
            rc = 0;
        } else if (setup_func != NULL) {
            rc = setup_func(state != NULL ? state : &current_state);

            /*
             * For setup we can ignore any allocated blocks. We just need to
             * ensure they're deallocated on tear down.
             */
        } else if (teardown_func != NULL) {
            rc = teardown_func(state != NULL ? state : &current_state);

            fail_if_blocks_allocated(check_point, function_name);
        } else {
            /* ERROR */
        }
        fail_if_leftover_values(function_name);
        global_running_test = 0;
    } else {
        /* TEST FAILED */
        global_running_test = 0;
        rc = -1;
    }
    teardown_testing(function_name);

    if (handle_exceptions) {
#ifndef _WIN32
        unsigned int i;
        for (i = 0; i < ARRAY_SIZE(exception_signals); i++) {
            signal(exception_signals[i], default_signal_functions[i]);
        }
#else /* _WIN32 */
        if (previous_exception_filter) {
            SetUnhandledExceptionFilter(previous_exception_filter);
            previous_exception_filter = NULL;
        }
#endif /* !_WIN32 */
    }

    return rc;
}

static int cmocka_run_group_fixture(const char *function_name,
                                    CMFixtureFunction setup_func,
                                    CMFixtureFunction teardown_func,
                                    void **state,
                                    const void *const heap_check_point)
{
    int rc;

    if (setup_func != NULL) {
        rc = cmocka_run_one_test_or_fixture(function_name,
                                        NULL,
                                        setup_func,
                                        NULL,
                                        state,
                                        heap_check_point);
    } else {
        rc = cmocka_run_one_test_or_fixture(function_name,
                                        NULL,
                                        NULL,
                                        teardown_func,
                                        state,
                                        heap_check_point);
    }

    return rc;
}

static int cmocka_run_one_tests(struct CMUnitTestState *test_state)
{
#ifdef HAVE_STRUCT_TIMESPEC
    struct timespec start = {
        .tv_sec = 0,
        .tv_nsec = 0,
    };
    struct timespec finish = {
        .tv_sec = 0,
        .tv_nsec = 0,
    };
#endif
    int rc = 0;

    /* Run setup */
    if (test_state->test->setup_func != NULL) {
        /* Setup the memory check point, it will be evaluated on teardown */
        test_state->check_point = check_point_allocated_blocks();

        rc = cmocka_run_one_test_or_fixture(test_state->test->name,
                                            NULL,
                                            test_state->test->setup_func,
                                            NULL,
                                            &test_state->state,
                                            test_state->check_point);
        if (rc != 0) {
            test_state->status = CM_TEST_ERROR;
            cm_print_error("Test setup failed");
        }
    }

    /* Run test */
#ifdef HAVE_STRUCT_TIMESPEC
    CMOCKA_CLOCK_GETTIME(CLOCK_REALTIME, &start);
#endif

    if (rc == 0) {
        rc = cmocka_run_one_test_or_fixture(test_state->test->name,
                                            test_state->test->test_func,
                                            NULL,
                                            NULL,
                                            &test_state->state,
                                            NULL);
        if (rc == 0) {
            test_state->status = CM_TEST_PASSED;
        } else {
            if (global_skip_test) {
                test_state->status = CM_TEST_SKIPPED;
                global_skip_test = 0; /* Do not skip the next test */
            } else {
                test_state->status = CM_TEST_FAILED;
            }
        }
        rc = 0;
    }

    test_state->runtime = 0.0;

#ifdef HAVE_STRUCT_TIMESPEC
    CMOCKA_CLOCK_GETTIME(CLOCK_REALTIME, &finish);
    test_state->runtime = cm_secdiff(finish, start);
#endif

    /* Run teardown */
    if (rc == 0 && test_state->test->teardown_func != NULL) {
        rc = cmocka_run_one_test_or_fixture(test_state->test->name,
                                            NULL,
                                            NULL,
                                            test_state->test->teardown_func,
                                            &test_state->state,
                                            test_state->check_point);
        if (rc != 0) {
            test_state->status = CM_TEST_ERROR;
            cm_print_error("Test teardown failed");
        }
    }

    test_state->error_message = cm_error_message;
    cm_error_message = NULL;

    return rc;
}

int _cmocka_run_group_tests(const char *group_name,
                            const struct CMUnitTest * const tests,
                            const size_t num_tests,
                            CMFixtureFunction group_setup,
                            CMFixtureFunction group_teardown)
{
    struct CMUnitTestState *cm_tests;
    const ListNode *group_check_point = check_point_allocated_blocks();
    void *group_state = NULL;
    size_t total_tests = 0;
    size_t total_failed = 0;
    size_t total_passed = 0;
    size_t total_executed = 0;
    size_t total_errors = 0;
    size_t total_skipped = 0;
    double total_runtime = 0;
    size_t i;
    int rc;

    /* Make sure LargestIntegralType is at least the size of a pointer. */
    assert_true(sizeof(LargestIntegralType) >= sizeof(void*));

    cm_tests = (struct CMUnitTestState *)libc_malloc(sizeof(struct CMUnitTestState) * num_tests);
    if (cm_tests == NULL) {
        return -1;
    }

    /* Setup cmocka test array */
    for (i = 0; i < num_tests; i++) {
        if (tests[i].name != NULL &&
            (tests[i].test_func != NULL
             || tests[i].setup_func != NULL
             || tests[i].teardown_func != NULL)) {
            cm_tests[i] = (struct CMUnitTestState) {
                .test = &tests[i],
                .status = CM_TEST_NOT_STARTED,
                .state = NULL,
            };
            total_tests++;
        }
    }

    cmprintf_group_start(total_tests);

    rc = 0;

    /* Run group setup */
    if (group_setup != NULL) {
        rc = cmocka_run_group_fixture("cmocka_group_setup",
                                      group_setup,
                                      NULL,
                                      &group_state,
                                      group_check_point);
    }

    if (rc == 0) {
        /* Execute tests */
        for (i = 0; i < total_tests; i++) {
            struct CMUnitTestState *cmtest = &cm_tests[i];
            size_t test_number = i + 1;

            cmprintf(PRINTF_TEST_START, test_number, cmtest->test->name, NULL);

            if (group_state != NULL) {
                cmtest->state = group_state;
            } else if (cmtest->test->initial_state  != NULL) {
                cmtest->state = cmtest->test->initial_state;
            }

            rc = cmocka_run_one_tests(cmtest);
            total_executed++;
            total_runtime += cmtest->runtime;
            if (rc == 0) {
                switch (cmtest->status) {
                    case CM_TEST_PASSED:
                        cmprintf(PRINTF_TEST_SUCCESS,
                                 test_number,
                                 cmtest->test->name,
                                 cmtest->error_message);
                        total_passed++;
                        break;
                    case CM_TEST_SKIPPED:
                        cmprintf(PRINTF_TEST_SKIPPED,
                                 test_number,
                                 cmtest->test->name,
                                 cmtest->error_message);
                        total_skipped++;
                        break;
                    case CM_TEST_FAILED:
                        cmprintf(PRINTF_TEST_FAILURE,
                                 test_number,
                                 cmtest->test->name,
                                 cmtest->error_message);
                        total_failed++;
                        break;
                    default:
                        cmprintf(PRINTF_TEST_ERROR,
                                 test_number,
                                 cmtest->test->name,
                                 "Internal cmocka error");
                        total_errors++;
                        break;
                }
            } else {
                cmprintf(PRINTF_TEST_ERROR,
                         test_number,
                         cmtest->test->name,
                         "Could not run the test - check test fixtures");
                total_errors++;
            }
        }
    } else {
        if (cm_error_message != NULL) {
            print_error("[  ERROR   ] --- %s\n", cm_error_message);
            vcm_free_error(cm_error_message);
            cm_error_message = NULL;
        }
        cmprintf(PRINTF_TEST_ERROR, 0,
                 group_name, "[  FAILED  ] GROUP SETUP");
        total_errors++;
    }

    /* Run group teardown */
    if (group_teardown != NULL) {
        rc = cmocka_run_group_fixture("cmocka_group_teardown",
                                      NULL,
                                      group_teardown,
                                      &group_state,
                                      group_check_point);
        if (rc != 0) {
            if (cm_error_message != NULL) {
                print_error("[  ERROR   ] --- %s\n", cm_error_message);
                vcm_free_error(cm_error_message);
                cm_error_message = NULL;
            }
            cmprintf(PRINTF_TEST_ERROR, 0,
                     group_name, "[  FAILED  ] GROUP TEARDOWN");
        }
    }

    cmprintf_group_finish(group_name,
                          total_executed,
                          total_passed,
                          total_failed,
                          total_errors,
                          total_skipped,
                          total_runtime,
                          cm_tests);

    for (i = 0; i < total_tests; i++) {
        vcm_free_error(discard_const_p(char, cm_tests[i].error_message));
    }
    libc_free(cm_tests);
    fail_if_blocks_allocated(group_check_point, "cmocka_group_tests");

    return total_failed + total_errors;
}

/****************************************************************************
 * DEPRECATED TEST RUNNER
 ****************************************************************************/

int _run_test(
        const char * const function_name,  const UnitTestFunction Function,
        void ** const volatile state, const UnitTestFunctionType function_type,
        const void* const heap_check_point) {
    const ListNode * const volatile check_point = (const ListNode*)
        (heap_check_point ?
         heap_check_point : check_point_allocated_blocks());
    void *current_state = NULL;
    volatile int rc = 1;
    int handle_exceptions = 1;
#ifdef _WIN32
    handle_exceptions = !IsDebuggerPresent();
#endif /* _WIN32 */
#ifdef UNIT_TESTING_DEBUG
    handle_exceptions = 0;
#endif /* UNIT_TESTING_DEBUG */

    cm_error_message_enabled = 0;

    if (handle_exceptions) {
#ifndef _WIN32
        unsigned int i;
        for (i = 0; i < ARRAY_SIZE(exception_signals); i++) {
            default_signal_functions[i] = signal(
                exception_signals[i], exception_handler);
        }
#else /* _WIN32 */
        previous_exception_filter = SetUnhandledExceptionFilter(
            exception_filter);
#endif /* !_WIN32 */
    }

    if (function_type == UNIT_TEST_FUNCTION_TYPE_TEST) {
        print_message("[ RUN      ] %s\n", function_name);
    }
    initialize_testing(function_name);
    global_running_test = 1;
    if (cm_setjmp(global_run_test_env) == 0) {
        Function(state ? state : &current_state);
        fail_if_leftover_values(function_name);

        /* If this is a setup function then ignore any allocated blocks
         * only ensure they're deallocated on tear down. */
        if (function_type != UNIT_TEST_FUNCTION_TYPE_SETUP) {
            fail_if_blocks_allocated(check_point, function_name);
        }

        global_running_test = 0;

        if (function_type == UNIT_TEST_FUNCTION_TYPE_TEST) {
            print_message("[       OK ] %s\n", function_name);
        }
        rc = 0;
    } else {
        global_running_test = 0;
        print_message("[  FAILED  ] %s\n", function_name);
    }
    teardown_testing(function_name);

    if (handle_exceptions) {
#ifndef _WIN32
        unsigned int i;
        for (i = 0; i < ARRAY_SIZE(exception_signals); i++) {
            signal(exception_signals[i], default_signal_functions[i]);
        }
#else /* _WIN32 */
        if (previous_exception_filter) {
            SetUnhandledExceptionFilter(previous_exception_filter);
            previous_exception_filter = NULL;
        }
#endif /* !_WIN32 */
    }

    return rc;
}


int _run_tests(const UnitTest * const tests, const size_t number_of_tests) {
    /* Whether to execute the next test. */
    int run_next_test = 1;
    /* Whether the previous test failed. */
    int previous_test_failed = 0;
    /* Whether the previous setup failed. */
    int previous_setup_failed = 0;
    /* Check point of the heap state. */
    const ListNode * const check_point = check_point_allocated_blocks();
    /* Current test being executed. */
    size_t current_test = 0;
    /* Number of tests executed. */
    size_t tests_executed = 0;
    /* Number of failed tests. */
    size_t total_failed = 0;
    /* Number of setup functions. */
    size_t setups = 0;
    /* Number of teardown functions. */
    size_t teardowns = 0;
    size_t i;
    /*
     * A stack of test states.  A state is pushed on the stack
     * when a test setup occurs and popped on tear down.
     */
    TestState* test_states =
       (TestState*)malloc(number_of_tests * sizeof(*test_states));
    /* The number of test states which should be 0 at the end */
    long number_of_test_states = 0;
    /* Names of the tests that failed. */
    const char** failed_names = (const char**)malloc(number_of_tests *
                                       sizeof(*failed_names));
    void **current_state = NULL;

    /* Count setup and teardown functions */
    for (i = 0; i < number_of_tests; i++) {
        const UnitTest * const test = &tests[i];

        if (test->function_type == UNIT_TEST_FUNCTION_TYPE_SETUP) {
            setups++;
        }

        if (test->function_type == UNIT_TEST_FUNCTION_TYPE_TEARDOWN) {
            teardowns++;
        }
    }

    print_message("[==========] Running %"PRIdS " test(s).\n",
                  number_of_tests - setups - teardowns);

    /* Make sure LargestIntegralType is at least the size of a pointer. */
    assert_true(sizeof(LargestIntegralType) >= sizeof(void*));

    while (current_test < number_of_tests) {
        const ListNode *test_check_point = NULL;
        TestState *current_TestState;
        const UnitTest * const test = &tests[current_test++];
        if (!test->function) {
            continue;
        }

        switch (test->function_type) {
        case UNIT_TEST_FUNCTION_TYPE_TEST:
            if (! previous_setup_failed) {
                run_next_test = 1;
            }
            break;
        case UNIT_TEST_FUNCTION_TYPE_SETUP: {
            /* Checkpoint the heap before the setup. */
            current_TestState = &test_states[number_of_test_states++];
            current_TestState->check_point = check_point_allocated_blocks();
            test_check_point = current_TestState->check_point;
            current_state = &current_TestState->state;
            *current_state = NULL;
            run_next_test = 1;
            break;
        }
        case UNIT_TEST_FUNCTION_TYPE_TEARDOWN:
            /* Check the heap based on the last setup checkpoint. */
            assert_true(number_of_test_states);
            current_TestState = &test_states[--number_of_test_states];
            test_check_point = current_TestState->check_point;
            current_state = &current_TestState->state;
            break;
        default:
            print_error("Invalid unit test function type %d\n",
                        test->function_type);
            exit_test(1);
            break;
        }

        if (run_next_test) {
            int failed = _run_test(test->name, test->function, current_state,
                                   test->function_type, test_check_point);
            if (failed) {
                failed_names[total_failed] = test->name;
            }

            switch (test->function_type) {
            case UNIT_TEST_FUNCTION_TYPE_TEST:
                previous_test_failed = failed;
                total_failed += failed;
                tests_executed ++;
                break;

            case UNIT_TEST_FUNCTION_TYPE_SETUP:
                if (failed) {
                    total_failed ++;
                    tests_executed ++;
                    /* Skip forward until the next test or setup function. */
                    run_next_test = 0;
                    previous_setup_failed = 1;
                }
                previous_test_failed = 0;
                break;

            case UNIT_TEST_FUNCTION_TYPE_TEARDOWN:
                /* If this test failed. */
                if (failed && !previous_test_failed) {
                    total_failed ++;
                }
                break;
            default:
#ifndef _HPUX
                assert_null("BUG: shouldn't be here!");
#endif
                break;
            }
        }
    }

    print_message("[==========] %"PRIdS " test(s) run.\n", tests_executed);
    print_error("[  PASSED  ] %"PRIdS " test(s).\n", tests_executed - total_failed);

    if (total_failed > 0) {
        print_error("[  FAILED  ] %"PRIdS " test(s), listed below:\n", total_failed);
        for (i = 0; i < total_failed; i++) {
            print_error("[  FAILED  ] %s\n", failed_names[i]);
        }
    } else {
        print_error("\n %"PRIdS " FAILED TEST(S)\n", total_failed);
    }

    if (number_of_test_states != 0) {
        print_error("[  ERROR   ] Mismatched number of setup %"PRIdS " and "
                    "teardown %"PRIdS " functions\n", setups, teardowns);
        total_failed = (size_t)-1;
    }

    free(test_states);
    free((void*)failed_names);

    fail_if_blocks_allocated(check_point, "run_tests");
    return (int)total_failed;
}

int _run_group_tests(const UnitTest * const tests, const size_t number_of_tests)
{
    UnitTestFunction setup = NULL;
    const char *setup_name;
    size_t num_setups = 0;
    UnitTestFunction teardown = NULL;
    const char *teardown_name;
    size_t num_teardowns = 0;
    size_t current_test = 0;
    size_t i;

    /* Number of tests executed. */
    size_t tests_executed = 0;
    /* Number of failed tests. */
    size_t total_failed = 0;
    /* Check point of the heap state. */
    const ListNode * const check_point = check_point_allocated_blocks();
    const char** failed_names = (const char**)malloc(number_of_tests *
                                       sizeof(*failed_names));
    void **current_state = NULL;
    TestState group_state;

    /* Find setup and teardown function */
    for (i = 0; i < number_of_tests; i++) {
        const UnitTest * const test = &tests[i];

        if (test->function_type == UNIT_TEST_FUNCTION_TYPE_GROUP_SETUP) {
            if (setup == NULL) {
                setup = test->function;
                setup_name = test->name;
                num_setups = 1;
            } else {
                print_error("[  ERROR   ] More than one group setup function detected\n");
                exit_test(1);
            }
        }

        if (test->function_type == UNIT_TEST_FUNCTION_TYPE_GROUP_TEARDOWN) {
            if (teardown == NULL) {
                teardown = test->function;
                teardown_name = test->name;
                num_teardowns = 1;
            } else {
                print_error("[  ERROR   ] More than one group teardown function detected\n");
                exit_test(1);
            }
        }
    }

    print_message("[==========] Running %"PRIdS " test(s).\n",
                  number_of_tests - num_setups - num_teardowns);

    if (setup != NULL) {
        int failed;

        group_state.check_point = check_point_allocated_blocks();
        current_state = &group_state.state;
        *current_state = NULL;
        failed = _run_test(setup_name,
                           setup,
                           current_state,
                           UNIT_TEST_FUNCTION_TYPE_SETUP,
                           group_state.check_point);
        if (failed) {
            failed_names[total_failed] = setup_name;
        }

        total_failed += failed;
        tests_executed++;
    }

    while (current_test < number_of_tests) {
        int run_test = 0;
        const UnitTest * const test = &tests[current_test++];
        if (test->function == NULL) {
            continue;
        }

        switch (test->function_type) {
        case UNIT_TEST_FUNCTION_TYPE_TEST:
            run_test = 1;
            break;
        case UNIT_TEST_FUNCTION_TYPE_SETUP:
        case UNIT_TEST_FUNCTION_TYPE_TEARDOWN:
        case UNIT_TEST_FUNCTION_TYPE_GROUP_SETUP:
        case UNIT_TEST_FUNCTION_TYPE_GROUP_TEARDOWN:
            break;
        default:
            print_error("Invalid unit test function type %d\n",
                        test->function_type);
            break;
        }

        if (run_test) {
            int failed;

            failed = _run_test(test->name,
                               test->function,
                               current_state,
                               test->function_type,
                               NULL);
            if (failed) {
                failed_names[total_failed] = test->name;
            }

            total_failed += failed;
            tests_executed++;
        }
    }

    if (teardown != NULL) {
        int failed;

        failed = _run_test(teardown_name,
                           teardown,
                           current_state,
                           UNIT_TEST_FUNCTION_TYPE_GROUP_TEARDOWN,
                           group_state.check_point);
        if (failed) {
            failed_names[total_failed] = teardown_name;
        }

        total_failed += failed;
        tests_executed++;
    }

    print_message("[==========] %"PRIdS " test(s) run.\n", tests_executed);
    print_error("[  PASSED  ] %"PRIdS " test(s).\n", tests_executed - total_failed);

    if (total_failed) {
        print_error("[  FAILED  ] %"PRIdS " test(s), listed below:\n", total_failed);
        for (i = 0; i < total_failed; i++) {
            print_error("[  FAILED  ] %s\n", failed_names[i]);
        }
    } else {
        print_error("\n %"PRIdS " FAILED TEST(S)\n", total_failed);
    }

    free((void*)failed_names);
    fail_if_blocks_allocated(check_point, "run_group_tests");

    return (int)total_failed;
}

