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
