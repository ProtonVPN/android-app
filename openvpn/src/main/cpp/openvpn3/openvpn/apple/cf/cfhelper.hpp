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

#ifndef OPENVPN_APPLECRYPTO_CF_CFHELPER_H
#define OPENVPN_APPLECRYPTO_CF_CFHELPER_H

#include <openvpn/buffer/buffer.hpp>
#include <openvpn/apple/cf/cf.hpp>

// These methods build on the Wrapper classes for Apple Core Foundation objects
// defined in cf.hpp.  They add additional convenience methods, such as dictionary
// lookup.

namespace openvpn {
  namespace CF {

    // essentially a vector of void *, used as source for array and dictionary constructors
    typedef BufferAllocatedType<CFTypeRef, thread_unsafe_refcount> SrcList;

    inline Array array(const SrcList& values)
    {
      return array((const void **)values.c_data(), values.size());
    }

    inline Dict dict(const SrcList& keys, const SrcList& values)
    {
      return dict((const void **)keys.c_data(), (const void **)values.c_data(), std::min(keys.size(), values.size()));
    }

    inline CFTypeRef mutable_dict_new()
    {
      return CFDictionaryCreateMutable(kCFAllocatorDefault, 0, &kCFTypeDictionaryKeyCallBacks, &kCFTypeDictionaryValueCallBacks);
    }

    inline CFTypeRef mutable_array_new()
    {
      return CFArrayCreateMutable(kCFAllocatorDefault, 0, &kCFTypeArrayCallBacks);
    }

    // Lookup or create (if absent) an item in a mutable dictionary.
    // Return the item, which will be owned by base.
    template <typename KEY>
    inline CFTypeRef dict_get_create(CFMutableDictionaryRef base,
				     const KEY& key,
				     CFTypeRef (*create_method)())
    {
      if (base)
	{
	  String keystr = string(key);
	  CFTypeRef ret = CFDictionaryGetValue(base, keystr()); // try lookup first
	  if (!ret)
	    {
	      // doesn't exist, must create
	      ret = (*create_method)();
	      CFDictionaryAddValue(base, keystr(), ret);
	      CFRelease(ret); // because ret is now owned by base
	    }
	  return ret;
	}
      return nullptr;
    }

    // lookup a dict in another dict (base) and return or create if absent
    template <typename KEY>
    inline MutableDict dict_get_create_dict(MutableDict& base, const KEY& key)
    {
      String keystr = string(key);
      return mutable_dict_cast(dict_get_create(base(), keystr(), mutable_dict_new));
    }

    // lookup an array in a dict (base) and return or create if absent
    template <typename KEY>
    inline MutableArray dict_get_create_array(MutableDict& base, const KEY& key)
    {
      String keystr = string(key);
      return mutable_array_cast(dict_get_create(base(), keystr(), mutable_array_new));
    }

    // lookup an object in a dictionary (DICT should be a Dict or a MutableDict)
    template <typename DICT, typename KEY>
    inline CFTypeRef dict_get_obj(const DICT& dict, const KEY& key)
    {
      return dict_index(dict, key);
    }

    // lookup a string in a dictionary (DICT should be a Dict or a MutableDict)
    template <typename DICT, typename KEY>
    inline std::string dict_get_str(const DICT& dict, const KEY& key)
    {
      return cppstring(string_cast(dict_index(dict, key)));
    }

    // lookup a string in a dictionary (DICT should be a Dict or a MutableDict)
    template <typename DICT, typename KEY>
    inline std::string dict_get_str(const DICT& dict, const KEY& key, const std::string& default_value)
    {
      String str(string_cast(dict_index(dict, key)));
      if (str.defined())
	return cppstring(str());
      else
	return default_value;
    }

    // lookup an integer in a dictionary (DICT should be a Dict or a MutableDict)
    template <typename DICT, typename KEY>
    inline int dict_get_int(const DICT& dict, const KEY& key, const int default_value)
    {
      int ret;
      Number num = number_cast(dict_index(dict, key));
      if (num.defined() && CFNumberGetValue(num(), kCFNumberIntType, &ret))
	return ret;
      else
	return default_value;
    }

