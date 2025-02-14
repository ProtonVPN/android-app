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

#ifndef OPENVPN_ADDR_IP_H
#define OPENVPN_ADDR_IP_H

#include <algorithm>
#include <functional>
#include <string>
#include <cstring> // for std::memset

#include <openvpn/io/io.hpp>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/ostream.hpp>
#include <openvpn/common/hash.hpp>
#include <openvpn/addr/ipv4.hpp>
#include <openvpn/addr/ipv6.hpp>
#include <openvpn/addr/iperr.hpp>

#define OPENVPN_IP_THROW(ERR) throw openvpn::IP::ip_exception(ERR)

namespace openvpn::IP {

OPENVPN_EXCEPTION(ip_exception);

// This is our fundamental IP address class that handles IPv4 or IPv6
// IP addresses.  It is implemented as a discriminated union of IPv4::Addr
// and IPv6::Addr.
class Addr
{
  public:
    enum Version
    {
        UNSPEC,
        V4,
        V6
    };

    enum
    {
        V4_MASK = (1 << 0),
        V6_MASK = (1 << 1)
    };
    typedef unsigned int VersionMask;

    enum VersionSize
    {
        V4_SIZE = IPv4::Addr::SIZE,
        V6_SIZE = IPv6::Addr::SIZE,
    };

#ifndef OPENVPN_LEGACY_TITLE_ABSTRACTION

    template <typename TITLE>
    Addr(const Addr &other, const TITLE &title, const Version required_version)
        : ver(other.ver)
    {
        other.validate_version(title, required_version);
        switch (ver)
        {
        case V4:
            u.v4 = other.u.v4;
            break;
        case V6:
            u.v6 = other.u.v6;
            break;
        default:
            break;
        }
    }

    template <typename TITLE>
    Addr(const Addr &other, const TITLE &title)
        : Addr(other, title, UNSPEC)
    {
    }

    template <typename TITLE>
    Addr(const std::string &ipstr, const TITLE &title, const Version required_version)
        : Addr(from_string(ipstr, title, required_version))
    {
    }

    template <typename TITLE>
    Addr(const std::string &ipstr, const TITLE &title)
        : Addr(from_string(ipstr, title, UNSPEC))
    {
    }

    Addr(const std::string &ipstr)
        : Addr(from_string(ipstr, nullptr, UNSPEC))
    {
    }

    template <typename TITLE>
    static Addr from_string(const std::string &ipstr,
                            const TITLE &title,
                            const Version required_version)
    {
        openvpn_io::error_code ec;
        openvpn_io::ip::address a = openvpn_io::ip::make_address(ipstr, ec);
        if (ec)
            OPENVPN_IP_THROW(internal::format_error(ipstr, title, "", ec));
        const Addr ret = from_asio(a);
        if (required_version != UNSPEC && required_version != ret.ver)
            OPENVPN_IP_THROW(internal::format_error(ipstr, title, version_string_static(required_version), "wrong IP version"));
        return ret;
    }

    template <typename TITLE>
    static Addr from_string(const std::string &ipstr, const TITLE &title)
    {
        return from_string(ipstr, title, UNSPEC);
    }

    static Addr from_string(const std::string &ipstr)
    {
        return from_string(ipstr, nullptr, UNSPEC);
    }

    template <typename TITLE>
    static std::string validate(const std::string &ipstr,
                                const TITLE &title,
                                const Version required_version)
    {
        const Addr a = from_string(ipstr, title, required_version);
        return a.to_string();
    }

    template <typename TITLE>
    static std::string validate(const std::string &ipstr, const TITLE &title)
    {
        return validate(ipstr, title, UNSPEC);
    }

    static std::string validate(const std::string &ipstr)
    {
        return validate(ipstr, nullptr, UNSPEC);
    }

    template <typename TITLE>
    void validate_version(const TITLE &title, const Version required_version) const
    {
        if (required_version != UNSPEC && required_version != ver)
            OPENVPN_IP_THROW(internal::format_error(to_string(), title, version_string_static(required_version), "wrong IP version"));
    }

#else

    Addr(const Addr &other, const char *title = nullptr, Version required_version = UNSPEC)
        : ver(other.ver)
    {
        other.validate_version(title, required_version);
        switch (ver)
        {
        case V4:
            u.v4 = other.u.v4;
            break;
        case V6:
            u.v6 = other.u.v6;
            break;
        default:
            break;
        }
    }

