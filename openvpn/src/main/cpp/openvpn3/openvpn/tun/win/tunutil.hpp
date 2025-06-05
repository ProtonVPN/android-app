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

// tun interface utilities for Windows

#pragma once

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
#include <new>

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
#include <openvpn/addr/ipv6.hpp>
#include <openvpn/tun/builder/capture.hpp>
#include <openvpn/win/reg.hpp>
#include <openvpn/win/scoped_handle.hpp>
#include <openvpn/win/unicode.hpp>
#include <openvpn/win/cmd.hpp>
#include <openvpn/win/winerr.hpp>

namespace openvpn::TunWin {
enum Type
{
    TapWindows6,
    Wintun,
    OvpnDco
};

namespace Util {
OPENVPN_EXCEPTION(tun_win_util);

namespace {
// from tap-windows.h
const char ADAPTER[] = ADAPTER_KEY;                         // CONST GLOBAL
const char NETWORK_CONNECTIONS[] = NETWORK_CONNECTIONS_KEY; // CONST GLOBAL

// generally defined on cl command line
const char COMPONENT_ID[] = OPENVPN_STRINGIZE(TAP_WIN_COMPONENT_ID); // CONST GLOBAL
const char WINTUN_COMPONENT_ID[] = "wintun";                         // CONST GLOBAL
const char OVPNDCO_COMPONENT_ID[] = "ovpn-dco";                      // CONST GLOBAL

const char ROOT_COMPONENT_ID[] = "root\\" OPENVPN_STRINGIZE(TAP_WIN_COMPONENT_ID);
const char ROOT_WINTUN_COMPONENT_ID[] = "root\\wintun";
const char ROOT_OVPNDCO_COMPONENT_ID[] = "root\\ovpn-dco";

const char OVPNDCO_DEV_INTERFACE_REF_STRING[] = "\\ovpn-dco";
} // namespace

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

    switch (tun_type)
    {
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

    Win::Reg::Key adapter_key;
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
        Win::Reg::Key unit_key;

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
        if (string::strcasecmp(strbuf, component_id)
            && string::strcasecmp(strbuf, root_component_id))
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
    TapNameGuidPair()
        : index(DWORD(-1))
    {
    }

    bool index_defined() const
    {
        return index != DWORD(-1);
    }

    std::string index_or_name() const
    {
        if (index_defined())
            return to_string(index);
        else if (!name.empty())
            return '"' + name + '"';
        else
            OPENVPN_THROW(tun_win_util, "TapNameGuidPair: TAP interface " << guid << " has no name or interface index");
    }

    void reset()
    {
        name.clear();
        guid.clear();
        net_luid_index = DWORD(-1);
        index = DWORD(-1);
    }

    std::string name;
    std::string guid;
    DWORD net_luid_index = DWORD(-1);
    DWORD index = DWORD(-1);
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
                    wbuf[len - 1] = 0;
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

            Win::Reg::Key network_connections_key;
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
                Win::Reg::Key connection_key;

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
                        TapNameGuidPair &pair = *j;
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
            const TapNameGuidPair &pair = *i;
            os << "guid='" << pair.guid << '\'';
            if (pair.index_defined())
                os << " index=" << pair.index;
            if (!pair.name.empty())
                os << " name='" << pair.name << '\'';
            os << std::endl;
        }
        return os.str();
    }

    std::string name_from_guid(const std::string &guid) const
    {
        for (const_iterator i = begin(); i != end(); i++)
        {
            const TapNameGuidPair &pair = *i;
            if (pair.guid == guid)
                return pair.name;
        }
        throw std::range_error{"guid not found"};
    }

    std::string guid_from_name(const std::string &name) const
    {
        for (const_iterator i = begin(); i != end(); i++)
        {
            const TapNameGuidPair &pair = *i;
            if (pair.name == name)
                return pair.guid;
        }
        throw std::range_error{"name not found"};
    }
};

struct DeviceInstanceIdInterfacePair
{
    std::string net_cfg_instance_id;
    std::string device_interface;
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

            Win::Reg::Key regkey;
            *regkey.ref() = SetupDiOpenDevRegKey(device_info_set, &dev_info_data, DICS_FLAG_GLOBAL, 0, DIREG_DRV, KEY_QUERY_VALUE);
            if (!regkey.defined())
                continue;

