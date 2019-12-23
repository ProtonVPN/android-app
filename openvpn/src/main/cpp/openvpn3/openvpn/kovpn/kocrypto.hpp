//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2018 OpenVPN Inc.
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

// kovpn crypto wrappers

#ifndef OPENVPN_KOVPN_KOCRYPTO_H
#define OPENVPN_KOVPN_KOCRYPTO_H

#include <cstring> // for std::memset, std::memcpy
#include <utility> // for std::move

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/frame/frame.hpp>
#include <openvpn/crypto/cryptodc.hpp>
#include <openvpn/crypto/bs64_data_limit.hpp>
#include <openvpn/kovpn/kovpn.hpp>

namespace openvpn {
  namespace KoRekey {

    OPENVPN_EXCEPTION(korekey_error);

    struct Info {
      Info() {}

      Info(const CryptoDCContext::Ptr& dc_context_delegate_arg,
	   const unsigned int key_id_arg,
	   const Frame::Ptr& frame_arg)
	: dc_context_delegate(dc_context_delegate_arg),
	  key_id(key_id_arg),
	  frame(frame_arg)
      {
      }

      CryptoDCContext::Ptr dc_context_delegate;
      CompressContext comp_ctx;
      unsigned int key_id = 0;
      int remote_peer_id = -1;
      bool tcp_linear = false;
      StaticKey encrypt_cipher;
      StaticKey encrypt_hmac;
      StaticKey decrypt_cipher;
      StaticKey decrypt_hmac;
      Frame::Ptr frame;
    };

    class Key
    {
      // noncopyable because of "opk.primary = &key" below
      Key(const Key&) = delete;
      Key& operator=(const Key&) = delete;

    public:
      static void validate(const CryptoAlgs::Type cipher,
			   const CryptoAlgs::Type digest)
      {
	const CryptoAlgs::Alg& calg = CryptoAlgs::get(cipher);
	const CryptoAlgs::Alg& halg = CryptoAlgs::get(digest);

	switch (cipher)
	  {
	  case CryptoAlgs::AES_128_GCM:
	  case CryptoAlgs::AES_192_GCM:
	  case CryptoAlgs::AES_256_GCM:
	  case CryptoAlgs::AES_128_CBC:
	  case CryptoAlgs::AES_192_CBC:
	  case CryptoAlgs::AES_256_CBC:
	  case CryptoAlgs::BF_CBC:
	    break;
	  default:
	    OPENVPN_THROW(korekey_error, "cipher alg " << calg.name() << " is not currently supported by kovpn");
	  }

	if (calg.mode() == CryptoAlgs::CBC_HMAC)
	  {
	    switch (digest)
	      {
	      case CryptoAlgs::SHA1:
	      case CryptoAlgs::SHA256:
		break;
	      default:
		OPENVPN_THROW(korekey_error, "HMAC alg " << halg.name() << " is not currently supported by kovpn");
	      }
	  }
      }