    Addr(const std::string &ipstr, const char *title = nullptr, Version required_version = UNSPEC)
        : Addr(from_string(ipstr, title, required_version))
    {
    }

#ifndef SWIGPYTHON
    // When calling IP:Addr with None as the second parameter, Swig will
    // always pick this function and complain about not being able to convert
    // a null pointer to a const std::string reference. Hide this function, so
    // swig is forced to take the const char* variant of this function instead
    Addr(const std::string &ipstr, const std::string &title, Version required_version = UNSPEC)
        : Addr(from_string(ipstr, title.c_str(), required_version))
    {
    }
#endif

    void validate_version(const char *title, Version required_version) const
    {
        if (required_version != UNSPEC && required_version != ver)
            OPENVPN_IP_THROW(internal::format_error(to_string(), title, version_string_static(required_version), "wrong IP version"));
    }

#ifndef SWIGPYTHON
    void validate_version(const std::string &title, Version required_version) const
    {
        validate_version(title.c_str(), required_version);
    }
#endif

    static std::string validate(const std::string &ipstr, const char *title = nullptr, Version required_version = UNSPEC)
    {
        Addr a = from_string(ipstr, title, required_version);
        return a.to_string();
    }

#ifndef SWIGPYTHON
    static std::string validate(const std::string &ipstr, const std::string &title, Version required_version = UNSPEC)
    {
        return validate(ipstr, title.c_str(), required_version);
    }
#endif

    static Addr from_string(const std::string &ipstr, const char *title = nullptr, Version required_version = UNSPEC)
    {
        openvpn_io::error_code ec;
        openvpn_io::ip::address a = openvpn_io::ip::make_address(ipstr, ec);
        if (ec)
            OPENVPN_IP_THROW(internal::format_error(ipstr, title, "", ec));
        const Addr ret = from_asio(a);
        if (required_version != UNSPEC && required_version != ret.ver)
            OPENVPN_IP_THROW(internal::format_error(ipstr, title, version_string_static(required_version), "wrong IP version"));
        return ret;
    }

#endif

    static bool is_valid(const std::string &ipstr)
    {
        // fast path -- rule out validity if invalid chars
        if (std::any_of(ipstr.begin(), ipstr.end(), [](auto c)
                        { return !(std::isxdigit(c) || c == '.' || c == ':' || c == '%'); }))
            return false;

        // slow path
        {
            openvpn_io::error_code ec;
            openvpn_io::ip::make_address(ipstr, ec);
            return !ec;
        }
    }

    static Addr from_hex(Version v, const std::string &s)
    {
        switch (v)
        {
        case V4:
            return from_ipv4(IPv4::Addr::from_hex(s));
        case V6:
            return from_ipv6(IPv6::Addr::from_hex(s));
        default:
            OPENVPN_IP_THROW("from_hex: address unspecified");
        }
    }

    static Addr from_ipv4(IPv4::Addr addr)
    {
        Addr a;
        a.ver = V4;
        a.u.v4 = std::move(addr);
        return a;
    }

    static Addr from_ipv6(IPv6::Addr addr)
    {
        Addr a;
        a.ver = V6;
        a.u.v6 = std::move(addr);
        return a;
    }

    const IPv4::Addr &to_ipv4() const
    {
        if (ver == V4)
            return u.v4;
        OPENVPN_IP_THROW("to_ipv4: address is not IPv4");
    }

    IPv4::Addr to_ipv4_zero() const
    {
        switch (ver)
        {
        case V4:
            return u.v4;
        case UNSPEC:
            return IPv4::Addr::from_zero();
        default:
            OPENVPN_IP_THROW("to_ipv4_zero: address is not IPv4");
        }
    }

    const IPv6::Addr &to_ipv6() const
    {
        if (ver == V6)
            return u.v6;
        OPENVPN_IP_THROW("to_ipv6: address is not IPv6");
    }

    IPv6::Addr to_ipv6_zero() const
    {
        switch (ver)
        {
        case V6:
            return u.v6;
        case UNSPEC:
            return IPv6::Addr::from_zero();
        default:
            OPENVPN_IP_THROW("to_ipv6_zero: address is not IPv6");
        }
    }

    const IPv4::Addr &to_ipv4_nocheck() const
    {
        return u.v4;
    }

    const IPv6::Addr &to_ipv6_nocheck() const
    {
        return u.v6;
    }

