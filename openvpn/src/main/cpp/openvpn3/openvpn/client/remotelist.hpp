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

// These classes handle parsing and representation of OpenVPN "remote" directives,
// and the list of IP addresses that they resolve to.
// <connection> blocks are supported.

#ifndef OPENVPN_CLIENT_REMOTELIST_H
#define OPENVPN_CLIENT_REMOTELIST_H

#include <ctime>
#include <string>
#include <sstream>
#include <vector>
#include <algorithm>
#include <utility>
#include <thread>

#include <openvpn/io/io.hpp>
#include <openvpn/asio/asiowork.hpp>

#include <openvpn/common/bigmutex.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/common/options.hpp>
#include <openvpn/common/number.hpp>
#include <openvpn/common/hostport.hpp>
#include <openvpn/random/randapi.hpp>
#include <openvpn/addr/ip.hpp>
#include <openvpn/addr/addrlist.hpp>
#include <openvpn/transport/protocol.hpp>
#include <openvpn/client/cliconstants.hpp>
#include <openvpn/log/sessionstats.hpp>
#include <openvpn/client/async_resolve.hpp>

#if OPENVPN_DEBUG_REMOTELIST >= 1
#define OPENVPN_LOG_REMOTELIST(x) OPENVPN_LOG(x)
#else
#define OPENVPN_LOG_REMOTELIST(x)
#endif

namespace openvpn {
class RemoteList : public RC<thread_unsafe_refcount>
{
    // A single IP address that is part of a list of IP addresses
    // associated with a "remote" item.
    struct ResolvedAddr : public RC<thread_unsafe_refcount>
    {
        typedef RCPtr<ResolvedAddr> Ptr;
        IP::Addr addr;

        std::string to_string() const
        {
            return addr.to_string();
        }
    };

    // The IP address list associated with a single "remote" item.
    struct ResolvedAddrList : public std::vector<ResolvedAddr::Ptr>, public RC<thread_unsafe_refcount>
    {
        typedef RCPtr<ResolvedAddrList> Ptr;

        std::string to_string() const
        {
            std::string ret;
            for (std::vector<ResolvedAddr::Ptr>::const_iterator i = begin(); i != end(); ++i)
            {
                if (!ret.empty())
                    ret += ' ';
                ret += (*i)->to_string();
            }
            return ret;
        }
    };

  public:
    enum class Advance
    {
        None,
        Addr,
        Remote
    };

    struct Item;

    struct ConnBlock : public RC<thread_unsafe_refcount>
    {
        typedef RCPtr<ConnBlock> Ptr;

        virtual void new_item(const Item &item) = 0;
    };

    struct ConnBlockFactory
    {
        typedef RCPtr<ConnBlockFactory> Ptr;

        virtual ConnBlock::Ptr new_conn_block(const OptionList::Ptr &opt) = 0;
    };

    // A single "remote" item
    struct Item : public RC<thread_unsafe_refcount>
    {
        typedef RCPtr<Item> Ptr;

        // "remote" item parameters from config file
        std::string server_host;
        std::string server_port;
        Protocol transport_protocol;

        // Non-empty if --remote-random-hostname is active.
        std::string random_host;

        // IP address list defined after actual_host() is resolved
        ResolvedAddrList::Ptr res_addr_list;

        // Other options if this is a <connection> block
        ConnBlock::Ptr conn_block;

        // Time when the item's resolved addresses are considered outdated
        std::time_t decay_time = std::numeric_limits<std::time_t>::max();

        bool res_addr_list_defined() const
        {
            return res_addr_list && res_addr_list->size() > 0;
        }

        std::string actual_host() const
        {
            return random_host.empty() ? server_host : random_host;
        }

        // cache a single IP address
        void set_ip_addr(const IP::Addr &addr)
        {
            res_addr_list.reset(new ResolvedAddrList());
            ResolvedAddr::Ptr ra(new ResolvedAddr());
            ra->addr = addr;
            res_addr_list->push_back(std::move(ra));
            decay_time = std::numeric_limits<std::time_t>::max();
            OPENVPN_LOG_REMOTELIST("*** RemoteList::Item endpoint SET " << to_string());
        }

