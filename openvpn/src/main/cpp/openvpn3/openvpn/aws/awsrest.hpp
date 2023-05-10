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

// AWS REST API query utilities such as query signing

#pragma once

#include <string>
#include <vector>
#include <cstdint> // for std::uint8_t
#include <algorithm>
#include <utility>
#include <time.h>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/hexstr.hpp>
#include <openvpn/http/urlencode.hpp>
#include <openvpn/crypto/digestapi.hpp>
#include <openvpn/aws/awscreds.hpp>

namespace openvpn {
namespace AWS {
class REST
{
  public:
    OPENVPN_EXCEPTION(aws_rest_error);

    // 20130524T000000Z
    static std::string amz_date()
    {
        struct tm lt;
        char buf[64];
        const time_t t = ::time(nullptr);
        if (!::gmtime_r(&t, &lt))
            throw aws_rest_error("gmtime_r failed");
        if (!::strftime(buf, sizeof(buf), "%Y%m%dT%H%M%SZ", &lt))
            throw aws_rest_error("strftime failed");
        return std::string(buf);
    }

    struct SHA256
    {
        std::string to_hex() const
        {
            return render_hex(hash, sizeof(hash));
        }

        std::uint8_t hash[32];
    };

    static SHA256 hmac_sha256(DigestFactory &digest_factory, const std::string &data, const std::string &key)
    {
        SHA256 ret;
        HMACInstance::Ptr hi(digest_factory.new_hmac(CryptoAlgs::SHA256, (const std::uint8_t *)key.c_str(), key.length()));
        hi->update((const std::uint8_t *)data.c_str(), data.length());
        hi->final(ret.hash);
        return ret;
    }

    static SHA256 hmac_sha256(DigestFactory &digest_factory, const std::string &data, const SHA256 &key)
    {
        SHA256 ret;
        HMACInstance::Ptr hi(digest_factory.new_hmac(CryptoAlgs::SHA256, key.hash, sizeof(key.hash)));
        hi->update((const std::uint8_t *)data.c_str(), data.length());
        hi->final(ret.hash);
        return ret;
    }

    static SHA256 sha256(DigestFactory &digest_factory, const std::string &data)
    {
        SHA256 ret;
        DigestInstance::Ptr di(digest_factory.new_digest(CryptoAlgs::SHA256));
        di->update((const std::uint8_t *)data.c_str(), data.length());
        di->final(ret.hash);
        return ret;
    }

    static SHA256 signing_key(DigestFactory &df,
                              const std::string &key,
                              const std::string &date_stamp,
                              const std::string &region_name,
                              const std::string &service_name)
    {
        const SHA256 h1 = hmac_sha256(df, date_stamp, "AWS4" + key);
        const SHA256 h2 = hmac_sha256(df, region_name, h1);
        const SHA256 h3 = hmac_sha256(df, service_name, h2);
        const SHA256 h4 = hmac_sha256(df, "aws4_request", h3);
        return h4;
    }

    struct KeyValue
    {
        KeyValue(std::string key_arg, std::string value_arg)
            : key(std::move(key_arg)),
              value(std::move(value_arg))
        {
        }

        bool operator<(const KeyValue &rhs) const
        {
            return key < rhs.key;
        }

        std::string uri_encode() const
        {
            return URL::encode(key) + '=' + URL::encode(value);
        }

        std::string key;
        std::string value;
    };

    struct Query : public std::vector<KeyValue>
    {
        std::string canonical_query_string() const
        {
            bool first = true;
            std::string ret;
            for (auto &p : *this)
            {
                if (!first)
                    ret += '&';
                ret += p.uri_encode();
                first = false;
            }
            return ret;
        }

        void sort()
        {
            std::sort(begin(), end());
        }
    };

    struct QueryBuilder
    {
        std::string date;           // such as "20130524T000000Z"
        unsigned int expires = 300; // request expiration in seconds
        std::string region;         // such as "us-east-1"
        std::string service;        // such as "s3"
        std::string method;         // such as "GET"
        std::string host;           // such as "ec2.us-west-2.amazonaws.com"
        std::string uri;            // such as "/"
        Query parms;

        std::string uri_query() const
        {
            return uri + '?' + parms.canonical_query_string();
        }

        std::string url_query() const
        {
            return "https://" + host + uri_query();
        }

        void add_amz_parms(const Creds &creds)
        {
            parms.emplace_back("X-Amz-Algorithm", "AWS4-HMAC-SHA256");
            parms.emplace_back("X-Amz-Credential", creds.access_key + '/' + amz_credential());
            parms.emplace_back("X-Amz-Date", date);
            parms.emplace_back("X-Amz-Expires", std::to_string(expires));
            parms.emplace_back("X-Amz-SignedHeaders", amz_signed_headers());

            if (!creds.token.empty())
                parms.emplace_back("X-Amz-Security-Token", creds.token);
        }

        void sort_parms()
        {
            parms.sort();
        }

        void add_amz_signature(DigestFactory &digest_factory, const Creds &creds)
        {
            parms.emplace_back("X-Amz-Signature", signature(digest_factory, creds));
        }

        std::string signature(DigestFactory &digest_factory, const Creds &creds) const
        {
            const SHA256 sk = signing_key(digest_factory,
                                          creds.secret_key,
                                          date.substr(0, 8),
                                          region,
                                          service);
            return hmac_sha256(digest_factory, string_to_sign(digest_factory), sk).to_hex();
        }

        virtual std::string content_hash() const
        {
            // SHA256 of empty string
            return "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        }

        std::string canonical_request() const
        {
            std::string ret = method + '\n'
                              + uri + '\n'
                              + parms.canonical_query_string() + '\n'
                              + "host:" + host + '\n'
                              + '\n'
                              + amz_signed_headers() + '\n';
            if (service == "s3")
                ret += "UNSIGNED-PAYLOAD";
            else
                ret += content_hash();
            return ret;
        }

        std::string amz_signed_headers() const
        {
            std::string signed_headers = "host";
            return signed_headers;
        }

        std::string string_to_sign(DigestFactory &digest_factory) const
        {
            return "AWS4-HMAC-SHA256\n"
                   + date + '\n'
                   + amz_credential() + "\n"
                   + sha256(digest_factory, canonical_request()).to_hex();
        }

        std::string amz_credential() const
        {
            return date.substr(0, 8) + '/' + region + '/' + service + "/aws4_request";
        }

        virtual ~QueryBuilder()
        {
        }
    };
};
} // namespace AWS
} // namespace openvpn
