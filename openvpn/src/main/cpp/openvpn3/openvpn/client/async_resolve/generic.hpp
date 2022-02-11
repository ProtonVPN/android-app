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

#ifndef OPENVPN_CLIENT_ASYNC_RESOLVE_GENERIC_H
#define OPENVPN_CLIENT_ASYNC_RESOLVE_GENERIC_H

#include <openvpn/common/bigmutex.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/common/hostport.hpp>


namespace openvpn {
  template<typename RESOLVER_TYPE>
  class AsyncResolvable: public virtual RC<thread_unsafe_refcount>
  {
  private:
    typedef RCPtr<AsyncResolvable> Ptr;

    openvpn_io::io_context& io_context;
    RESOLVER_TYPE resolver;

  public:
    using resolver_type = RESOLVER_TYPE;
    using results_type = typename RESOLVER_TYPE::results_type;

    AsyncResolvable(openvpn_io::io_context& io_context_arg)
      : io_context(io_context_arg),
        resolver(io_context_arg)
    {
    }

    virtual void resolve_callback(const openvpn_io::error_code& error,
				  results_type results) = 0;

    // This implementation assumes that the i/o reactor provides an asynchronous
    // DNS resolution routine using its own primitives and that doesn't require
    // us to take care of any non-interruptible opration (i.e. getaddrinfo() in
    // case of ASIO).
    //
    // For example, iOS implements aync_resolve using GCD and CFHost. This
    // implementation satisfies the constraints mentioned above
    virtual void async_resolve_name(const std::string& host, const std::string& port)
    {
	resolver.async_resolve(host, port, [self=Ptr(this)](const openvpn_io::error_code& error,
							    results_type results)
	{
	  OPENVPN_ASYNC_HANDLER;
	  self->resolve_callback(error, results);
	});
    }

    // no-op: needed to provide the same class signature of the ASIO version
    void async_resolve_lock()
    {
    }

    void async_resolve_cancel()
    {
      resolver.cancel();
    }
  };
}

#endif /* OPENVPN_CLIENT_ASYNC_RESOLVE_GENERIC_H */
