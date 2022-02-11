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

#ifndef OPENVPN_ADDR_IP_H
#define OPENVPN_ADDR_IP_H

#include <string>
#include <cstring> // for std::memset

#include <openvpn/io/io.hpp>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/ostream.hpp>
#include <openvpn/common/hash.hpp>
#include <openvpn/addr/ipv4.hpp>
#include <openvpn/addr/ipv6.hpp>
#include <openvpn/addr/iperr.hpp>

namespace openvpn {
  // This is our fundamental IP address class that handles IPv4 or IPv6
  // IP addresses.  It is implemented as a discriminated union of IPv4::Addr
  // and IPv6::Addr.
  namespace IP {

    OPENVPN_EXCEPTION(ip_exception);

    class Addr
    {
    public:
      enum Version { UNSPEC, V4, V6 };

      enum { V4_MASK=(1<<0), V6_MASK=(1<<1) };
      typedef unsigned int VersionMask;

      enum VersionSize {
	V4_SIZE = IPv4::Addr::SIZE,
	V6_SIZE = IPv6::Addr::SIZE,
      };

#ifndef OPENVPN_LEGACY_TITLE_ABSTRACTION

      template <typename TITLE>
      Addr(const Addr& other, const TITLE& title, const Version required_version)
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
      Addr(const Addr& other, const TITLE& title)
	: Addr(other, title, UNSPEC)
      {
      }

      template <typename TITLE>
      Addr(const std::string& ipstr, const TITLE& title, const Version required_version)
	: Addr(from_string(ipstr, title, required_version))
      {
      }

      template <typename TITLE>
      Addr(const std::string& ipstr, const TITLE& title)
	: Addr(from_string(ipstr, title, UNSPEC))
      {
      }

      Addr(const std::string& ipstr)
	: Addr(from_string(ipstr, nullptr, UNSPEC))
      {
      }

      template <typename TITLE>
      static Addr from_string(const std::string& ipstr,
			      const TITLE& title,
			      const Version required_version)
      {
	openvpn_io::error_code ec;
	openvpn_io::ip::address a = openvpn_io::ip::make_address(ipstr, ec);
	if (ec)
	  throw ip_exception(internal::format_error(ipstr, title, "", ec));
	const Addr ret = from_asio(a);
	if (required_version != UNSPEC && required_version != ret.ver)
	  throw ip_exception(internal::format_error(ipstr, title, version_string_static(required_version), "wrong IP version"));
	return ret;
      }

      template <typename TITLE>
      static Addr from_string(const std::string& ipstr, const TITLE& title)
      {
	return from_string(ipstr, title, UNSPEC);
      }

      static Addr from_string(const std::string& ipstr)
      {
	return from_string(ipstr, nullptr, UNSPEC);
      }

      template <typename TITLE>
      static std::string validate(const std::string& ipstr,
				  const TITLE& title,
				  const Version required_version)
      {
	Addr a = from_string(ipstr, title, required_version);
	return a.to_string();
      }

      template <typename TITLE>
      static std::string validate(const std::string& ipstr, const TITLE& title)
      {
	return validate(ipstr, title, UNSPEC);
      }

      static std::string validate(const std::string& ipstr)
      {
	return validate(ipstr, nullptr, UNSPEC);
      }

      template <typename TITLE>
      void validate_version(const TITLE& title, const Version required_version) const
      {
	if (required_version != UNSPEC && required_version != ver)
	  throw ip_exception(internal::format_error(to_string(), title, version_string_static(required_version), "wrong IP version"));
      }

#else

      Addr(const Addr& other, const char *title = nullptr, Version required_version = UNSPEC)
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

      Addr(const std::string& ipstr, const char *title = nullptr, Version required_version = UNSPEC)
	: Addr(from_string(ipstr, title, required_version))
      {
      }

#ifndef SWIGPYTHON
      // When calling IP:Addr with None as the second parameter, Swig will
      // always pick this function and complain about not being able to convert
      // a null pointer to a const std::string reference. Hide this function, so
      // swig is forced to take the const char* variant of this function instead
      Addr(const std::string& ipstr, const std::string& title, Version required_version = UNSPEC)
	: Addr(from_string(ipstr, title.c_str(), required_version))
      {
      }
#endif

      void validate_version(const char *title, Version required_version) const
      {
	if (required_version != UNSPEC && required_version != ver)
	  throw ip_exception(internal::format_error(to_string(), title, version_string_static(required_version), "wrong IP version"));
      }

#ifndef SWIGPYTHON
      void validate_version(const std::string& title, Version required_version) const
      {
	validate_version(title.c_str(), required_version);
      }
#endif

