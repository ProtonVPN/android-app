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

#pragma once

#include <openvpn/time/asiotimer.hpp>

// AsioTimerSafe is like AsioTimer but with an epoch counter
// that allows a handler to determine if it is the most recent
// handler to be queued.

namespace openvpn {
  class AsioTimerSafe
  {
  public:
    typedef std::size_t epoch_t;

    AsioTimerSafe(openvpn_io::io_context& io_context)
      : timer_(io_context)
    {
    }

    std::size_t expires_at(const Time& t)
    {
      ++epoch_;
      return timer_.expires_at(t);
    }

    std::size_t expires_after(const Time::Duration& d)
    {
      ++epoch_;
      return timer_.expires_after(d);
    }

    std::size_t cancel()
    {
      ++epoch_;
      return timer_.cancel();
    }

    epoch_t epoch() const
    {
      return epoch_;
    }

    template <typename F>
    void async_wait(F&& func)
    {
      ++epoch_;
      timer_.async_wait([func=std::move(func), epoch=epoch_](const openvpn_io::error_code& error) mutable
			 {
			   func(error, epoch);
			 });
    }

  private:
    AsioTimer timer_;
    epoch_t epoch_ = 0;
  };
}
