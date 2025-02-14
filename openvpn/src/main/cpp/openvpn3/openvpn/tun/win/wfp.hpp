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
//    SPDX-License-Identifier: MPL-2.0 OR AGPL-3.0-only WITH openvpn3-openssl-exception
//

#pragma once

#include <ostream>

#include <openvpn/common/rc.hpp>
#include <openvpn/common/wstring.hpp>
#include <openvpn/common/action.hpp>
#include <openvpn/common/uniqueptr.hpp>
#include <openvpn/buffer/bufstr.hpp>
#include <openvpn/tun/win/tunutil.hpp>
#include <openvpn/win/winerr.hpp>

#include <fwpmu.h>
#include <fwpmtypes.h>
#include <iphlpapi.h>
#include <wchar.h>

#ifdef __MINGW32__

#include <initguid.h>

// WFP-related defines and GUIDs not in mingw32

#ifndef FWPM_SESSION_FLAG_DYNAMIC
#define FWPM_SESSION_FLAG_DYNAMIC 0x00000001
#endif

// defines below are taken from openvpn2 code
// which likely borrowed them from Windows SDK header fwpmu.h

/* c38d57d1-05a7-4c33-904f-7fbceee60e82 */
DEFINE_GUID(
    FWPM_LAYER_ALE_AUTH_CONNECT_V4,
    0xc38d57d1,
    0x05a7,
    0x4c33,
    0x90,
    0x4f,
    0x7f,
    0xbc,
    0xee,
    0xe6,
    0x0e,
    0x82);

/* 4a72393b-319f-44bc-84c3-ba54dcb3b6b4 */
DEFINE_GUID(
    FWPM_LAYER_ALE_AUTH_CONNECT_V6,
    0x4a72393b,
    0x319f,
    0x44bc,
    0x84,
    0xc3,
    0xba,
    0x54,
    0xdc,
    0xb3,
    0xb6,
    0xb4);

/* d78e1e87-8644-4ea5-9437-d809ecefc971 */
DEFINE_GUID(
    FWPM_CONDITION_ALE_APP_ID,
    0xd78e1e87,
    0x8644,
    0x4ea5,
    0x94,
    0x37,
    0xd8,
    0x09,
    0xec,
    0xef,
    0xc9,
    0x71);

/* c35a604d-d22b-4e1a-91b4-68f674ee674b */
DEFINE_GUID(
    FWPM_CONDITION_IP_REMOTE_PORT,
    0xc35a604d,
    0xd22b,
    0x4e1a,
    0x91,
    0xb4,
    0x68,
    0xf6,
    0x74,
    0xee,
    0x67,
    0x4b);

/* 4cd62a49-59c3-4969-b7f3-bda5d32890a4 */
DEFINE_GUID(
    FWPM_CONDITION_IP_LOCAL_INTERFACE,
    0x4cd62a49,
    0x59c3,
    0x4969,
    0xb7,
    0xf3,
    0xbd,
    0xa5,
    0xd3,
    0x28,
    0x90,
    0xa4);

/* 632ce23b-5167-435c-86d7-e903684aa80c */
DEFINE_GUID(
    FWPM_CONDITION_FLAGS,
    0x632ce23b,
    0x5167,
    0x435c,
    0x86,
    0xd7,
    0xe9,
    0x03,
    0x68,
    0x4a,
    0xa8,
    0x0c);

#endif

namespace openvpn::TunWin {

/**
 * @brief Add WFP rules to block traffic from escaping the VPN
 */
class WFP : public RC<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<WFP> Ptr;

    OPENVPN_EXCEPTION(wfp_error);

    /**
     * @brief Enum for type of local traffic to block
     */
    enum class Block
    {
        All,
        AllButLocalDns,
        Dns,
    };

    class ActionBase;

    /**
     * @brief Wrapper class for a WFP session
     */
    class Context : public RC<thread_unsafe_refcount>
    {
      public:
        typedef RCPtr<Context> Ptr;

      private:
        friend class ActionBase;

        void block(const std::wstring &openvpn_app_path,
                   const NET_IFINDEX itf_index,
                   const Block block_type,
                   std::ostream &log)
        {
            unblock(log);
            wfp.reset(new WFP());
            wfp->block(openvpn_app_path, itf_index, block_type, log);
        }

