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

#ifndef OPENVPN_ADDR_POOL_H
#define OPENVPN_ADDR_POOL_H

#include <deque>
#include <optional>
#include <string>
#include <unordered_map>

#include <openvpn/common/exception.hpp>
#include <openvpn/addr/ip.hpp>
#include <openvpn/addr/range.hpp>

namespace openvpn::IP {

// Maintain a pool of IP addresses.
// Should be IP::Addr, IPv4::Addr, or IPv6::Addr.
template <typename ADDR>
class PoolType
{
  public:
    PoolType() = default;

    /**
     * @brief Adds range of addresses to pool (pool will own the addresses).
     * @param range RangeType of IP Addresses
     */
    void add_range(const RangeType<ADDR> &range)
    {
        for (const auto &address : range)
        {
            add_addr(address);
        }
    }

    // Add single address to pool (pool will own the address).
    void add_addr(const ADDR &addr)
    {
        auto [iter, inserted] = map.try_emplace(addr, false);
        if (inserted)
        {
            freelist.push_back(addr);
        }
    }

    /**
     * @brief Returns number of pool addresses currently in use
     * @return number of pool addresses currently in use
     */
    [[nodiscard]] size_t n_in_use() const noexcept
    {
        return map.size() - freelist.size();
    }

    /**
     * @brief Returns number of free pool addresses
     * @return number of free pool addresses
     */
    [[nodiscard]] size_t n_free() const noexcept
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

    /**
     * @brief Acquires a specific address from the pool.
     *
     * This function attempts to acquire a specific address from the pool. If the address is
     * available, it marks the address as in use and returns true. If the address is not available,
     * it returns false.
     *
     * @param addr The IP address to acquire.
     * @return true if the address was successfully acquired, false otherwise.
     */
    bool acquire_specific_addr(const ADDR &addr)
    {
        auto optional_iterator = is_address_available(addr);
        if (optional_iterator)
        {
            (*optional_iterator)->second = true;
            return true;
        }
        return false;
    }

    // Return a previously acquired address to the pool.  Does nothing if
    // (a) the address is owned by the pool and marked as free, or
    // (b) the address is not owned by the pool.
    void release_addr(const ADDR &addr)
    {
        auto optional_iterator = is_address_in_use(addr);
        if (optional_iterator)
        {
            freelist.push_back(addr);
            (*optional_iterator)->second = false;
        }
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

    virtual ~PoolType() = default;

  private:
    std::deque<ADDR> freelist;
    std::unordered_map<ADDR, bool> map;

    /**
     * @brief Checks if address is available (free)
     * @param addr IP Address to check
     * @return Optional iterator to position; std::nullopt if address is not available
     */
    auto is_address_available(const ADDR &addr) -> std::optional<decltype(map.begin())> // Easy decltype for map iterator
    {
        auto it = map.find(addr);
        if (it != map.end() && !it->second)
            return it;
        return std::nullopt;
    }

    /**
     * @brief Checks if address is in use
     * @param addr IP Address to check
     * @return Optional containing iterator to position if found, std::nullopt otherwise
     */
    auto is_address_in_use(const ADDR &addr) -> std::optional<decltype(map.begin())> // Easy decltype for map iterator
    {
        if (auto it = map.find(addr); it != map.end())
            return it;
        return std::nullopt;
    }
};

typedef PoolType<IP::Addr> Pool;
} // namespace openvpn::IP

#endif
