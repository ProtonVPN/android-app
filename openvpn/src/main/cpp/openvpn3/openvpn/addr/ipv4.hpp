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

#ifndef OPENVPN_ADDR_IPV4_H
#define OPENVPN_ADDR_IPV4_H

#include <cstring>           // for std::memcpy, std::memset
#include <sstream>
#include <cstdint>           // for std::uint32_t

#include <openvpn/io/io.hpp>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/endian.hpp>
#include <openvpn/common/ostream.hpp>
#include <openvpn/common/socktypes.hpp>
#include <openvpn/common/ffs.hpp>
#include <openvpn/common/hexstr.hpp>
#include <openvpn/common/hash.hpp>
#include <openvpn/addr/iperr.hpp>

namespace openvpn {
  namespace IP {
    class Addr;
  }

  // Fundamental classes for representing an IPv4 IP address.

  namespace IPv4 {

    OPENVPN_EXCEPTION(ipv4_exception);

    class Addr // NOTE: must be union-legal, so default constructor does not initialize
    {
      friend class IP::Addr;

    public:
      enum { SIZE=32 };

      typedef std::uint32_t base_type;
      typedef std::int32_t signed_base_type;

      static constexpr int ip_version()
      {
	return 4;
      }

      bool defined() const
      {
	return true;
      }

      static Addr from_addr(const Addr& addr)
      {
	return addr;
      }

      static Addr from_in_addr(const struct in_addr *in4)
      {
	Addr ret;
	ret.u.addr = ntohl(in4->s_addr);
	return ret;
      }

      struct in_addr to_in_addr() const
      {
	struct in_addr ret;
	ret.s_addr = htonl(u.addr);
	return ret;
      }

      static Addr from_sockaddr(const struct sockaddr_in *sa)
      {
	Addr ret;
	ret.u.addr = ntohl(sa->sin_addr.s_addr);
	return ret;
      }

      struct sockaddr_in to_sockaddr(const unsigned short port=0) const
      {
	struct sockaddr_in ret;
	std::memset(&ret, 0, sizeof(ret));
	ret.sin_family = AF_INET;
	ret.sin_port = htons(port);
	ret.sin_addr.s_addr = htonl(u.addr);
	return ret;
      }

      static Addr from_uint32(const base_type addr) // host byte order
      {
	Addr ret;
	ret.u.addr = addr;
	return ret;
      }

      std::uint32_t to_uint32() const // host byte order
      {
	return u.addr;
      }

      static Addr from_uint32_net(const base_type addr) // addr in net byte order
      {
	Addr ret;
	ret.u.addr = ntohl(addr);
	return ret;
      }

      void to_byte_string(unsigned char *bytestr) const
      {
	*(base_type*)bytestr = ntohl(u.addr);
      }

      std::uint32_t to_uint32_net() const // return value in net byte order
      {
	return htonl(u.addr);
      }

      static Addr from_ulong(unsigned long ul)
      {
	Addr ret;
	ret.u.addr = (base_type)ul;
	return ret;
      }

      // return *this as a unsigned long
      unsigned long to_ulong() const
      {
	return (unsigned long)u.addr;
      }

      static Addr from_long(long ul)
      {
	Addr ret;
	ret.u.addr = (base_type)(signed_base_type)ul;
	return ret;
      }

      // return *this as a long
      long to_long() const
      {
	return (long)(signed_base_type)u.addr;
      }

      static Addr from_bytes(const unsigned char *bytes) // host byte order
      {
	Addr ret;
	std::memcpy(ret.u.bytes, bytes, 4);
	return ret;
      }

