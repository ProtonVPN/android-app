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

#pragma once

#include <openssl/rsa.h>
#include <openssl/evp.h>

#include <openssl/ec.h>
#include <openssl/ecdsa.h>

#include <openvpn/pki/epkibase.hpp>
#include <openvpn/ssl/sslapi.hpp>

#include <openvpn/openssl/compat.hpp>

namespace openvpn {
using ssl_external_pki = SSLFactoryAPI::ssl_external_pki;

class ExternalPKIRsaImpl : public ExternalPKIImpl
{
  public:
    ExternalPKIRsaImpl(SSL_CTX *ssl_ctx, ::X509 *cert, ExternalPKIBase *external_pki_arg)
        : external_pki(external_pki_arg), n_errors(0)
    {
        RSA *rsa = nullptr;
        const RSA *pub_rsa = nullptr;
        RSA_METHOD *rsa_meth = nullptr;
        const char *errtext = "";

        /* allocate custom RSA method object */
        rsa_meth = RSA_meth_new("OpenSSLContext::ExternalPKIRsaImpl private key RSA Method",
                                RSA_METHOD_FLAG_NO_CHECK);

        RSA_meth_set_pub_enc(rsa_meth, rsa_pub_enc);
        RSA_meth_set_pub_dec(rsa_meth, rsa_pub_dec);
        RSA_meth_set_priv_enc(rsa_meth, rsa_priv_enc);
        RSA_meth_set_priv_dec(rsa_meth, rsa_priv_dec);
        RSA_meth_set_init(rsa_meth, nullptr);
        RSA_meth_set_finish(rsa_meth, rsa_finish);
        RSA_meth_set0_app_data(rsa_meth, this);


        /* get the public key */
        if (X509_get0_pubkey(cert) == nullptr) /* nullptr before SSL_CTX_use_certificate() is called */
        {
            errtext = "pkey is NULL";
            goto err;
        }

        if (EVP_PKEY_id(X509_get0_pubkey(cert)) != EVP_PKEY_RSA)
        {
            errtext = "pkey is not RSA";
            goto err;
        }
        pub_rsa = EVP_PKEY_get0_RSA(X509_get0_pubkey(cert));

        /* allocate RSA object */
        rsa = RSA_new();
        if (rsa == nullptr)
        {
            SSLerr(SSL_F_SSL_USE_PRIVATEKEY, ERR_R_MALLOC_FAILURE);
            errtext = "RSA_new";
            goto err;
        }

        /* only set e and n as d (private key) is outside our control */
        RSA_set0_key(rsa, BN_dup(RSA_get0_n(pub_rsa)), BN_dup(RSA_get0_e(pub_rsa)), nullptr);
        RSA_set_flags(rsa, RSA_FLAG_EXT_PKEY);

        if (!RSA_set_method(rsa, rsa_meth))
        {
            errtext = "RSA_set_method";
            goto err;
        }
        /* rsa_meth will be freed when rsa is freed from this point,
         * set pointer to nullptr so the err does not try to free it
         */
        rsa_meth = nullptr;

        /* bind our custom RSA object to ssl_ctx */
        if (!SSL_CTX_use_RSAPrivateKey(ssl_ctx, rsa))
        {
            errtext = "SSL_CTX_use_RSAPrivateKey";
            goto err;
        }

        RSA_free(rsa); /* doesn't necessarily free, just decrements refcount */
        return;

    err:
        RSA_free(rsa);
        RSA_meth_free(rsa_meth);

        OPENVPN_THROW(OpenSSLException, "OpenSSLContext::ExternalPKIRsaImpl: " << errtext);
    }

    ~ExternalPKIRsaImpl() override = default;

    unsigned int get_n_errors() const
    {
        return n_errors;
    }

  private:
    /* called at RSA_free */
    static int rsa_finish(RSA *rsa)
    {
        RSA_meth_free(const_cast<RSA_METHOD *>(RSA_get_method(rsa)));
        return 1;
    }