            std::string str_net_cfg_instance_id;

            DWORD size;
            LONG status = RegQueryValueExA(regkey(), "NetCfgInstanceId", NULL, NULL, NULL, &size);
            if (status != ERROR_SUCCESS)
                continue;
            BufferAllocatedType<char> buf_net_cfg_inst_id(size, BufAllocFlags::CONSTRUCT_ZERO);

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

            BufferAllocatedType<char> buf_dev_inst_id(size, BufAllocFlags::CONSTRUCT_ZERO);
            if (!SetupDiGetDeviceInstanceId(device_info_set, &dev_info_data, buf_dev_inst_id.data(), size, &size))
                continue;
            buf_dev_inst_id.set_size(size);

            ULONG dev_interface_list_size = 0;
            CONFIGRET cr = CM_Get_Device_Interface_List_Size(&dev_interface_list_size,
                                                             (LPGUID)&GUID_DEVINTERFACE_NET,
                                                             buf_dev_inst_id.data(),
                                                             CM_GET_DEVICE_INTERFACE_LIST_PRESENT);

            if (cr != CR_SUCCESS)
                continue;

            BufferAllocatedType<char> buf_dev_iface_list(dev_interface_list_size, BufAllocFlags::CONSTRUCT_ZERO);
            cr = CM_Get_Device_Interface_List((LPGUID)&GUID_DEVINTERFACE_NET,
                                              buf_dev_inst_id.data(),
                                              buf_dev_iface_list.data(),
                                              dev_interface_list_size,
                                              CM_GET_DEVICE_INTERFACE_LIST_PRESENT);
            if (cr != CR_SUCCESS)
                continue;

            char *dev_if = buf_dev_iface_list.data();
            while (strlen(dev_if) > 0)
            {
                DeviceInstanceIdInterfacePair pair;
                pair.net_cfg_instance_id = str_net_cfg_instance_id;
                pair.device_interface = std::string(dev_if);
                push_back(pair);

                dev_if += strlen(dev_if) + 1;
            }
        }
    }
};

// given a TAP GUID, form the pathname of the TAP device node
inline std::string tap_path(const TapNameGuidPair &tap)
{
    return std::string(USERMODEDEVICEDIR) + tap.guid + std::string(TAP_WIN_SUFFIX);
}

