/*
 * Copyright 1995-2018 The OpenSSL Project Authors. All Rights Reserved.
 *
 * Licensed under the OpenSSL license (the "License").  You may not use
 * this file except in compliance with the License.  You can obtain a copy
 * in the file LICENSE in the source distribution or at
 * https://www.openssl.org/source/license.html
 */

/* The methods in this file are copied from OpenSSL 1.1.1 source code */

#pragma once

#if OPENSSL_VERSION_NUMBER < 0x10100000L

#include <openssl/bio.h>
#include <openssl/crypto.h>
#include <openssl/rsa.h>
#include <openssl/dsa.h>
#include <openssl/hmac.h>

/* Reimplemented/adjusted for 1.0.2 */

// make sure type 94 doesn't collide with anything in bio.h
// Start with the same number as before

static int lastindex = 94;
inline int BIO_get_new_index(void)
{
  int newval = lastindex|BIO_TYPE_SOURCE_SINK;
  lastindex++;
  return newval;
}

inline BIO_METHOD *BIO_meth_new(int type, const char *name)
{
  BIO_METHOD *biom = new BIO_METHOD();

  if ((biom->name = OPENSSL_strdup(name)) == nullptr) {
      delete biom;
      BIOerr(BIO_F_BIO_NEW, ERR_R_MALLOC_FAILURE);
      return nullptr;
    }
  biom->type = type;
  return biom;
}

inline void BIO_meth_free(BIO_METHOD *biom)
{
  if (biom != nullptr) {
      OPENSSL_free((void *)biom->name);
      delete biom;
    }
}

inline RSA_METHOD *RSA_meth_new(const char *name, int flags)
{
  RSA_METHOD *meth = new RSA_METHOD();

  meth->flags = flags;

  meth->name = name;
  return meth;
}

inline void RSA_meth_free(RSA_METHOD *meth)
{
  delete meth;
}

inline HMAC_CTX *HMAC_CTX_new()
{
  HMAC_CTX* ctx = new HMAC_CTX();
  HMAC_CTX_init(ctx);
  return ctx;
}

inline void HMAC_CTX_free(HMAC_CTX *ctx)
{
  HMAC_CTX_cleanup(ctx);
  delete ctx;
}

inline EVP_CIPHER_CTX *EVP_CIPHER_CTX_new(void)
{
  return new EVP_CIPHER_CTX ();
}

inline void EVP_CIPHER_CTX_free (EVP_CIPHER_CTX* ctx)
{
  EVP_CIPHER_CTX_cleanup(ctx);
  delete ctx;
}

inline EVP_MD_CTX *EVP_MD_CTX_new()
{
  return new EVP_MD_CTX();
}

void EVP_MD_CTX_free(EVP_MD_CTX *ctx)
{
  delete ctx;
}

/* Copied verbatim */
inline void BIO_set_shutdown(BIO *a, int shut)
{
  a->shutdown = shut;
}

inline int BIO_get_shutdown(BIO *a)
{
  return a->shutdown;
}

inline void BIO_set_data(BIO *a, void *ptr)
{
  a->ptr = ptr;
}

inline void *BIO_get_data(BIO *a)
{
  return a->ptr;
}

inline void BIO_set_init(BIO *a, int init)
{
  a->init = init;
}

inline int BIO_get_init(BIO *a)
{
  return a->init;
}

inline int BIO_meth_set_write(BIO_METHOD *biom,
			      int (*bwrite)(BIO *, const char *, int))
{
  biom->bwrite = bwrite;
  return 1;
}

inline int BIO_meth_set_read(BIO_METHOD *biom,
			     int (*bread)(BIO *, char *, int))
{
  biom->bread = bread;
  return 1;
}

inline int BIO_meth_set_puts(BIO_METHOD *biom,
			     int (*bputs)(BIO *, const char *))
{
  biom->bputs = bputs;
  return 1;
}


