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

// DNS utilities for Mac

#ifndef OPENVPN_TUN_MAC_MACDNS_WATCHDOG_H
#define OPENVPN_TUN_MAC_MACDNS_WATCHDOG_H

#include <thread>
#include <mutex>

#include <openvpn/log/logthread.hpp>
#include <openvpn/common/action.hpp>
#include <openvpn/apple/cf/cftimer.hpp>
#include <openvpn/apple/cf/cfrunloop.hpp>
#include <openvpn/tun/mac/macdns.hpp>

namespace openvpn {
OPENVPN_EXCEPTION(macdns_watchdog_error);

class MacDNSWatchdog : public RC<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<MacDNSWatchdog> Ptr;

    // flags
    enum
    {
        ENABLE_WATCHDOG = (1 << 0),
        SYNCHRONOUS = (1 << 1),
        FLUSH_RECONFIG = (1 << 2),
    };

    class DNSAction : public Action
    {
      public:
        typedef RCPtr<DNSAction> Ptr;

        DNSAction(const MacDNSWatchdog::Ptr &parent_arg,
                  const MacDNS::Config::Ptr &config_arg,
                  const unsigned int flags_arg)
            : parent(parent_arg),
              config(config_arg),
              flags(flags_arg)
        {
        }

        virtual void execute(std::ostream &os)
        {
            os << to_string() << std::endl;
            if (parent)
                parent->setdns(config, flags);
        }

        virtual std::string to_string() const
        {
            std::ostringstream os;
            os << "MacDNSAction: FLAGS=";
            if (flags & ENABLE_WATCHDOG)
                os << 'E';
            if (flags & SYNCHRONOUS)
                os << 'S';
            if (flags & FLUSH_RECONFIG)
                os << 'F';
            if (config)
                os << ' ' << config->to_string();
            return os.str();
        }

      private:
        const MacDNSWatchdog::Ptr parent;
        const MacDNS::Config::Ptr config;
        const unsigned int flags;
    };

    MacDNSWatchdog()
        : macdns(new MacDNS("OpenVPNConnect")),
          thread(nullptr)
    {
    }

    virtual ~MacDNSWatchdog()
    {
        stop_thread();
    }

    static void add_actions(const MacDNS::Config::Ptr &dns,
                            const unsigned int flags,
                            ActionList &create,
                            ActionList &destroy)
    {
        MacDNSWatchdog::Ptr watchdog(new MacDNSWatchdog);
        MacDNS::Config::Ptr dns_remove;
        DNSAction::Ptr create_action(new DNSAction(watchdog, dns, flags));
        DNSAction::Ptr destroy_action(new DNSAction(watchdog, dns_remove, flags));
        create.add(create_action);
        destroy.add(destroy_action);
    }

  private:
    bool setdns(const MacDNS::Config::Ptr &config, const unsigned int flags)
    {
        bool mod = false;
        if (config)
        {
            if ((flags & SYNCHRONOUS) || !(flags & ENABLE_WATCHDOG))
                stop_thread();
            config_ = config;
            if (flags & ENABLE_WATCHDOG)
            {
                if (!thread)
                {
                    mod = macdns->setdns(*config_);
                    thread = new std::thread(&MacDNSWatchdog::thread_func, this);
                }
                else
                {
                    if (runloop.defined())
                        schedule_push_timer(0);
                    else
                        OPENVPN_LOG("MacDNSWatchdog::setdns: runloop undefined");
                }
            }
            else
            {
                mod = macdns->setdns(*config_);
            }
        }
        else
        {
            stop_thread();
            config_.reset();
            mod = macdns->resetdns();
        }
        if (mod && (flags & FLUSH_RECONFIG))
        {
            macdns->flush_cache();
            macdns->signal_network_reconfiguration();
        }
        return mod;
    }

    std::string to_string() const
    {
        const MacDNS::Config::Ptr config(config_);
        if (config)
            return config->to_string();
        else
            return std::string("UNDEF");
    }

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

