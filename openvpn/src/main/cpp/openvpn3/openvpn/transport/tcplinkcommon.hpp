//    Copyright (C) 2012-2020 OpenVPN Inc.

// Base class for TCP link objects.

#ifndef OPENVPN_TRANSPORT_COMMONLINK_H
#define OPENVPN_TRANSPORT_COMMONLINK_H

#include <deque>
#include <utility> // for std::move
#include <memory>

#include <openvpn/io/io.hpp>

#include <openvpn/common/size.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/common/socktypes.hpp>
#include <openvpn/error/excode.hpp>
#include <openvpn/frame/frame.hpp>
#include <openvpn/log/sessionstats.hpp>
#include <openvpn/transport/tcplinkbase.hpp>
#include <openvpn/transport/pktstream.hpp>
#include <openvpn/transport/mutate.hpp>

#ifdef OPENVPN_GREMLIN
#include <openvpn/transport/gremlin.hpp>
#endif

#if defined(OPENVPN_DEBUG_TCPLINK) && OPENVPN_DEBUG_TCPLINK >= 1
#define OPENVPN_LOG_TCPLINK_ERROR(x) OPENVPN_LOG(x)
#else
#define OPENVPN_LOG_TCPLINK_ERROR(x)
#endif

#if defined(OPENVPN_DEBUG_TCPLINK) && OPENVPN_DEBUG_TCPLINK >= 3
#define OPENVPN_LOG_TCPLINK_VERBOSE(x) OPENVPN_LOG(x)
#else
#define OPENVPN_LOG_TCPLINK_VERBOSE(x)
#endif

namespace openvpn {
  namespace TCPTransport {

    template <typename Protocol,
	      typename ReadHandler,
	      bool RAW_MODE_ONLY>
    class LinkCommon : public LinkBase
    {
      typedef std::deque<BufferPtr> Queue;

    public:
      typedef RCPtr<LinkCommon<Protocol, ReadHandler, RAW_MODE_ONLY>> Ptr;
      typedef Protocol protocol;

      // In raw mode, data is sent and received without any special encapsulation.
      // In non-raw mode, data is packetized by prepending a 16-bit length word
      // onto each packet.  The OpenVPN protocol runs in non-raw mode, while other
      // TCP protocols such as HTTP or HTTPS would run in raw mode.
      // This method is a no-op if RAW_MODE_ONLY is true.
      void set_raw_mode(const bool mode)
      {
	set_raw_mode_read(mode);
	set_raw_mode_write(mode);
      }

      void set_raw_mode_read(const bool mode)
      {
	if (RAW_MODE_ONLY)
	  raw_mode_read = true;
	else
	  raw_mode_read = mode;
      }

      void set_raw_mode_write(const bool mode)
      {
	if (RAW_MODE_ONLY)
	  raw_mode_write = true;
	else
	  raw_mode_write = mode;
      }

      void set_mutate(const TransportMutateStream::Ptr& mutate_arg)
      {
	mutate = mutate_arg;
      }

      bool send_queue_empty() const
      {
	return send_queue_size() == 0;
      }

      void inject(const Buffer& src)
      {
	const size_t size = src.size();
	OPENVPN_LOG_TCPLINK_VERBOSE("TCP inject size=" << size);
	if (size && !RAW_MODE_ONLY)
	  {
	    BufferAllocated buf;
	    frame_context.prepare(buf);
	    buf.write(src.c_data(), size);
	    BufferAllocated pkt;
	    put_pktstream(buf, pkt);
	  }
      }

      void start()
      {
	if (!halt)
	  queue_recv(nullptr);
      }

      void stop()
      {
	halt = true;
#ifdef OPENVPN_GREMLIN
	if (gremlin)
	  gremlin->stop();
#endif
      }

      void reset_align_adjust(const size_t align_adjust)
      {
	frame_context.reset_align_adjust(align_adjust + (is_raw_mode() ? 0 : 2));
      }

      unsigned int send_queue_size() const
      {
	return queue.size()
#ifdef OPENVPN_GREMLIN
	  + (gremlin ? gremlin->send_size() : 0)
#endif
	  ;
      }

      bool send(BufferAllocated& b)
      {
	if (halt)
	  return false;

	if (send_queue_max_size && send_queue_size() >= send_queue_max_size)
	  {
	    stats->error(Error::TCP_OVERFLOW);
	    read_handler->tcp_error_handler("TCP_OVERFLOW");
	    stop();
	    return false;
	  }

	BufferPtr buf;
	if (!free_list.empty())
	  {
	    buf = free_list.front();
	    free_list.pop_front();
	  }
	else
	  buf.reset(new BufferAllocated());
	buf->swap(b);
	if (!is_raw_mode_write())
	  PacketStream::prepend_size(*buf);
	if (mutate)
	  mutate->pre_send(*buf);
#ifdef OPENVPN_GREMLIN
	if (gremlin)
	  gremlin_queue_send_buffer(buf);
	else
#endif
	from_app_send_buffer(buf);
	return true;
      }

