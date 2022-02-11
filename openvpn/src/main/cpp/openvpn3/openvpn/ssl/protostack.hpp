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

#ifndef OPENVPN_SSL_PROTOSTACK_H
#define OPENVPN_SSL_PROTOSTACK_H

#include <deque>
#include <utility>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/size.hpp>
#include <openvpn/common/usecount.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/time/time.hpp>
#include <openvpn/log/sessionstats.hpp>
#include <openvpn/reliable/relrecv.hpp>
#include <openvpn/reliable/relsend.hpp>
#include <openvpn/reliable/relack.hpp>
#include <openvpn/frame/frame.hpp>
#include <openvpn/error/excode.hpp>
#include <openvpn/ssl/sslconsts.hpp>
#include <openvpn/ssl/sslapi.hpp>

// ProtoStackBase is designed to allow general-purpose protocols (including
// but not limited to OpenVPN) to run over SSL, where the underlying transport
// layer is unreliable, such as UDP.  The OpenVPN protocol implementation in
// proto.hpp (ProtoContext) layers on top of ProtoStackBase.
// ProtoStackBase is independent of any particular SSL implementation, and
// accepts the SSL object type as a template parameter.

namespace openvpn {

  // PACKET type must define the following methods:
  //
  // Default constructor:
  //   PACKET()
  //
  // Constructor for BufferPtr:
  //   explicit PACKET(const BufferPtr& buf)
  //
  // Return cloned packet, including cloned buffer content:
  //   PACKET clone() const
  //
  // Test if defined:
  //   operator bool() const
  //
  // Return true if packet is raw, or false if packet is SSL ciphertext:
  //   bool is_raw() const
  //
  // Reset back to post-default-constructor state:
  //   void reset()
  //
  // Return internal BufferPtr:
  //   const BufferPtr& buffer_ptr() const
  //
  // Call frame.prepare on internal buffer:
  //   void frame_prepare(const Frame& frame, const unsigned int context)

  template <typename PACKET, typename PARENT>
  class ProtoStackBase
  {
  public:
    typedef reliable::id_t id_t;
    typedef ReliableSendTemplate<PACKET> ReliableSend;
    typedef ReliableRecvTemplate<PACKET> ReliableRecv;

    OPENVPN_SIMPLE_EXCEPTION(proto_stack_invalidated);
    OPENVPN_SIMPLE_EXCEPTION(unknown_status_from_ssl_layer);

    enum NetSendType {
      NET_SEND_SSL,
      NET_SEND_RAW,
      NET_SEND_ACK,
      NET_SEND_RETRANSMIT,
    };

    ProtoStackBase(SSLFactoryAPI& ssl_factory, // SSL factory object that can be used to generate new SSL sessions
		   TimePtr now_arg,                   // pointer to current time
		   const Time::Duration& tls_timeout_arg, // packet retransmit timeout
		   const Frame::Ptr& frame,           // contains info on how to allocate and align buffers
		   const SessionStats::Ptr& stats_arg,  // error statistics
		   const id_t span,                   // basically the window size for our reliability layer
		   const size_t max_ack_list)         // maximum number of ACK messages to bundle in one packet
      : tls_timeout(tls_timeout_arg),
	ssl_(ssl_factory.ssl()),
	frame_(frame),
	up_stack_reentry_level(0),
	invalidated_(false),
	invalidation_reason_(Error::SUCCESS),
	ssl_started_(false),
	next_retransmit_(Time::infinite()),
	stats(stats_arg),
	now(now_arg),
	rel_recv(span),
	rel_send(span),
	xmit_acks(max_ack_list)
    {
    }

    // Start SSL handshake on underlying SSL connection object.
    void start_handshake()
    {
      if (!invalidated())
	{
	  ssl_->start_handshake();
	  ssl_started_ = true;
	  up_sequenced();
	}
    }

    uint32_t get_tls_warnings() const
    {
      return ssl_->get_tls_warnings();
    }

    // Incoming ciphertext packet arriving from network,
    // we will take ownership of pkt.
    bool net_recv(PACKET&& pkt)
    {
      if (!invalidated())
	return up_stack(pkt);
      return false;
    }

    // Outgoing application-level cleartext packet ready to send
    // (will be encrypted via SSL), we will take ownership
    // of buf.
    void app_send(BufferPtr&& buf)
    {
      if (!invalidated())
	app_write_queue.push_back(std::move(buf));
    }

    // Outgoing raw packet ready to send (will NOT be encrypted
    // via SSL, but will still be encapsulated, sequentialized,
    // and tracked via reliability layer).
    void raw_send(PACKET&& pkt)
    {
      if (!invalidated())
	raw_write_queue.push_back(std::move(pkt));
    }

    // Write any pending data to network and update retransmit
    // timer.  Should be called as a final step after one or more
    // net_recv, app_send, raw_send, or start_handshake calls.
    void flush()
    {
      if (!invalidated() && !up_stack_reentry_level)
	{
	  down_stack_raw();
	  down_stack_app();
	  update_retransmit();
	}
    }

