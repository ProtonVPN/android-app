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

// Parse the tls-cert-profile option.

#ifndef OPENVPN_SSL_TLS_CERT_PROFILE_H
#define OPENVPN_SSL_TLS_CERT_PROFILE_H

#include <string>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/options.hpp>

namespace openvpn::TLSCertProfile {
enum Type
{
    UNDEF = 0,
#ifdef OPENVPN_ALLOW_INSECURE_CERTPROFILE
    INSECURE,
#endif
    LEGACY,
    PREFERRED,
    SUITEB,
};

inline Type default_if_undef(const Type type)
{
    if (type == UNDEF)
        return LEGACY; // this is the default if unspecified
    else
        return type;
}

inline const std::string to_string(const Type type)
{
    switch (type)
    {
    case UNDEF:
        return "UNDEF";
#ifdef OPENVPN_ALLOW_INSECURE_CERTPROFILE
    case INSECURE:
        return "INSECURE";
#endif
    case LEGACY:
        return "LEGACY";
    case PREFERRED:
        return "PREFERRED";
    case SUITEB:
        return "SUITEB";
    default:
        return "???";
    }
}

inline Type parse_tls_cert_profile(const std::string &profile_name)
{
#ifdef OPENVPN_ALLOW_INSECURE_CERTPROFILE
    if (profile_name == "insecure")
        return INSECURE;
    else
#endif
        if (profile_name == "legacy")
        return LEGACY;
    else if (profile_name == "preferred")
        return PREFERRED;
    else if (profile_name == "suiteb")
        return SUITEB;
    else
        throw option_error(ERR_INVALID_OPTION_CRYPTO, "tls-cert-profile: unrecognized profile name");
}

inline Type parse_tls_cert_profile(const OptionList &opt,
                                   const std::string &relay_prefix)
{
    const Option *o = opt.get_ptr(relay_prefix + "tls-cert-profile");
    if (o)
    {
        const std::string profile_name = o->get_optional(1, 16);
        return parse_tls_cert_profile(profile_name);
    }
    return UNDEF;
}

// If the override ends with "default", it is only applied
// if the config doesn't specify tls-cert-profile.
// Otherwise, the override has priority over the config.
inline void apply_override(Type &type, const std::string &override)
{
    const Type orig = type;
    if (override.empty() || override == "default")
        ;
#ifdef OPENVPN_ALLOW_INSECURE_CERTPROFILE
    else if (override == "insecure-default")
    {
        if (orig == UNDEF)
            type = INSECURE;
    }
#endif
    else if (override == "legacy-default")
    {
        if (orig == UNDEF)
            type = LEGACY;
    }
    else if (override == "preferred-default")
    {
        if (orig == UNDEF)
            type = PREFERRED;
    }
#ifdef OPENVPN_ALLOW_INSECURE_CERTPROFILE
    else if (override == "insecure")
        type = INSECURE;
#endif
    else if (override == "legacy")
        type = LEGACY;
    else if (override == "preferred")
        type = PREFERRED;
    else if (override == "suiteb")
        type = SUITEB;
    else
        throw option_error(ERR_INVALID_OPTION_CRYPTO, "tls-cert-profile: unrecognized override string");
    // OPENVPN_LOG("*** tls-cert-profile before=" << to_string(orig) << " override=" << override << " after=" << to_string(type));
}
} // namespace openvpn::TLSCertProfile

#endif
