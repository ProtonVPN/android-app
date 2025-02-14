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

#ifndef OPENVPN_COMMON_WAITBARRIER_H
#define OPENVPN_COMMON_WAITBARRIER_H

#include <openvpn/common/exception.hpp>
#include <openvpn/common/pthreadcond.hpp>

namespace openvpn {

#ifdef INSTRUMENTATION_SLOWDOWN
static constexpr unsigned int WAIT_BARRIER_TIMEOUT = 300;
#else
static constexpr unsigned int WAIT_BARRIER_TIMEOUT = 30;
#endif

template <typename THREAD_COMMON>
inline void event_loop_wait_barrier(THREAD_COMMON &tc,
                                    const unsigned int seconds = WAIT_BARRIER_TIMEOUT)
{
    // barrier prior to event-loop entry
    switch (tc.event_loop_bar.wait(seconds))
    {
    case PThreadBarrier::SUCCESS:
        break;
    case PThreadBarrier::CHOSEN_ONE:
        tc.user_group.activate();
        tc.show_unused_options();
        tc.event_loop_bar.signal();
        break;
    case PThreadBarrier::TIMEOUT:
        throw Exception("event loop barrier timeout");
    case PThreadBarrier::ERROR_SIGNAL:
        throw Exception("event loop barrier error");
    }
}
} // namespace openvpn

#endif