    static Addr from_sockaddr(const struct sockaddr *sa)
    {
        if (sa->sa_family == AF_INET)
            return from_ipv4(IPv4::Addr::from_sockaddr(reinterpret_cast<const struct sockaddr_in *>(sa)));
        if (sa->sa_family == AF_INET6)
            return from_ipv6(IPv6::Addr::from_sockaddr(reinterpret_cast<const struct sockaddr_in6 *>(sa)));
        return Addr();
    }

    static bool sockaddr_defined(const struct sockaddr *sa)
    {
        return sa && (sa->sa_family == AF_INET || sa->sa_family == AF_INET6);
    }

    static Addr from_ulong(Version v, unsigned long ul)
    {
        switch (v)
        {
        case V4:
            return from_ipv4(IPv4::Addr::from_ulong(ul));
        case V6:
            return from_ipv6(IPv6::Addr::from_ulong(ul));
        default:
            OPENVPN_IP_THROW("from_ulong: address unspecified");
        }
    }

    // return *this as a ulong, will raise exception on overflow
    unsigned long to_ulong() const
    {
        switch (ver)
        {
        case V4:
            return u.v4.to_ulong();
        case V6:
            return u.v6.to_ulong();
        default:
            OPENVPN_IP_THROW("to_ulong: address unspecified");
        }
    }

    static Addr from_long(Version v, const long ul)
    {
        switch (v)
        {
        case V4:
            return from_ipv4(IPv4::Addr::from_long(ul));
        case V6:
            return from_ipv6(IPv6::Addr::from_long(ul));
        default:
            OPENVPN_IP_THROW("from_long: address unspecified");
        }
    }

    // return *this as a long, will raise exception on overflow
    long to_long() const
    {
        switch (ver)
        {
        case V4:
            return u.v4.to_long();
        case V6:
            return u.v6.to_long();
        default:
            OPENVPN_IP_THROW("to_long: address unspecified");
        }
    }

    // return Addr from 16 byte binary string
    static Addr from_byte_string(const unsigned char *bytestr)
    {
        Addr a;
        if (IPv6::Addr::byte_string_is_v4(bytestr))
        {
            a.ver = V4;
            a.u.v4 = IPv4::Addr::from_uint32_net(IPv6::Addr::v4_from_byte_string(bytestr));
        }
        else
        {
            a.ver = V6;
            a.u.v6 = IPv6::Addr::from_byte_string(bytestr);
        }
        return a;
    }

    // convert Addr to 16 byte binary string
    void to_byte_string(unsigned char *bytestr) const
    {
        switch (ver)
        {
        case V4:
            IPv6::Addr::v4_to_byte_string(bytestr, u.v4.to_uint32_net());
            break;
        case V6:
            u.v6.to_byte_string(bytestr);
            break;
        default:
            std::memset(bytestr, 0, 16);
            break;
        }
    }

    // convert Addr to variable length byte string
    void to_byte_string_variable(unsigned char *bytestr) const
    {
        switch (ver)
        {
        case V4:
            u.v4.to_byte_string(bytestr);
            break;
        case V6:
            u.v6.to_byte_string(bytestr);
            break;
        default:
            OPENVPN_IP_THROW("to_byte_string_variable: address unspecified");
        }
    }

    std::uint32_t to_uint32_net() const // return value in net byte order
    {
        return (ver == V4) ? u.v4.to_uint32_net() : 0;
    }

    // construct an address where all bits are zero
    static Addr from_zero(const Version v)
    {
        switch (v)
        {
        case V4:
            return from_ipv4(IPv4::Addr::from_zero());
        case V6:
            return from_ipv6(IPv6::Addr::from_zero());
        default:
            OPENVPN_IP_THROW("from_zero: IP version unspecified");
        }
    }

    // construct the "one" address
    static Addr from_one(const Version v)
    {
        switch (v)
        {
        case V4:
            return from_ipv4(IPv4::Addr::from_one());
        case V6:
            return from_ipv6(IPv6::Addr::from_one());
        default:
            OPENVPN_IP_THROW("from_one: IP version unspecified");
        }
    }

    // construct an address where all bits are one
    static Addr from_zero_complement(const Version v)
    {
        switch (v)
        {
        case V4:
            return from_ipv4(IPv4::Addr::from_zero_complement());
        case V6:
            return from_ipv6(IPv6::Addr::from_zero_complement());
        default:
            OPENVPN_IP_THROW("from_zero_complement: IP version unspecified");
        }
    }

