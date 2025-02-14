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


#pragma once

#if OPENSSL_VERSION_NUMBER < 0x30000000L
#include <cassert>
#include <cstring>
#include <openssl/evp.h>
#include <openssl/ec.h>
#include <openssl/objects.h>


/* Note that this is not a perfect emulation of the new function but
 * is good enough for our case of printing certificate details during
 * handshake */
static inline int EVP_PKEY_get_group_name(EVP_PKEY *pkey,
                                          char *gname,
                                          size_t gname_sz,
                                          size_t *gname_len)
{
    if (EVP_PKEY_get0_EC_KEY(pkey) == nullptr)
    {
        return 0;
    }
    const EC_KEY *ec = EVP_PKEY_get0_EC_KEY(pkey);
    const EC_GROUP *group = EC_KEY_get0_group(ec);

    int nid = EC_GROUP_get_curve_name(group);

    if (nid == NID_undef)
    {
        return 0;
    }
    const char *curve = OBJ_nid2sn(nid);

    std::strncpy(gname, curve, gname_sz - 1);
    *gname_len = std::strlen(curve);
    return 1;
}

/* Mimics the function but only when the default context without
 * options is chosen */
static inline const EVP_CIPHER *
EVP_CIPHER_fetch(void *ctx, const char *algorithm, const char *properties)
{
    assert(!ctx);
    assert(!properties);
    const EVP_CIPHER *cipher = EVP_get_cipherbyname(algorithm);
#ifdef OPENSSL_FIPS
    /* Rhel 8/CentOS 8 have a patched OpenSSL version that return a cipher
     * here that is actually not usable if in FIPS mode */

    if (FIPS_mode() && !(EVP_CIPHER_flags(cipher) & EVP_CIPH_FLAG_FIPS))
    {
        return nullptr;
    }
#endif
    return cipher;
}

static inline EVP_PKEY *
PEM_read_bio_PrivateKey_ex(BIO *bp,
                           EVP_PKEY **x,
                           pem_password_cb *cb,
                           void *u,
                           void *libctx,
                           const char *propq)
{
    return PEM_read_bio_PrivateKey(bp, x, cb, u);
}

static inline void
EVP_CIPHER_free(const EVP_CIPHER *cipher)
{
    /* OpenSSL 1.1.1 and lower have no concept of dynamic EVP_CIPHER, so this is
     * a noop */
}

static inline SSL_CTX *
SSL_CTX_new_ex(void *libctx, const char *propq, const SSL_METHOD *meth)
{
    return SSL_CTX_new(meth);
}

static inline void
OSSL_LIB_CTX_free(void *libctx)
{
}
#define EVP_PKEY_get_bits EVP_PKEY_bits

static inline const EVP_MD *
EVP_MD_fetch(void *, const char *algorithm, const char *)
{
    return EVP_get_digestbyname(algorithm);
}

static inline void
EVP_MD_free(const EVP_MD *md)
{
    /* OpenSSL 1.1.1 and lower use only const EVP_CIPHER, nothing to free */
}

#endif
