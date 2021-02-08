//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU Affero General Public License Version 3
//    as published by the Free Software Foundation.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU Affero General Public License for more details.
//
//    You should have received a copy of the GNU Affero General Public License
//    along with this program in the COPYING file.
//    If not, see <http://www.gnu.org/licenses/>.

#pragma once

#include <errno.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/resource.h>
#include <unistd.h>

#include <string>

#include <openvpn/common/file.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/number.hpp>
#include <openvpn/common/exception.hpp>

#if defined(__APPLE__)
#include <mach/mach.h>

  /**
   * Wrapper around the mach thread_info call that emulates the Linux
   * getrusage with Thread specific call.
   */
  static int getrusage_thread(struct rusage& rusage)
  {
    int ret = -1;
    thread_basic_info_data_t info{};
    mach_msg_type_number_t info_count = THREAD_BASIC_INFO_COUNT;
    kern_return_t kern_err;

    kern_err = thread_info(mach_thread_self(),
			   THREAD_BASIC_INFO,
			   (thread_info_t)&info,
			   &info_count);
    if (kern_err == KERN_SUCCESS) {
	rusage.ru_utime.tv_sec = info.user_time.seconds;
	rusage.ru_utime.tv_usec = info.user_time.microseconds;
	rusage.ru_stime.tv_sec = info.system_time.seconds;
	rusage.ru_stime.tv_usec = info.system_time.microseconds;
	ret = 0;
      } else {
	errno = EINVAL;
      }
    return ret;
  }

#endif

namespace openvpn {
  /**
   * Retrieve the time (in seconds) the current process or thread
   * has been running.  Runing time includes both system and user
   * times.
   *
   * @param thread  Boolean flag controlling if process or thread
   *                runtime should be returned
   *
   * @return Returns a double containing number of seconds the
   *         current process (PID) or thread has been running.
   *         On errors -1.0 is returned.
   *
   */
  inline double cpu_time(const bool thread=false)
  {
    try
      {
        struct rusage usage{};

        int ret = 0;
#if defined(__APPLE__)
	if (thread)
	  ret = getrusage_thread(usage);
	else
	  ret = getrusage(RUSAGE_SELF, &usage);
#else
	ret = getrusage((thread ? RUSAGE_THREAD : RUSAGE_SELF), &usage);
#endif
        if (ret != 0)
          {
            throw Exception("getrusage() call failed: " + std::string(strerror(errno)));
          }
        double utime = usage.ru_utime.tv_sec + ((double)usage.ru_utime.tv_usec / 1000000);
        double stime = usage.ru_stime.tv_sec + ((double)usage.ru_stime.tv_usec / 1000000);

        return utime + stime;
      }
    catch (const std::exception& e)
      {
	//OPENVPN_LOG("cpu_time exception: " << e.what());
	return -1.0;
      }
  }


}
