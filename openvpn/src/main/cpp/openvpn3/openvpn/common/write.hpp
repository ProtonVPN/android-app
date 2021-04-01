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

#ifndef OPENVPN_COMMON_WRITE_H
#define OPENVPN_COMMON_WRITE_H

#include <unistd.h>
#include <stdlib.h> // defines std::abort()

namespace openvpn {
  // like posix write() but retry if full buffer is not written
  inline ssize_t write_retry(int fd, const void *buf, size_t count)
  {
    size_t total = 0;
    while (true)
      {
	const ssize_t status = ::write(fd, buf, count);
	if (status < 0)
	  return status;
	if (static_cast<size_t>(status) > count) // should never happen
	  std::abort();
	total += status;
	count -= status;
	if (!count)
	  break;
	buf = static_cast<const unsigned char*>(buf) + status;
      }
    return total;
  }
}

#endif