    /* sign arbitrary data */
    static int
    rsa_priv_enc(int flen, const unsigned char *from, unsigned char *to, RSA *rsa, int padding)
    {
        ExternalPKIRsaImpl *self = (ExternalPKIRsaImpl *)(RSA_meth_get0_app_data(RSA_get_method(rsa)));

        try
        {
            if (padding != RSA_PKCS1_PADDING && padding != RSA_NO_PADDING)
            {
                RSAerr(RSA_F_RSA_OSSL_PRIVATE_ENCRYPT, RSA_R_UNKNOWN_PADDING_TYPE);
                throw ssl_external_pki("OpenSSL: bad padding type");
            }
            std::string padding_algo;
            if (padding == RSA_PKCS1_PADDING)
            {
                padding_algo = "RSA_PKCS1_PADDING";
            }
            else if (padding == RSA_NO_PADDING)
            {
                padding_algo = "RSA_NO_PADDING";
            }

            /* convert 'from' to base64 */
            ConstBuffer from_buf(from, flen, true);
            const std::string from_b64 = base64->encode(from_buf);

            /* get signature */
            std::string sig_b64;
            const bool status = self->external_pki->sign(from_b64, sig_b64, padding_algo, "", "");
            if (!status)
                throw ssl_external_pki("OpenSSL: could not obtain signature");

            /* decode base64 signature to binary */
            const int len = RSA_size(rsa);
            Buffer sig(to, len, false);
            base64->decode(sig, sig_b64);

            /* verify length */
            if (sig.size() != static_cast<size_t>(len))
                throw ssl_external_pki("OpenSSL: incorrect signature length");

            /* return length of signature */
            return len;
        }
        catch (const std::exception &e)
        {
            OPENVPN_LOG("OpenSSLContext::ExternalPKIRsaImpl::rsa_priv_enc exception: " << e.what());
            ++self->n_errors;
            return -1;
        }
    }

    static void not_implemented(RSA *rsa)
    {
        ExternalPKIRsaImpl *self = (ExternalPKIRsaImpl *)(RSA_meth_get0_app_data(RSA_get_method(rsa)));
        ++self->n_errors;
    }

    /* encrypt */
    static int
    rsa_pub_enc(int flen, const unsigned char *from, unsigned char *to, RSA *rsa, int padding)
    {
        not_implemented(rsa);
        return -1;
    }

    /* verify arbitrary data */
    static int
    rsa_pub_dec(int flen, const unsigned char *from, unsigned char *to, RSA *rsa, int padding)
    {
        not_implemented(rsa);
        return -1;
    }

    /* decrypt */
    static int
    rsa_priv_dec(int flen, const unsigned char *from, unsigned char *to, RSA *rsa, int padding)
    {
        not_implemented(rsa);
        return -1;
    }

    ExternalPKIBase *external_pki;
    unsigned int n_errors;
};

/* The OpenSSL EC_* methods we are using here are only available for OpenSSL 1.1.0 and later */
#if OPENSSL_VERSION_NUMBER >= 0x10100000L && !defined(OPENSSL_NO_EC)
class ExternalPKIECImpl : public ExternalPKIImpl
{

  public:
    ExternalPKIECImpl(SSL_CTX *ssl_ctx, ::X509 *cert, ExternalPKIBase *external_pki_arg)
        : external_pki(external_pki_arg)
    {

        if (ec_self_data_index < 0)
            throw ssl_external_pki("ExternalPKIECImpl::ec_self_data_index is uninitialized");

        std::string errtext;

        EVP_PKEY *privkey = nullptr;
        EC_KEY *ec = nullptr;
        EC_KEY_METHOD *ec_method = EC_KEY_METHOD_new(EC_KEY_OpenSSL());

        /* we only need to override a small number of methods */
        EC_KEY_METHOD_set_init(ec_method, NULL, ec_finish, NULL, NULL, NULL, NULL);
        EC_KEY_METHOD_set_sign(ec_method, ecdsa_sign, ecdsa_sign_setup, ecdsa_sign_sig);

        /* get the public key */
        EVP_PKEY *pubkey = X509_get0_pubkey(cert);

        if (pubkey == nullptr) /* nullptr before SSL_CTX_use_certificate() is called */
        {
            errtext = "public key is NULL";
            goto err;
        }

        if (EVP_PKEY_id(pubkey) != EVP_PKEY_EC)
        {
            errtext = "public key is not EC";
            goto err;
        }

        ec = EVP_PKEY_get1_EC_KEY(pubkey);

        if (!ec)
        {
            errtext = "cannot get public EC key";
            goto err;
        }

        /* This will move responsibility to free ec_method to ec */
        if (!EC_KEY_set_method(ec, ec_method))
        {
            errtext = "Could not set EC method";
            EC_KEY_METHOD_free(ec_method);
            goto err;
        }

        if (!EC_KEY_set_ex_data(ec, ec_self_data_index, this))
        {
            errtext = "Could not set EC Key ex data";
            EC_KEY_METHOD_free(ec_method);
            goto err;
        }

        privkey = EVP_PKEY_new();
        if (!EVP_PKEY_assign_EC_KEY(privkey, ec))
        {
            errtext = "assigning EC key methods failed";
            goto err;
        }

        if (!SSL_CTX_use_PrivateKey(ssl_ctx, privkey))
        {
            errtext = "assigning EC private key to SSL context failed";
            goto err;
        }

        EVP_PKEY_free(privkey); /* release ref to privkey and ec */

        return;

    err:
        if (privkey)
        {
            EVP_PKEY_free(privkey);
        }
        else
        {
            EC_KEY_free(ec);
        }
        OPENVPN_THROW(OpenSSLException, "OpenSSLContext::ExternalPKIECImpl: " << errtext);
    }

