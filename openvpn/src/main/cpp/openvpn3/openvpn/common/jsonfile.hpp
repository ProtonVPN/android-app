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

#pragma once

#include <openvpn/common/jsonhelper.hpp>
#include <openvpn/common/fileatomic.hpp>

namespace openvpn {
  namespace json {

    inline Json::Value read_fast(const std::string& fn,
				 const bool optional=true,
				 std::uint64_t* mtime_ns=nullptr)
    {
      BufferPtr bp = read_binary_unix(fn, 0, optional ? NULL_ON_ENOENT : 0, mtime_ns);
      if (!bp || bp->empty())
	return Json::Value();
      return parse_from_buffer(*bp, fn);
    }

    inline void write_atomic(const std::string& fn,
			     const std::string& tmpdir,
			     const mode_t mode,
			     const std::uint64_t mtime_ns,  // set explicit modification-time in nanoseconds since epoch, or 0 to defer to system
			     const Json::Value& root,
			     const size_t size_hint,
			     RandomAPI& rng)
    {
      BufferPtr bp = new BufferAllocated(size_hint, BufferAllocated::GROW);
      format_compact(root, *bp);
      write_binary_atomic(fn, tmpdir, mode, mtime_ns, *bp, rng);
    }

    inline void write_fast(const std::string& fn,
			   const mode_t mode,
			   const std::uint64_t mtime_ns,  // set explicit modification-time in nanoseconds since epoch, or 0 to defer to system
			   const Json::Value& root,
			   const size_t size_hint)
    {
      BufferPtr bp = new BufferAllocated(size_hint, BufferAllocated::GROW);
      format_compact(root, *bp);
      write_binary_unix(fn, mode, mtime_ns, *bp);
    }
  }
}
