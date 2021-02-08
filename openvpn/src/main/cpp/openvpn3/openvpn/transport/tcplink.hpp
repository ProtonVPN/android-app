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

// Low-level TCP transport object.

#ifndef OPENVPN_TRANSPORT_TCPLINK_H
#define OPENVPN_TRANSPORT_TCPLINK_H

#include <deque>
#include <utility> // for std::move
#include <memory>

#include <openvpn/io/io.hpp>

#include <openvpn/common/bigmutex.hpp>
#include <openvpn/common/size.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/common/socktypes.hpp>
#include <openvpn/frame/frame.hpp>
#include <openvpn/log/sessionstats.hpp>
#include <openvpn/transport/pktstream.hpp>
#include <openvpn/transport/mutate.hpp>
#include <openvpn/transport/tcplinkcommon.hpp>

#ifdef OPENVPN_GREMLIN
#include <openvpn/transport/gremlin.hpp>
#endif

namespace openvpn {
  namespace TCPTransport {

    template <typename Protocol, typename ReadHandler, bool RAW_MODE_ONLY>
    class Link : public LinkCommon<Protocol,
				   ReadHandler,
				   RAW_MODE_ONLY>
    {
      typedef std::deque<BufferPtr> Queue;

    public:
      typedef LinkCommon<Protocol,
		         ReadHandler,
		         RAW_MODE_ONLY> Base;
      typedef RCPtr<Link> Ptr;

      typedef Protocol protocol;

      friend Base;

      Link(ReadHandler read_handler_arg,
	   typename Protocol::socket& socket_arg,
	   const size_t send_queue_max_size_arg, // 0 to disable
	   const size_t free_list_max_size_arg,
	   const Frame::Context& frame_context_arg,
	   const SessionStats::Ptr& stats_arg)
	: Base(read_handler_arg, socket_arg, send_queue_max_size_arg,
	       free_list_max_size_arg, frame_context_arg, stats_arg)
      { }

    private:
      // Called by LinkCommon
      virtual void from_app_send_buffer(BufferPtr& buf) override
      {
	Base::queue_send_buffer(buf);
      }

      virtual void recv_buffer(PacketFrom::SPtr& pfp, const size_t bytes_recvd) override
      {
	bool requeue = true;
	OPENVPN_LOG_TCPLINK_VERBOSE("TCP recv raw=" << Base::raw_mode_read << " size=" << bytes_recvd);

	pfp->buf.set_size(bytes_recvd);
	requeue = Base::process_recv_buffer(pfp->buf);
	if (!Base::halt && requeue)
	  Base::queue_recv(pfp.release()); // reuse PacketFrom object
      }
    };
  }
} // namespace openvpn

#endif
