//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012- OpenVPN Inc.
//
//    SPDX-License-Identifier: MPL-2.0 OR AGPL-3.0-only WITH openvpn3-openssl-exception
//

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

    ASIOSignals(openvpn_io::io_context &io_context)
        : halt(false), signals_(io_context)
    {
    }

    enum
    {
        S_SIGINT = (1 << 0),
        S_SIGTERM = (1 << 1),
#ifndef OPENVPN_PLATFORM_WIN
        S_SIGQUIT = (1 << 2),
        S_SIGHUP = (1 << 3),
        S_SIGUSR1 = (1 << 4),
        S_SIGUSR2 = (1 << 5),
#endif
    };

    template <typename SignalHandler>
    void register_signals(SignalHandler stop_handler, unsigned int sigmask = (S_SIGINT | S_SIGTERM))
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

} // namespace openvpn

#endif