        void unblock(std::ostream &log)
        {
            if (wfp)
            {
                wfp->reset(log);
                wfp.reset();
            }
        }

        WFP::Ptr wfp;
    };

    /**
     * @brief Base class for WFP actions
     *
     * It holds a pointer to the WFP context and blocks / unblocks using
     * the context, when it is invoked. This class cannot be constructed,
     * use the derived ActionBlock and ActionUnblock classes to manage
     * the firewall rules.
     */
    class ActionBase : public Action
    {
      public:
        /**
         * @brief Invoke the context class to block / unblock traffic.
         *
         * @param log   the log stream for diagnostics
         */
        void execute(std::ostream &log) override
        {
            log << to_string() << std::endl;
            if (block_)
                ctx_->block(openvpn_app_path_, itf_index_, block_type_, log);
            else
                ctx_->unblock(log);
        }

        std::string to_string() const override
        {
            return "ActionBase openvpn_app_path=" + wstring::to_utf8(openvpn_app_path_)
                   + " tap_index=" + std::to_string(itf_index_)
                   + " enable=" + std::to_string(block_);
        }

      protected:
        ActionBase(const bool block,
                   const std::wstring &openvpn_app_path,
                   const NET_IFINDEX itf_index,
                   const Block block_type,
                   const Context::Ptr &ctx)
            : block_(block),
              openvpn_app_path_(openvpn_app_path),
              itf_index_(itf_index),
              block_type_(block_type),
              ctx_(ctx)
        {
        }

      private:
        const bool block_;
        const std::wstring openvpn_app_path_;
        const NET_IFINDEX itf_index_;
        const Block block_type_;
        Context::Ptr ctx_;
    };

    struct ActionBlock : public ActionBase
    {
        ActionBlock(const std::wstring &openvpn_app_path,
                    const NET_IFINDEX itf_index,
                    const Block block_type,
                    const Context::Ptr &wfp)
            : ActionBase(true, openvpn_app_path, itf_index, block_type, wfp)
        {
        }
    };

    struct ActionUnblock : public ActionBase
    {
        ActionUnblock(const std::wstring &openvpn_app_path,
                      const NET_IFINDEX itf_index,
                      const Block block_type,
                      const Context::Ptr &wfp)
            : ActionBase(false, openvpn_app_path, itf_index, block_type, wfp)
        {
        }
    };

  private:
    friend class Context;

