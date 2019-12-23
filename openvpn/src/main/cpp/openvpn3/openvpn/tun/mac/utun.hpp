//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2017 OpenVPN Inc.
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
//

// Thanks to Jonathan Levin for proof-of-concept utun code for Mac OS X.
// http://newosxbook.com/src.jl?tree=listings&file=17-15-utun.c

// Open a utun device on Mac OS X.

#ifndef OPENVPN_TUN_MAC_UTUN_H
#define OPENVPN_TUN_MAC_UTUN_H

#include <sys/types.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/sys_domain.h>
#include <sys/kern_control.h>
#include <net/if_utun.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <syslog.h>
#include <unistd.h>
#include <stdlib.h>

#include <string>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/to_string.hpp>
#include <openvpn/common/scoped_fd.hpp>

namespace openvpn {
  namespace TunMac {
    namespace UTun {
      OPENVPN_EXCEPTION(utun_error);

      // Open specific utun device unit and return fd.
      // If the unit number is already in use, return -1.
      // Throw exceptions for all other errors.
      // Return the iface name in name.
      inline int utun_open(std::string& name, const int unit)
      {
	struct sockaddr_ctl sc;
	struct ctl_info ctlInfo;

	memset(&ctlInfo, 0, sizeof(ctlInfo));
	if (strlcpy(ctlInfo.ctl_name, UTUN_CONTROL_NAME, sizeof(ctlInfo.ctl_name))
	    >= sizeof(ctlInfo.ctl_name))
	  throw utun_error("UTUN_CONTROL_NAME too long");

	ScopedFD fd(socket(PF_SYSTEM, SOCK_DGRAM, SYSPROTO_CONTROL));
	if (!fd.defined())
	  throw utun_error("socket(SYSPROTO_CONTROL)");

	if (ioctl(fd(), CTLIOCGINFO, &ctlInfo) == -1)
	  throw utun_error("ioctl(CTLIOCGINFO)");

	sc.sc_id = ctlInfo.ctl_id;
	sc.sc_len = sizeof(sc);
	sc.sc_family = AF_SYSTEM;
	sc.ss_sysaddr = AF_SYS_CONTROL;
	sc.sc_unit = unit + 1;

	// If the connect is successful, a utunX device will be created, where X
	// is our unit number - 1.
	if (connect(fd(), (struct sockaddr *)&sc, sizeof(sc)) == -1)
	  return -1;

	// Get iface name of newly created utun dev.
	char utunname[20];
	socklen_t utunname_len = sizeof(utunname);
	if (getsockopt(fd(), SYSPROTO_CONTROL, UTUN_OPT_IFNAME, utunname, &utunname_len))
	  throw utun_error("getsockopt(SYSPROTO_CONTROL)");
	name = utunname;

	return fd.release();
      }

      // Try to open an available utun device unit.
      // Return the iface name in name.
      inline int utun_open(std::string& name)
      {
	for (int unit = 0; unit < 256; ++unit)
	  {
	    const int fd = utun_open(name, unit);
	    if (fd >= 0)
	      return fd;
	  }
	throw utun_error("cannot open available utun device");
      }
    }
  }
}

#endif