      Key(const CryptoDCInstance::RekeyType rktype,
	  const Info& rkinfo, // must remain in scope for duration of Key lifetime
	  const int peer_id,
	  const bool verbose)
      {
	std::memset(&opk, 0, sizeof(opk));

	// set peer ID
	opk.peer_id = peer_id;

	// set rekey op
	bool new_key = false;
	bool secondary_key = false; // only relevant for non-deactivate ops to secondary
	switch (rktype)
	  {
	  case CryptoDCInstance::ACTIVATE_PRIMARY:
	    {
	      new_key = true;
	      opk.op = OVPN_KEYS_PRIMARY_ONLY;
	      break;
	    }
	  case CryptoDCInstance::ACTIVATE_PRIMARY_MOVE:
	    {
	      new_key = true;
	      opk.op = OVPN_KEYS_PRIMARY_ASSIGN_MOVE;
	      break;
	    }
	  case CryptoDCInstance::NEW_SECONDARY:
	    {
	      new_key = true;
	      secondary_key = true;
	      opk.op = OVPN_KEYS_SECONDARY_ONLY;
	      break;
	    }
	  case CryptoDCInstance::PRIMARY_SECONDARY_SWAP:
	    {
	      opk.op = OVPN_KEYS_PRIMARY_SECONDARY_SWAP;
	      break;
	    }
	  case CryptoDCInstance::DEACTIVATE_SECONDARY:
	    {
	      opk.op = OVPN_KEYS_SECONDARY_ONLY;
	      break;
	    }
	  case CryptoDCInstance::DEACTIVATE_ALL:
	    {
	      opk.op = OVPN_KEYS_BOTH;
	      break;
	    }
	  default:
	    OPENVPN_THROW(korekey_error, "unrecognized rekey type=" << (int)rktype);
	  }

	if (new_key)
	  {
	    const CryptoDCContext::Info ci = rkinfo.dc_context_delegate->crypto_info();
	    const CryptoAlgs::Alg& calg = CryptoAlgs::get(ci.cipher_alg);

	    // set crypto family
	    switch (calg.mode())
	      {
	      case CryptoAlgs::CBC_HMAC:
		opk.crypto_family = OVPN_CRYPTO_FAMILY_CBC_HMAC;
		break;
	      case CryptoAlgs::AEAD:
		opk.crypto_family = OVPN_CRYPTO_FAMILY_AEAD;
		break;
	      default:
		opk.crypto_family = OVPN_CRYPTO_FAMILY_UNDEF;
		break;
	      }

	    std::memset(&key, 0, sizeof(key));
	    key.key_id = rkinfo.key_id;
	    key.remote_peer_id = rkinfo.remote_peer_id;

	    switch (ci.cipher_alg)
	      {
	      case CryptoAlgs::AES_128_GCM:
		key.cipher_alg = OVPN_ALG_AES_GCM;
		key.encrypt.cipher_key_size = 128 / 8;
		break;
	      case CryptoAlgs::AES_192_GCM:
		key.cipher_alg = OVPN_ALG_AES_GCM;
		key.encrypt.cipher_key_size = 192 / 8;
		break;
	      case CryptoAlgs::AES_256_GCM:
		key.cipher_alg = OVPN_ALG_AES_GCM;
		key.encrypt.cipher_key_size = 256 / 8;
		break;
	      case CryptoAlgs::AES_128_CBC:
		key.cipher_alg = OVPN_ALG_AES_CBC;
		key.encrypt.cipher_key_size = 128 / 8;
		break;
	      case CryptoAlgs::AES_192_CBC:
		key.cipher_alg = OVPN_ALG_AES_CBC;
		key.encrypt.cipher_key_size = 192 / 8;
		break;
	      case CryptoAlgs::AES_256_CBC:
		key.cipher_alg = OVPN_ALG_AES_CBC;
		key.encrypt.cipher_key_size = 256 / 8;
		break;
	      case CryptoAlgs::BF_CBC:
		key.cipher_alg = OVPN_ALG_BF_CBC;
		key.encrypt.cipher_key_size = 128 / 8;

		// special data limits for 64-bit block-size ciphers (CVE-2016-6329)
		key.encrypt.data_limit = key.decrypt.data_limit = OPENVPN_BS64_DATA_LIMIT;
		break;
	      default:
		key.cipher_alg = OVPN_ALG_UNDEF;
		break;
	      }
	    key.decrypt.cipher_key_size = key.encrypt.cipher_key_size;

	    // make sure that chosen cipher/family is supported
	    if (opk.crypto_family == OVPN_CRYPTO_FAMILY_UNDEF
		|| key.cipher_alg == OVPN_ALG_UNDEF)
	      OPENVPN_THROW(korekey_error, "cipher alg " << calg.name() << " is not currently supported by kovpn");

	    // set cipher keys
	    key.encrypt.cipher_key = verify_key("cipher encrypt",
						rkinfo.encrypt_cipher,
						key.encrypt.cipher_key_size);
	    key.decrypt.cipher_key = verify_key("cipher decrypt",
						rkinfo.decrypt_cipher,
						key.decrypt.cipher_key_size);

	    switch (calg.mode())
	      {
	      case CryptoAlgs::CBC_HMAC:
		{
		  // if CBC mode, process HMAC digest
		  const CryptoAlgs::Alg& halg = CryptoAlgs::get(ci.hmac_alg);
		  switch (ci.hmac_alg)
		    {
		    case CryptoAlgs::SHA1:
		      key.hmac_alg = OVPN_ALG_HMAC_SHA1;
		      break;
		    case CryptoAlgs::SHA256:
		      key.hmac_alg = OVPN_ALG_HMAC_SHA256;
		      break;
		    default:
		      OPENVPN_THROW(korekey_error, "HMAC alg " << halg.name() << " is not currently supported by kovpn");
		    }
		  key.encrypt.hmac_key_size = halg.size();
		  key.decrypt.hmac_key_size = key.encrypt.hmac_key_size;

		  // set hmac keys
		  key.encrypt.hmac_key = verify_key("hmac encrypt",
						    rkinfo.encrypt_hmac,
						    key.encrypt.hmac_key_size);
		  key.decrypt.hmac_key = verify_key("hmac decrypt",
						    rkinfo.decrypt_hmac,
						    key.decrypt.hmac_key_size);

		  // handle compression V1
		  switch (rkinfo.comp_ctx.type())
		    {
		    case CompressContext::LZO_STUB:
		      key.compress.alg = OVPN_COMP_NONE;
		      key.compress.swap = false;
		      break;
		    case CompressContext::COMP_STUB:
		      key.compress.alg = OVPN_COMP_NONE;
		      key.compress.swap = true;
		      break;
		    case CompressContext::LZ4:
		      key.compress.alg = OVPN_COMP_LZ4;
		      key.compress.swap = true;
		      break;
		    default:
		      OPENVPN_THROW(korekey_error, "Compression alg " << rkinfo.comp_ctx.str() << " is not supported by kovpn in CBC/HMAC mode");
		    }
		  break;
		}
	      case CryptoAlgs::AEAD:
		{
		  // if AEAD mode, copy nonce tail from the HMAC key material
		  set_nonce_tail("AEAD nonce tail encrypt",
				 key.encrypt.nonce_tail,
				 sizeof(key.encrypt.nonce_tail),
				 rkinfo.encrypt_hmac);
		  set_nonce_tail("AEAD nonce tail decrypt",
				 key.decrypt.nonce_tail,
				 sizeof(key.decrypt.nonce_tail),
				 rkinfo.decrypt_hmac);

		  // handle compression V2
		  switch (rkinfo.comp_ctx.type())
		    {
		    case CompressContext::COMP_STUBv2:
		      key.compress.alg = OVPN_COMP_NONE;
		      break;
		    case CompressContext::LZ4v2:
		      key.compress.alg = OVPN_COMP_LZ4;
		      break;
		    default:
		      OPENVPN_THROW(korekey_error, "Compression alg " << rkinfo.comp_ctx.str() << " is not supported by kovpn in AEAD mode");
		    }
		  key.compress.swap = false;

		  break;
		}
	      default:
		{
		  // should have been caught above
		  throw korekey_error("internal error");
		}
	      }

	    // handle compression
	    key.compress.asym = rkinfo.comp_ctx.asym();
	    key.compress.max_decompress_size = (*rkinfo.frame)[Frame::DECOMPRESS_WORK].payload();

	    // handle TCP linear
	    key.tcp_linear = rkinfo.tcp_linear;

	    if (verbose)
	      OPENVPN_LOG("KOREKEY"
	                << " op=" << int(rktype) << '/' << opk.op
			<< " rpid=" << key.remote_peer_id
			<< " pri=" << key.key_id
			<< " cipher=" << key.cipher_alg
			<< "[e=" << render_hex(key.encrypt.cipher_key, 8)
			<< " d=" << render_hex(key.decrypt.cipher_key, 8) << ']'
			<< " hmac=" << key.hmac_alg
			<< "[e=" << render_hex(key.encrypt.hmac_key, 8)
			<< " d=" << render_hex(key.decrypt.hmac_key, 8) << ']'
			<< " comp=" << key.compress.alg
			<< " swap=" << key.compress.swap
			<< " asym=" << key.compress.asym
			<< " tcp_linear=" << key.tcp_linear
			<< " dl=[e=" << key.encrypt.data_limit
			<<     " d=" << key.decrypt.data_limit << ']');

	    // set key
	    if (secondary_key)
	      opk.secondary = &key;
	    else
	      opk.primary = &key;
	  }
	else if (verbose)
	  {
	    OPENVPN_LOG("KOREKEY" << " op=" << int(rktype) << '/' << opk.op);
	  }
      }

