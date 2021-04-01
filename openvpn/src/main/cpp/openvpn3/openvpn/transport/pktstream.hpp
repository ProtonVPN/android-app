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

#ifndef OPENVPN_TRANSPORT_PKTSTREAM_H
#define OPENVPN_TRANSPORT_PKTSTREAM_H

#include <algorithm>         // for std::min
#include <cstdint>           // for std::uint16_t, etc.

#include <openvpn/common/exception.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/frame/frame.hpp>

namespace openvpn {

  // Used to encapsulate OpenVPN packets onto a stream transport such as TCP,
  // or extract them from the stream.
  class PacketStream
  {
  public:
    OPENVPN_SIMPLE_EXCEPTION(embedded_packet_size_error);
    OPENVPN_SIMPLE_EXCEPTION(packet_not_fully_formed);

    PacketStream() : declared_size_defined(false) {}

    // Add stream fragment to packet that we are building up.
    // Data will be read from buf.  On return buf may still contain
    // residual data.  If function is able to use all of buf, it may
    // grab ownership of it, replacing buf as returned to caller with
    // an empty (but possibly pre-allocated) BufferAllocated object.
    void put(BufferAllocated& buf, const Frame::Context& frame_context)
    {
      if (buf.defined())
	{
	  if (!declared_size_defined && !buffer.defined())
	    {
	      if (size_defined(buf))
		{
		  extract_size(buf, frame_context);
		  if (buf.size() == declared_size)     // packet is correctly sized
		    buffer.swap(buf);
		  else if (buf.size() < declared_size) // packet is undersized
		    {
		      if (buf.offset() + declared_size + frame_context.tailroom() <= buf.capacity())
			buffer.swap(buf);
		      else
			{
			  buffer.swap(buf);
			  frame_context.realign(buffer);
			}
		    }
		  else                                 // packet is oversized
		    {
		      frame_context.prepare(buffer);
		      const unsigned char *data = buf.read_alloc(declared_size);
		      buffer.write(data, declared_size);
		    }
		}
	      else // rare case where packet fragment is too small to contain embedded size
		{
		  buffer.swap(buf);
		  frame_context.realign(buffer);
		}
	    }
	  else
	    {
	      while (!declared_size_defined)
		{
		  if (buf.empty())
		    return;
		  buffer.push_back(buf.pop_front());
		  if (size_defined(buffer))
		    extract_size(buffer, frame_context);
		}
	      if (buffer.size() < declared_size)
		{
		  const size_t needed = std::min(declared_size - buffer.size(), buf.size());
		  const unsigned char *data = buf.read_alloc(needed);
		  buffer.write(data, needed);
		}
	    }
	}
    }

    // returns true if get() may be called to return fully formed packet
    bool ready() const
    {
      return declared_size_defined && buffer.size() >= declared_size;
    }

    // return fully formed packet as ret.  ret, as passed to method, will
    // be grabbed, reset, and subsequently used internally.
    void get(BufferAllocated& ret)
    {
      if (declared_size_defined && buffer.size() == declared_size)
	{
	  ret.swap(buffer);
	  buffer.reset_content();
	  declared_size_defined = false;
	}
      else
	throw packet_not_fully_formed();
    }

    // prepend uint16_t size to buffer
    static void prepend_size(Buffer& buf)
    {
      const std::uint16_t net_len = htons(buf.size());
      buf.prepend((const unsigned char *)&net_len, sizeof(net_len));
    }

  private:
    void extract_size(Buffer& buf, const Frame::Context& frame_context)
    {
      const size_t size = read_size(buf);
      validate_size(size, frame_context);
      declared_size = size;
      declared_size_defined = true;
    }

    static bool size_defined(const Buffer& buf)
    {
      return buf.size() >= sizeof(std::uint16_t);
    }

    static size_t read_size(Buffer& buf)
    {
      std::uint16_t net_len;
      buf.read((unsigned char *)&net_len, sizeof(net_len));
      return ntohs(net_len);
    }

    static void validate_size(const size_t size, const Frame::Context& frame_context)
    {
      if (!size || size > frame_context.payload())
	throw embedded_packet_size_error();
    }

    size_t declared_size;       // declared size of packet in leading uint16_t prefix
    bool declared_size_defined; // true if declared_size is defined
    BufferAllocated buffer;     // accumulated packet data
  };
} // namespace openvpn

#endif // OPENVPN_TRANSPORT_PKTSTREAM_H
