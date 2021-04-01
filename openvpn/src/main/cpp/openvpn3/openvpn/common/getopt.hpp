/*
 * Copyright (c) 1987, 1993, 1994, 1996
 *	The Regents of the University of California.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. All advertising materials mentioning features or use of this software
 *    must display the following acknowledgement:
 *	This product includes software developed by the University of
 *	California, Berkeley and its contributors.
 * 4. Neither the name of the University nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

#ifndef OPENVPN_COMMON_GETOPT_H
#define OPENVPN_COMMON_GETOPT_H

#include <openvpn/common/platform.hpp>
#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>

#if !defined(OPENVPN_PLATFORM_WIN)
#include <getopt.h>
#else

#include <cstring> // for std::strlen, std::strchr, std::strncmp

#define	GETOPT_BADCH	(int)'?'
#define	GETOPT_BADARG	(int)':'
#define	GETOPT_EMSG	""

namespace openvpn {

  OPENVPN_SIMPLE_EXCEPTION(getopt_assert);
  OPENVPN_EXCEPTION(getopt_exception);

  int opterr = 1;         /* if error message should be printed */
  int optind = 1;         /* index into parent argv vector */
  int optopt = 0;         /* character checked for validity */
  int optreset = 0;       /* reset getopt */
  const char *optarg = nullptr;    /* argument associated with option */

  struct option
  {
    const char *name;
    int has_arg;
    int *flag;
    int val;
  };

  enum {
    no_argument=0,
    required_argument=1,
    optional_argument=2
  };

  namespace getopt_private {
    inline void error(const char *prefix, int arg)
    {
      std::string err = prefix;
      err += " -- ";
      err += (char)arg;
      throw getopt_exception(err);
    }

    inline void error(const char *prefix, const char *arg)
    {
      std::string err = prefix;
      err += " -- ";
      err += arg;
      throw getopt_exception(err);
    }

    /*
     * getopt --
     *	Parse argc/argv argument vector.
     */
    inline int getopt_internal(int nargc, char * const *nargv, const char *ostr)
    {
      static const char *place = GETOPT_EMSG;	/* option letter processing */
      const char *oli;				/* option letter list index */

      if (!nargv || !ostr)
	throw getopt_assert();

      if (optreset || !*place) {		/* update scanning pointer */
	optreset = 0;
	if (optind >= nargc || *(place = nargv[optind]) != '-') {
	  place = GETOPT_EMSG;
	  return (-1);
	}
	if (place[1] && *++place == '-') {	/* found "--" */
	  /* ++optind; */
	  place = GETOPT_EMSG;
	  return (-2);
	}
      }					/* option letter okay? */
      if ((optopt = (int)*place++) == (int)':' ||
	  !(oli = std::strchr(ostr, optopt))) {
	/*
	 * if the user didn't specify '-' as an option,
	 * assume it means -1.
	 */
	if (optopt == (int)'-')
	  return (-1);
	if (!*place)
	  ++optind;
	if (opterr && *ostr != ':')
	  getopt_private::error("illegal option", optopt);
	return (GETOPT_BADCH);
      }
      if (*++oli != ':') {			/* don't need argument */
	optarg = nullptr;
	if (!*place)
	  ++optind;
      } else {				/* need an argument */
	if (*place)			/* no white space */
	  optarg = place;
	else if (nargc <= ++optind) {	/* no arg */
	  place = GETOPT_EMSG;
	  if ((opterr) && (*ostr != ':'))
	    getopt_private::error("option requires an argument", optopt);
	  return (GETOPT_BADARG);
	} else				/* white space */
	  optarg = nargv[optind];
	place = GETOPT_EMSG;
	++optind;
      }
      return (optopt);			/* dump back option letter */
    }
  }

  /*
   * getopt_long --
   *	Parse argc/argv argument vector.
   */
  inline int getopt_long(int nargc, char * const *nargv, const char *options,
			 const struct option *long_options, int *index)
  {
    int retval;

    if (!nargv || !options || !long_options)
      throw getopt_assert();

    if ((retval = getopt_private::getopt_internal(nargc, nargv, options)) == -2) {
      char *current_argv = nargv[optind++] + 2;
      char *has_equal;
      int i;
      int current_argv_len;
      int match = -1;

      if (*current_argv == '\0')
	return(-1);
      if ((has_equal = std::strchr(current_argv, '=')) != nullptr) {
	current_argv_len = has_equal - current_argv;
	has_equal++;
      } else
	current_argv_len = std::strlen(current_argv);

      for (i = 0; long_options[i].name; i++) {
	if (std::strncmp(current_argv, long_options[i].name, current_argv_len))
	  continue;

	if (std::strlen(long_options[i].name) == (unsigned)current_argv_len) {
	  match = i;
	  break;
	}
	if (match == -1)
	  match = i;
      }
      if (match != -1) {
	if (long_options[match].has_arg == required_argument ||
	    long_options[match].has_arg == optional_argument) {
	  if (has_equal)
	    optarg = has_equal;
	  else
	    optarg = nargv[optind++];
	}
	if ((long_options[match].has_arg == required_argument)
	    && (optarg == nullptr)) {
	  /*
	   * Missing argument, leading :
	   * indicates no error should be generated
	   */
	  if ((opterr) && (*options != ':'))
	    getopt_private::error("option requires an argument", current_argv);
	  return (GETOPT_BADARG);
	}
      } else { /* No matching argument */
	if ((opterr) && (*options != ':'))
	  getopt_private::error("illegal option", current_argv);
	return (GETOPT_BADCH);
      }
      if (long_options[match].flag) {
	*long_options[match].flag = long_options[match].val;
	retval = 0;
      } else
	retval = long_options[match].val;
      if (index)
	*index = match;
    }
    return(retval);
  }

}
#endif
#endif
