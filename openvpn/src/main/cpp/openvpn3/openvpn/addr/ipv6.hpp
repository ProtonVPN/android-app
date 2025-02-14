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

#ifndef OPENVPN_ADDR_IPV6_H
#define OPENVPN_ADDR_IPV6_H

#include <cstring> // for std::memcpy, std::memset
#include <cstdint> // for std::uint32_t

#include <openvpn/io/io.hpp>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/ostream.hpp>
#include <openvpn/common/socktypes.hpp>
#include <openvpn/common/ffs.hpp>
#include <openvpn/common/hexstr.hpp>
#include <openvpn/addr/ipv4.hpp>
#include <openvpn/addr/iperr.hpp>

namespace openvpn::IP {
class Addr;
}

// Fundamental classes for representing an IPv6 IP address.

namespace openvpn::IPv6 {

OPENVPN_EXCEPTION(ipv6_exception);

class Addr // NOTE: must be union-legal, so default constructor does not initialize
{
    friend class IP::Addr;

  public:
    enum
    {
        SIZE = 128
    };

    static constexpr int ip_version()
    {
        return 6;
    }

    static constexpr bool defined()
    {
        return true;
    }

    static Addr from_addr(const Addr &addr)
    {
        return addr;
    }

    static Addr from_in6_addr(const in6_addr *in6)
    {
        Addr ret;
        /* Alignment of in6_addr is only 4 while our ipv6 addr requires an
         * alignment of 8 due to its uint64 members, so use memcpy to copy it
         */
        ipv6addr src;
        std::memcpy(&src, in6->s6_addr, sizeof(ipv6addr));
        network_to_host_order(&ret.u, &src);
        return ret;
    }

    in6_addr to_in6_addr() const
    {
        in6_addr ret;
        host_to_network_order(reinterpret_cast<ipv6addr *>(&ret), &u);
        return ret;
    }

    static Addr from_sockaddr(const sockaddr_in6 *sa)
    {
        Addr ret;
        network_to_host_order(&ret.u, reinterpret_cast<const ipv6addr *>(sa->sin6_addr.s6_addr));
        ret.scope_id_ = sa->sin6_scope_id;
        return ret;
    }

    sockaddr_in6 to_sockaddr(const unsigned short port = 0) const
    {
        sockaddr_in6 ret = {};
        ret.sin6_family = AF_INET6;
        ret.sin6_port = htons(port);
        host_to_network_order(reinterpret_cast<ipv6addr *>(&ret.sin6_addr.s6_addr), &u);
        ret.sin6_scope_id = scope_id_;
#ifdef SIN6_LEN
        /* This is defined on both macOS and FreeBSD that have the sin6_len member */
        ret.sin6_len = sizeof(sockaddr_in6);
#endif
        return ret;
    }

#ifndef OPENVPN_LEGACY_TITLE_ABSTRACTION

    template <typename TITLE>
    static Addr from_string(const std::string &ipstr, const TITLE &title)
    {
        openvpn_io::error_code ec;
        openvpn_io::ip::address_v6 a = openvpn_io::ip::make_address_v6(ipstr, ec);
        if (ec)
            throw ipv6_exception(IP::internal::format_error(ipstr, title, "v6", ec));
        return from_asio(a);
    }

    static Addr from_string(const std::string &ipstr)
    {
        return from_string(ipstr, nullptr);
    }

#else

    static Addr from_string(const std::string &ipstr, const char *title = nullptr)
    {
        openvpn_io::error_code ec;
        openvpn_io::ip::address_v6 a = openvpn_io::ip::make_address_v6(ipstr, ec);
        if (ec)
            throw ipv6_exception(IP::internal::format_error(ipstr, title, "v6", ec));
        return from_asio(a);
    }

#endif

    std::string to_string() const
    {
        const openvpn_io::ip::address_v6 a = to_asio();
        std::string ret = a.to_string();
#ifdef UNIT_TEST
        return string::to_lower_copy(ret);
#else
        return ret;
#endif
    }

