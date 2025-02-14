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

#pragma once

#include <algorithm> // for std::min
#include <cstdint>   // for std::uint16_t, etc.
#include <limits>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/socktypes.hpp>
#include <openvpn/common/size.hpp>
#include <openvpn/common/numeric_cast.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/frame/frame.hpp>

namespace openvpn {

// Used to encapsulate OpenVPN, DNS, or other protocols onto a
// stream transport such as TCP, or extract them from the stream.
// SIZE_TYPE indicates the size of the length word, and should be
// a uint16_t for OpenVPN and DNS protocols, but may be uint32_t
// for other procotols.  In all cases, the length word is represented
// by network-endian ordering.
template <typename SIZE_TYPE>
class PacketStream
{
  private:
    static constexpr size_t SIZE_UNDEF = std::numeric_limits<size_t>::max();

  public:
    OPENVPN_SIMPLE_EXCEPTION(embedded_packet_size_error);
    OPENVPN_SIMPLE_EXCEPTION(packet_not_fully_formed);

    // Add stream fragment to packet that we are building up.
    // Data will be read from buf.  On return buf may still contain
    // residual data.  If function is able to use all of buf, it may
    // grab ownership of it, replacing buf as returned to caller with
    // an empty (but possibly pre-allocated) BufferAllocated object.
    void put(BufferAllocated &buf, const Frame::Context &frame_context)
    {
        if (buf.defined())
        {
            if (!declared_size_defined() && !buffer.defined())
            {
                if (size_defined(buf))
                {
                    extract_size(buf, frame_context);
                    if (buf.size() == declared_size) // packet is correctly sized
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
                    else // packet is oversized
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
                while (!declared_size_defined())
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
        return declared_size_defined() && buffer.size() >= declared_size;
    }

    // return fully formed packet as ret.  ret, as passed to method, will
    // be grabbed, reset, and subsequently used internally.
    void get(BufferAllocated &ret)
    {
        if (declared_size_defined() && buffer.size() == declared_size)
        {
            ret.swap(buffer);
            buffer.reset_content();
            declared_size = SIZE_UNDEF;
        }
        else
            throw packet_not_fully_formed();
    }

    // this method is provided for prototype compatibility
    // with PacketStreamResidual
    void get(BufferAllocated &ret, const Frame::Context &frame_context)
    {
        get(ret);
    }

    // prepend SIZE_TYPE size to buffer
    static void prepend_size(Buffer &buf)
    {
        SIZE_TYPE net_len;
        host_to_network(net_len, buf.size());
        buf.prepend((const unsigned char *)&net_len, sizeof(net_len));
    }

    // reset the object to default-initialized state
    void reset()
    {
        declared_size = SIZE_UNDEF;
        buffer.clear();
    }

#ifndef UNIT_TEST
  private:
#endif

    // specialized methods for ntohl, ntohs, htonl, htons

    static size_t network_to_host(const std::uint16_t value)
    {
        return ntohs(value);
    }

    static size_t network_to_host(const std::uint32_t value)
    {
        return ntohl(value);
    }

    static void host_to_network(std::uint16_t &result, const size_t value)
    {
        result = htons(numeric_cast<std::uint16_t>(value));
    }

    static void host_to_network(std::uint32_t &result, const size_t value)
    {
        result = htonl(numeric_cast<std::uint32_t>(value));
    }

    bool declared_size_defined() const
    {
        return declared_size != SIZE_UNDEF;
    }

    void extract_size(Buffer &buf, const Frame::Context &frame_context)
    {
        const size_t size = read_size(buf);
        validate_size(size, frame_context);
        declared_size = size;
    }

    static bool size_defined(const Buffer &buf)
    {
        return buf.size() >= sizeof(SIZE_TYPE);
    }

    static size_t read_size(Buffer &buf)
    {
        SIZE_TYPE net_len;
        buf.read((unsigned char *)&net_len, sizeof(net_len));
        return network_to_host(net_len);
    }

    static void validate_size(const size_t size, const Frame::Context &frame_context)
    {
        // Don't validate upper bound on size if BufAllocFlags::GROW is set,
        // allowing it to range up to larger sizes.
        if (!size || (!(frame_context.buffer_flags() & BufAllocFlags::GROW) && size > frame_context.payload()))
            throw embedded_packet_size_error();
    }

    size_t declared_size = SIZE_UNDEF; // declared size of packet in leading SIZE_TYPE prefix
    BufferAllocated buffer;            // accumulated packet data
};

// In this variant of PacketStreamResidual, put()
// will absorb all residual data in buf, so that
// buf is always returned empty.
template <typename SIZE_TYPE>
class PacketStreamResidual
{
  public:
    void put(BufferAllocated &buf, const Frame::Context &frame_context)
    {
        if (residual.empty())
        {
            pktstream.put(buf, frame_context);
            residual = std::move(buf);
        }
        else
        {
            residual.append(buf);
            pktstream.put(residual, frame_context);
        }
        buf.reset_content();
    }

    void get(BufferAllocated &ret, const Frame::Context &frame_context)
    {
        pktstream.get(ret);
        if (!residual.empty())
            pktstream.put(residual, frame_context);
    }

    bool ready() const
    {
        return pktstream.ready();
    }

    static void prepend_size(Buffer &buf)
    {
        PacketStream<SIZE_TYPE>::prepend_size(buf);
    }

    // reset the object to default-initialized state
    void reset()
    {
        pktstream.reset();
        residual.clear();
    }

  private:
    PacketStream<SIZE_TYPE> pktstream;
    BufferAllocated residual;
};

} // namespace openvpn
