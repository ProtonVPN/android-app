//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2022 OpenVPN Inc.
//    Copyright (C) 2021-2022 Selva Nair <selva.nair@gmail.com>
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


#include "xkey_common.h"
#include "xkey_msg_compat.h"

#ifdef HAVE_XKEY_PROVIDER

#include <openssl/provider.h>
#include <openssl/params.h>
#include <openssl/core_dispatch.h>
#include <openssl/core_object.h>
#include <openssl/core_names.h>
#include <openssl/store.h>
#include <openssl/evp.h>
#include <openssl/err.h>
#include <assert.h>
#define ASSERT assert

static const char *const props = XKEY_PROV_PROPS;

static XKEY_LOGGING_CALLBACK_fn *xkey_log_callback;

static void
print_openssl_errors(void)
{
    unsigned long e;
    while ((e = ERR_get_error()))
    {
        msg(M_WARN, "OpenSSL error %lu: %s\n", e, ERR_error_string(e, NULL));
    }
}

/**
 * Load a generic key into the xkey provider.
 * Returns an EVP_PKEY object attached to xkey provider.
 * Caller must free it when no longer needed.
 */
EVP_PKEY *
xkey_load_generic_key(OSSL_LIB_CTX *libctx, void *handle, EVP_PKEY *pubkey,
                      XKEY_EXTERNAL_SIGN_fn *sign_op, XKEY_PRIVKEY_FREE_fn *free_op)
{
    EVP_PKEY *pkey = NULL;
    const char *origin = "external";

    /* UTF8 string pointers in here are only read from, so cast is safe */
    OSSL_PARAM params[] = {
        {"xkey-origin", OSSL_PARAM_UTF8_STRING, (char *) origin, 0, 0},
        {"pubkey", OSSL_PARAM_OCTET_STRING, &pubkey, sizeof(pubkey), 0},
        {"handle", OSSL_PARAM_OCTET_PTR, &handle, sizeof(handle), 0},
        {"sign_op", OSSL_PARAM_OCTET_PTR, (void **) &sign_op, sizeof(sign_op), 0},
        {"free_op", OSSL_PARAM_OCTET_PTR, (void **) &free_op, sizeof(free_op), 0},
        {NULL, 0, NULL, 0, 0}
    };

    /* Do not use EVP_PKEY_new_from_pkey as that will take keymgmt from pubkey */
    EVP_PKEY_CTX *ctx = EVP_PKEY_CTX_new_from_name(libctx, EVP_PKEY_get0_type_name(pubkey), props);
    if (!ctx
        || EVP_PKEY_fromdata_init(ctx) != 1
        || EVP_PKEY_fromdata(ctx, &pkey, EVP_PKEY_KEYPAIR, params) != 1)
    {
        print_openssl_errors();
        msg(M_WARN, "OpenSSL error: failed to load key into ovpn.xkey provider");
		pkey = NULL;
    }
    if (ctx)
    {
        EVP_PKEY_CTX_free(ctx);
    }

    return pkey;
}

/**
 * Add PKCS1 DigestInfo to tbs and return the result in *enc.
 *
 * @param enc           pointer to output buffer
 * @param enc_len       capacity in bytes of output buffer
 * @param mdname        name of the hash algorithm (SHA256, SHA1 etc.)
 * @param tbs           pointer to digest to be encoded
 * @param tbslen        length of data in bytes
 *
 * @return              false on error, true  on success
 *
 * On return enc_len is  set to actual size of the result.
 * enc is NULL or enc_len is not enough to store the result, it is set
 * to the required size and false is returned.
 */