      const struct ovpn_peer_keys_reset *operator()() const
      {
	return &opk;
      }

    private:
      const unsigned char *verify_key(const char *title, const StaticKey& sk, const size_t size_required)
      {
	if (sk.size() < size_required)
	  OPENVPN_THROW(korekey_error, title << ": insufficient key material, provided=" << sk.size() << " required=" << size_required);
	return sk.data();
      }

      void set_nonce_tail(const char *title, unsigned char *dest, const size_t dest_size, const StaticKey& src)
      {
	const int NONCE_TAIL_SIZE = CryptoAlgs::AEAD_NONCE_TAIL_SIZE;

	const unsigned char *k = verify_key(title, src, NONCE_TAIL_SIZE);
	if (dest_size < NONCE_TAIL_SIZE)
	  OPENVPN_THROW(korekey_error, title << ": cannot set");
	std::memcpy(dest, k, NONCE_TAIL_SIZE);

	// if dest is larger than NONCE_TAIL_SIZE, zero remaining bytes
	if (dest_size > NONCE_TAIL_SIZE)
	  std::memset(dest + NONCE_TAIL_SIZE, 0, dest_size - NONCE_TAIL_SIZE);
      }

      struct ovpn_peer_keys_reset opk;
      struct ovpn_key_config key;
    };

  }
}

#endif
