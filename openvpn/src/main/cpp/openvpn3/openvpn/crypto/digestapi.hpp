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

// Crypto digest/HMAC API

#ifndef OPENVPN_CRYPTO_DIGESTAPI_H
#define OPENVPN_CRYPTO_DIGESTAPI_H

#include <openvpn/common/rc.hpp>
#include <openvpn/crypto/cryptoalgs.hpp>

namespace openvpn {

// Digest/HMAC abstract base classes and factories

class DigestInstance : public RC<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<DigestInstance> Ptr;

    virtual void update(const unsigned char *in, const size_t size) = 0;
    virtual size_t final(unsigned char *out) = 0;
    virtual size_t size() const = 0;
};

class HMACInstance : public RC<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<HMACInstance> Ptr;

    virtual void reset() = 0;
    virtual void update(const unsigned char *in, const size_t size) = 0;
    virtual size_t final(unsigned char *out) = 0;
    virtual size_t size() const = 0;
};

class DigestFactory : public RC<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<DigestFactory> Ptr;

    virtual DigestInstance::Ptr new_digest(const CryptoAlgs::Type digest_type) = 0;

    virtual HMACInstance::Ptr new_hmac(const CryptoAlgs::Type digest_type, const unsigned char *key, const size_t key_size) = 0;
};

// Digest implementation using CRYPTO_API

template <typename CRYPTO_API>
class CryptoDigestInstance : public DigestInstance
{
  public:
    CryptoDigestInstance(const CryptoAlgs::Type digest, SSLLib::Ctx libctx)
        : impl(digest, libctx)
    {
    }

    virtual void update(const unsigned char *in, const size_t size)
    {
        impl.update(in, size);
    }

    virtual size_t final(unsigned char *out)
    {
        return impl.final(out);
    }

    virtual size_t size() const
    {
        return impl.size();
    }

  private:
    typename CRYPTO_API::DigestContext impl;
};

template <typename CRYPTO_API>
class CryptoHMACInstance : public HMACInstance
{
  public:
    CryptoHMACInstance(const CryptoAlgs::Type digest,
                       const unsigned char *key,
                       const size_t key_size)
        : impl(digest, key, key_size)
    {
    }

    virtual void reset()
    {
        impl.reset();
    }

    virtual void update(const unsigned char *in, const size_t size)
    {
        impl.update(in, size);
    }

    virtual size_t final(unsigned char *out)
    {
        return impl.final(out);
    }

    size_t size() const
    {
        return impl.size();
    }

  private:
    typename CRYPTO_API::HMACContext impl;
};

template <typename CRYPTO_API>
class CryptoDigestFactory : public DigestFactory
{
  public:
    CryptoDigestFactory(SSLLib::Ctx libctx_arg = nullptr)
        : libctx(libctx_arg)
    {
    }

    virtual DigestInstance::Ptr new_digest(const CryptoAlgs::Type digest_type)
    {
        return new CryptoDigestInstance<CRYPTO_API>(digest_type, libctx);
    }

    virtual HMACInstance::Ptr new_hmac(const CryptoAlgs::Type digest_type,
                                       const unsigned char *key,
                                       const size_t key_size)
    {
        return new CryptoHMACInstance<CRYPTO_API>(digest_type,
                                                  key,
                                                  key_size);
    }

    SSLLib::Ctx libctx;
};

} // namespace openvpn

#endif
