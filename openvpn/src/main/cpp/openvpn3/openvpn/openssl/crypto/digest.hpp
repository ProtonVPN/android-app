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

// Wrap the OpenSSL digest API defined in <openssl/evp.h>
// so that it can be used as part of the crypto layer of the OpenVPN core.

#ifndef OPENVPN_OPENSSL_CRYPTO_DIGEST_H
#define OPENVPN_OPENSSL_CRYPTO_DIGEST_H

#include <string>

#include <openssl/objects.h>
#include <openssl/evp.h>
#include <openssl/md4.h>
#include <openssl/md5.h>
#include <openssl/sha.h>
#include <openssl/hmac.h>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/crypto/cryptoalgs.hpp>
#include <openvpn/openssl/util/error.hpp>

#include <openvpn/openssl/compat.hpp>

namespace openvpn::OpenSSLCrypto {
class HMACContext;

class DigestContext
{
    DigestContext(const DigestContext &) = delete;
    DigestContext &operator=(const DigestContext &) = delete;

    /* In OpenSSL 3.0 the method that returns EVP_MD, the cipher needs to be
     * freed afterwards, thus needing a non-const type. In contrast, OpenSSL 1.1.1
     * and lower returns a const type, needing a const type */
#if OPENSSL_VERSION_NUMBER < 0x30000000L
    using evp_md_type = const EVP_MD;
#else
    using evp_md_type = EVP_MD;
#endif

  public:
    friend class HMACContext;

    OPENVPN_SIMPLE_EXCEPTION(openssl_digest_uninitialized);
    OPENVPN_EXCEPTION(openssl_digest_error);

    enum
    {
        MAX_DIGEST_SIZE = EVP_MAX_MD_SIZE
    };

    DigestContext() = default;

    DigestContext(const CryptoAlgs::Type alg, SSLLib::Ctx libctx)
    {
        ctx.reset(EVP_MD_CTX_new());

        md.reset(digest_type(alg, libctx));
        if (!EVP_DigestInit(ctx.get(), md.get()))
        {
            openssl_clear_error_stack();
            throw openssl_digest_error("EVP_DigestInit");
        }
    }

    void update(const unsigned char *in, const size_t size)
    {
        if (!EVP_DigestUpdate(ctx.get(), in, int(size)))
        {
            openssl_clear_error_stack();
            throw openssl_digest_error("EVP_DigestUpdate");
        }
    }

    size_t final(unsigned char *out)
    {
        unsigned int outlen;
        if (!EVP_DigestFinal(ctx.get(), out, &outlen))
        {
            openssl_clear_error_stack();
            throw openssl_digest_error("EVP_DigestFinal");
        }
        return outlen;
    }

    size_t size() const
    {
        return EVP_MD_CTX_size(ctx.get());
    }

  private:
    static evp_md_type *digest_type(const CryptoAlgs::Type alg, SSLLib::Ctx libctx)
    {
        switch (alg)
        {
        case CryptoAlgs::MD4:
        case CryptoAlgs::MD5:
        case CryptoAlgs::SHA1:
        case CryptoAlgs::SHA224:
        case CryptoAlgs::SHA256:
        case CryptoAlgs::SHA384:
        case CryptoAlgs::SHA512:
            return EVP_MD_fetch(libctx, CryptoAlgs::name(alg), NULL);
        default:
            OPENVPN_THROW(openssl_digest_error, CryptoAlgs::name(alg) << ": not usable");
        }
    }

    using MD_unique_ptr = std::unique_ptr<evp_md_type, decltype(&::EVP_MD_free)>;
    MD_unique_ptr md{nullptr, ::EVP_MD_free};

    using EVP_MD_CTX_unique_ptr = std::unique_ptr<EVP_MD_CTX, decltype(&::EVP_MD_CTX_free)>;
    EVP_MD_CTX_unique_ptr ctx{nullptr, ::EVP_MD_CTX_free};
};
} // namespace openvpn::OpenSSLCrypto

#endif
