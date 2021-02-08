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

#include <openvpn/common/rc.hpp>
#include <openvpn/time/asiotimer.hpp>

// AsioTimerSafe is like AsioTimer but with strict cancellation
// semantics that guarantees that a handler will never be called
// with a non-error status after the timer is cancelled.

namespace openvpn {
  class AsioTimerSafe
  {
  public:
    AsioTimerSafe(openvpn_io::io_context& io_context)
      : timer_(io_context),
	epoch_(new Epoch)
    {
    }

    std::size_t expires_at(const Time& t)
    {
      inc_epoch();
      return timer_.expires_at(t);
    }

    std::size_t expires_after(const Time::Duration& d)
    {
      inc_epoch();
      return timer_.expires_after(d);
    }

    std::size_t cancel()
    {
      inc_epoch();
      return timer_.cancel();
    }

    template <typename F>
    void async_wait(F&& func)
    {
      inc_epoch();
      timer_.async_wait([func=std::move(func), epoch=epoch(), eptr=epoch_](const openvpn_io::error_code& error)
			 {
			   func(epoch == eptr->epoch ? error : openvpn_io::error::operation_aborted);
			 });
    }

  private:
    typedef std::size_t epoch_t;

    struct Epoch : public RC<thread_unsafe_refcount>
    {
      typedef RCPtr<Epoch> Ptr;
      epoch_t epoch = 0;
    };

    epoch_t epoch() const
    {
      return epoch_->epoch;
    }

    void inc_epoch()
    {
      ++epoch_->epoch;
    }

    AsioTimer timer_;
    Epoch::Ptr epoch_;
  };
}
