//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
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
    struct Item;

    struct ConnBlock : public RC<thread_unsafe_refcount>
    {
      typedef RCPtr<ConnBlock> Ptr;

      virtual void new_item(const Item& item) = 0;
    };

    struct ConnBlockFactory
    {
      typedef RCPtr<ConnBlockFactory> Ptr;

      virtual ConnBlock::Ptr new_conn_block(const OptionList::Ptr& opt) = 0;
    };

    // A single "remote" item
    struct Item : public RC<thread_unsafe_refcount>
    {
      typedef RCPtr<Item> Ptr;

      // "remote" item parameters from config file
      std::string server_host;
      std::string server_port;
      Protocol transport_protocol;

      // IP address list defined after server_host is resolved
      ResolvedAddrList::Ptr res_addr_list;

      // Other options if this is a <connection> block
      ConnBlock::Ptr conn_block;

      bool res_addr_list_defined() const
      {
	return res_addr_list && res_addr_list->size() > 0;
      }

      // cache a single IP address
      void set_ip_addr(const IP::Addr& addr)
      {
	res_addr_list.reset(new ResolvedAddrList());
	ResolvedAddr::Ptr ra(new ResolvedAddr());
	ra->addr = addr;
	res_addr_list->push_back(std::move(ra));
	OPENVPN_LOG_REMOTELIST("*** RemoteList::Item endpoint SET " << to_string());
      }

      // cache a list of DNS-resolved IP addresses
      template <class EPRANGE>
      void set_endpoint_range(const EPRANGE& endpoint_range, RandomAPI* rng)
      {
	res_addr_list.reset(new ResolvedAddrList());
	for (const auto &i : endpoint_range)
	  {
	    ResolvedAddr::Ptr addr(new ResolvedAddr());
	    addr->addr = IP::Addr::from_asio(i.endpoint().address());
	    res_addr_list->push_back(addr);
	  }
	if (rng && res_addr_list->size() >= 2)
	  std::shuffle(res_addr_list->begin(), res_addr_list->end(), *rng);
	OPENVPN_LOG_REMOTELIST("*** RemoteList::Item endpoint SET " << to_string());
      }

      // get an endpoint for contacting server
      template <class EP>
      bool get_endpoint(EP& endpoint, const size_t index) const
      {
	if (res_addr_list && index < res_addr_list->size())
	  {
	    endpoint.address((*res_addr_list)[index]->addr.to_asio());
	    endpoint.port(parse_number_throw<unsigned int>(server_port, "remote_port"));
	    OPENVPN_LOG_REMOTELIST("*** RemoteList::Item endpoint GET[" << index << "] " << endpoint << ' ' << to_string());
	    return true;
	  }
	else
	  return false;
      }

      std::string to_string() const
      {
	std::ostringstream out;
	out << "host=" << server_host;
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
      void init(const std::string& connection_tag)
      {
	connection = connection_tag.length() ? connection_tag : "connection";
	remote = "remote";
	proto = "proto";
	port = "port";
      }

      std::string connection;
      std::string remote;
      std::string proto;
      std::string port;
    };

    // Used to index into remote list.
    // The primary index is the remote list index.
    // The secondary index is the index into the
    // Item's IP address list (res_addr_list).
    class Index
    {
    public:
      Index()
      {
	reset();
      }

      void reset()
      {
	primary_ = secondary_ = 0;
      }

      void reset_secondary()
      {
	secondary_ = 0;
      }

      // return true if primary index was incremented
      bool increment(const size_t pri_len, const size_t sec_len)
      {
	if (++secondary_ >= sec_len)
	  {
	    secondary_ = 0;
	    if (++primary_ >= pri_len)
	      primary_ = 0;
	    return true;
	  }
	else
	  return false;
      }

      bool equals(const Index& other) const
      {
	return primary_ == other.primary_ && secondary_ == other.secondary_;
      }

      size_t primary() const { return primary_; }
      size_t secondary() const { return secondary_; }

    private:
      size_t primary_;
      size_t secondary_;
    };

  public:
    // Used for errors occurring after initial options processing,
    // and generally indicate logic errors
    // (option_error used during initial options processing).
    OPENVPN_EXCEPTION(remote_list_error);

    typedef RCPtr<RemoteList> Ptr;

    // Helper class used to pre-resolve all items in remote list.
    // This is useful in tun_persist mode, where it may be necessary
    // to pre-resolve all potential remote server items prior
    // to initial tunnel establishment.
    class PreResolve : public virtual RC<thread_unsafe_refcount>, AsyncResolvableTCP
    {
    public:
      typedef RCPtr<PreResolve> Ptr;

      struct NotifyCallback
      {
	// client callback when resolve operation is complete
	virtual void pre_resolve_done() = 0;
      };

      PreResolve(openvpn_io::io_context& io_context_arg,
		 const RemoteList::Ptr& remote_list_arg,
		 const SessionStats::Ptr& stats_arg)
	:  AsyncResolvableTCP(io_context_arg),
	   notify_callback(nullptr),
	   remote_list(remote_list_arg),
	   stats(stats_arg),
	   index(0)
      {
      }

      bool work_available() const
      {
	return remote_list->defined() && remote_list->enable_cache;
      }

      void start(NotifyCallback* notify_callback_arg)
      {
	if (notify_callback_arg)
	  {
	    // This method is a no-op (i.e. pre_resolve_done is called immediately)
	    // if caching not enabled in underlying remote_list or if start() was
	    // previously called and is still in progress.
	    if (!notify_callback && work_available())
	      {
		notify_callback = notify_callback_arg;
		remote_list->index.reset();
		index = 0;
		async_resolve_lock();
		next();
	      }
	    else
	      notify_callback_arg->pre_resolve_done();
	  }
      }

      void cancel()
      {
	notify_callback = nullptr;
	index = 0;
	async_resolve_cancel();
      }

    private:
      void next()
      {
	while (index < remote_list->list.size())
	  {
	    Item& item = *remote_list->list[index];

	    // try to resolve item if no cached data present
	    if (!item.res_addr_list_defined())
	      {
		// next item to resolve
		const Item* sitem = remote_list->search_server_host(item.server_host);
		if (sitem)
		  {
		    // item's server_host matches one previously resolved -- use it
		    OPENVPN_LOG_REMOTELIST("*** PreResolve USED CACHE for " << item.server_host);
		    item.res_addr_list = sitem->res_addr_list;
		  }
		else
		  {
		    OPENVPN_LOG_REMOTELIST("*** PreResolve RESOLVE on " << item.server_host << " : " << item.server_port);
		    async_resolve_name(item.server_host, item.server_port);
		    return;
		  }
	      }
	    ++index;
	  }

	// Done resolving list.  Prune out all entries we were unable to
	// resolve unless doing so would result in an empty list.
	// Then call client's callback method.
	{
	  async_resolve_cancel();
	  NotifyCallback* ncb = notify_callback;
	  if (remote_list->cached_item_exists())
	    remote_list->prune_uncached();
	  cancel();
	  ncb->pre_resolve_done();
	}
      }

      // callback on resolve completion
      void resolve_callback(const openvpn_io::error_code& error,
			    openvpn_io::ip::tcp::resolver::results_type results) override
      {
	if (notify_callback && index < remote_list->list.size())
	  {
	    Item& item = *remote_list->list[index++];
	    if (!error)
	      {
		// resolve succeeded
		item.set_endpoint_range(results, remote_list->rng.get());
	      }
	    else
	      {
		// resolve failed
		OPENVPN_LOG("DNS pre-resolve error on " << item.server_host << ": " << error.message());
		if (stats)
		  stats->error(Error::RESOLVE_ERROR);
	      }
	    next();
	  }
      }

      NotifyCallback* notify_callback;
      RemoteList::Ptr remote_list;
      SessionStats::Ptr stats;
      size_t index;
    };

    // create an empty remote list
    RemoteList()
    {
      init("");
    }

    // create a remote list with a RemoteOverride callback
    RemoteList(RemoteOverride* remote_override_arg)
      : remote_override(remote_override_arg)
    {
      init("");
      next();
    }

    // create a remote list with exactly one item
    RemoteList(const std::string& server_host,
	       const std::string& server_port,
	       const Protocol& transport_protocol,
	       const std::string& title)
    {
      init("");

      HostPort::validate_port(server_port, title);

      Item::Ptr item(new Item());
      item->server_host = server_host;
      item->server_port = server_port;
      item->transport_protocol = transport_protocol;
      list.push_back(item);
    }

    // RemoteList flags
    enum {
      WARN_UNSUPPORTED=1<<0,
      CONN_BLOCK_ONLY=1<<1,
      CONN_BLOCK_OMIT_UNDEF=1<<2,
      ALLOW_EMPTY=1<<3,
    };

    // create a remote list from config file option list
    RemoteList(const OptionList& opt,
	       const std::string& connection_tag,
	       const unsigned int flags,
	       ConnBlockFactory* conn_block_factory)
    {
      init(connection_tag);

      // defaults
      Protocol default_proto(Protocol::UDPv4);
      std::string default_port = "1194";

      // handle remote, port, and proto at the top-level
      if (!(flags & CONN_BLOCK_ONLY))
	add(opt, default_proto, default_port, ConnBlock::Ptr());

      // cycle through <connection> blocks
      {
	const size_t max_conn_block_size = 4096;
	const OptionList::IndexList* conn = opt.get_index_ptr(directives.connection);
	if (conn)
	  {
	    for (OptionList::IndexList::const_iterator i = conn->begin(); i != conn->end(); ++i)
	      {
		try {
		  const Option& o = opt[*i];
		  o.touch();
		  const std::string& conn_block_text = o.get(1, Option::MULTILINE);
		  OptionList::Limits limits("<connection> block is too large",
					    max_conn_block_size,
					    ProfileParseLimits::OPT_OVERHEAD,
					    ProfileParseLimits::TERM_OVERHEAD,
					    ProfileParseLimits::MAX_LINE_SIZE,
					    ProfileParseLimits::MAX_DIRECTIVE_SIZE);
		  OptionList::Ptr conn_block = OptionList::parse_from_config_static_ptr(conn_block_text, &limits);
		  Protocol proto(default_proto);
		  std::string port(default_port);

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
		      add(*conn_block, proto, port, cb);
		  }
		}
		catch (Exception& e)
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

      //OPENVPN_LOG(to_string());
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
    void set_server_override(const std::string& server_override)
    {
      if (server_override.empty())
	return;
      for (auto &item : list)
	{
	  item->server_host = server_override;
	  item->res_addr_list.reset();
	}
      reset_cache();
    }

    // override all server ports to port_override
    void set_port_override(const std::string& port_override)
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

    void set_random(const RandomAPI::Ptr& rng_arg)
    {
      rng = rng_arg;
    }

    // randomize item list, used to implement remote-random directive
    void randomize()
    {
      if (rng)
	{
	  std::shuffle(list.begin(), list.end(), *rng);
	  index.reset();
	}
    }

    // return true if at least one remote entry is of type proto
    bool contains_protocol(const Protocol& proto)
    {
      for (std::vector<Item::Ptr>::const_iterator i = list.begin(); i != list.end(); ++i)
	{
	  if (proto.transport_match((*i)->transport_protocol))
	    return true;
	}
      return false;
    }

    // Higher-level version of set_proto_override that also supports indication
    // on whether or not TCP-based proxies are enabled.  Should be called after set_enable_cache
    // because it may modify enable_cache flag.
    void handle_proto_override(const Protocol& proto_override, const bool tcp_proxy_enabled)
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

    // increment to next IP address
    void next()
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

      index.increment(list.size(), secondary_length(index.primary()));
      if (!enable_cache)
	reset_item(index.primary());
    }

    // Return details about current connection entry.
    // Return value is true if get_endpoint may be called
    // without raising an exception.
    bool endpoint_available(std::string* server_host, std::string* server_port, Protocol* transport_protocol) const
    {
      const Item& item = *list[primary_index()];
      if (server_host)
	*server_host = item.server_host;
      if (server_port)
	*server_port = item.server_port;
      const bool cached = (item.res_addr_list && index.secondary() < item.res_addr_list->size());
      if (transport_protocol)
	{
	  if (cached)
	    {
	      // Since we know whether resolved address is IPv4 or IPv6, add
	      // that info to the returned Protocol object.
	      Protocol proto(item.transport_protocol);
	      proto.mod_addr_version((*item.res_addr_list)[index.secondary()]->addr);
	      *transport_protocol = proto;
	    }
	  else
	    *transport_protocol = item.transport_protocol;
	}
      return cached;
    }

    // cache a list of DNS-resolved IP addresses
    template <class EPRANGE>
    void set_endpoint_range(EPRANGE& endpoint_range)
    {
      Item& item = *list[primary_index()];
      item.set_endpoint_range(endpoint_range, rng.get());
      index.reset_secondary();
    }

    // get an endpoint for contacting server
    template <class EP>
    void get_endpoint(EP& endpoint) const
    {
      const Item& item = *list[primary_index()];
      if (!item.get_endpoint(endpoint, index.secondary()))
	throw remote_list_error("current remote server endpoint is undefined");
    }

    // return true if object has at least one connection entry
    bool defined() const { return list.size() > 0; }

    // return remote list size
    size_t size() const { return list.size(); }

    const Item& get_item(const size_t index) const
    {
      return *list.at(index);
    }

    // return hostname (or IP address) of current connection entry
    const std::string& current_server_host() const
    {
      const Item& item = *list[primary_index()];
      return item.server_host;
    }

    // return transport protocol of current connection entry
    const Protocol& current_transport_protocol() const
    {
      const Item& item = *list[primary_index()];
      return item.transport_protocol;
    }

    template <typename T>
    typename T::Ptr current_conn_block() const
    {
      const Item& item = *list[primary_index()];
      return item.conn_block.template dynamic_pointer_cast<T>();
    }

    template <typename T>
    T* current_conn_block_rawptr() const
    {
      const Item& item = *list[primary_index()];
      return dynamic_cast<T*>(item.conn_block.get());
    }

    // return hostname (or IP address) of first connection entry
    std::string first_server_host() const
    {
      const Item& item = *list.at(0);
      return item.server_host;
    }

    const Item* first_item() const
    {
      if (defined())
	return list[0].get();
      else
	return nullptr;
    }

    std::string to_string() const
    {
      std::ostringstream out;
      for (size_t i = 0; i < list.size(); ++i)
	{
	  const Item& e = *list[i];
	  out << '[' << i << "] " << e.to_string() << std::endl;
	}
      return out.str();
    }

    // return a list of unique, cached IP addresses
    void cached_ip_address_list(IP::AddrList& addrlist) const
    {
      for (std::vector<Item::Ptr>::const_iterator i = list.begin(); i != list.end(); ++i)
	{
	  const Item& item = **i;
	  if (item.res_addr_list_defined())
	    {
	      const ResolvedAddrList& ral = *item.res_addr_list;
	      for (ResolvedAddrList::const_iterator j = ral.begin(); j != ral.end(); ++j)
		{
		  const ResolvedAddr& addr = **j;
		  addrlist.add(addr.addr);
		}
	    }
	}
    }

    // reset the cache associated with all items
    void reset_cache()
    {
      for (auto &e : list)
	e->res_addr_list.reset(nullptr);
      index.reset();
    }

    // if caching is disabled, reset the cache for current item
    void reset_cache_item()
    {
      if (!enable_cache)
	reset_item(index.primary());
    }

  private:
    // initialization, called by constructors
    void init(const std::string& connection_tag)
    {
      enable_cache = false;
      directives.init(connection_tag);
    }

    // reset the cache associated with a given item
    void reset_item(const size_t i)
    {
      if (i <= list.size())
	list[i]->res_addr_list.reset(nullptr);
    }

    // return the current primary index (into list) and raise an exception
    // if it is undefined
    size_t primary_index() const
    {
      const size_t pri = index.primary();
      if (pri < list.size())
	return pri;
      else
	throw remote_list_error("current remote server item is undefined");
    }

    // return the number of cached IP addresses associated with a given item
    size_t secondary_length(const size_t i) const
    {
      if (i < list.size())
	{
	  const Item& item = *list[i];
	  if (item.res_addr_list)
	    return item.res_addr_list->size();
	}
      return 0;
    }

    // Search for cached Item by server_host
    Item* search_server_host(const std::string& server_host)
    {
      for (std::vector<Item::Ptr>::iterator i = list.begin(); i != list.end(); ++i)
	{
	  Item* item = i->get();
	  if (server_host == item->server_host && item->res_addr_list_defined())
	    return item;
	}
      return nullptr;
    }

    // prune remote entries so that only those of Protocol proto_override remain
    void set_proto_override(const Protocol& proto_override)
    {
      if (proto_override.defined())
	{
	  size_t di = 0;
	  for (size_t si = 0; si < list.size(); ++si)
	    {
	      const Item& item = *list[si];
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
	  const Item& item = **i;
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
	  const Item& item = *list[si];
	  if (item.res_addr_list_defined())
	    {
	      if (si != di)
		list[di] = list[si];
	      ++di;
	    }
	}
      if (di != list.size())
	list.resize(di);
      index.reset();
    }

    void add(const OptionList& opt, Protocol& default_proto, std::string& default_port, ConnBlock::Ptr conn_block)
    {
      // parse "proto" option if present
      {
	const Option* o = opt.get_ptr(directives.proto);
	if (o)
	  default_proto = Protocol::parse(o->get(1, 16), Protocol::CLIENT_SUFFIX);
      }

      // parse "port" option if present
      {
	const Option* o = opt.get_ptr(directives.port);
	if (o)
	  {
	    default_port = o->get(1, 16);
	    HostPort::validate_port(default_port, directives.port);
	  }
      }

      // cycle through remote entries
      {
	const OptionList::IndexList* rem = opt.get_index_ptr(directives.remote);
	if (rem)
	  {
	    for (OptionList::IndexList::const_iterator i = rem->begin(); i != rem->end(); ++i)
	      {
		Item::Ptr e(new Item());
		const Option& o = opt[*i];
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
		if (o.size() >= (size_t)(4+adj))
		  e->transport_protocol = Protocol::parse(o.get(3+adj, 16), Protocol::CLIENT_SUFFIX);
		else
		  e->transport_protocol = default_proto;
		e->conn_block = conn_block;
		if (conn_block)
		  conn_block->new_item(*e);
		list.push_back(e);
	      }
	  }
      }
    }

    void unsupported_in_connection_block(const OptionList& options, const std::string& option)
    {
      if (options.exists(option))
	OPENVPN_LOG("NOTE: " << option << " directive is not currently supported in <connection> blocks");
    }

    bool enable_cache;
    Index index;

    std::vector<Item::Ptr> list;

    Directives directives;

    RemoteOverride* remote_override = nullptr;

    RandomAPI::Ptr rng;
  };

}

#endif
