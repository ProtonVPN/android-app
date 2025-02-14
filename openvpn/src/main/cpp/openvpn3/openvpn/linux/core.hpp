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

// Linux method for binding a thread to a particular core.

#ifndef OPENVPN_LINUX_CORE_H
#define OPENVPN_LINUX_CORE_H

#include <pthread.h>

#include <openvpn/common/core.hpp>

namespace openvpn {

inline int bind_to_core(const int core_id)
{
    const int num_cores = n_cores();
    if (core_id >= num_cores)
        return EINVAL;

    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    CPU_SET(core_id, &cpuset);

    pthread_t current_thread = pthread_self();
    return pthread_setaffinity_np(current_thread, sizeof(cpu_set_t), &cpuset);
}

inline int exclude_from_core(const int core_id)
{
    const int num_cores = n_cores();
    if (num_cores <= 1 || core_id >= num_cores)
        return EINVAL;

    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    for (int i = 0; i < num_cores; ++i)
        if (i != core_id)
            CPU_SET(i, &cpuset);

    pthread_t current_thread = pthread_self();
    return pthread_setaffinity_np(current_thread, sizeof(cpu_set_t), &cpuset);
}
} // namespace openvpn

#endif