    ~ExternalPKIECImpl() override = default;

    static void init_static()
    {
        ec_self_data_index = EC_KEY_get_ex_new_index(0, (char *)"ExternalPKIECImpl", nullptr, nullptr, nullptr);
    }

  private:
    static void ec_finish(EC_KEY *ec)
    {
        EC_KEY_METHOD_free(const_cast<EC_KEY_METHOD *>(EC_KEY_get_method(ec)));
    }

    /* sign arbitrary data */
    static int ecdsa_sign(int type,
                          const unsigned char *dgst,
                          int dlen,
                          unsigned char *sig,
                          unsigned int *siglen,
                          const BIGNUM *kinv,
                          const BIGNUM *r,
                          EC_KEY *eckey)
    {
        ExternalPKIECImpl *self = (ExternalPKIECImpl *)(EC_KEY_get_ex_data(eckey, ec_self_data_index));

        try
        {
            *siglen = ECDSA_size(eckey);
            self->do_sign(dgst, dlen, sig, *siglen);
            /* No error */
            return 1;
        }
        catch (const std::exception &e)
        {
            OPENVPN_LOG("OpenSSLContext::ExternalPKIECImpl::ecdsa_sign exception: " << e.what());
            return 0;
        }
    }

    static int ecdsa_sign_setup(EC_KEY *eckey, BN_CTX *ctx_in, BIGNUM **kinvp, BIGNUM **rp)
    {
        /* No precomputation, return success */
        return 1;
    }

    static ECDSA_SIG *
    ecdsa_sign_sig(const unsigned char *dgst, int dgstlen, const BIGNUM *kinvp, const BIGNUM *rp, EC_KEY *eckey)
    {
        ExternalPKIECImpl *self = (ExternalPKIECImpl *)(EC_KEY_get_ex_data(eckey, ec_self_data_index));

        unsigned len = ECDSA_size(eckey);

        auto sig = new unsigned char[len];

        ECDSA_SIG *ecsig = nullptr;
        try
        {
            unsigned int siglen = len;
            self->do_sign(dgst, dgstlen, sig, siglen);

            ecsig = d2i_ECDSA_SIG(NULL, (const unsigned char **)&sig, siglen);
        }
        catch (const std::exception &e)
        {
            OPENVPN_LOG("OpenSSLContext::ExternalPKIECImpl::ecdsa_sign_sig exception: " << e.what());
        }

        delete[] sig;
        return ecsig;
    }

    /**
     * Sign the input via external pki callback
     *
     * @param dgst 	digest to be signed
     * @param dlen 	length of the digest to be signed
     * @param sig 	buffer backing the signature
     * @param siglen 	maximum size for the signature, and length of the signature
     * 	   		returned in sig
     * @return
     */
    void do_sign(const unsigned char *dgst, int dlen, unsigned char *sig, unsigned int &siglen)
    {
        /* convert 'dgst' to base64 */
        ConstBuffer dgst_buf(dgst, dlen, true);
        const std::string dgst_b64 = base64->encode(dgst_buf);

        /* get signature */
        std::string sig_b64;
        const bool status = external_pki->sign(dgst_b64, sig_b64, "ECDSA", "", "");
        if (!status)
            throw ssl_external_pki("OpenSSL: could not obtain signature");

        /* decode base64 signature to binary */
        Buffer sigout(sig, siglen, false);
        base64->decode(sigout, sig_b64);

        siglen = sigout.size();
    }

    ExternalPKIBase *external_pki;
    static int ec_self_data_index;
};

#ifdef OPENVPN_NO_EXTERN
int ExternalPKIECImpl::ec_self_data_index = -1;
#endif
#endif /* OPENSSL_VERSION_NUMBER >= 0x10100000L && !defined(OPENSSL_NO_EC) */
} // namespace openvpn
