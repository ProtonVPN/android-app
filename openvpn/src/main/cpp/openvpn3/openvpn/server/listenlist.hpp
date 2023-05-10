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

#ifndef OPENVPN_SERVER_LISTENLIST_H
#define OPENVPN_SERVER_LISTENLIST_H

#include <string>
#include <vector>
#include <utility> // for std::move

#include <openvpn/common/platform.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/options.hpp>
#include <openvpn/common/hostport.hpp>
#include <openvpn/common/number.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/format.hpp>
#include <openvpn/common/to_string.hpp>
#include <openvpn/addr/ip.hpp>
#include <openvpn/transport/protocol.hpp>

namespace openvpn {
namespace Listen {
struct Item
{
    enum SSLMode
    {
        SSLUnspecified,
        SSLOn,
        SSLOff,
#ifdef OPENVPN_POLYSOCK_SUPPORTS_ALT_ROUTING
        AltRouting,
#endif
    };

    std::string directive;
    std::string addr;
    std::string port;
    Protocol proto;
    SSLMode ssl = SSLUnspecified;
    unsigned int n_threads = 0;

    std::string to_string() const
    {
        std::string ret;
        ret += directive + ' ' + addr;
        if (!proto.is_local())
            ret += ' ' + port;
        ret += ' ' + std::string(proto.str()) + ' ' + openvpn::to_string(n_threads);
        switch (ssl)
        {
        case SSLUnspecified:
            break;
        case SSLOn:
            ret += " ssl";
            break;
        case SSLOff:
            ret += " !ssl";
            break;
#ifdef OPENVPN_POLYSOCK_SUPPORTS_ALT_ROUTING
        case AltRouting:
            ret += " alt";
            break;
#endif
        }
        return ret;
    }

    Item port_offset(const unsigned int offset) const
    {
        Item ret(*this);
        if (ret.proto.is_unix()) // unix socket filenames should contain %s for "port" substitution
            ret.addr = printfmt(ret.addr, offset);
        else
            ret.port = openvpn::to_string(HostPort::parse_port(ret.port, "offset") + offset);
        ret.n_threads = 0;
        return ret;
    }
};

class List : public std::vector<Item>
{
  public:
    enum LoadMode
    {
        Nominal,
        AllowDefault,
        AllowEmpty,
#ifdef VPN_BINDING_PROFILES
        AllowVPNBindingProfile,
#endif
    };

    List()
    {
    }

    List(const Item &item)
    {
        push_back(item);
    }

    List(Item &&item)
    {
        push_back(std::move(item));
    }

