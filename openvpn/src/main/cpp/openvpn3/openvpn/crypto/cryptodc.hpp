//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2017 OpenVPN Inc.
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
#include <openvpn/crypto/packet_id.hpp>
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
    virtual bool encrypt(BufferAllocated& buf, const PacketID::time_t now, const unsigned char *op32) = 0;

    virtual Error::Type decrypt(BufferAllocated& buf, const PacketID::time_t now, const unsigned char *op32) = 0;

    // Initialization

    // return value of defined()
    enum {
      CIPHER_DEFINED=(1<<0),  // may call init_cipher method
      HMAC_DEFINED=(1<<1),    // may call init_hmac method
      CRYPTO_DEFINED=(1<<2),  // may call encrypt or decrypt methods
      EXPLICIT_EXIT_NOTIFY_DEFINED=(1<<3),  // may call explicit_exit_notify method
    };
    virtual unsigned int defined() const = 0;

    virtual void init_cipher(StaticKey&& encrypt_key,
			     StaticKey&& decrypt_key) = 0;

    virtual void init_hmac(StaticKey&& encrypt_key,
			   StaticKey&& decrypt_key) = 0;

    virtual void init_pid(const int send_form,
			  const int recv_mode,
			  const int recv_form,
			  const char *recv_name,
			  const int recv_unit,
			  const SessionStats::Ptr& recv_stats_arg) = 0;

    virtual void init_remote_peer_id(const int remote_peer_id) {}

    virtual bool consider_compression(const CompressContext& comp_ctx) = 0;

    virtual void explicit_exit_notify() {}

    // Rekeying

    enum RekeyType {
      ACTIVATE_PRIMARY,
      ACTIVATE_PRIMARY_MOVE,
      NEW_SECONDARY,
      PRIMARY_SECONDARY_SWAP,
      DEACTIVATE_SECONDARY,
      DEACTIVATE_ALL,
    };

    virtual void rekey(const RekeyType type) = 0;
  };

  // Factory for CryptoDCInstance objects
  class CryptoDCContext : public RC<thread_unsafe_refcount>
  {
  public:
    typedef RCPtr<CryptoDCContext> Ptr;

    virtual CryptoDCInstance::Ptr new_obj(const unsigned int key_id) = 0;

    // cipher/HMAC/key info
    struct Info {
      Info() : cipher_alg(CryptoAlgs::NONE), hmac_alg(CryptoAlgs::NONE) {}
      CryptoAlgs::Type cipher_alg;
      CryptoAlgs::Type hmac_alg;
    };
    virtual Info crypto_info() = 0;

    // Info for ProtoContext::link_mtu_adjust
    virtual size_t encap_overhead() const = 0;
  };

  // Factory for CryptoDCContext objects
  class CryptoDCFactory : public RC<thread_unsafe_refcount>
  {
  public:
    typedef RCPtr<CryptoDCFactory> Ptr;

    virtual CryptoDCContext::Ptr new_obj(const CryptoAlgs::Type cipher,
					 const CryptoAlgs::Type digest) = 0;
  };

  // Manage cipher/digest settings, DC factory, and DC context.
  class CryptoDCSettings
  {
  public:
    OPENVPN_SIMPLE_EXCEPTION(no_data_channel_factory);

    CryptoDCSettings()
      : cipher_(CryptoAlgs::NONE),
	digest_(CryptoAlgs::NONE),
	dirty(false)
    {
    }

    void set_factory(const CryptoDCFactory::Ptr& factory)
    {
      factory_ = factory;
      context_.reset();
      dirty = false;
    }

    void set_cipher(const CryptoAlgs::Type cipher)
    {
      if (cipher != cipher_)
	{
	  cipher_ = cipher;
	  dirty = true;
	}
    }

    void set_digest(const CryptoAlgs::Type digest)
    {
      if (digest != digest_)
	{
	  digest_ = digest;
	  dirty = true;
	}
    }

    CryptoDCContext& context()
    {
      if (!context_ || dirty)
	{
	  if (!factory_)
	    throw no_data_channel_factory();
	  context_ = factory_->new_obj(cipher_, digest_);
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

    CryptoAlgs::Type cipher() const { return cipher_; }
    CryptoAlgs::Type digest() const { return digest_; }

    CryptoDCFactory::Ptr factory() const { return factory_; }

  private:
    CryptoAlgs::Type cipher_;
    CryptoAlgs::Type digest_;
    CryptoDCFactory::Ptr factory_;
    CryptoDCContext::Ptr context_;
    bool dirty;
  };
}

#endif
