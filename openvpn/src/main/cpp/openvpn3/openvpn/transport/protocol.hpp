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

#ifndef OPENVPN_TRANSPORT_PROTOCOL_H
#define OPENVPN_TRANSPORT_PROTOCOL_H

#include <string>
#include <cstdint> // for std::uint32_t, etc.

#include <openvpn/common/exception.hpp>
#include <openvpn/common/option_error.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/addr/ip.hpp>

namespace openvpn {
// A class that encapsulates a transport protocol.
  class Protocol
  {
  public:
    enum Type {
      NONE,
      UDPv4,
      TCPv4,
      UDPv6,
      TCPv6,
      TLSv4,        // TLS over IPv4
      TLSv6,        // TLS over IPv6
      UnixStream,   // unix domain socket (stream)
      UnixDGram,    // unix domain socket (datagram)
      NamedPipe,    // named pipe (Windows only)
      UDP,
      TCP,
      TLS,
    };

    enum AllowSuffix {
      NO_SUFFIX,
      CLIENT_SUFFIX,
      SERVER_SUFFIX,
    };

    Protocol() : type_(NONE) {}
    explicit Protocol(const Type t) : type_(t) {}
    Type operator()() const { return type_; }

    bool defined() const { return type_ != NONE; }

    void reset() { type_ = NONE; }

    bool is_udp() const { return type_ == UDP || type_ == UDPv4 || type_ == UDPv6; }
    bool is_tcp() const { return type_ == TCP || type_ == TCPv4 || type_ == TCPv6; }
    bool is_tls() const { return type_ == TLS || type_ == TLSv4 || type_ == TLSv6; }
    bool is_reliable() const { return is_tcp() || is_tls(); }
    bool is_ipv4() const { return type_ == UDPv4 || type_ == TCPv4 || type_ == TLSv4; }
    bool is_ipv6() const { return type_ == UDPv6 || type_ == TCPv6 || type_ == TLSv6; }
    bool is_unix() const { return type_ == UnixStream || type_ == UnixDGram; }
    bool is_named_pipe() const { return type_ == NamedPipe; }
    bool is_local() const { return is_unix() || is_named_pipe(); }

    bool operator==(const Protocol& other) const
    {
      return type_ == other.type_;
    }

    bool operator!=(const Protocol& other) const
    {
      return type_ != other.type_;
    }

    bool transport_match(const Protocol& other) const
    {
      return transport_proto() == other.transport_proto();
    }

    unsigned int extra_transport_bytes() const
    {
      return (is_tcp() || is_tls()) ? sizeof(std::uint16_t) : 0;
    }

    void mod_addr_version(const IP::Addr::Version ip_version)
    {
      switch (ip_version)
	{
	case IP::Addr::UNSPEC:
	  break;
	case IP::Addr::V4:
	  if (is_udp())
	    type_ = UDPv4;
	  else if (is_tcp())
	    type_ = TCPv4;
	  else if (is_tls())
	    type_ = TLSv4;
	  break;
	case IP::Addr::V6:
	  if (is_udp())
	    type_ = UDPv6;
	  else if (is_tcp())
	    type_ = TCPv6;
	  else if (is_tls())
	    type_ = TLSv6;
	  break;
	}
    }

    static Protocol parse(const std::string& str,
			  const AllowSuffix allow_suffix,
			  const char *title = nullptr)
    {
      Protocol ret;
      if (string::strcasecmp(str, "adaptive") == 0)
	return ret;
      ret.type_ = parse_type(str, allow_suffix);
      if (ret.type_ == NONE)
	{
	  if (!title)
	    title = "protocol";
	  OPENVPN_THROW(option_error, "error parsing " << title << ": " << str);
	}
      return ret;
    }

    static bool is_local_type(const std::string& str)
    {
      if (str.empty())
	return false;
      if (str[0] != 'u' && str[0] != 'U'  // unix fast path
       && str[0] != 'n' && str[0] != 'N') // named pipe fast path
	return false;
      const Type type = parse_type(str, NO_SUFFIX);
      return type == UnixStream || type == UnixDGram || type == NamedPipe;
    }

