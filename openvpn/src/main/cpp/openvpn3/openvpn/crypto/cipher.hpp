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

// General-purpose cipher classes that are independent of the underlying CRYPTO_API

#ifndef OPENVPN_CRYPTO_CIPHER_H
#define OPENVPN_CRYPTO_CIPHER_H

#include <string>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/crypto/static_key.hpp>
#include <openvpn/crypto/cryptoalgs.hpp>
#include <openvpn/crypto/definitions.hpp>

namespace openvpn {
  template <typename CRYPTO_API>
  class CipherContext
  {
  public:
    OPENVPN_SIMPLE_EXCEPTION(cipher_mode_error);
    OPENVPN_SIMPLE_EXCEPTION(cipher_uninitialized);
    OPENVPN_SIMPLE_EXCEPTION(cipher_init_insufficient_key_material);
    OPENVPN_SIMPLE_EXCEPTION(cipher_internal_error);
    OPENVPN_SIMPLE_EXCEPTION(cipher_output_buffer);

  public:
    CipherContext() : mode_(CRYPTO_API::CipherContext::MODE_UNDEF) {}

    CipherContext(const CryptoAlgs::Type cipher, const StaticKey& key, const int mode)
      : mode_(CRYPTO_API::CipherContext::MODE_UNDEF)
    {
      init(cipher, key, mode);
    }

    bool defined() const { return ctx.is_initialized(); }

    // size of iv buffer to pass to encrypt_decrypt
    size_t iv_length() const
    {
      return ctx.iv_length();
    }

    // cipher mode (such as CIPH_CBC_MODE, etc.)
    int cipher_mode() const
    {
      return ctx.cipher_mode();
    }

    // size of out buffer to pass to encrypt_decrypt
    size_t output_size(const size_t in_size) const
    {
      return in_size + ctx.block_size();
    }

    void init(SSLLib::Ctx libctx, const CryptoAlgs::Type cipher, const StaticKey& key, const int mode)
    {
      const CryptoAlgs::Alg& alg = CryptoAlgs::get(cipher);

      // check that provided key is large enough
      if (key.size() < alg.key_length())
	throw cipher_init_insufficient_key_material();

      // IV consistency check
      if (alg.iv_length() > CRYPTO_API::CipherContext::MAX_IV_LENGTH)
	throw cipher_internal_error();

      // initialize cipher context with cipher type, key, and encrypt/decrypt mode
      ctx.init(libctx, cipher, key.data(), mode);

      // save mode in object
      mode_ = mode;
    }

    size_t encrypt(const unsigned char *iv,
		   unsigned char *out, const size_t out_size,
		   const unsigned char *in, const size_t in_size)
    {
      if (mode_ != CRYPTO_API::CipherContext::ENCRYPT)
	throw cipher_mode_error();
      return encrypt_decrypt(iv, out, out_size, in, in_size);
    }

    size_t decrypt(const unsigned char *iv,
		   unsigned char *out, const size_t out_size,
		   const unsigned char *in, const size_t in_size)
    {
      if (mode_ != CRYPTO_API::CipherContext::DECRYPT)
	throw cipher_mode_error();
      return encrypt_decrypt(iv, out, out_size, in, in_size);
    }

    size_t encrypt_decrypt(const unsigned char *iv,
			   unsigned char *out, const size_t out_size,
			   const unsigned char *in, const size_t in_size)
    {
      if (out_size < output_size(in_size))
	throw cipher_output_buffer();
      ctx.reset(iv);
      size_t outlen = 0;
      if (!ctx.update(out, out_size, in, in_size, outlen))
	return 0;
      if (!ctx.final(out + outlen, out_size - outlen, outlen))
	return 0;
      return outlen;
    }

  private:
    int mode_;
    typename CRYPTO_API::CipherContext ctx;
  };

} // namespace openvpn

#endif // OPENVPN_CRYPTO_CIPHER_H
