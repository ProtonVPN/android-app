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

// Client tun setup base class for unix

#ifndef OPENVPN_TUN_BUILDER_SETUP_H
#define OPENVPN_TUN_BUILDER_SETUP_H

#include <openvpn/common/jsonlib.hpp>
#include <openvpn/common/destruct.hpp>
#include <openvpn/common/stop.hpp>
#include <openvpn/tun/builder/capture.hpp>

namespace openvpn::TunBuilderSetup {
struct Config
{
#ifdef HAVE_JSON
    virtual Json::Value to_json() = 0;
    virtual void from_json(const Json::Value &root, const std::string &title) = 0;
#endif
    virtual ~Config() = default;
};

struct Base : public DestructorBase
{
    typedef RCPtr<Base> Ptr;

    virtual int establish(const TunBuilderCapture &pull, Config *config, Stop *stop, std::ostream &os) = 0;
};

struct Factory : public RC<thread_unsafe_refcount>
{
    typedef RCPtr<Factory> Ptr;

    virtual Base::Ptr new_setup_obj() = 0;
};
} // namespace openvpn::TunBuilderSetup

#endif
