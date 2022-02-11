//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2021 OpenVPN Inc.
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

// Low level tun device I/O class for all platforms (Unix and Windows)

#pragma once

#include <utility>

#include <openvpn/io/io.hpp>

#include <openvpn/common/bigmutex.hpp>
#include <openvpn/common/size.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/frame/frame.hpp>
#include <openvpn/ip/ipcommon.hpp>
#include <openvpn/common/socktypes.hpp>
#include <openvpn/log/sessionstats.hpp>
#include <openvpn/tun/tunlog.hpp>

namespace openvpn {

  template <typename ReadHandler, typename PacketFrom, typename STREAM>
  class TunIO : public RC<thread_unsafe_refcount>
  {
  public:
    typedef RCPtr<TunIO> Ptr;

    TunIO(ReadHandler read_handler_arg,
	  const Frame::Ptr& frame_arg,
	  const SessionStats::Ptr& stats_arg,
	  const size_t frame_context_type=Frame::READ_TUN)
      : read_handler(read_handler_arg),
	frame_context((*frame_arg)[frame_context_type]),
	stats(stats_arg)
    {
    }

    TunIO(ReadHandler read_handler_arg,
	  const Frame::Context& frame_context_arg,
	  const SessionStats::Ptr& stats_arg)
      : read_handler(read_handler_arg),
	frame_context(frame_context_arg),
	stats(stats_arg)
    {
    }

    virtual ~TunIO()
    {
      //OPENVPN_LOG("**** TUNIO destruct");
      stop();
      delete stream;
    }

    bool write(Buffer& buf)
    {
      if (!halt)
	{
	  try {
	    // handle tun packet prefix, if enabled
	    if (tun_prefix)
	      {
		if (buf.offset() >= 4 && buf.size() >= 1)
		  {
		    switch (IPCommon::version(buf[0]))
		      {
		      case 4:
			prepend_pf_inet(buf, PF_INET);
			break;
		      case 6:
			prepend_pf_inet(buf, PF_INET6);
			break;
		      default:
			OPENVPN_LOG_TUN_ERROR("TUN write error: cannot identify IP version for prefix");
			tun_error(Error::TUN_FRAMING_ERROR, nullptr);
			return false;
		      }
		  }
		else
		  {
		    OPENVPN_LOG_TUN_ERROR("TUN write error: cannot write prefix");
		    tun_error(Error::TUN_FRAMING_ERROR, nullptr);
		    return false;
		  }
	      }

	    // write data to tun device
	    const size_t wrote = stream->write_some(buf.const_buffer());
	    if (stats)
	      {
		stats->inc_stat(SessionStats::TUN_BYTES_OUT, wrote);
		stats->inc_stat(SessionStats::TUN_PACKETS_OUT, 1);
	      }
	    if (wrote == buf.size())
	      return true;
	    else
	      {
		OPENVPN_LOG_TUN_ERROR("TUN partial write error");
		tun_error(Error::TUN_WRITE_ERROR, nullptr);
		return false;
	      }
	  }
	  catch (openvpn_io::system_error& e)
	    {
	      OPENVPN_LOG_TUN_ERROR("TUN write exception: " << e.what());
	      const openvpn_io::error_code code(e.code());
	      tun_error(Error::TUN_WRITE_ERROR, &code);
	      return false;
	    }
	}
      else
	return false;
    }

    template <class BUFSEQ>
    bool write_seq(const BUFSEQ& bs)
    {
      if (!halt)
	{
	  try {
	    // write data to tun device
	    const size_t wrote = stream->write_some(bs);
	    if (stats)
	      {
		stats->inc_stat(SessionStats::TUN_BYTES_OUT, wrote);
		stats->inc_stat(SessionStats::TUN_PACKETS_OUT, 1);
	      }
	    if (wrote == bs.size())
	      return true;
	    else
	      {
		OPENVPN_LOG_TUN_ERROR("TUN partial write error");
		tun_error(Error::TUN_WRITE_ERROR, nullptr);
		return false;
	      }
	  }
	  catch (openvpn_io::system_error& e)
	    {
	      OPENVPN_LOG_TUN_ERROR("TUN write exception: " << e.what());
	      const openvpn_io::error_code code(e.code());
	      tun_error(Error::TUN_WRITE_ERROR, &code);
	      return false;
	    }
	}
      else
	return false;
    }

    void start(const int n_parallel)
    {
      if (!halt)
	{
	  for (int i = 0; i < n_parallel; i++)
	    queue_read(nullptr);
	}
    }

    // must be called by derived class destructor
    void stop()
    {
      if (!halt)
	{
	  halt = true;
	  if (stream)
	    {
	      stream->cancel();
	      if (!retain_stream)
		{
		  //OPENVPN_LOG("**** TUNIO close");
		  stream->close();
		}
	      else
		stream->release();
	    }
	}
    }

    std::string name() const
    {
      return name_;
    }

  private:
    void prepend_pf_inet(Buffer& buf, const std::uint32_t value)
    {
      const std::uint32_t net_value = htonl(value);
      buf.prepend((unsigned char *)&net_value, sizeof(net_value));
    }

  protected:
    void queue_read(PacketFrom *tunfrom)
    {
      OPENVPN_LOG_TUN_VERBOSE("TunIO::queue_read");
      if (!tunfrom)
	tunfrom = new PacketFrom();
      frame_context.prepare(tunfrom->buf);

      // queue read on tun device
      stream->async_read_some(frame_context.mutable_buffer(tunfrom->buf),
			      [self=Ptr(this), tunfrom=typename PacketFrom::SPtr(tunfrom)](const openvpn_io::error_code& error, const size_t bytes_recvd) mutable
                              {
                                OPENVPN_ASYNC_HANDLER;
                                self->handle_read(std::move(tunfrom), error, bytes_recvd);
                              });
    }

    void handle_read(typename PacketFrom::SPtr pfp, const openvpn_io::error_code& error, const size_t bytes_recvd)
    {
      OPENVPN_LOG_TUN_VERBOSE("TunIO::handle_read: " << error.message());
      if (!halt)
	{
	  if (!error)
	    {
	      pfp->buf.set_size(bytes_recvd);
	      if (stats)
		{
		  stats->inc_stat(SessionStats::TUN_BYTES_IN, bytes_recvd);
		  stats->inc_stat(SessionStats::TUN_PACKETS_IN, 1);
		}
	      if (!tun_prefix)
		{
		  read_handler->tun_read_handler(pfp);
		}
	      else if (pfp->buf.size() >= 4)
		{
		  // handle tun packet prefix, if enabled
		  pfp->buf.advance(4);
		  read_handler->tun_read_handler(pfp);
		}
	      else
		{
		  OPENVPN_LOG_TUN_ERROR("TUN Read Error: cannot read prefix");
		  tun_error(Error::TUN_READ_ERROR, nullptr);
		}
	    }
	  else
	    {
	      OPENVPN_LOG_TUN_ERROR("TUN Read Error: " << error.message());
	      tun_error(Error::TUN_READ_ERROR, &error);
	    }
	  if (!halt)
	    queue_read(pfp.release()); // reuse buffer if still available
	}
    }

    void tun_error(const Error::Type errtype, const openvpn_io::error_code* error)
    {
      if (stats)
	stats->error(errtype);
      read_handler->tun_error_handler(errtype, error);
    }

    // should be set by derived class constructor
    std::string name_;
    STREAM *stream = nullptr;
    bool retain_stream = false;  // don't close tun stream
    bool tun_prefix = false;

    ReadHandler read_handler;
    const Frame::Context frame_context;
    SessionStats::Ptr stats;

    bool halt = false;
  };
}
