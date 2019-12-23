//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2017 OpenVPN Inc.
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

// tun interface utilities for Windows

#ifndef OPENVPN_TUN_WIN_TUNUTIL_H
#define OPENVPN_TUN_WIN_TUNUTIL_H

#include <openvpn/common/socktypes.hpp> // prevent winsock multiple def errors

#include <windows.h>
#include <winsock2.h> // for IPv6
#include <winioctl.h>
#include <iphlpapi.h>
#include <ntddndis.h>
#include <wininet.h>
#include <ws2tcpip.h> // for IPv6

#include <string>
#include <vector>
#include <sstream>
#include <cstdint> // for std::uint32_t
#include <memory>

#include <tap-windows.h>

#include <openvpn/common/to_string.hpp>
#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/socktypes.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/stringize.hpp>
#include <openvpn/common/action.hpp>
#include <openvpn/common/uniqueptr.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/addr/ip.hpp>
#include <openvpn/tun/builder/capture.hpp>
#include <openvpn/win/reg.hpp>
#include <openvpn/win/scoped_handle.hpp>
#include <openvpn/win/unicode.hpp>
#include <openvpn/win/cmd.hpp>
#include <openvpn/win/winerr.hpp>

namespace openvpn {
  namespace TunWin {
    namespace Util {
      OPENVPN_EXCEPTION(tun_win_util);

      namespace {
	// from tap-windows.h
	const char ADAPTER[] = ADAPTER_KEY; // CONST GLOBAL
	const char NETWORK_CONNECTIONS[] = NETWORK_CONNECTIONS_KEY; // CONST GLOBAL

	// generally defined on cl command line
	const char COMPONENT_ID[] = OPENVPN_STRINGIZE(TAP_WIN_COMPONENT_ID); // CONST GLOBAL
      }

      // Return a list of TAP device GUIDs installed on the system,
      // filtered by TAP_WIN_COMPONENT_ID.
      inline std::vector<std::string> tap_guids()
      {
	LONG status;
	DWORD len;
	DWORD data_type;

	std::vector<std::string> ret;

	Win::RegKey adapter_key;
	status = ::RegOpenKeyExA(HKEY_LOCAL_MACHINE,
				 ADAPTER,
				 0,
				 KEY_READ,
				 adapter_key.ref());
	if (status != ERROR_SUCCESS)
	  {
	    const Win::Error err(status);
	    OPENVPN_THROW(tun_win_util, "tap_guids: error opening adapter registry key: " << ADAPTER << " : " << err.message());
	  }

	for (int i = 0;; ++i)
	  {
	    char strbuf[256];
	    Win::RegKey unit_key;

	    len = sizeof(strbuf);
	    status = ::RegEnumKeyExA(adapter_key(),
				     i,
				     strbuf,
				     &len,
				     nullptr,
				     nullptr,
				     nullptr,
				     nullptr);
	    if (status == ERROR_NO_MORE_ITEMS)
	      break;
	    else if (status != ERROR_SUCCESS)
	      OPENVPN_THROW(tun_win_util, "tap_guids: error enumerating registry subkeys of key: " << ADAPTER);
	    strbuf[len] = '\0';

	    const std::string unit_string = std::string(ADAPTER)
	                                  + std::string("\\")
	                                  + std::string(strbuf);

	    status = ::RegOpenKeyExA(HKEY_LOCAL_MACHINE,
				     unit_string.c_str(),
				     0,
				     KEY_READ,
				     unit_key.ref());

	    if (status != ERROR_SUCCESS)
	      continue;

	    len = sizeof(strbuf);
	    status = ::RegQueryValueExA(unit_key(),
					"ComponentId",
					nullptr,
					&data_type,
					(LPBYTE)strbuf,
					&len);

	    if (status != ERROR_SUCCESS || data_type != REG_SZ)
	      continue;
	    strbuf[len] = '\0';
	    if (std::strcmp(strbuf, COMPONENT_ID))
	      continue;

	    len = sizeof(strbuf);
	    status = ::RegQueryValueExA(unit_key(),
					"NetCfgInstanceId",
					nullptr,
					&data_type,
					(LPBYTE)strbuf,
					&len);

	    if (status == ERROR_SUCCESS && data_type == REG_SZ)
	      {
		strbuf[len] = '\0';
		ret.push_back(std::string(strbuf));
	      }
	  }
	return ret;
      }