      static std::string validate(const std::string& ipstr, const char *title = nullptr, Version required_version = UNSPEC)
      {
	Addr a = from_string(ipstr, title, required_version);
	return a.to_string();
      }

#ifndef SWIGPYTHON
      static std::string validate(const std::string& ipstr, const std::string& title, Version required_version = UNSPEC)
      {
	return validate(ipstr, title.c_str(), required_version);
      }
#endif

      static Addr from_string(const std::string& ipstr, const char *title = nullptr, Version required_version = UNSPEC)
      {
	openvpn_io::error_code ec;
	openvpn_io::ip::address a = openvpn_io::ip::make_address(ipstr, ec);
	if (ec)
	  throw ip_exception(internal::format_error(ipstr, title, "", ec));
	const Addr ret = from_asio(a);
	if (required_version != UNSPEC && required_version != ret.ver)
	  throw ip_exception(internal::format_error(ipstr, title, version_string_static(required_version), "wrong IP version"));
	return ret;
      }

#endif

      static bool is_valid(const std::string& ipstr)
      {
	// fast path -- rule out validity if invalid chars
	for (size_t i = 0; i < ipstr.length(); ++i)
	  {
	    const char c = ipstr[i];
	    if (!((c >= '0' && c <= '9')
		  || (c >= 'a' && c <= 'f')
		  || (c >= 'A' && c <= 'F')
		  || (c == '.' || c == ':' || c == '%')))
	      return false;
	  }

	// slow path
	{
	  openvpn_io::error_code ec;
	  openvpn_io::ip::make_address(ipstr, ec);
	  return !ec;
	}
      }

