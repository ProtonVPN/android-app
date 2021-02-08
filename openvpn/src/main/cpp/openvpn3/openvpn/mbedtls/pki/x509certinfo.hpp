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
//

#pragma once

#include <cstring>
#include <string>
#include <vector>

#include <mbedtls/x509.h>
#include <mbedtls/x509_crt.h>
#include <mbedtls/oid.h>

#include "openvpn/common/hexstr.hpp"

#define MBEDTLS_MAX_SUBJECT_LENGTH 256

namespace openvpn {
namespace MbedTLSPKI {

/**
 *  Retrieve the complete X.509 Certificate Subject field
 *
 *  OpenSSL supports two ways of representing the subject line.  The old
 *  format is deprecated, but there might be code expecting this old format.
 *  The old format looks like this:
 *
 *      /C=KG/ST=NA/O=OpenVPN-TEST/CN=Test-Server/emailAddress=me@myhost.mydomain
 *
 *  The new format is UTF-8 compliant and has a different formatting scheme:
 *
 *      C=KG, ST=NA, O=OpenVPN-TEST, CN=Test-Server,
 *emailAddress=me@myhost.mydomain
 *
 *  This mbed TLS implementation supports generating a subject line formatted
 *  as the deprecated OpenSSL format.  This is the default behaviour, to
 *  preserve OpenSSL compatibility with existing OpenVPN code.
 *
 * @param cert         Pointer to a native mbed TLS X509 object containing the
 *                     certificate
 * @param new_format   (optional, default: false) Which format to use,
 *                     true indicates the new format
 *
 * @return Returns a std::string containing the complete certificate subject.
 *         If it was not possible to retrieve the subject, and empty string
 *         is returned.
 */
static std::string x509_get_subject(const mbedtls_x509_crt *cert,
                                    bool new_format = false) {
  if (!new_format) {
    // Try to return the x509 subject formatted like the OpenSSL
    // X509_NAME_oneline method.  Only attributes matched in the switch
    // statements below will be rendered.  All other attributes will be
    // ignored.

    std::string ret;
    for (const mbedtls_x509_name *name = &cert->subject; name != nullptr;
         name = name->next) {
      const char *key = nullptr;
      if (!MBEDTLS_OID_CMP(MBEDTLS_OID_AT_CN, &name->oid))
        key = "CN";
      else if (!MBEDTLS_OID_CMP(MBEDTLS_OID_AT_COUNTRY, &name->oid))
        key = "C";
      else if (!MBEDTLS_OID_CMP(MBEDTLS_OID_AT_LOCALITY, &name->oid))
        key = "L";
      else if (!MBEDTLS_OID_CMP(MBEDTLS_OID_AT_STATE, &name->oid))
        key = "ST";
      else if (!MBEDTLS_OID_CMP(MBEDTLS_OID_AT_ORGANIZATION, &name->oid))
        key = "O";
      else if (!MBEDTLS_OID_CMP(MBEDTLS_OID_AT_ORG_UNIT, &name->oid))
        key = "OU";
      else if (!MBEDTLS_OID_CMP(MBEDTLS_OID_PKCS9_EMAIL, &name->oid))
        key = "emailAddress";

      // make sure that key is defined and value has no embedded nulls
      if (key &&
          !string::embedded_null((const char *)name->val.p, name->val.len))
        ret += "/" + std::string(key) + "=" +
               std::string((const char *)name->val.p, name->val.len);
    }
    return ret;
  }

  char tmp_subj[MBEDTLS_MAX_SUBJECT_LENGTH] = {0};
  int ret = mbedtls_x509_dn_gets(tmp_subj, MBEDTLS_MAX_SUBJECT_LENGTH - 1,
                                 &cert->subject);
  return (ret > 0 ? std::string(tmp_subj) : std::string(""));
}

/**
 *  Retrieves just the common name of the X.509 Certificate subject field
 *
 * @param cert     Pointer to a native mbedTLS X509 object containing the
 *                 certificate
 *
 * @return Returns the contents of the extracted field on success.  The
 *         resulting string may be empty if the extraction failed or the field
 *         is empty.
 */
static std::string x509_get_common_name(const mbedtls_x509_crt *cert) {
  const mbedtls_x509_name *name = &cert->subject;

  // find common name
  while (name != nullptr) {
    if (!MBEDTLS_OID_CMP(MBEDTLS_OID_AT_CN, &name->oid)) {
      break;
    }
    name = name->next;
  }

  return (name ? std::string((const char *)name->val.p, name->val.len)
               : std::string(""));
}

}  // namespace MbedTLSPKI
}  // namespace openvpn
