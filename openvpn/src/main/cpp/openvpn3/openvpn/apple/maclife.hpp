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

#ifndef OPENVPN_APPLE_MACLIFE_H
#define OPENVPN_APPLE_MACLIFE_H

#include <string>
#include <sstream>

#include <thread>

#include <openvpn/log/logthread.hpp>
#include <openvpn/apple/cf/cftimer.hpp>
#include <openvpn/apple/cf/cfhelper.hpp>
#include <openvpn/apple/cf/cfrunloop.hpp>
#include <openvpn/apple/reachable.hpp>
#include <openvpn/client/clilife.hpp>
#include <openvpn/apple/macsleep.hpp>
#include <openvpn/apple/scdynstore.hpp>

namespace openvpn {
class MacLifeCycle : public ClientLifeCycle, MacSleep, ReachabilityTracker
{
  public:
    OPENVPN_EXCEPTION(mac_lifecycle_error);

    MacLifeCycle()
        : ReachabilityTracker(true, false),
          nc(nullptr),
          thread(nullptr),
          paused(false)
    {
    }

    virtual ~MacLifeCycle()
    {
        stop_thread();
    }

    virtual bool network_available()
    {
        return net_up();
    }

    virtual void start(NotifyCallback *nc_arg)
    {
        if (!thread && nc_arg)
        {
            nc = nc_arg;
            thread = new std::thread(&MacLifeCycle::thread_func, this);
        }
    }

    virtual void stop()
    {
        stop_thread();
    }

  private:
    struct State
    {
        State()
            : net_up(false),
              sleep(false)
        {
        }

        State(bool net_up_arg, const std::string &iface_arg, bool sleep_arg)
            : net_up(net_up_arg),
              iface(iface_arg),
              sleep(sleep_arg)
        {
        }

        bool operator==(const State &other) const
        {
            return net_up == other.net_up && iface == other.iface && sleep == other.sleep;
        }

        bool operator!=(const State &other) const
        {
            return !operator==(other);
        }

        std::string to_string() const
        {
            std::ostringstream os;
            os << "[net_up=" << net_up << " iface=" << iface << " sleep=" << sleep << ']';
            return os.str();
        }

        bool net_up;
        std::string iface;
        bool sleep;
    };

    void stop_thread()
    {
        if (thread)
        {
            if (runloop.defined())
                CFRunLoopStop(runloop());
            thread->join();
            delete thread;
            thread = nullptr;
        }
    }

    void thread_func()
    {
        runloop.reset(CFRunLoopGetCurrent(), CF::GET);
        Log::Context logctx(logwrap);
        try
        {
            // set up dynamic store query object
            dstore.reset(SCDynamicStoreCreate(kCFAllocatorDefault,
                                              CFSTR("OpenVPN_MacLifeCycle"),
                                              nullptr,
                                              nullptr));

            // init state
            state = State(net_up(), primary_interface(), false);
            prev_state = state;
            paused = false;

            // enable sleep/wakeup notifications
            mac_sleep_start();

            // enable network reachability notifications
            reachability_tracker_schedule();

            // enable interface change notifications
            iface_watch();

            // process event loop until CFRunLoopStop is called from parent thread
            CFRunLoopRun();
        }
        catch (const std::exception &e)
        {
            OPENVPN_LOG("MacLifeCycle exception: " << e.what());
        }

        // cleanup
        cancel_action_timer();
        mac_sleep_stop();
        reachability_tracker_cancel();
        dstore.reset();
    }

    std::string primary_interface()
    {
        CF::Dict dict(CF::DynamicStoreCopyDict(dstore, "State:/Network/Global/IPv4"));
        return CF::dict_get_str(dict, "PrimaryInterface");
    }

    bool net_up()
    {
        ReachabilityViaInternet r;
        return ReachabilityViaInternet::status_from_flags(r.flags()) != ReachabilityInterface::NotReachable;
    }

    void iface_watch()
    {
        SCDynamicStoreContext context = {0, this, nullptr, nullptr, nullptr};
        CF::DynamicStore ds(SCDynamicStoreCreate(kCFAllocatorDefault,
                                                 CFSTR("OpenVPN_MacLifeCycle_iface_watch"),
                                                 iface_watch_callback_static,
                                                 &context));
        if (!ds.defined())
            throw mac_lifecycle_error("SCDynamicStoreCreate");
        CF::MutableArray watched_keys(CF::mutable_array());
        CF::array_append_str(watched_keys, "State:/Network/Global/IPv4");
        // CF::array_append_str(watched_keys, "State:/Network/Global/IPv6");
        if (!watched_keys.defined())
            throw mac_lifecycle_error("watched_keys is undefined");
        if (!SCDynamicStoreSetNotificationKeys(ds(),
                                               watched_keys(),
                                               nullptr))
            throw mac_lifecycle_error("SCDynamicStoreSetNotificationKeys failed");
        CF::RunLoopSource rls(SCDynamicStoreCreateRunLoopSource(kCFAllocatorDefault, ds(), 0));
        if (!rls.defined())
            throw mac_lifecycle_error("SCDynamicStoreCreateRunLoopSource failed");
        CFRunLoopAddSource(CFRunLoopGetCurrent(), rls(), kCFRunLoopDefaultMode);
    }

