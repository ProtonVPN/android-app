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

// A simple class that allows an arbitrary set of posix signals to be
// associated with an Asio handler.

#ifndef OPENVPN_ASIO_ASIOSIGNAL_H
#define OPENVPN_ASIO_ASIOSIGNAL_H

#include <openvpn/io/io.hpp>

#include <openvpn/common/platform.hpp>
#include <openvpn/common/rc.hpp>

namespace openvpn {

  class ASIOSignals : public RC<thread_safe_refcount>
  {
  public:
    typedef RCPtr<ASIOSignals> Ptr;

    ASIOSignals(openvpn_io::io_context& io_context)
      : halt(false), signals_(io_context) {}

    enum {
      S_SIGINT  = (1<<0),
      S_SIGTERM = (1<<1),
#ifndef OPENVPN_PLATFORM_WIN
      S_SIGQUIT = (1<<2),
      S_SIGHUP  = (1<<3),
      S_SIGUSR1  = (1<<4),
      S_SIGUSR2  = (1<<5),
#endif
    };

    template <typename SignalHandler>
    void register_signals(SignalHandler stop_handler, unsigned int sigmask = (S_SIGINT|S_SIGTERM))
    {
      if (sigmask & S_SIGINT)
	signals_.add(SIGINT);
      if (sigmask & S_SIGTERM)
	signals_.add(SIGTERM);
#ifndef OPENVPN_PLATFORM_WIN
      if (sigmask & S_SIGQUIT)
	signals_.add(SIGQUIT);
      if (sigmask & S_SIGHUP)
	signals_.add(SIGHUP);
      if (sigmask & S_SIGUSR1)
	signals_.add(SIGUSR1);
      if (sigmask & S_SIGUSR2)
	signals_.add(SIGUSR2);
#endif
      signals_.async_wait(stop_handler);
    }

    template <typename SignalHandler>
    void register_signals_all(SignalHandler stop_handler)
    {
      register_signals(stop_handler,
		         S_SIGINT
		       | S_SIGTERM
#ifndef OPENVPN_PLATFORM_WIN
		       | S_SIGHUP
		       | S_SIGUSR1
		       | S_SIGUSR2
#endif
		       );
    }

    void cancel()
    {
      if (!halt)
	{
	  halt = true;
	  signals_.cancel();
	}
    }

  private:
    bool halt;
    openvpn_io::signal_set signals_;
  };

}

#endif