      struct TapNameGuidPair
      {
	TapNameGuidPair() : index(DWORD(-1)) {}

	bool index_defined() const { return index != DWORD(-1); }

	std::string index_or_name() const
	{
	  if (index_defined())
	    return to_string(index);
	  else if (!name.empty())
	    return '"' + name + '"';
	  else
	    OPENVPN_THROW(tun_win_util, "TapNameGuidPair: TAP interface " << guid << " has no name or interface index");
	}

	std::string name;
	std::string guid;
	DWORD index;
      };

      struct TapNameGuidPairList : public std::vector<TapNameGuidPair>
      {
	TapNameGuidPairList()
	{
	  // first get the TAP guids
	  {
	    std::vector<std::string> guids = tap_guids();
	    for (std::vector<std::string>::const_iterator i = guids.begin(); i != guids.end(); i++)
	      {
		TapNameGuidPair pair;
		pair.guid = *i;

		// lookup adapter index
		{
		  ULONG aindex;
		  const size_t len = 128;
		  wchar_t wbuf[len];
		  _snwprintf(wbuf, len, L"\\DEVICE\\TCPIP_%S", pair.guid.c_str());
		  wbuf[len-1] = 0;
		  if (::GetAdapterIndex(wbuf, &aindex) == NO_ERROR)
		    pair.index = aindex;
		}

		push_back(pair);
	      }
	  }

	  // next, match up control panel interface names with GUIDs
	  {
	    LONG status;
	    DWORD len;
	    DWORD data_type;

	    Win::RegKey network_connections_key;
	    status = ::RegOpenKeyExA(HKEY_LOCAL_MACHINE,
				     NETWORK_CONNECTIONS,
				     0,
				     KEY_READ,
				     network_connections_key.ref());
	    if (status != ERROR_SUCCESS)
	      {
		const Win::Error err(status);
		OPENVPN_THROW(tun_win_util, "TapNameGuidPairList: error opening network connections registry key: " << NETWORK_CONNECTIONS << " : " << err.message());
	      }

	    for (int i = 0;; ++i)
	      {
		char strbuf[256];
		Win::RegKey connection_key;

		len = sizeof(strbuf);
		status = ::RegEnumKeyExA(network_connections_key(),
					 i,
					 strbuf,
					 &len,
					 nullptr,
					 nullptr,
					 nullptr,
					 nullptr);
		if (status == ERROR_NO_MORE_ITEMS)
		  break;
		else if (status != ERROR_SUCCESS)
		  OPENVPN_THROW(tun_win_util, "TapNameGuidPairList: error enumerating registry subkeys of key: " << NETWORK_CONNECTIONS);
		strbuf[len] = '\0';

		const std::string guid = std::string(strbuf);
		const std::string connection_string = std::string(NETWORK_CONNECTIONS) + std::string("\\") + guid + std::string("\\Connection");

		status = ::RegOpenKeyExA(HKEY_LOCAL_MACHINE,
					 connection_string.c_str(),
					 0,
					 KEY_READ,
					 connection_key.ref());
		if (status != ERROR_SUCCESS)
		  continue;

		len = sizeof(strbuf);
		status = ::RegQueryValueExA(connection_key(),
					    "Name",
					    nullptr,
					    &data_type,
					    (LPBYTE)strbuf,
					    &len);
		if (status != ERROR_SUCCESS || data_type != REG_SZ)
		  continue;
		strbuf[len] = '\0';
		const std::string name = std::string(strbuf);

		// iterate through self and try to patch the name
		{
		  for (iterator j = begin(); j != end(); j++)
		    {
		      TapNameGuidPair& pair = *j;
		      if (pair.guid == guid)
			pair.name = name;
		    }
		}
	      }
	  }
	}

	std::string to_string() const
	{
	  std::ostringstream os;
	  for (const_iterator i = begin(); i != end(); i++)
	    {
	      const TapNameGuidPair& pair = *i;
	      os << "guid='" << pair.guid << '\'';
	      if (pair.index_defined())
		os << " index=" << pair.index;
	      if (!pair.name.empty())
		os << " name='" << pair.name << '\'';
	      os << std::endl;
	    }
	  return os.str();
	}

