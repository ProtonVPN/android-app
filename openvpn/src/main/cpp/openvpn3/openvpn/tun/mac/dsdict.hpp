//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2022 OpenVPN Inc.
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

#pragma once

namespace openvpn {
class DSDict
{
  public:
    OPENVPN_EXCEPTION(dsdict_error);

    DSDict(CF::DynamicStore &sc_arg, const std::string &sname_arg, const std::string &dskey_arg)
        : sc(sc_arg),
          sname(sname_arg),
          dskey(dskey_arg),
          dict(CF::DynamicStoreCopyDict(sc_arg, dskey))
    {
    }

    bool dirty() const
    {
        return mod.defined() ? !CFEqual(dict(), mod()) : false;
    }

    bool push_to_store()
    {
        if (dirty())
        {
            const CF::String keystr = CF::string(dskey);
            if (SCDynamicStoreSetValue(sc(), keystr(), mod()))
            {
                OPENVPN_LOG("DSDict: updated " << dskey);
                return true;
            }
            else
                OPENVPN_LOG("DSDict: ERROR updating " << dskey);
        }
        return false;
    }

    bool remove_from_store()
    {
        if (dirty())
            throw dsdict_error("internal error: remove_from_store called on modified dict");
        const CF::String keystr = CF::string(dskey);
        if (SCDynamicStoreRemoveValue(sc(), keystr()))
        {
            OPENVPN_LOG("DSDict: removed " << dskey);
            return true;
        }
        else
        {
            OPENVPN_LOG("DSDict: ERROR removing " << dskey);
            return false;
        }
    }

    void will_modify()
    {
        if (!mod.defined())
            mod = CF::mutable_dict_copy(dict);
    }

    void mod_reset()
    {
        mod = CF::mutable_dict();
    }

    void backup_orig(const std::string &key, const bool wipe_orig = true)
    {
        const CF::String k = CF::string(key);
        const CF::String orig = orig_key(key);
        if (!CFDictionaryContainsKey(dict(), orig()))
        {
            const CF::String delval = delete_value();
            CFTypeRef v = CFDictionaryGetValue(dict(), k());
            if (!v)
                v = delval();
            will_modify();
            CFDictionarySetValue(mod(), orig(), v);
        }
        if (wipe_orig)
        {
            will_modify();
            CFDictionaryRemoveValue(mod(), k());
        }
    }

    void restore_orig()
    {
        const CFIndex size = CFDictionaryGetCount(dict());
        std::unique_ptr<const void *[]> keys(new const void *[size]);
        std::unique_ptr<const void *[]> values(new const void *[size]);
        CFDictionaryGetKeysAndValues(dict(), keys.get(), values.get());
        const CF::String orig_prefix = orig_key("");
        const CFIndex orig_prefix_len = CFStringGetLength(orig_prefix());
        const CF::String delval = delete_value();
        for (CFIndex i = 0; i < size; ++i)
        {
            const CF::String key = CF::string_cast(keys[i]);
            if (CFStringHasPrefix(key(), orig_prefix()))
            {
                const CFIndex key_len = CFStringGetLength(key());
                if (key_len > orig_prefix_len)
                {
                    const CFRange r = CFRangeMake(orig_prefix_len, key_len - orig_prefix_len);
                    const CF::String k(CFStringCreateWithSubstring(kCFAllocatorDefault, key(), r));
                    const CFTypeRef v = values[i];
                    const CF::String vstr = CF::string_cast(v);
                    will_modify();
                    if (vstr.defined() && CFStringCompare(vstr(), delval(), 0) == kCFCompareEqualTo)
                        CFDictionaryRemoveValue(mod(), k());
                    else
                        CFDictionaryReplaceValue(mod(), k(), v);
                    CFDictionaryRemoveValue(mod(), key());
                }
            }
        }
    }

    std::string to_string() const
    {
        std::ostringstream os;
        os << "*** DSDict " << dskey << std::endl;
        std::string orig = CF::description(dict());
        string::trim_crlf(orig);
        os << "ORIG " << orig << std::endl;
        if (dirty())
        {
            std::string modstr = CF::description(mod());
            string::trim_crlf(modstr);
            os << "MODIFIED " << modstr << std::endl;
        }
        return os.str();
    }

    static CF::DynamicStore ds_create(const std::string &sname)
    {
        CF::String sn = CF::string(sname);
        return CF::DynamicStore(SCDynamicStoreCreate(kCFAllocatorDefault, sn(), nullptr, nullptr));
    }

    static bool signal_network_reconfiguration(const std::string &sname)
    {
        const char *key = "Setup:/Network/Global/IPv4";
        CF::DynamicStore sc = ds_create(sname);
        const CF::String cfkey = CF::string(key);
        OPENVPN_LOG("DSDict: SCDynamicStoreNotifyValue " << key);
        return bool(SCDynamicStoreNotifyValue(sc(), cfkey()));
    }

    CF::DynamicStore sc;
    const std::string sname;
    const std::string dskey;
    const CF::Dict dict;
    CF::MutableDict mod;

  private:
    CF::String orig_key(const std::string &key) const
    {
        return CF::string(sname + "Orig" + key);
    }

    CF::String delete_value() const
    {
        return CF::string(sname + "DeleteValue");
    }
};
} // namespace openvpn
