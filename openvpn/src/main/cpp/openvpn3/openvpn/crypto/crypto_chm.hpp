//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
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

// OpenVPN CBC/HMAC data channel

#ifndef OPENVPN_CRYPTO_CRYPTO_CHM_H
#define OPENVPN_CRYPTO_CRYPTO_CHM_H

#include <openvpn/crypto/encrypt_chm.hpp>
#include <openvpn/crypto/decrypt_chm.hpp>
#include <openvpn/crypto/cryptodc.hpp>
#include <openvpn/random/randapi.hpp>

namespace openvpn {

  template <typename CRYPTO_API>
  class CryptoCHM : public CryptoDCInstance
  {
  public:
    typedef CryptoDCInstance Base;

    CryptoCHM(const CryptoAlgs::Type cipher_arg,
	      const CryptoAlgs::Type digest_arg,
	      const Frame::Ptr& frame_arg,
	      const SessionStats::Ptr& stats_arg,
	      const RandomAPI::Ptr& prng_arg)
      : cipher(cipher_arg),
	digest(digest_arg),
	frame(frame_arg),
	stats(stats_arg),
	prng(prng_arg)
    {
      encrypt_.frame = frame;
      decrypt_.frame = frame;
      encrypt_.set_prng(prng);
    }

    // Encrypt/Decrypt

    /* returns true if packet ID is close to wrapping */
    virtual bool encrypt(BufferAllocated& buf, const PacketID::time_t now, const unsigned char *op32)
    {
      encrypt_.encrypt(buf, now);
      return encrypt_.pid_send.wrap_warning();
    }

    virtual Error::Type decrypt(BufferAllocated& buf, const PacketID::time_t now, const unsigned char *op32)
    {
      return decrypt_.decrypt(buf, now);
    }

    // Initialization

    virtual void init_cipher(StaticKey&& encrypt_key,
			     StaticKey&& decrypt_key)
    {
      encrypt_.cipher.init(cipher, encrypt_key, CRYPTO_API::CipherContext::ENCRYPT);
      decrypt_.cipher.init(cipher, decrypt_key, CRYPTO_API::CipherContext::DECRYPT);
    }

    virtual void init_hmac(StaticKey&& encrypt_key,
			   StaticKey&& decrypt_key)
    {
      encrypt_.hmac.init(digest, encrypt_key);
      decrypt_.hmac.init(digest, decrypt_key);
    }

    virtual void init_pid(const int send_form,
			  const int recv_mode,
			  const int recv_form,
			  const char *recv_name,
			  const int recv_unit,
			  const SessionStats::Ptr& recv_stats_arg)
    {
      encrypt_.pid_send.init(send_form);
      decrypt_.pid_recv.init(recv_mode, recv_form, recv_name, recv_unit, recv_stats_arg);
    }

    virtual bool consider_compression(const CompressContext& comp_ctx)
    {
      return true;
    }

    // Indicate whether or not cipher/digest is defined

    virtual unsigned int defined() const
    {
      unsigned int ret = CRYPTO_DEFINED;
      if (CryptoAlgs::defined(cipher))
	ret |= CIPHER_DEFINED;
      if (CryptoAlgs::defined(digest))
	ret |= HMAC_DEFINED;
      return ret;
    }

    // Rekeying

    virtual void rekey(const typename Base::RekeyType type)
    {
    }

  private:
    CryptoAlgs::Type cipher;
    CryptoAlgs::Type digest;
    Frame::Ptr frame;
    SessionStats::Ptr stats;
    RandomAPI::Ptr prng;

    EncryptCHM<CRYPTO_API> encrypt_;
    DecryptCHM<CRYPTO_API> decrypt_;
  };

  template <typename CRYPTO_API>
  class CryptoContextCHM : public CryptoDCContext
  {
  public:
    typedef RCPtr<CryptoContextCHM> Ptr;

    CryptoContextCHM(const CryptoAlgs::Type cipher_arg,
		     const CryptoAlgs::Type digest_arg,
		     const CryptoAlgs::KeyDerivation key_method,
		     const Frame::Ptr& frame_arg,
		     const SessionStats::Ptr& stats_arg,
		     const RandomAPI::Ptr& prng_arg)
      : CryptoDCContext(key_method),
        cipher(CryptoAlgs::legal_dc_cipher(cipher_arg)),
	digest(CryptoAlgs::legal_dc_digest(digest_arg)),
	frame(frame_arg),
	stats(stats_arg),
	prng(prng_arg)
    {
    }

    virtual CryptoDCInstance::Ptr new_obj(const unsigned int key_id)
    {
      return new CryptoCHM<CRYPTO_API>(cipher, digest, frame, stats, prng);
    }

    // cipher/HMAC/key info
    virtual Info crypto_info()
    {
      Info ret;
      ret.cipher_alg = cipher;
      ret.hmac_alg = digest;
      ret.key_derivation = key_derivation;
      return ret;
    }

    // Info for ProtoContext::link_mtu_adjust

    virtual size_t encap_overhead() const
    {
      return CryptoAlgs::size(digest) +       // HMAC
	CryptoAlgs::iv_length(cipher) +       // Cipher IV
	CryptoAlgs::block_size(cipher);       // worst-case PKCS#7 padding expansion
    }

  private:
    CryptoAlgs::Type cipher;
    CryptoAlgs::Type digest;
    Frame::Ptr frame;
    SessionStats::Ptr stats;
    RandomAPI::Ptr prng;
  };
}

#endif
