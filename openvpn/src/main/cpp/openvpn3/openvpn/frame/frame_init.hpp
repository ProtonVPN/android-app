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

// Method to generate a Frame object for typical OpenVPN usage

#ifndef OPENVPN_FRAME_FRAME_INIT_H
#define OPENVPN_FRAME_FRAME_INIT_H

#include <algorithm>

#include <openvpn/frame/frame.hpp>

namespace openvpn {

inline Frame::Ptr frame_init(const bool align_adjust_3_1,
                             const size_t tun_mtu_max,
                             const size_t control_channel_payload,
                             const bool verbose)
{
    const size_t payload = std::max(tun_mtu_max + 512, size_t(2048));
    constexpr size_t headroom = 512;
    constexpr size_t tailroom = 512;
    constexpr size_t align_block = 16;
    constexpr BufferFlags buffer_flags = BufAllocFlags::NO_FLAGS;

    Frame::Ptr frame(new Frame(Frame::Context(headroom, payload, tailroom, 0, align_block, buffer_flags)));
    if (align_adjust_3_1)
    {
        (*frame)[Frame::READ_LINK_TCP] = Frame::Context(headroom, payload, tailroom, 3, align_block, buffer_flags);
        (*frame)[Frame::READ_LINK_UDP] = Frame::Context(headroom, payload, tailroom, 1, align_block, buffer_flags);
    }
    (*frame)[Frame::READ_BIO_MEMQ_STREAM] = Frame::Context(headroom,
                                                           std::min(control_channel_payload, payload),
                                                           tailroom,
                                                           0,
                                                           align_block,
                                                           buffer_flags);
    (*frame)[Frame::WRITE_SSL_CLEARTEXT] = Frame::Context(headroom,
                                                          payload,
                                                          tailroom,
                                                          0,
                                                          align_block,
                                                          BufAllocFlags::GROW);
    frame->standardize_capacity(~0);

    if (verbose)
        OPENVPN_LOG("Frame=" << headroom << '/' << payload << '/' << tailroom
                             << " mssfix-ctrl=" << (*frame)[Frame::READ_BIO_MEMQ_STREAM].payload());

    return frame;
}

inline Frame::Context frame_init_context_simple(const size_t payload)
{
    constexpr size_t headroom = 512;
    constexpr size_t tailroom = 512;
    constexpr size_t align_block = 16;
    constexpr BufferFlags buffer_flags = BufAllocFlags::NO_FLAGS;
    return Frame::Context(headroom, payload, tailroom, 0, align_block, buffer_flags);
}

inline Frame::Ptr frame_init_simple(const size_t payload)
{
    Frame::Ptr frame = new Frame(frame_init_context_simple(payload));
    frame->standardize_capacity(~0);
    return frame;
}

} // namespace openvpn

#endif // OPENVPN_FRAME_FRAME_INIT_H
