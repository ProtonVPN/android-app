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

// OpenVPN TLS-Crypt classes

#ifndef OPENVPN_CRYPTO_TLSCRYPT_H
#define OPENVPN_CRYPTO_TLSCRYPT_H

#include <string>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/common/memneq.hpp>
#include <openvpn/crypto/static_key.hpp>
#include <openvpn/crypto/cryptoalgs.hpp>
#include <openvpn/crypto/packet_id_control.hpp>
#include <openvpn/ssl/psid.hpp>

namespace openvpn {

// OpenVPN protocol HMAC usage for HMAC/CTR integrity checking and tls-crypt

// Control packet format when tls-crypt is enabled:
// [OP]  [PSID]  [PID]  [HMAC] [...]

template <typename CRYPTO_API>
class TLSCrypt
{
  public:
    OPENVPN_SIMPLE_EXCEPTION(ovpn_tls_crypt_context_digest_size);
    OPENVPN_SIMPLE_EXCEPTION(ovpn_tls_crypt_context_bad_sizing);
    OPENVPN_SIMPLE_EXCEPTION(ovpn_tls_crypt_wrong_mode);

    TLSCrypt()
        : mode(CRYPTO_API::CipherContext::MODE_UNDEF)
    {
    }

    TLSCrypt(SSLLib::Ctx libctx, const CryptoAlgs::Type digest, const StaticKey &key_hmac, const CryptoAlgs::Type cipher, const StaticKey &key_crypt, const int mode)
    {
        init(libctx, digest, key_hmac, cipher, key_crypt, mode);
    }

    bool defined() const
    {
        return ctx_hmac.is_initialized() && ctx_crypt.is_initialized();
    }

    // size of out buffer to pass to hmac
    size_t output_hmac_size() const
    {
        return ctx_hmac.size();
    }

    void init(SSLLib::Ctx libctx, const CryptoAlgs::Type digest, const StaticKey &key_hmac, const CryptoAlgs::Type cipher, const StaticKey &key_crypt, const int mode_arg)
    {
        const CryptoAlgs::Alg &alg_hmac = CryptoAlgs::get(digest);

        // check that key is large enough
        if (key_hmac.size() < alg_hmac.size())
            throw ovpn_tls_crypt_context_digest_size();

        // initialize HMAC context with digest type and key
        ctx_hmac.init(digest, key_hmac.data(), alg_hmac.size());

        // initialize Cipher context with cipher, key and mode
        ctx_crypt.init(libctx, cipher, key_crypt.data(), mode_arg);

        mode = mode_arg;
    }

    bool hmac_gen(unsigned char *header, const size_t header_len, const unsigned char *payload, const size_t payload_len)
    {
        hmac_pre(header, header_len, payload, payload_len);
        ctx_hmac.final(header + header_len);

        return true;
    }

    bool hmac_cmp(const unsigned char *header, const size_t header_len, const unsigned char *payload, const size_t payload_len)
    {
        unsigned char local_hmac[CRYPTO_API::HMACContext::MAX_HMAC_SIZE];

        hmac_pre(header, header_len, payload, payload_len);
        ctx_hmac.final(local_hmac);

        return !crypto::memneq(header + header_len, local_hmac, output_hmac_size());
    }

    size_t encrypt(const unsigned char *iv, unsigned char *out, const size_t olen, const unsigned char *in, const size_t ilen)
    {
        if (mode != CRYPTO_API::CipherContext::ENCRYPT)
            throw ovpn_tls_crypt_wrong_mode();

        return encrypt_decrypt(iv, out, olen, in, ilen);
    }

    size_t decrypt(const unsigned char *iv, unsigned char *out, const size_t olen, const unsigned char *in, const size_t ilen)
    {
        if (mode != CRYPTO_API::CipherContext::DECRYPT)
            throw ovpn_tls_crypt_wrong_mode();

        return encrypt_decrypt(iv, out, olen, in, ilen);
    }

  private:
    // assume length check on header has already been performed
    void hmac_pre(const unsigned char *header, const size_t header_len, const unsigned char *payload, const size_t payload_len)
    {
        ctx_hmac.reset();
        ctx_hmac.update(header, header_len);
        ctx_hmac.update(payload, payload_len);
    }

    size_t encrypt_decrypt(const unsigned char *iv, unsigned char *out, const size_t olen, const unsigned char *in, const size_t ilen)
    {
        ctx_crypt.reset(iv);

        size_t outlen = 0;

        if (!ctx_crypt.update(out, olen, in, ilen, outlen))
            return 0;

        if (!ctx_crypt.final(out + outlen, olen - outlen, outlen))
            return 0;

        return outlen;
    }

    typename CRYPTO_API::HMACContext ctx_hmac;
    typename CRYPTO_API::CipherContext ctx_crypt;
    int mode;
};

// OvpnHMAC wrapper API using dynamic polymorphism

class TLSCryptInstance : public RC<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<TLSCryptInstance> Ptr;

    virtual void init(SSLLib::Ctx libctx, const StaticKey &key_hmac, const StaticKey &key_crypt) = 0;

    virtual size_t output_hmac_size() const = 0;

