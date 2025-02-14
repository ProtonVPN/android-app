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

#ifndef OPENVPN_LINUX_DAEMON_ALIVE_H
#define OPENVPN_LINUX_DAEMON_ALIVE_H

#include <openvpn/common/file.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/number.hpp>

namespace openvpn {
inline int daemon_pid(const std::string &cmd,
                      const std::string &pidfile)
{
    try
    {
        std::string pidstr = read_text(pidfile);
        string::trim_crlf(pidstr);
        const std::string cmdline_fn = "/proc/" + pidstr + "/cmdline";
        BufferPtr cmdbuf = read_binary_linear(cmdline_fn);
        const size_t len = ::strnlen((const char *)cmdbuf->c_data(), cmdbuf->size());
        if (cmd == std::string((const char *)cmdbuf->c_data(), len))
        {
            int ret;
            if (parse_number(pidstr, ret))
                return ret;
        }
    }
    catch (const std::exception &e)
    {
    }
    return -1;
}

inline bool is_daemon_alive(const std::string &cmd,
                            const std::string &pidfile)
{
    return daemon_pid(cmd, pidfile) >= 0;
}
} // namespace openvpn

#endif
