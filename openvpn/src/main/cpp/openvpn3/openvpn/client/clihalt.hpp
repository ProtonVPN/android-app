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

#ifndef OPENVPN_CLIENT_CLIHALT_H
#define OPENVPN_CLIENT_CLIHALT_H

#include <string>
#include <sstream>
#include <vector>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/split.hpp>
#include <openvpn/common/unicode.hpp>
#include <openvpn/common/string.hpp>

// Process halt/restart messages from server:
//   HALT,<client_reason>        -> disconnect
//   RESTART,<client_reason>     -> restart with reason, don't preserve session ID
//   RESTART,[P]:<client_reason> -> restart with reason, do preserve session ID

namespace openvpn {
class ClientHalt
{
    typedef std::vector<std::string> StringList;

  public:
    OPENVPN_SIMPLE_EXCEPTION(client_halt_error);

    ClientHalt(const std::string &msg, const bool unicode_filter)
    {
        // get operator (halt or restart)
        StringList sl;
        parse_msg(sl, msg);
        if (is_halt(sl))
            ;
        else if (is_restart(sl))
            restart_ = true;
        else
            throw client_halt_error();

        // get flags and reason
        if (sl.size() >= 2)
        {
            size_t reason_pos = 0;
            if (restart_ && string::starts_with(sl[1], "[P]:"))
            {
                psid_ = true;
                reason_pos = 4;
            }
            reason_ = sl[1].substr(reason_pos);
            if (unicode_filter)
                reason_ = Unicode::utf8_printable(reason_, 256);
        }
    }

    static bool match(const std::string &msg)
    {
        StringList sl;
        parse_msg(sl, msg);
        return is_halt(sl) || is_restart(sl);
    }

    // returns true for restart, false for halt
    bool restart() const
    {
        return restart_;
    }

    // returns true if session ID should be preserved
    bool psid() const
    {
        return psid_;
    }

    // returns user-visible reason string
    const std::string &reason() const
    {
        return reason_;
    }

    std::string render() const
    {
        std::ostringstream os;
        os << (restart_ ? "RESTART" : "HALT") << " psid=" << psid_ << " reason='" << reason_ << '\'';
        return os.str();
    }

  private:
    static void parse_msg(StringList &sl, const std::string &msg)
    {
        sl.reserve(2);
        Split::by_char_void<StringList, NullLex, Split::NullLimit>(sl, msg, ',', 0, 1);
    }

    static bool is_halt(const StringList &sl)
    {
        return sl.size() >= 1 && sl[0] == "HALT";
    }

    static bool is_restart(const StringList &sl)
    {
        return sl.size() >= 1 && sl[0] == "RESTART";
    }

    bool restart_ = false;
    bool psid_ = false;
    std::string reason_;
};
} // namespace openvpn

#endif
