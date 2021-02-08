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

// OpenVPN AEAD data channel interface

#ifndef OPENVPN_CRYPTO_CRYPTO_AEAD_H
#define OPENVPN_CRYPTO_CRYPTO_AEAD_H

#include <cstring>           // for std::memcpy, std::memset

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/frame/frame.hpp>
#include <openvpn/crypto/static_key.hpp>
#include <openvpn/crypto/packet_id.hpp>
#include <openvpn/log/sessionstats.hpp>
#include <openvpn/crypto/cryptodc.hpp>

// Sample AES-GCM head:
//   48000001 00000005 7e7046bd 444a7e28 cc6387b1 64a4d6c1 380275a...
//   [ OP32 ] [seq # ] [             auth tag            ] [ payload ... ]
//            [4-byte
//            IV head]

namespace openvpn {
  namespace AEAD {

    OPENVPN_EXCEPTION(aead_error);

    /**
     * Check if a specific algorithm is support or not in the underlying
     * crypto library
     */
    template  <typename CRYPTO_API>
    static inline bool is_algorithm_supported(const CryptoAlgs::Type cipher)
    {
      return CRYPTO_API::CipherContextAEAD::is_supported(cipher);
    }

    template <typename CRYPTO_API>
    class Crypto : public CryptoDCInstance
    {
      class Nonce {
      public:
	Nonce()
	{
	  static_assert(4 + CRYPTO_API::CipherContextAEAD::IV_LEN == sizeof(data),
			"AEAD IV_LEN inconsistency");
	  ad_op32 = false;
	  std::memset(data, 0, sizeof(data));
	}

	// setup
	void set_tail(const StaticKey& sk)
	{
	  if (sk.size() < 8)
	    throw aead_error("insufficient key material for nonce tail");
	  std::memcpy(data + 8, sk.data(), 8);
	}

	// for encrypt
	Nonce(const Nonce& ref, PacketIDSend& pid_send, const PacketID::time_t now,
	      const unsigned char *op32)
	{
	  std::memcpy(data, ref.data, sizeof(data));
	  Buffer buf(data + 4, 4, false);
	  pid_send.write_next(buf, false, now);
	  if (op32)
	    {
	      ad_op32 = true;
	      std::memcpy(data, op32, 4);
	    }
	  else
	    ad_op32 = false;
	}

	// for encrypt
	void prepend_ad(Buffer& buf) const
	{
	  buf.prepend(data + 4, 4);
	}

	// for decrypt
	Nonce(const Nonce& ref, Buffer& buf, const unsigned char *op32)
	{
	  std::memcpy(data, ref.data, sizeof(data));
	  buf.read(data + 4, 4);
	  if (op32)
	    {
	      ad_op32 = true;
	      std::memcpy(data, op32, 4);
	    }
	  else
	    ad_op32 = false;
	}

	// for decrypt
	bool verify_packet_id(PacketIDReceive& pid_recv, const PacketID::time_t now)
	{
	  Buffer buf(data + 4, 4, true);
	  const PacketID pid = pid_recv.read_next(buf);
	  return pid_recv.test_add(pid, now, true); // verify packet ID
	}

	const unsigned char *iv() const
	{
	  return data + 4;
	}

	const unsigned char *ad() const
	{
	  return ad_op32 ? data : data + 4;
	}

	const size_t ad_len() const
	{
	  return ad_op32 ? 8 : 4;
	}

      private:
	bool ad_op32; // true if AD includes op32 opcode

	// Sample data:
	//   [ OP32 (optional) ] [  pkt ID     ] [     nonce tail          ]
	//   [ 48 00 00 01     ] [ 00 00 00 05 ] [ 7f 45 64 db 33 5b 6c 29 ]
	unsigned char data[16];
      };

      struct Encrypt {
	typename CRYPTO_API::CipherContextAEAD impl;
	Nonce nonce;
	PacketIDSend pid_send;
	BufferAllocated work;
      };

      struct Decrypt {
	typename CRYPTO_API::CipherContextAEAD impl;
	Nonce nonce;
	PacketIDReceive pid_recv;
	BufferAllocated work;
      };
    public:
      typedef CryptoDCInstance Base;

      Crypto(const CryptoAlgs::Type cipher_arg,
	     const Frame::Ptr& frame_arg,
	     const SessionStats::Ptr& stats_arg)
	: cipher(cipher_arg),
	  frame(frame_arg),
	  stats(stats_arg)
      {
      }

      // Encrypt/Decrypt

      // returns true if packet ID is close to wrapping
      virtual bool encrypt(BufferAllocated& buf, const PacketID::time_t now, const unsigned char *op32)
      {
	// only process non-null packets
	if (buf.size())
	  {
	    // build nonce/IV/AD
	    Nonce nonce(e.nonce, e.pid_send, now, op32);

	    if (CRYPTO_API::CipherContextAEAD::SUPPORTS_IN_PLACE_ENCRYPT)
	      {
		unsigned char *data = buf.data();
		const size_t size = buf.size();

		// alloc auth tag in buffer
		unsigned char *auth_tag = buf.prepend_alloc(CRYPTO_API::CipherContextAEAD::AUTH_TAG_LEN);

		// encrypt in-place
		e.impl.encrypt(data, data, size, nonce.iv(), auth_tag, nonce.ad(), nonce.ad_len());
	      }
	    else
	      {
		// encrypt to work buf
		frame->prepare(Frame::ENCRYPT_WORK, e.work);
		if (e.work.max_size() < buf.size())
		  throw aead_error("encrypt work buffer too small");

		// alloc auth tag in buffer
		unsigned char *auth_tag = e.work.prepend_alloc(CRYPTO_API::CipherContextAEAD::AUTH_TAG_LEN);

		// prepare output buffer
		unsigned char *work_data = e.work.write_alloc(buf.size());

		// encrypt
		e.impl.encrypt(buf.data(), work_data, buf.size(), nonce.iv(), auth_tag, nonce.ad(), nonce.ad_len());
		buf.swap(e.work);
	      }

	    // prepend additional data
	    nonce.prepend_ad(buf);
	  }
	return e.pid_send.wrap_warning();
      }

