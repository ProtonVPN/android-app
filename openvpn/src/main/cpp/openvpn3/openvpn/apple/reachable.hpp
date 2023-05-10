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
//
//  This code is derived from the Apple sample Reachability.m under
//  the following license.
//
// Disclaimer: IMPORTANT:  This Apple software is supplied to you by Apple
// Inc. ("Apple") in consideration of your agreement to the following
// terms, and your use, installation, modification or redistribution of
// this Apple software constitutes acceptance of these terms.  If you do
// not agree with these terms, please do not use, install, modify or
// redistribute this Apple software.
//
// In consideration of your agreement to abide by the following terms, and
// subject to these terms, Apple grants you a personal, non-exclusive
// license, under Apple's copyrights in this original Apple software (the
// "Apple Software"), to use, reproduce, modify and redistribute the Apple
// Software, with or without modifications, in source and/or binary forms;
// provided that if you redistribute the Apple Software in its entirety and
// without modifications, you must retain this notice and the following
// text and disclaimers in all such redistributions of the Apple Software.
// Neither the name, trademarks, service marks or logos of Apple Inc. may
// be used to endorse or promote products derived from the Apple Software
// without specific prior written permission from Apple.  Except as
// expressly stated in this notice, no other rights or licenses, express or
// implied, are granted by Apple herein, including but not limited to any
// patent rights that may be infringed by your derivative works or by other
// works in which the Apple Software may be incorporated.
//
// The Apple Software is provided by Apple on an "AS IS" basis.  APPLE
// MAKES NO WARRANTIES, EXPRESS OR IMPLIED, INCLUDING WITHOUT LIMITATION
// THE IMPLIED WARRANTIES OF NON-INFRINGEMENT, MERCHANTABILITY AND FITNESS
// FOR A PARTICULAR PURPOSE, REGARDING THE APPLE SOFTWARE OR ITS USE AND
// OPERATION ALONE OR IN COMBINATION WITH YOUR PRODUCTS.
//
// IN NO EVENT SHALL APPLE BE LIABLE FOR ANY SPECIAL, INDIRECT, INCIDENTAL
// OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) ARISING IN ANY WAY OUT OF THE USE, REPRODUCTION,
// MODIFICATION AND/OR DISTRIBUTION OF THE APPLE SOFTWARE, HOWEVER CAUSED
// AND WHETHER UNDER THEORY OF CONTRACT, TORT (INCLUDING NEGLIGENCE),
// STRICT LIABILITY OR OTHERWISE, EVEN IF APPLE HAS BEEN ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.
//
// Copyright (C) 2013 Apple Inc. All Rights Reserved.

// Wrapper for Apple SCNetworkReachability methods.

#ifndef OPENVPN_APPLECRYPTO_UTIL_REACHABLE_H
#define OPENVPN_APPLECRYPTO_UTIL_REACHABLE_H

#import "TargetConditionals.h"

#include <netinet/in.h>
#include <SystemConfiguration/SCNetworkReachability.h>

#include <string>
#include <sstream>
#include <memory>

#include <openvpn/common/socktypes.hpp>
#include <openvpn/apple/cf/cf.hpp>
#include <openvpn/apple/reach.hpp>

namespace openvpn {
namespace CF {
OPENVPN_CF_WRAP(NetworkReachability, network_reachability_cast, SCNetworkReachabilityRef, SCNetworkReachabilityGetTypeID);
}

class ReachabilityBase
{
  public:
    typedef ReachabilityInterface::Status Status;

    enum Type
    {
        Internet,
        WiFi,
    };

    std::string to_string() const
    {
        return to_string(flags());
    }

    std::string to_string(const SCNetworkReachabilityFlags f) const
    {
        const Status s = vstatus(f);
        const Type t = vtype();

        std::string ret;
        ret += render_type(t);
        ret += ':';
        ret += render_status(s);
        ret += '/';
        ret += render_flags(f);
        return ret;
    }

    Status status() const
    {
        return vstatus(flags());
    }

    SCNetworkReachabilityFlags flags() const
    {
        SCNetworkReachabilityFlags f = 0;
        if (SCNetworkReachabilityGetFlags(reach(), &f) == TRUE)
            return f;
        else
            return 0;
    }

    static std::string render_type(Type type)
    {
        switch (type)
        {
        case Internet:
            return "Internet";
        case WiFi:
            return "WiFi";
        default:
            return "Type???";
        }
    }

    static std::string render_status(const Status status)
    {
        switch (status)
        {
        case ReachabilityInterface::NotReachable:
            return "NotReachable";
        case ReachabilityInterface::ReachableViaWiFi:
            return "ReachableViaWiFi";
        case ReachabilityInterface::ReachableViaWWAN:
            return "ReachableViaWWAN";
        default:
            return "ReachableVia???";
        }
    }