    int transport_proto() const
    {
      switch (type_)
	{
	case UDP:
	case UDPv4:
	case UDPv6:
	  return 0;
	case TCP:
	case TCPv4:
	case TCPv6:
	  return 1;
	case UnixDGram:
	  return 2;
	case UnixStream:
	  return 3;
	case NamedPipe:
	  return 4;
	case TLS:
	case TLSv4:
	case TLSv6:
	  return 5;
	default:
	  return -1;
	}
    }

    const char *str() const
    {
      switch (type_)
	{
	case UDP:
	  return "UDP";
	case UDPv4:
	  return "UDPv4";
	case UDPv6:
	  return "UDPv6";
	case TCP:
	  return "TCP";
	case TCPv4:
	  return "TCPv4";
	case TCPv6:
	  return "TCPv6";
	case TLS:
	  return "TLS/TCP";
	case TLSv4:
	  return "TLS/TCPv4";
	case TLSv6:
	  return "TLS/TCPv6";
	case UnixStream:
	  return "UnixStream";
	case UnixDGram:
	  return "UnixDGram";
	case NamedPipe:
	  return "NamedPipe";
	default:
	  return "UNDEF_PROTO";
	}
    }

    /* This function returns a parseable string representation of the used
     * transport protocol. NOTE: returns nullptr if there is no mapping */
    const char *protocol_to_string() const
    {
      switch (type_)
	{
	case UDP:
	  return "udp";
	case UDPv4:
	  return "udp4";
	case UDPv6:
	  return "udp6";
	case TCP:
	  return "tcp";
	case TCPv4:
	  return "tcp4";
	case TCPv6:
	  return "tcp6";
	case TLS:
	  return "tls";
	case TLSv4:
	  return "tls4";
	case TLSv6:
	  return "tls6";
	case UnixStream:
	  return "unix-stream";
	case UnixDGram:
	  return "unix-dgram";
	case NamedPipe:
	  return "named-pipe";
	case NONE:
	  return "adaptive";
	default:
	  return nullptr;
	}
    }

    // OpenVPN has always sent UDPv4, TCPv4_* over the wire.
    // Keep all strings v4 for backward compatibility.
    const char *occ_str(const bool server) const
    {
      switch (type_)
	{
	case UDP:
	case UDPv4:
	case UDPv6:
	  return "UDPv4";
	case TCP:
	case TCPv4:
	case TCPv6:
	  return server ? "TCPv4_SERVER" : "TCPv4_CLIENT";
	case TLS:
	case TLSv4:
	case TLSv6:
	  return "TLSv4";
	default:
	  return "UNDEF_PROTO";
	}
    }

  private:
    static Type parse_type(const std::string& str,
			   const AllowSuffix allow_suffix)
    {
      Type ret = NONE;
      std::string s = str;
      string::to_lower(s);
      switch (allow_suffix)
	{
	case NO_SUFFIX:
	  break;
	case CLIENT_SUFFIX:
	  if (string::ends_with(s, "-client"))
	    s = s.substr(0, s.length()-7);
	  break;
	case SERVER_SUFFIX:
	  if (string::ends_with(s, "-server"))
	    s = s.substr(0, s.length()-7);
	  break;
	}
      if (string::starts_with(s, "unix")) // unix domain socket
	{
	  if (s == "unix-stream")
	    ret = UnixStream;
	  else if (s == "unix-dgram")
	    ret = UnixDGram;
	}
      else if (s == "named-pipe")         // Windows named pipe
	ret = NamedPipe;
      else if (s.length() >= 3) // udp/tcp/tls
	{
	  const std::string s1 = s.substr(0, 3);
	  const std::string s2 = s.substr(3);
	  if (s2 == "")
	    {
	      if (s1 == "udp")
		ret = UDP;
	      else if (s1 == "tcp")
		ret = TCP;
	      else if (s1 == "tls")
		ret = TLS;
	    }
	  else if (s2 == "4" || s2 == "v4")
	    {
	      if (s1 == "udp")
		ret = UDPv4;
	      else if (s1 == "tcp")
		ret = TCPv4;
	      else if (s1 == "tls")
		ret = TLSv4;
	    }
	  else if (s2 == "6" || s2 == "v6")
	    {
	      if (s1 == "udp")
		ret = UDPv6;
	      else if (s1 == "tcp")
		ret = TCPv6;
	      else if (s1 == "tls")
		ret = TLSv6;
	    }
	}
      return ret;
    }

    Type type_;
  };
} // namespace openvpn

#endif // OPENVPN_TRANSPORT_PROTOCOL_H
