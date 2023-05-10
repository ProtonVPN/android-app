
//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2022 OpenVPN Inc.
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

#pragma once

#include <openssl/evp.h>
#include <openssl/provider.h>

#include <openvpn/pki/epkibase.hpp>
#include <openvpn/ssl/sslapi.hpp>

#include <openvpn/openssl/compat.hpp>

#include <openvpn/openssl/xkey/xkey_common.h>

namespace openvpn {
class XKeyExternalPKIImpl : public ExternalPKIImpl
{
  private:
    OSSL_LIB_CTX *tls_libctx = nullptr;
    ExternalPKIBase *external_pki;

    static void
    xkey_logging_callback(const char *message, bool debug)
    {
        if (!debug)
            OPENVPN_LOG(message);
    }

    static int
    provider_load(OSSL_PROVIDER *prov, void *dest_libctx)
    {
        const char *name = OSSL_PROVIDER_get0_name(prov);
        OSSL_PROVIDER_load(static_cast<OSSL_LIB_CTX *>(dest_libctx), name);
        return 1;
    }

    static int
    provider_unload(OSSL_PROVIDER *prov, [[maybe_unused]] void *unused)
    {
        OSSL_PROVIDER_unload(prov);
        return 1;
    }

    void load_xkey_provider()
    {
        /* setup logging first to be able to see error while loading the provider */
        xkey_set_logging_cb_function(xkey_logging_callback);

        /* Make a new library context for use in TLS context */
        if (!tls_libctx)
        {
            tls_libctx = OSSL_LIB_CTX_new();
            if (!tls_libctx)
                OPENVPN_THROW(OpenSSLException, "OpenSSLContext::ExternalPKIImpl: OSSL_LIB_CTX_new");

            /* Load all providers in default LIBCTX into this libctx.
             * OpenSSL has a child libctx functionality to automate this,
             * but currently that is usable only from within providers.
             * So we do something close to it manually here.
             */
            OSSL_PROVIDER_do_all(nullptr, provider_load, tls_libctx);
        }

        if (!OSSL_PROVIDER_available(tls_libctx, "ovpn.xkey"))
        {
            OSSL_PROVIDER_add_builtin(tls_libctx, "ovpn.xkey", xkey_provider_init);
            if (!OSSL_PROVIDER_load(tls_libctx, "ovpn.xkey"))
            {
                OPENVPN_THROW(OpenSSLException, "OpenSSLContext::ExternalPKIImpl: "
                                                    << "failed loading external key provider: "
                                                       "Signing with external keys will not work.");
            }
        }

        /* We only implement minimal functionality in ovpn.xkey, so we do not want
         * methods in xkey to be picked unless absolutely required (i.e, when the key
         * is external). Ensure this by setting a default propquery for the custom
         * libctx that unprefers, but does not forbid, ovpn.xkey. See also man page
         * of "property" in OpenSSL 3.0.
         */
        EVP_set_default_properties(tls_libctx, "?provider!=ovpn.xkey");
    }

    void unload_xkey_provider()
    {
        if (tls_libctx)
        {
            OSSL_PROVIDER_do_all(tls_libctx, provider_unload, nullptr);
            OSSL_LIB_CTX_free(tls_libctx);
        }
        tls_libctx = nullptr;
    }

    EVP_PKEY *
    tls_ctx_use_external_key(::SSL_CTX *ctx, ::X509 *cert)
    {
        if (cert == nullptr)
            OPENVPN_THROW(OpenSSLException, "OpenSSLContext::ExternalPKIImpl: pubcert undefined");

        /* get the public key */
        EVP_PKEY *pkey = X509_get0_pubkey(cert);
        if (!pkey)
            OPENVPN_THROW(OpenSSLException, "OpenSSLContext::ExternalPKIImpl: X509_get0_pubkey");

        EVP_PKEY *privkey = xkey_load_generic_key(tls_libctx, this, pkey, xkey_sign_cb, nullptr);
        if (!privkey
            || !SSL_CTX_use_PrivateKey(ctx, privkey))
        {
            EVP_PKEY_free(privkey);
            return nullptr;
        }

        return privkey;
    }

