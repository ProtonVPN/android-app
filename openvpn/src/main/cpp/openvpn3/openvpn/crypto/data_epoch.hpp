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


#ifndef CRYPTO_DATA_EPOCH_H
#define CRYPTO_DATA_EPOCH_H

#include <cstdint>
#include <cstdio>
#include <array>

#include "openvpn/crypto/static_key.hpp"
#include "openvpn/crypto/cryptoalgs.hpp"
#include "openvpn/crypto/cryptochoose.hpp"
#include "openvpn/crypto/packet_id_data.hpp"
#include "openvpn/crypto/aead_usage_limit.hpp"


namespace openvpn {
/**
 * Implementation of the RFC5869 HKDF-Expand function with the following
 * restrictions
 *  - salt is always assumed to be zero length (ie not supported)
 *  - IKM (secret) is assumed to be always 32 bytes
 *  - HASH is always SHA256
 *
 *  @param secret   the input keying material (HMAC key)
 *  @param info     context and application specific information
 *  @param info_len length of the application specific information
 *  @param out      output keying material
 *  @param out_len  length of output keying material
 */
void ovpn_hkdf_expand(const uint8_t *secret,
                      const uint8_t *info,
                      int info_len,
                      uint8_t *out,
                      int out_len);

/**
 * Variant of the RFC 8446 TLS 1.3  HKDF-Expand-Label function with the
 * following differences/restrictions:
 *  - secret must 32 bytes in length
 *  - label prefix is "ovpn " instead of "tls13 "
 *  - HASH is always SHA256
 *
 * @param secret        Input secret
 * @param secret_len    length of the input secret
 * @param label         Label for the exported key material
 * @param label_len     length of the label
 * @param context       optional context
 * @param context_len   length of the context
 * @param out      output keying material
 * @param out_len  length of output keying material
 *
 * Note, this function accepts size_t parameter only to make using this function easier. All values must be
 * uin16_t or smaller.
 */
void ovpn_expand_label(const uint8_t *secret,
                       size_t secret_len,
                       const uint8_t *label,
                       size_t label_len,
                       const uint8_t *context,
                       size_t context_len,
                       uint8_t *out,
                       size_t out_len);

OPENVPN_EXCEPTION(epoch_key_exception);

struct EpochDataChannelCryptoContext
{
    /** The IV size in bytes. Since all currently supported AEAD ciphers uses 96 bits, we hardcode it for now */
    constexpr static int IV_SIZE = 12;

    std::uint16_t epoch = 0;
    openvpn::SSLLib::CryptoAPI::CipherContextAEAD cipher;
    std::array<uint8_t, IV_SIZE> implicit_iv{};

    /* will calculate the ID from the packet id and the implicit IV and store the result in
     * the iv_dest parameter */
    void calculate_iv(uint8_t *packet_id, std::array<uint8_t, IV_SIZE> &iv_dest);
};


struct EpochDataChannelEncryptContext : public EpochDataChannelCryptoContext
{
    openvpn::PacketIDDataSend pid{true, 0};
};

struct EpochDataChannelDecryptContext : public EpochDataChannelCryptoContext
{
    openvpn::PacketIDDataReceive pid;
};

class EpochKey
{
  public:
    /* SHA256 digest size */
    constexpr static int SECRET_SIZE = 32;

    std::array<uint8_t, SECRET_SIZE> keydata{};
    std::uint16_t epoch = 0;


    /* Constructs a default epoch that is not initialised. Epoch 0 doubles as
     * marker of an uninitialised key */
    EpochKey() = default;

    /** Constructs an epoch key with the given key material and epoch */
    EpochKey(decltype(keydata) keydata, uint16_t epoch)
        : keydata(keydata), epoch(epoch)
    {
    }

    /** Constructs an epoch key with the given OpenVPNStaticKey as epoch 1 key. \param key is assumed to be already
     * prepared as the correct slice of the Data channel key using key.slice */
    EpochKey(StaticKey key);

    /**
     * Iterates the epoch key to make it E_n+1, ie increase the epoch by one
     * and derive the new key material accordingly
     */
    void iterate();

    /** Derives the data channel keys that are tied to the current epoch.
     * @return Key material for the encryption/decryption key and the implicit IV material
     * */
    std::pair<StaticKey, StaticKey> data_key(openvpn::CryptoAlgs::Type cipher);

    /** Generate a context that can be used to encrypt or decrypt using this epoch */
    EpochDataChannelCryptoContext key_context(openvpn::SSLLib::Ctx libctx, openvpn::CryptoAlgs::Type cipher, int mode);
};

class DataChannelEpoch
{
  protected:
    /** Cipher to use to generate the keys */
    openvpn::CryptoAlgs::Type cipher;

    /** TLS library context to initialise the ciphers */
    SSLLib::Ctx libctx;

    /** Usage limit (q+s) for plaintext blocks + number of invocations */

    /** the number of future receive keys that we calculate in advance */
    uint16_t future_keys_count;

    EpochDataChannelEncryptContext encrypt_ctx{};

    EpochDataChannelDecryptContext decrypt_ctx{};

    EpochDataChannelDecryptContext retiring_decrypt_ctx{};


    std::vector<EpochDataChannelDecryptContext> future_keys;


    /** The key used to generate the last  send data channel keys */
    EpochKey send{};

    /** The key used to generate the last receive data channel keys */
    EpochKey receive{};

    void generate_future_receive_keys();

    void generate_encrypt_ctx();

  public:
    /**
     * Forces the use of a new epoch key for sending
     */
    void iterate_send_key();


    /**
     * Returns the number of future receive keys that this will consider as validate candidates for decryption
     */
    uint16_t get_future_keys_count()
    {
        return future_keys_count;
    }


    /**
     * Check if the VPN session should be renegotiated to generate new epoch send/receive keys
     */
    bool should_renegotiate()
    {
        return send.epoch > 0xFF00;
    }

    DataChannelEpoch() = default;

    DataChannelEpoch(decltype(cipher) cipher, openvpn::StaticKey e1send, openvpn::StaticKey e1recv, SSLLib::Ctx libctx = nullptr, uint16_t future_key_count = 16);

    void replace_update_recv_key(std::uint16_t new_epoch, const SessionStats::Ptr &stats_arg);

    /**
     * Checks if the send epoch needs to be iterated and update the encryption context if needed
     */
    void check_send_iterate();

    /**
     * Using an epoch, this function will try to retrieve a decryption
     * key context that matches that epoch from the \c opt argument
     * @param epoch     epoch of the key to lookup
     * @return          the key context with
     */
    EpochDataChannelDecryptContext *lookup_decrypt_key(uint16_t epoch);

    /**
     * Return the context that should be used to encrypt packets
     */
    EpochDataChannelEncryptContext &encrypt()
    {
        return encrypt_ctx;
    }
};



}; // namespace openvpn

#endif // CRYPTO_DATA_EPOCH_H
