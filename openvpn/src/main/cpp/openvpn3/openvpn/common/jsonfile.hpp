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

#include <openvpn/common/jsonhelper.hpp>
#include <openvpn/common/fileatomic.hpp>

namespace openvpn::json {

inline Json::Value read_fast(const std::string &fn,
                             const bool optional = true,
                             std::uint64_t *mtime_ns = nullptr)
{
    BufferPtr bp = read_binary_unix(fn, 0, optional ? NULL_ON_ENOENT : 0, mtime_ns);
    if (!bp || bp->empty())
        return Json::Value();
    return parse_from_buffer(*bp, fn);
}

inline Json::Value read_fast_dict(const std::string &fn,
                                  const bool optional = true,
                                  std::uint64_t *mtime_ns = nullptr)
{
    Json::Value jret = read_fast(fn, optional, mtime_ns);
    if (!jret)
        return jret;
    if (!jret.isObject())
        throw json_parse("read_fast_dict: json file " + fn + " does not contain a top-level dictionary");
    return jret;
}

inline void write_atomic(const std::string &fn,
                         const std::string &tmpdir,
                         const mode_t mode,
                         const std::uint64_t mtime_ns, // set explicit modification-time in nanoseconds since epoch, or 0 to defer to system
                         const Json::Value &root,
                         const size_t size_hint,
                         StrongRandomAPI &rng)
{
    auto bp = BufferAllocatedRc::Create(size_hint, BufAllocFlags::GROW);
    format_compact(root, *bp);
    write_binary_atomic(fn, tmpdir, mode, mtime_ns, *bp, rng);
}

inline void write_fast(const std::string &fn,
                       const mode_t mode,
                       const std::uint64_t mtime_ns, // set explicit modification-time in nanoseconds since epoch, or 0 to defer to system
                       const Json::Value &root,
                       const size_t size_hint)
{
    auto bp = BufferAllocatedRc::Create(size_hint, BufAllocFlags::GROW);
    format_compact(root, *bp);
    write_binary_unix(fn, mode, mtime_ns, *bp);
}
} // namespace openvpn::json
