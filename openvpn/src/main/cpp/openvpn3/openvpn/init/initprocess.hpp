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

// Process-wide static initialization

#pragma once

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
    private:
      class InitImpl
      {
      public:
	InitImpl()
	{
	  // initialize time base
	  Time::reset_base();

	  // initialize compression
	  CompressContext::init_static();

	  // init OpenSSL if included
	  init_openssl("auto");

	  base64_init_static();
	}

	~InitImpl()
	{
	  base64_uninit_static();
	}

      private:
	// SSL library init happens when instantiated
	crypto_init crypto_init_;
      };

      // process-wide singular instance
      static std::weak_ptr<InitImpl> init_instance; // GLOBAL
      static std::mutex the_instance_mutex; // GLOBAL

      // istance of this class to refcount
      std::shared_ptr<InitImpl> initptr;

    public:
      Init()
      {
	std::lock_guard<std::mutex> lock(the_instance_mutex);

	initptr = init_instance.lock();
	if (!initptr)
	  {
	    initptr = std::make_shared<InitImpl>();
	    init_instance = initptr;
	  }
      }

      ~Init()
      {
        //explicitly reset smart pointer to make the destructor run under the lock_guard
	std::lock_guard<std::mutex> lock(the_instance_mutex);
	initptr.reset();
      }
    };

#ifdef OPENVPN_NO_EXTERN
    std::weak_ptr<Init::InitImpl> Init::init_instance; // GLOBAL
    std::mutex Init::the_instance_mutex; // GLOBAL
#endif
  }
}




