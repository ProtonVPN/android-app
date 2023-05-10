//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2021-2022 OpenVPN Inc.
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

#include <iostream>

#include "test_common.h"

#include <openvpn/ssl/sslchoose.hpp>
#include <openvpn/crypto/cryptoalgs.hpp>
#include <openvpn/crypto/crypto_aead.hpp>


static uint8_t testkey[20] = {0x0b, 0x00};
static uint8_t goodhash[20] = {
    // clang-format off
    0x58, 0xea, 0x5a, 0xf0, 0x42, 0x94, 0xe9, 0x17,
    0xed, 0x84, 0xb9, 0xf0, 0x83, 0x30, 0x23, 0xae,
    0x8b, 0xa7, 0x7e, 0xb8
    // clang-format on
};

static const char *ipsumlorem = "Lorem ipsum dolor sit amet, consectetur "
                                "adipisici elit, sed eiusmod tempor incidunt "
                                "ut labore et dolore magna aliqua.";

TEST(crypto, hmac)
{
    uint8_t key[20];
    std::memcpy(key, testkey, sizeof(key));

    openvpn::SSLLib::CryptoAPI::HMACContext hmac(openvpn::CryptoAlgs::SHA1, key, sizeof(key));

    const uint8_t *ipsum = reinterpret_cast<const uint8_t *>(ipsumlorem);

    hmac.update(ipsum, std::strlen(ipsumlorem));
    hmac.update(ipsum, std::strlen(ipsumlorem));

    uint8_t hash[20];

    ASSERT_EQ(hmac.final(hash), 20u);

    /* Google test does not seem to have a good memory equality test macro */
    ASSERT_EQ(std::memcmp(hash, goodhash, sizeof(goodhash)), 0);

    hmac.reset();

    /* Do this again to ensure that reset works */
    hmac.update(ipsum, std::strlen(ipsumlorem));
    hmac.update(ipsum, std::strlen(ipsumlorem));
    ASSERT_EQ(hmac.final(hash), 20u);

    /* Google test does not seem to have a good memory equality test macro */
    ASSERT_EQ(std::memcmp(hash, goodhash, sizeof(goodhash)), 0);

    /* Overwrite the key to ensure that the memory is no referenced by internal
     * structs of the hmac */
    std::memset(key, 0x55, sizeof(key));

    hmac.reset();

    /* Do this again to ensure that reset works */
    hmac.update(ipsum, std::strlen(ipsumlorem));
    hmac.update(ipsum, std::strlen(ipsumlorem));
    ASSERT_EQ(hmac.final(hash), 20u);

    /* Google test does not seem to have a good memory equality test macro */
    ASSERT_EQ(std::memcmp(hash, goodhash, sizeof(goodhash)), 0);
}

static openvpn::Frame::Context frame_ctx()
{
    const size_t payload = 2048;
    const size_t headroom = 64;
    const size_t tailroom = 64;
    const size_t align_block = 16;
    const unsigned int buffer_flags = 0;
    return openvpn::Frame::Context{headroom, payload, tailroom, 0, align_block, buffer_flags};
}


TEST(crypto, dcaead)
{

    auto frameptr = openvpn::Frame::Ptr{new openvpn::Frame{frame_ctx()}};
    auto statsptr = openvpn::SessionStats::Ptr{new openvpn::SessionStats{}};

    openvpn::AEAD::Crypto<openvpn::SSLLib::CryptoAPI> cryptodc{nullptr, openvpn::CryptoAlgs::AES_256_GCM, frameptr, statsptr};

    const char *plaintext = "The quick little fox jumps over the bureaucratic hurdles";

    const uint8_t key[] = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', '0', '1', '2', '3', '4', '5', '6', '7', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'j', 'k', 'u', 'c', 'h', 'e', 'n', 'l'};

    static_assert(sizeof(key) == 32, "Size of key should be 32 bytes");

    /* copy the key a few times to ensure to have the size we need for
     * Statickey but XOR it to not repeat it */
    uint8_t bigkey[openvpn::OpenVPNStaticKey::KEY_SIZE]{};

    for (int i = 0; i < openvpn::OpenVPNStaticKey::KEY_SIZE; i++)
    {
        bigkey[i] = key[i % sizeof(key)] ^ i;
    }

    openvpn::StaticKey const static_bigkey{bigkey, openvpn::OpenVPNStaticKey::KEY_SIZE};
    openvpn::StaticKey static_en_key{key, sizeof(key)};
    openvpn::StaticKey static_de_key = static_en_key;

    /* StaticKey implements all implicit copy and move operations as it
       explicitly defines none of them nor does it explicitly define a dtor */
    cryptodc.init_cipher(std::move(static_en_key), std::move(static_de_key));
    cryptodc.init_pid(openvpn::PacketID::SHORT_FORM,
                      0,
                      openvpn::PacketID::SHORT_FORM,
                      "DATA",
                      0,
                      statsptr);

    openvpn::BufferAllocated work{2048, 0};

    /* reserve some headroom */
    work.realign(128);

    std::memcpy(work.write_alloc(std::strlen(plaintext)), plaintext, std::strlen(plaintext));
    const unsigned char *data = work.data();
    EXPECT_TRUE(std::memcmp(data, plaintext, std::strlen(plaintext)) == 0);

    const openvpn::PacketID::time_t now = 42;

    const unsigned char op32[]{7, 0, 0, 23};

    bool const wrapwarn = cryptodc.encrypt(work, now, op32);
    ASSERT_FALSE(wrapwarn);

    /* 16 for tag, 4 for IV */
    EXPECT_EQ(work.size(), std::strlen(plaintext) + 4 + 16);

    const uint8_t expected_tag[16]{0xe0, 0xa7, 0x19, '*', 0x89, ']', 0x1d, 0x90, 0xc9, 0xd6, '\n', 0xee, '8', 'z', 0x01, 0xbd};
    // Packet id/IV should 1
    uint8_t packetid1[]{0, 0, 0, 1};
    EXPECT_TRUE(std::memcmp(work.data(), packetid1, 4) == 0);

    // Tag is in the front after packet id
    EXPECT_TRUE(std::memcmp(work.data() + 4, expected_tag, 16) == 0);

    // Check a few random bytes of the encrypted output
    const uint8_t bytesat30[6]{0x52, 0x2e, 0xbf, 0xdf, 0x24, 0x1c};
    EXPECT_TRUE(std::memcmp(work.data() + 30, bytesat30, 6) == 0);

    /* Check now if decrypting also works */
    auto ret = cryptodc.decrypt(work, now, op32);

    EXPECT_EQ(ret, openvpn::Error::SUCCESS);
    EXPECT_EQ(work.size(), std::strlen(plaintext));

    EXPECT_TRUE(std::memcmp(work.data(), plaintext, std::strlen(plaintext)) == 0);
}
