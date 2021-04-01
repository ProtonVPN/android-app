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

#ifndef OPENVPN_TUN_PERSIST_TUNWRAP_H
#define OPENVPN_TUN_PERSIST_TUNWRAP_H

#include <openvpn/common/size.hpp>
#include <openvpn/common/destruct.hpp>

namespace openvpn {

  // TunWrapTemplate is used client-side to store the underlying tun
  // interface fd/handle.  SCOPED_OBJ is generally a ScopedFD (unix) or a
  // ScopedHANDLE (Windows).  It can also be a ScopedAsioStream.
  template <typename SCOPED_OBJ>
  class TunWrapTemplate : public RC<thread_unsafe_refcount>
  {
  public:
    typedef RCPtr<TunWrapTemplate> Ptr;

    TunWrapTemplate(const bool retain_obj)
      : retain_obj_(retain_obj)
    {
    }

    virtual ~TunWrapTemplate()
    {
      close();
    }

    bool obj_defined() const
    {
      return obj_.defined();
    }

    // Current persisted tun fd/handle
    typename SCOPED_OBJ::base_type obj() const
    {
      return obj_();
    }

    bool destructor_defined() const
    {
      return bool(destruct_);
    }

    // destruct object performs cleanup prior to TAP device
    // HANDLE close, such as removing added routes.
    void add_destructor(const DestructorBase::Ptr& destruct)
    {
      close_destructor();
      destruct_ = destruct;
    }

    void close_destructor()
    {
      try {
	if (destruct_)
	  {
	    std::ostringstream os;
	    destruct_->destroy(os);
	    OPENVPN_LOG_STRING(os.str());
	    destruct_.reset();
	  }
      }
      catch (const std::exception& e)
	{
	  OPENVPN_LOG("TunWrap destructor exception: " << e.what());
	}
    }

    void close()
    {
      if (retain_obj_)
	obj_.release();
      else
	{
	  close_destructor();
	  obj_.close();
	}
    }

    void save_replace_sock(const typename SCOPED_OBJ::base_type obj)
    {
      if (retain_obj_)
	obj_.replace(obj);
      else
	obj_.reset(obj);
    }

  private:
    const bool retain_obj_;
    DestructorBase::Ptr destruct_;
    SCOPED_OBJ obj_;
  };

}
#endif
