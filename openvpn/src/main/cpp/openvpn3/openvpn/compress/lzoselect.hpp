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

// If we are linked with the LZO library, use it.  Otherwise default
// to an intrinstic LZO implementation that only handles decompression.

#ifndef OPENVPN_COMPRESS_LZOSELECT_H
#define OPENVPN_COMPRESS_LZOSELECT_H

#if defined(HAVE_LZO)
#include <openvpn/compress/lzo.hpp>
#else
#include <openvpn/compress/lzoasym.hpp>
#endif

namespace openvpn {
#if !defined(HAVE_LZO)
typedef CompressLZOAsym CompressLZO;
#endif
} // namespace openvpn

#endif
