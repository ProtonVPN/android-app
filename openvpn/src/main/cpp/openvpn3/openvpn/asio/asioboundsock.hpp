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

// Asio TCP socket that can be configured so that open() method
// always prebinds the socket to a given local address.  Useful
// for TCP clients.

#ifndef OPENVPN_ASIO_ASIOBOUNDSOCK_H
#define OPENVPN_ASIO_ASIOBOUNDSOCK_H

#include <openvpn/io/io.hpp>

#include <openvpn/addr/ip.hpp>
#include <openvpn/common/extern.hpp>
#include <openvpn/common/to_string.hpp>

namespace openvpn {
  namespace AsioBoundSocket {

    typedef openvpn_io::basic_stream_socket<openvpn_io::ip::tcp> SocketBase;

    class Socket : public SocketBase
    {
    public:
      explicit Socket(openvpn_io::io_context& io_context)
	: SocketBase(io_context)
      {
      }

      // if port 0, kernel will dynamically allocate free port
      void bind_local(const IP::Addr& addr, const unsigned short port=0)
      {
	bind_local_addr = addr;
	bind_local_port = port;
      }

      std::string to_string() const
      {
	std::string ret;
	ret.reserve(64);
	if (bind_local_addr.defined())
	  {
	    ret += "local=[";
	    ret += bind_local_addr.to_string();
	    ret += "]:";
	    ret += openvpn::to_string(bind_local_port);
	  }
	try {
	  const std::string re = openvpn::to_string(remote_endpoint());
	  if (!ret.empty())
	    ret += ' ';
	  ret += "remote=";
	  ret += re;
	}
	catch (const std::exception& e)
	  {
	  }
	return ret;
      }

    protected:
      virtual void async_connect_post_open(const protocol_type& protocol, openvpn_io::error_code& ec) override
      {
	if (bind_local_addr.defined())
	  {
	    set_option(openvpn_io::socket_base::reuse_address(true), ec);
	    if (ec)
	      return;
	    bind(openvpn_io::ip::tcp::endpoint(bind_local_addr.to_asio(), bind_local_port), ec);
	  }
      }

    private:
      IP::Addr bind_local_addr;
      unsigned short bind_local_port = 0;
    };

  }
}

#endif