    static Addr from_hex(const std::string &s)
    {
        Addr ret;
        ret.zero();
        size_t len = s.length();
        size_t base = 0;
        if (len > 0 && s[len - 1] == 'L')
            len -= 1;
        if (len >= 2 && s[0] == '0' && s[1] == 'x')
        {
            base = 2;
            len -= 2;
        }
        if (len < 1 || len > 32)
            throw ipv6_exception("parse hex error");
        size_t di = (len - 1) >> 1;
        for (int i = (len & 1) ? -1 : 0; i < static_cast<int>(len); i += 2)
        {
            const size_t idx = base + i;
            const int bh = (i >= 0) ? parse_hex_char(s[idx]) : 0;
            const int bl = parse_hex_char(s[idx + 1]);
            if (bh == -1 || bl == -1)
                throw ipv6_exception("parse hex error");
            ret.u.bytes[Endian::e16(di--)] = static_cast<unsigned char>((bh << 4) + bl);
        }
        return ret;
    }

    std::string to_hex() const
    {
        std::string ret;
        ret.reserve(32);
        bool firstnonzero = false;
        for (size_t i = 0; i < 16; ++i)
        {
            const unsigned char b = u.bytes[Endian::e16rev(i)];
            if (b || firstnonzero || i == 15)
            {
                const char bh = static_cast<decltype(bh)>(b >> 4);
                if (bh || firstnonzero)
                    ret += render_hex_char(bh);
                ret += render_hex_char(b & 0x0F);
                firstnonzero = true;
            }
        }
        return ret;
    }

    static Addr from_ulong(unsigned long ul)
    {
        Addr ret;
        ret.u.u64[Endian::e2(0)] = ul;
        ret.u.u64[Endian::e2(1)] = 0;
        return ret;
    }

    unsigned long to_ulong() const
    {
        const unsigned long ret = static_cast<unsigned long>(u.u64[Endian::e2(0)]);
        const auto cmp = std::uint64_t(ret);
        if (u.u64[Endian::e2(1)] || cmp != u.u64[Endian::e2(0)])
            throw ipv6_exception("overflow in conversion from IPv6.Addr to unsigned long");
        return ret;
    }

    static Addr from_long(long ul)
    {
        bool neg = false;
        Addr ret;
        if (ul < 0)
        {
            ul = -(ul + 1);
            neg = true;
        }
        ret.u.u64[Endian::e2(0)] = std::uint64_t(ul);
        ret.u.u64[Endian::e2(1)] = 0;
        if (neg)
            ret.negate();
        return ret;
    }

    long to_long() const
    {
        bool neg = false;
        Addr a = *this;
        if (a.u.u64[Endian::e2(1)])
        {
            a.negate();
            neg = true;
        }
        const long ret = static_cast<long>(a.u.u64[Endian::e2(0)]);
        const auto cmp = std::uint64_t(ret);
        if (a.u.u64[Endian::e2(1)] || cmp != a.u.u64[Endian::e2(0)])
            throw ipv6_exception("overflow in conversion from IPv6.Addr to long");
        return neg ? -(ret + 1) : ret;
    }

    static std::string arpa()
    {
        throw ipv6_exception("arpa() not implemented");
    }

    static Addr from_asio(const openvpn_io::ip::address_v6 &asio_addr)
    {
        Addr ret;
        ipv6addr addr{};
        addr.asio_bytes = asio_addr.to_bytes();
        network_to_host_order(&ret.u, &addr);
        ret.scope_id_ = asio_addr.scope_id();
        return ret;
    }

    static Addr from_byte_string(const unsigned char *bytestr)
    {
        /* bytestr might not be correctly aligned to an 8 byte boundary
         * that ipv6addr requires, so use a temporary object that is
         * properly aligned */
        Addr ret;
        ipv6addr src{};
        std::memcpy(&src, bytestr, sizeof(src));
        network_to_host_order(&ret.u, &src);
        return ret;
    }

    void to_byte_string(unsigned char *bytestr) const
    {
        /* bytestr might not be correctly aligned to an 8 byte boundary
         * that ipv6addr requires, so use a temporary object that is
         * properly aligned */
        ipv6addr ret{};
        host_to_network_order(&ret, &u);
        std::memcpy(bytestr, &ret, sizeof(ret));
    }

    static void v4_to_byte_string(unsigned char *bytestr,
                                  const std::uint32_t v4addr)
    {
        ipv6addr ret{};
        ret.u32[0] = ret.u32[1] = ret.u32[2] = 0;
        ret.u32[3] = v4addr;

        std::memcpy(bytestr, &ret, sizeof(ret));
    }

