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

#ifndef OPENVPN_BUFFER_BUFCOMPOSED_H
#define OPENVPN_BUFFER_BUFCOMPOSED_H

#include <openvpn/common/exception.hpp>
#include <openvpn/buffer/bufcomplete.hpp>
#include <openvpn/buffer/buflist.hpp>

namespace openvpn {
class BufferComposed
{
  public:
    class Complete : public BufferComplete
    {
      public:
        BufferPtr get()
        {
#if 0 // don't include for production
	if (iter_defined())
	  throw Exception("BufferComposed::Complete: residual data");
#endif
            BufferPtr ret = bc.bv.join();
            bc.bv.clear();
            return ret;
        }

      private:
        friend class BufferComposed;

        Complete(BufferComposed &bc_arg)
            : bc(bc_arg),
              iter(bc.bv.cbegin())
        {
            next_buffer_impl();
        }

        bool iter_defined()
        {
            return iter != bc.bv.end();
        }

        virtual void next_buffer() override
        {
            next_buffer_impl();
        }

        // Both ctor and next_buffer delegate here
        void next_buffer_impl()
        {
            if (iter_defined())
                reset_buf(**iter++);
            else
                reset_buf();
        }

        BufferComposed &bc;
        BufferVector::const_iterator iter;
    };

    size_t size() const
    {
        return bv.join_size();
    }

    void put(BufferPtr bp)
    {
        bv.push_back(std::move(bp));
    }

    Complete complete()
    {
        return Complete(*this);
    }

  private:
    BufferVector bv;
};
} // namespace openvpn

#endif