    // Send pending ACKs back to sender for packets already received
    void send_pending_acks()
    {
      if (!invalidated())
	{
	  while (!xmit_acks.empty())
	    {
	      ack_send_buf.frame_prepare(*frame_, Frame::WRITE_ACK_STANDALONE);

	      // encapsulate standalone ACK
	      parent().generate_ack(ack_send_buf);

	      // transmit it
	      parent().net_send(ack_send_buf, NET_SEND_ACK);
	    }
	}
    }

    // Send any pending retransmissions
    void retransmit()
    {
      if (!invalidated() && *now >= next_retransmit_)
	{
	  for (id_t i = rel_send.head_id(); i < rel_send.tail_id(); ++i)
	    {
	      typename ReliableSend::Message& m = rel_send.ref_by_id(i);
	      if (m.ready_retransmit(*now))
		{
		  // preserve original packet non-encapsulated
		  PACKET pkt = m.packet.clone();

		  // encapsulate packet
		  try {
		    parent().encapsulate(m.id(), pkt);
		  }
		  catch (...)
		    {
		      error(Error::ENCAPSULATION_ERROR);
		      throw;
		    }
		  parent().net_send(pkt, NET_SEND_RETRANSMIT);
		  m.reset_retransmit(*now, tls_timeout);
		}
	    }
	  update_retransmit();
	}
    }

    // When should we next call retransmit()
    Time next_retransmit() const
    {
      if (!invalidated())
	return next_retransmit_;
      else
	return Time::infinite();
    }

    // Has SSL handshake been started yet?
    bool ssl_started() const { return ssl_started_; }

    // Was session invalidated by an exception?
    bool invalidated() const { return invalidated_; }

    // Reason for invalidation
    Error::Type invalidation_reason() const { return invalidation_reason_; }

    // Invalidate session
    void invalidate(const Error::Type reason)
    {
      if (!invalidated_)
	{
	  invalidated_ = true;
	  invalidation_reason_ = reason;
	  parent().invalidate_callback();
	}
    }

    std::string ssl_handshake_details() const
    {
      return ssl_->ssl_handshake_details();
    }

    void export_key_material(OpenVPNStaticKey& key) const
    {
      if (!ssl_->export_keying_material("EXPORTER-OpenVPN-datakeys", key.raw_alloc(),
      	OpenVPNStaticKey::KEY_SIZE))
	throw ErrorCode(Error::KEY_EXPANSION_ERROR, true, "TLS Keying material export error");
    }

    const AuthCert::Ptr& auth_cert() const
    {
      return ssl_->auth_cert();
    }

  private:
    // Parent methods -- derived class must define these methods

    // Encapsulate packet, use id as sequence number.  If xmit_acks is non-empty,
    // try to piggy-back ACK replies from xmit_acks to sender in encapsulated
    // packet. Any exceptions thrown will invalidate session, i.e. this object
    // can no longer be used.
    //
    // void encapsulate(id_t id, PACKET& pkt) = 0;

    // Perform integrity check on packet.  If packet is good, unencapsulate it and
    // pass it into the rel_recv object.  Any ACKs received for messages previously
    // sent should be marked in rel_send.  Message sequence number should be recorded
    // in xmit_acks.  Exceptions may be thrown here and they will be passed up to
    // caller of net_recv and will not invalidate the session.
    // Method should return true if packet was placed into rel_recv.
    //
    // bool decapsulate(PACKET& pkt) = 0;

    // Generate a standalone ACK message in buf based on ACKs in xmit_acks
    // (PACKET will be already be initialized by frame_prepare()).
    //
    // void generate_ack(PACKET& pkt) = 0;

    // Transmit encapsulated ciphertext packet to peer.  Method may not modify
    // or take ownership of net_pkt or underlying data unless it copies it.
    //
    // void net_send(const PACKET& net_pkt, const NetSendType nstype) = 0;

    // Pass cleartext data up to application, which make take
    // ownership of to_app_buf via std::move.
    //
    // void app_recv(BufferPtr&& to_app_buf) = 0;

    // Pass raw data up to application.  A packet is considered to be raw
    // if is_raw() method returns true.  Method may take ownership
    // of raw_pkt via std::move.
    //
    // void raw_recv(PACKET&& raw_pkt) = 0;

    // called if session is invalidated by an error (optional)
    //
    // void invalidate_callback() {}

    // END of parent methods

    // get reference to parent for CRTP
    PARENT& parent()
    {
      return *static_cast<PARENT*>(this);
    }

