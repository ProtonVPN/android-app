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

#ifndef OPENVPN_HTTP_URLPARSE_H
#define OPENVPN_HTTP_URLPARSE_H

#include <string>

#include <openvpn/common/platform.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/hostport.hpp>
#include <openvpn/common/format.hpp>
#include <openvpn/http/validate_uri.hpp>
#include <openvpn/http/parseutil.hpp>

namespace openvpn {
namespace URL {
OPENVPN_EXCEPTION(url_parse_error);

class Parse
{
  public:
    Parse()
    {
    }

    Parse(const std::string &url,
          const bool set_default_port = false,
          const bool loose_validation = false)
    {
        enum State
        {
            Scheme,
            PostSchemeSlash1,
            PostSchemeSlash2,
            StartHost,
            Host,
            BracketedHost,
            PostBracketedHost,
            Port,
            URI,
        };

        State state = Scheme;
        for (auto &c : url)
        {
        reprocess:
            switch (state)
            {
            case Scheme:
                if (c == ':')
                    state = PostSchemeSlash1;
                else if (is_valid_scheme_char(c))
                    scheme += c;
                else
                    throw url_parse_error("bad scheme char");
                break;
            case PostSchemeSlash1:
                if (c == '/')
                    state = PostSchemeSlash2;
                else
                    throw url_parse_error("expected '://' after scheme");
                break;
            case PostSchemeSlash2:
                if (c == '/')
                    state = StartHost;
                else
                    throw url_parse_error("expected '://' after scheme");
                break;
            case StartHost:
                if (c == '[')
                    state = BracketedHost;
                else
                {
                    state = Host;
                    goto reprocess;
                }
                break;
            case Host:
                if (c == ':')
                    state = Port;
                else if (c == '/')
                {
                    state = URI;
                    goto reprocess;
                }
                else
                    host += c;
                break;
            case BracketedHost:
                if (c == ']')
                    state = PostBracketedHost;
                else
                    host += c;
                break;
            case PostBracketedHost:
                if (c == ':')
                {
                    state = Port;
                    break;
                }
                else
                {
                    state = URI;
                    goto reprocess;
                }
                break;
            case Port:
                if (c == '/')
                {
                    state = URI;
                    goto reprocess;
                }
                else
                    port += c;
                break;
            case URI:
                if (!HTTP::is_valid_uri_char(c) && !loose_validation)
                    throw url_parse_error("bad URI char");
                uri += c;
                break;
            }
        }
        if (set_default_port)
            default_port();
        if (uri.empty())
            uri = "/";
        validate();
    }

    // Note that special address types such as unix domain
    // sockets or windows named pipes store a tag such as
    // "unix" or "np" as the port component of an address/port
    // tuple.  Here, we move such tags into the scheme.
    static Parse from_components(const bool https,
                                 const std::string &host,
                                 const std::string &port,
                                 const std::string &uri)
    {
        Parse p;
        p.scheme = https ? "https" : "http";
        p.host = host;
        if (port.size() >= 1 && !string::is_digit(port[0])) // non-INET address
            p.scheme = port;
        else
            p.port = port;
        p.uri = uri;
        return p;
    }

    void validate() const
    {
        if (scheme.empty())
            throw url_parse_error("undefined scheme");
        if (host.empty())
            throw url_parse_error("undefined host");
        if (uri.empty())
            throw url_parse_error("undefined uri");

        if (!port.empty() && !HostPort::is_valid_port(port))
            throw url_parse_error("bad port");
        if ((scheme == "http" || scheme == "https") && !HostPort::is_valid_host(host))
            throw url_parse_error("bad host");
    }

    void default_port()
    {
        if (port.empty())
        {
            if (scheme == "http")
                port = "80";
            else if (scheme == "https")
                port = "443";
        }
    }

    bool port_implied() const
    {
        return (scheme == "http" && port == "80") || (scheme == "https" && port == "443");
    }

    bool is_bracketed_host() const
    {
        return host.find_first_of(":/\\") != std::string::npos;
    }

    std::string bracketed_host() const
    {
        return '[' + host + ']';
    }

    std::string to_string() const
    {
        const bool bracket_host = is_bracketed_host();

        std::string ret;
        ret.reserve(256);
        ret += scheme;
        ret += "://";
        if (bracket_host)
            ret += '[';
        ret += host;
        if (bracket_host)
            ret += ']';
        if (!port.empty() && !port_implied())
        {
            ret += ':';
            ret += port;
        }
        ret += uri;
        return ret;
    }

    std::string format_components() const
    {
        return printfmt("[scheme=%r host=%r port=%r uri=%r]", scheme, host, port, uri);
    }

    // Note that special address types such as unix domain
    // sockets or windows named pipes store a tag such as
    // "unix" or "np" as the port component of an address/port
    // tuple.  This method returns the port number for INET
    // addresses or a special tag for non-INET addresses.
    // Internally, we store the tag as an alternative
    // scheme such as "unix" or "np".
    std::string port_for_scheme() const
    {
#ifdef OPENVPN_PLATFORM_WIN
        if (scheme == "np") // named pipe
            return scheme;
#else
        if (scheme == "unix") // unix domain socket
            return scheme;
#endif
        if (scheme == "http" || scheme == "https")
            return port;

        throw url_parse_error("unknown scheme");
    }

    std::string scheme;
    std::string host;
    std::string port;
    std::string uri;

  private:
    bool is_valid_scheme_char(const char c)
    {
        return (c >= 'a' && c <= 'z') || c == '_';
    }
};

} // namespace URL
} // namespace openvpn

#endif