    List(const OptionList &opt,
         const std::string &directive,
         const LoadMode load_mode,
         const unsigned int n_cores)
    {
        const auto *opt_list = opt.get_index_ptr(directive);
        if (opt_list)
        {
            reserve(opt_list->size());
            for (auto i : *opt_list)
            {
                const Option &o = opt[i];
                o.touch();

                unsigned int mult = 1;
                unsigned int local = 0;

                Item e;

                // directive
                e.directive = o.get(0, 64);

                // IP address
                e.addr = o.get(1, 128);

                // port number
                e.port = o.get(2, 16);
                if (Protocol::is_local_type(e.port))
                {
                    local = 1;
                    e.port = "";
                }
                else
                    HostPort::validate_port(e.port, e.directive);

                // protocol
                {
                    const std::string title = e.directive + " protocol";
                    e.proto = Protocol::parse(o.get(3 - local, 16), Protocol::NO_SUFFIX, title.c_str());
                }

                // Modify protocol based on IP version of given address.
                // AllowVPNBindingProfile tells us to support
                // special address case for WS::ViaVPN, where address
                // begins with '@' followed by a client connection
                // profile filename.
                if (!local && !is_vpn_binding_profile(load_mode, e))
                {
                    const std::string title = e.directive + " addr";
                    const IP::Addr addr = IP::Addr(e.addr, title.c_str());
                    e.proto.mod_addr_version(addr.version());
                }

                // number of threads
                int n_threads_exists = 0;
                {
                    const std::string ntstr = o.get_optional(4 - local, 16);
                    if (ntstr.length() > 0 && string::is_digit(ntstr[0]))
                        n_threads_exists = 1;
                }
                if (n_threads_exists)
                {
                    std::string n_threads = o.get(4 - local, 16);
                    if (string::ends_with(n_threads, "*N"))
                    {
                        mult = n_cores;
                        n_threads = n_threads.substr(0, n_threads.length() - 2);
                    }
                    if (!parse_number_validate<unsigned int>(n_threads, 3, 1, 100, &e.n_threads))
                        OPENVPN_THROW(option_error, e.directive << ": bad num threads: " << n_threads);
#ifndef OPENVPN_PLATFORM_WIN
                    if (local && e.n_threads != 1)
                        OPENVPN_THROW(option_error, e.directive << ": local socket only supports one thread per pathname (not " << n_threads << ')');
#endif
                    e.n_threads *= mult;
                }
                else
                    e.n_threads = 1;

                // SSL
                if (o.size() >= 5 - local + n_threads_exists)
                {
                    const std::string &ssl_qualifier = o.get(4 - local + n_threads_exists, 16);
                    if (ssl_qualifier == "ssl")
                    {
                        if (local)
                            OPENVPN_THROW(option_error, e.directive << ": SSL not supported on local sockets");
                        e.ssl = Item::SSLOn;
                    }
                    else if (ssl_qualifier == "!ssl")
                        e.ssl = Item::SSLOff;
#ifdef OPENVPN_POLYSOCK_SUPPORTS_ALT_ROUTING
                    else if (ssl_qualifier == "alt")
                        e.ssl = Item::AltRouting;
#endif
                    else
                        OPENVPN_THROW(option_error, e.directive << ": unrecognized SSL qualifier");
                }

                push_back(std::move(e));
            }
        }
        else if (load_mode == AllowDefault)
        {
            Item e;

            // parse "proto" option if present
            {
                const Option *o = opt.get_ptr("proto");
                if (o)
                    e.proto = Protocol::parse(o->get(1, 16), Protocol::SERVER_SUFFIX);
                else
                    e.proto = Protocol(Protocol::UDPv4);
            }

            // parse "port" option if present
            {
                const Option *o = opt.get_ptr("lport");
                if (!o)
                    o = opt.get_ptr("port");
                if (o)
                {
                    e.port = o->get(1, 16);
                    HostPort::validate_port(e.port, "listen");
                }
                else
                    e.port = "1194";
            }

            // parse "local" option if present
            {
                const Option *o = opt.get_ptr("local");
                if (o)
                {
                    e.addr = o->get(1, 128);
                    const IP::Addr addr = IP::Addr(e.addr, "local addr");
                    e.proto.mod_addr_version(addr.version());
                }
                else if (e.proto.is_ipv6())
                    e.addr = "::0";
                else
                    e.addr = "0.0.0.0";
            }

            // n_threads defaults to one unless "listen" directive is used
            e.n_threads = 1;

            push_back(std::move(e));
        }
        else if (load_mode != AllowEmpty)
            OPENVPN_THROW(option_error, "no " << directive << " directives found");
    }

    unsigned int total_threads() const
    {
        unsigned int ret = 0;
        for (auto &i : *this)
            ret += i.n_threads;
        return ret;
    }

    std::string to_string() const
    {
        std::string ret;
        for (auto &i : *this)
        {
            ret += i.to_string();
            ret += '\n';
        }
        return ret;
    }

    std::string local_addr() const
    {
        for (auto &i : *this)
            if (i.proto.is_local())
                return i.addr;
        return std::string();
    }

    List expand_ports_by_n_threads(const size_t max_size) const
    {
        List ret;
        for (const auto &e : *this)
        {
            unsigned int offset = 0;
            do
            {
                if (ret.size() >= max_size)
                    OPENVPN_THROW(option_error, e.directive << ": max_size=" << max_size << " exceeded");
                ret.emplace_back(e.port_offset(offset));
            } while (++offset < e.n_threads);
        }
        return ret;
    }

    List expand_ports_by_unit(const unsigned int unit) const
    {
        List ret;
        for (const auto &e : *this)
            ret.emplace_back(e.port_offset(unit));
        return ret;
    }

  private:
    static bool is_vpn_binding_profile(const LoadMode load_mode,
                                       const Item &e)
    {
#ifdef VPN_BINDING_PROFILES
        return load_mode == AllowVPNBindingProfile
               && !e.addr.empty()
               && e.addr[0] == '@';
#else
        return false;
#endif
    }
};
} // namespace Listen
} // namespace openvpn

#endif