    // app data -> SSL -> protocol encapsulation -> reliability layer -> network
    void down_stack_app()
    {
      if (ssl_started_)
	{
	  // push app-layer cleartext through SSL object
	  while (!app_write_queue.empty())
	    {
	      BufferPtr& buf = app_write_queue.front();
	      ssize_t size;
	      try {
		size = ssl_->write_cleartext_unbuffered(buf->data(), buf->size());
	      }
	      catch (...)
		{
		  error(Error::SSL_ERROR);
		  throw;
		}
	      if (size == static_cast<ssize_t>(buf->size()))
		app_write_queue.pop_front();
	      else if (size == SSLConst::SHOULD_RETRY)
		break;
	      else if (size >= 0)
		{
		  // partial write
		  app_write_queue.front()->advance(size);
		  break;
		}
	      else
		{
		  error(Error::SSL_ERROR);
		  throw unknown_status_from_ssl_layer();
		}
	    }

	  // encapsulate SSL ciphertext packets
	  while (ssl_->read_ciphertext_ready() && rel_send.ready())
	    {
	      typename ReliableSend::Message& m = rel_send.send(*now, tls_timeout);
	      m.packet = PACKET(ssl_->read_ciphertext());

	      // encapsulate and send cloned packet, preserve original one for retransmit
	      PACKET pkt = m.packet.clone();

	      // encapsulate packet
	      try {
		parent().encapsulate(m.id(), pkt);
	      }
	      catch (...)
		{
		  error(Error::ENCAPSULATION_ERROR);
		  throw;
		}

	      // transmit it
	      parent().net_send(pkt, NET_SEND_SSL);
	    }
	}
    }

    // raw app data -> protocol encapsulation -> reliability layer -> network
    void down_stack_raw()
    {
      while (!raw_write_queue.empty() && rel_send.ready())
	{
	  typename ReliableSend::Message& m = rel_send.send(*now, tls_timeout);
	  m.packet = raw_write_queue.front();
	  raw_write_queue.pop_front();

	  PACKET pkt = m.packet.clone();

	  // encapsulate packet
	  try {
	    parent().encapsulate(m.id(), pkt);
	  }
	  catch (...)
	    {
	      error(Error::ENCAPSULATION_ERROR);
	      throw;
	    }

	  // transmit it
	  parent().net_send(pkt, NET_SEND_RAW);
	}
    }

    // network -> reliability layer -> protocol decapsulation -> SSL -> app
    bool up_stack(PACKET& recv)
    {
      UseCount use_count(up_stack_reentry_level);
      if (parent().decapsulate(recv))
	{
	  up_sequenced();
	  return true;
	}
      else
	return false;
    }

    // if a sequenced packet is available from reliability layer,
    // move it up the stack
    void up_sequenced()
    {
      // is sequenced receive packet available?
      while (rel_recv.ready())
	{
	  typename ReliableRecv::Message& m = rel_recv.next_sequenced();
	  if (m.packet.is_raw())
	    parent().raw_recv(std::move(m.packet));
	  else // SSL packet
	    {
	      if (ssl_started_)
		ssl_->write_ciphertext(m.packet.buffer_ptr());
	      else
		break;
	    }
	  rel_recv.advance();
	}

      // read cleartext data from SSL object
      if (ssl_started_)
	while (ssl_->read_cleartext_ready())
	  {
	    ssize_t size;
	    to_app_buf.reset(new BufferAllocated());
	    frame_->prepare(Frame::READ_SSL_CLEARTEXT, *to_app_buf);
	    try {
	      size = ssl_->read_cleartext(to_app_buf->data(), to_app_buf->max_size());
	    }
	    catch (...)
	      {
		// SSL fatal errors will invalidate the session
		error(Error::SSL_ERROR);
		throw;
	      }
	    if (size >= 0)
	      {
		to_app_buf->set_size(size);

		// pass cleartext data to app
		parent().app_recv(std::move(to_app_buf));
	      }
	    else if (size == SSLConst::SHOULD_RETRY)
	      break;
	    else if (size == SSLConst::PEER_CLOSE_NOTIFY)
	      {
		error(Error::SSL_ERROR);
		throw ErrorCode(Error::CLIENT_HALT, true, "SSL Close Notify received");
	      }
	    else
	      {
		error(Error::SSL_ERROR);
		throw unknown_status_from_ssl_layer();
	      }
	  }
    }

    void update_retransmit()
    {
      next_retransmit_ = *now + rel_send.until_retransmit(*now);
    }

    void error(const Error::Type reason)
    {
      if (stats)
	stats->error(reason);
      invalidate(reason);
    }

  private:
    const Time::Duration tls_timeout;
    typename SSLAPI::Ptr ssl_;
    Frame::Ptr frame_;
    int up_stack_reentry_level;
    bool invalidated_;
    Error::Type invalidation_reason_;
    bool ssl_started_;
    Time next_retransmit_;
    BufferPtr to_app_buf; // cleartext data decrypted by SSL that is to be passed to app via app_recv method
    PACKET ack_send_buf;  // only used for standalone ACKs to be sent to peer
    std::deque<BufferPtr> app_write_queue;
    std::deque<PACKET> raw_write_queue;
    SessionStats::Ptr stats;

  protected:
    TimePtr now;
    ReliableRecv rel_recv;
    ReliableSend rel_send;
    ReliableAck xmit_acks;
  };

} // namespace openvpn

#endif // OPENVPN_SSL_PROTOSTACK_H