    /**
     * @brief Add WFP block filters to prevent VPN traffic from leaking
     *
     * Block traffic to all interfaces besides the VPN interface.
     * The OpenVPN process gets an exception to this rule.
     * If dns_only is set this only concerns traffic to port 53.
     *
     * Derived from code in openvpn 2, originally:
     * https://github.com/ValdikSS/openvpn-with-patches/commit/3bd4d503d21aa34636e4f97b3e32ae0acca407f0
     *
     * @param openvpn_app_path  path to the openvpn executable
     * @param itf_index         interface index of the VPN interface
     * @param block_type        which type of traffic should be blocked
     * @param log               the log ostream to use for diagnostics
     */
    void block(const std::wstring &openvpn_app_path,
               NET_IFINDEX itf_index,
               Block block_type,
               std::ostream &log)
    {
        // WFP filter/conditions
        FWPM_FILTER0 filter = {};
        FWPM_FILTER_CONDITION0 condition[2] = {};
        FWPM_FILTER_CONDITION0 match_openvpn = {};
        FWPM_FILTER_CONDITION0 match_port_53 = {};
        FWPM_FILTER_CONDITION0 match_interface = {};
        FWPM_FILTER_CONDITION0 match_loopback = {};
        FWPM_FILTER_CONDITION0 match_not_loopback = {};
        UINT64 filterid = 0;

        // Get NET_LUID object for adapter
        NET_LUID itf_luid = adapter_index_to_luid(itf_index);

        // Get app ID
        unique_ptr_del<FWP_BYTE_BLOB> openvpn_app_id_blob = get_app_id_blob(openvpn_app_path);

        // Populate packet filter layer information
        {
            FWPM_SUBLAYER0 subLayer = {};
            subLayer.subLayerKey = subLayerGUID;
            subLayer.displayData.name = const_cast<wchar_t *>(L"OpenVPN");
            subLayer.displayData.description = const_cast<wchar_t *>(L"OpenVPN");
            subLayer.flags = 0;
            subLayer.weight = 0x100;

            // Add packet filter to interface
            const DWORD status = ::FwpmSubLayerAdd0(engineHandle(), &subLayer, NULL);
            if (status != ERROR_SUCCESS)
                OPENVPN_THROW(wfp_error, "FwpmSubLayerAdd0 failed with status=0x" << std::hex << status);
        }

        /* Prepare match conditions */
        match_openvpn.fieldKey = FWPM_CONDITION_ALE_APP_ID;
        match_openvpn.matchType = FWP_MATCH_EQUAL;
        match_openvpn.conditionValue.type = FWP_BYTE_BLOB_TYPE;
        match_openvpn.conditionValue.byteBlob = openvpn_app_id_blob.get();

        match_port_53.fieldKey = FWPM_CONDITION_IP_REMOTE_PORT;
        match_port_53.matchType = FWP_MATCH_EQUAL;
        match_port_53.conditionValue.type = FWP_UINT16;
        match_port_53.conditionValue.uint16 = 53;

        match_interface.fieldKey = FWPM_CONDITION_IP_LOCAL_INTERFACE;
        match_interface.matchType = FWP_MATCH_EQUAL;
        match_interface.conditionValue.type = FWP_UINT64;
        match_interface.conditionValue.uint64 = &itf_luid.Value;

        match_loopback.fieldKey = FWPM_CONDITION_FLAGS;
        match_loopback.matchType = FWP_MATCH_FLAGS_ALL_SET;
        match_loopback.conditionValue.type = FWP_UINT32;
        match_loopback.conditionValue.uint32 = FWP_CONDITION_FLAG_IS_LOOPBACK;

        match_not_loopback.fieldKey = FWPM_CONDITION_FLAGS;
        match_not_loopback.matchType = FWP_MATCH_FLAGS_NONE_SET;
        match_not_loopback.conditionValue.type = FWP_UINT32;
        match_not_loopback.conditionValue.uint32 = FWP_CONDITION_FLAG_IS_LOOPBACK;

        // Prepare filter
        filter.subLayerKey = subLayerGUID;
        filter.displayData.name = const_cast<wchar_t *>(L"OpenVPN");
        filter.weight.type = FWP_UINT8;
        filter.weight.uint8 = 0xF;
        filter.filterCondition = condition;

        // Filter #1 -- permit IPv4 requests from OpenVPN app
        filter.layerKey = FWPM_LAYER_ALE_AUTH_CONNECT_V4;
        filter.action.type = FWP_ACTION_PERMIT;
        filter.numFilterConditions = 1;
        condition[0] = match_openvpn;
        add_filter(&filter, NULL, &filterid);
        log << "permit IPv4 requests from OpenVPN app" << std::endl;


        // Filter #2 -- permit IPv6 requests from OpenVPN app
        filter.layerKey = FWPM_LAYER_ALE_AUTH_CONNECT_V6;
        add_filter(&filter, NULL, &filterid);
        log << "permit IPv6 requests from OpenVPN app" << std::endl;


        // Filter #3 -- block IPv4 (DNS) requests, except to loopback, from other apps
        filter.layerKey = FWPM_LAYER_ALE_AUTH_CONNECT_V4;
        filter.action.type = FWP_ACTION_BLOCK;
        filter.weight.type = FWP_EMPTY;
        filter.numFilterConditions = 1;
        condition[0] = match_not_loopback;
        if (block_type == Block::Dns)
        {
            filter.numFilterConditions = 2;
            condition[1] = match_port_53;
        }
        add_filter(&filter, NULL, &filterid);
        log << "block IPv4 requests from other apps" << std::endl;


        // Filter #4 -- block IPv6 (DNS) requests, except to loopback, from other apps
        filter.layerKey = FWPM_LAYER_ALE_AUTH_CONNECT_V6;
        add_filter(&filter, NULL, &filterid);
        log << "block IPv6 requests from other apps" << std::endl;


        // Filter #5 -- allow IPv4 traffic from VPN interface
        filter.layerKey = FWPM_LAYER_ALE_AUTH_CONNECT_V4;
        filter.action.type = FWP_ACTION_PERMIT;
        filter.numFilterConditions = 1;
        condition[0] = match_interface;
        add_filter(&filter, NULL, &filterid);
        log << "allow IPv4 traffic from TAP" << std::endl;


        // Filter #6 -- allow IPv6 traffic from VPN interface
        filter.layerKey = FWPM_LAYER_ALE_AUTH_CONNECT_V6;
        add_filter(&filter, NULL, &filterid);
        log << "allow IPv6 traffic from TAP" << std::endl;

        if (block_type != Block::AllButLocalDns)
        {
            // Filter #7 -- block IPv4 DNS requests to loopback from other apps
            filter.layerKey = FWPM_LAYER_ALE_AUTH_CONNECT_V4;
            filter.action.type = FWP_ACTION_BLOCK;
            filter.weight.type = FWP_EMPTY;
            filter.numFilterConditions = 2;
            condition[0] = match_loopback;
            condition[1] = match_port_53;
            add_filter(&filter, NULL, &filterid);
            log << "block IPv4 DNS requests to loopback from other apps" << std::endl;


            // Filter #8 -- block IPv6 DNS requests to loopback from other apps
            filter.layerKey = FWPM_LAYER_ALE_AUTH_CONNECT_V6;
            add_filter(&filter, NULL, &filterid);
            log << "block IPv6 DNS requests to loopback from other apps" << std::endl;
        }
    }