      static Addr from_bytes_net(const unsigned char *bytes) // network byte order
      {
	Addr ret;
	std::memcpy(ret.u.bytes, bytes, 4);
	ret.u.addr = ntohl(ret.u.addr);
	return ret;
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

      // build a netmask using given prefix_len
      static Addr netmask_from_prefix_len(const unsigned int prefix_len)
      {
	Addr ret;
	ret.u.addr = prefix_len_to_netmask(prefix_len);
	return ret;
      }

      // build a netmask using given extent
      Addr netmask_from_extent() const
      {
	const int lb = find_last_set(u.addr - 1);
	return netmask_from_prefix_len(SIZE - lb);
      }

#ifndef OPENVPN_LEGACY_TITLE_ABSTRACTION

      template <typename TITLE>
      static Addr from_string(const std::string& ipstr, const TITLE& title)
      {
	openvpn_io::error_code ec;
	openvpn_io::ip::address_v4 a = openvpn_io::ip::make_address_v4(ipstr, ec);
	if (ec)
	  throw ipv4_exception(IP::internal::format_error(ipstr, title, "v4", ec));
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
	openvpn_io::ip::address_v4 a = openvpn_io::ip::make_address_v4(ipstr, ec);
	if (ec)
	  throw ipv4_exception(IP::internal::format_error(ipstr, title, "v4", ec));
	return from_asio(a);
      }

#endif

      std::string to_string() const
      {
	const openvpn_io::ip::address_v4 a = to_asio();
	std::string ret = a.to_string();
	return ret;
      }

      static Addr from_hex(const std::string& s)
      {
	Addr ret;
	ret.u.addr = 0;
	size_t len = s.length();
	size_t base = 0;
	if (len > 0 && s[len-1] == 'L')
	  len -= 1;
	if (len >= 2 && s[0] == '0' && s[1] == 'x')
	  {
	    base = 2;
	    len -= 2;
	  }
	if (len < 1 || len > 8)
	  throw ipv4_exception("parse hex error");
	size_t di = (len-1)>>1;
	for (int i = (len & 1) ? -1 : 0; i < int(len); i += 2)
	  {
	    const size_t idx = base + i;
	    const int bh = (i >= 0) ? parse_hex_char(s[idx]) : 0;
	    const int bl = parse_hex_char(s[idx+1]);
	    if (bh == -1 || bl == -1)
	      throw ipv4_exception("parse hex error");
	    ret.u.bytes[Endian::e4(di--)] = (bh<<4) + bl;
	  }
	return ret;
      }

      std::string to_hex() const
      {
	std::string ret;
	ret.reserve(8);
	bool firstnonzero = false;
	for (size_t i = 0; i < 4; ++i)
	  {
	    const unsigned char b = u.bytes[Endian::e4rev(i)];
	    if (b || firstnonzero || i == 3)
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

      std::string arpa() const
      {
	std::ostringstream os;
	os << int(u.bytes[Endian::e4(0)]) << '.'
	   << int(u.bytes[Endian::e4(1)]) << '.'
	   << int(u.bytes[Endian::e4(2)]) << '.'
	   << int(u.bytes[Endian::e4(3)]) << ".in-addr.arpa";
	return os.str();
      }

      static Addr from_asio(const openvpn_io::ip::address_v4& asio_addr)
      {
	Addr ret;
	ret.u.addr = (std::uint32_t)asio_addr.to_uint();
	return ret;
      }

      openvpn_io::ip::address_v4 to_asio() const
      {
	return openvpn_io::ip::address_v4(u.addr);
      }

      Addr operator&(const Addr& other) const {
	Addr ret;
	ret.u.addr = u.addr & other.u.addr;
	return ret;
      }

      Addr operator|(const Addr& other) const {
	Addr ret;
	ret.u.addr = u.addr | other.u.addr;
	return ret;
      }

      Addr operator+(const long delta) const {
	Addr ret;
	ret.u.addr = u.addr + (std::uint32_t)delta;
	return ret;
      }

      Addr operator+(const Addr& other) const {
	Addr ret;
	ret.u.addr = u.addr + other.u.addr;
	return ret;
      }

      Addr operator-(const long delta) const {
	return operator+(-delta);
      }

      Addr operator-(const Addr& other) const {
	Addr ret;
	ret.u.addr = u.addr - other.u.addr;
	return ret;
      }

      Addr operator*(const Addr& other) const {
	Addr ret;
	ret.u.addr = u.addr * other.u.addr;
	return ret;
      }

      Addr operator/(const Addr& other) const {
	Addr ret;
	ret.u.addr = u.addr / other.u.addr;
	return ret;
      }

      Addr operator%(const Addr& other) const {
	Addr ret;
	ret.u.addr = u.addr % other.u.addr;
	return ret;
      }

      Addr operator<<(const unsigned int shift) const {
	Addr ret;
	ret.u.addr = u.addr << shift;
	return ret;
      }

      Addr operator>>(const unsigned int shift) const {
	Addr ret;
	ret.u.addr = u.addr >> shift;
	return ret;
      }

      Addr operator~() const {
	Addr ret;
	ret.u.addr = ~u.addr;
	return ret;
      }

      // return the network that contains the current address
      Addr network_addr(const unsigned int prefix_len) const
      {
	Addr ret;
	ret.u.addr = u.addr & prefix_len_to_netmask(prefix_len);
	return ret;
      }

      bool operator==(const Addr& other) const
      {
	return u.addr == other.u.addr;
      }

      bool operator!=(const Addr& other) const
      {
	return u.addr != other.u.addr;
      }

      bool operator<(const Addr& other) const
      {
	return u.addr < other.u.addr;
      }

      bool operator>(const Addr& other) const
      {
	return u.addr > other.u.addr;
      }

      bool operator<=(const Addr& other) const
      {
	return u.addr <= other.u.addr;
      }

      bool operator>=(const Addr& other) const
      {
	return u.addr >= other.u.addr;
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
	return u.addr == 0;
      }

      bool all_ones() const
      {
	return ~u.addr == 0;
      }

      bool is_loopback() const
      {
	return (u.addr & 0x7F000000) == 0x7F000000;
      }

      // number of network bits in netmask,
      // throws exception if addr is not a netmask
      unsigned int prefix_len() const
      {
	const int ret = prefix_len_32(u.addr);
	if (ret >= 0)
	  return ret;
	else
	  throw ipv4_exception("malformed netmask");
      }

      int prefix_len_nothrow() const
      {
	return prefix_len_32(u.addr);
      }

      // number of host bits in netmask
      unsigned int host_len() const
      {
	return SIZE - prefix_len();
      }

      // return the number of host addresses contained within netmask
      Addr extent_from_netmask() const
      {
	Addr ret;
	ret.u.addr = extent_from_netmask_uint32();
	return ret;
      }

      std::uint32_t extent_from_netmask_uint32() const
      {
	const unsigned int hl = host_len();
	if (hl < SIZE)
	  return 1 << hl;
	else if (hl == SIZE)
	  return 0;
	else
	  throw ipv4_exception("extent overflow");
      }

      // convert netmask in addr to prefix_len, will return -1 on error
      static int prefix_len_32(const std::uint32_t addr)
      {
	if (addr == ~std::uint32_t(0))
	  return 32;
	else if (addr == 0)
	  return 0;
	else
	  {
	    unsigned int high = 32;
	    unsigned int low = 1;
	    for (unsigned int i = 0; i < 5; ++i)
	      {
		const unsigned int mid = (high + low) / 2;
		const IPv4::Addr::base_type test = prefix_len_to_netmask_unchecked(mid);
		if (addr == test)
		  return mid;
		else if (addr > test)
		  low = mid;
		else
		  high = mid;
	      }
	    return -1;
	  }
      }

      // address size in bits
      static unsigned int size()
      {
	return SIZE;
      }

      template <typename HASH>
      void hash(HASH& h) const
      {
	h(u.addr);
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
	u.addr = ~u.addr;
      }

      void zero()
      {
	u.addr = 0;
      }

      void zero_complement()
      {
	u.addr = ~0;
      }

      void one()
      {
	u.addr = 1;
      }

      Addr& operator++()
      {
	++u.addr;
	return *this;
      }

      Addr& operator+=(const long delta)
      {
	u.addr += (std::uint32_t)delta;
	return *this;
      }

      Addr& operator-=(const long delta)
      {
	return operator+=(-delta);
      }

    private:
      static base_type prefix_len_to_netmask_unchecked(const unsigned int prefix_len)
      {
	if (prefix_len)
	  return ~((1 << (SIZE - prefix_len)) - 1);
	else
	  return 0;
      }

      static base_type prefix_len_to_netmask(const unsigned int prefix_len)
      {
	if (prefix_len <= SIZE)
	  return prefix_len_to_netmask_unchecked(prefix_len);
	else
	  throw ipv4_exception("bad prefix len");
      }

      union {
	base_type addr; // host byte order
	unsigned char bytes[4];
      } u;
    };

    OPENVPN_OSTREAM(Addr, to_string)
  }
}

#ifdef USE_OPENVPN_HASH
OPENVPN_HASH_METHOD(openvpn::IPv4::Addr, hashval);
#endif

#endif // OPENVPN_ADDR_IPV4_H
