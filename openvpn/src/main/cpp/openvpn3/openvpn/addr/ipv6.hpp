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

#ifndef OPENVPN_ADDR_IPV6_H
#define OPENVPN_ADDR_IPV6_H

#include <cstring>           // for std::memcpy, std::memset
#include <algorithm>         // for std::min
#include <cstdint>           // for std::uint32_t

#include <openvpn/io/io.hpp>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/ostream.hpp>
#include <openvpn/common/socktypes.hpp>
#include <openvpn/common/ffs.hpp>
#include <openvpn/common/hexstr.hpp>
#include <openvpn/common/hash.hpp>
#include <openvpn/addr/ipv4.hpp>
#include <openvpn/addr/iperr.hpp>

namespace openvpn {
  namespace IP {
    class Addr;
  }

  // Fundamental classes for representing an IPv6 IP address.

  namespace IPv6 {

    OPENVPN_EXCEPTION(ipv6_exception);

    class Addr // NOTE: must be union-legal, so default constructor does not initialize
    {
      friend class IP::Addr;

    public:
      enum { SIZE=128 };

      static constexpr int ip_version()
      {
	return 6;
      }

      bool defined() const
      {
	return true;
      }

      static Addr from_addr(const Addr& addr)
      {
	return addr;
      }

      static Addr from_in6_addr(const struct in6_addr *in6)
      {
	Addr ret;
	network_to_host_order(&ret.u, (const union ipv6addr *)in6->s6_addr);
	ret.scope_id_ = 0;
	return ret;
      }

      struct in6_addr to_in6_addr() const
      {
	struct in6_addr ret;
	host_to_network_order((union ipv6addr *)&ret, &u);
	return ret;
      }

      static Addr from_sockaddr(const struct sockaddr_in6 *sa)
      {
	Addr ret;
	network_to_host_order(&ret.u, (const union ipv6addr *)sa->sin6_addr.s6_addr);
	ret.scope_id_ = sa->sin6_scope_id;
	return ret;
      }

      struct sockaddr_in6 to_sockaddr(const unsigned short port=0) const
      {
	struct sockaddr_in6 ret;
	std::memset(&ret, 0, sizeof(ret));
	ret.sin6_family = AF_INET6;
	ret.sin6_port = htons(port);
	host_to_network_order((union ipv6addr *)&ret.sin6_addr.s6_addr, &u);
	ret.sin6_scope_id = scope_id_;
	return ret;
      }

#ifndef OPENVPN_LEGACY_TITLE_ABSTRACTION

      template <typename TITLE>
      static Addr from_string(const std::string& ipstr, const TITLE& title)
      {
	openvpn_io::error_code ec;
	openvpn_io::ip::address_v6 a = openvpn_io::ip::make_address_v6(ipstr, ec);
	if (ec)
	  throw ipv6_exception(IP::internal::format_error(ipstr, title, "v6", ec));
	return from_asio(a);
      }

      static Addr from_string(const std::string& ipstr)
      {
	return from_string(ipstr, nullptr);
      }

#else

      static Addr from_string(const std::string& ipstr, const char *title = nullptr)
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