    // validate the prefix length for the IP version
    static bool validate_prefix_len(Version v, const unsigned int prefix_len)
    {
        if (v == V4 && prefix_len <= V4_SIZE)
            return true;
        if (v == V6 && prefix_len <= V6_SIZE)
            return true;
        return false;
    }

    // build a netmask using given prefix_len
    static Addr netmask_from_prefix_len(Version v, const unsigned int prefix_len)
    {
        switch (v)
        {
        case V4:
            return from_ipv4(IPv4::Addr::netmask_from_prefix_len(prefix_len));
        case V6:
            return from_ipv6(IPv6::Addr::netmask_from_prefix_len(prefix_len));
        default:
            OPENVPN_IP_THROW("netmask_from_prefix_len: address unspecified");
        }
    }

    Addr netmask_from_this_as_extent() const
    {
        switch (ver)
        {
        case V4:
            return from_ipv4(u.v4.netmask_from_this_as_extent());
        case V6:
            return from_ipv6(u.v6.netmask_from_this_as_extent());
        default:
            OPENVPN_IP_THROW("netmask_from_extent: address unspecified");
        }
    }

    std::string to_string() const
    {
        if (ver != UNSPEC)
        {
            const openvpn_io::ip::address a = to_asio();
            std::string ret = a.to_string();
            return ret;
        }
        return "UNSPEC";
    }

    std::string to_string_bracket_ipv6() const
    {
        std::string ret;
        if (ver == V6)
            ret += '[';
        ret += to_string();
        if (ver == V6)
            ret += ']';
        return ret;
    }

    std::string to_hex() const
    {
        switch (ver)
        {
        case V4:
            return u.v4.to_hex();
        case V6:
            return u.v6.to_hex();
        default:
            OPENVPN_IP_THROW("to_hex: address unspecified");
        }
    }

    std::string arpa() const
    {
        switch (ver)
        {
        case V4:
            return u.v4.arpa();
        case V6:
            return u.v6.arpa();
        default:
            OPENVPN_IP_THROW("arpa: address unspecified");
        }
    }

    static Addr from_asio(const openvpn_io::ip::address &addr)
    {
        Addr ret;
        if (addr.is_v4())
        {
            ret.ver = V4;
            ret.u.v4 = IPv4::Addr::from_asio(addr.to_v4());
        }
        else if (addr.is_v6())
        {
            ret.ver = V6;
            ret.u.v6 = IPv6::Addr::from_asio(addr.to_v6());
        }
        else if (ret.ver == UNSPEC)
            OPENVPN_IP_THROW("from_asio: address unspecified");
        return ret;
    }

    openvpn_io::ip::address to_asio() const
    {
        switch (ver)
        {
        case V4:
            return openvpn_io::ip::address_v4(u.v4.to_asio());
        case V6:
            return openvpn_io::ip::address_v6(u.v6.to_asio());
        default:
            OPENVPN_IP_THROW("to_asio: address unspecified");
        }
    }

    Addr operator+(const long delta) const
    {
        switch (ver)
        {
        case V4:
            {
                Addr ret;
                ret.ver = V4;
                ret.u.v4 = u.v4 + delta;
                return ret;
            }
        case V6:
            {
                Addr ret;
                ret.ver = V6;
                ret.u.v6 = u.v6 + delta;
                return ret;
            }
        default:
            OPENVPN_IP_THROW("operator+: address unspecified");
        }
    }

    Addr operator-(const long delta) const
    {
        return operator+(-delta);
    }

    Addr operator<<(const unsigned int shift) const
    {
        switch (ver)
        {
        case V4:
            {
                Addr ret;
                ret.ver = V4;
                ret.u.v4 = u.v4 << shift;
                return ret;
            }
        case V6:
            {
                Addr ret;
                ret.ver = V6;
                ret.u.v6 = u.v6 << shift;
                return ret;
            }
        default:
            OPENVPN_IP_THROW("operator<<: address unspecified");
        }
    }

    Addr operator>>(const unsigned int shift) const
    {
        switch (ver)
        {
        case V4:
            {
                Addr ret;
                ret.ver = V4;
                ret.u.v4 = u.v4 >> shift;
                return ret;
            }
        case V6:
            {
                Addr ret;
                ret.ver = V6;
                ret.u.v6 = u.v6 >> shift;
                return ret;
            }
        default:
            OPENVPN_IP_THROW("operator>>: address unspecified");
        }
    }