    static bool byte_string_is_v4(const unsigned char *bytestr)
    {
        ipv6addr a{};
        std::memcpy(&a, bytestr, sizeof(a));
        return a.u32[0] == 0 && a.u32[1] == 0 && a.u32[2] == 0;
    }

    static std::uint32_t v4_from_byte_string(const unsigned char *bytestr)
    {
        ipv6addr a{};
        std::memcpy(&a, bytestr, sizeof(a));
        return a.u32[3];
    }

    openvpn_io::ip::address_v6 to_asio() const
    {
        ipv6addr addr;
        host_to_network_order(&addr, &u);
        return openvpn_io::ip::address_v6(addr.asio_bytes, scope_id_);
    }

    static Addr from_zero()
    {
        Addr ret;
        ret.zero();
        return ret;
    }

    static Addr from_one()
    {
        Addr ret;
        ret.one();
        return ret;
    }

    static Addr from_zero_complement()
    {
        Addr ret;
        ret.zero_complement();
        return ret;
    }

    static Addr netmask_from_prefix_len(const unsigned int prefix_len)
    {
        Addr ret;
        ret.prefix_len_to_netmask(prefix_len);
        return ret;
    }

    Addr netmask_from_this_as_extent() const
    {
        const Addr lb = *this - 1;
        for (size_t i = 4; i-- > 0;)
        {
            const std::uint32_t v = lb.u.u32[Endian::e4(i)];
            if (v)
                return netmask_from_prefix_len(SIZE - ((static_cast<unsigned int>(i) << 5) + find_last_set(v)));
        }
        return from_zero_complement();
    }

    Addr operator&(const Addr &other) const
    {
        Addr ret;
        ret.scope_id_ = scope_id_;
        ret.u.u64[0] = u.u64[0] & other.u.u64[0];
        ret.u.u64[1] = u.u64[1] & other.u.u64[1];
        return ret;
    }

    Addr operator|(const Addr &other) const
    {
        Addr ret;
        ret.scope_id_ = scope_id_;
        ret.u.u64[0] = u.u64[0] | other.u.u64[0];
        ret.u.u64[1] = u.u64[1] | other.u.u64[1];
        return ret;
    }

    Addr operator+(const long delta) const
    {
        Addr ret = *this;
        ret.u.u64[Endian::e2(0)] += delta;
        ret.u.u64[Endian::e2(1)] += (delta >= 0)
                                        ? (ret.u.u64[Endian::e2(0)] < u.u64[Endian::e2(0)])
                                        : -(ret.u.u64[Endian::e2(0)] > u.u64[Endian::e2(0)]);
        return ret;
    }

    Addr operator+(const Addr &other) const
    {
        Addr ret = *this;
        add(ret.u, other.u);
        return ret;
    }

    Addr operator-(const long delta) const
    {
        return operator+(-delta);
    }

    Addr operator-(const Addr &other) const
    {
        Addr ret = *this;
        sub(ret.u, other.u);
        return ret;
    }

    Addr operator*(const Addr &d) const
    {
        Addr m = d;
        Addr ret = from_zero();
        for (unsigned int i = 0; i < SIZE; ++i)
        {
            if (bit(i))
                ret += m;
            m <<= 1;
        }
        return ret;
    }

    Addr operator/(const Addr &d) const
    {
        Addr q, r;
        div(*this, d, q, r);
        return q;
    }

    Addr operator%(const Addr &d) const
    {
        Addr q, r;
        div(*this, d, q, r);
        return r;
    }

    Addr operator<<(const unsigned int shift) const
    {
        Addr ret = *this;
        shiftl128(ret.u.u64[Endian::e2(0)],
                  ret.u.u64[Endian::e2(1)],
                  shift);
        return ret;
    }

    Addr operator>>(const unsigned int shift) const
    {
        Addr ret = *this;
        shiftr128(ret.u.u64[Endian::e2(0)],
                  ret.u.u64[Endian::e2(1)],
                  shift);
        return ret;
    }

    Addr operator~() const
    {
        Addr ret;
        ret.scope_id_ = scope_id_;
        ret.u.u64[0] = ~u.u64[0];
        ret.u.u64[1] = ~u.u64[1];
        return ret;
    }

    // return the network that contains the current address
    Addr network_addr(const unsigned int prefix_len) const
    {
        return *this & netmask_from_prefix_len(prefix_len);
    }