    static std::string render_flags(const SCNetworkReachabilityFlags flags)
    {
        std::string ret;
#if TARGET_OS_IPHONE || TARGET_IPHONE_SIMULATOR // Mac OS X doesn't define WWAN flags
        if (flags & kSCNetworkReachabilityFlagsIsWWAN)
            ret += 'W';
        else
#endif
            ret += '-';
        if (flags & kSCNetworkReachabilityFlagsReachable)
            ret += 'R';
        else
            ret += '-';
        ret += ' ';
        if (flags & kSCNetworkReachabilityFlagsTransientConnection)
            ret += 't';
        else
            ret += '-';
        if (flags & kSCNetworkReachabilityFlagsConnectionRequired)
            ret += 'c';
        else
            ret += '-';
        if (flags & kSCNetworkReachabilityFlagsConnectionOnTraffic)
            ret += 'C';
        else
            ret += '-';
        if (flags & kSCNetworkReachabilityFlagsInterventionRequired)
            ret += 'i';
        else
            ret += '-';
        if (flags & kSCNetworkReachabilityFlagsConnectionOnDemand)
            ret += 'D';
        else
            ret += '-';
        if (flags & kSCNetworkReachabilityFlagsIsLocalAddress)
            ret += 'l';
        else
            ret += '-';
        if (flags & kSCNetworkReachabilityFlagsIsDirect)
            ret += 'd';
        else
            ret += '-';
        return ret;
    }

    virtual Type vtype() const = 0;
    virtual Status vstatus(const SCNetworkReachabilityFlags flags) const = 0;

    virtual ~ReachabilityBase()
    {
    }

    CF::NetworkReachability reach;
};

class ReachabilityViaInternet : public ReachabilityBase
{
  public:
    ReachabilityViaInternet()
    {
        struct sockaddr_in addr;
        bzero(&addr, sizeof(addr));
        addr.sin_len = sizeof(addr);
        addr.sin_family = AF_INET;
        reach.reset(SCNetworkReachabilityCreateWithAddress(kCFAllocatorDefault, (struct sockaddr *)&addr));
    }

    virtual Type vtype() const
    {
        return Internet;
    }

    virtual Status vstatus(const SCNetworkReachabilityFlags flags) const
    {
        return status_from_flags(flags);
    }

    static Status status_from_flags(const SCNetworkReachabilityFlags flags)
    {
        if ((flags & kSCNetworkReachabilityFlagsReachable) == 0)
        {
            // The target host is not reachable.
            return ReachabilityInterface::NotReachable;
        }

        Status ret = ReachabilityInterface::NotReachable;

        if ((flags & kSCNetworkReachabilityFlagsConnectionRequired) == 0)
        {
            // If the target host is reachable and no connection is required then
            // we'll assume (for now) that you're on Wi-Fi...
            ret = ReachabilityInterface::ReachableViaWiFi;
        }

#if 0 // don't contaminate result by considering on-demand viability
      if ((((flags & kSCNetworkReachabilityFlagsConnectionOnDemand ) != 0) ||
	   (flags & kSCNetworkReachabilityFlagsConnectionOnTraffic) != 0))
	{
	  // ... and the connection is on-demand (or on-traffic) if the
	  //     calling application is using the CFSocketStream or higher APIs...

	  if ((flags & kSCNetworkReachabilityFlagsInterventionRequired) == 0)
	    {
	      // ... and no [user] intervention is needed...
	      ret = ReachabilityInterface::ReachableViaWiFi;
	    }
	}
#endif

#if TARGET_OS_IPHONE || TARGET_IPHONE_SIMULATOR // Mac OS X doesn't define WWAN flags
        if ((flags & kSCNetworkReachabilityFlagsIsWWAN) == kSCNetworkReachabilityFlagsIsWWAN)
        {
            // ... but WWAN connections are OK if the calling application
            // is using the CFNetwork APIs.
            ret = ReachabilityInterface::ReachableViaWWAN;
        }
#endif

        return ret;
    }
};

class ReachabilityViaWiFi : public ReachabilityBase
{
  public:
    ReachabilityViaWiFi()
    {
        struct sockaddr_in addr;
        bzero(&addr, sizeof(addr));
        addr.sin_len = sizeof(addr);
        addr.sin_family = AF_INET;
        addr.sin_addr.s_addr = htonl(IN_LINKLOCALNETNUM); // 169.254.0.0.
        reach.reset(SCNetworkReachabilityCreateWithAddress(kCFAllocatorDefault, (struct sockaddr *)&addr));
    }

    virtual Type vtype() const
    {
        return WiFi;
    }