      static Addr from_hex(const std::string& s)
      {
	Addr ret;
	ret.scope_id_ = 0;
	ret.zero();
	size_t len = s.length();
	size_t base = 0;
	if (len > 0 && s[len-1] == 'L')
	  len -= 1;
	if (len >= 2 && s[0] == '0' && s[1] == 'x')
	  {
	    base = 2;
	    len -= 2;
	  }
	if (len < 1 || len > 32)
	  throw ipv6_exception("parse hex error");
	size_t di = (len-1)>>1;
	for (int i = (len & 1) ? -1 : 0; i < int(len); i += 2)
	  {
	    const size_t idx = base + i;
	    const int bh = (i >= 0) ? parse_hex_char(s[idx]) : 0;
	    const int bl = parse_hex_char(s[idx+1]);
	    if (bh == -1 || bl == -1)
	      throw ipv6_exception("parse hex error");
	    ret.u.bytes[Endian::e16(di--)] = (bh<<4) + bl;
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
		const char bh = b >> 4;
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
	ret.scope_id_ = 0;
	ret.u.u64[Endian::e2(0)] = std::uint64_t(ul);
	ret.u.u64[Endian::e2(1)] = 0;
	return ret;
      }

      // return *this as a unsigned long
      unsigned long to_ulong() const
      {
	const unsigned long ret = (unsigned long)u.u64[Endian::e2(0)];
	const std::uint64_t cmp = std::uint64_t(ret);
	if (u.u64[Endian::e2(1)] || cmp != u.u64[Endian::e2(0)])
	  throw ipv6_exception("overflow in conversion from IPv6.Addr to unsigned long");
	return ret;
      }

      static Addr from_long(long ul)
      {
	bool neg = false;
	Addr ret;
	ret.scope_id_ = 0;
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

      // return *this as a long
      long to_long() const
      {
	bool neg = false;
	Addr a = *this;
	if (a.u.u64[Endian::e2(1)])
	  {
	    a.negate();
	    neg = true;
	  }
	const long ret = (long)a.u.u64[Endian::e2(0)];
	const std::uint64_t cmp = std::uint64_t(ret);
	if (a.u.u64[Endian::e2(1)] || cmp != a.u.u64[Endian::e2(0)])
	  throw ipv6_exception("overflow in conversion from IPv6.Addr to long");
	return neg ? -(ret + 1) : ret;
      }

      std::string arpa() const
      {
	throw ipv6_exception("arpa() not implemented");
      }

      static Addr from_asio(const openvpn_io::ip::address_v6& asio_addr)
      {
	Addr ret;
	union ipv6addr addr;
	addr.asio_bytes = asio_addr.to_bytes();
	network_to_host_order(&ret.u, &addr);
	ret.scope_id_ = (unsigned int)asio_addr.scope_id();
	return ret;
      }

      static Addr from_byte_string(const unsigned char *bytestr)
      {
	Addr ret;
	network_to_host_order(&ret.u, (const union ipv6addr *)bytestr);
	ret.scope_id_ = 0;
	return ret;
      }

      void to_byte_string(unsigned char *bytestr) const
      {
	host_to_network_order((union ipv6addr *)bytestr, &u);
      }

      static void v4_to_byte_string(unsigned char *bytestr,
				    const std::uint32_t v4addr)
      {
	union ipv6addr *a = (union ipv6addr *)bytestr;
	a->u32[0] = a->u32[1] = a->u32[2] = 0;
	a->u32[3] = v4addr;
      }

      static bool byte_string_is_v4(const unsigned char *bytestr)
      {
	const union ipv6addr *a = (const union ipv6addr *)bytestr;
	return a->u32[0] == 0 && a->u32[1] == 0 && a->u32[2] == 0;
      }

      static std::uint32_t v4_from_byte_string(const unsigned char *bytestr)
      {
	const union ipv6addr *a = (const union ipv6addr *)bytestr;
	return a->u32[3];
      }

      openvpn_io::ip::address_v6 to_asio() const
      {
	union ipv6addr addr;
	host_to_network_order(&addr, &u);
	return openvpn_io::ip::address_v6(addr.asio_bytes, scope_id_);
      }

      static Addr from_zero()
      {
	Addr ret;
	ret.scope_id_ = 0;
	ret.zero();
	return ret;
      }

      static Addr from_one()
      {
	Addr ret;
	ret.scope_id_ = 0;
	ret.one();
	return ret;
      }

      static Addr from_zero_complement()
      {
	Addr ret;
	ret.scope_id_ = 0;
	ret.zero_complement();
	return ret;
      }

      // build a netmask using given prefix_len
      static Addr netmask_from_prefix_len(const unsigned int prefix_len)
      {
	Addr ret;
	ret.scope_id_ = 0;
	ret.prefix_len_to_netmask(prefix_len);
	return ret;
      }

      // build a netmask using given extent
      Addr netmask_from_extent() const
      {
	const Addr lb = *this - 1;
	for (size_t i = 4; i --> 0 ;)
	  {
	    const std::uint32_t v = lb.u.u32[Endian::e4(i)];
	    if (v)
	      return netmask_from_prefix_len(SIZE - (((unsigned int)i<<5) + find_last_set(v)));
	  }
	return from_zero_complement();
      }

      Addr operator&(const Addr& other) const {
	Addr ret;
	ret.scope_id_ = scope_id_;
	ret.u.u64[0] = u.u64[0] & other.u.u64[0];
	ret.u.u64[1] = u.u64[1] & other.u.u64[1];
	return ret;
      }

      Addr operator|(const Addr& other) const {
	Addr ret;
	ret.scope_id_ = scope_id_;
	ret.u.u64[0] = u.u64[0] | other.u.u64[0];
	ret.u.u64[1] = u.u64[1] | other.u.u64[1];
	return ret;
      }

      Addr operator+(const long delta) const {
	Addr ret = *this;
	ret.u.u64[Endian::e2(0)] += delta;
	ret.u.u64[Endian::e2(1)] += (delta >= 0)
	  ? (ret.u.u64[Endian::e2(0)] < u.u64[Endian::e2(0)])
	  : -(ret.u.u64[Endian::e2(0)] > u.u64[Endian::e2(0)]);
	return ret;
      }

      Addr operator+(const Addr& other) const {
	Addr ret = *this;
	add(ret.u, other.u);
	return ret;
      }

      Addr operator-(const long delta) const {
	return operator+(-delta);
      }

      Addr operator-(const Addr& other) const {
	Addr ret = *this;
	sub(ret.u, other.u);
	return ret;
      }

      Addr operator*(const Addr& d) const {
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

      Addr operator/(const Addr& d) const {
	Addr q, r;
	div(*this, d, q, r);
	return q;
      }

      Addr operator%(const Addr& d) const {
	Addr q, r;
	div(*this, d, q, r);
	return r;
      }

      Addr operator<<(const unsigned int shift) const {
	Addr ret = *this;
	shiftl128(ret.u.u64[Endian::e2(0)],
		  ret.u.u64[Endian::e2(1)],
		  shift);
	return ret;
      }

      Addr operator>>(const unsigned int shift) const {
	Addr ret = *this;
	shiftr128(ret.u.u64[Endian::e2(0)],
		  ret.u.u64[Endian::e2(1)],
		  shift);
	return ret;
      }

      Addr operator~() const {
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

      bool operator==(const Addr& other) const
      {
	return u.u64[0] == other.u.u64[0] && u.u64[1] == other.u.u64[1] && scope_id_ == other.scope_id_;
      }

      bool operator!=(const Addr& other) const
      {
	return !operator==(other);
      }

#define OPENVPN_IPV6_OPERATOR_REL(OP)					\
      bool operator OP(const Addr& other) const				\
      {									\
	if (u.u64[Endian::e2(1)] == other.u.u64[Endian::e2(1)])		\
	  {								\
	    if (u.u64[Endian::e2(0)] != other.u.u64[Endian::e2(0)])	\
	      return u.u64[Endian::e2(0)] OP other.u.u64[Endian::e2(0)]; \
	    else							\
	      return scope_id_ OP other.scope_id_;			\
	  }								\
	else								\
	  return u.u64[Endian::e2(1)] OP other.u.u64[Endian::e2(1)];	\
      }

      OPENVPN_IPV6_OPERATOR_REL(<)
      OPENVPN_IPV6_OPERATOR_REL(>)
      OPENVPN_IPV6_OPERATOR_REL(<=)
      OPENVPN_IPV6_OPERATOR_REL(>=)

#undef OPENVPN_IPV6_OPERATOR_REL

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

      bool bit(unsigned int pos) const
      {
	if (pos < 64)
	  return (u.u64[Endian::e2(0)] & (std::uint64_t(1)<<pos)) != 0;
	else
	  return (u.u64[Endian::e2(1)] & (std::uint64_t(1)<<(pos-64))) != 0;
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
	      return ret + (idx<<5);
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
	    a.scope_id_ = 0;
	    a.one();
	    return a << hl;
	  }
	else if (hl == SIZE)
	  return from_zero();
	else
	  throw ipv6_exception("extent overflow");
      }

      // address size in bits
      static unsigned int size()
      {
	return SIZE;
      }

      template <typename HASH>
      void hash(HASH& h) const
      {
	h(u.bytes, sizeof(u.bytes));
      }

#ifdef USE_OPENVPN_HASH
      std::size_t hashval() const
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

      Addr& operator++()
      {
	if (++u.u64[Endian::e2(0)] == 0)
	  ++u.u64[Endian::e2(1)];
	return *this;
      }

      Addr& operator+=(const long delta)
      {
	*this = *this + delta;
	return *this;
      }

      Addr& operator-=(const long delta)
      {
	return operator+=(-delta);
      }

      Addr& operator+=(const Addr& other) {
	add(u, other.u);
	return *this;
      }

      Addr& operator-=(const Addr& other) {
	sub(u, other.u);
	return *this;
      }

      Addr& operator<<=(const unsigned int shift) {
	shiftl128(u.u64[Endian::e2(0)],
		  u.u64[Endian::e2(1)],
		  shift);
	return *this;
      }

      Addr& operator>>=(const unsigned int shift) {
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
	      u.u64[Endian::e2(0)] |= (std::uint64_t(1)<<pos);
	    else
	      u.u64[Endian::e2(0)] &= ~(std::uint64_t(1)<<pos);
	  }
	else
	  {
	    if (value)
	      u.u64[Endian::e2(1)] |= (std::uint64_t(1)<<(pos-64));
	    else
	      u.u64[Endian::e2(1)] &= ~(std::uint64_t(1)<<(pos-64));
	  }
      }

      void set_bit(unsigned int pos, bool value)
      {
	if (value)
	  {
	    if (pos < 64)
	      u.u64[Endian::e2(0)] |= (std::uint64_t(1)<<pos);
	    else
	      u.u64[Endian::e2(1)] |= (std::uint64_t(1)<<(pos-64));
	  }
      }

      static void div(const Addr& n, const Addr& d, Addr& q, Addr& r)
      {
	if (d.all_zeros())
	  throw ipv6_exception("division by 0");
	q = from_zero();        // quotient
	r = n;                  // remainder (init to numerator)
	Addr ml = from_zero();  // mask low
	Addr mh = d;            // mask high (init to denominator)
	for (unsigned int i = 0; i < SIZE; ++i)
	  {
	    ml >>= 1;
	    ml.set_bit(SIZE-1, mh.bit(0));
	    mh >>= 1;
	    if (mh.all_zeros() && r >= ml)
	      {
		r -= ml;
		q.set_bit((SIZE-1)-i, true);
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
	    const std::uint32_t mask = ~((1 << (31 - (pl & 31))) - 1);
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
	else
	  throw ipv6_exception("bad prefix len");
      }

      static void host_to_network_order(union ipv6addr *dest, const union ipv6addr *src)
      {
	dest->u32[0] = htonl(src->u32[Endian::e4rev(0)]);
	dest->u32[1] = htonl(src->u32[Endian::e4rev(1)]);
	dest->u32[2] = htonl(src->u32[Endian::e4rev(2)]);
	dest->u32[3] = htonl(src->u32[Endian::e4rev(3)]);
      }

      static void network_to_host_order(union ipv6addr *dest, const union ipv6addr *src)
      {
	dest->u32[0] = ntohl(src->u32[Endian::e4rev(0)]);
	dest->u32[1] = ntohl(src->u32[Endian::e4rev(1)]);
	dest->u32[2] = ntohl(src->u32[Endian::e4rev(2)]);
	dest->u32[3] = ntohl(src->u32[Endian::e4rev(3)]);
      }

      static void shiftl128(std::uint64_t& low,
			    std::uint64_t& high,
			    unsigned int shift)
      {
	if (shift == 1)
	  {
	    high <<= 1;
	    if (low & (std::uint64_t(1) << 63))
	      high |= 1;
	    low <<= 1;
	  }
	else if (shift == 0)
	  ;
	else if (shift <= 128)
	  {
	    if (shift >= 64)
	      {
		high = low;
		low = 0;
		shift -= 64;
	      }
	    if (shift < 64)
	      {
		high = (high << shift) | (low >> (64-shift));
		low <<= shift;
	      }
	    else // shift == 64
	      high = 0;
	  }
	else
	  throw ipv6_exception("l-shift too large");
      }

      static void shiftr128(std::uint64_t& low,
			    std::uint64_t& high,
			    unsigned int shift)
      {
	if (shift == 1)
	  {
	    low >>= 1;
	    if (high & 1)
	      low |= (std::uint64_t(1) << 63);
	    high >>= 1;
	  }
	else if (shift == 0)
	  ;
	else if (shift <= 128)
	  {
	    if (shift >= 64)
	      {
		low = high;
		high = 0;
		shift -= 64;
	      }
	    if (shift < 64)
	      {
		low = (low >> shift) | (high << (64-shift));
		high >>= shift;
	      }
	    else // shift == 64
	      low = 0;
	  }
	else
	  throw ipv6_exception("r-shift too large");
      }

      static void add(ipv6addr& dest, const ipv6addr& src) {
	const std::uint64_t dorigl = dest.u64[Endian::e2(0)];
        dest.u64[Endian::e2(0)] += src.u64[Endian::e2(0)];
        dest.u64[Endian::e2(1)] += src.u64[Endian::e2(1)];
        // check for overflow of low 64 bits, add carry to high
        if (dest.u64[Endian::e2(0)] < dorigl)
            ++dest.u64[Endian::e2(1)];
      }

      static void sub(ipv6addr& dest, const ipv6addr& src) {
	const std::uint64_t dorigl = dest.u64[Endian::e2(0)];
        dest.u64[Endian::e2(0)] -= src.u64[Endian::e2(0)];
        dest.u64[Endian::e2(1)] -= src.u64[Endian::e2(1)]
	  + (dorigl < dest.u64[Endian::e2(0)]);
      }

      union ipv6addr u;
      unsigned int scope_id_;
    };

    OPENVPN_OSTREAM(Addr, to_string)
  }
}

#ifdef USE_OPENVPN_HASH
OPENVPN_HASH_METHOD(openvpn::IPv6::Addr, hashval);
#endif

#endif // OPENVPN_ADDR_IPV6_H
