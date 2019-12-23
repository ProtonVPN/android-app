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

// Process-wide static initialization

#ifndef OPENVPN_INIT_INITPROCESS_H
#define OPENVPN_INIT_INITPROCESS_H

#include <thread>
#include <mutex>

#include <openvpn/common/size.hpp>
#include <openvpn/common/base64.hpp>
#include <openvpn/common/extern.hpp>
#include <openvpn/time/time.hpp>
#include <openvpn/compress/compress.hpp>
#include <openvpn/init/cryptoinit.hpp>
#include <openvpn/init/engineinit.hpp>

namespace openvpn {
  namespace InitProcess {

    class Init
    {
    public:
      Init()
      {
	// initialize time base
	Time::reset_base();

	// initialize compression
	CompressContext::init_static();

	// init OpenSSL if included
	init_openssl("auto");

	base64_init_static();
      }

      ~Init()
      {
	base64_uninit_static();
      }
    };

    // process-wide singular instance
    OPENVPN_EXTERN Init* the_instance; // GLOBAL
    OPENVPN_EXTERN std::mutex the_instance_mutex; // GLOBAL

    inline void init()
    {
      std::lock_guard<std::mutex> lock(the_instance_mutex);
      if (!the_instance)
	the_instance = new Init();
    }

    inline void uninit()
    {
      std::lock_guard<std::mutex> lock(the_instance_mutex);
      if (the_instance)
	{
	  delete the_instance;
	  the_instance = nullptr;
	}
    }

  }
}

#endif