        // cache a list of DNS-resolved IP addresses
        template <class EPRANGE>
        void set_endpoint_range(const EPRANGE &endpoint_range, RandomAPI *rng, std::size_t addr_lifetime)
        {
            // Keep addresses in case there are no results
            if (endpoint_range.size())
            {
                res_addr_list.reset(new ResolvedAddrList());
                for (const auto &i : endpoint_range)
                {
                    // Skip addresses with incompatible family
                    if ((transport_protocol.is_ipv6() && i.endpoint().address().is_v4())
                        || (transport_protocol.is_ipv4() && i.endpoint().address().is_v6()))
                        continue;
                    ResolvedAddr::Ptr addr(new ResolvedAddr());
                    addr->addr = IP::Addr::from_asio(i.endpoint().address());
                    res_addr_list->push_back(addr);
                }
                if (rng && res_addr_list->size() >= 2)
                    std::shuffle(res_addr_list->begin(), res_addr_list->end(), *rng);
                OPENVPN_LOG_REMOTELIST("*** RemoteList::Item endpoint SET " << to_string());
            }
            else if (!res_addr_list)
                res_addr_list.reset(new ResolvedAddrList());

            if (addr_lifetime)
                decay_time = time(nullptr) + addr_lifetime;
        }

        // get an endpoint for contacting server
        template <class EP>
        bool get_endpoint(EP &endpoint, const size_t index) const
        {
            if (res_addr_list && index < res_addr_list->size())
            {
                endpoint.address((*res_addr_list)[index]->addr.to_asio());
                endpoint.port(parse_number_throw<unsigned short>(server_port, "remote_port"));
                OPENVPN_LOG_REMOTELIST("*** RemoteList::Item endpoint GET[" << index << "] " << endpoint << ' ' << to_string());
                return true;
            }
            else
                return false;
        }

        bool need_resolve()
        {
            return !res_addr_list || decay_time <= time(nullptr);
        }

        std::string to_string() const
        {
            std::ostringstream out;
            out << "host=" << actual_host();
            if (res_addr_list)
                out << '[' << res_addr_list->to_string() << ']';
            out << " port=" << server_port
                << " proto=" << transport_protocol.str();
            return out.str();
        }
    };

    struct RemoteOverride
    {
        virtual Item::Ptr get() = 0;
    };

  private:
    // Directive names that we search for in options
    struct Directives
    {
        explicit Directives(const std::string &conn_tag = "")
            : connection(conn_tag.length() ? conn_tag : "connection")
        {
        }

        const std::string connection;
        const std::string remote = "remote";
        const std::string proto = "proto";
        const std::string port = "port";
    };

    // Used to index into remote list items and their address(es).
    struct Index
    {
        void reset()
        {
            item_ = item_addr_ = 0;
        }
        void reset_item_addr()
        {
            item_addr_ = 0;
        }
        void set_item(const size_t i)
        {
            item_ = i;
        }

        size_t item() const
        {
            return item_;
        }
        size_t item_addr() const
        {
            return item_addr_;
        }

        // return true if item index was incremented
        bool increment(const Advance type, const size_t item_len, const size_t addr_len)
        {
            if (type == Advance::Remote || ++item_addr_ >= addr_len)
            {
                item_addr_ = 0;
                if (++item_ >= item_len)
                    item_ = 0;
                return true;
            }
            else
                return false;
        }

      private:
        size_t item_ = 0;
        size_t item_addr_ = 0;
    };

  public:
    // Used for errors occurring after initial options processing,
    // and generally indicate logic errors
    // (option_error used during initial options processing).
    OPENVPN_EXCEPTION(remote_list_error);

    typedef RCPtr<RemoteList> Ptr;