	std::string name_from_guid(const std::string& guid) const
	{
	  for (const_iterator i = begin(); i != end(); i++)
	    {
	      const TapNameGuidPair& pair = *i;
	      if (pair.guid == guid)
		return pair.name;
	    }
	}

	std::string guid_from_name(const std::string& name) const
	{
	  for (const_iterator i = begin(); i != end(); i++)
	    {
	      const TapNameGuidPair& pair = *i;
	      if (pair.name == name)
		return pair.guid;
	    }
	}
      };

      // given a TAP GUID, form the pathname of the TAP device node
      inline std::string tap_path(const std::string& tap_guid)
      {
	return std::string(USERMODEDEVICEDIR) + tap_guid + std::string(TAP_WIN_SUFFIX);
      }

      // open an available TAP adapter
      inline HANDLE tap_open(const TapNameGuidPairList& guids,
			     std::string& path_opened,
			     TapNameGuidPair& used)
      {
	Win::ScopedHANDLE hand;

	// iterate over list of TAP adapters on system
	for (TapNameGuidPairList::const_iterator i = guids.begin(); i != guids.end(); i++)
	  {
	    const TapNameGuidPair& tap = *i;
	    const std::string path = tap_path(tap.guid);
	    hand.reset(::CreateFileA(path.c_str(),
				     GENERIC_READ | GENERIC_WRITE,
				     0, /* was: FILE_SHARE_READ */
				     0,
				     OPEN_EXISTING,
				     FILE_ATTRIBUTE_SYSTEM | FILE_FLAG_OVERLAPPED,
				     0));
	    if (hand.defined())
	      {
		used = tap;
		path_opened = path;
		break;
	      }
	  }
	return hand.release();
      }

      // set TAP adapter to topology subnet
      inline void tap_configure_topology_subnet(HANDLE th, const IP::Addr& local, const unsigned int prefix_len)
      {
	const IPv4::Addr netmask = IPv4::Addr::netmask_from_prefix_len(prefix_len);
	const IPv4::Addr network = local.to_ipv4() & netmask;

	std::uint32_t ep[3];
	ep[0] = htonl(local.to_ipv4().to_uint32());
	ep[1] = htonl(network.to_uint32());
	ep[2] = htonl(netmask.to_uint32());

	DWORD len;
	if (!::DeviceIoControl(th, TAP_WIN_IOCTL_CONFIG_TUN,
			       ep, sizeof (ep),
			       ep, sizeof (ep), &len, nullptr))
	  throw tun_win_util("DeviceIoControl TAP_WIN_IOCTL_CONFIG_TUN failed");
      }

      // set TAP adapter to topology net30
      inline void tap_configure_topology_net30(HANDLE th, const IP::Addr& local_addr, const unsigned int prefix_len)
      {
	const IPv4::Addr local = local_addr.to_ipv4();
	const IPv4::Addr netmask = IPv4::Addr::netmask_from_prefix_len(prefix_len);
	const IPv4::Addr network = local & netmask;
	const IPv4::Addr remote = network + 1;

	std::uint32_t ep[2];
	ep[0] = htonl(local.to_uint32());
	ep[1] = htonl(remote.to_uint32());

	DWORD len;
	if (!::DeviceIoControl(th, TAP_WIN_IOCTL_CONFIG_POINT_TO_POINT,
			       ep, sizeof (ep),
			       ep, sizeof (ep), &len, nullptr))
	  throw tun_win_util("DeviceIoControl TAP_WIN_IOCTL_CONFIG_POINT_TO_POINT failed");
      }

      // set driver media status to 'connected'
      inline void tap_set_media_status(HANDLE th, bool media_status)
      {
	DWORD len;
	ULONG status = media_status ? TRUE : FALSE;
	if (!::DeviceIoControl(th, TAP_WIN_IOCTL_SET_MEDIA_STATUS,
			       &status, sizeof (status),
			       &status, sizeof (status), &len, nullptr))
	  throw tun_win_util("DeviceIoControl TAP_WIN_IOCTL_SET_MEDIA_STATUS failed");
      }

      // get debug logging from TAP driver (requires that
      // TAP driver was built with logging enabled)
      inline void tap_process_logging(HANDLE th)
      {
	const size_t size = 1024;
	std::unique_ptr<char[]> line(new char[size]);
	DWORD len;

	while (::DeviceIoControl(th, TAP_WIN_IOCTL_GET_LOG_LINE,
				 line.get(), size,
				 line.get(), size,
				 &len, nullptr))
	  {
	    OPENVPN_LOG("TAP-Windows: " << line.get());
	  }
      }

