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

#ifndef OPENVPN_TIME_COARSETIME_H
#define OPENVPN_TIME_COARSETIME_H

#include <openvpn/time/time.hpp>

namespace openvpn {

  // Used to compare two time objects within the accuracy limits
  // defined by pre and post.

  class CoarseTime
  {
  public:
    CoarseTime() {}

    CoarseTime(const Time::Duration& pre, const Time::Duration& post)
      : pre_(pre), post_(post) {}

    void init(const Time::Duration& pre, const Time::Duration& post)
    {
      pre_ = pre;
      post_ = post;
    }

    void reset(const Time& t) { time_ = t; }
    void reset() { time_.reset(); }

    bool similar(const Time& t) const
    {
      if (time_.defined())
	{
	  if (t >= time_)
	    return (t - time_) <= post_;
	  else
	    return (time_ - t) <= pre_;
	}
      else
	return false;
    }

  private:
    Time time_;
    Time::Duration pre_;
    Time::Duration post_;
  };

} // namespace openvpn

#endif // OPENVPN_TIME_COARSETIME_H