    // Helper class used to resolve all items in remote list.
    // This is useful in tun_persist mode, where it may be necessary
    // to pre-resolve all potential remote server items prior
    // to initial tunnel establishment. Also used when trying to
    // re-resolve items which had too many failed attempts.
    class BulkResolve : public virtual RC<thread_unsafe_refcount>, protected AsyncResolvableTCP
    {
      public:
        typedef RCPtr<BulkResolve> Ptr;

        struct NotifyCallback
        {
            // client callback when resolve operation is complete
            virtual void bulk_resolve_done() = 0;
        };

        BulkResolve(openvpn_io::io_context &io_context_arg,
                    const RemoteList::Ptr &remote_list_arg,
                    const SessionStats::Ptr &stats_arg)
            : AsyncResolvableTCP(io_context_arg),
              notify_callback(nullptr),
              remote_list(remote_list_arg),
              stats(stats_arg),
              index(0)
        {
            remote_list->index.reset();
        }

        bool work_available() const
        {
            return remote_list->defined() && remote_list->enable_cache;
        }

        void start(NotifyCallback *notify_callback_arg)
        {
            if (notify_callback_arg)
            {
                // This method is a no-op (i.e. bulk_resolve_done is called immediately)
                // if caching not enabled in underlying remote_list or if start() was
                // previously called and is still in progress.
                if (!notify_callback && work_available())
                {
                    notify_callback = notify_callback_arg;
                    index = 0;
                    async_resolve_lock();
                    resolve_next();
                }
                else
                    notify_callback_arg->bulk_resolve_done();
            }
        }

        void cancel()
        {
            notify_callback = nullptr;
            index = 0;
            async_resolve_cancel();
        }

      protected:
        void resolve_next()
        {
            while (index < remote_list->list.size())
            {
                // try to resolve item if needed
                auto &item = remote_list->list[index];
                if (item->need_resolve())
                {
                    // next item to resolve
                    OPENVPN_LOG_REMOTELIST("*** BulkResolve RESOLVE on " << item->to_string());
                    async_resolve_name(item->actual_host(), item->server_port);
                    return;
                }
                ++index;
            }

            // Done resolving list.  Prune out all entries we were unable to
            // resolve unless doing so would result in an empty list.
            // Then call client's callback method.
            {
                async_resolve_cancel();
                NotifyCallback *ncb = notify_callback;
                if (remote_list->cached_item_exists())
                    remote_list->prune_uncached();
                cancel();
                ncb->bulk_resolve_done();
            }
        }

        // callback on resolve completion
        void resolve_callback(const openvpn_io::error_code &error,
                              results_type results) override
        {
            if (notify_callback && index < remote_list->list.size())
            {
                auto indexed_item(remote_list->index.item());
                const auto item_in_use(remote_list->list[indexed_item]);
                const auto resolve_item(remote_list->list[index++]);
                if (!error)
                {
                    // Set results to Items, where applicable
                    auto rand = remote_list->random ? remote_list->rng.get() : nullptr;
                    for (auto &item : remote_list->list)
                    {
                        // Skip already resolved and items with different hostname
                        if (!item->need_resolve()
                            || item->server_host != resolve_item->server_host)
                            continue;

                        // Reset item's address index as the list changes
                        if (item == item_in_use)
                            remote_list->index.reset_item_addr();

                        item->set_endpoint_range(results, rand, remote_list->cache_lifetime);
                        item->random_host = resolve_item->random_host;
                    }
                }
                else
                {
                    // resolve failed
                    OPENVPN_LOG("DNS bulk-resolve error on " << resolve_item->actual_host()
                                                             << ": " << error.message());
                    if (stats)
                        stats->error(Error::RESOLVE_ERROR);
                }
                resolve_next();
            }
        }

        NotifyCallback *notify_callback;
        RemoteList::Ptr remote_list;
        SessionStats::Ptr stats;
        size_t index;
    };

    // create a remote list with a RemoteOverride callback
    RemoteList(RemoteOverride *remote_override_arg)
        : remote_override(remote_override_arg)
    {
        next();
    }

