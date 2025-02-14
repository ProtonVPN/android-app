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

#ifndef OPENVPN_APPLE_SCDYNSTORE_H
#define OPENVPN_APPLE_SCDYNSTORE_H

#include <SystemConfiguration/SCDynamicStore.h>

#include <openvpn/apple/cf/cf.hpp>

namespace openvpn::CF {
OPENVPN_CF_WRAP(DynamicStore, dynamic_store_cast, SCDynamicStoreRef, SCDynamicStoreGetTypeID)

template <typename RET, typename KEY>
inline RET DynamicStoreCopy(const DynamicStore &ds, const KEY &key)
{
    String keystr = string(key);
    return RET(RET::cast(SCDynamicStoreCopyValue(ds(), keystr())));
}

template <typename KEY>
inline Dict DynamicStoreCopyDict(const DynamicStore &ds, const KEY &key)
{
    Dict dict = DynamicStoreCopy<Dict>(ds, key);
    if (dict.defined())
        return dict;
    else
        return CF::empty_dict();
}
} // namespace openvpn::CF

#endif
