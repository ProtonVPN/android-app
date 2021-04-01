//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
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

// Method to generate a Frame object for typical OpenVPN usage

#ifndef OPENVPN_FRAME_FRAME_INIT_H
#define OPENVPN_FRAME_FRAME_INIT_H

#include <algorithm>

#include <openvpn/frame/frame.hpp>

namespace openvpn {

  inline Frame::Ptr frame_init(const bool align_adjust_3_1,
			       const size_t tun_mtu,
			       const size_t control_channel_payload,
			       const bool verbose)
  {
    const size_t payload = std::max(tun_mtu + 512, size_t(2048));
    const size_t headroom = 512;
    const size_t tailroom = 512;
    const size_t align_block = 16;
    const unsigned int buffer_flags = 0;

    Frame::Ptr frame(new Frame(Frame::Context(headroom, payload, tailroom, 0, align_block, buffer_flags)));
    if (align_adjust_3_1)
      {
	(*frame)[Frame::READ_LINK_TCP] = Frame::Context(headroom, payload, tailroom, 3, align_block, buffer_flags);
	(*frame)[Frame::READ_LINK_UDP] = Frame::Context(headroom, payload, tailroom, 1, align_block, buffer_flags);
      }
    (*frame)[Frame::READ_BIO_MEMQ_STREAM] = Frame::Context(headroom, std::min(control_channel_payload, payload),
							   tailroom, 0, align_block, buffer_flags);
    (*frame)[Frame::WRITE_SSL_CLEARTEXT] = Frame::Context(headroom, payload, tailroom, 0, align_block, BufferAllocated::GROW);
    frame->standardize_capacity(~0);

    if (verbose)
      OPENVPN_LOG("Frame=" << headroom << '/' << payload << '/' << tailroom
		  << " mssfix-ctrl=" << (*frame)[Frame::READ_BIO_MEMQ_STREAM].payload());

    return frame;
  }

  inline Frame::Context frame_init_context_simple(const size_t payload)
  {
    const size_t headroom = 512;
    const size_t tailroom = 512;
    const size_t align_block = 16;
    const unsigned int buffer_flags = 0;
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