      static Addr from_hex(Version v, const std::string& s)
      {
	if (v == V4)
	  return from_ipv4(IPv4::Addr::from_hex(s));
	else if (v == V6)
	  return from_ipv6(IPv6::Addr::from_hex(s));
	else
	  throw ip_exception("address unspecified");
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

      const IPv4::Addr& to_ipv4() const
      {
	if (ver == V4)
	  return u.v4;
	else
	  throw ip_exception("address is not IPv4");
      }

      IPv4::Addr to_ipv4_zero() const
      {
	if (ver == V4)
	  return u.v4;
	else if (ver == UNSPEC)
	  return IPv4::Addr::from_zero();
	else
	  throw ip_exception("address is not IPv4 (zero)");
      }

      const IPv6::Addr& to_ipv6() const
      {
	if (ver == V6)
	  return u.v6;
	else
	  throw ip_exception("address is not IPv6");
      }

      IPv6::Addr to_ipv6_zero() const
      {
	if (ver == V6)
	  return u.v6;
	else if (ver == UNSPEC)
	  return IPv6::Addr::from_zero();
	else
	  throw ip_exception("address is not IPv6 (zero)");
      }

      const IPv4::Addr& to_ipv4_nocheck() const
      {
	return u.v4;
      }

      const IPv6::Addr& to_ipv6_nocheck() const
      {
	return u.v6;
      }

      static Addr from_sockaddr(const struct sockaddr *sa)
      {
	if (sa->sa_family == AF_INET)
	  return from_ipv4(IPv4::Addr::from_sockaddr((struct sockaddr_in *)sa));
	else if (sa->sa_family == AF_INET6)
	  return from_ipv6(IPv6::Addr::from_sockaddr((struct sockaddr_in6 *)sa));
	else
	  return Addr();
      }

      static bool sockaddr_defined(const struct sockaddr *sa)
      {
	return sa && (sa->sa_family == AF_INET || sa->sa_family == AF_INET6);
      }

      static Addr from_ulong(Version v, unsigned long ul)
      {
	if (v == V4)
	  return from_ipv4(IPv4::Addr::from_ulong(ul));
	else if (v == V6)
	  return from_ipv6(IPv6::Addr::from_ulong(ul));
	else
	  throw ip_exception("address unspecified");
      }

      // return *this as a ulong, will raise exception on overflow
      unsigned long to_ulong() const
      {
	if (ver == V4)
	  return u.v4.to_ulong();
	else if (ver == V6)
	  return u.v6.to_ulong();
	else
	  throw ip_exception("address unspecified");
      }

      static Addr from_long(Version v, long ul)
      {
	if (v == V4)
	  return from_ipv4(IPv4::Addr::from_long(ul));
	else if (v == V6)
	  return from_ipv6(IPv6::Addr::from_long(ul));
	else
	  throw ip_exception("address unspecified");
      }

      // return *this as a long, will raise exception on overflow
      long to_long() const
      {
	if (ver == V4)
	  return u.v4.to_long();
	else if (ver == V6)
	  return u.v6.to_long();
	else
	  throw ip_exception("address unspecified");
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
	if (ver == V4)
	  IPv6::Addr::v4_to_byte_string(bytestr, u.v4.to_uint32_net());
	else if (ver == V6)
	  u.v6.to_byte_string(bytestr);
	else
	  std::memset(bytestr, 0, 16);
      }

      // convert Addr to variable length byte string
      void to_byte_string_variable(unsigned char *bytestr) const
      {
	if (ver == V4)
	  u.v4.to_byte_string(bytestr);
	else if (ver == V6)
	  u.v6.to_byte_string(bytestr);
	else
	  throw ip_exception("address unspecified");
      }

      std::uint32_t to_uint32_net() const // return value in net byte order
      {
	if (ver == V4)
	  return u.v4.to_uint32_net();
	else
	  return 0;
      }

      // construct an address where all bits are zero
      static Addr from_zero(Version v)
      {
	if (v == V4)
	  return from_ipv4(IPv4::Addr::from_zero());
	else if (v == V6)
	  return from_ipv6(IPv6::Addr::from_zero());
	else
	  throw ip_exception("address unspecified");
      }

      // construct an address where all bits are zero
      static Addr from_one(Version v)
      {
	if (v == V4)
	  return from_ipv4(IPv4::Addr::from_one());
	else if (v == V6)
	  return from_ipv6(IPv6::Addr::from_one());
	else
	  throw ip_exception("address unspecified");
      }

      // construct an address where all bits are one
      static Addr from_zero_complement(Version v)
      {
	if (v == V4)
	  return from_ipv4(IPv4::Addr::from_zero_complement());
	else if (v == V6)
	  return from_ipv6(IPv6::Addr::from_zero_complement());
	else
	  throw ip_exception("address unspecified");
      }

      // validate the prefix length for the IP version
      static bool validate_prefix_len(Version v, const unsigned int prefix_len)
      {
	if (v == V4)
	  {
	    if (prefix_len <= V4_SIZE)
	      return true;
	  }
	else if (v == V6)
	  {
	    if (prefix_len <= V6_SIZE)
	      return true;
	  }
	return false;
      }

      // build a netmask using given prefix_len
      static Addr netmask_from_prefix_len(Version v, const unsigned int prefix_len)
      {
	if (v == V4)
	  return from_ipv4(IPv4::Addr::netmask_from_prefix_len(prefix_len));
	else if (v == V6)
	  return from_ipv6(IPv6::Addr::netmask_from_prefix_len(prefix_len));
	else
	  throw ip_exception("address unspecified");
      }

      // build a netmask using *this as extent
      Addr netmask_from_extent() const
      {
	if (ver == V4)
	  return from_ipv4(u.v4.netmask_from_extent());
	else if (ver == V6)
	  return from_ipv6(u.v6.netmask_from_extent());
	else
	  throw ip_exception("address unspecified");
      }

      std::string to_string() const
      {
	if (ver != UNSPEC)
	  {
	    const openvpn_io::ip::address a = to_asio();
	    std::string ret = a.to_string();
	    return ret;
	  }
	else
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
	if (ver == V4)
	  return u.v4.to_hex();
	else if (ver == V6)
	  return u.v6.to_hex();
	else
	  throw ip_exception("address unspecified");
      }

      std::string arpa() const
      {
	if (ver == V4)
	  return u.v4.arpa();
	else if (ver == V6)
	  return u.v6.arpa();
	else
	  throw ip_exception("address unspecified");
      }

      static Addr from_asio(const openvpn_io::ip::address& addr)
      {
	if (addr.is_v4())
	  {
	    Addr a;
	    a.ver = V4;
	    a.u.v4 = IPv4::Addr::from_asio(addr.to_v4());
	    return a;
	  }
	else if (addr.is_v6())
	  {
	    Addr a;
	    a.ver = V6;
	    a.u.v6 = IPv6::Addr::from_asio(addr.to_v6());
	    return a;
	  }
	else
	  throw ip_exception("address unspecified");
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
	    throw ip_exception("address unspecified");
	  }
      }

