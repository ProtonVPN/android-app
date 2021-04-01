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

#include <windows.h>

#include <openvpn/buffer/bufhex.hpp>
#include <openvpn/win/winerr.hpp>

namespace openvpn {
  namespace Win {
    namespace HandleComm {

      OPENVPN_EXCEPTION(handle_comm);

      // Duplicate a local handle into the address space of a
      // remote process and return as a hex string that can be
      // communicated across a process boundary.
      inline std::string send_handle(const HANDLE handle,
				     const HANDLE remote_process)
      {
	HANDLE remote_handle;
	if (!::DuplicateHandle(GetCurrentProcess(),
			       handle,
			       remote_process,
			       &remote_handle,
			       0,
			       FALSE,
			       DUPLICATE_SAME_ACCESS))
	  {
	    const Win::LastError err;
	    OPENVPN_THROW(handle_comm, "send_handle: DuplicateHandle failed: " << err.message());
	  }
	return BufHex::render(remote_handle);
      }

      // Duplicate a remote handle (specified as a hex string) into
      // the address space of the local process.
      inline HANDLE receive_handle(const std::string& remote_handle_hex,
				   const HANDLE remote_process)
      {
	const HANDLE remote_handle = BufHex::parse<HANDLE>(remote_handle_hex, "receive_handle");
	HANDLE local_handle;
	if (!::DuplicateHandle(remote_process,
			       remote_handle,
			       GetCurrentProcess(),
			       &local_handle,
			       0,
			       FALSE,
			       DUPLICATE_SAME_ACCESS))
	  {
	    const Win::LastError err;
	    OPENVPN_THROW(handle_comm, "receive_handle: DuplicateHandle failed: " << err.message());
	  }
	return local_handle;
      }

    }
  }
}
