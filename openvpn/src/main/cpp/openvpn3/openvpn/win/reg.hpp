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


// registry utilities for Windows

#pragma once

#include <windows.h>
#include <string>
#include <openvpn/win/winerr.hpp>
#include <openvpn/common/size.hpp>

namespace openvpn::Win {

template <typename E>
static void check_reg_error(DWORD status, const std::string &key)
{
    if (status != ERROR_SUCCESS)
    {
        const Win::Error err(status);
        OPENVPN_THROW(E, "registry key " << key << " error: " << err.message());
    }
}

/**
 * @class Reg
 * @brief Abstraction of Windows Registry operations
 */
struct Reg
{
    /**
     * @class Key
     * @brief Wrapper class for a Registry key handle
     */
    class Key
    {
        Key(const Key &) = delete;
        Key &operator=(const Key &) = delete;

      public:
        /**
         * @brief Construct a Key with an open handle for a subkey under key
         *
         * In case the subkey cannot be opened or created, the handle remains invalid.
         *
         * @param key       the key handle which the subkey is relative to
         * @param subkey    the subkey to open for the object
         * @param create    whether the subkey will be created if it doesn't exist
         */
        Key(HKEY key, const std::wstring &subkey, bool create = false)
        {
            LSTATUS error;
            if (create)
            {
                error = ::RegCreateKeyExW(key,
                                          subkey.c_str(),
                                          0,
                                          nullptr,
                                          0,
                                          KEY_ALL_ACCESS,
                                          nullptr,
                                          &key_,
                                          nullptr);
            }
            else
            {
                error = ::RegOpenKeyExW(key,
                                        subkey.c_str(),
                                        0,
                                        KEY_ALL_ACCESS,
                                        &key_);
            }
            if (error)
            {
                key_ = static_cast<HKEY>(INVALID_HANDLE_VALUE);
            }
        }
        Key(Key &key, const std::wstring &subkey, bool create = false)
            : Key(key(), subkey, create)
        {
        }
        /**
         * @brief Construct a Key with an open handle for a subkey under HKLM
         *
         * In case the subkey cannot be opened or created, the handle remains invalid.
         *
         * @param subkey    the subkey to open for the object
         * @param create    whether the subkey will be created if it doesn't exist
         */
        Key(const std::wstring &subkey, bool create = false)
            : Key(HKEY_LOCAL_MACHINE, subkey, create)
        {
        }

        Key() = default;

        Key(Key &&rhs)
        {
            if (defined())
            {
                ::RegCloseKey(key_);
                key_ = static_cast<HKEY>(INVALID_HANDLE_VALUE);
            }
            std::swap(key_, rhs.key_);
        }
        Key &operator=(Key &&rhs)
        {
            Key copy{std::move(rhs)};
            std::swap(copy.key_, this->key_);
            return *this;
        }

        ~Key()
        {
            if (defined())
            {
                ::RegCloseKey(key_);
            }
        }

        /**
         * @brief Check for a valid key handle
         *
         * @return true     if the handle is valid
         * @return false    if the handle is invalid
         */
        bool defined() const
        {
            return key_ != INVALID_HANDLE_VALUE;
        }

        /**
         * @brief Retrun a pointer to the Registry key handle
         *
         * @return PHKEY    the Registry key handle pointer
         */
        PHKEY ref()
        {
            return &key_;
        }

        /**
         * @brief Return the Registry key handle
         * @return HKEY     the key handle
         */
        HKEY operator()()
        {
            return key_;
        }

      private:
        HKEY key_ = static_cast<HKEY>(INVALID_HANDLE_VALUE);
    };

