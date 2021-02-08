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

#include <unistd.h>    // for unlink()
#include <sys/stat.h>  // for chmod()

#include <openvpn/acceptor/base.hpp>

namespace openvpn {
  namespace Acceptor {

    struct Unix : public Base
    {
      OPENVPN_EXCEPTION(unix_acceptor_error);

      typedef RCPtr<Unix> Ptr;

      Unix(openvpn_io::io_context& io_context)
	: acceptor(io_context)
      {
      }

      virtual void async_accept(ListenerBase* listener,
				const size_t acceptor_index,
				openvpn_io::io_context& io_context) override
      {
	AsioPolySock::Unix::Ptr sock(new AsioPolySock::Unix(io_context, acceptor_index));
	acceptor.async_accept(sock->socket, [listener=ListenerBase::Ptr(listener), sock](const openvpn_io::error_code& error) mutable
			      {
				listener->handle_accept(std::move(sock), error);
			      });
      }

      virtual void close() override
      {
	acceptor.close();
      }

      static void pre_listen(const std::string& socket_path)
      {
	// remove previous socket instance
	::unlink(socket_path.c_str());
      }

      // set socket permissions in filesystem
      static void set_socket_permissions(const std::string& socket_path,
					 const mode_t unix_mode)
      {
	if (unix_mode)
	  {
	    if (::chmod(socket_path.c_str(), unix_mode) < 0)
	      throw unix_acceptor_error("chmod failed on unix socket");
	  }
      }

      openvpn_io::local::stream_protocol::endpoint local_endpoint;
      openvpn_io::basic_socket_acceptor<openvpn_io::local::stream_protocol> acceptor;
    };

  }
}