      struct InterfaceInfoList
      {
      public:
	InterfaceInfoList()
	{
	  DWORD size = 0;
	  if (::GetInterfaceInfo(nullptr, &size) != ERROR_INSUFFICIENT_BUFFER)
	    OPENVPN_THROW(tun_win_util, "InterfaceInfoList: GetInterfaceInfo #1");
	  list.reset((IP_INTERFACE_INFO*)new unsigned char[size]);
	  if (::GetInterfaceInfo(list.get(), &size) != NO_ERROR)
	    OPENVPN_THROW(tun_win_util, "InterfaceInfoList: GetInterfaceInfo #2");
	}

	IP_ADAPTER_INDEX_MAP* iface(const DWORD index) const
	{
	  if (list)
	    {
	      for (unsigned int i = 0; i < list->NumAdapters; ++i)
		{
		  IP_ADAPTER_INDEX_MAP* inter = &list->Adapter[i];
		  if (index == inter->Index)
		    return inter;
		}
	    }
	  return nullptr;
	}

	std::unique_ptr<IP_INTERFACE_INFO> list;
      };

      inline void dhcp_release(const InterfaceInfoList& ii,
			       const DWORD adapter_index,
			       std::ostream& os)
      {
	IP_ADAPTER_INDEX_MAP* iface = ii.iface(adapter_index);
	if (iface)
	  {
	    const DWORD status = ::IpReleaseAddress(iface);
	    if (status == NO_ERROR)
	      os << "TAP: DHCP release succeeded" << std::endl;
	    else
	      os << "TAP: DHCP release failed" << std::endl;
	  }
      }

      inline void dhcp_renew(const InterfaceInfoList& ii,
			     const DWORD adapter_index,
			     std::ostream& os)
      {
	IP_ADAPTER_INDEX_MAP* iface = ii.iface(adapter_index);
	if (iface)
	  {
	    const DWORD status = ::IpRenewAddress(iface);
	    if (status == NO_ERROR)
	      os << "TAP: DHCP renew succeeded" << std::endl;
	    else
	      os << "TAP: DHCP renew failed" << std::endl;
	  }
      }

      inline void flush_arp(const DWORD adapter_index,
			    std::ostream& os)
      {
	const DWORD status = ::FlushIpNetTable(adapter_index);
	if (status == NO_ERROR)
	  os << "TAP: ARP flush succeeded" << std::endl;
	else
	  os << "TAP: ARP flush failed" << std::endl;
      }

      struct IPNetmask4
      {
	IPNetmask4() {}

	IPNetmask4(const TunBuilderCapture& pull, const char *title)
	{
	  const TunBuilderCapture::RouteAddress* local4 = pull.vpn_ipv4();
	  if (local4)
	    {
	      ip = IPv4::Addr::from_string(local4->address, title);
	      netmask = IPv4::Addr::netmask_from_prefix_len(local4->prefix_length);
	    }
	}

	IPNetmask4(const IP_ADDR_STRING *ias)
	{
	  if (ias)
	    {
	      try {
		if (ias->IpAddress.String)
		  ip = IPv4::Addr::from_string(ias->IpAddress.String);
	      }
	      catch (const std::exception&)
		{
		}
	      try {
		if (ias->IpMask.String)
		  netmask = IPv4::Addr::from_string(ias->IpMask.String);
	      }
	      catch (const std::exception&)
		{
		}
	    }
	}

	bool operator==(const IPNetmask4& rhs) const
	{
	  return ip == rhs.ip && netmask == rhs.netmask;
	}

	bool operator!=(const IPNetmask4& rhs) const
	{
	  return !operator==(rhs);
	}

	IPv4::Addr ip = IPv4::Addr::from_zero();
	IPv4::Addr netmask = IPv4::Addr::from_zero();
      };

      struct IPAdaptersInfo
      {
	IPAdaptersInfo()
	{
	  ULONG size = 0;
	  if (::GetAdaptersInfo(nullptr, &size) != ERROR_BUFFER_OVERFLOW)
	    OPENVPN_THROW(tun_win_util, "IPAdaptersInfo: GetAdaptersInfo #1");
	  list.reset((IP_ADAPTER_INFO*)new unsigned char[size]);
	  if (::GetAdaptersInfo(list.get(), &size) != NO_ERROR)
	    OPENVPN_THROW(tun_win_util, "IPAdaptersInfo: GetAdaptersInfo #2");
	}