inline int BIO_meth_set_gets(BIO_METHOD *biom,
			     int (*bgets)(BIO *, char *, int))
{
  biom->bgets = bgets;
  return 1;
}

inline int BIO_meth_set_ctrl(BIO_METHOD *biom,
			     long (*ctrl)(BIO *, int, long, void *))
{
  biom->ctrl = ctrl;
  return 1;
}

inline int BIO_meth_set_create(BIO_METHOD *biom, int (*create)(BIO *))
{
  biom->create = create;
  return 1;
}

inline int BIO_meth_set_destroy(BIO_METHOD *biom, int (*destroy)(BIO *))
{
  biom->destroy = destroy;
  return 1;
}

inline RSA *EVP_PKEY_get0_RSA(EVP_PKEY *pkey)
{
  return pkey->pkey.rsa;
}

inline const BIGNUM *RSA_get0_n(const RSA *r)
{
  return r->n;
}

inline const BIGNUM *RSA_get0_e(const RSA *r)
{
  return r->e;
}

inline int RSA_meth_set_pub_enc(RSA_METHOD *meth,
				int (*pub_enc)(int flen, const unsigned char *from,
					       unsigned char *to, RSA *rsa,
					       int padding))
{
  meth->rsa_pub_enc = pub_enc;
  return 1;
}

inline int RSA_meth_set_pub_dec(RSA_METHOD *meth,
				int (*pub_dec)(int flen, const unsigned char *from,
					       unsigned char *to, RSA *rsa,
					       int padding))
{
  meth->rsa_pub_dec = pub_dec;
  return 1;
}

inline int RSA_meth_set_priv_enc(RSA_METHOD *meth,
				 int (*priv_enc)(int flen, const unsigned char *from,
						 unsigned char *to, RSA *rsa,
						 int padding))
{
  meth->rsa_priv_enc = priv_enc;
  return 1;
}

int RSA_meth_set_priv_dec(RSA_METHOD *meth,
			  int (*priv_dec)(int flen, const unsigned char *from,
					  unsigned char *to, RSA *rsa,
					  int padding))
{
  meth->rsa_priv_dec = priv_dec;
  return 1;
}

inline int RSA_meth_set_init(RSA_METHOD *meth, int (*init)(RSA *rsa))
{
  meth->init = init;
  return 1;
}

inline int RSA_meth_set_finish(RSA_METHOD *meth, int (*finish)(RSA *rsa))
{
  meth->finish = finish;
  return 1;
}

inline int RSA_meth_set0_app_data(RSA_METHOD *meth, void *app_data)
{
  meth->app_data = (char *)app_data;
  return 1;
}

inline void *RSA_meth_get0_app_data(const RSA_METHOD *meth)
{
  return (void *)meth->app_data;
}

inline DSA *EVP_PKEY_get0_DSA(EVP_PKEY *pkey)
{
  return pkey->pkey.dsa;
}

inline const BIGNUM *DSA_get0_p(const DSA *d)
{
  return d->p;
}

inline void RSA_set_flags(RSA *r, int flags)
{
  r->flags |= flags;
}

inline int RSA_set0_key(RSA *r, BIGNUM *n, BIGNUM *e, BIGNUM *d)
{
  /* If the fields n and e in r are NULL, the corresponding input
   * parameters MUST be non-NULL for n and e.  d may be
   * left NULL (in case only the public key is used).
   */
  if ((r->n == NULL && n == NULL)
      || (r->e == NULL && e == NULL))
    return 0;

  if (n != NULL) {
      BN_free(r->n);
      r->n = n;
    }
  if (e != NULL) {
      BN_free(r->e);
      r->e = e;
    }
  if (d != NULL) {
      BN_free(r->d);
      r->d = d;
    }

  return 1;
}

/* Renamed in OpenSSL 1.1 */
#define X509_get0_pubkey X509_get_pubkey
#define RSA_F_RSA_OSSL_PRIVATE_ENCRYPT RSA_F_RSA_EAY_PRIVATE_ENCRYPT
#endif