// open an available TAP adapter
inline HANDLE tap_open(const Type tun_type,
                       const TapNameGuidPairList &guids,
                       std::string &path_opened,
                       TapNameGuidPair &used)
{
    Win::ScopedHANDLE hand;

    std::unique_ptr<DeviceInstanceIdInterfaceList> inst_id_interface_list;
    if (tun_type != TapWindows6)
        inst_id_interface_list.reset(new DeviceInstanceIdInterfaceList());

    // iterate over list of TAP adapters on system
    for (TapNameGuidPairList::const_iterator i = guids.begin(); i != guids.end(); i++)
    {
        const TapNameGuidPair &tap = *i;

        std::string path;

        if (tun_type != TapWindows6)
        {
            for (const auto &inst_id_interface : *inst_id_interface_list)
            {
                if (inst_id_interface.net_cfg_instance_id != tap.guid)
                    continue;

                if (tun_type == OvpnDco)
                {
                    if (!string::ends_with(inst_id_interface.device_interface, OVPNDCO_DEV_INTERFACE_REF_STRING))
                        continue;
                }
                path = inst_id_interface.device_interface;
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
inline void tap_configure_topology_subnet(HANDLE th, const IP::Addr &local, const unsigned int prefix_len)
{
    const IPv4::Addr netmask = IPv4::Addr::netmask_from_prefix_len(prefix_len);
    const IPv4::Addr network = local.to_ipv4() & netmask;

    std::uint32_t ep[3];
    ep[0] = htonl(local.to_ipv4().to_uint32());
    ep[1] = htonl(network.to_uint32());
    ep[2] = htonl(netmask.to_uint32());

    DWORD len;
    if (!::DeviceIoControl(th,
                           TAP_WIN_IOCTL_CONFIG_TUN,
                           ep,
                           sizeof(ep),
                           ep,
                           sizeof(ep),
                           &len,
                           nullptr))
    {
        throw tun_win_util("DeviceIoControl TAP_WIN_IOCTL_CONFIG_TUN failed");
    }
}

// set TAP adapter to topology net30
inline void tap_configure_topology_net30(HANDLE th, const IP::Addr &local_addr, const IP::Addr &remote_addr)
{
    const IPv4::Addr local = local_addr.to_ipv4();
    const IPv4::Addr remote = remote_addr.to_ipv4();

    std::uint32_t ep[2];
    ep[0] = htonl(local.to_uint32());
    ep[1] = htonl(remote.to_uint32());

    DWORD len;
    if (!::DeviceIoControl(th,
                           TAP_WIN_IOCTL_CONFIG_POINT_TO_POINT,
                           ep,
                           sizeof(ep),
                           ep,
                           sizeof(ep),
                           &len,
                           nullptr))
    {
        throw tun_win_util("DeviceIoControl TAP_WIN_IOCTL_CONFIG_POINT_TO_POINT failed");
    }
}

// set driver media status to 'connected'
inline void tap_set_media_status(HANDLE th, bool media_status)
{
    DWORD len;
    ULONG status = media_status ? TRUE : FALSE;
    if (!::DeviceIoControl(th,
                           TAP_WIN_IOCTL_SET_MEDIA_STATUS,
                           &status,
                           sizeof(status),
                           &status,
                           sizeof(status),
                           &len,
                           nullptr))
    {
        throw tun_win_util("DeviceIoControl TAP_WIN_IOCTL_SET_MEDIA_STATUS failed");
    }
}

// get debug logging from TAP driver (requires that
// TAP driver was built with logging enabled)
inline void tap_process_logging(HANDLE th)
{
    const size_t size = 1024;
    std::unique_ptr<char[]> line(new char[size]);
    DWORD len;

    while (::DeviceIoControl(th,
                             TAP_WIN_IOCTL_GET_LOG_LINE,
                             line.get(),
                             size,
                             line.get(),
                             size,
                             &len,
                             nullptr))
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
        list.reset((IP_INTERFACE_INFO *)::operator new(size));
        if (::GetInterfaceInfo(list.get(), &size) != NO_ERROR)
            OPENVPN_THROW(tun_win_util, "InterfaceInfoList: GetInterfaceInfo #2");
    }

    IP_ADAPTER_INDEX_MAP *iface(const DWORD index) const
    {
        if (list)
        {
            for (LONG i = 0; i < list->NumAdapters; ++i)
            {
                IP_ADAPTER_INDEX_MAP *inter = &list->Adapter[i];
                if (index == inter->Index)
                    return inter;
            }
        }
        return nullptr;
    }

    unique_ptr_slab<IP_INTERFACE_INFO> list;
};

inline void dhcp_release(const InterfaceInfoList &ii,
                         const DWORD adapter_index,
                         std::ostream &os)
{
    IP_ADAPTER_INDEX_MAP *iface = ii.iface(adapter_index);
    if (iface)
    {
        const DWORD status = ::IpReleaseAddress(iface);
        if (status == NO_ERROR)
            os << "TAP: DHCP release succeeded" << std::endl;
        else
            os << "TAP: DHCP release failed" << std::endl;
    }
}

inline void dhcp_renew(const InterfaceInfoList &ii,
                       const DWORD adapter_index,
                       std::ostream &os)
{
    IP_ADAPTER_INDEX_MAP *iface = ii.iface(adapter_index);
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
                      std::ostream &os)
{
    const DWORD status = ::FlushIpNetTable2(AF_INET, adapter_index);
    if (status == NO_ERROR)
        os << "TAP: ARP flush succeeded" << std::endl;
    else
        os << "TAP: ARP flush failed" << std::endl;
}

struct IPNetmask4
{
    IPNetmask4()
    {
    }

    IPNetmask4(const TunBuilderCapture &pull, const char *title)
    {
        const TunBuilderCapture::RouteAddress *local4 = pull.vpn_ipv4();
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
            try
            {
                ip = IPv4::Addr::from_string(ias->IpAddress.String);
            }
            catch (const std::exception &)
            {
            }
            try
            {
                netmask = IPv4::Addr::from_string(ias->IpMask.String);
            }
            catch (const std::exception &)
            {
            }
        }
    }

    bool operator==(const IPNetmask4 &rhs) const
    {
        return ip == rhs.ip && netmask == rhs.netmask;
    }

    bool operator!=(const IPNetmask4 &rhs) const
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
        list.reset((IP_ADAPTER_INFO *)::operator new(size));
        if (::GetAdaptersInfo(list.get(), &size) != NO_ERROR)
            OPENVPN_THROW(tun_win_util, "IPAdaptersInfo: GetAdaptersInfo #2");
    }

    const IP_ADAPTER_INFO *adapter(const DWORD index) const
    {
        if (list)
        {
            for (const IP_ADAPTER_INFO *a = list.get(); a != nullptr; a = a->Next)
            {
                if (index == a->Index)
                    return a;
            }
        }
        return nullptr;
    }

    bool is_up(const DWORD index, const IPNetmask4 &vpn_addr) const
    {
        const IP_ADAPTER_INFO *ai = adapter(index);
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
        const IP_ADAPTER_INFO *ai = adapter(index);
        return ai && ai->DhcpEnabled;
    }

    unique_ptr_slab<IP_ADAPTER_INFO> list;
};

struct IPPerAdapterInfo
{
    IPPerAdapterInfo(const DWORD index)
    {
        ULONG size = 0;
        if (::GetPerAdapterInfo(index, nullptr, &size) != ERROR_BUFFER_OVERFLOW)
            return;
        adapt.reset((IP_PER_ADAPTER_INFO *)::operator new(size));
        if (::GetPerAdapterInfo(index, adapt.get(), &size) != ERROR_SUCCESS)
            adapt.reset();
    }

    unique_ptr_slab<IP_PER_ADAPTER_INFO> adapt;
};

class TAPDriverVersion
{
  public:
    TAPDriverVersion(HANDLE th)
        : defined(false)
    {
        DWORD len;
        info[0] = info[1] = info[2] = 0;
        if (::DeviceIoControl(th,
                              TAP_WIN_IOCTL_GET_VERSION,
                              &info,
                              sizeof(info),
                              &info,
                              sizeof(info),
                              &len,
                              nullptr))
        {
            defined = true;
        }
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
    ActionSetAdapterDomainSuffix(const std::string &search_domain_arg,
                                 const std::string &tap_guid_arg)
        : search_domain(search_domain_arg),
          tap_guid(tap_guid_arg)
    {
    }

    virtual void execute(std::ostream &os) override
    {
        os << to_string() << std::endl;

        LONG status;
        Win::Reg::Key key;
        const std::string reg_key_name = "SYSTEM\\CurrentControlSet\\services\\Tcpip\\Parameters\\Interfaces\\" + tap_guid;
        status = ::RegOpenKeyExA(HKEY_LOCAL_MACHINE,
                                 reg_key_name.c_str(),
                                 0,
                                 KEY_READ | KEY_WRITE,
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
                                  reinterpret_cast<const BYTE *>(dom.get()),
                                  static_cast<DWORD>((Win::utf16_strlen(dom.get()) + 1) * sizeof(wchar_t)));
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
inline const MIB_IPFORWARDTABLE *windows_routing_table()
{
    ULONG size = 0;
    DWORD status;
    unique_ptr_slab<MIB_IPFORWARDTABLE> rt;

    status = ::GetIpForwardTable(nullptr, &size, TRUE);
    if (status == ERROR_INSUFFICIENT_BUFFER)
    {
        rt.reset((MIB_IPFORWARDTABLE *)::operator new(size));
        status = ::GetIpForwardTable(rt.get(), &size, TRUE);
        if (status != NO_ERROR)
        {
            OPENVPN_LOG("windows_routing_table: GetIpForwardTable failed");
            return nullptr;
        }
    }
    return rt.release();
}

// Get the Windows IPv4/IPv6 routing table.
// Note that returned pointer must be freed with FreeMibTable.
inline const MIB_IPFORWARD_TABLE2 *windows_routing_table2(ADDRESS_FAMILY af)
{
    MIB_IPFORWARD_TABLE2 *routes = nullptr;
    int res = ::GetIpForwardTable2(af, &routes);
    if (res == NO_ERROR)
        return routes;
    else
        return nullptr;
}

class BestGateway
{
  public:
    /**
     * Construct object which represents default gateway
     */
    BestGateway(ADDRESS_FAMILY af)
    {
        unique_ptr_del<const MIB_IPFORWARD_TABLE2> rt2(windows_routing_table2(af),
                                                       [](const MIB_IPFORWARD_TABLE2 *p)
                                                       { FreeMibTable((PVOID)p); });

        if (!rt2)
        {
            OPENVPN_THROW(tun_win_util, "Failed to get routing table");
        }

        std::map<NET_IFINDEX, ULONG> metric_per_iface;
        ULONG gw_metric = 0;

        const MIB_IPFORWARD_ROW2 *gw = nullptr;
        for (size_t i = 0; i < rt2->NumEntries; ++i)
        {
            const MIB_IPFORWARD_ROW2 *row = &rt2->Table[i];

            IP::Addr dst = IP::Addr::from_sockaddr((const sockaddr *)&row->DestinationPrefix.Prefix);
            bool default_gw = dst.all_zeros() && row->DestinationPrefix.PrefixLength == 0;

            ULONG metric = row->Metric + get_iface_metric(metric_per_iface, row->InterfaceIndex, af);

            if (default_gw && (!gw || metric < gw_metric))
            {
                gw = row;
                gw_metric = metric;
            }
        }
        if (gw)
        {
            index = gw->InterfaceIndex;
            if (af == AF_INET6)
            {
                addr = IPv6::Addr::from_in6_addr(&gw->NextHop.Ipv6.sin6_addr).to_string();
            }
            else
            {
                addr = IPv4::Addr::from_in_addr(&gw->NextHop.Ipv4.sin_addr).to_string();
            }
        }
    }

    /**
     * Construct object which represents best gateway to given
     * destination, excluding gateway on VPN interface. Gateway is chosen
     * first by the longest prefix match and then by metric. If destination
     * is in local network, no gateway is selected and "local_route" flag is set.
     *
     * @param af address family, AF_INET or AF_INET6
     * @param dest_str destination IPv4/IPv6 address
     * @param vpn_interface_index index of VPN interface which is excluded from gateway selection
     */
    BestGateway(ADDRESS_FAMILY af, const std::string &dest_str, DWORD vpn_interface_index)
    {
        unique_ptr_del<const MIB_IPFORWARD_TABLE2> rt2(windows_routing_table2(af),
                                                       [](const MIB_IPFORWARD_TABLE2 *p)
                                                       { FreeMibTable((PVOID)p); });

        if (!rt2)
        {
            OPENVPN_THROW(tun_win_util, "Failed to get routing table");
        }

        IP::Addr dest = IP::Addr::from_string(dest_str);

        void *dst_addr = NULL;
        struct sockaddr_in sa4;
        struct sockaddr_in6 sa6;
        if (af == AF_INET6)
        {
            sa6 = dest.to_ipv6().to_sockaddr();
            dst_addr = &sa6;
        }
        else
        {
            sa4 = dest.to_ipv4().to_sockaddr();
            dst_addr = &sa4;
        }

        NET_IFINDEX best_interface = 0;
        DWORD res = ::GetBestInterfaceEx((sockaddr *)dst_addr, &best_interface);
        if (res != NO_ERROR)
        {
            OPENVPN_THROW(tun_win_util,
                          "GetBestInterfaceEx: error retrieving the best interface for " << dest
                                                                                         << ": " << res);
        }

        // check if route is local
        MIB_IPFORWARD_ROW2 row{};
        SOCKADDR_INET best_source{};
        res = ::GetBestRoute2(NULL, best_interface, NULL, (const SOCKADDR_INET *)dst_addr, 0, &row, &best_source);
        if (res != NO_ERROR)
        {
            OPENVPN_THROW(tun_win_util,
                          "GetBestGateway: error retrieving the best route for " << dest
                                                                                 << ": " << res);
        }

        // no gw needed, route is local
        if (row.Protocol == RouteProtocolLocal)
        {
            local_route_ = true;
            return;
        }

        // if there is no VPN interface - we're done
        if (vpn_interface_index == DWORD(-1))
        {
            fill_gw_details(&row, dest_str);
            return;
        }

        // find the best route excluding VPN interface
        const MIB_IPFORWARD_ROW2 *gw = nullptr;
        std::map<NET_IFINDEX, ULONG> metric_per_iface;
        ULONG gw_metric = 0;
        for (size_t i = 0; i < rt2->NumEntries; ++i)
        {
            const MIB_IPFORWARD_ROW2 *row = &rt2->Table[i];
            IP::Addr mask = IP::Addr::netmask_from_prefix_len(af == AF_INET6 ? IP::Addr::Version::V6 : IP::Addr::Version::V4, row->DestinationPrefix.PrefixLength);

            IP::Addr dest_prefix = IP::Addr::from_sockaddr((const sockaddr *)&row->DestinationPrefix.Prefix);

            if ((dest & mask) == dest_prefix)
            {
                // skip gateway on VPN interface
                if ((vpn_interface_index != DWORD(-1)) && (row->InterfaceIndex == vpn_interface_index))
                {
                    OPENVPN_LOG("GetBestGateway: skip gateway "
                                << IP::Addr::from_sockaddr((const sockaddr *)&row->NextHop).to_string()
                                << " on VPN interface " << vpn_interface_index);
                    continue;
                }

                if (!gw)
                {
                    gw = row;
                    continue;
                }

                ULONG metric = row->Metric + get_iface_metric(metric_per_iface, row->InterfaceIndex, af);

                // use new gateway if it has longer prefix OR the same prefix but lower metric
                if ((row->DestinationPrefix.PrefixLength > gw->DestinationPrefix.PrefixLength) || ((row->DestinationPrefix.PrefixLength == gw->DestinationPrefix.PrefixLength) && (metric < gw_metric)))
                {
                    gw = row;
                    gw_metric = metric;
                }
            }
        }

        if (gw)
        {
            fill_gw_details(gw, dest_str);
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

    const std::string &gateway_address() const
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
    void fill_gw_details(const MIB_IPFORWARD_ROW2 *row, const std::string &dest)
    {
        index = row->InterfaceIndex;
        addr = IP::Addr::from_sockaddr((const sockaddr *)&row->NextHop).to_string();
        OPENVPN_LOG("GetBestGateway: "
                    << "selected gateway " << addr
                    << " on adapter " << index
                    << " for destination " << dest);
    }

    static ULONG get_iface_metric(std::map<NET_IFINDEX, ULONG> &metric_per_iface, NET_IFINDEX iface, ADDRESS_FAMILY af)
    {
        if (!metric_per_iface.contains(iface))
        {
            MIB_IPINTERFACE_ROW ir{};
            ir.InterfaceIndex = iface;
            ir.Family = af;
            ::GetIpInterfaceEntry(&ir);
            metric_per_iface[iface] = ir.Metric;
        }
        return metric_per_iface[iface];
    }

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

    virtual void execute(std::ostream &os) override
    {
        os << to_string() << std::endl;

        ActionList::Ptr actions = new ActionList();
        remove_all_ipv4_routes_on_iface(iface_index, *actions);
        remove_all_ipv6_routes_on_iface(iface_index, *actions);
        actions->execute(os);
    }

    virtual std::string to_string() const override
    {
        return "ActionDeleteAllRoutesOnInterface iface_index=" + std::to_string(iface_index);
    }

  private:
    static void remove_all_ipv4_routes_on_iface(DWORD index, ActionList &actions)
    {
        std::unique_ptr<const MIB_IPFORWARDTABLE> rt(windows_routing_table());
        if (rt)
        {
            for (size_t i = 0; i < rt->dwNumEntries; ++i)
            {
                const MIB_IPFORWARDROW *row = &rt->table[i];
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

    static void remove_all_ipv6_routes_on_iface(DWORD index, ActionList &actions)
    {
        unique_ptr_del<const MIB_IPFORWARD_TABLE2> rt2(windows_routing_table2(AF_INET6),
                                                       [](const MIB_IPFORWARD_TABLE2 *p)
                                                       { FreeMibTable((PVOID)p); });
        if (rt2)
        {
            const IPv6::Addr ll_net = IPv6::Addr::from_string("fe80::");
            const IPv6::Addr ll_mask = IPv6::Addr::netmask_from_prefix_len(64);
            for (size_t i = 0; i < rt2->NumEntries; ++i)
            {
                const MIB_IPFORWARD_ROW2 *row = &rt2->Table[i];
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

    const DWORD iface_index;
};

class ActionEnableDHCP : public WinCmd
{
  public:
    ActionEnableDHCP(const TapNameGuidPair &tap)
        : WinCmd(cmd(tap))
    {
    }

  private:
    static std::string cmd(const TapNameGuidPair &tap)
    {
        return "netsh interface ip set address " + tap.index_or_name() + " dhcp";
    }
};

namespace TunNETSH {
class AddRoute4Cmd : public Action
{
  public:
    typedef RCPtr<AddRoute4Cmd> Ptr;

    AddRoute4Cmd(const std::string &route_address,
                 int prefix_length,
                 DWORD iface_index,
                 const std::string &iface_name,
                 const std::string &gw_address,
                 int metric,
                 bool add)
    {
        std::ostringstream os;
        os << "netsh interface ip ";
        if (add)
            os << "add ";
        else
            os << "delete ";
        os << "route " << route_address << "/" << std::to_string(prefix_length) << " ";
        if (iface_index != DWORD(-1))
            os << iface_index;
        else
            os << iface_name;
        os << " " << gw_address << " ";
        if (add && metric >= 0)
            os << "metric=" << std::to_string(metric) << " ";
        os << "store=active";
        cmd.reset(new WinCmd(os.str()));
    };

    void execute(std::ostream &os) override
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
} // namespace TunNETSH

namespace TunIPHELPER {
static SOCKADDR_INET sockaddr_inet(short family, const std::string &addr)
{
    SOCKADDR_INET sa;
    ZeroMemory(&sa, sizeof(sa));
    sa.si_family = family;
    inet_pton(family, addr.c_str(), family == AF_INET ? &(sa.Ipv4.sin_addr) : (PVOID) & (sa.Ipv6.sin6_addr));
    return sa;
}

static DWORD InterfaceLuid(const std::string &iface_name, PNET_LUID luid)
{
    auto wide_name = wstring::from_utf8(iface_name);
    return ConvertInterfaceAliasToLuid(wide_name.c_str(), luid);
}

class AddRoute4Cmd : public Action
{
  public:
    typedef RCPtr<AddRoute4Cmd> Ptr;

    AddRoute4Cmd(const std::string &route_address,
                 int prefix_length,
                 DWORD iface_index,
                 const std::string &iface_name,
                 const std::string &gw_address,
                 int metric,
                 bool add)
        : add(add)
    {
        os_ << "IPHelper: ";
        if (add)
            os_ << "add ";
        else
            os_ << "delete ";
        os_ << "route " << route_address << "/" << std::to_string(prefix_length) << " ";
        if (iface_index != DWORD(-1))
            os_ << iface_index;
        else
            os_ << iface_name;
        os_ << " " << gw_address << " metric=" << std::to_string(metric);

        ZeroMemory(&fwd_row, sizeof(fwd_row));
        fwd_row.ValidLifetime = 0xffffffff;
        fwd_row.PreferredLifetime = 0xffffffff;
        fwd_row.Protocol = (NL_ROUTE_PROTOCOL)MIB_IPPROTO_NETMGMT;
        fwd_row.Metric = metric;
        fwd_row.DestinationPrefix.Prefix = sockaddr_inet(AF_INET, route_address);
        fwd_row.DestinationPrefix.PrefixLength = prefix_length;
        fwd_row.NextHop = sockaddr_inet(AF_INET, gw_address);

        if (iface_index != DWORD(-1))
            fwd_row.InterfaceIndex = iface_index;
        else if (!iface_name.empty())
        {
            NET_LUID luid;
            auto err = InterfaceLuid(iface_name, &luid);
            if (err)
                OPENVPN_THROW(tun_win_util, "Cannot convert interface name " << iface_name << " to LUID");
            fwd_row.InterfaceLuid = luid;
        }
    };

    void execute(std::ostream &os) override
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
} // namespace TunIPHELPER
} // namespace Util
} // namespace openvpn::TunWin
