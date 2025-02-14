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

#ifndef OPENVPN_COMMON_ENUMDIR_H
#define OPENVPN_COMMON_ENUMDIR_H

#include <sys/types.h>
#include <dirent.h>

#include <string>
#include <vector>
#include <utility>
#include <memory>
#include <algorithm>
#include <functional>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/uniqueptr.hpp>

namespace openvpn {
OPENVPN_EXCEPTION(enum_dir_error);

template <typename F>
inline bool enum_dir(const std::string &dirname,
                     F func)
{
    unique_ptr_del<DIR> dir(::opendir(dirname.c_str()), [](DIR *d)
                            { ::closedir(d); });
    if (!dir)
        return false;

    struct dirent *e;
    while ((e = ::readdir(dir.get())) != nullptr)
    {
        std::string fn(e->d_name);
        if (fn != "." && fn != "..")
            std::invoke(func, std::move(fn));
    }
    return true;
}

inline std::vector<std::string> enum_dir(const std::string &dirname,
                                         const size_t size_hint = 0,
                                         const bool sort = false)
{
    std::vector<std::string> ret;
    if (size_hint)
        ret.reserve(size_hint);

    if (!enum_dir(dirname, [&ret](std::string fn)
                  { ret.push_back(std::move(fn)); }))
        throw enum_dir_error(dirname + ": cannot open directory");

    if (sort)
        std::sort(ret.begin(), ret.end());

    return ret;
}

} // namespace openvpn

#endif