    virtual bool hmac_gen(unsigned char *header, const size_t header_len, const unsigned char *payload, const size_t payload_len) = 0;

    virtual bool hmac_cmp(const unsigned char *header, const size_t header_len, const unsigned char *payload, const size_t payload_len) = 0;

    virtual size_t encrypt(const unsigned char *iv, unsigned char *out, const size_t olen, const unsigned char *in, const size_t ilen) = 0;

    virtual size_t decrypt(const unsigned char *iv, unsigned char *out, const size_t olen, const unsigned char *in, const size_t ilen) = 0;
};

class TLSCryptContext : public RC<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<TLSCryptContext> Ptr;

    virtual size_t digest_size() const = 0;

    virtual size_t cipher_key_size() const = 0;

    virtual TLSCryptInstance::Ptr new_obj_send() = 0;

    virtual TLSCryptInstance::Ptr new_obj_recv() = 0;

    // This is the size of the header in a TLSCrypt-wrapped packets,
    // excluding the HMAC. Format:
    //
    // [OP]  [PSID]  [PID]  [HMAC] [...]
    //

    constexpr const static size_t hmac_offset = 1 + ProtoSessionID::SIZE + PacketIDControl::idsize;
};



class TLSCryptFactory : public RC<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<TLSCryptFactory> Ptr;

    virtual TLSCryptContext::Ptr new_obj(SSLLib::Ctx libctx, const CryptoAlgs::Type digest_type, const CryptoAlgs::Type cipher_type) = 0;
};

// TLSCrypt wrapper implementation using dynamic polymorphism

template <typename CRYPTO_API>
class CryptoTLSCryptInstance : public TLSCryptInstance
{
  public:
    CryptoTLSCryptInstance(SSLLib::Ctx libctx_arg,
                           const CryptoAlgs::Type digest_arg,
                           const CryptoAlgs::Type cipher_arg,
                           int mode_arg)
        : digest(digest_arg),
          cipher(cipher_arg),
          mode(mode_arg),
          libctx(libctx_arg)
    {
    }

    void init(SSLLib::Ctx libctx, const StaticKey &key_hmac, const StaticKey &key_crypt)
    {
        tls_crypt.init(libctx, digest, key_hmac, cipher, key_crypt, mode);
    }

    size_t output_hmac_size() const
    {
        return tls_crypt.output_hmac_size();
    }

    bool hmac_gen(unsigned char *header, const size_t header_len, const unsigned char *payload, const size_t payload_len)
    {
        return tls_crypt.hmac_gen(header, header_len, payload, payload_len);
    }

    // verify the HMAC generated by hmac_gen, return true if verified
    bool hmac_cmp(const unsigned char *header, const size_t header_len, const unsigned char *payload, const size_t payload_len)
    {
        return tls_crypt.hmac_cmp(header, header_len, payload, payload_len);
    }

    size_t encrypt(const unsigned char *iv, unsigned char *out, const size_t olen, const unsigned char *in, const size_t ilen)
    {
        return tls_crypt.encrypt(iv, out, olen, in, ilen);
    }

    size_t decrypt(const unsigned char *iv, unsigned char *out, const size_t olen, const unsigned char *in, const size_t ilen)
    {
        return tls_crypt.decrypt(iv, out, olen, in, ilen);
    }

  private:
    typename CryptoAlgs::Type digest;
    typename CryptoAlgs::Type cipher;
    int mode;
    TLSCrypt<CRYPTO_API> tls_crypt;
    SSLLib::Ctx libctx;
};

template <typename CRYPTO_API>
class CryptoTLSCryptContext : public TLSCryptContext
{
  public:
    CryptoTLSCryptContext(SSLLib::Ctx libctx_arg, const CryptoAlgs::Type digest_type, const CryptoAlgs::Type cipher_type)
        : digest(digest_type),
          cipher(cipher_type),
          libctx(libctx_arg)
    {
    }

    size_t digest_size() const override
    {
        return CryptoAlgs::size(digest);
    }

    size_t cipher_key_size() const override
    {
        return CryptoAlgs::key_length(cipher);
    }

    TLSCryptInstance::Ptr new_obj_send() override
    {
        return new CryptoTLSCryptInstance<CRYPTO_API>(libctx, digest, cipher, CRYPTO_API::CipherContext::ENCRYPT);
    }

    TLSCryptInstance::Ptr new_obj_recv() override
    {
        return new CryptoTLSCryptInstance<CRYPTO_API>(libctx, digest, cipher, CRYPTO_API::CipherContext::DECRYPT);
    }

  private:
    CryptoAlgs::Type digest;
    CryptoAlgs::Type cipher;
    SSLLib::Ctx libctx;
};

template <typename CRYPTO_API>
class CryptoTLSCryptFactory : public TLSCryptFactory
{
  public:
    TLSCryptContext::Ptr new_obj(SSLLib::Ctx libctx, const CryptoAlgs::Type digest_type, const CryptoAlgs::Type cipher_type) override
    {
        return new CryptoTLSCryptContext<CRYPTO_API>(libctx, digest_type, cipher_type);
    }
};
} // namespace openvpn

#endif
