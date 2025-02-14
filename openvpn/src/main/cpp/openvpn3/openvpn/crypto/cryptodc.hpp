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

// Base class for OpenVPN data channel encryption/decryption

#ifndef OPENVPN_CRYPTO_CRYPTODC_H
#define OPENVPN_CRYPTO_CRYPTODC_H

#include <utility> // for std::move
#include <cstdint> // for std::uint32_t, etc.

#include <openvpn/common/exception.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/error/error.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/frame/frame.hpp>
#include <openvpn/crypto/static_key.hpp>
#include <openvpn/crypto/packet_id_control.hpp>
#include <openvpn/crypto/cryptoalgs.hpp>
#include <openvpn/compress/compress.hpp>

namespace openvpn {

// Base class for encryption/decryption of data channel
class CryptoDCInstance : public RC<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<CryptoDCInstance> Ptr;

    // Encrypt/Decrypt

    // returns true if packet ID is close to wrapping
    virtual bool encrypt(BufferAllocated &buf, const unsigned char *op32) = 0;

    virtual Error::Type decrypt(BufferAllocated &buf, std::time_t now, const unsigned char *op32) = 0;

    // Initialization

    // return value of defined()
    enum
    {
        CIPHER_DEFINED = (1 << 0),              // may call init_cipher method
        HMAC_DEFINED = (1 << 1),                // may call init_hmac method
        CRYPTO_DEFINED = (1 << 2),              // may call encrypt or decrypt methods
        EXPLICIT_EXIT_NOTIFY_DEFINED = (1 << 3) // may call explicit_exit_notify method
    };
    virtual unsigned int defined() const = 0;


    /**
     * Initialised the encryption/decryption cipher of the classs. Note that this is
     * and init_hmac need to typically called before encrypt/decrypt can be called.
     */
    virtual void init_cipher(StaticKey &&encrypt_key,
                             StaticKey &&decrypt_key) = 0;

    virtual void init_hmac(StaticKey &&encrypt_key,
                           StaticKey &&decrypt_key) = 0;

    virtual void init_pid(const char *recv_name,
                          const int recv_unit,
                          const SessionStats::Ptr &recv_stats_arg) = 0;

    virtual void init_remote_peer_id(const int remote_peer_id)
    {
    }

    virtual bool consider_compression(const CompressContext &comp_ctx) = 0;

    virtual void explicit_exit_notify()
    {
    }

    // Rekeying

    enum RekeyType
    {
        ACTIVATE_PRIMARY,
        ACTIVATE_PRIMARY_MOVE,
        NEW_SECONDARY,
        PRIMARY_SECONDARY_SWAP,
        DEACTIVATE_SECONDARY,
        DEACTIVATE_ALL,
    };

    virtual void rekey(const RekeyType type) = 0;
};

/** class that holds settings for a data channel encryption */
class CryptoDCSettingsData
{
  public:
    OPENVPN_SIMPLE_EXCEPTION(no_data_channel_factory);

    CryptoDCSettingsData() = default;

    void set_cipher(CryptoAlgs::Type cipher)
    {
        cipher_ = cipher;
    }

    void set_digest(CryptoAlgs::Type digest)
    {
        digest_ = digest;
    }

    void set_use_epoch_keys(bool use_epoch)
    {
        use_epoch_keys = use_epoch;
    }

    CryptoAlgs::Type cipher() const
    {
        return cipher_;
    }

    /**
     *  Retrieve the digest configured for the data channel.
     *  If the configured data channel cipher does not use any
     *  additional digest, CryptoAlgs::NONE is returned.
     *
     * @return  Returns the cipher digest in use
     */
    CryptoAlgs::Type digest() const
    {
        return (CryptoAlgs::use_cipher_digest(cipher_) ? digest_ : CryptoAlgs::NONE);
    }

    bool useEpochKeys() const
    {
        return use_epoch_keys;
    }

    void set_key_derivation(CryptoAlgs::KeyDerivation method)
    {
        key_derivation_ = method;
    }

    CryptoAlgs::KeyDerivation key_derivation() const
    {
        return key_derivation_;
    }


  private:
    CryptoAlgs::Type cipher_ = CryptoAlgs::NONE;
    CryptoAlgs::Type digest_ = CryptoAlgs::NONE;
    CryptoAlgs::KeyDerivation key_derivation_ = CryptoAlgs::KeyDerivation::OPENVPN_PRF;
    bool use_epoch_keys = false;
};

// Factory for CryptoDCInstance objects
class CryptoDCContext : public RC<thread_unsafe_refcount>
{
  public:
    explicit CryptoDCContext(const CryptoAlgs::KeyDerivation method)
        : key_derivation(method)
    {
    }

    typedef RCPtr<CryptoDCContext> Ptr;

    virtual CryptoDCInstance::Ptr new_obj(const unsigned int key_id) = 0;

    virtual CryptoDCSettingsData crypto_info() = 0;

    // Info for ProtoContext::link_mtu_adjust
    virtual size_t encap_overhead() const = 0;

  protected:
    CryptoAlgs::KeyDerivation key_derivation = CryptoAlgs::KeyDerivation::OPENVPN_PRF;
};

// Factory for CryptoDCContext objects
class CryptoDCFactory : public RC<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<CryptoDCFactory> Ptr;

    virtual CryptoDCContext::Ptr new_obj(const CryptoDCSettingsData) = 0;
};


// Manage cipher/digest settings, DC factory, and DC context.
class CryptoDCSettings : public CryptoDCSettingsData
{
  public:
    OPENVPN_SIMPLE_EXCEPTION(no_data_channel_factory);

    CryptoDCSettings() = default;

    void set_factory(const CryptoDCFactory::Ptr &factory)
    {
        factory_ = factory;
        context_.reset();
        dirty = false;
    }

    void set_cipher(const CryptoAlgs::Type new_cipher)
    {
        if (new_cipher != cipher())
        {
            CryptoDCSettingsData::set_cipher(new_cipher);
            dirty = true;
        }
    }

    void set_digest(const CryptoAlgs::Type new_digest)
    {
        if (new_digest != digest())
        {
            CryptoDCSettingsData::set_digest(new_digest);
            dirty = true;
        }
    }

    void set_use_epoch_keys(bool at_the_end)
    {
        if (at_the_end != useEpochKeys())
        {
            CryptoDCSettingsData::set_use_epoch_keys(at_the_end);
            dirty = true;
        }
    }

    CryptoDCContext &context()
    {
        if (!context_ || dirty)
        {
            if (!factory_)
                throw no_data_channel_factory();
            context_ = factory_->new_obj(*this);
            dirty = false;
        }
        return *context_;
    }

    void reset()
    {
        factory_.reset();
        context_.reset();
        dirty = false;
    }

    [[nodiscard]] CryptoDCFactory::Ptr factory() const
    {
        return factory_;
    }

  private:
    bool dirty = false;
    CryptoDCFactory::Ptr factory_;
    CryptoDCContext::Ptr context_;
};
} // namespace openvpn

#endif