    Addr operator~() const
    {
        switch (ver)
        {
        case V4:
            {
                Addr ret;
                ret.ver = V4;
                ret.u.v4 = ~u.v4;
                return ret;
            }
        case V6:
            {
                Addr ret;
                ret.ver = V6;
                ret.u.v6 = ~u.v6;
                return ret;
            }
        default:
            OPENVPN_IP_THROW("operator~: address unspecified");
        }
    }

    Addr network_addr(const unsigned int prefix_len) const
    {
        switch (ver)
        {
        case V4:
            {
                Addr ret;
                ret.ver = V4;
                ret.u.v4 = u.v4.network_addr(prefix_len);
                return ret;
            }
        case V6:
            {
                Addr ret;
                ret.ver = V6;
                ret.u.v6 = u.v6.network_addr(prefix_len);
                return ret;
            }
        default:
            OPENVPN_IP_THROW("network_addr: address unspecified");
        }
    }

    bool operator==(const Addr &other) const
    {
        switch (ver)
        {
        case UNSPEC:
            return other.ver == UNSPEC;
        case V4:
            if (ver == other.ver)
                return u.v4 == other.u.v4;
            break;
        case V6:
            if (ver == other.ver)
                return u.v6 == other.u.v6;
            break;
        }
        return false;
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

    // Operator overloads for binary operations
    Addr operator+(const Addr &other) const
    {
        return binary_op(other, std::plus<>{});
    }

    Addr operator-(const Addr &other) const
    {
        return binary_op(other, std::minus<>{});
    }

    Addr operator*(const Addr &other) const
    {
        return binary_op(other, std::multiplies<>{});
    }

    Addr operator/(const Addr &other) const
    {
        return binary_op(other, std::divides<>{});
    }

    Addr operator%(const Addr &other) const
    {
        return binary_op(other, std::modulus<>{});
    }

    Addr operator&(const Addr &other) const
    {
        return binary_op(other, std::bit_and<>{});
    }

    Addr operator|(const Addr &other) const
    {
        return binary_op(other, std::bit_or<>{});
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
        switch (ver)
        {
        case V4:
            return u.v4.all_zeros();
        case V6:
            return u.v6.all_zeros();
        default:
            return true;
        }
    }

    bool all_ones() const
    {
        switch (ver)
        {
        case V4:
            return u.v4.all_ones();
        case V6:
            return u.v6.all_ones();
        default:
            return false;
        }
    }

    bool is_loopback() const
    {
        switch (ver)
        {
        case V4:
            return u.v4.is_loopback();
        case V6:
            return u.v6.is_loopback();
        default:
            return false;
        }
    }

    /**
     * Determines if the IP address is a mapped IP address (e.g. \::ffff:127.0.0.1)
     */
    bool is_mapped_address() const
    {
        if (ver != V6)
            return false;

        return u.v6.is_mapped_address();
    }

    IP::Addr to_v4_addr() const
    {
        const std::uint32_t ipv4 = u.v6.get_mapped_ipv4_address();
        return IP::Addr::from_ipv4(IPv4::Addr::from_uint32(ipv4));
    };

    bool defined() const
    {
        return ver != UNSPEC;
    }

    const char *version_string() const
    {
        return version_string_static(ver);
    }

    static const char *version_string_static(Version ver)
    {
        switch (ver)
        {
        case V4:
            return "v4";
        case V6:
            return "v6";
        default:
            return "UNSPEC";
        }
    }

    Version version() const
    {
        return ver;
    }

    static VersionMask version_mask(const Version ver)
    {
        switch (ver)
        {
        case V4:
            return V4_MASK;
        case V6:
            return V6_MASK;
        default:
            return 0;
        }
    }

    VersionMask version_mask() const
    {
        return version_mask(ver);
    }

    int version_index() const
    {
        switch (ver)
        {
        case V4:
            return 0;
        case V6:
            return 1;
        default:
            OPENVPN_IP_THROW("version_index: version index undefined");
        }
    }

    int family() const
    {
        switch (ver)
        {
        case V4:
            return AF_INET;
        case V6:
            return AF_INET6;
        default:
            return -1;
        }
    }

    bool is_compatible(const Addr &other) const
    {
        return ver == other.ver;
    }

    bool is_ipv6() const
    {
        return ver == V6;
    }

    void verify_version_consistency(const Addr &other) const
    {
        if (!is_compatible(other))
            OPENVPN_IP_THROW("verify_version_consistency: version inconsistency");
    }

    // throw exception if address is not a valid netmask
    void validate_netmask() const
    {
        prefix_len();
    }

    // number of network bits in netmask,
    // throws exception if addr is not a netmask
    unsigned int prefix_len() const
    {
        switch (ver)
        {
        case V4:
            return u.v4.prefix_len();
        case V6:
            return u.v6.prefix_len();
        default:
            OPENVPN_IP_THROW("prefix_len: address unspecified");
        }
    }

    // IPv6 scope ID or -1 if not IPv6
    int scope_id() const
    {
        return ver == V6 ? u.v6.scope_id() : -1;
    }

    // number of host bits in netmask
    unsigned int host_len() const
    {
        switch (ver)
        {
        case V4:
            return u.v4.host_len();
        case V6:
            return u.v6.host_len();
        default:
            OPENVPN_IP_THROW("host_len: address unspecified");
        }
    }

    // return the number of host addresses contained within netmask
    Addr extent_from_netmask() const
    {
        switch (ver)
        {
        case V4:
            return from_ipv4(u.v4.extent_from_netmask());
        case V6:
            return from_ipv6(u.v6.extent_from_netmask());
        default:
            OPENVPN_IP_THROW("extent_from_netmask: address unspecified");
        }
    }

    // address size in bits
    unsigned int size() const
    {
        return version_size(ver);
    }

    // address size in bytes
    unsigned int size_bytes() const
    {
        return size() / 8;
    }

    // address size in bits of particular IP version
    static unsigned int version_size(Version v)
    {
        switch (v)
        {
        case V4:
            return IPv4::Addr::SIZE;
        case V6:
            return IPv6::Addr::SIZE;
        default:
            return 0;
        }
    }

    template <typename HASH>
    void hash(HASH &h) const
    {
        switch (ver)
        {
        case Addr::V4:
            u.v4.hash(h);
            break;
        case Addr::V6:
            u.v6.hash(h);
            break;
        default:
            break;
        }
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

    Addr()
        : ver(UNSPEC)
    {
    }

    void reset()
    {
        ver = UNSPEC;
    }

    Addr &operator=(const Addr &other)
    {
        ver = other.ver;
        u = other.u;
        return *this;
    }

    Addr &operator++()
    {
        switch (ver)
        {
        case V4:
            ++u.v4;
            break;
        case V6:
            ++u.v6;
            break;
        default:
            break;
        }
        return *this;
    }

    Addr &operator+=(const long delta)
    {
        switch (ver)
        {
        case V4:
            u.v4 += delta;
            break;
        case V6:
            u.v6 += delta;
            break;
        default:
            break;
        }
        return *this;
    }

    Addr &operator-=(const long delta)
    {
        switch (ver)
        {
        case V4:
            u.v4 -= delta;
            break;
        case V6:
            u.v6 -= delta;
            break;
        default:
            break;
        }
        return *this;
    }

    void reset_ipv4_from_uint32(const IPv4::Addr::base_type addr)
    {
        ver = V4;
        u.v4 = IPv4::Addr::from_uint32(addr);
    }

  private:
    union {
        IPv4::Addr v4;
        IPv6::Addr v6;
    } u{};

    Version ver;

    template <typename Comparator>
    bool compare(const Addr &other, Comparator comp) const
    {
        if (ver == other.ver)
        {
            switch (ver)
            {
            case V4:
                return comp(u.v4, other.u.v4);
            case V6:
                return comp(u.v6, other.u.v6);
            default:
                return false;
            }
        }
        return comp(ver, other.ver);
    }

    template <typename BinaryOp>
    Addr binary_op(const Addr &other, BinaryOp op) const
    {
        if (ver != other.ver)
        {
            OPENVPN_IP_THROW("binop: version inconsistency");
        }

        Addr ret;
        ret.ver = ver;

        switch (ver)
        {
        case V4:
            ret.u.v4 = op(u.v4, other.u.v4);
            break;
        case V6:
            ret.u.v6 = op(u.v6, other.u.v6);
            break;
        default:
            OPENVPN_IP_THROW("binop: address unspecified");
        }

        return ret;
    }
};

OPENVPN_OSTREAM(Addr, to_string)
} // namespace openvpn::IP

#ifdef USE_OPENVPN_HASH
OPENVPN_HASH_METHOD(openvpn::IP::Addr, hashval);
#endif

#endif
