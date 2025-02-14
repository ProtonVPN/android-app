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
#if (OPENSSL_VERSION_NUMBER >= 0x30000000L)
#include <openssl/core_names.h>
#include <openssl/params.h>
#endif

#include <openssl/kdf.h>



#include <openvpn/common/numeric_util.hpp>

namespace openvpn::OpenSSLCrypto {

class TLS1PRF
{
  public:
#if OPENSSL_VERSION_NUMBER >= 0x30000000L
    static bool PRF(unsigned char *label,
                    const size_t label_len,
                    const unsigned char *sec,
                    const size_t slen,
                    unsigned char *out1,
                    const size_t olen)

    {
        using EVP_KDF_ptr = std::unique_ptr<EVP_KDF, decltype(&::EVP_KDF_free)>;
        using EVP_KDF_CTX_ptr = std::unique_ptr<EVP_KDF_CTX, decltype(&::EVP_KDF_CTX_free)>;

        EVP_KDF_ptr kdf{::EVP_KDF_fetch(NULL, "TLS1-PRF", NULL), ::EVP_KDF_free};
        if (!kdf)
        {
            return false;
        }

        EVP_KDF_CTX_ptr kctx{::EVP_KDF_CTX_new(kdf.get()), ::EVP_KDF_CTX_free};

        if (!kctx)
        {
            return false;
        }

        OSSL_PARAM params[4];
        params[0] = OSSL_PARAM_construct_utf8_string(OSSL_KDF_PARAM_DIGEST,
                                                     const_cast<char *>(SN_md5_sha1),
                                                     strlen(SN_md5_sha1));
        params[1] = OSSL_PARAM_construct_octet_string(OSSL_KDF_PARAM_SECRET,
                                                      const_cast<unsigned char *>(sec),
                                                      slen);
        params[2] = OSSL_PARAM_construct_octet_string(OSSL_KDF_PARAM_SEED,
                                                      label,
                                                      label_len);
        params[3] = OSSL_PARAM_construct_end();

        if (::EVP_KDF_derive(kctx.get(), out1, olen, params) <= 0)
        {
            return false;
        }

        return true;
    }
#else
    static bool PRF(unsigned char *label,
                    const size_t label_len,
                    const unsigned char *sec,
                    const size_t slen,
                    unsigned char *out1,
                    const size_t olen)
    {
        /* TODO use EVP_PKEY_CTX_new_from_name and library context for OpenSSL 3.0 but
         * this needs passing the library context down here.*/
        using EVP_PKEY_CTX_ptr = std::unique_ptr<EVP_PKEY_CTX, decltype(&::EVP_PKEY_CTX_free)>;

        EVP_PKEY_CTX_ptr pctx(EVP_PKEY_CTX_new_id(EVP_PKEY_TLS1_PRF, NULL), ::EVP_PKEY_CTX_free);

        if (!pctx.get())
            return false;

        if (!EVP_PKEY_derive_init(pctx.get()))
            return false;

        if (!EVP_PKEY_CTX_set_tls1_prf_md(pctx.get(), EVP_md5_sha1()))
            return false;
        if (!is_safe_conversion<int>(slen)
            || !EVP_PKEY_CTX_set1_tls1_prf_secret(pctx.get(), sec, static_cast<int>(slen)))
            return false;

        if (!is_safe_conversion<int>(label_len)
            || !EVP_PKEY_CTX_add1_tls1_prf_seed(pctx.get(), label, static_cast<int>(label_len)))
            return false;

        size_t out_len = olen;
        if (!EVP_PKEY_derive(pctx.get(), out1, &out_len))
            return false;

        if (out_len != olen)
            return false;

        return true;
    }
#endif
};

} // namespace openvpn::OpenSSLCrypto
