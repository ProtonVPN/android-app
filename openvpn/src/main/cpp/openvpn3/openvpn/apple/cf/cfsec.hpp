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

#ifndef OPENVPN_APPLECRYPTO_CF_CFSEC_H
#define OPENVPN_APPLECRYPTO_CF_CFSEC_H

#include <openvpn/common/platform.hpp>

#include <Security/SecCertificate.h>
#include <Security/SecIdentity.h>
#include <Security/SecPolicy.h>
#include <Security/SecTrust.h>

#ifndef OPENVPN_PLATFORM_IPHONE
#include <Security/SecKeychain.h>
#include <Security/SecAccess.h>
#endif

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/apple/cf/cf.hpp>

// Define C++ wrappings for Apple security-related objects.

namespace openvpn::CF {
OPENVPN_CF_WRAP(Cert, cert_cast, SecCertificateRef, SecCertificateGetTypeID)
OPENVPN_CF_WRAP(Key, key_cast, SecKeyRef, SecKeyGetTypeID)
OPENVPN_CF_WRAP(Identity, identity_cast, SecIdentityRef, SecIdentityGetTypeID)
OPENVPN_CF_WRAP(Policy, policy_cast, SecPolicyRef, SecPolicyGetTypeID)
OPENVPN_CF_WRAP(Trust, trust_cast, SecTrustRef, SecTrustGetTypeID)
#ifndef OPENVPN_PLATFORM_IPHONE
OPENVPN_CF_WRAP(Keychain, keychain_cast, SecKeychainRef, SecKeychainGetTypeID)
OPENVPN_CF_WRAP(Access, access_cast, SecAccessRef, SecAccessGetTypeID)
#endif
} // namespace openvpn::CF


#endif // OPENVPN_APPLECRYPTO_CF_CFSEC_H
