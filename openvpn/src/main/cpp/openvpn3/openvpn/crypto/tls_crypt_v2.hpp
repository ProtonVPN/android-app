//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2017-2018 OpenVPN Technologies, Inc.
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License Version 3
//    as published by the Free Software Foundation.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program in the COPYING file.
//    If not, see <http://www.gnu.org/licenses/>.

// Classes for handling OpenVPN tls-crypt-v2 internals

#ifndef OPENVPN_CRYPTO_TLS_CRYPT_V2_H
#define OPENVPN_CRYPTO_TLS_CRYPT_V2_H

#include <string>

#include <openvpn/common/exception.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/crypto/static_key.hpp>
#include <openvpn/crypto/tls_crypt.hpp>
#include <openvpn/ssl/sslchoose.hpp>

namespace openvpn {
constexpr static const char *tls_crypt_v2_server_key_name = "OpenVPN tls-crypt-v2 server key";
constexpr static const char *tls_crypt_v2_client_key_name = "OpenVPN tls-crypt-v2 client key";

class TLSCryptV2ServerKey
{
  public:
    OPENVPN_SIMPLE_EXCEPTION(tls_crypt_v2_server_key_parse_error);
    OPENVPN_SIMPLE_EXCEPTION(tls_crypt_v2_server_key_encode_error);
    OPENVPN_SIMPLE_EXCEPTION(tls_crypt_v2_server_key_bad_size);

    TLSCryptV2ServerKey()
        : key_size(128),
          key(key_size, BufferAllocated::DESTRUCT_ZERO)
    {
    }

    bool defined() const
    {
        return key.defined();
    }

    void parse(const std::string &key_text)
    {
        if (!SSLLib::PEMAPI::pem_decode(key, key_text.c_str(), key_text.length(), tls_crypt_v2_server_key_name))
            throw tls_crypt_v2_server_key_parse_error();

        if (key.size() != key_size)
            throw tls_crypt_v2_server_key_bad_size();
    }

    void extract_key(OpenVPNStaticKey &tls_key)
    {
        std::memcpy(tls_key.raw_alloc(), key.c_data(), key_size);
    }

    std::string render() const
    {
        BufferAllocated data(32 + 2 * key.size(), 0);

        if (!SSLLib::PEMAPI::pem_encode(data, key.c_data(), key.size(), tls_crypt_v2_server_key_name))
            throw tls_crypt_v2_server_key_encode_error();

        return std::string((const char *)data.c_data());
    }

  private:
    const size_t key_size;
    BufferAllocated key;
};


class TLSCryptV2ClientKey
{
  public:
    enum
    {
        WKC_MAX_SIZE = 1024, // bytes
    };

    OPENVPN_SIMPLE_EXCEPTION(tls_crypt_v2_client_key_parse_error);
    OPENVPN_SIMPLE_EXCEPTION(tls_crypt_v2_client_key_encode_error);
    OPENVPN_SIMPLE_EXCEPTION(tls_crypt_v2_client_key_bad_size);

    TLSCryptV2ClientKey() = delete;

    TLSCryptV2ClientKey(TLSCryptContext::Ptr context)
        : key_size(OpenVPNStaticKey::KEY_SIZE),
          tag_size(context->digest_size())
    {
    }

    bool defined() const
    {
        return key.defined() && wkc.defined();
    }

    void parse(const std::string &key_text)
    {
        BufferAllocated data(key_size + WKC_MAX_SIZE, BufferAllocated::DESTRUCT_ZERO);

        if (!SSLLib::PEMAPI::pem_decode(data, key_text.c_str(), key_text.length(), tls_crypt_v2_client_key_name))
            throw tls_crypt_v2_client_key_parse_error();

        if (data.size() < (tag_size + key_size))
            throw tls_crypt_v2_client_key_bad_size();

        key.init(data.data(), key_size, BufferAllocated::DESTRUCT_ZERO);
        wkc.init(data.data() + key_size, data.size() - key_size, BufferAllocated::DESTRUCT_ZERO);
    }

    void extract_key(OpenVPNStaticKey &tls_key)
    {
        std::memcpy(tls_key.raw_alloc(), key.c_data(), key_size);
    }

    std::string render() const
    {
        BufferAllocated data(32 + 2 * (key.size() + wkc.size()), 0);
        BufferAllocated in(key, BufferAllocated::GROW);
        in.append(wkc);

        if (!SSLLib::PEMAPI::pem_encode(data, in.c_data(), in.size(), tls_crypt_v2_client_key_name))
            throw tls_crypt_v2_client_key_encode_error();

        return std::string((const char *)data.c_data());
    }

    void extract_wkc(BufferAllocated &wkc_out) const
    {
        wkc_out = wkc;
    }

  private:
    BufferAllocated key;
    BufferAllocated wkc;

    const size_t key_size;
    const size_t tag_size;
};

// the user can extend the TLSCryptMetadata and the TLSCryptMetadataFactory
// classes to implement its own metadata verification method.
//
// default method is to *ignore* the metadata contained in the WKc sent by the client
class TLSCryptMetadata : public RC<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<TLSCryptMetadata> Ptr;

    // override this method with your own verification mechanism.
    //
    // If type is -1 it means that metadata is empty.
    //
    virtual bool verify(int type, Buffer &metadata) const
    {
        return true;
    }
};

// abstract class to be extended when creating other factories
class TLSCryptMetadataFactory : public RC<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<TLSCryptMetadataFactory> Ptr;

    virtual TLSCryptMetadata::Ptr new_obj() = 0;
};

// factory implementation for the basic verification method
class CryptoTLSCryptMetadataFactory : public TLSCryptMetadataFactory
{
  public:
    TLSCryptMetadata::Ptr new_obj()
    {
        return new TLSCryptMetadata();
    }
};
} // namespace openvpn

#endif /* OPENVPN_CRYPTO_TLS_CRYPT_V2_H */
