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

// Automatically reset a target object when
// AutoReset goes out of scope.

#ifndef OPENVPN_COMMON_AUTORESET_H
#define OPENVPN_COMMON_AUTORESET_H

namespace openvpn {

template <typename T>
class AutoReset
{
  public:
    AutoReset(T &obj)
        : obj_(&obj)
    {
    }

    ~AutoReset()
    {
        if (obj_)
            obj_->reset();
    }

    void disarm()
    {
        obj_ = nullptr;
    }

  private:
    T *obj_;
};

} // namespace openvpn

#endif