	const IP_ADAPTER_INFO* adapter(const DWORD index) const
	{
	  if (list)
	    {
	      for (const IP_ADAPTER_INFO* a = list.get(); a != nullptr; a = a->Next)
		{
		  if (index == a->Index)
		    return a;
		}
	    }
	  return nullptr;
	}

	bool is_up(const DWORD index, const IPNetmask4& vpn_addr) const
	{
	  const IP_ADAPTER_INFO* ai = adapter(index);
	  if (ai)
	    {
	      for (const IP_ADDR_STRING *iplist = &ai->IpAddressList; iplist != nullptr; iplist = iplist->Next)
		{
		  if (vpn_addr == IPNetmask4(iplist))
		    return true;
		}
	    }
	  return false;
	}

	bool is_dhcp_enabled(const DWORD index) const
	{
	  const IP_ADAPTER_INFO* ai = adapter(index);
	  return ai && ai->DhcpEnabled;
	}

	std::unique_ptr<IP_ADAPTER_INFO> list;
      };

      struct IPPerAdapterInfo
      {
	IPPerAdapterInfo(const DWORD index)
	{
	  ULONG size = 0;
	  if (::GetPerAdapterInfo(index, nullptr, &size) != ERROR_BUFFER_OVERFLOW)
	    return;
	  adapt.reset((IP_PER_ADAPTER_INFO*)new unsigned char[size]);
	  if (::GetPerAdapterInfo(index, adapt.get(), &size) != ERROR_SUCCESS)
	    adapt.reset();
	}

	std::unique_ptr<IP_PER_ADAPTER_INFO> adapt;
      };

      // Use the TAP DHCP masquerade capability to set TAP adapter properties.
      // Generally only used on pre-Vista.
      class TAPDHCPMasquerade
      {
      public:
	OPENVPN_EXCEPTION(dhcp_masq);

	// VPN IP/netmask
	IPNetmask4 vpn;

	// IP address of fake DHCP server in TAP adapter
	IPv4::Addr dhcp_serv_addr = IPv4::Addr::from_zero();

	// DHCP lease for one year
	unsigned int lease_time = 31536000;

	// DHCP options
	std::string domain;            // DOMAIN (15)
	std::string netbios_scope;     // NBS (47)
	int netbios_node_type = 0;     // NBT 1,2,4,8 (46)
	bool disable_nbt = false;      // DISABLE_NBT (43, Vendor option 001)
	std::vector<IPv4::Addr> dns;   // DNS (6)
	std::vector<IPv4::Addr> wins;  // WINS (44)
	std::vector<IPv4::Addr> ntp;   // NTP (42)
	std::vector<IPv4::Addr> nbdd;  // NBDD (45)

	void init_from_capture(const TunBuilderCapture& pull)
	{
	  // VPN IP/netmask
	  vpn = IPNetmask4(pull, "VPN IP");

	  // DHCP server address
	  {
	    const IPv4::Addr network_addr = vpn.ip & vpn.netmask;
	    const std::uint32_t extent = vpn.netmask.extent_from_netmask_uint32();
	    if (extent >= 16)
	      dhcp_serv_addr = network_addr + (extent - 2);
	    else
	      dhcp_serv_addr = network_addr;
	  }

	  // DNS
	  for (auto &ds : pull.dns_servers)
	    {
	      if (!ds.ipv6)
		dns.push_back(IPv4::Addr::from_string(ds.address, "DNS Server"));
	    }

	  // WINS
	  for (auto &ws : pull.wins_servers)
	    wins.push_back(IPv4::Addr::from_string(ws.address, "WINS Server"));

	  // DOMAIN
	  if (!pull.search_domains.empty())
	    domain = pull.search_domains[0].domain;
	}

