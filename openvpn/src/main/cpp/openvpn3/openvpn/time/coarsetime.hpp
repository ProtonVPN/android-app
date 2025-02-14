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

#ifndef OPENVPN_TIME_COARSETIME_H
#define OPENVPN_TIME_COARSETIME_H

#include <openvpn/time/time.hpp>

namespace openvpn {

// Used to compare two time objects within the accuracy limits
// defined by pre and post.

class CoarseTime
{
  public:
    CoarseTime()
    {
    }

    CoarseTime(const Time::Duration &pre, const Time::Duration &post)
        : pre_(pre), post_(post)
    {
    }

    void init(const Time::Duration &pre, const Time::Duration &post)
    {
        pre_ = pre;
        post_ = post;
    }

    void reset(const Time &t)
    {
        time_ = t;
    }
    void reset()
    {
        time_.reset();
    }

    bool similar(const Time &t) const
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
