//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2017 OpenVPN Inc.
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

// A null compression class.

#ifndef OPENVPN_COMPRESS_COMPNULL_H
#define OPENVPN_COMPRESS_COMPNULL_H

namespace openvpn {

  class CompressNull : public Compress
  {
  public:
    CompressNull(const Frame::Ptr& frame, const SessionStats::Ptr& stats)
      : Compress(frame, stats)
    {
    }

    virtual const char *name() const { return "null"; }
    virtual void compress(BufferAllocated& buf, const bool hint) {}
    virtual void decompress(BufferAllocated& buf) {}
  };

} // namespace openvpn

#endif // OPENVPN_COMPRESS_COMPNULL_H