    bool operator==(const Addr &other) const
    {
        return u.u64[0] == other.u.u64[0] && u.u64[1] == other.u.u64[1] && scope_id_ == other.scope_id_;
    }

    bool operator!=(const Addr &other) const
    {
        return !operator==(other);
    }


    bool operator<(const Addr &other) const
    {
        return compare(other, std::less<>{});
    }
    bool operator>(const Addr &other) const
    {
        return compare(other, std::greater<>{});
    }
    bool operator<=(const Addr &other) const
    {
        return compare(other, std::less_equal<>{});
    }
    bool operator>=(const Addr &other) const
    {
        return compare(other, std::greater_equal<>{});
    }

    bool unspecified() const
    {
        return all_zeros();
    }

    bool specified() const
    {
        return !unspecified();
    }

    bool all_zeros() const
    {
        return u.u64[0] == 0 && u.u64[1] == 0;
    }

    bool all_ones() const
    {
        return u.u64[0] == ~std::uint64_t(0) && u.u64[1] == ~std::uint64_t(0);
    }

    bool is_loopback() const // ::1
    {
        return u.u64[Endian::e2(1)] == 0 && u.u64[Endian::e2(0)] == 1;
    }

    bool is_mapped_address() const
    {
        return u.u32[Endian::e4(1)] == 0xffff && u.u32[Endian::e4(2)] == 0 && u.u32[Endian::e4(3)] == 0;
    }

    std::uint32_t get_mapped_ipv4_address() const
    {
        return u.u32[Endian::e2(0)];
    }

    bool bit(unsigned int pos) const
    {
        if (pos < 64)
            return (u.u64[Endian::e2(0)] & (std::uint64_t(1) << pos)) != 0;
        return (u.u64[Endian::e2(1)] & (std::uint64_t(1) << (pos - 64))) != 0;
    }

    // number of network bits in netmask,
    // throws exception if addr is not a netmask
    unsigned int prefix_len() const
    {
        int idx = -1;

        if (u.u32[Endian::e4(3)] != ~std::uint32_t(0))
        {
            if (!u.u32[Endian::e4(0)] && !u.u32[Endian::e4(1)] && !u.u32[Endian::e4(2)])
                idx = 0;
        }
        else if (u.u32[Endian::e4(2)] != ~std::uint32_t(0))
        {
            if (!u.u32[Endian::e4(0)] && !u.u32[Endian::e4(1)])
                idx = 1;
        }
        else if (u.u32[Endian::e4(1)] != ~std::uint32_t(0))
        {
            if (!u.u32[Endian::e4(0)])
                idx = 2;
        }
        else
            idx = 3;

        if (idx >= 0)
        {
            const int ret = IPv4::Addr::prefix_len_32(u.u32[Endian::e4rev(idx)]);
            if (ret >= 0)
                return ret + (idx << 5);
        }
        throw ipv6_exception("malformed netmask");
    }

    // number of host bits in netmask
    unsigned int host_len() const
    {
        return SIZE - prefix_len();
    }

    // return the number of host addresses contained within netmask
    Addr extent_from_netmask() const
    {
        const unsigned int hl = host_len();
        if (hl < SIZE)
        {
            Addr a;
            a.one();
            return a << hl;
        }
        if (hl == SIZE)
            return from_zero();
        throw ipv6_exception("extent overflow");
    }

    // address size in bits
    static unsigned int size()
    {
        return SIZE;
    }

    template <typename HASH>
    void hash(HASH &h) const
    {
        h(u.bytes, sizeof(u.bytes));
    }

#ifdef USE_OPENVPN_HASH
    std::uint64_t hashval() const
    {
        Hash64 h;
        hash(h);
        return h.value();
    }
#endif

#ifdef OPENVPN_IP_IMMUTABLE
  private:
#endif

    void negate()
    {
        u.u64[0] = ~u.u64[0];
        u.u64[1] = ~u.u64[1];
    }

    void zero()
    {
        u.u64[0] = 0;
        u.u64[1] = 0;
    }

    void zero_complement()
    {
        u.u64[0] = ~std::uint64_t(0);
        u.u64[1] = ~std::uint64_t(0);
    }

    void one()
    {
        u.u64[0] = 1;
        u.u64[1] = 0;
    }