    // All methods below this point called in the context of watchdog thread
    // except for schedule_push_timer which may be called from parent thread
    // as well.
    void thread_func()
    {
        runloop.reset(CFRunLoopGetCurrent(), CF::GET);
        Log::Context logctx(logwrap);

        try
        {
            SCDynamicStoreContext context = {0, this, nullptr, nullptr, nullptr};
            CF::DynamicStore ds(SCDynamicStoreCreate(kCFAllocatorDefault,
                                                     CFSTR("OpenVPN_MacDNSWatchdog"),
                                                     callback_static,
                                                     &context));
            if (!ds.defined())
                throw macdns_watchdog_error("SCDynamicStoreCreate");
            const CF::Array watched_keys(macdns->dskey_array());
            if (!watched_keys.defined())
                throw macdns_watchdog_error("watched_keys is undefined");
            if (!SCDynamicStoreSetNotificationKeys(ds(),
                                                   watched_keys(),
                                                   nullptr))
                throw macdns_watchdog_error("SCDynamicStoreSetNotificationKeys failed");
            CF::RunLoopSource rls(SCDynamicStoreCreateRunLoopSource(kCFAllocatorDefault, ds(), 0));
            if (!rls.defined())
                throw macdns_watchdog_error("SCDynamicStoreCreateRunLoopSource failed");
            CFRunLoopAddSource(CFRunLoopGetCurrent(), rls(), kCFRunLoopDefaultMode);

            // process event loop until CFRunLoopStop is called from parent thread
            CFRunLoopRun();
        }
        catch (const std::exception &e)
        {
            OPENVPN_LOG("MacDNSWatchdog::thread_func exception: " << e.what());
        }
        cancel_push_timer();
    }

    static void callback_static(SCDynamicStoreRef store, CFArrayRef changedKeys, void *arg)
    {
        MacDNSWatchdog *self = (MacDNSWatchdog *)arg;
        self->callback(store, changedKeys);
    }

    void callback(SCDynamicStoreRef store, CFArrayRef changedKeys)
    {
        // DNS Watchdog delay from the time that change is detected
        // to when we forcibly revert it (seconds).
        schedule_push_timer(1);
    }

    void schedule_push_timer(const int seconds)
    {
        std::lock_guard<std::mutex> lock(push_timer_lock);
        CFRunLoopTimerContext context = {0, this, nullptr, nullptr, nullptr};
        cancel_push_timer_nolock();
        push_timer.reset(CFRunLoopTimerCreate(kCFAllocatorDefault, CFAbsoluteTimeGetCurrent() + seconds, 0, 0, 0, push_timer_callback_static, &context));
        if (push_timer.defined())
            CFRunLoopAddTimer(runloop(), push_timer(), kCFRunLoopCommonModes);
        else
            OPENVPN_LOG("MacDNSWatchdog::schedule_push_timer: failed to create timer");
    }

    void cancel_push_timer_nolock()
    {
        if (push_timer.defined())
        {
            CFRunLoopTimerInvalidate(push_timer());
            push_timer.reset(nullptr);
        }
    }

    void cancel_push_timer()
    {
        std::lock_guard<std::mutex> lock(push_timer_lock);
        cancel_push_timer_nolock();
    }

    static void push_timer_callback_static(CFRunLoopTimerRef timer, void *info)
    {
        MacDNSWatchdog *self = (MacDNSWatchdog *)info;
        self->push_timer_callback(timer);
    }

    void push_timer_callback(CFRunLoopTimerRef timer)
    {
        try
        {
            // reset DNS settings after watcher detected modifications by third party
            const MacDNS::Config::Ptr config(config_);
            if (macdns->setdns(*config))
                OPENVPN_LOG("MacDNSWatchdog: updated DNS settings");
        }
        catch (const std::exception &e)
        {
            OPENVPN_LOG("MacDNSWatchdog::push_timer_callback exception: " << e.what());
        }
    }


    MacDNS::Config::Ptr config_;
    MacDNS::Ptr macdns;

    std::thread *thread;  // watcher thread
    CF::RunLoop runloop;  // run loop in watcher thread
    CF::Timer push_timer; // watcher thread timer
    std::mutex push_timer_lock;
    Log::Context::Wrapper logwrap; // used to carry forward the log context from parent thread
};
} // namespace openvpn

#endif
