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

#ifndef OPENVPN_TRANSPORT_MUTATE_H
#define OPENVPN_TRANSPORT_MUTATE_H

#include <openvpn/common/rc.hpp>
#include <openvpn/buffer/buffer.hpp>

namespace openvpn {

class TransportMutateStream : public RC<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<TransportMutateStream> Ptr;

    virtual void pre_send(BufferAllocated &buf) = 0;
    virtual void post_recv(BufferAllocated &buf) = 0;
};
} // namespace openvpn

#endif
