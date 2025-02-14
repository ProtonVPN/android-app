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

// Atomic file-handling methods.

#pragma once

#include <openvpn/common/platform.hpp>

#if defined(OPENVPN_PLATFORM_WIN)
#error atomic file methods not supported on Windows
#endif

#include <stdio.h>  // for rename()
#include <unistd.h> // for unlink()
#include <errno.h>
#include <cstring>

#include <openvpn/common/file.hpp>
#include <openvpn/common/fileunix.hpp>
#include <openvpn/common/strerror.hpp>
#include <openvpn/common/tmpfilename.hpp>

namespace openvpn {
// Atomically write binary buffer to file (relies on
// the atomicity of rename())
inline void write_binary_atomic(const std::string &fn,
                                const std::string &tmpdir,
                                const mode_t mode,
                                const std::uint64_t mtime_ns, // set explicit modification-time in nanoseconds since epoch, or 0 to defer to system
                                const ConstBuffer &buf,
                                StrongRandomAPI &rng)
{
    // generate temporary filename
    const std::string tfn = tmp_filename(fn, tmpdir, rng);

    // write to temporary file
    write_binary_unix(tfn, mode, mtime_ns, buf);

    // then move into position
    if (::rename(tfn.c_str(), fn.c_str()) == -1)
    {
        const int eno = errno;
        ::unlink(tfn.c_str()); // move failed, so delete the temporary file
        OPENVPN_THROW(file_unix_error, "error moving '" << tfn << "' -> '" << fn << "' : " << strerror_str(eno));
    }
}

inline void write_binary_atomic(const std::string &fn,
                                const std::string &tmpdir,
                                const mode_t mode,
                                const std::uint64_t mtime_ns,
                                const Buffer &buf,
                                StrongRandomAPI &rng)
{
    write_binary_atomic(fn, tmpdir, mode, mtime_ns, const_buffer_ref(buf), rng);
}
} // namespace openvpn