      virtual Error::Type decrypt(BufferAllocated& buf, const PacketID::time_t now, const unsigned char *op32)
      {
	// only process non-null packets
	if (buf.size())
	  {
	    // get nonce/IV/AD
	    Nonce nonce(d.nonce, buf, op32);

	    // get auth tag
	    unsigned char *auth_tag = buf.read_alloc(CRYPTO_API::CipherContextAEAD::AUTH_TAG_LEN);

	    // initialize work buffer
	    frame->prepare(Frame::DECRYPT_WORK, d.work);
	    if (d.work.max_size() < buf.size())
	      throw aead_error("decrypt work buffer too small");

	    // decrypt from buf -> work
	    if (!d.impl.decrypt(buf.c_data(), d.work.data(), buf.size(), nonce.iv(), auth_tag,
				nonce.ad(), nonce.ad_len()))
	      {
		buf.reset_size();
		return Error::DECRYPT_ERROR;
	      }
	    d.work.set_size(buf.size());

	    // verify packet ID
	    if (!nonce.verify_packet_id(d.pid_recv, now))
	      {
		buf.reset_size();
		return Error::REPLAY_ERROR;
	      }

	    // return cleartext result in buf
	    buf.swap(d.work);
	  }
	return Error::SUCCESS;
      }

      // Initialization

      virtual void init_cipher(StaticKey&& encrypt_key,
			       StaticKey&& decrypt_key)
      {
	e.impl.init(cipher, encrypt_key.data(), encrypt_key.size(), CRYPTO_API::CipherContextAEAD::ENCRYPT);
	d.impl.init(cipher, decrypt_key.data(), decrypt_key.size(), CRYPTO_API::CipherContextAEAD::DECRYPT);
      }

      virtual void init_hmac(StaticKey&& encrypt_key,
			     StaticKey&& decrypt_key)
      {
	e.nonce.set_tail(encrypt_key);
	d.nonce.set_tail(decrypt_key);
      }

      virtual void init_pid(const int send_form,
			    const int recv_mode,
			    const int recv_form,
			    const char *recv_name,
			    const int recv_unit,
			    const SessionStats::Ptr& recv_stats_arg)
      {
	e.pid_send.init(send_form);
	d.pid_recv.init(recv_mode, recv_form, recv_name, recv_unit, recv_stats_arg);
      }

      // Indicate whether or not cipher/digest is defined

      virtual unsigned int defined() const
      {
	unsigned int ret = CRYPTO_DEFINED;

	// AEAD mode doesn't use HMAC, but we still indicate HMAC_DEFINED
	// because we want to use the HMAC keying material for the AEAD nonce tail.
	if (CryptoAlgs::defined(cipher))
	  ret |= (CIPHER_DEFINED|HMAC_DEFINED);
	return ret;
      }

      virtual bool consider_compression(const CompressContext& comp_ctx)
      {
	return true;
      }

      // Rekeying

      virtual void rekey(const typename Base::RekeyType type)
      {
      }

    private:
      CryptoAlgs::Type cipher;
      Frame::Ptr frame;
      SessionStats::Ptr stats;
      Encrypt e;
      Decrypt d;
    };

    template <typename CRYPTO_API>
    class CryptoContext : public CryptoDCContext
    {
    public:
      typedef RCPtr<CryptoContext> Ptr;

      CryptoContext(const CryptoAlgs::Type cipher_arg,
		    const CryptoAlgs::KeyDerivation key_method,
		    const Frame::Ptr& frame_arg,
		    const SessionStats::Ptr& stats_arg)
	: CryptoDCContext(key_method),
	  cipher(CryptoAlgs::legal_dc_cipher(cipher_arg)),
	  frame(frame_arg),
	  stats(stats_arg)
      {
      }

      virtual CryptoDCInstance::Ptr new_obj(const unsigned int key_id)
      {
	return new Crypto<CRYPTO_API>(cipher, frame, stats);
      }

      // cipher/HMAC/key info
      virtual Info crypto_info()
      {
	Info ret;
	ret.cipher_alg = cipher;
	ret.hmac_alg = CryptoAlgs::NONE;
	ret.key_derivation = key_derivation;
	return ret;
      }

      // Info for ProtoContext::link_mtu_adjust

      virtual size_t encap_overhead() const
      {
	return CRYPTO_API::CipherContextAEAD::AUTH_TAG_LEN;
      }

    private:
      CryptoAlgs::Type cipher;
      Frame::Ptr frame;
      SessionStats::Ptr stats;
    };
  }
}

#endif
