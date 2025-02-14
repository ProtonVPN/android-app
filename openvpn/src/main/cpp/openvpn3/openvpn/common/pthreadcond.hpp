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

#ifndef OPENVPN_COMMON_PTHREADCOND_H
#define OPENVPN_COMMON_PTHREADCOND_H

#include <mutex>
#include <condition_variable>
#include <chrono>

#include <openvpn/common/stop.hpp>

namespace openvpn {

// Barrier class that is useful in cases where all threads
// need to reach a known point before executing some action.
// Note that this barrier implementation is
// constructed using C++11 condition variables.
class PThreadBarrier
{
    enum State
    {
        UNSIGNALED = 0, // initial state
        SIGNALED,       // signal() was called
        ERROR_THROWN,   // error() was called
    };

  public:
    // status return from wait()
    enum Status
    {
        SUCCESS = 0,  // successful
        CHOSEN_ONE,   // successful and chosen (only one thread is chosen)
        TIMEOUT,      // timeout
        ERROR_SIGNAL, // at least one thread called error()
    };

    PThreadBarrier(const int initial_limit = -1)
        : stop(nullptr),
          limit(initial_limit)
    {
    }

    PThreadBarrier(Stop *stop_arg, const int initial_limit = -1)
        : stop(stop_arg),
          limit(initial_limit)
    {
    }

    // All callers will increment count and block until
    // count == limit.  CHOSEN_ONE will be returned to
    // the first caller to reach limit.  This caller can
    // then release all the other callers by calling
    // signal().
    int wait(const unsigned int seconds)
    {
        // allow asynchronous stop
        Stop::Scope stop_scope(stop, [this]()
                               { error(); });

        bool timeout = false;
        int ret;
        std::unique_lock<std::mutex> lock(mutex);
        const unsigned int c = ++count;
        while (state == UNSIGNALED
               && (limit < 0 || c < static_cast<unsigned int>(limit))
               && !timeout)
            timeout = (cv.wait_for(lock, std::chrono::seconds(seconds)) == std::cv_status::timeout);
        if (timeout)
            ret = TIMEOUT;
        else if (state == ERROR_THROWN)
            ret = ERROR_SIGNAL;
        else if (state == UNSIGNALED && !chosen)
        {
            ret = CHOSEN_ONE;
            chosen = true;
        }
        else
            ret = SUCCESS;
        return ret;
    }

    void set_limit(const int new_limit)
    {
        std::unique_lock<std::mutex> lock(mutex);
        limit = new_limit;
        cv.notify_all();
    }

    // Generally, only the CHOSEN_ONE calls signal() after its work
    // is complete, to allow the other threads to pass the barrier.
    void signal()
    {
        signal_(SIGNALED);
    }

    // Causes all threads waiting on wait() (and those which call wait()
    // in the future) to exit with ERROR_SIGNAL status.
    void error()
    {
        signal_(ERROR_THROWN);
    }

  private:
    void signal_(const State newstate)
    {
        std::unique_lock<std::mutex> lock(mutex);
        if (state == UNSIGNALED)
        {
            state = newstate;
            cv.notify_all();
        }
    }

    std::mutex mutex;
    std::condition_variable cv;
    Stop *stop;
    State state{UNSIGNALED};
    bool chosen = false;
    int count = 0;
    int limit;
};

} // namespace openvpn

#endif
