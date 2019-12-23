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

// A scoped Asio stream that is automatically closed by its destructor.

#ifndef OPENVPN_ASIO_SCOPED_ASIO_STREAM_H
#define OPENVPN_ASIO_SCOPED_ASIO_STREAM_H

#include <openvpn/common/size.hpp>

namespace openvpn {

  template <typename STREAM>
  class ScopedAsioStream
  {
    ScopedAsioStream(const ScopedAsioStream&) = delete;
    ScopedAsioStream& operator=(const ScopedAsioStream&) = delete;

  public:
    typedef STREAM* base_type;

    ScopedAsioStream() : obj_(undefined()) {}

    explicit ScopedAsioStream(STREAM *obj)
      : obj_(obj) {}

    static STREAM* undefined() { return nullptr; }

    STREAM* release()
    {
      STREAM* ret = obj_;
      obj_ = nullptr;
      //OPENVPN_LOG("**** SAS RELEASE=" << ret);
      return ret;
    }

    static bool defined_static(STREAM* obj)
    {
      return obj != nullptr;
    }

    bool defined() const
    {
      return defined_static(obj_);
    }

    STREAM* operator()() const
    {
      return obj_;
    }

    void reset(STREAM* obj)
    {
      close();
      obj_ = obj;
      //OPENVPN_LOG("**** SAS RESET=" << obj_);
    }

    // unusual semantics: replace obj without closing it first
    void replace(STREAM* obj)
    {
      //OPENVPN_LOG("**** SAS REPLACE " << obj_ << " -> " << obj);
      obj_ = obj;
    }

    // return false if close error
    bool close()
    {
      if (defined())
	{
	  //OPENVPN_LOG("**** SAS CLOSE obj=" << obj_);
	  delete obj_;
	  obj_ = nullptr;
	}
      return true;
    }

    ~ScopedAsioStream()
    {
      //OPENVPN_LOG("**** SAS DESTRUCTOR");
      close();
    }

  private:
    STREAM* obj_;
  };

} // namespace openvpn

#endif