    // create a remote list with exactly one item
    RemoteList(const std::string &server_host,
               const std::string &server_port,
               const Protocol &transport_protocol,
               const std::string &title)
    {
        HostPort::validate_port(server_port, title);

        Item::Ptr item(new Item());
        item->server_host = server_host;
        item->server_port = server_port;
        item->transport_protocol = transport_protocol;
        list.push_back(item);
    }

    // RemoteList flags
    enum
    {
        WARN_UNSUPPORTED = 1 << 0,
        CONN_BLOCK_ONLY = 1 << 1,
        CONN_BLOCK_OMIT_UNDEF = 1 << 2,
        ALLOW_EMPTY = 1 << 3,
    };

    // create a remote list from config file option list
    RemoteList(const OptionList &opt,
               const std::string &connection_tag,
               const unsigned int flags,
               ConnBlockFactory *conn_block_factory,
               RandomAPI::Ptr rng_arg)
        : random_hostname(opt.exists("remote-random-hostname")),
          directives(connection_tag), rng(rng_arg)
    {
        process_cache_lifetime(opt);

        // defaults
        const Protocol default_proto = get_proto(opt, Protocol(Protocol::UDPv4));
        const std::string default_port = get_port(opt, "1194");

        // handle remote, port, and proto at the top-level
        if (!(flags & CONN_BLOCK_ONLY))
            add(opt, default_proto, default_port, ConnBlock::Ptr());

        // cycle through <connection> blocks
        {
            const size_t max_conn_block_size = 4096;
            const OptionList::IndexList *conn = opt.get_index_ptr(directives.connection);
            if (conn)
            {
                for (OptionList::IndexList::const_iterator i = conn->begin(); i != conn->end(); ++i)
                {
                    try
                    {
                        const Option &o = opt[*i];
                        o.touch();
                        const std::string &conn_block_text = o.get(1, Option::MULTILINE);
                        OptionList::Limits limits("<connection> block is too large",
                                                  max_conn_block_size,
                                                  ProfileParseLimits::OPT_OVERHEAD,
                                                  ProfileParseLimits::TERM_OVERHEAD,
                                                  ProfileParseLimits::MAX_LINE_SIZE,
                                                  ProfileParseLimits::MAX_DIRECTIVE_SIZE);
                        OptionList::Ptr conn_block = OptionList::parse_from_config_static_ptr(conn_block_text, &limits);
                        const Protocol block_proto = get_proto(*conn_block, default_proto);
                        const std::string block_port = get_port(*conn_block, default_port);

                        // unsupported options
                        if (flags & WARN_UNSUPPORTED)
                        {
                            unsupported_in_connection_block(*conn_block, "http-proxy");
                            unsupported_in_connection_block(*conn_block, "http-proxy-option");
                            unsupported_in_connection_block(*conn_block, "http-proxy-user-pass");
                        }

                        // connection block options encapsulation via user-defined factory
                        {
                            ConnBlock::Ptr cb;
                            if (conn_block_factory)
                                cb = conn_block_factory->new_conn_block(conn_block);
                            if (!(flags & CONN_BLOCK_OMIT_UNDEF) || cb)
                                add(*conn_block, block_proto, block_port, cb);
                        }
                    }
                    catch (Exception &e)
                    {
                        e.remove_label("option_error");
                        e.add_label("connection_block");
                        throw;
                    }
                }
            }
        }

        if (!(flags & ALLOW_EMPTY) && list.empty())
            throw option_error("remote option not specified");
    }

    void process_push(const OptionList &opt)
    {
        process_cache_lifetime(opt);
    }

    // if cache is enabled, all DNS names will be preemptively queried
    void set_enable_cache(const bool enable_cache_arg)
    {
        enable_cache = enable_cache_arg;
    }

    bool get_enable_cache() const
    {
        return enable_cache;
    }

    // override all server hosts to server_override
    void set_server_override(const std::string &server_override)
    {
        if (server_override.empty())
            return;
        for (auto &item : list)
        {
            item->server_host = server_override;
            item->random_host.clear();
            item->res_addr_list.reset();
        }
        random_hostname = false;
        reset_cache();
    }