    /**
     * @class KeyEnumerator
     * @brief Wrapper for Registry subkey enumeration
     *
     * The class is based on a std::vector and supports range based for
     * loops. This makes it easy to enumerate over a range of subkeys.
     */
    class KeyEnumerator : public std::vector<std::wstring>
    {
      public:
        /**
         * @brief Construct a new Key Enumerator object
         *
         * @param key The Registry key, its subkeys will be enumerated.
         */
        KeyEnumerator(Key &key)
        {
            if (!key.defined())
                return;

            LSTATUS status;
            DWORD subkeys_num;
            status = ::RegQueryInfoKeyA(key(),
                                        nullptr,
                                        nullptr,
                                        nullptr,
                                        &subkeys_num,
                                        nullptr,
                                        nullptr,
                                        nullptr,
                                        nullptr,
                                        nullptr,
                                        nullptr,
                                        nullptr);

            if (status != ERROR_SUCCESS)
                return;

            constexpr int MAX_KEY_LENGTH = 255;
            for (DWORD i = 0; i < subkeys_num; ++i)
            {
                DWORD subkey_size = MAX_KEY_LENGTH;
                WCHAR subkey[MAX_KEY_LENGTH];
                status = ::RegEnumKeyExW(key(),
                                         i,
                                         subkey,
                                         &subkey_size,
                                         nullptr,
                                         nullptr,
                                         nullptr,
                                         nullptr);
                if (status == ERROR_SUCCESS)
                    push_back(subkey);
            }
        }
    };

    /**
     * Registry subkeys where NRPT rules can be found
     */
    static constexpr PCWSTR gpol_nrpt_subkey = LR"(SOFTWARE\Policies\Microsoft\Windows NT\DNSClient\DnsPolicyConfig)";
    static constexpr PCWSTR local_nrpt_subkey = LR"(SYSTEM\CurrentControlSet\Services\Dnscache\Parameters\DnsPolicyConfig)";

    /**
     * Registry subkeys where IP configuration for interfaces can be found
     */
    static constexpr WCHAR subkey_ipv4_itfs[] = LR"(SYSTEM\CurrentControlSet\Services\Tcpip\Parameters\Interfaces)";
    static constexpr WCHAR subkey_ipv6_itfs[] = LR"(SYSTEM\CurrentControlSet\Services\Tcpip6\Parameters\Interfaces)";

    /**
     * @brief Read a REG_DWORD value from the Windows registry
     *
     * @param key   the key the value is to be read from
     * @param name  the name of the REG_DWORD value
     * @return std::pair<std::wstring, LSTATUS> the string and the status code of
     *              the registry operations. If the string is empty, the status
     *              may indicate an error.
     */
    static std::pair<DWORD, LSTATUS> get_dword(Key &key, PCWSTR name)
    {
        DWORD type;
        DWORD value;
        DWORD size = sizeof(value);
        PBYTE data = reinterpret_cast<PBYTE>(&value);
        LSTATUS err;

        err = ::RegGetValueW(key(), nullptr, name, RRF_RT_REG_DWORD, &type, data, &size);
        if (err)
        {
            return {0, err};
        }
        else if (type != REG_DWORD)
        {
            return {0, ERROR_DATATYPE_MISMATCH};
        }

        return {value, err};
    }

    /**
     * @brief Read a REG_SZ value from the Windows registry
     *
     * @param key   the key the value is to be read from
     * @param name  the name of the REG_SZ value
     * @return std::pair<std::wstring, LSTATUS> the string and the status code of
     *              the registry operations. If the string is empty, the status
     *              may indicate an error.
     */
    static std::pair<std::wstring, LSTATUS> get_string(Key &key, PCWSTR name)
    {
        LSTATUS err;
        DWORD size = 0;
        DWORD type;
        err = ::RegGetValueW(key(), nullptr, name, RRF_RT_REG_SZ, &type, nullptr, &size);
        if (err)
        {
            return {{}, err};
        }
        else if (type != REG_SZ)
        {
            return {{}, ERROR_DATATYPE_MISMATCH};
        }

        std::wstring str(size / sizeof(std::wstring::value_type) + 1, '\0');
        PBYTE data = reinterpret_cast<PBYTE>(str.data());
        err = ::RegGetValueW(key(), nullptr, name, RRF_RT_REG_SZ, nullptr, data, &size);
        if (err)
        {
            return {{}, err};
        }

        str.resize(::wcslen(str.c_str())); // remove trailing NULs
        return {str, err};
    }

