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

      Complete(BufferComposed& bc_arg)
	: bc(bc_arg),
	  iter(bc.bv.cbegin())
      {
	next_buffer();
      }

      bool iter_defined()
      {
	return iter != bc.bv.end();
      }

      virtual void next_buffer() override
      {
	if (iter_defined())
	  reset_buf(**iter++);
	else
	  reset_buf();
      }

      BufferComposed& bc;
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
}

#endif