	void ioctl(HANDLE th) const
	{
	  // TAP_WIN_IOCTL_CONFIG_DHCP_MASQ
	  {
	    std::uint32_t ep[4];
	    ep[0] = vpn.ip.to_uint32_net();
	    ep[1] = vpn.netmask.to_uint32_net();
	    ep[2] = dhcp_serv_addr.to_uint32_net();
	    ep[3] = lease_time;

	    DWORD len;
	    if (!::DeviceIoControl(th, TAP_WIN_IOCTL_CONFIG_DHCP_MASQ,
				   ep, sizeof (ep),
				   ep, sizeof (ep), &len, nullptr))
	      throw dhcp_masq("DeviceIoControl TAP_WIN_IOCTL_CONFIG_DHCP_MASQ failed");
	  }

	  // TAP_WIN_IOCTL_CONFIG_DHCP_SET_OPT
	  {
	    BufferAllocated buf(256, BufferAllocated::GROW);
	    write_options(buf);

	    DWORD len;
	    if (!::DeviceIoControl(th, TAP_WIN_IOCTL_CONFIG_DHCP_SET_OPT,
				   buf.data(), buf.size(),
				   buf.data(), buf.size(), &len, nullptr))
	      throw dhcp_masq("DeviceIoControl TAP_WIN_IOCTL_CONFIG_DHCP_SET_OPT failed");
	  }
	}

      private:
	void write_options(Buffer& buf) const
	{
	  // DOMAIN
	  write_dhcp_str(buf, 15, domain);

	  // NBS
	  write_dhcp_str(buf, 47, netbios_scope);

	  // NBT
	  if (netbios_node_type)
	    write_dhcp_u8(buf, 46, netbios_node_type);

	  // DNS
	  write_dhcp_addr_list(buf, 6, dns);

	  // WINS
	  write_dhcp_addr_list(buf, 44, wins);

	  // NTP
	  write_dhcp_addr_list(buf, 42, ntp);

	  // NBDD
	  write_dhcp_addr_list(buf, 45, nbdd);

	  // DISABLE_NBT
	  //
	  // The MS DHCP server option 'Disable Netbios-over-TCP/IP
	  // is implemented as vendor option 001, value 002.
	  // A value of 001 means 'leave NBT alone' which is the default.
	  if (disable_nbt)
	    {
	      buf.push_back(43);
	      buf.push_back(6);     // total length field
	      buf.push_back(0x001);
	      buf.push_back(4);     // length of the vendor-specified field
	      {
		const std::uint32_t raw = 0x002;
		buf.write((const unsigned char *)&raw, sizeof(raw));
	      }
	    }
	}

	static void write_dhcp_u8(Buffer& buf,
				  const unsigned char type,
				  const unsigned char data)
	{
	  buf.push_back(type);
	  buf.push_back(1);
	  buf.push_back(data);
	}

	static void write_dhcp_str(Buffer& buf,
				   const unsigned char type,
				   const std::string& str)
	{
	  const size_t len = str.length();
	  if (len)
	    {
	      if (len > 255)
		OPENVPN_THROW(dhcp_masq, "string '" << str << "' must be > 0 bytes and <= 255 bytes");
	      buf.push_back(type);
	      buf.push_back((unsigned char)len);
	      buf.write((const unsigned char *)str.c_str(), len);
	    }
	}

	static void write_dhcp_addr_list(Buffer& buf,
					 const unsigned char type,
					 const std::vector<IPv4::Addr>& addr_list)
	{
	  if (!addr_list.empty())
	    {
	      const size_t size = addr_list.size() * sizeof(std::uint32_t);
	      if (size < 1 || size > 255)
		OPENVPN_THROW(dhcp_masq, "array size=" << size << " must be > 0 bytes and <= 255 bytes");
	      buf.push_back(type);
	      buf.push_back((unsigned char)size);
	      for (auto &a : addr_list)
		{
		  const std::uint32_t rawaddr = a.to_uint32_net();
		  buf.write((const unsigned char *)&rawaddr, sizeof(std::uint32_t));
		}
	    }
	}
      };

      class TAPDriverVersion
      {
      public:
	TAPDriverVersion(HANDLE th)
	  : defined(false)
	{
	  DWORD len;
	  info[0] = info[1] = info[2] = 0;
	  if (::DeviceIoControl(th, TAP_WIN_IOCTL_GET_VERSION,
				&info, sizeof (info),
				&info, sizeof (info), &len, nullptr))
	    defined = true;
	}

	std::string to_string()
	{
	  std::ostringstream os;
	  os << "TAP-Windows Driver Version ";
	  if (defined)
	    {
	      os << info[0] << '.' << info[1];
	      if (info[2])
		os << " (DEBUG)";
	    }
	  else
	    os << "UNDEFINED";
	  return os.str();
	}