    /**
     * @brief Remove WFP block filters
     *
     * @param log   the log ostream to use for disgnostics
     */
    void reset(std::ostream &log)
    {
        engineHandle.reset(&log);
    }

    /**
     * @class Wrapper for the WFP engine handle
     */
    class EngineHandle
    {
      public:
        /**
         * @brief Open a new WFP session and store the handle
         */
        EngineHandle()
        {
            FWPM_SESSION0 session = {};

            // delete all filters when engine handle is closed
            session.flags = FWPM_SESSION_FLAG_DYNAMIC;

            const DWORD status = ::FwpmEngineOpen0(NULL, RPC_C_AUTHN_WINNT, NULL, &session, &handle);
            if (status != ERROR_SUCCESS)
                OPENVPN_THROW(wfp_error, "FwpmEngineOpen0 failed with status=0x" << std::hex << status);
        }

        /**
         * @brief Close the engine handle.
         *
         * This will effectively remove all block filter rules set using this engine handle.
         *
         * @param log   the log ostream to use for disgnostics
         */
        void reset(std::ostream *log)
        {
            if (defined())
            {
                const DWORD status = ::FwpmEngineClose0(handle);
                handle = INVALID_HANDLE_VALUE;
                if (log)
                {
                    if (status != ERROR_SUCCESS)
                        *log << "FwpmEngineClose0 failed, status=" << status << std::endl;
                    else
                        *log << "WFP Engine closed" << std::endl;
                }
            }
        }

        ~EngineHandle()
        {
            reset(nullptr);
        }

        bool defined() const
        {
            return Win::Handle::defined(handle);
        }

        /**
         * @brief Return the engine handle.
         *
         * @return HANDLE   The engine handle. May not represent an open session.
         */
        HANDLE operator()() const
        {
            return handle;
        }

      private:
        EngineHandle(const EngineHandle &) = delete;
        EngineHandle &operator=(const EngineHandle &) = delete;

        HANDLE handle = INVALID_HANDLE_VALUE;
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
        NET_LUID itf_luid;
        const NETIO_STATUS ret = ::ConvertInterfaceIndexToLuid(index, &itf_luid);
        if (ret != NO_ERROR)
            throw wfp_error("ConvertInterfaceIndexToLuid failed");
        return itf_luid;
    }

    static unique_ptr_del<FWP_BYTE_BLOB> get_app_id_blob(const std::wstring &app_path)
    {
        FWP_BYTE_BLOB *blob;
        const DWORD status = ::FwpmGetAppIdFromFileName0(app_path.c_str(), &blob);
        if (status != ERROR_SUCCESS)
            OPENVPN_THROW(wfp_error, "FwpmGetAppIdFromFileName0 failed, status=0x" << std::hex << status);
        return unique_ptr_del<FWP_BYTE_BLOB>(blob, [](FWP_BYTE_BLOB *blob)
                                             { ::FwpmFreeMemory0((void **)&blob); });
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
    EngineHandle engineHandle;
};

} // namespace openvpn::TunWin
