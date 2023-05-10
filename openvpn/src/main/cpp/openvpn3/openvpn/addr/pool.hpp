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

#ifndef OPENVPN_ADDR_POOL_H
#define OPENVPN_ADDR_POOL_H

#include <string>
#include <sstream>
#include <deque>
#include <unordered_map>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>

#include <openvpn/addr/ip.hpp>
#include <openvpn/addr/range.hpp>

namespace openvpn {
namespace IP {

// Maintain a pool of IP addresses.
// A should be IP::Addr, IPv4::Addr, or IPv6::Addr.
template <typename ADDR>
class PoolType
{
  public:
    PoolType() = default;

    // Add range of addresses to pool (pool will own the addresses).
    void add_range(const RangeType<ADDR> &range)
    {
        auto iter = range.iterator();
        while (iter.more())
        {
            const ADDR &a = iter.addr();
            add_addr(a);
            iter.next();
        }
    }

    // Add single address to pool (pool will own the address).
    void add_addr(const ADDR &addr)
    {
        auto e = map.find(addr);
        if (e == map.end())
        {
            freelist.push_back(addr);
            map[addr] = false;
        }
    }

    // Return number of pool addresses currently in use.
    size_t n_in_use() const
    {
        return map.size() - freelist.size();
    }

    // Return number of pool addresses currently in use.
    size_t n_free() const
    {
        return freelist.size();
    }

    // Acquire an address from pool.  Returns true if successful,
    // with address placed in dest, or false if pool depleted.
    bool acquire_addr(ADDR &dest)
    {
        while (true)
        {
            freelist_fill();
            if (freelist.empty())
                return false;
            const ADDR &a = freelist.front();
            auto e = map.find(a);
            if (e == map.end()) // any address in freelist must exist in map
                throw Exception("PoolType: address in freelist doesn't exist in map");
            if (!e->second)
            {
                e->second = true;
                dest = a;
                freelist.pop_front();
                return true;
            }
            freelist.pop_front();
        }
    }

    // Acquire a specific address from pool, returning true if
    // successful, or false if the address is not available.
    bool acquire_specific_addr(const ADDR &addr)
    {
        auto e = map.find(addr);
        if (e != map.end() && !e->second)
        {
            e->second = true;
            return true;
        }
        else
            return false;
    }

    // Return a previously acquired address to the pool.  Does nothing if
    // (a) the address is owned by the pool and marked as free, or
    // (b) the address is not owned by the pool.
    void release_addr(const ADDR &addr)
    {
        auto e = map.find(addr);
        if (e != map.end() && e->second)
        {
            freelist.push_back(addr);
            e->second = false;
        }
    }

    // DEBUGGING -- get the map load factor
    float load_factor() const
    {
        return map.load_factor();
    }

    // Override to refill freelist on demand
    virtual void freelist_fill()
    {
    }

    std::string to_string() const
    {
        std::string ret;
        for (const auto &e : map)
        {
            if (e.second)
            {
                ret += e.first.to_string();
                ret += '\n';
            }
        }
        return ret;
    }

    virtual ~PoolType<ADDR>() = default;

  private:
    std::deque<ADDR> freelist;
    std::unordered_map<ADDR, bool> map;
};

typedef PoolType<IP::Addr> Pool;
} // namespace IP
} // namespace openvpn

#endif
