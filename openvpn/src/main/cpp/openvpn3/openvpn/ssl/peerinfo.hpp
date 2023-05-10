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

// These objects are primary concerned with generating the Peer Info on the
// client side before transmission to server.  For the reverse case (parsing
// the Peer Info on the server) we normally use an OptionList.

#ifndef OPENVPN_SSL_PEERINFO_H
#define OPENVPN_SSL_PEERINFO_H

#include <string>
#include <vector>

#include <openvpn/common/rc.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/file.hpp>
#include <openvpn/common/split.hpp>
#include <openvpn/common/unicode.hpp>
#include <openvpn/common/jsonlib.hpp>

#ifdef HAVE_JSON
#include <openvpn/common/jsonhelper.hpp>
#endif

namespace openvpn {
namespace PeerInfo {

OPENVPN_EXCEPTION(peer_info_error);

struct KeyValue
{
    KeyValue(const std::string &key_arg, const std::string &value_arg)
        : key(key_arg),
          value(value_arg)
    {
    }

    std::string key;
    std::string value;

    std::string to_string() const
    {
        return key + '=' + value;
    }
};

struct Set : public std::vector<KeyValue>, public RCCopyable<thread_unsafe_refcount>
{
    typedef RCPtr<Set> Ptr;

    template <typename SET>
    static Ptr new_from_foreign_set(const SET &other)
    {
        Ptr sp = new Set();
        for (const auto &kv : other)
            sp->emplace_back(kv.key, kv.value);
        return sp;
    }

    template <typename SET>
    void append_foreign_set_ptr(const SET *other)
    {
        if (other)
            for (const auto &kv : *other)
                emplace_back(kv.key, kv.value);
    }

    template <typename SET>
    void append_foreign_set_ref(const SET &other)
    {
        for (const auto &kv : other)
            emplace_back(kv.key, kv.value);
    }

    Ptr copy() const
    {
        return new Set(*this);
    }

    // src may be comma-separated key=value pairs or @filename, where
    // filename contains a JSON dictionary of key/value pairs.
    template <typename SET>
    static void parse_flexible(const std::string &src, SET &dest)
    {
        if (src.length() >= 1 && src[0] == '@')
        {
            const std::string fn = src.substr(1);
#ifdef OPENVPN_JSON_INTERNAL
            const Json::Value root = json::parse_from_file(fn);
            parse_json(root, dest, fn);
#else
            OPENVPN_THROW(peer_info_error, fn << ": JSON library not available");
#endif
        }
        else
            parse_csv(src, dest);
    }

    // Parse src in the form K1=V1,K2=V2,...
    template <typename SET>
    static void parse_csv(const std::string &src, SET &dest)
    {
        if (!string::is_empty(src))
        {
            if (string::is_multiline(src))
                OPENVPN_THROW(peer_info_error, "key/value list must be a single line: " << Unicode::utf8_printable(src, 256));
            const auto list = Split::by_char<std::vector<std::string>, StandardLex, Split::NullLimit>(src, ',', Split::TRIM_LEADING_SPACES | Split::TRIM_SPECIAL);
            for (const auto &kvstr : list)
            {
                const auto kv = Split::by_char<std::vector<std::string>, StandardLex, Split::NullLimit>(kvstr, '=', 0, 1);
                if (kv.size() == 2)
                    dest.emplace_back(kv[0], kv[1]);
                else
                    OPENVPN_THROW(peer_info_error, "key/value must be in the form K=V, not: " << Unicode::utf8_printable(kvstr, 256));
            }
        }
    }

#ifdef OPENVPN_JSON_INTERNAL
    template <typename SET>
    static void parse_json(const Json::Value &src, SET &dest, const std::string &title)
    {
        if (!src.isObject())
            OPENVPN_THROW(peer_info_error, title << ": top level JSON object must be a dictionary");
        auto m = src.map();
        for (auto &e : m)
        {
            if (e.second.isString())
                dest.emplace_back(e.first, e.second.asStringRef());
            else
                dest.emplace_back(e.first, e.second.toCompactString());
        }
    }
#endif

    std::string to_string() const
    {
        std::string ret;
        ret.reserve(256);
        for (const auto &kv : *this)
        {
            ret += kv.to_string();
            ret += '\n';
        }
        return ret;
    }
};

} // namespace PeerInfo
} // namespace openvpn

#endif
