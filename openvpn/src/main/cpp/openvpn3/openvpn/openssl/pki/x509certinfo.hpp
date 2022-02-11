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
//
//  Generic functions for extracting X.509 Certificate info from
//  OpenSSL X509 objects

#pragma once

#include <cstring>
#include <string>
#include <vector>

#include <openssl/ssl.h>
#include <openssl/bio.h>
#include <openssl/x509v3.h>
#include <openssl/x509.h>

#include "openvpn/common/hexstr.hpp"
#include "openvpn/common/uniqueptr.hpp"

namespace openvpn {
namespace OpenSSLPKI {

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
 *
 * @param cert         Pointer to a native OpenSSL X509 object containing the
 *                     certificate
 * @param new_format   (optional, default: false) Which format to use,
 *                     true indicates the new format
 *
 * @return Returns a std::string containing the complete certificate subject.
 *         If it was not possible to retrieve the subject, and empty string
 *         is returned.
 */
static std::string x509_get_subject(::X509 *cert, bool new_format = false) {
  if (!new_format) {
    unique_ptr_del<char> subject(
        X509_NAME_oneline(X509_get_subject_name(cert), nullptr, 0),
        [](char *p) { OPENSSL_free(p); });
    if (subject)
      return std::string(subject.get());
    else
      return std::string("");
  }

  unique_ptr_del<BIO> subject_bio(BIO_new(BIO_s_mem()),
                                  [](BIO *p) { BIO_free(p); });
  if (subject_bio == nullptr) {
    return std::string("");
  }

  X509_NAME_print_ex(subject_bio.get(), X509_get_subject_name(cert), 0,
                     XN_FLAG_SEP_CPLUS_SPC | XN_FLAG_FN_SN |
                         ASN1_STRFLGS_UTF8_CONVERT | ASN1_STRFLGS_ESC_CTRL);
  if (BIO_eof(subject_bio.get())) {
    return std::string("");
  }

  BUF_MEM *subject_mem = nullptr;
  BIO_get_mem_ptr(subject_bio.get(), &subject_mem);
  return std::string(subject_mem->data,
                     subject_mem->data + subject_mem->length);
}

/**
 * Retrives the algorithm used to sign a X509 certificate
 * @param cert 	OpenSSL certificate
 * @return
 */
static const std::string x509_get_signature_algorithm(const ::X509* cert)
{
  int nid = X509_get_signature_nid(cert);
  const char *sig = OBJ_nid2sn(nid);

  if (sig)
    {
      return sig;
    }
  else
    return "(error getting signature algorithm)";
}

/**
 *  Retrieves a specific portion of the X.509 Certificate subject field
 *
 * @param cert     Pointer to a native OpenSSL X509 object containing the
 *                 certificate
 * @param nid      Subject name ID to retrieve.  See openssl/obj_mac.h for
 *                 list of valid NID_* references.
 *
 * @return Returns the contents of the extracted field on success.  The
 *         resulting string may be empty if the extraction failed or the field
 *         is empty.
 */
static std::string x509_get_field(::X509 *cert, const int nid) {
  static const char nullc = '\0';
  std::string ret;
  X509_NAME *x509_name = X509_get_subject_name(cert);
  int i = X509_NAME_get_index_by_NID(x509_name, nid, -1);
  if (i >= 0) {
    X509_NAME_ENTRY *ent = X509_NAME_get_entry(x509_name, i);
    if (ent) {
      ASN1_STRING *val = X509_NAME_ENTRY_get_data(ent);
      unsigned char *buf;
      buf = (unsigned char *)1;  // bug in OpenSSL 0.9.6b ASN1_STRING_to_UTF8
                                 // requires this workaround
      const int len = ASN1_STRING_to_UTF8(&buf, val);
      if (len > 0) {
        if (std::strlen((char *)buf) == static_cast<unsigned int>(len)) ret = (char *)buf;
        OPENSSL_free(buf);
      }
    }
  } else {
    i = X509_get_ext_by_NID(cert, nid, -1);
    if (i >= 0) {
      X509_EXTENSION *ext = X509_get_ext(cert, i);
      if (ext) {
        BIO *bio = BIO_new(BIO_s_mem());
        if (bio) {
          if (X509V3_EXT_print(bio, ext, 0, 0)) {
            if (BIO_write(bio, &nullc, 1) == 1) {
              char *str;
              const long len = BIO_get_mem_data(bio, &str);
              if (std::strlen(str) == static_cast<size_t>(len)) ret = str;
            }
          }
          BIO_free(bio);
        }
      }
    }
  }
  return ret;
}

/**
 *  Retrieves the X.509 certificate serial number
 *
 * @param cert     Pointer to a native OpenSSL X509 object containing the
 *                 certificate
 *
 * @return Returns the numeric representation of the certificate serial number
 *         as a std::string.
 */
static std::string x509_get_serial(::X509 *cert) {
  ASN1_INTEGER *asn1_i;
  BIGNUM *bignum;
  char *openssl_serial;

  asn1_i = X509_get_serialNumber(cert);
  bignum = ASN1_INTEGER_to_BN(asn1_i, NULL);
  openssl_serial = BN_bn2dec(bignum);

  const std::string ret = openssl_serial;

  BN_free(bignum);
  OPENSSL_free(openssl_serial);

  return ret;
}

/**
 *  Retrieves the X.509 certificate serial number as hexadecimal
 *
 * @param cert     Pointer to a native OpenSSL X509 object containing the
 *                 certificate
 *
 * @return Returns the hexadecimal representation of the certificate
 *         serial number as a std::string.
 */
static std::string x509_get_serial_hex(::X509 *cert) {
  const ASN1_INTEGER *asn1_i = X509_get_serialNumber(cert);
  return render_hex_sep(asn1_i->data, asn1_i->length, ':', false);
}

/**
 *  Retrieves the X.509 certificate SHA256 fingerprint as binary
 *
 * @param cert     Pointer to a native OpenSSL X509 object containing the
 *                 certificate
 *
 * @return Returns a uint8_t std:vector containing the binary representation
 *         of the certificate's SHA256 fingerprint.
 */
static std::size_t x509_fingerprint_size() { return EVP_MD_size(EVP_sha256()); }
static std::vector<uint8_t> x509_get_fingerprint(const ::X509 *cert)
{
  std::vector<uint8_t> fingerprint;
  fingerprint.resize(x509_fingerprint_size());

  if (::X509_digest(cert, EVP_sha256(), fingerprint.data(), NULL) != 1)
    throw OpenSSLException("OpenSSL error while calling X509_digest()");

  return fingerprint;
}

}  // namespace OpenSSLPKI
}  // namespace openvpn
