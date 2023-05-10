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

#ifndef OPENVPN_LOG_LOGBASE_H
#define OPENVPN_LOG_LOGBASE_H

#include <string>

#include <openvpn/common/rc.hpp>

#define OPENVPN_LOG_CLASS openvpn::LogBase
#define OPENVPN_LOG_INFO(x) x

namespace openvpn {

#ifdef OPENVPN_LOGBASE_NO_RC

struct LogBase
{
    virtual void log(const std::string &str) = 0;
};

#else

struct LogBase : public RC<thread_safe_refcount>
{
    typedef RCPtr<LogBase> Ptr;
    virtual void log(const std::string &str) = 0;
};

#endif
} // namespace openvpn

#include <openvpn/log/logthread.hpp>

#endif
