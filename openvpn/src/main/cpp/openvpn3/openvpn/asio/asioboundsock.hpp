//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2022 OpenVPN Inc.
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
    explicit Socket(openvpn_io::io_context &io_context)
        : SocketBase(io_context)
    {
    }

    // May be called twice with IPv4 and IPv6 address.
    // If port 0, kernel will dynamically allocate free port.
    void bind_local(const IP::Addr &addr, const unsigned short port = 0)
    {
        switch (addr.version())
        {
        case IP::Addr::V4:
            v4.bind_local(addr.to_ipv4(), port);
            break;
        case IP::Addr::V6:
            v6.bind_local(addr.to_ipv6(), port);
            break;
        default:
            return;
        }
    }

    std::string to_string() const
    {
        std::string ret;
        ret.reserve(64);

        if (v4.defined())
        {
            ret += "local4=";
            ret += v4.to_string();
        }
        if (v6.defined())
        {
            if (!ret.empty())
                ret += ' ';
            ret += "local6=";
            ret += v6.to_string();
        }

        try
        {
            const std::string re = openvpn::to_string(remote_endpoint());
            if (!ret.empty())
                ret += ' ';
            ret += "remote=";
            ret += re;
        }
        catch (const std::exception &e)
        {
        }
        return ret;
    }

  protected:
    template <typename IP_ADDR>
    class Proto
    {
      public:
        Proto()
        {
            local_.zero();
            port_ = 0;
        }

        bool defined() const
        {
            return local_.specified();
        }

        void bind_local(const IP_ADDR &local, const unsigned short port)
        {
            local_ = local;
            port_ = port;
        }

        template <typename PARENT>
        void post_open(PARENT *parent, openvpn_io::error_code &ec) const
        {
            if (defined())
            {
                parent->set_option(openvpn_io::socket_base::reuse_address(true), ec);
                if (!ec)
                    parent->bind(openvpn_io::ip::tcp::endpoint(local_.to_asio(), port_), ec);
            }
        }

        std::string to_string() const
        {
            return '[' + local_.to_string() + "]:" + std::to_string(port_);
        }

      private:
        IP_ADDR local_;
        unsigned short port_;
    };

    virtual void async_connect_post_open(const protocol_type &protocol, openvpn_io::error_code &ec) override
    {
        if (protocol == openvpn_io::ip::tcp::v4())
            v4.post_open(this, ec);
        else if (protocol == openvpn_io::ip::tcp::v6())
            v6.post_open(this, ec);
    }

  private:
    Proto<IPv4::Addr> v4;
    Proto<IPv6::Addr> v6;
};

} // namespace AsioBoundSocket
} // namespace openvpn

#endif
