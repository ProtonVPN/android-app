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

#ifndef OPENVPN_TUN_WIN_WFP_H
#define OPENVPN_TUN_WIN_WFP_H

#include <ostream>

#include <openvpn/common/rc.hpp>
#include <openvpn/common/wstring.hpp>
#include <openvpn/common/action.hpp>
#include <openvpn/buffer/bufstr.hpp>
#include <openvpn/tun/win/tunutil.hpp>
#include <openvpn/win/winerr.hpp>

#include <fwpmu.h>
#include <fwpmtypes.h>
#include <iphlpapi.h>

namespace openvpn {
  namespace TunWin {

    class WFP : public RC<thread_unsafe_refcount>
    {
    public:
      typedef RCPtr<WFP> Ptr;

      OPENVPN_EXCEPTION(wfp_error);

      // Block DNS from all apps except openvpn_app_path and
      // from all interfaces except tap_index.
      // Derived from https://github.com/ValdikSS/openvpn-with-patches/commit/3bd4d503d21aa34636e4f97b3e32ae0acca407f0
      void block_dns(const std::wstring& openvpn_app_path,
		     const NET_IFINDEX tap_index,
		     std::ostream& log)
      {
	// WFP filter/conditions
	FWPM_FILTER0 filter = {0};
	FWPM_FILTER_CONDITION0 condition[2] = {0};
	UINT64 filterid = 0;

	// Get NET_LUID object for adapter
	NET_LUID tap_luid = adapter_index_to_luid(tap_index);

	// Get app ID
	unique_ptr_del<FWP_BYTE_BLOB> openvpn_app_id_blob = get_app_id_blob(openvpn_app_path);

	// Populate packet filter layer information
	{
	  FWPM_SUBLAYER0 subLayer = {0};
	  subLayer.subLayerKey = subLayerGUID;
	  subLayer.displayData.name = L"OpenVPN";
	  subLayer.displayData.description = L"OpenVPN";
	  subLayer.flags = 0;
	  subLayer.weight = 0x100;

	  // Add packet filter to interface
	  const DWORD status = ::FwpmSubLayerAdd0(engineHandle(), &subLayer, NULL);
	  if (status != ERROR_SUCCESS)
	    OPENVPN_THROW(wfp_error, "FwpmSubLayerAdd0 failed with status=0x" << std::hex << status);
	}

	// Prepare filter
	filter.subLayerKey = subLayerGUID;
	filter.displayData.name = L"OpenVPN";
	filter.weight.type = FWP_UINT8;
	filter.weight.uint8 = 0xF;
	filter.filterCondition = condition;

	// Filter #1 -- permit IPv4 DNS requests from OpenVPN app
	filter.layerKey = FWPM_LAYER_ALE_AUTH_CONNECT_V4;
	filter.action.type = FWP_ACTION_PERMIT;
	filter.numFilterConditions = 2;

	condition[0].fieldKey = FWPM_CONDITION_IP_REMOTE_PORT;
	condition[0].matchType = FWP_MATCH_EQUAL;
	condition[0].conditionValue.type = FWP_UINT16;
	condition[0].conditionValue.uint16 = 53;

	condition[1].fieldKey = FWPM_CONDITION_ALE_APP_ID;
	condition[1].matchType = FWP_MATCH_EQUAL;
	condition[1].conditionValue.type = FWP_BYTE_BLOB_TYPE;
	condition[1].conditionValue.byteBlob = openvpn_app_id_blob.get();

	add_filter(&filter, NULL, &filterid);
	log << "permit IPv4 DNS requests from OpenVPN app" << std::endl;

	// Filter #2 -- permit IPv6 DNS requests from OpenVPN app
	filter.layerKey = FWPM_LAYER_ALE_AUTH_CONNECT_V6;

	add_filter(&filter, NULL, &filterid);
	log << "permit IPv6 DNS requests from OpenVPN app" << std::endl;

	// Filter #3 -- block IPv4 DNS requests from other apps
	filter.layerKey = FWPM_LAYER_ALE_AUTH_CONNECT_V4;
	filter.action.type = FWP_ACTION_BLOCK;
	filter.weight.type = FWP_EMPTY;
	filter.numFilterConditions = 1;

	add_filter(&filter, NULL, &filterid);
	log << "block IPv4 DNS requests from other apps" << std::endl;

	// Filter #4 -- block IPv6 DNS requests from other apps
	filter.layerKey = FWPM_LAYER_ALE_AUTH_CONNECT_V6;

	add_filter(&filter, NULL, &filterid);
	log << "block IPv6 DNS requests from other apps" << std::endl;

	// Filter #5 -- allow IPv4 traffic from TAP
	filter.layerKey = FWPM_LAYER_ALE_AUTH_CONNECT_V4;
	filter.action.type = FWP_ACTION_PERMIT;
	filter.numFilterConditions = 2;

	condition[1].fieldKey = FWPM_CONDITION_IP_LOCAL_INTERFACE;
	condition[1].matchType = FWP_MATCH_EQUAL;
	condition[1].conditionValue.type = FWP_UINT64;
	condition[1].conditionValue.uint64 = &tap_luid.Value;

	add_filter(&filter, NULL, &filterid);
	log << "allow IPv4 traffic from TAP" << std::endl;

	// Filter #6 -- allow IPv6 traffic from TAP
	filter.layerKey = FWPM_LAYER_ALE_AUTH_CONNECT_V6;

	add_filter(&filter, NULL, &filterid);
	log << "allow IPv6 traffic from TAP" << std::endl;
      }

