//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2022 OpenVPN Inc.
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

// Define Frame classes.  These classes act as a factory for standard protocol
// buffers and also try to optimize the buffers for alignment.

#ifndef OPENVPN_FRAME_FRAME_H
#define OPENVPN_FRAME_FRAME_H

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/buffer/buffer.hpp>

namespace openvpn {

class Frame : public RC<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<Frame> Ptr;

    // Frame context types -- we maintain a Context object for each context type
    enum
    {
        ENCRYPT_WORK = 0,
        DECRYPT_WORK,
        COMPRESS_WORK,
        DECOMPRESS_WORK,
        READ_LINK_UDP,
        READ_LINK_TCP,
        READ_TUN,
        READ_BIO_MEMQ_DGRAM,
        READ_BIO_MEMQ_STREAM,
        READ_SSL_CLEARTEXT,
        WRITE_SSL_INIT,
        WRITE_SSL_CLEARTEXT,
        WRITE_ACK_STANDALONE,
        WRITE_DC_MSG,
        WRITE_HTTP,
        READ_HTTP,

        N_ALIGN_CONTEXTS
    };

    OPENVPN_SIMPLE_EXCEPTION(frame_context_index);

    // We manage an array of Context objects, one for each
    // Frame context above.
    class Context
    {
      public:
        Context()
        {
            headroom_ = 0;
            payload_ = 0;
            tailroom_ = 0;
            buffer_flags_ = 0;
            align_adjust_ = 0;
            align_block_ = sizeof(std::size_t);
            adj_headroom_ = 0;
            adj_capacity_ = 0;
        }

        Context(const size_t headroom,
                const size_t payload,
                const size_t tailroom,
                const size_t align_adjust,       // length of leading prefix data before the data that needs to be aligned on a size_t boundary
                const size_t align_block,        // size of alignment block, usually sizeof(size_t) but sometimes the cipher block size
                const unsigned int buffer_flags) // flags passed to BufferAllocated constructor
        {
            headroom_ = headroom;
            payload_ = payload;
            tailroom_ = tailroom;
            buffer_flags_ = buffer_flags;
            align_adjust_ = align_adjust;
            align_block_ = align_block;
            recalc_derived();
        }

        void reset_align_adjust(const size_t align_adjust)
        {
            align_adjust_ = align_adjust;
        }

        size_t headroom() const
        {
            return adj_headroom_;
        }
        size_t payload() const
        {
            return payload_;
        }
        size_t tailroom() const
        {
            return tailroom_;
        }
        size_t capacity() const
        {
            return adj_capacity_;
        }
        unsigned int buffer_flags() const
        {
            return buffer_flags_;
        }

        // Calculate a starting offset into a buffer object, dealing with
        // headroom and alignment issues.
        size_t prepare(Buffer &buf) const
        {
            buf.reset(capacity(), buffer_flags());
            buf.init_headroom(actual_headroom(buf.c_data_raw()));
            return payload();
        }

        // Allocated a new prepared buffer
        BufferAllocated alloc() const
        {
            BufferAllocated buf;
            prepare(buf);
            return buf;
        }

        // Realign a buffer to headroom
        void realign(Buffer &buf) const
        {
            buf.realign(actual_headroom(buf.c_data_raw()));
        }

        // Return a new BufferAllocated object initialized with the given data.
        BufferPtr copy(const unsigned char *data, const size_t size) const
        {
            const size_t cap = size + headroom() + tailroom();
            BufferPtr b = new BufferAllocated(cap, buffer_flags());
            b->init_headroom(actual_headroom(b->c_data_raw()));
            b->write(data, size);
            return b;
        }

        // Return a new BufferAllocated object by value initialized with the given data.
        BufferAllocated copy_by_value(const unsigned char *data, const size_t size) const
        {
            const size_t cap = size + headroom() + tailroom();
            BufferAllocated b(cap, buffer_flags());
            b.init_headroom(actual_headroom(b.c_data_raw()));
            b.write(data, size);
            return b;
        }

        // Return a new BufferAllocated object initialized with
        // the data in given buffer.  buf may be empty or undefined.
        BufferPtr copy(const BufferPtr &buf) const
        {
            const size_t size = buf ? buf->size() : 0;
            const size_t cap = size + headroom() + tailroom();
            BufferPtr b = new BufferAllocated(cap, buffer_flags());
            b->init_headroom(actual_headroom(b->c_data_raw()));
            if (size)
                b->write(buf->c_data(), size);
            return b;
        }