    // override all server ports to port_override
    void set_port_override(const std::string &port_override)
    {
        if (port_override.empty())
            return;
        for (auto &item : list)
        {
            item->server_port = port_override;
            item->res_addr_list.reset();
        }
        reset_cache();
    }

    // override all Item's transport_protocol version to v
    void set_proto_version_override(const IP::Addr::Version v)
    {
        if (v == IP::Addr::Version::UNSPEC)
            return;
        for (auto item : list)
            item->transport_protocol.mod_addr_version(v);
        reset_cache();
    }

    void set_random(const RandomAPI::Ptr &rng_arg)
    {
        rng = rng_arg;
    }

    // randomize item list, used to implement remote-random directive
    void randomize()
    {
        if (rng)
        {
            random = true;
            std::shuffle(list.begin(), list.end(), *rng);
            index.reset();
        }
    }

    // Higher-level version of set_proto_override that also supports indication
    // on whether or not TCP-based proxies are enabled.  Should be called after set_enable_cache
    // because it may modify enable_cache flag.
    void handle_proto_override(const Protocol &proto_override, const bool tcp_proxy_enabled)
    {
        if (tcp_proxy_enabled)
        {
            const Protocol tcp(Protocol::TCP);
            if (contains_protocol(tcp))
                set_proto_override(tcp);
            else
                throw option_error("cannot connect via TCP-based proxy because no TCP server entries exist in profile");
        }
        else if (proto_override.defined() && contains_protocol(proto_override))
            set_proto_override(proto_override);
    }

    // increment to next IP address or remote list entry
    void next(Advance type = Advance::Addr)
    {
        if (remote_override)
        {
            Item::Ptr item = remote_override->get();
            if (item)
            {
                list.clear();
                index.reset();
                list.push_back(std::move(item));
                return;
            }
        }

        if (type != Advance::None)
        {
            bool item_changed = index.increment(type, list.size(), item_addr_length(index.item()));
            if (item_changed && !enable_cache)
                reset_item(index.item());
        }
    }

    // Return details about current connection entry.
    // Return value is true if get_endpoint may be called
    // without raising an exception.
    bool endpoint_available(std::string *server_host, std::string *server_port, Protocol *transport_protocol) const
    {
        const Item &item = *list[item_index()];
        if (server_host)
            *server_host = item.actual_host();
        if (server_port)
            *server_port = item.server_port;
        const bool cached = (item.res_addr_list && index.item_addr() < item.res_addr_list->size());
        if (transport_protocol)
        {
            if (cached)
            {
                // Since we know whether resolved address is IPv4 or IPv6, add
                // that info to the returned Protocol object.
                Protocol proto(item.transport_protocol);
                const IP::Addr &addr = (*item.res_addr_list)[index.item_addr()]->addr;
                proto.mod_addr_version(addr.version());
                *transport_protocol = proto;
            }
            else
                *transport_protocol = item.transport_protocol;
        }
        return cached;
    }

    // cache a list of DNS-resolved IP addresses
    template <class EPRANGE>
    void set_endpoint_range(EPRANGE &endpoint_range)
    {
        Item &item = *list[item_index()];
        auto rand = random ? rng.get() : nullptr;
        std::size_t lifetime = enable_cache ? cache_lifetime : 0;
        item.set_endpoint_range(endpoint_range, rand, lifetime);
        index.reset_item_addr();
    }

    // get an endpoint for contacting server
    template <class EP>
    void get_endpoint(EP &endpoint) const
    {
        const Item &item = *list[item_index()];
        if (!item.get_endpoint(endpoint, index.item_addr()))
            throw remote_list_error("current remote server endpoint is undefined");
    }

    // return true if object has at least one connection entry
    bool defined() const
    {
        return list.size() > 0;
    }

    // return remote list size
    size_t size() const
    {
        return list.size();
    }

