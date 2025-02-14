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

#include <openvpn/common/exception.hpp>
#include <openvpn/common/lex.hpp>
#include <openvpn/buffer/buffer.hpp>

namespace openvpn {

/**
 *  Class PushLex is used to parse out the individual options
 *  in a comma-separated push options list.
 */
class PushLex
{
  public:
    OPENVPN_EXCEPTION(pushlex_error);

    /**
     *  Setup parsing iterator for comma-separated push options list.
     *
     *  @param buf Contains the comma-separated push options list.
     *  @param discard_prefix If the list begins with a "PUSH_REPLY"
     *         or "PUSH_UPDATE" prefix, discard the prefix.
     */
    PushLex(const ConstBuffer &buf, const bool discard_prefix)
        : buf_(buf)
    {
        if (!discard_prefix || !defined())
            return;
        if (!string::starts_with(buf_, "PUSH_"))
            throw pushlex_error("not a valid PUSH_x message [1]");
        buf_.advance(5);
        while (defined())
        {
            const char c = buf_.pop_front();
            if (c == ',')
                return;
            if (c >= 'A' && c <= 'Z')
                continue;
        }
        throw pushlex_error("not a valid PUSH_x message [2]");
    }

    /**
     *  Use in iterator loop to determine if more options are
     *  still available to be fetched with next().
     *
     *  @return true if next() may be called to return next option.
     */
    bool defined() const
    {
        return !buf_.empty();
    }

    /**
     *  Use in iterator loop to fetch next option.
     *
     *  @return next option.
     */
    std::string next()
    {
        StandardLex lex;
        std::string ret;
        while (defined())
        {
            const char c = buf_.pop_front();
            lex.put(c);
            if (lex.get() == ',' && !(lex.in_quote() || lex.in_backslash()))
                return ret;
            ret += c;
        }
        return ret;
    }

  private:
    ConstBuffer buf_;
};

} // namespace openvpn
