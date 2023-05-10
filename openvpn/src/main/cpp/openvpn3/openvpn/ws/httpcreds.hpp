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

#pragma once

#include <string>
#include <vector>
#include <atomic>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/file.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/base64.hpp>
#include <openvpn/common/splitlines.hpp>
#include <openvpn/common/strneq.hpp>
#include <openvpn/common/unicode.hpp>
#include <openvpn/common/userpass.hpp>
#include <openvpn/common/platform.hpp>
#include <openvpn/common/writeprivate.hpp>
#include <openvpn/http/header.hpp>

namespace openvpn {
namespace WS {
struct Creds
{
    OPENVPN_EXCEPTION(web_creds_error);

    static Creds load_from_header(const HTTP::HeaderList &headlist,
                                  const bool password_required,
                                  const bool throw_on_error)
    {
        Creds ret;
        try
        {
            // Authorization: Basic Zm9vOmJhcg==
            for (auto &h : headlist)
            {
                if (string::strcasecmp(h.name, "authorization") == 0
                    && h.value.length() >= 7
                    && string::strcasecmp(h.value.substr(0, 6), "basic ") == 0)
                {
                    const std::string creds = base64->decode(h.value.substr(6));
                    const auto cv = string::split(creds, ':', 1);
                    if (cv.size() != 2)
                        throw Exception("error splitting credentials");
                    if (!Unicode::is_valid_utf8(cv[0]))
                        throw Exception("username not UTF-8");
                    if (!Unicode::is_valid_utf8(cv[1]))
                        throw Exception("password not UTF-8");
                    if (cv[0].empty())
                        throw Exception("username empty");
                    if (password_required && cv[1].empty())
                        throw Exception("password empty");
                    ret.username = cv[0];
                    ret.password = cv[1];
                    break;
                }
            }
        }
        catch (const std::exception &e)
        {
            if (throw_on_error)
                throw web_creds_error(e.what());
        }
        return ret;
    }

    static Creds load_from_file(const std::string &fn,
                                const bool password_required,
                                const bool throw_on_error)
    {
        Creds ret;
        try
        {
            const std::string content = read_text_utf8(fn);
            SplitLines sl(content, 1024);
            std::string u, p;
            if (sl.next(u) != SplitLines::S_OKAY)
                throw Exception(fn + " : username missing");
            if (sl.next(p) != SplitLines::S_OKAY)
                throw Exception(fn + " : password missing");
            if (u.empty())
                throw Exception(fn + " : username empty");
            if (password_required && p.empty())
                throw Exception(fn + " : password empty");
            ret.username = std::move(u);
            ret.password = std::move(p);
        }
        catch (const std::exception &e)
        {
            if (throw_on_error)
                throw web_creds_error(e.what());
        }
        return ret;
    }

    static Creds load_from_options(const OptionList &opt,
                                   const std::string &opt_name,
                                   const unsigned int flags)
    {
        Creds ret;
        UserPass::parse(opt, opt_name, flags, ret.username, ret.password);
        return ret;
    }

    bool defined() const
    {
        return !username.empty();
    }

    bool defined_full() const
    {
        return !username.empty() && !password.empty();
    }

    void save_to_file(const std::string &fn) const
    {
        write_private(fn, username + '\n' + password + '\n');
    }

    bool operator==(const Creds &rhs) const
    {
        return !operator!=(rhs);
    }

    bool operator!=(const Creds &rhs) const
    {
        bool neq = crypto::str_neq(username, rhs.username);
        atomic_thread_fence(std::memory_order_acq_rel);
        neq |= crypto::str_neq(password, rhs.password);
        atomic_thread_fence(std::memory_order_acq_rel);
        return neq;
    }

    bool password_eq(const Creds &rhs) const
    {
        bool neq = crypto::str_neq(password, rhs.password);
        atomic_thread_fence(std::memory_order_acq_rel);
        return !neq;
    }

    std::string to_string() const
    {
        return username + '/' + password;
    }

    std::string username;
    std::string password;
};
} // namespace WS
} // namespace openvpn