    Item::Ptr get_item(const size_t index) const
    {
        return list.at(index);
    }

    // return hostname (or IP address) of current connection entry
    std::string current_server_host() const
    {
        const Item &item = *list[item_index()];
        return item.actual_host();
    }

    // return transport protocol of current connection entry
    const Protocol &current_transport_protocol() const
    {
        const Item &item = *list[item_index()];
        return item.transport_protocol;
    }

    template <typename T>
    T *current_conn_block_rawptr() const
    {
        const Item &item = *list[item_index()];
        return dynamic_cast<T *>(item.conn_block.get());
    }

    std::string to_string() const
    {
        std::ostringstream out;
        for (size_t i = 0; i < list.size(); ++i)
        {
            const Item &e = *list[i];
            out << '[' << i << "] " << e.to_string() << std::endl;
        }
        return out.str();
    }

    // return a list of unique, cached IP addresses
    void cached_ip_address_list(IP::AddrList &addrlist) const
    {
        for (std::vector<Item::Ptr>::const_iterator i = list.begin(); i != list.end(); ++i)
        {
            const Item &item = **i;
            if (item.res_addr_list_defined())
            {
                const ResolvedAddrList &ral = *item.res_addr_list;
                for (ResolvedAddrList::const_iterator j = ral.begin(); j != ral.end(); ++j)
                {
                    const ResolvedAddr &addr = **j;
                    addrlist.add(addr.addr);
                }
            }
        }
    }

    // reset the cache associated with all items
    void reset_cache()
    {
        for (auto &e : list)
        {
            e->res_addr_list.reset(nullptr);
            randomize_host(*e);
        }
        index.reset();
    }

    // if caching is disabled, reset the cache for current item
    void reset_cache_item()
    {
        if (!enable_cache)
            reset_item(index.item());
    }

  private:
    // Process --remote-cache-lifetime option
    void process_cache_lifetime(const OptionList &opt)
    {
        if (!opt.exists("remote-cache-lifetime"))
            return;

        const bool lifetimes_set = cache_lifetime != 0;
        cache_lifetime = opt.get("remote-cache-lifetime").get_num(1, 0);
        if (!enable_cache || lifetimes_set)
            return;

        // Init lifetimes on items with adresses
        for (auto &item : list)
        {
            if (item->res_addr_list_defined())
                item->decay_time = time(nullptr) + cache_lifetime;
        }
    }

    // reset the cache associated with a given item
    void reset_item(const size_t i)
    {
        if (i < list.size())
        {
            list[i]->res_addr_list.reset(nullptr);
            list[i]->decay_time = std::numeric_limits<std::time_t>::max();
            randomize_host(*list[i]);
        }
    }

    // return the current item index (into list) and raise an exception
    // if it is undefined
    size_t item_index() const
    {
        const size_t pri = index.item();
        if (pri < list.size())
            return pri;
        else
            throw remote_list_error("current remote server item is undefined");
    }

    // return the number of cached IP addresses associated with a given item
    size_t item_addr_length(const size_t i) const
    {
        if (i < list.size())
        {
            const Item &item = *list[i];
            if (item.res_addr_list)
                return item.res_addr_list->size();
        }
        return 0;
    }

    // return true if at least one remote entry is of type proto
    bool contains_protocol(const Protocol &proto)
    {
        for (std::vector<Item::Ptr>::const_iterator i = list.begin(); i != list.end(); ++i)
        {
            if (proto.transport_match((*i)->transport_protocol))
                return true;
        }
        return false;
    }

    // prune remote entries so that only those of Protocol proto_override remain
    void set_proto_override(const Protocol &proto_override)
    {
        if (proto_override.defined())
        {
            size_t di = 0;
            for (size_t si = 0; si < list.size(); ++si)
            {
                const Item &item = *list[si];
                if (proto_override.transport_match(item.transport_protocol))
                {
                    if (si != di)
                        list[di] = list[si];
                    ++di;
                }
            }
            if (di != list.size())
                list.resize(di);
            reset_cache();
        }
    }

