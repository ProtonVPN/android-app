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
#include <map>
#include <set>
#include <tuple>
#include <memory>
#include <utility>

#include <openssl/ssl.h>

#include <openvpn/common/rc.hpp>
#include <openvpn/common/msfind.hpp>

namespace openvpn {

// Client-side session cache.
// (We don't cache server-side sessions because we use TLS
// session resumption tickets which are stateless on the server).
class OpenSSLSessionCache : public RC<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<OpenSSLSessionCache> Ptr;

    OPENVPN_EXCEPTION(openssl_sess_cache_error);

    // Wrapper for OpenSSL SSL_SESSION pointers that manages reference counts.
    class Session
    {
      public:
        Session(::SSL_SESSION *sess) // caller must pre-increment refcount on sess
            : sess_(sess)
        {
        }

        Session(Session &&other) noexcept
        {
            sess_ = other.sess_;
            other.sess_ = nullptr;
        }

        Session &operator=(Session &&other) noexcept
        {
            if (sess_)
                ::SSL_SESSION_free(sess_);
            sess_ = other.sess_;
            other.sess_ = nullptr;
            return *this;
        }

        ::SSL_SESSION *openssl_session() const
        {
            return sess_;
        }

        bool operator<(const Session &rhs) const // used when Session is a std::set key
        {
            return sess_ < rhs.sess_;
        }

        explicit operator bool() const
        {
            return sess_ != nullptr;
        }

        ~Session()
        {
            if (sess_)
                ::SSL_SESSION_free(sess_);
        }

      private:
        // These methods are deleted because we have no way to increment
        // an SSL_SESSION refcount until OpenSSL 1.1.
        Session(const Session &) = delete;
        Session &operator=(const Session &) = delete;

        ::SSL_SESSION *sess_;
    };

    class Key
    {
      public:
        typedef std::unique_ptr<Key> UPtr;

        Key(const std::string &key_arg,
            OpenSSLSessionCache::Ptr cache_arg)
            : key(key_arg),
              cache(std::move(cache_arg))
        {
            // OPENVPN_LOG("OpenSSLSessionCache::Key CONSTRUCT key=" << key);
        }

        void commit(::SSL_SESSION *sess)
        {
            if (!sess)
                return;
            auto mi = MSF::find(cache->map, key);
            if (mi)
            {
                /* auto ins = */ mi->second.emplace(sess);
                // OPENVPN_LOG("OpenSSLSessionCache::Key::commit ADD=" << ins.second << " key=" << key);
            }
            else
            {
                // OPENVPN_LOG("OpenSSLSessionCache::Key::commit CREATE key=" << key);
                auto ins = cache->map.emplace(std::piecewise_construct,
                                              std::forward_as_tuple(key),
                                              std::forward_as_tuple());
                ins.first->second.emplace(sess);
            }
        }

      private:
        const std::string key;
        OpenSSLSessionCache::Ptr cache;
    };

    // Remove a session from the map after calling func() on it.
    // This would be a lot cleaner if we had C++17 std::set::extract().
    template <typename FUNC>
    void extract(const std::string &key, FUNC func)
    {
        auto mi = MSF::find(map, key);
        if (mi)
        {
            // OPENVPN_LOG("OpenSSLSessionCache::Key::lookup EXISTS key=" << key);
            SessionSet &ss = mi->second;
            if (ss.empty())
                throw openssl_sess_cache_error("internal error: SessionSet is empty");
            auto ssi = ss.begin();
            try
            {
                func(ssi->openssl_session());
            }
            catch (...)
            {
                remove_session(mi, ss, ssi);
                throw;
            }
            remove_session(mi, ss, ssi);
        }
        else
        {
            // OPENVPN_LOG("OpenSSLSessionCache::Key::lookup NOT_FOUND key=" << key);
        }
    }

  private:
    struct SessionSet : public std::set<Session>
    {
    };

    typedef std::map<std::string, SessionSet> Map;

    void remove_session(Map::iterator mi, SessionSet &ss, SessionSet::iterator ssi)
    {
        ss.erase(ssi);
        if (ss.empty())
            map.erase(mi);
    }

    Map map;
};

} // namespace openvpn
