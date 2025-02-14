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

#ifndef OPENVPN_COMMON_HOSTLIST_H
#define OPENVPN_COMMON_HOSTLIST_H

#include <string>
#include <sstream>
#include <vector>
#include <algorithm>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/options.hpp>
#include <openvpn/common/hostport.hpp>
#include <openvpn/random/randapi.hpp>

namespace openvpn::HostList {

struct Host
{
    Host()
    {
    }

    Host(const std::string &host_arg, const std::string &port_arg)
        : host(host_arg),
          port(port_arg)
    {
    }

    bool defined() const
    {
        return !host.empty();
    }

    void swap(Host &rhs) noexcept
    {
        host.swap(rhs.host);
        port.swap(rhs.port);
    }

    void reset()
    {
        host.clear();
        port.clear();
    }

    std::string to_string() const
    {
        std::ostringstream os;
        if (defined())
            os << '[' << host << "]:" << port;
        else
            os << "UNDEF_HOST";
        return os.str();
    }

    std::string host;
    std::string port;
};

class List : public std::vector<Host>
{
  public:
    List()
    {
    }

    List(const OptionList &opt,
         const std::string &directive,
         const std::string &default_port)
    {
        auto hl = opt.get_index_ptr(directive);
        if (hl)
        {
            for (auto &i : *hl)
            {
                const Option &o = opt[i];
                o.touch();
                add(o.get(1, 256), o.get_default(2, 16, default_port));
            }
        }
    }

    void randomize(RandomAPI &rng)
    {
        std::shuffle(begin(), end(), rng());
    }

    std::string to_string() const
    {
        std::ostringstream os;
        for (auto &h : *this)
            os << h.to_string() << '\n';
        return os.str();
    }

  private:
    void add(const std::string &host,
             const std::string &port)
    {
        const std::string title = "host list";
        HostPort::validate_host(host, title);
        HostPort::validate_port(port, title);
        emplace_back(host, port);
    }
};

class Iterator
{
  public:
    Iterator()
    {
        reset();
    }

    void reset()
    {
        index = -1;
    }

    template <typename HOST>
    bool next(const List &list, HOST &host)
    {
        if (list.size() > 0)
        {
            if (++index >= list.size())
                index = 0;
            const Host &h = list[index];
            host.host = h.host;
            host.port = h.port;
            return true;
        }
        else
            return false;
    }

  private:
    int index;
};
} // namespace openvpn::HostList

#endif
