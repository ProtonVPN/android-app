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

class DigestContext : public RC<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<DigestContext> Ptr;

    virtual std::string name() const = 0;
    virtual size_t size() const = 0;

    virtual DigestInstance::Ptr new_digest() = 0;

    virtual HMACInstance::Ptr new_hmac(const unsigned char *key,
                                       const size_t key_size)
        = 0;
};

class DigestFactory : public RC<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<DigestFactory> Ptr;

    virtual DigestContext::Ptr new_context(const CryptoAlgs::Type digest_type) = 0;

    virtual DigestInstance::Ptr new_digest(const CryptoAlgs::Type digest_type) = 0;

    virtual HMACInstance::Ptr new_hmac(const CryptoAlgs::Type digest_type, const unsigned char *key, const size_t key_size) = 0;
};

// Digest implementation using CRYPTO_API

template <typename CRYPTO_API>
class CryptoDigestInstance : public DigestInstance
{
  public:
    CryptoDigestInstance(const CryptoAlgs::Type digest)
        : impl(digest)
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
class CryptoDigestContext : public DigestContext
{
  public:
    CryptoDigestContext(const CryptoAlgs::Type digest_type)
        : digest(digest_type)
    {
    }

    virtual std::string name() const
    {
        return CryptoAlgs::name(digest);
    }

    virtual size_t size() const
    {
        return CryptoAlgs::size(digest);
    }

    virtual DigestInstance::Ptr new_digest()
    {
        return new CryptoDigestInstance<CRYPTO_API>(digest);
    }

    virtual HMACInstance::Ptr new_hmac(const unsigned char *key,
                                       const size_t key_size)
    {
        return new CryptoHMACInstance<CRYPTO_API>(digest,
                                                  key,
                                                  key_size);
    }

  private:
    CryptoAlgs::Type digest;
};

template <typename CRYPTO_API>
class CryptoDigestFactory : public DigestFactory
{
  public:
    virtual DigestContext::Ptr new_context(const CryptoAlgs::Type digest_type)
    {
        return new CryptoDigestContext<CRYPTO_API>(digest_type);
    }

    virtual DigestInstance::Ptr new_digest(const CryptoAlgs::Type digest_type)
    {
        return new CryptoDigestInstance<CRYPTO_API>(digest_type);
    }

    virtual HMACInstance::Ptr new_hmac(const CryptoAlgs::Type digest_type,
                                       const unsigned char *key,
                                       const size_t key_size)
    {
        return new CryptoHMACInstance<CRYPTO_API>(digest_type,
                                                  key,
                                                  key_size);
    }
};

} // namespace openvpn

#endif
