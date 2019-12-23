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

#ifndef OPENVPN_COMMON_SIGNAL_H
#define OPENVPN_COMMON_SIGNAL_H

#include <openvpn/common/platform.hpp>

#if !defined(OPENVPN_PLATFORM_WIN)

#include <signal.h>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>

namespace openvpn {
  class Signal
  {
  public:
    OPENVPN_SIMPLE_EXCEPTION(signal_error);

    typedef void (*handler_t)(int signum);

    enum {
      F_SIGINT  = (1<<0),
      F_SIGTERM = (1<<1),
      F_SIGHUP  = (1<<2),
      F_SIGUSR1  = (1<<3),
      F_SIGUSR2  = (1<<4),
      F_SIGPIPE  = (1<<5),
    };

    Signal(const handler_t handler, const unsigned int flags)
    {
      struct sigaction sa;
      sa.sa_handler = handler;
      sigemptyset(&sa.sa_mask);
      sa.sa_flags = SA_RESTART; // restart functions if interrupted by handler
      sigconf(sa, flags_ = flags);
    }

    ~Signal()
    {
      struct sigaction sa;
      sa.sa_handler = SIG_DFL;
      sigemptyset(&sa.sa_mask);
      sa.sa_flags = 0;
      sigconf(sa, flags_);
    }

  private:
    static void sigconf(struct sigaction& sa, const unsigned int flags)
    {
      if (flags & F_SIGINT)
	sigact(sa, SIGINT);
      if (flags & F_SIGTERM)
	sigact(sa, SIGTERM);
      if (flags & F_SIGHUP)
	sigact(sa, SIGHUP);
      if (flags & F_SIGUSR1)
	sigact(sa, SIGUSR1);
      if (flags & F_SIGUSR2)
	sigact(sa, SIGUSR2);
      if (flags & F_SIGPIPE)
	sigact(sa, SIGPIPE);
    }

    static void sigact(struct sigaction& sa, const int sig)
    {
      if (sigaction(sig, &sa, nullptr) == -1)
	throw signal_error();
    }

    unsigned int flags_;
  };

  // Like Asio posix_signal_blocker, but only block certain signals
  class SignalBlocker
  {
    SignalBlocker(const SignalBlocker&) = delete;
    SignalBlocker& operator=(const SignalBlocker&) = delete;

  public:
    SignalBlocker(const unsigned int flags) // use signal mask from class Signal
      : blocked_(false)
    {
      sigset_t new_mask;
      sigemptyset(&new_mask);
      if (flags & Signal::F_SIGINT)
	sigaddset(&new_mask, SIGINT);
      if (flags & Signal::F_SIGTERM)
	sigaddset(&new_mask, SIGTERM);
      if (flags & Signal::F_SIGHUP)
	sigaddset(&new_mask, SIGHUP);
      if (flags & Signal::F_SIGUSR1)
	sigaddset(&new_mask, SIGUSR1);
      if (flags & Signal::F_SIGUSR2)
	sigaddset(&new_mask, SIGUSR2);
      if (flags & Signal::F_SIGPIPE)
	sigaddset(&new_mask, SIGPIPE);
      blocked_ = (pthread_sigmask(SIG_BLOCK, &new_mask, &old_mask_) == 0);
    }

    // Destructor restores the previous signal mask.
    ~SignalBlocker()
    {
      if (blocked_)
	pthread_sigmask(SIG_SETMASK, &old_mask_, 0);
    }

  private:
    // Have signals been blocked.
    bool blocked_;

    // The previous signal mask.
    sigset_t old_mask_;
  };

  // Like SignalBlocker, but block specific signals in default constructor
  struct SignalBlockerDefault : public SignalBlocker
  {
    SignalBlockerDefault()
      : SignalBlocker( // these signals should be handled by parent thread
		      Signal::F_SIGINT|
		      Signal::F_SIGTERM|
		      Signal::F_SIGHUP|
		      Signal::F_SIGUSR1|
		      Signal::F_SIGUSR2|
		      Signal::F_SIGPIPE)
    {
    }
  };

  struct SignalBlockerPipe : public SignalBlocker
  {
    SignalBlockerPipe()
      : SignalBlocker(Signal::F_SIGPIPE)
    {
    }
  };

}
#endif
#endif