      private:
	bool defined;
	ULONG info[3];
      };

      // An action to set the DNS "Connection-specific DNS Suffix"
      class ActionSetAdapterDomainSuffix : public Action
      {
      public:
	ActionSetAdapterDomainSuffix(const std::string& search_domain_arg,
			      const std::string& tap_guid_arg)
	  : search_domain(search_domain_arg),
	    tap_guid(tap_guid_arg)
	{
	}

	virtual void execute(std::ostream& os) override
	{
	  os << to_string() << std::endl;

	  LONG status;
	  Win::RegKey key;
	  const std::string reg_key_name = "SYSTEM\\CurrentControlSet\\services\\Tcpip\\Parameters\\Interfaces\\" + tap_guid;
	  status = ::RegOpenKeyExA(HKEY_LOCAL_MACHINE,
				   reg_key_name.c_str(),
				   0,
				   KEY_READ|KEY_WRITE,
				   key.ref());
	  if (status != ERROR_SUCCESS)
	    {
	      const Win::Error err(status);
	      OPENVPN_THROW(tun_win_util, "ActionSetAdapterDomainSuffix: error opening registry key: " << reg_key_name << " : " << err.message());
	    }

	  Win::UTF16 dom(Win::utf16(search_domain));
	  status = ::RegSetValueExW(key(),
				    L"Domain",
				    0,
				    REG_SZ,
				    (const BYTE *)dom.get(),
				    (Win::utf16_strlen(dom.get())+1)*2);
	  if (status != ERROR_SUCCESS)
	    OPENVPN_THROW(tun_win_util, "ActionSetAdapterDomainSuffix: error writing Domain registry key: " << reg_key_name);
	}

	virtual std::string to_string() const override
	{
	  return "Set adapter domain suffix: '" + search_domain + "' " + tap_guid;
	}

      private:
	const std::string search_domain;
	const std::string tap_guid;
      };

      // get the Windows IPv4 routing table
      inline const MIB_IPFORWARDTABLE* windows_routing_table()
      {
	ULONG size = 0;
	DWORD status;
	std::unique_ptr<MIB_IPFORWARDTABLE> rt;

	status = ::GetIpForwardTable(nullptr, &size, TRUE);
	if (status == ERROR_INSUFFICIENT_BUFFER)
	  {
	    rt.reset((MIB_IPFORWARDTABLE*)new unsigned char[size]);
	    status = ::GetIpForwardTable(rt.get(), &size, TRUE);
	    if (status != NO_ERROR)
	      {
		OPENVPN_LOG("windows_routing_table: GetIpForwardTable failed");
		return nullptr;
	      }
	  }
	return rt.release();
      }

#if _WIN32_WINNT >= 0x0600 // Vista and higher
      // Get the Windows IPv4/IPv6 routing table.
      // Note that returned pointer must be freed with FreeMibTable.
      inline const MIB_IPFORWARD_TABLE2* windows_routing_table2(ADDRESS_FAMILY af)
      {
	MIB_IPFORWARD_TABLE2* routes = nullptr;
	int res = ::GetIpForwardTable2(af, &routes);
	if (res == NO_ERROR)
	  return routes;
	else
	  return nullptr;
      }
#endif

      // Get the current default gateway
      class DefaultGateway
      {
      public:
	DefaultGateway()
	  : index(DWORD(-1))
	{
	  std::unique_ptr<const MIB_IPFORWARDTABLE> rt(windows_routing_table());
	  if (rt)
	    {
	      const MIB_IPFORWARDROW* gw = nullptr;
	      for (size_t i = 0; i < rt->dwNumEntries; ++i)
		{
		  const MIB_IPFORWARDROW* row = &rt->table[i];
		  if (!row->dwForwardDest && !row->dwForwardMask
		      && (!gw || row->dwForwardMetric1 < gw->dwForwardMetric1))
		    gw = row;
		}
	      if (gw)
		{
		  index = gw->dwForwardIfIndex;
		  addr = IPv4::Addr::from_uint32(ntohl(gw->dwForwardNextHop)).to_string();
		}
	    }
	}

	bool defined() const
	{
	  return index != DWORD(-1) && !addr.empty();
	}

