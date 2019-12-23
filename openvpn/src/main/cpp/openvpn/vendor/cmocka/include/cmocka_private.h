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

#ifndef CMOCKA_PRIVATE_H_
#define CMOCKA_PRIVATE_H_

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <stdint.h>

#ifdef _WIN32
#include <windows.h>

# ifdef _MSC_VER
# include <stdio.h> /* _snprintf */

#  undef inline
#  define inline __inline

#  ifndef va_copy
#   define va_copy(dest, src) (dest = src)
#  endif

#  define strcasecmp _stricmp
#  define strncasecmp _strnicmp

#  if defined(HAVE__SNPRINTF_S)
#   undef snprintf
#   define snprintf(d, n, ...) _snprintf_s((d), (n), _TRUNCATE, __VA_ARGS__)
#  else /* HAVE__SNPRINTF_S */
#   if defined(HAVE__SNPRINTF)
#     undef snprintf
#     define snprintf _snprintf
#   else /* HAVE__SNPRINTF */
#    if !defined(HAVE_SNPRINTF)
#     error "no snprintf compatible function found"
#    endif /* HAVE_SNPRINTF */
#   endif /* HAVE__SNPRINTF */
#  endif /* HAVE__SNPRINTF_S */

#  if defined(HAVE__VSNPRINTF_S)
#   undef vsnprintf
#   define vsnprintf(s, n, f, v) _vsnprintf_s((s), (n), _TRUNCATE, (f), (v))
#  else /* HAVE__VSNPRINTF_S */
#   if defined(HAVE__VSNPRINTF)
#    undef vsnprintf
#    define vsnprintf _vsnprintf
#   else
#    if !defined(HAVE_VSNPRINTF)
#     error "No vsnprintf compatible function found"
#    endif /* HAVE_VSNPRINTF */
#   endif /* HAVE__VSNPRINTF */
#  endif /* HAVE__VSNPRINTF_S */
# endif /* _MSC_VER */

/*
 * Backwards compatibility with headers shipped with Visual Studio 2005 and
 * earlier.
 */
WINBASEAPI BOOL WINAPI IsDebuggerPresent(VOID);

#ifndef PRIdS
# define PRIdS "Id"
#endif

#ifndef PRIu64
# define PRIu64 "I64u"
#endif

#ifndef PRIuMAX
# define PRIuMAX PRIu64
#endif

#ifndef PRIxMAX
#define PRIxMAX "I64x"
#endif

#ifndef PRIXMAX
#define PRIXMAX "I64X"
#endif

#else /* _WIN32 */

#ifndef __PRI64_PREFIX
# if __WORDSIZE == 64
#  define __PRI64_PREFIX "l"
# else
#  define __PRI64_PREFIX "ll"
# endif
#endif

#ifndef PRIdS
# define PRIdS "zd"
#endif

#ifndef PRIu64
# define PRIu64 __PRI64_PREFIX "u"
#endif

#ifndef PRIuMAX
# define PRIuMAX __PRI64_PREFIX "u"
#endif

#ifndef PRIxMAX
#define PRIxMAX __PRI64_PREFIX "x"
#endif

#ifndef PRIXMAX
#define PRIXMAX __PRI64_PREFIX "X"
#endif

#endif /* _WIN32 */

/** Free memory space */
#define SAFE_FREE(x) do { if ((x) != NULL) {free(x); x=NULL;} } while(0)

/** Zero a structure */
#define ZERO_STRUCT(x) memset((char *)&(x), 0, sizeof(x))

/** Zero a structure given a pointer to the structure */
#define ZERO_STRUCTP(x) do { if ((x) != NULL) memset((char *)(x), 0, sizeof(*(x))); } while(0)

/** Get the size of an array */
#define ARRAY_SIZE(a) (sizeof(a)/sizeof(a[0]))

/** Overwrite the complete string with 'X' */
#define BURN_STRING(x) do { if ((x) != NULL) memset((x), 'X', strlen((x))); } while(0)

/**
 * This is a hack to fix warnings. The idea is to use this everywhere that we
 * get the "discarding const" warning by the compiler. That doesn't actually
 * fix the real issue, but marks the place and you can search the code for
 * discard_const.
 *
 * Please use this macro only when there is no other way to fix the warning.
 * We should use this function in only in a very few places.
 *
 * Also, please call this via the discard_const_p() macro interface, as that
 * makes the return type safe.
 */
#define discard_const(ptr) ((void *)((uintptr_t)(ptr)))

/**
 * Type-safe version of discard_const
 */
#define discard_const_p(type, ptr) ((type *)discard_const(ptr))

#endif /* CMOCKA_PRIVATE_H_ */