        // How much payload space left in buffer
        size_t remaining_payload(const Buffer &buf) const
        {
            if (payload() > buf.size())
                return payload() - buf.size();
            else
                return 0;
        }

        // Used to set the capacity of a group of Context objects
        // to the highest capacity of any one of the members.
        void standardize_capacity(const size_t newcap)
        {
            if (newcap > adj_capacity_)
                adj_capacity_ = newcap;
        }

#ifndef OPENVPN_NO_IO
        // return a openvpn_io::mutable_buffer object used by
        // asio read methods.
        openvpn_io::mutable_buffer mutable_buffer(Buffer &buf) const
        {
            return openvpn_io::mutable_buffer(buf.data(), remaining_payload(buf));
        }

        // clamped version of mutable_buffer
        openvpn_io::mutable_buffer mutable_buffer_clamp(Buffer &buf) const
        {
            return openvpn_io::mutable_buffer(buf.data(), buf_clamp_read(remaining_payload(buf)));
        }
#endif

        std::string info() const
        {
            std::ostringstream info;
            info << "head=" << headroom_ << "[" << adj_headroom_ << "] "
                 << "pay=" << payload_ << ' '
                 << "tail=" << tailroom_ << ' '
                 << "cap=" << adj_capacity_ << ' '
                 << "bf=" << buffer_flags_ << ' '
                 << "align_adj=" << align_adjust_ << ' '
                 << "align_block=" << align_block_;
            return info.str();
        }

      private:
        // recalculate derived values when object parameters are modified
        void recalc_derived()
        {
            // calculate adjusted headroom due to worst-case alignment loss
            adj_headroom_ = headroom_ + align_block_;

            // calculate capacity
            adj_capacity_ = adj_headroom_ + payload_ + tailroom_;
        }

        // add a small delta ( < align_block) to headroom so that the point
        // after the first align_adjust bytes of the buffer starting at base
        // will be aligned on an align_block boundary
        size_t actual_headroom(const void *base) const
        {
            return headroom_ + (-(size_t(base) + headroom_ + align_adjust_) & (align_block_ - 1));
        }

        // parameters
        size_t headroom_;
        size_t payload_;
        size_t tailroom_;
        size_t align_adjust_;
        size_t align_block_;
        unsigned int buffer_flags_;

        // derived
        size_t adj_headroom_;
        size_t adj_capacity_;
    };

    Frame()
    {
    }

    explicit Frame(const Context &c)
    {
        set_default_context(c);
    }

    // set the default context
    void set_default_context(const Context &c)
    {
        for (int i = 0; i < N_ALIGN_CONTEXTS; ++i)
            contexts[i] = c;
    }

    // Calculate a starting offset into a buffer object, dealing with
    // headroom and alignment issues.  context should be one of
    // the context types above.  Returns payload size of buffer.
    size_t prepare(const unsigned int context, Buffer &buf) const
    {
        return (*this)[context].prepare(buf);
    }

    BufferPtr prepare(const unsigned int context) const
    {
        BufferPtr buf(new BufferAllocated());
        prepare(context, *buf);
        return buf;
    }

    size_t n_contexts() const
    {
        return N_ALIGN_CONTEXTS;
    }

    Context &operator[](const size_t i)
    {
        if (i >= N_ALIGN_CONTEXTS)
            throw frame_context_index();
        return contexts[i];
    }

    const Context &operator[](const size_t i) const
    {
        if (i >= N_ALIGN_CONTEXTS)
            throw frame_context_index();
        return contexts[i];
    }

    void standardize_capacity(const unsigned int context_mask)
    {
        size_t i;
        size_t max_cap = 0;
        unsigned int mask = context_mask;

        // find the largest capacity in the group
        for (i = 0; i < N_ALIGN_CONTEXTS; ++i)
        {
            if (mask & 1)
            {
                const size_t cap = contexts[i].capacity();
                if (cap > max_cap)
                    max_cap = cap;
            }
            mask >>= 1;
        }

        // set all members of group to largest capacity found
        mask = context_mask;
        for (i = 0; i < N_ALIGN_CONTEXTS; ++i)
        {
            if (mask & 1)
                contexts[i].standardize_capacity(max_cap);
            mask >>= 1;
        }
    }

  private:
    Context contexts[N_ALIGN_CONTEXTS];
};

} // namespace openvpn

#endif // OPENVPN_FRAME_FRAME_H
