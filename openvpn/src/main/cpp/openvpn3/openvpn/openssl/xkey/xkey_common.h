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

#ifndef XKEY_COMMON_H_
#define XKEY_COMMON_H_

#if defined (__cplusplus)
extern "C" {
#endif

/* Guard to only enable if OpenSSL is used and not trigger an error if mbed
 * TLS is compiled without OpenSSL being installed */
#if defined(USE_OPENSSL)
#include <openssl/opensslv.h>
#if OPENSSL_VERSION_NUMBER >= 0x30000010L && !defined(DISABLE_XKEY_PROVIDER)
#define HAVE_XKEY_PROVIDER 1

#include <stdbool.h>
#include <openssl/provider.h>
#include <openssl/core_dispatch.h>

/**
 * Initialization function for OpenVPN external key provider for OpenSSL
 * Follows the function signature of OSSL_PROVIDER init()
 */
OSSL_provider_init_fn xkey_provider_init;



#define XKEY_PROV_PROPS "provider=ovpn.xkey"

/**
 * Struct to encapsulate signature algorithm parameters to pass
 * to sign operation.
 */
typedef struct {
    const char *padmode; /**< "pkcs1", "pss" or "none" */
    const char *mdname; /**< "SHA256" or "SHA2-256" etc. */
    const char *saltlen; /**< "digest", "auto" or "max" */
    const char *keytype; /**< "EC" or "RSA" */
    const char *op;     /**< "Sign" or "DigestSign" */
} XKEY_SIGALG;

/**
 * Callback for sign operation -- must be implemented for each backend and
 * is used in xkey_signature_sign(), or set when loading the key.
 * (custom key loading not yet implemented).
 *
 * @param handle opaque key handle provided by the backend -- could be null
 *               or unused for management interface.
 * @param sig    On return caller should fill this with the signature
 * @param siglen On entry *siglen has max size of sig and on return must be
 *               set to the actual size of the signature
 * @param tbs    buffer to sign
 * @param tbslen size of data in tbs buffer
 * @sigalg       contains the signature algorithm parameters
 *
 * @returns 1 on success, 0 on error.
 *
 * If sigalg.op = "Sign", the data in tbs is the digest. If sigalg.op = "DigestSign"
 * it is the message that the backend should hash wih appropriate hash algorithm before
 * signing. In the former case no DigestInfo header is added to tbs. This is
 * unlike the deprecated RSA_sign callback which provides encoded digest.
 * For RSA_PKCS1 signatures, the external signing function must encode the digest
 * before signing. The digest algorithm used (or to be used) is passed in the sigalg
 * structure.
 */
typedef int (XKEY_EXTERNAL_SIGN_fn)(void *handle, unsigned char *sig, size_t *siglen,
				    const unsigned char *tbs, size_t tbslen,
				    XKEY_SIGALG sigalg);
/**
 * Signature of private key free function callback used
 * to free the opaque private key handle obtained from the
 * backend. Not required for management-external-key.
 */
typedef void (XKEY_PRIVKEY_FREE_fn)(void *handle);


/**
 * Load a generic key into the xkey provider.
 * Returns an EVP_PKEY object attached to xkey provider.
 * Caller must free it when no longer needed.
 */
EVP_PKEY *
xkey_load_generic_key(OSSL_LIB_CTX *libctx, void *handle, EVP_PKEY *pubkey,
					  XKEY_EXTERNAL_SIGN_fn *sign_op, XKEY_PRIVKEY_FREE_fn *free_op);

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
				  const unsigned char *tbs, size_t tbslen);

/** Prototype for the logging callback is used to provided logging output from
 * the xkey provider */
typedef void (XKEY_LOGGING_CALLBACK_fn)(const char *msg, bool debug);


/**
 * Sets the function the xkey provider should call when logging. Use NULL
 * to disable logging again.
 * @param logfunc
 */
void xkey_set_logging_cb_function(XKEY_LOGGING_CALLBACK_fn logfunc);


#endif /* HAVE_XKEY_PROVIDER */

#endif /* USE_OPENSSL */

#if defined (__cplusplus)
}
#endif
#endif /* XKEY_COMMON_H_ */