    // Return true if at least one cached Item exists
    bool cached_item_exists() const
    {
        for (std::vector<Item::Ptr>::const_iterator i = list.begin(); i != list.end(); ++i)
        {
            const Item &item = **i;
            if (item.res_addr_list_defined())
                return true;
        }
        return false;
    }

    // Prune uncached Items so that only Items containing a res_addr_list with
    // size > 0 remain.
    void prune_uncached()
    {
        size_t di = 0;
        for (size_t si = 0; si < list.size(); ++si)
        {
            const Item &item = *list[si];
            if (item.res_addr_list_defined())
            {
                if (si != di)
                {
                    list[di] = list[si];
                    if (si == index.item())
                        index.set_item(di);
                }
                ++di;
            }
        }
        if (di != list.size())
            list.resize(di);
    }

    std::string get_port(const OptionList &opt, const std::string &default_port)
    {
        // parse "port" option if present
        const Option *o = opt.get_ptr(directives.port);
        if (!o)
            return default_port;

        std::string port = o->get(1, 16);
        HostPort::validate_port(port, directives.port);
        return port;
    }

    Protocol get_proto(const OptionList &opt, const Protocol &default_proto)
    {
        // parse "proto" option if present
        const Option *o = opt.get_ptr(directives.proto);
        if (o)
            return Protocol::parse(o->get(1, 16), Protocol::CLIENT_SUFFIX);

        return default_proto;
    }

    void add(const OptionList &opt, const Protocol &default_proto, const std::string &default_port, ConnBlock::Ptr conn_block)
    {
        const OptionList::IndexList *rem = opt.get_index_ptr(directives.remote);
        if (!rem)
            return;

        // cycle through remote entries
        for (const auto &i : *rem)
        {
            Item::Ptr e(new Item());
            const Option &o = opt[i];
            o.touch();
            e->server_host = o.get(1, 256);
            int adj = 0;
            if (o.size() >= 3)
            {
                e->server_port = o.get(2, 16);
                if (Protocol::is_local_type(e->server_port))
                {
                    adj = -1;
                    e->server_port = "";
                }
                else
                    HostPort::validate_port(e->server_port, directives.port);
            }
            else
                e->server_port = default_port;
            if (o.size() >= (size_t)(4 + adj))
                e->transport_protocol = Protocol::parse(o.get(3 + adj, 16), Protocol::CLIENT_SUFFIX);
            else
                e->transport_protocol = default_proto;
            e->conn_block = conn_block;
            randomize_host(*e);
            if (conn_block)
                conn_block->new_item(*e);
            list.push_back(e);
        }
    }

    void unsupported_in_connection_block(const OptionList &options, const std::string &option)
    {
        if (options.exists(option))
            OPENVPN_LOG("NOTE: " << option << " directive is not currently supported in <connection> blocks");
    }

    void randomize_host(Item &item)
    {
        if (!random_hostname || !rng)
            return;

        try
        {
            // Throws if server_host is not an IP address
            IP::Addr(item.server_host);
        }
        catch (const IP::ip_exception &e)
        {
            // Produce 6 bytes of random prefix data
            unsigned char prefix[6];
            rng->rand_bytes(prefix, sizeof(prefix));

            // Prepend it to the server_host
            std::ostringstream random_host;
            random_host << std::hex;
            for (std::size_t i = 0; i < sizeof(prefix); ++i)
            {
                random_host << std::setw(2) << std::setfill('0')
                            << static_cast<unsigned>(prefix[i]);
            }
            random_host << "." << item.server_host;

            item.random_host = random_host.str();
        }
    }

    std::size_t cache_lifetime = 0;
    bool random_hostname = false;
    bool random = false;
    bool enable_cache = false;
    Index index;

    std::vector<Item::Ptr> list;

    Directives directives;

    RemoteOverride *remote_override = nullptr;

    RandomAPI::Ptr rng;
};

} // namespace openvpn

#endif