    static void iface_watch_callback_static(SCDynamicStoreRef store, CFArrayRef changedKeys, void *arg)
    {
        MacLifeCycle *self = (MacLifeCycle *)arg;
        self->iface_watch_callback(store, changedKeys);
    }

    void iface_watch_callback(SCDynamicStoreRef store, CFArrayRef changedKeys)
    {
        state.iface = primary_interface();
        OPENVPN_LOG("MacLifeCycle NET_IFACE " << state.iface);
        schedule_action_timer(1);
    }

    virtual void notify_sleep()
    {
        OPENVPN_LOG("MacLifeCycle SLEEP");
        state.sleep = true;
        schedule_action_timer(0);
    }

    virtual void notify_wakeup()
    {
        OPENVPN_LOG("MacLifeCycle WAKEUP");
        state.sleep = false;
        schedule_action_timer(1);
    }

    virtual void reachability_tracker_event(const ReachabilityBase &rb, SCNetworkReachabilityFlags flags)
    {
        if (rb.vtype() == ReachabilityBase::Internet)
        {
            const ReachabilityBase::Status status = rb.vstatus(flags);
            state.net_up = (status != ReachabilityInterface::NotReachable);
            OPENVPN_LOG("MacLifeCycle NET_STATE " << state.net_up << " status=" << ReachabilityBase::render_status(status) << " flags=" << ReachabilityBase::render_flags(flags));
            schedule_action_timer(1);
        }
    }

    void schedule_action_timer(const int seconds)
    {
        cancel_action_timer();
        if (seconds)
        {
            CFRunLoopTimerContext context = {0, this, nullptr, nullptr, nullptr};
            action_timer.reset(CFRunLoopTimerCreate(kCFAllocatorDefault, CFAbsoluteTimeGetCurrent() + seconds, 0, 0, 0, action_timer_callback_static, &context));
            if (action_timer.defined())
                CFRunLoopAddTimer(CFRunLoopGetCurrent(), action_timer(), kCFRunLoopCommonModes);
            else
                OPENVPN_LOG("MacLifeCycle::schedule_action_timer: failed to create timer");
        }
        else
            action_timer_callback(nullptr);
    }

    void cancel_action_timer()
    {
        if (action_timer.defined())
        {
            CFRunLoopTimerInvalidate(action_timer());
            action_timer.reset(nullptr);
        }
    }

    static void action_timer_callback_static(CFRunLoopTimerRef timer, void *info)
    {
        MacLifeCycle *self = (MacLifeCycle *)info;
        self->action_timer_callback(timer);
    }

    void action_timer_callback(CFRunLoopTimerRef timer)
    {
        try
        {
            if (state != prev_state)
            {
                OPENVPN_LOG("MacLifeCycle ACTION pause=" << paused << " state=" << state.to_string() << " prev=" << prev_state.to_string());
                if (paused)
                {
                    if (!state.sleep && state.net_up)
                    {
                        nc->cln_resume();
                        paused = false;
                    }
                }
                else
                {
                    if (state.sleep)
                    {
                        nc->cln_pause("sleep");
                        paused = true;
                    }
                    else if (!state.net_up)
                    {
                        nc->cln_pause("network-unavailable");
                        paused = true;
                    }
                    else
                    {
                        if (state.iface != prev_state.iface)
                            nc->cln_reconnect(0);
                    }
                }
                prev_state = state;
            }
        }
        catch (const std::exception &e)
        {
            OPENVPN_LOG("MacLifeCycle::action_timer_callback exception: " << e.what());
        }
    }

    NotifyCallback *nc;
    std::thread *thread;
    CF::RunLoop runloop; // run loop in thread
    CF::DynamicStore dstore;
    State state;
    State prev_state;
    bool paused;
    CF::Timer action_timer;
    Log::Context::Wrapper logwrap; // used to carry forward the log context from parent thread
};
} // namespace openvpn

#endif
