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

// Verify a signature using OpenSSL EVP interface

#ifndef OPENVPN_OPENSSL_SIGN_VERIFY_H
#define OPENVPN_OPENSSL_SIGN_VERIFY_H

#include <string>
#include <list>

#include <openssl/ssl.h>
#include <openssl/bio.h>

#include <openvpn/common/cleanup.hpp>
#include <openvpn/common/numeric_cast.hpp>
#include <openvpn/common/base64.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/openssl/pki/x509.hpp>
#include <openvpn/openssl/util/error.hpp>

#include <openvpn/openssl/compat.hpp>

namespace openvpn::OpenSSLSign {
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
    BufferAllocated binsig(1024);
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
    if (EVP_VerifyFinal(md_ctx, binsig.c_data(), numeric_cast<unsigned int>(binsig.length()), pkey) != 1)
        throw OpenSSLException("OpenSSLSign::verify: verification failed");
}
} // namespace openvpn::OpenSSLSign

#endif
