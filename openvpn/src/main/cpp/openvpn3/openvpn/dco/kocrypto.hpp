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

// kovpn crypto wrappers

#pragma once

#include <cstring> // for std::memset, std::memcpy
#include <utility> // for std::move

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/frame/frame.hpp>
#include <openvpn/crypto/cryptodc.hpp>
#include <openvpn/crypto/bs64_data_limit.hpp>

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
#ifdef ENABLE_OVPNDCO
	  case CryptoAlgs::CHACHA20_POLY1305:
	  case CryptoAlgs::NONE:
#endif
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
#ifdef ENABLE_OVPNDCO
	      case CryptoAlgs::NONE:
#endif
		break;
	      default:
		OPENVPN_THROW(korekey_error, "HMAC alg " << halg.name() << " is not currently supported by kovpn");
	      }
	  }
      }

      Key() {}

    protected:
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
    };
  }
}

#ifdef ENABLE_KOVPN
#include <openvpn/kovpn/kovpnkocrypto.hpp>
#elif defined(ENABLE_OVPNDCO) || defined(ENABLE_OVPNDCOWIN)
#include <openvpn/dco/ovpndcokocrypto.hpp>
#else
#error either ENABLE_KOVPN, ENABLE_OVPNDCO or ENABLE_OVPNDCOWIN must be defined
#endif
