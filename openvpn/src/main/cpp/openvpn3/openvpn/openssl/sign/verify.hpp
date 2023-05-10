//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2022 OpenVPN Inc.
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

// Verify a signature using OpenSSL EVP interface

#ifndef OPENVPN_OPENSSL_SIGN_VERIFY_H
#define OPENVPN_OPENSSL_SIGN_VERIFY_H

#include <string>

#include <openssl/ssl.h>
#include <openssl/bio.h>

#include <openvpn/common/cleanup.hpp>
#include <openvpn/common/base64.hpp>
#include <openvpn/openssl/pki/x509.hpp>
#include <openvpn/openssl/util/error.hpp>

#include <openvpn/openssl/compat.hpp>

namespace openvpn {
namespace OpenSSLSign {
/*
 * Verify signature.
 * On success, return.
 * On fail, throw exception.
 */
inline void verify(const OpenSSLPKI::X509 &cert,
                   const std::string &sig,
                   const std::string &data,
                   const std::string &digest)
{
    const EVP_MD *dig;
    EVP_MD_CTX *md_ctx = nullptr;
    EVP_PKEY *pkey = nullptr;

    auto clean = Cleanup([&]()
                         {
	  if (pkey)
	    EVP_PKEY_free(pkey);
	  if (md_ctx)
	    {
#if OPENSSL_VERSION_NUMBER < 0x10100000L
	      EVP_MD_CTX_cleanup(md_ctx);
#endif
	      EVP_MD_CTX_free(md_ctx);
	    } });

    // get digest
    dig = EVP_get_digestbyname(digest.c_str());
    if (!dig)
        throw Exception("OpenSSLSign::verify: unknown digest: " + digest);

    // get public key
    pkey = X509_get_pubkey(cert.obj());
    if (!pkey)
        throw Exception("OpenSSLSign::verify: no public key");

    // convert signature from base64 to binary
    BufferAllocated binsig(1024, 0);
    try
    {
        base64->decode(binsig, sig);
    }
    catch (const std::exception &e)
    {
        throw Exception(std::string("OpenSSLSign::verify: base64 decode error on signature: ") + e.what());
    }

    // initialize digest context
    md_ctx = EVP_MD_CTX_new();

    // verify signature
    EVP_VerifyInit(md_ctx, dig);
    EVP_VerifyUpdate(md_ctx, data.c_str(), data.length());
    if (EVP_VerifyFinal(md_ctx, binsig.c_data(), binsig.length(), pkey) != 1)
        throw OpenSSLException("OpenSSLSign::verify: verification failed");
}
} // namespace OpenSSLSign
} // namespace openvpn

#endif
