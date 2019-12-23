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

namespace openvpn {
  namespace CF {
    OPENVPN_CF_WRAP(Cert, cert_cast, SecCertificateRef, SecCertificateGetTypeID)
    OPENVPN_CF_WRAP(Key, key_cast, SecKeyRef, SecKeyGetTypeID)
    OPENVPN_CF_WRAP(Identity, identity_cast, SecIdentityRef, SecIdentityGetTypeID)
    OPENVPN_CF_WRAP(Policy, policy_cast, SecPolicyRef, SecPolicyGetTypeID)
    OPENVPN_CF_WRAP(Trust, trust_cast, SecTrustRef, SecTrustGetTypeID)
#ifndef OPENVPN_PLATFORM_IPHONE
    OPENVPN_CF_WRAP(Keychain, keychain_cast, SecKeychainRef, SecKeychainGetTypeID)
    OPENVPN_CF_WRAP(Access, access_cast, SecAccessRef, SecAccessGetTypeID)
#endif
  } // namespace CF

} // namespace openvpn

#endif // OPENVPN_APPLECRYPTO_CF_CFSEC_H