    virtual Status vstatus(const SCNetworkReachabilityFlags flags) const
    {
        return status_from_flags(flags);
    }

    static Status status_from_flags(const SCNetworkReachabilityFlags flags)
    {
        Status ret = ReachabilityInterface::NotReachable;
        if ((flags & kSCNetworkReachabilityFlagsReachable) && (flags & kSCNetworkReachabilityFlagsIsDirect))
            ret = ReachabilityInterface::ReachableViaWiFi;
        return ret;
    }
};

class Reachability : public ReachabilityInterface
{
  public:
    Reachability(const bool enable_internet, const bool enable_wifi)
    {
        if (enable_internet)
            internet.reset(new ReachabilityViaInternet);
        if (enable_wifi)
            wifi.reset(new ReachabilityViaWiFi);
    }

    bool reachableViaWiFi() const
    {
        if (internet)
        {
            if (wifi)
                return internet->status() == ReachableViaWiFi && wifi->status() == ReachableViaWiFi;
            else
                return internet->status() == ReachableViaWiFi;
        }
        else
        {
            if (wifi)
                return wifi->status() == ReachableViaWiFi;
            else
                return false;
        }
    }

    bool reachableViaCellular() const
    {
        if (internet)
            return internet->status() == ReachableViaWWAN;
        else
            return false;
    }

    virtual Status reachable() const
    {
        if (reachableViaWiFi())
            return ReachableViaWiFi;
        else if (reachableViaCellular())
            return ReachableViaWWAN;
        else
            return NotReachable;
    }

    virtual bool reachableVia(const std::string &net_type) const
    {
        if (net_type == "cellular")
            return reachableViaCellular();
        else if (net_type == "wifi")
            return reachableViaWiFi();
        else
            return reachableViaWiFi() || reachableViaCellular();
    }

    virtual std::string to_string() const
    {
        std::string ret;
        if (internet)
            ret += internet->to_string();
        if (internet && wifi)
            ret += ' ';
        if (wifi)
            ret += wifi->to_string();
        return ret;
    }

    std::unique_ptr<ReachabilityViaInternet> internet;
    std::unique_ptr<ReachabilityViaWiFi> wifi;
};

class ReachabilityTracker
{
  public:
    ReachabilityTracker(const bool enable_internet, const bool enable_wifi)
        : reachability(enable_internet, enable_wifi),
          scheduled(false)
    {
    }

    void reachability_tracker_schedule()
    {
        if (!scheduled)
        {
            if (reachability.internet)
                schedule(*reachability.internet, internet_callback_static);
            if (reachability.wifi)
                schedule(*reachability.wifi, wifi_callback_static);
            scheduled = true;
        }
    }

    void reachability_tracker_cancel()
    {
        if (scheduled)
        {
            if (reachability.internet)
                cancel(*reachability.internet);
            if (reachability.wifi)
                cancel(*reachability.wifi);
            scheduled = false;
        }
    }

    virtual void reachability_tracker_event(const ReachabilityBase &rb, SCNetworkReachabilityFlags flags) = 0;

    virtual ~ReachabilityTracker()
    {
        reachability_tracker_cancel();
    }

  private:
    bool schedule(ReachabilityBase &rb, SCNetworkReachabilityCallBack cb)
    {
        SCNetworkReachabilityContext context = {0, this, nullptr, nullptr, nullptr};
        if (rb.reach.defined())
        {
            if (SCNetworkReachabilitySetCallback(rb.reach(),
                                                 cb,
                                                 &context)
                == FALSE)
                return false;
            if (SCNetworkReachabilityScheduleWithRunLoop(rb.reach(),
                                                         CFRunLoopGetCurrent(),
                                                         kCFRunLoopCommonModes)
                == FALSE)
                return false;
            return true;
        }
        else
            return false;
    }

    void cancel(ReachabilityBase &rb)
    {
        if (rb.reach.defined())
            SCNetworkReachabilityUnscheduleFromRunLoop(rb.reach(), CFRunLoopGetCurrent(), kCFRunLoopCommonModes);
    }

    static void internet_callback_static(SCNetworkReachabilityRef target,
                                         SCNetworkReachabilityFlags flags,
                                         void *info)
    {
        ReachabilityTracker *self = (ReachabilityTracker *)info;
        self->reachability_tracker_event(*self->reachability.internet, flags);
    }

    static void wifi_callback_static(SCNetworkReachabilityRef target,
                                     SCNetworkReachabilityFlags flags,
                                     void *info)
    {
        ReachabilityTracker *self = (ReachabilityTracker *)info;
        self->reachability_tracker_event(*self->reachability.wifi, flags);
    }

    Reachability reachability;
    bool scheduled;
};
} // namespace openvpn

#endif