    Addr &operator++()
    {
        if (++u.u64[Endian::e2(0)] == 0)
            ++u.u64[Endian::e2(1)];
        return *this;
    }

    Addr &operator+=(const long delta)
    {
        *this = *this + delta;
        return *this;
    }

    Addr &operator-=(const long delta)
    {
        return operator+=(-delta);
    }

    Addr &operator+=(const Addr &other)
    {
        add(u, other.u);
        return *this;
    }

    Addr &operator-=(const Addr &other)
    {
        sub(u, other.u);
        return *this;
    }

    Addr &operator<<=(const unsigned int shift)
    {
        shiftl128(u.u64[Endian::e2(0)],
                  u.u64[Endian::e2(1)],
                  shift);
        return *this;
    }

    Addr &operator>>=(const unsigned int shift)
    {
        shiftr128(u.u64[Endian::e2(0)],
                  u.u64[Endian::e2(1)],
                  shift);
        return *this;
    }

    void set_clear_bit(unsigned int pos, bool value)
    {
        if (pos < 64)
        {
            if (value)
                u.u64[Endian::e2(0)] |= (std::uint64_t(1) << pos);
            else
                u.u64[Endian::e2(0)] &= ~(std::uint64_t(1) << pos);
        }
        else
        {
            if (value)
                u.u64[Endian::e2(1)] |= (std::uint64_t(1) << (pos - 64));
            else
                u.u64[Endian::e2(1)] &= ~(std::uint64_t(1) << (pos - 64));
        }
    }

    void set_bit(unsigned int pos, bool value)
    {
        if (value)
        {
            if (pos < 64)
                u.u64[Endian::e2(0)] |= (std::uint64_t(1) << pos);
            else
                u.u64[Endian::e2(1)] |= (std::uint64_t(1) << (pos - 64));
        }
    }

    static void div(const Addr &numerator, const Addr &denominator, Addr &quotient, Addr &remainder)
    {
        if (denominator.all_zeros())
            throw ipv6_exception("division by 0");
        quotient = from_zero();
        remainder = numerator;
        Addr mask_low = from_zero();
        Addr mask_high = denominator;
        for (unsigned int i = 0; i < SIZE; ++i)
        {
            mask_low >>= 1;
            mask_low.set_bit(SIZE - 1, mask_high.bit(0));
            mask_high >>= 1;
            if (mask_high.all_zeros() && remainder >= mask_low)
            {
                remainder -= mask_low;
                quotient.set_bit((SIZE - 1) - i, true);
            }
        }
    }

    int scope_id() const
    {
        return scope_id_;
    }

  private:
    union ipv6addr {
        std::uint64_t u64[2];
        std::uint32_t u32[4]; // generally stored in host byte order
        unsigned char bytes[16];
        openvpn_io::ip::address_v6::bytes_type asio_bytes;
    };

    void prefix_len_to_netmask_unchecked(const unsigned int prefix_len)
    {
        if (prefix_len > 0)
        {
            const unsigned int pl = prefix_len - 1;
            const std::uint32_t mask = ~((1u << (31 - (pl & 31))) - 1);
            switch (pl >> 5)
            {
            case 0:
                u.u32[Endian::e4(0)] = 0;
                u.u32[Endian::e4(1)] = 0;
                u.u32[Endian::e4(2)] = 0;
                u.u32[Endian::e4(3)] = mask;
                break;
            case 1:
                u.u32[Endian::e4(0)] = 0;
                u.u32[Endian::e4(1)] = 0;
                u.u32[Endian::e4(2)] = mask;
                u.u32[Endian::e4(3)] = ~0;
                break;
            case 2:
                u.u32[Endian::e4(0)] = 0;
                u.u32[Endian::e4(1)] = mask;
                u.u32[Endian::e4(2)] = ~0;
                u.u32[Endian::e4(3)] = ~0;
                break;
            case 3:
                u.u32[Endian::e4(0)] = mask;
                u.u32[Endian::e4(1)] = ~0;
                u.u32[Endian::e4(2)] = ~0;
                u.u32[Endian::e4(3)] = ~0;
                break;
            }
        }
        else
            zero();
    }

    void prefix_len_to_netmask(const unsigned int prefix_len)
    {
        if (prefix_len <= SIZE)
            return prefix_len_to_netmask_unchecked(prefix_len);
        throw ipv6_exception("bad prefix len");
    }

