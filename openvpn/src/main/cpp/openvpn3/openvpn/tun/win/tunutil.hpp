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
#include <tlhelp32.h> // for impersonating as LocalSystem


#include <setupapi.h>
#include <devguid.h>
#include <cfgmgr32.h>

#ifdef __MINGW32__
#include <ddk/ndisguid.h>
#else
#include <ndisguid.h>
#endif

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
#include <openvpn/common/wstring.hpp>
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
    enum Type {
      TapWindows6,
      Wintun,
      OvpnDco
    };

    namespace Util {
      OPENVPN_EXCEPTION(tun_win_util);

      namespace {
	// from tap-windows.h
	const char ADAPTER[] = ADAPTER_KEY; // CONST GLOBAL
	const char NETWORK_CONNECTIONS[] = NETWORK_CONNECTIONS_KEY; // CONST GLOBAL

	// generally defined on cl command line
	const char COMPONENT_ID[] = OPENVPN_STRINGIZE(TAP_WIN_COMPONENT_ID); // CONST GLOBAL
	const char WINTUN_COMPONENT_ID[] = "wintun"; // CONST GLOBAL
	const char OVPNDCO_COMPONENT_ID[] = "ovpn-dco"; // CONST GLOBAL

	const char ROOT_COMPONENT_ID[] = "root\\" OPENVPN_STRINGIZE(TAP_WIN_COMPONENT_ID);
	const char ROOT_WINTUN_COMPONENT_ID[] = "root\\wintun"; 
	const char ROOT_OVPNDCO_COMPONENT_ID[] = "root\\ovpn-dco";
      }

      using TapGuidLuid = std::pair<std::string, DWORD>;

      // Return a list of TAP device GUIDs installed on the system,
      // filtered by TAP_WIN_COMPONENT_ID.
      inline std::vector<TapGuidLuid> tap_guids(const Type tun_type)
      {
	LONG status;
	DWORD len;
	DWORD data_type;

	std::vector<TapGuidLuid> ret;

	const char *component_id;
	const char *root_component_id;

	switch (tun_type) {
	case TapWindows6:
	  component_id = COMPONENT_ID;
	  root_component_id = ROOT_COMPONENT_ID;
	  break;
	case Wintun:
	  component_id = WINTUN_COMPONENT_ID;
	  root_component_id = ROOT_WINTUN_COMPONENT_ID;
	  break;
	case OvpnDco:
	  component_id = OVPNDCO_COMPONENT_ID;
	  root_component_id = ROOT_OVPNDCO_COMPONENT_ID;
	  break;
	default:
	  OPENVPN_THROW(tun_win_util, "tap_guids: unsupported TAP type");
	  break;
	}

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
	    if (string::strcasecmp(strbuf, component_id) &&
	        string::strcasecmp(strbuf, root_component_id))
	      continue;

	    TapGuidLuid tgl;

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
		tgl.first = std::string(strbuf);
	      }

	    DWORD luid;
	    len = sizeof(luid);
	    status = ::RegQueryValueExA(unit_key(),
					"NetLuidIndex",
					nullptr,
					&data_type,
					(LPBYTE)&luid,
					&len);

	    if (status == ERROR_SUCCESS && data_type == REG_DWORD)
	      {
		tgl.second = luid;
	      }

	    ret.push_back(tgl);
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
	DWORD net_luid_index;
	DWORD index;
      };

      struct TapNameGuidPairList : public std::vector<TapNameGuidPair>
      {
	TapNameGuidPairList(const Type tun_type)
	{
	  // first get the TAP guids
	  {
	    std::vector<TapGuidLuid> guids = tap_guids(tun_type);
	    for (auto i = guids.begin(); i != guids.end(); i++)
	      {
		TapNameGuidPair pair;
		pair.guid = i->first;
		pair.net_luid_index = i->second;

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

		wchar_t wbuf[256] = L"";
		DWORD cbwbuf = sizeof(wbuf);
		status = ::RegQueryValueExW(connection_key(),
					    L"Name",
					    nullptr,
					    &data_type,
					    (LPBYTE)wbuf,
					    &cbwbuf);
		if (status != ERROR_SUCCESS || data_type != REG_SZ)
		  continue;
		wbuf[(cbwbuf / sizeof(wchar_t)) - 1] = L'\0';

		// iterate through self and try to patch the name
		{
		  for (iterator j = begin(); j != end(); j++)
		    {
		      TapNameGuidPair& pair = *j;
		      if (pair.guid == guid)
			pair.name = wstring::to_utf8(wbuf);
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

      struct DeviceInstanceIdInterfacePair
      {
	std::string net_cfg_instance_id;
	std::string device_interface_list;
      };

      class DevInfoSetHelper
      {
      public:
	DevInfoSetHelper()
	{
	  handle = SetupDiGetClassDevsEx(&GUID_DEVCLASS_NET, NULL, NULL, DIGCF_PRESENT, NULL, NULL, NULL);
	}

	bool is_valid()
	{
	  return handle != INVALID_HANDLE_VALUE;
	}

	operator HDEVINFO()
	{
	  return handle;
	}

	~DevInfoSetHelper()
	{
	  if (is_valid())
	    {
	      SetupDiDestroyDeviceInfoList(handle);
	    }
	}

      private:
	HDEVINFO handle;
      };

      struct DeviceInstanceIdInterfaceList : public std::vector<DeviceInstanceIdInterfacePair>
      {
	DeviceInstanceIdInterfaceList()
	{
	  DevInfoSetHelper device_info_set;
	  if (!device_info_set.is_valid())
	    return;

	  for (DWORD i = 0;; ++i)
	    {
	      SP_DEVINFO_DATA dev_info_data;
	      ZeroMemory(&dev_info_data, sizeof(SP_DEVINFO_DATA));
	      dev_info_data.cbSize = sizeof(SP_DEVINFO_DATA);
	      BOOL res = SetupDiEnumDeviceInfo(device_info_set, i, &dev_info_data);
	      if (!res)
		{
		  if (GetLastError() == ERROR_NO_MORE_ITEMS)
		    break;
		  else
		    continue;
		}

	      Win::RegKey regkey;
	      *regkey.ref() = SetupDiOpenDevRegKey(device_info_set, &dev_info_data, DICS_FLAG_GLOBAL, 0, DIREG_DRV, KEY_QUERY_VALUE);
	      if (!regkey.defined())
		continue;

	      std::string str_net_cfg_instance_id;

	      DWORD size;
	      LONG status = RegQueryValueExA(regkey(), "NetCfgInstanceId", NULL, NULL, NULL, &size);
	      if (status != ERROR_SUCCESS)
		continue;
	      BufferAllocatedType<char, thread_unsafe_refcount> buf_net_cfg_inst_id(size, BufferAllocated::CONSTRUCT_ZERO);

	      status = RegQueryValueExA(regkey(), "NetCfgInstanceId", NULL, NULL, (LPBYTE)buf_net_cfg_inst_id.data(), &size);
	      if (status == ERROR_SUCCESS)
		{
		  buf_net_cfg_inst_id.data()[size - 1] = '\0';
		  str_net_cfg_instance_id = std::string(buf_net_cfg_inst_id.data());
		}
	      else
		continue;

	      res = SetupDiGetDeviceInstanceId(device_info_set, &dev_info_data, NULL, 0, &size);
	      if (res != FALSE && GetLastError() != ERROR_INSUFFICIENT_BUFFER)
		continue;

	      BufferAllocatedType<char, thread_unsafe_refcount> buf_dev_inst_id(size, BufferAllocated::CONSTRUCT_ZERO);
	      if (!SetupDiGetDeviceInstanceId(device_info_set, &dev_info_data, buf_dev_inst_id.data(), size, &size))
		continue;
	      buf_dev_inst_id.set_size(size);

	      ULONG dev_interface_list_size = 0;
	      CONFIGRET cr = CM_Get_Device_Interface_List_Size(&dev_interface_list_size,
							       (LPGUID)& GUID_DEVINTERFACE_NET,
							       buf_dev_inst_id.data(),
							       CM_GET_DEVICE_INTERFACE_LIST_PRESENT);

	      if (cr != CR_SUCCESS)
		continue;

	      BufferAllocatedType<char, thread_unsafe_refcount> buf_dev_iface_list(dev_interface_list_size, BufferAllocated::CONSTRUCT_ZERO);
	      cr = CM_Get_Device_Interface_List((LPGUID)& GUID_DEVINTERFACE_NET, buf_dev_inst_id.data(),
						buf_dev_iface_list.data(),
      						dev_interface_list_size,
						CM_GET_DEVICE_INTERFACE_LIST_PRESENT);
	      if (cr != CR_SUCCESS)
		continue;

	      DeviceInstanceIdInterfacePair pair;
	      pair.net_cfg_instance_id = str_net_cfg_instance_id;
	      pair.device_interface_list = std::string(buf_dev_iface_list.data());
	      push_back(pair);
	    }
	}
      };

      // given a TAP GUID, form the pathname of the TAP device node
      inline std::string tap_path(const TapNameGuidPair& tap)
      {
	  return std::string(USERMODEDEVICEDIR) + tap.guid + std::string(TAP_WIN_SUFFIX);
      }

      // open an available TAP adapter
      inline HANDLE tap_open(const Type tun_type,
			     const TapNameGuidPairList& guids,
			     std::string& path_opened,
			     TapNameGuidPair& used)
      {
	Win::ScopedHANDLE hand;

	std::unique_ptr<DeviceInstanceIdInterfaceList> inst_id_interface_list;
	if (tun_type != TapWindows6)
	  inst_id_interface_list.reset(new DeviceInstanceIdInterfaceList());

	// iterate over list of TAP adapters on system
	for (TapNameGuidPairList::const_iterator i = guids.begin(); i != guids.end(); i++)
	  {
	    const TapNameGuidPair& tap = *i;

	    std::string path;

	    if (tun_type != TapWindows6)
	      {
		for (const auto& inst_id_interface : *inst_id_interface_list)
		  {
		    if (inst_id_interface.net_cfg_instance_id != tap.guid)
		      continue;

		    path = inst_id_interface.device_interface_list;
		    break;
		  }
	      }
	    else
	      {
		path = tap_path(tap);
	      }

	    if (path.length() > 0)
	      {
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
      inline void tap_configure_topology_net30(HANDLE th, const IP::Addr& local_addr, const IP::Addr& remote_addr)
      {
	const IPv4::Addr local = local_addr.to_ipv4();
	const IPv4::Addr remote = remote_addr.to_ipv4();

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
	      for (LONG i = 0; i < list->NumAdapters; ++i)
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
	const DWORD status = ::FlushIpNetTable2(AF_INET, adapter_index);
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

      class BestGateway
      {
      public:
	/**
	 * Construct object which represents default gateway
	 */
	BestGateway()
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

	/**
	 * Construct object which represents best gateway to given
	 * destination, excluding gateway on VPN interface. Gateway is chosen
	 * first by the longest prefix match and then by metric. If destination
	 * is in local network, no gateway is selected and "local_route" flag is set.
	 *
	 * @param dest destination IPv4 address
	 * @param vpn_interface_index index of VPN interface which is excluded from gateway selection
	 */
	BestGateway(const std::string& dest, DWORD vpn_interface_index)
	{
	  DWORD dest_addr;
	  auto res = inet_pton(AF_INET, dest.c_str(), &dest_addr);
	  switch (res)
	    {
	    case -1:
	      OPENVPN_THROW(tun_win_util, "GetBestGateway: error converting IPv4 address " << dest << " to int: " << ::WSAGetLastError());

	    case 0:
	      OPENVPN_THROW(tun_win_util, "GetBestGateway: " << dest << " is not a valid IPv4 address");
	    }

	  {
	    MIB_IPFORWARDROW row;
	    DWORD res2 = GetBestRoute(dest_addr, 0, &row);
	    if (res2 != NO_ERROR)
	      {
		OPENVPN_THROW(tun_win_util, "GetBestGateway: error retrieving the best route for " << dest << ": " << res2);
	      }

	    if (row.dwForwardType == MIB_IPROUTE_TYPE_DIRECT)
	      {
		local_route_ = true;
		return;
	      }
	  }

	  std::unique_ptr<const MIB_IPFORWARDTABLE> rt(windows_routing_table());
	  if (rt)
	    {
	      const MIB_IPFORWARDROW* gw = nullptr;
	      for (size_t i = 0; i < rt->dwNumEntries; ++i)
		{
		  const MIB_IPFORWARDROW* row = &rt->table[i];
		  // does route match?
		  if ((dest_addr & row->dwForwardMask) == (row->dwForwardDest & row->dwForwardMask))
		    {
		      // skip gateway on VPN interface
		      if ((vpn_interface_index != DWORD(-1)) && (row->dwForwardIfIndex == vpn_interface_index))
			{
			  OPENVPN_LOG("GetBestGateway: skip gateway " <<
				      IPv4::Addr::from_uint32(ntohl(row->dwForwardNextHop)).to_string() <<
				      " on VPN interface " << vpn_interface_index);
			  continue;
			}

		      if (!gw)
			{
			  gw = row;
			  continue;
			}

		      auto cur_prefix = IPv4::Addr::prefix_len_32(ntohl(gw->dwForwardMask));
		      auto new_prefix = IPv4::Addr::prefix_len_32(ntohl(row->dwForwardMask));
		      auto new_metric_is_lower = row->dwForwardMetric1 < gw->dwForwardMetric1;

		      /* use new gateway if it has longer prefix OR same prefix but lower metric */
		      if ((new_prefix > cur_prefix) || ((new_prefix == cur_prefix) && new_metric_is_lower))
			gw = row;
		    }
		}
	      if (gw)
		{
		  index = gw->dwForwardIfIndex;
		  addr = IPv4::Addr::from_uint32(ntohl(gw->dwForwardNextHop)).to_string();
		  OPENVPN_LOG("GetBestGateway: selected gateway " << addr << " on adapter " << index << " for destination " << dest);
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

	/**
	 * Return true if destination, provided to constructor,
	 * doesn't require gateway, false otherwise.
	 */
	bool local_route() const
	{
	  return local_route_;
	}

      private:
	DWORD index = -1;
	std::string addr;
	bool local_route_ = false;
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

      namespace TunNETSH
      {
	class AddRoute4Cmd : public Action
	{
	public:
	  typedef RCPtr<AddRoute4Cmd> Ptr;

	  AddRoute4Cmd(const std::string& route_address,
		       int prefix_length,
		       const TunWin::Util::TapNameGuidPair& tap,
		       const std::string& gw_address,
		       int metric,
		       bool add)
	  {
	    std::ostringstream os;
	    os << "netsh interface ip ";
	    if (add)
	      os << "add ";
	    else
	      os << "delete ";
	    os << "route " << route_address << "/" << std::to_string(prefix_length) << " " << tap.index_or_name() << " " << gw_address << " ";
	    if (add && metric >= 0)
	      os << "metric=" << std::to_string(metric) << " ";
	    os << "store=active";
	    cmd.reset(new WinCmd(os.str()));
	  };

	  void execute(std::ostream& os) override
	  {
	    cmd->execute(os);
	  }

	  std::string to_string() const override
	  {
	    return cmd->to_string();
	  }

	private:
	  WinCmd::Ptr cmd;
	};
      }

      namespace TunIPHELPER
      {
	static SOCKADDR_INET sockaddr_inet(short family, const std::string& addr)
	{
	  SOCKADDR_INET sa;
	  ZeroMemory(&sa, sizeof(sa));
	  sa.si_family = family;
	  inet_pton(family, addr.c_str(), family == AF_INET ? &(sa.Ipv4.sin_addr) : (PVOID) & (sa.Ipv6.sin6_addr));
	  return sa;
	}

	static DWORD InterfaceLuid(const std::string& iface_name, PNET_LUID luid)
	{
	  auto wide_name = wstring::from_utf8(iface_name);
	  return ConvertInterfaceAliasToLuid(wide_name.c_str(), luid);
	}

	class AddRoute4Cmd : public Action
	{
	public:
	  typedef RCPtr<AddRoute4Cmd> Ptr;

	  AddRoute4Cmd(const std::string& route_address,
		       int prefix_length,
		       const TunWin::Util::TapNameGuidPair& tap,
		       const std::string& gw_address,
		       int metric,
		       bool add) : add(add)
	  {
	    os_ << "IPHelper: ";
	    if (add)
	      os_ << "add ";
	    else
	      os_ << "delete ";
	    os_ << "route " << route_address << "/" << std::to_string(prefix_length) << " " << tap.index_or_name() << " " << gw_address << " ";
	    os_ << "metric=" << std::to_string(metric);

	    ZeroMemory(&fwd_row, sizeof(fwd_row));
	    fwd_row.ValidLifetime = 0xffffffff;
	    fwd_row.PreferredLifetime = 0xffffffff;
	    fwd_row.Protocol = (NL_ROUTE_PROTOCOL)MIB_IPPROTO_NETMGMT;
	    fwd_row.Metric = metric;
	    fwd_row.DestinationPrefix.Prefix = sockaddr_inet(AF_INET, route_address);
	    fwd_row.DestinationPrefix.PrefixLength = prefix_length;
	    fwd_row.NextHop = sockaddr_inet(AF_INET, gw_address);

	    if (tap.index_defined())
	      fwd_row.InterfaceIndex = tap.index;
	    else if (!tap.name.empty())
	      {
		NET_LUID luid;
		auto err = InterfaceLuid(tap.name, &luid);
		if (err)
		  OPENVPN_THROW(tun_win_util, "Cannot convert interface name " << tap.name << " to LUID");
		fwd_row.InterfaceLuid = luid;
	      }
	  };

	  void execute(std::ostream& os) override
	  {
	    os << os_.str() << std::endl;
	    DWORD res;
	    if (add)
	      res = CreateIpForwardEntry2(&fwd_row);
	    else
	      res = DeleteIpForwardEntry2(&fwd_row);
	    if (res)
	      os << "cannot modify route: error " << res << std::endl;
	  }

	  std::string to_string() const override
	  {
	    return os_.str();
	  }

	private:
	  MIB_IPFORWARD_ROW2 fwd_row;
	  bool add;
	  std::ostringstream os_;
	};
      }
    }
  }
}

#endif
