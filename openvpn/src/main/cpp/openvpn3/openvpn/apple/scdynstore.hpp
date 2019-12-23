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

#ifndef OPENVPN_APPLE_SCDYNSTORE_H
#define OPENVPN_APPLE_SCDYNSTORE_H

#include <SystemConfiguration/SCDynamicStore.h>

#include <openvpn/apple/cf/cf.hpp>

namespace openvpn {
  namespace CF {
    OPENVPN_CF_WRAP(DynamicStore, dynamic_store_cast, SCDynamicStoreRef, SCDynamicStoreGetTypeID)

    template <typename RET, typename KEY>
    inline RET DynamicStoreCopy(const DynamicStore& ds, const KEY& key)
    {
      String keystr = string(key);
      return RET(RET::cast(SCDynamicStoreCopyValue(ds(), keystr())));
    }

    template <typename KEY>
    inline Dict DynamicStoreCopyDict(const DynamicStore& ds, const KEY& key)
    {
      Dict dict = DynamicStoreCopy<Dict>(ds, key);
      if (dict.defined())
	return dict;
      else
	return CF::empty_dict();
    }
  }
}

#endif