      void reset(std::ostream& log)
      {
	engineHandle.reset(&log);
      }

    private:
      class WFPEngine
      {
      public:
	WFPEngine()
	{
	  FWPM_SESSION0 session = {0};

	  // delete all filters when engine handle is closed
	  session.flags = FWPM_SESSION_FLAG_DYNAMIC;

	  const DWORD status = ::FwpmEngineOpen0(NULL, RPC_C_AUTHN_WINNT, NULL, &session, &handle);
	  if (status != ERROR_SUCCESS)
	    OPENVPN_THROW(wfp_error, "FwpmEngineOpen0 failed with status=0x" << std::hex << status);
	}

	void reset(std::ostream* log)
	{
	  if (defined())
	    {
	      const DWORD status = ::FwpmEngineClose0(handle);
	      handle = NULL;
	      if (log)
		{
		  if (status != ERROR_SUCCESS)
		    *log << "FwpmEngineClose0 failed, status=" << status << std::endl;
		  else
		    *log << "WFPEngine closed" << std::endl;
		}
	    }
	}

	~WFPEngine()
	{
	  reset(nullptr);
	}

	bool defined() const
	{
	  return Win::Handle::defined(handle);
	}

	HANDLE operator()() const
	{
	  return handle;
	}

      private:
	WFPEngine(const WFPEngine&) = delete;
	WFPEngine& operator=(const WFPEngine&) = delete;

	HANDLE handle = NULL;
      };

      static GUID new_guid()
      {
	UUID ret;
	const RPC_STATUS status = ::UuidCreate(&ret);
	if (status != RPC_S_OK && status != RPC_S_UUID_LOCAL_ONLY)
	  throw wfp_error("UuidCreate failed");
	return ret;
      }

      static NET_LUID adapter_index_to_luid(const NET_IFINDEX index)
      {
	NET_LUID tap_luid;
	const NETIO_STATUS ret = ::ConvertInterfaceIndexToLuid(index, &tap_luid);
	if (ret != NO_ERROR)
	  throw wfp_error("ConvertInterfaceIndexToLuid failed");
	return tap_luid;
      }

      static unique_ptr_del<FWP_BYTE_BLOB> get_app_id_blob(const std::wstring& app_path)
      {
	FWP_BYTE_BLOB *blob;
	const DWORD status = ::FwpmGetAppIdFromFileName0(app_path.c_str(), &blob);
	if (status != ERROR_SUCCESS)
	  OPENVPN_THROW(wfp_error, "FwpmGetAppIdFromFileName0 failed, status=0x" << std::hex << status);
	return unique_ptr_del<FWP_BYTE_BLOB>(blob, [](FWP_BYTE_BLOB* blob) {
	    ::FwpmFreeMemory0((void**)&blob);
	  });
      }

      void add_filter(const FWPM_FILTER0 *filter,
		      PSECURITY_DESCRIPTOR sd,
		      UINT64 *id)
      {
	const DWORD status = ::FwpmFilterAdd0(engineHandle(), filter, sd, id);
	if (status != ERROR_SUCCESS)
	  OPENVPN_THROW(wfp_error, "FwpmFilterAdd0 failed, status=0x" << std::hex << status);
      }

      const GUID subLayerGUID{new_guid()};
      WFPEngine engineHandle;
    };

    class WFPContext : public RC<thread_unsafe_refcount>
    {
    public:
      typedef RCPtr<WFPContext> Ptr;

    private:
      friend class ActionWFP;

      void block(const std::wstring& openvpn_app_path,
		 const NET_IFINDEX tap_index,
		 std::ostream& log)
      {
	unblock(log);
	wfp.reset(new WFP());
	wfp->block_dns(openvpn_app_path, tap_index, log);
      }

      void unblock(std::ostream& log)
      {
	if (wfp)
	  {
	    wfp->reset(log);
	    wfp.reset();
	  }
      }

      WFP::Ptr wfp;
    };

    class ActionWFP : public Action
    {
    public:
      ActionWFP(const std::wstring& openvpn_app_path_arg,
		const NET_IFINDEX tap_index_arg,
		const bool enable_arg,
		const WFPContext::Ptr& wfp_arg)
	: openvpn_app_path(openvpn_app_path_arg),
	  tap_index(tap_index_arg),
	  enable(enable_arg),
	  wfp(wfp_arg)
      {
      }

      virtual void execute(std::ostream& log) override
      {
	log << to_string() << std::endl;
	if (enable)
	  wfp->block(openvpn_app_path, tap_index, log);
	else
	  wfp->unblock(log);
      }

      virtual std::string to_string() const override
      {
	return "ActionWFP openvpn_app_path=" + wstring::to_utf8(openvpn_app_path) + " tap_index=" + std::to_string(tap_index) + " enable=" + std::to_string(enable);
      }

    private:
      const std::wstring openvpn_app_path;
      const NET_IFINDEX tap_index;
      const bool enable;
      WFPContext::Ptr wfp;
    };
  }
}

#endif