      void queue_recv(PacketFrom *tcpfrom)
      {
	OPENVPN_LOG_TCPLINK_VERBOSE("TLSLink::queue_recv");
	if (!tcpfrom)
	  tcpfrom = new PacketFrom();
	frame_context.prepare(tcpfrom->buf);

	socket.async_receive(frame_context.mutable_buffer_clamp(tcpfrom->buf),
			     [self=Ptr(this), tcpfrom=PacketFrom::SPtr(tcpfrom)](const openvpn_io::error_code& error, const size_t bytes_recvd) mutable
			     {
			       OPENVPN_ASYNC_HANDLER;
			       try
			       {
			         self->handle_recv(std::move(tcpfrom), error, bytes_recvd);
			       }
			       catch (const std::exception& e)
			       {
			         Error::Type err = Error::TCP_SIZE_ERROR;
				 const char *msg = "TCP_SIZE_ERROR";
			         // if exception is an ExceptionCode, translate the code
				 // to return status string
				 {
				   const ExceptionCode *ec = dynamic_cast<const ExceptionCode *>(&e);
				   if (ec && ec->code_defined())
				   {
				     err = ec->code();
				     msg = ec->what();
				   }
				 }

			         OPENVPN_LOG_TCPLINK_ERROR("TCP packet extract exception: " << e.what());
				 self->stats->error(err);
				 self->read_handler->tcp_error_handler(msg);
				 self->stop();
			       }
			     });
      }

    protected:
      LinkCommon(ReadHandler read_handler_arg,
		 typename Protocol::socket& socket_arg,
		 const size_t send_queue_max_size_arg, // 0 to disable
		 const size_t free_list_max_size_arg,
		 const Frame::Context& frame_context_arg,
		 const SessionStats::Ptr& stats_arg)
	: socket(socket_arg),
	  halt(false),
	  read_handler(read_handler_arg),
	  frame_context(frame_context_arg),
	  stats(stats_arg),
	  send_queue_max_size(send_queue_max_size_arg),
	  free_list_max_size(free_list_max_size_arg)
      {
	set_raw_mode(false);
      }

#ifdef OPENVPN_GREMLIN
      void gremlin_config(const Gremlin::Config::Ptr& config)
      {
	if (config)
	  gremlin.reset(new Gremlin::SendRecvQueue(socket.get_executor().context(), config, true));
      }
#endif

      bool is_raw_mode() const {
	return is_raw_mode_read() && is_raw_mode_write();
      }

      bool is_raw_mode_read() const {
	if (RAW_MODE_ONLY)
	  return true;
	else
	  return raw_mode_read;
      }

      bool is_raw_mode_write() const {
	if (RAW_MODE_ONLY)
	  return true;
	else
	  return raw_mode_write;
      }

      LinkCommon() { stop(); }

      void queue_send_buffer(BufferPtr& buf)
      {
	queue.push_back(std::move(buf));
	if (queue.size() == 1) // send operation not currently active?
	  queue_send();
      }

      void queue_send()
      {
	BufferAllocated& buf = *queue.front();
	socket.async_send(buf.const_buffer_clamp(),
			  [self=Ptr(this)](const openvpn_io::error_code& error, const size_t bytes_sent)
			  {
			    OPENVPN_ASYNC_HANDLER;
			    self->handle_send(error, bytes_sent);
			  });
      }

      void handle_send(const openvpn_io::error_code& error, const size_t bytes_sent)
      {
	if (!halt)
	  {
	    if (!error)
	      {
		OPENVPN_LOG_TCPLINK_VERBOSE("TLS-TCP send raw=" << raw_mode_write << " size=" << bytes_sent);
		stats->inc_stat(SessionStats::BYTES_OUT, bytes_sent);
		stats->inc_stat(SessionStats::PACKETS_OUT, 1);

		BufferPtr buf = queue.front();
		if (bytes_sent == buf->size())
		  {
		    queue.pop_front();
		    if (free_list.size() < free_list_max_size)
		      {
			buf->reset_content();
			free_list.push_back(std::move(buf)); // recycle the buffer for later use
		      }
		  }
		else if (bytes_sent < buf->size())
		  buf->advance(bytes_sent);
		else
		  {
		    stats->error(Error::TCP_OVERFLOW);
		    read_handler->tcp_error_handler("TCP_INTERNAL_ERROR"); // error sent more bytes than we asked for
		    stop();
		    return;
		  }
	      }
	    else
	      {
		OPENVPN_LOG_TCPLINK_ERROR("TLS-TCP send error: " << error.message());
		stats->error(Error::NETWORK_SEND_ERROR);
		read_handler->tcp_error_handler("NETWORK_SEND_ERROR");
		stop();
		return;
	      }
	    if (!queue.empty())
	      queue_send();
	    else
	      tcp_write_queue_needs_send();
	  }
      }