bool
xkey_encode_pkcs1(unsigned char *enc, size_t *enc_len, const char *mdname,
             	  const unsigned char *tbs, size_t tbslen)
{
    ASSERT(enc_len != NULL);
    ASSERT(tbs != NULL);

    /* Tabulate the digest info header for expected hash algorithms
     * These were pre-computed using the DigestInfo definition:
     * DigestInfo ::= SEQUENCE {
     *    digestAlgorithm DigestAlgorithmIdentifier,
     *    digest Digest }
     * Also see the table in RFC 8017 section 9.2, Note 1.
     */

    const unsigned char sha1[] = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b,
                                  0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};
    const unsigned char sha256[] = {0x30, 0x31, 0x30, 0x0d, 0x06, 0x09, 0x60, 0x86, 0x48,
                                    0x01, 0x65, 0x03, 0x04, 0x02, 0x01, 0x05, 0x00, 0x04, 0x20};
    const unsigned char sha384[] = {0x30, 0x41, 0x30, 0x0d, 0x06, 0x09, 0x60, 0x86, 0x48,
                                    0x01, 0x65, 0x03, 0x04, 0x02, 0x02, 0x05, 0x00, 0x04, 0x30};
    const unsigned char sha512[] = {0x30, 0x51, 0x30, 0x0d, 0x06, 0x09, 0x60, 0x86, 0x48,
                                    0x01, 0x65, 0x03, 0x04, 0x02, 0x03, 0x05, 0x00, 0x04, 0x40};
    const unsigned char sha224[] = {0x30, 0x2d, 0x30, 0x0d, 0x06, 0x09, 0x60, 0x86, 0x48,
                                    0x01, 0x65, 0x03, 0x04, 0x02, 0x04, 0x05, 0x00, 0x04, 0x1c};
    const unsigned char sha512_224[] = {0x30, 0x2d, 0x30, 0x0d, 0x06, 0x09, 0x60, 0x86, 0x48,
                                        0x01, 0x65, 0x03, 0x04, 0x02, 0x05, 0x05, 0x00, 0x04, 0x1c};
    const unsigned char sha512_256[] = {0x30, 0x31, 0x30, 0x0d, 0x06, 0x09, 0x60, 0x86, 0x48,
                                        0x01, 0x65, 0x03, 0x04, 0x02, 0x06, 0x05, 0x00, 0x04, 0x20};

    typedef struct {
        const int nid;
        const unsigned char *header;
        size_t sz;
    } DIG_INFO;

#define MAKE_DI(x) {NID_ ## x, x, sizeof(x)}

    DIG_INFO dinfo[] = {MAKE_DI(sha1), MAKE_DI(sha256), MAKE_DI(sha384),
                        MAKE_DI(sha512), MAKE_DI(sha224), MAKE_DI(sha512_224),
                        MAKE_DI(sha512_256), {0,NULL,0}};

    int out_len = 0;
    int ret = 0;

    int nid = OBJ_sn2nid(mdname);
    if (nid == NID_undef)
    {
        /* try harder  -- name variants like SHA2-256 doesn't work */
        nid = EVP_MD_type(EVP_get_digestbyname(mdname));
        if (nid == NID_undef)
        {
            msg(M_WARN, "Error: encode_pkcs11: invalid digest name <%s>", mdname);
            goto done;
        }
    }

    if (tbslen != EVP_MD_size(EVP_get_digestbyname(mdname)))
    {
        msg(M_WARN, "Error: encode_pkcs11: invalid input length <%d>", (int)tbslen);
        goto done;
    }

    if (nid == NID_md5_sha1) /* no encoding needed -- just copy */
    {
        if (enc && (*enc_len >= tbslen))
        {
            memcpy(enc, tbs, tbslen);
            ret = true;
        }
        out_len = tbslen;
        goto done;
    }

    /* locate entry for nid in dinfo table */
    DIG_INFO *di = dinfo;
    while ((di->nid != nid) && (di->nid != 0))
    {
        di++;
    }
    if (di->nid != nid) /* not found in our table */
    {
        msg(M_WARN, "Error: encode_pkcs11: unsupported hash algorithm <%s>", mdname);
        goto done;
    }

    out_len = tbslen + di->sz;

    if (enc && (out_len <= (int) *enc_len))
    {
        /* combine header and digest */
        memcpy(enc, di->header, di->sz);
        memcpy(enc + di->sz, tbs, tbslen);
        dmsg(D_XKEY, "encode_pkcs1: digest length = %d encoded length = %d",
             (int) tbslen, (int) out_len);
        ret = true;
    }

done:
    *enc_len = out_len; /* assignment safe as out_len is > 0 at this point */

    return ret;
}

void xkey_set_logging_cb_function(XKEY_LOGGING_CALLBACK_fn logfunc)
{
  xkey_log_callback = logfunc;
}


void
openvpn_msg_xkey_compat(const unsigned int flags, const char *format, ...) {
  va_list arglist;
  va_start(arglist, format);

  char msgbuf[4096] = { 0 };

  vsnprintf(msgbuf, sizeof(msgbuf), format, arglist);

  /* Do not print debug messages from the xkey provider */
  bool debug = (flags & D_XKEY) == 0;
  if (debug && xkey_log_callback != NULL)
  {
	  xkey_log_callback(msgbuf, debug);
  }
  va_end(arglist);
}

#endif /* HAVE_XKEY_PROVIDER */