    // lookup a boolean in a dictionary (DICT should be a Dict or a MutableDict)
    template <typename DICT, typename KEY>
    inline bool dict_get_bool(const DICT& dict, const KEY& key, const bool default_value)
    {
      Bool b = bool_cast(dict_index(dict, key));
      if (b.defined())
	{
	  if (b() == kCFBooleanTrue)
	    return true;
	  else if (b() == kCFBooleanFalse)
	    return false;
	}
      return default_value;
    }

    // like CFDictionarySetValue, but no-op if any args are NULL
    inline void dictionarySetValue(CFMutableDictionaryRef theDict, const void *key, const void *value)
    {
      if (theDict && key && value)
	CFDictionarySetValue(theDict, key, value);
    }

    // like CFArrayAppendValue, but no-op if any args are NULL
    inline void arrayAppendValue(CFMutableArrayRef theArray, const void *value)
    {
      if (theArray && value)
	CFArrayAppendValue(theArray, value);
    }

    // set a CFTypeRef in a mutable dictionary
    template <typename KEY>
    inline void dict_set_obj(MutableDict& dict, const KEY& key, CFTypeRef value)
    {
      String keystr = string(key);
      dictionarySetValue(dict(), keystr(), value);
    }

    // set a string in a mutable dictionary

    template <typename KEY, typename VALUE>
    inline void dict_set_str(MutableDict& dict, const KEY& key, const VALUE& value)
    {
      String keystr = string(key);
      String valstr = string(value);
      dictionarySetValue(dict(), keystr(), valstr());
    }

    // set a number in a mutable dictionary

    template <typename KEY>
    inline void dict_set_int(MutableDict& dict, const KEY& key, int value)
    {
      String keystr = string(key);
      Number num = number_from_int(value);
      dictionarySetValue(dict(), keystr(), num());
    }

    template <typename KEY>
    inline void dict_set_int32(MutableDict& dict, const KEY& key, SInt32 value)
    {
      String keystr = string(key);
      Number num = number_from_int32(value);
      dictionarySetValue(dict(), keystr(), num());
    }

    template <typename KEY>
    inline void dict_set_long_long(MutableDict& dict, const KEY& key, long long value)
    {
      String keystr = string(key);
      Number num = number_from_long_long(value);
      dictionarySetValue(dict(), keystr(), num());
    }

    template <typename KEY>
    inline void dict_set_index(MutableDict& dict, const KEY& key, CFIndex value)
    {
      String keystr = string(key);
      Number num = number_from_index(value);
      dictionarySetValue((CFMutableDictionaryRef)dict(), keystr(), num());
    }

    // set a boolean in a mutable dictionary

    template <typename KEY>
    inline void dict_set_bool(MutableDict& dict, const KEY& key, bool value)
    {
      String keystr = string(key);
      CFBooleanRef boolref = value ? kCFBooleanTrue : kCFBooleanFalse;
      dictionarySetValue(dict(), keystr(), boolref);
    }

    // append string to a mutable array

    template <typename VALUE>
    inline void array_append_str(MutableArray& array, const VALUE& value)
    {
      String valstr = string(value);
      arrayAppendValue(array(), valstr());
    }

    // append a number to a mutable array

    inline void array_append_int(MutableArray& array, int value)
    {
      Number num = number_from_int(value);
      arrayAppendValue(array(), num());
    }

    inline void array_append_int32(MutableArray& array, SInt32 value)
    {
      Number num = number_from_int32(value);
      arrayAppendValue(array(), num());
    }

    inline void array_append_long_long(MutableArray& array, long long value)
    {
      Number num = number_from_long_long(value);
      arrayAppendValue(array(), num());
    }

    inline void array_append_index(MutableArray& array, CFIndex value)
    {
      Number num = number_from_index(value);
      arrayAppendValue(array(), num());
    }
  }
}
#endif