	DWORD interface_index() const
	{
	  return index;
	}

	const std::string& gateway_address() const
	{
	  return addr;
	}

      private:
	DWORD index;
	std::string addr;
      };

      // An action to delete all routes on an interface
      class ActionDeleteAllRoutesOnInterface : public Action
      {
      public:
	ActionDeleteAllRoutesOnInterface(const DWORD iface_index_arg)
	  : iface_index(iface_index_arg)
	{
	}

	virtual void execute(std::ostream& os) override
	{
	  os << to_string() << std::endl;

	  ActionList::Ptr actions = new ActionList();
	  remove_all_ipv4_routes_on_iface(iface_index, *actions);
#if _WIN32_WINNT >= 0x0600 // Vista and higher
	  remove_all_ipv6_routes_on_iface(iface_index, *actions);
#endif
	  actions->execute(os);
	}

	virtual std::string to_string() const override
	{
	  return "ActionDeleteAllRoutesOnInterface iface_index=" + std::to_string(iface_index);
	}

      private:
	static void remove_all_ipv4_routes_on_iface(DWORD index, ActionList& actions)
	{
	  std::unique_ptr<const MIB_IPFORWARDTABLE> rt(windows_routing_table());
	  if (rt)
	    {
	      for (size_t i = 0; i < rt->dwNumEntries; ++i)
		{
		  const MIB_IPFORWARDROW* row = &rt->table[i];
		  if (row->dwForwardIfIndex == index)
		    {
		      const IPv4::Addr net = IPv4::Addr::from_uint32(ntohl(row->dwForwardDest));
		      const IPv4::Addr mask = IPv4::Addr::from_uint32(ntohl(row->dwForwardMask));
		      const std::string net_str = net.to_string();
		      const unsigned int pl = mask.prefix_len();

		      // don't remove multicast route or other Windows-assigned routes
		      if (net_str == "224.0.0.0" && pl == 4)
			continue;
		      if (net_str == "255.255.255.255" && pl == 32)
			continue;

		      actions.add(new WinCmd("netsh interface ip delete route " + net_str + '/' + openvpn::to_string(pl) + ' ' + openvpn::to_string(index) + " store=active"));
		    }
		}
	    }
	}

#if _WIN32_WINNT >= 0x0600 // Vista and higher
	static void remove_all_ipv6_routes_on_iface(DWORD index, ActionList& actions)
	{
	  unique_ptr_del<const MIB_IPFORWARD_TABLE2> rt2(windows_routing_table2(AF_INET6),
							 [](const MIB_IPFORWARD_TABLE2* p) { FreeMibTable((PVOID)p); });
	  if (rt2)
	    {
	      const IPv6::Addr ll_net = IPv6::Addr::from_string("fe80::");
	      const IPv6::Addr ll_mask = IPv6::Addr::netmask_from_prefix_len(64);
	      for (size_t i = 0; i < rt2->NumEntries; ++i)
		{
		  const MIB_IPFORWARD_ROW2* row = &rt2->Table[i];
		  if (row->InterfaceIndex == index)
		    {
		      const unsigned int pl = row->DestinationPrefix.PrefixLength;
		      if (row->DestinationPrefix.Prefix.si_family == AF_INET6)
			{
			  const IPv6::Addr net = IPv6::Addr::from_byte_string(row->DestinationPrefix.Prefix.Ipv6.sin6_addr.u.Byte);
			  const std::string net_str = net.to_string();

			  // don't remove multicast route or other Windows-assigned routes
			  if (net_str == "ff00::" && pl == 8)
			    continue;
			  if ((net & ll_mask) == ll_net && pl >= 64)
			    continue;
			  actions.add(new WinCmd("netsh interface ipv6 delete route " + net_str + '/' + openvpn::to_string(pl) + ' ' + openvpn::to_string(index) + " store=active"));
			}
		    }
		}
	    }
	}
#endif

	const DWORD iface_index;
      };

      class ActionEnableDHCP : public WinCmd
      {
      public:
	ActionEnableDHCP(const TapNameGuidPair& tap)
	  : WinCmd(cmd(tap))
	{
	}

      private:
	static std::string cmd(const TapNameGuidPair& tap)
	{
	  return "netsh interface ip set address " + tap.index_or_name() + " dhcp";
	}
      };

    }
  }
}

#endif