  public:
    XKeyExternalPKIImpl(SSL_CTX *ssl_ctx, ::X509 *cert, ExternalPKIBase *external_pki)
        : external_pki(external_pki)
    {

        /* Ensure provider is loaded */
        load_xkey_provider();

        /* Set public key/certificate */
        ::EVP_PKEY *privkey = tls_ctx_use_external_key(ssl_ctx, cert);

        if (!privkey)
        {
            OPENVPN_THROW(OpenSSLException, "OpenSSLContext::ExternalPKIImpl: "
                                                << "SSL_CTX_use_PrivateKey");
        }

        EVP_PKEY_free(privkey);
    }

    virtual ~XKeyExternalPKIImpl()
    {
        unload_xkey_provider();
    }

    static int xkey_sign_cb(void *this_ptr,
                            unsigned char *sig,
                            size_t *siglen,
                            const unsigned char *tbs,
                            size_t tbslen,
                            XKEY_SIGALG alg)
    {
        return static_cast<XKeyExternalPKIImpl *>(this_ptr)->xkey_sign(sig, siglen, tbs, tbslen, alg);
    }

    /**
     * Signature callback for xkey_provider
     *
     * @param sig           On successful return signature is in sig.
     * @param siglen        On entry *siglen has length of buffer sig,
     *                      on successful return size of signature
     * @param tbs           hash or message to be signed
     * @param tbslen        len of data in dgst
     * @param sigalg        extra signature parameters
     *
     * @return              signature length or -1 on error.
     */
    int
    xkey_sign(unsigned char *sig, size_t *siglen, const unsigned char *tbs, size_t tbslen, XKEY_SIGALG alg)
    {
        std::string algstr;
        std::string hashalg;
        std::string saltlen;

        unsigned char enc[EVP_MAX_MD_SIZE + 32]; /* 32 bytes enough for digest info structure */
        size_t enc_len = sizeof(enc);

        if (!strcmp(alg.keytype, "ED448") || !strcmp(alg.keytype, "ED25519"))
        {
            algstr = alg.keytype;
            hashalg = alg.mdname;
        }
        else if (!strcmp(alg.keytype, "EC"))
        {
            algstr = "ECDSA";
            if (strcmp(alg.op, "Sign"))
            {
                hashalg = alg.mdname;
            }
        }
        else if (!strcmp(alg.padmode, "pkcs1"))
        {
            /* assume RSA key */
            algstr = "RSA_PKCS1_PADDING";
            /* For Sign, interface expects a pkcs1 encoded digest -- add it */
            if (!strcmp(alg.op, "Sign"))
            {
                if (!xkey_encode_pkcs1(enc, &enc_len, alg.mdname, tbs, tbslen))
                {
                    return 0;
                }
                tbs = enc;
                tbslen = enc_len;
            }
            else
            {
                /* For undigested message, add hashalg=digest parameter */
                hashalg = alg.mdname;
            }
        }
        else if (!strcmp(alg.padmode, "none") && !strcmp(alg.op, "Sign"))
        {
            /* NO_PADDING requires digested data */
            algstr = "RSA_NO_PADDING";
        }
        else if (!strcmp(alg.padmode, "pss"))
        {
            algstr = "RSA_PKCS1_PSS_PADDING";
            hashalg = alg.mdname;
            saltlen = alg.saltlen;
        }
        else
        {
            OPENVPN_LOG("RSA padding mode not supported by external key " << alg.padmode);
            return 0;
        }

        /* convert 'tbs' to base64 */
        ConstBuffer from_buf(tbs, tbslen, true);
        const std::string from_b64 = base64->encode(from_buf);

        std::string sig_b64;
        external_pki->sign(from_b64, sig_b64, algstr, hashalg, saltlen);

        Buffer sigbuf(static_cast<void *>(sig), *siglen, false);
        base64->decode(sigbuf, sig_b64);
        *siglen = sigbuf.size();

        return (int)*siglen;
    }
};

}; // namespace openvpn