      bool process_recv_buffer(BufferAllocated& buf)
      {
	bool requeue = true;

	OPENVPN_LOG_TCPLINK_VERBOSE("TLSLink::process_recv_buffer: size=" << buf.size());

	if (!is_raw_mode_read())
	{
	  try {
	    BufferAllocated pkt;
	    requeue = put_pktstream(buf, pkt);
	    if (!buf.allocated() && pkt.allocated()) // recycle pkt allocated buffer
	      buf.move(pkt);
	  }
	  catch (const std::exception& e)
	  {
	    OPENVPN_LOG_TCPLINK_ERROR("TLS-TCP packet extract error: " << e.what());
	    stats->error(Error::TCP_SIZE_ERROR);
	    read_handler->tcp_error_handler("TCP_SIZE_ERROR");
	    stop();
	    return false;
	  }
	}
	else
	{
	  if (mutate)
	    mutate->post_recv(buf);
#ifdef OPENVPN_GREMLIN
	  if (gremlin)
	    requeue = gremlin_recv(buf);
	  else
#endif
	  requeue = read_handler->tcp_read_handler(buf);
	}

	return requeue;
      }

      void handle_recv(PacketFrom::SPtr pfp, const openvpn_io::error_code& error, const size_t bytes_recvd)
      {
	OPENVPN_LOG_TCPLINK_VERBOSE("Link::handle_recv: " << error.message());
	if (!halt)
	{
	  if (!error)
	  {
	    recv_buffer(pfp, bytes_recvd);
	  }
	  else if (error == openvpn_io::error::eof)
	  {
	    OPENVPN_LOG_TCPLINK_ERROR("TCP recv EOF");
	    read_handler->tcp_eof_handler();
	  }
	  else
	  {
	    OPENVPN_LOG_TCPLINK_ERROR("TCP recv error: " << error.message());
	    stats->error(Error::NETWORK_RECV_ERROR);
	    read_handler->tcp_error_handler("NETWORK_RECV_ERROR");
	    stop();
	  }
	}
      }

      bool put_pktstream(BufferAllocated& buf, BufferAllocated& pkt)
      {
	bool requeue = true;
	stats->inc_stat(SessionStats::BYTES_IN, buf.size());
	stats->inc_stat(SessionStats::PACKETS_IN, 1);
	if (mutate)
	  mutate->post_recv(buf);
	while (buf.size())
	  {
	    pktstream.put(buf, frame_context);
	    if (pktstream.ready())
	      {
		pktstream.get(pkt);
#ifdef OPENVPN_GREMLIN
		if (gremlin)
		  requeue = gremlin_recv(pkt);
		else
#endif
		requeue = read_handler->tcp_read_handler(pkt);
	      }
	  }
	return requeue;
      }

#ifdef OPENVPN_GREMLIN
      void gremlin_queue_send_buffer(BufferPtr& buf)
      {
	gremlin->send_queue([self=Ptr(this), buf=std::move(buf)]() mutable {
	    if (!self->halt)
	      {
		self->queue_send_buffer(buf);
	      }
	  });
      }

      bool gremlin_recv(BufferAllocated& buf)
      {
	gremlin->recv_queue([self=Ptr(this), buf=std::move(buf)]() mutable {
	    if (!self->halt)
	      {
		const bool requeue = self->read_handler->tcp_read_handler(buf);
		if (requeue)
		  self->queue_recv(nullptr);
	      }
	  });
	return false;
      }
#endif

      void tcp_write_queue_needs_send()
      {
	read_handler->tcp_write_queue_needs_send();
      }

      typename Protocol::socket& socket;
      bool halt;
      ReadHandler read_handler;
      Frame::Context frame_context;
      SessionStats::Ptr stats;
      const size_t send_queue_max_size;
      const size_t free_list_max_size;
      Queue queue;      // send queue
      Queue free_list;  // recycled free buffers for send queue
      PacketStream pktstream;
      TransportMutateStream::Ptr mutate;
      bool raw_mode_read;
      bool raw_mode_write;

#ifdef OPENVPN_GREMLIN
      std::unique_ptr<Gremlin::SendRecvQueue> gremlin;
#endif

    private:
      virtual void recv_buffer(PacketFrom::SPtr& pfp, const size_t bytes_recvd) = 0;
      virtual void from_app_send_buffer(BufferPtr& buf) = 0;
    };
  }
} // namespace openvpn

#endif