    /**
     * @brief Read a REG_BINARY value from the Windows registry
     *
     * @param key   the key the value is to be read from
     * @param name  the name of the REG_BINARY value
     * @return std::pair<std::string, LSTATUS> the binary value and the status code of
     *              the registry operations. If the string is empty, the status
     *              may indicate an error. Otherwise string::data() and string::size()
     *              can be used to access the data from the Registry value.
     */
    static std::pair<std::string, LSTATUS> get_binary(Key &key, PCWSTR name)
    {
        LSTATUS err;
        DWORD size = 0;
        DWORD type;
        err = ::RegGetValueW(key(), nullptr, name, RRF_RT_REG_BINARY, &type, nullptr, &size);
        if (err)
        {
            return {{}, err};
        }
        else if (type != REG_BINARY)
        {
            return {{}, ERROR_DATATYPE_MISMATCH};
        }

        std::string str(size, '\0');
        PBYTE data = reinterpret_cast<PBYTE>(str.data());
        err = ::RegGetValueW(key(), nullptr, name, RRF_RT_REG_BINARY, nullptr, data, &size);
        if (err)
        {
            return {{}, err};
        }

        return {str, err};
    }

    /**
     * @brief Set a DWORD value in the Registry
     *
     * @param key       the Key where the value is set in
     * @param name      the name of the value
     * @param value     the value itself
     * @return LSTATUS  status of the Registry operation
     */
    static LSTATUS set_dword(Key &key, PCWSTR name, DWORD value)
    {
        const BYTE *bytes = reinterpret_cast<const BYTE *>(&value);
        DWORD size = static_cast<DWORD>(sizeof(value));
        return ::RegSetValueExW(key(), name, 0, REG_DWORD, bytes, size);
    }

    /**
     * @brief Set a REG_SZ value in the Registy
     *
     * @param key       the Key where the value is set in
     * @param name      the name of the value
     * @param value     the value itself
     * @return LSTATUS  status of the Registry operation
     */
    static LSTATUS set_string(Key &key, PCWSTR name, const std::wstring &value)
    {
        const BYTE *bytes = reinterpret_cast<const BYTE *>(value.c_str());
        DWORD size = static_cast<DWORD>((value.size() + 1) * sizeof(std::wstring::value_type));
        return ::RegSetValueExW(key(), name, 0, REG_SZ, bytes, size);
    }

    /**
     * @brief Set a REG_MULTI_SZ value in the Registry
     *
     * Note the this function, unlike the one for REG_SZ values, expects the
     * string value to be set to be a complete MULTI_SZ string, i.e. have two
     * NUL characters at the end, and a NUL character between individual
     * string subvalues.
     *
     * @param key       the Key where the value is set in
     * @param name      the name of the value
     * @param value     the value itself
     * @return LSTATUS  status of the Registry operation
     */
    static LSTATUS set_multi_string(Key &key, PCWSTR name, const std::wstring &value)
    {
        const BYTE *bytes = reinterpret_cast<const BYTE *>(value.data());
        DWORD size = static_cast<DWORD>(value.size() * sizeof(std::wstring::value_type));
        return ::RegSetValueExW(key(), name, 0, REG_MULTI_SZ, bytes, size);
    }

    /**
     * @brief Delete a subkey from the Registry
     *
     * If the subkey contains values or subkeys, these are
     * deleted from the Registry as well.
     *
     * @param subkey    the subkey to be deleted
     * @return LSTATUS  status of the Registry operation
     */
    static LSTATUS delete_subkey(const std::wstring &subkey)
    {
        return ::RegDeleteTreeW(HKEY_LOCAL_MACHINE, subkey.c_str());
    }

    /**
     * @brief Delete a value from the Registry
     *
     * @param key       the Key where the value is deleted from
     * @param name      the name of the value
     * @return LSTATUS  status of the Registry operation
     */
    static LSTATUS delete_value(Key &key, PCWSTR name)
    {
        return ::RegDeleteValueW(key(), name);
    }
};

} // namespace openvpn::Win