    static void host_to_network_order(ipv6addr *dest, const ipv6addr *src)
    {
        dest->u32[0] = htonl(src->u32[Endian::e4rev(0)]);
        dest->u32[1] = htonl(src->u32[Endian::e4rev(1)]);
        dest->u32[2] = htonl(src->u32[Endian::e4rev(2)]);
        dest->u32[3] = htonl(src->u32[Endian::e4rev(3)]);
    }

    static void network_to_host_order(ipv6addr *dest, const ipv6addr *src)
    {
        dest->u32[0] = ntohl(src->u32[Endian::e4rev(0)]);
        dest->u32[1] = ntohl(src->u32[Endian::e4rev(1)]);
        dest->u32[2] = ntohl(src->u32[Endian::e4rev(2)]);
        dest->u32[3] = ntohl(src->u32[Endian::e4rev(3)]);
    }


    static void shiftl128(std::uint64_t &low,
                          std::uint64_t &high,
                          unsigned int shift)
    {
        if (shift == 1)
        {
            high <<= 1u;
            if (low & (std::uint64_t(1) << 63u))
                high |= 1u;
            low <<= 1u;
        }
        else if (shift == 0)
        {
            // Nothing to do
        }
        else if (shift == 128)
        {
            /* shifts everything away */
            high = low = 0;
        }
        else if (shift < 64)
        {
            high = (high << shift) | (low >> (64u - shift));
            low <<= shift;
        }
        else if (shift < 128) /* in [64, 127] */
        {
            high = low;
            low = 0;
            /* Shift is guaranteed to be in [0, 63], so
             * recursion will not come here again */
            shiftl128(low, high, shift - 64);
        }
        else
            throw ipv6_exception("l-shift too large");
    }

    static void shiftr128(std::uint64_t &low,
                          std::uint64_t &high,
                          unsigned int shift)
    {
        if (shift == 1)
        {
            low >>= 1u;
            if (high & 1u)
                low |= (std::uint64_t(1) << 63u);
            high >>= 1u;
        }
        else if (shift == 0)
        {
        }
        else if (shift < 64)
        {
            low = (low >> shift) | (high << (64 - shift));
            high >>= shift;
        }
        else if (shift <= 128) // shift in [64, 128]
        {
            low = high;
            high = 0;
            shiftr128(low, high, shift - 64);
        }
        else
            throw ipv6_exception("r-shift too large");
    }

    static void add(ipv6addr &dest, const ipv6addr &src)
    {
        const std::uint64_t dorigl = dest.u64[Endian::e2(0)];
        dest.u64[Endian::e2(0)] += src.u64[Endian::e2(0)];
        dest.u64[Endian::e2(1)] += src.u64[Endian::e2(1)];
        // check for overflow of low 64 bits, add carry to high
        if (dest.u64[Endian::e2(0)] < dorigl)
            ++dest.u64[Endian::e2(1)];
    }

    static void sub(ipv6addr &dest, const ipv6addr &src)
    {
        const std::uint64_t dorigl = dest.u64[Endian::e2(0)];
        dest.u64[Endian::e2(0)] -= src.u64[Endian::e2(0)];
        dest.u64[Endian::e2(1)] -= src.u64[Endian::e2(1)]
                                   + (dorigl < dest.u64[Endian::e2(0)]);
    }


    template <typename Comparator>
    bool compare(const Addr &other, Comparator comp) const
    {

        if (u.u64[Endian::e2(1)] == other.u.u64[Endian::e2(1)])
        {
            if (u.u64[Endian::e2(0)] != other.u.u64[Endian::e2(0)])
                return comp(u.u64[Endian::e2(0)], other.u.u64[Endian::e2(0)]);
            return comp(scope_id_, other.scope_id_);
        }
        return comp(u.u64[Endian::e2(1)], other.u.u64[Endian::e2(1)]);
    }

    ipv6addr u;
    unsigned int scope_id_ = 0;
};

OPENVPN_OSTREAM(Addr, to_string)
} // namespace openvpn::IPv6

#ifdef USE_OPENVPN_HASH
OPENVPN_HASH_METHOD(openvpn::IPv6::Addr, hashval);
#endif

#endif // OPENVPN_ADDR_IPV6_H
