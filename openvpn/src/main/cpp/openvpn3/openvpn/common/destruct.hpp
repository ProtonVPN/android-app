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

#ifndef OPENVPN_COMMON_DESTRUCT_H
#define OPENVPN_COMMON_DESTRUCT_H

#include <openvpn/common/rc.hpp>

// used for general-purpose cleanup

namespace openvpn {

struct DestructorBase : public RC<thread_unsafe_refcount>
{
    typedef RCPtr<DestructorBase> Ptr;
    virtual void destroy(std::ostream &os) = 0;
    virtual ~DestructorBase() = default;
};

} // namespace openvpn

#endif