      Addr operator+(const long delta) const {
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
	    throw ip_exception("address unspecified");
	  }
      }

      Addr operator-(const long delta) const {
	return operator+(-delta);
      }

#define OPENVPN_IP_OPERATOR_BINOP(OP)		       \
      Addr operator OP (const Addr& other) const {     \
	if (ver != other.ver)                          \
	  throw ip_exception("version inconsistency"); \
	switch (ver)                                   \
	  {                                            \
	  case V4:                                     \
	    {                                          \
	      Addr ret;                                \
	      ret.ver = V4;                            \
	      ret.u.v4 = u.v4 OP other.u.v4;           \
	      return ret;                              \
	    }                                          \
	  case V6:                                     \
	    {                                          \
	      Addr ret;                                \
	      ret.ver = V6;                            \
	      ret.u.v6 = u.v6 OP other.u.v6;           \
	      return ret;                              \
	    }                                          \
	  default:                                     \
	    throw ip_exception("address unspecified"); \
	  }                                            \
      }

      OPENVPN_IP_OPERATOR_BINOP(+)
      OPENVPN_IP_OPERATOR_BINOP(-)
      OPENVPN_IP_OPERATOR_BINOP(*)
      OPENVPN_IP_OPERATOR_BINOP(/)
      OPENVPN_IP_OPERATOR_BINOP(%)
      OPENVPN_IP_OPERATOR_BINOP(&)
      OPENVPN_IP_OPERATOR_BINOP(|)

#undef OPENVPN_IP_OPERATOR_BINOP

      Addr operator<<(const unsigned int shift) const {
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
	    throw ip_exception("address unspecified");
	  }
      }

      Addr operator>>(const unsigned int shift) const {
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
	    throw ip_exception("address unspecified");
	  }
      }

      Addr operator~() const {
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
	    throw ip_exception("address unspecified");
	  }
      }

      Addr network_addr(const unsigned int prefix_len) const {
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
	    throw ip_exception("address unspecified");
	  }
      }

      bool operator==(const Addr& other) const
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

      bool operator!=(const Addr& other) const
      {
	return !operator==(other);
      }

#define OPENVPN_IP_OPERATOR_REL(OP)		\
      bool operator OP(const Addr& other) const \
      {						\
	if (ver == other.ver)			\
	  {					\
	    switch (ver)			\
	      {					\
	      case V4:				\
		return u.v4 OP other.u.v4;	\
	      case V6:				\
		return u.v6 OP other.u.v6;	\
	      default:				\
		return false;			\
	      }					\
	  }					\
	else if (ver OP other.ver)		\
	  return true;				\
	else					\
	  return false;				\
      }

      OPENVPN_IP_OPERATOR_REL(<)
      OPENVPN_IP_OPERATOR_REL(>)
      OPENVPN_IP_OPERATOR_REL(<=)
      OPENVPN_IP_OPERATOR_REL(>=)

#undef OPENVPN_IP_OPERATOR_REL

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
	    return "v?";
	  }
      }

      Version version() const { return ver; }

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
	    throw ip_exception("version index undefined");
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

      bool is_compatible(const Addr& other) const
      {
	return ver == other.ver;
      }

      bool is_ipv6() const
      {
	return ver == V6;
      }

      void verify_version_consistency(const Addr& other) const
      {
	if (!is_compatible(other))
	  throw ip_exception("version inconsistency");
      }

      // throw exception if address is not a valid netmask
      void validate_netmask()
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
	    throw ip_exception("address unspecified");
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
	    throw ip_exception("address unspecified");
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
	    throw ip_exception("address unspecified");
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
	if (v == V4)
	  return IPv4::Addr::SIZE;
	else if (v == V6)
	  return IPv6::Addr::SIZE;
	else
	  return 0;
      }

      template <typename HASH>
      void hash(HASH& h) const
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

      Addr()
	: ver(UNSPEC)
      {
      }

      void reset()
      {
	ver = UNSPEC;
      }

      Addr& operator=(const Addr& other)
      {
	ver = other.ver;
	u = other.u;
	return *this;
      }

      Addr& operator++()
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

      Addr& operator+=(const long delta)
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

      Addr& operator-=(const long delta)
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
      } u {};

      Version ver;
    };

    OPENVPN_OSTREAM(Addr, to_string)
  }
}

#ifdef USE_OPENVPN_HASH
OPENVPN_HASH_METHOD(openvpn::IP::Addr, hashval);
#endif

#endif
