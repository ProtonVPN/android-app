//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2021 OpenVPN Inc.
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


static uint8_t testkey[20] = {0x0b, 0x00};
static uint8_t goodhash[20] = {0x58, 0xea, 0x5a, 0xf0, 0x42, 0x94, 0xe9, 0x17,
			       0xed, 0x84, 0xb9, 0xf0, 0x83, 0x30, 0x23, 0xae,
			       0x8b, 0xa7, 0x7e, 0xb8};

static const char* ipsumlorem = "Lorem ipsum dolor sit amet, consectetur "
				"adipisici elit, sed eiusmod tempor incidunt "
				"ut labore et dolore magna aliqua.";

TEST(crypto, hmac)
{
  uint8_t key[20];
  std::memcpy(key, testkey, sizeof(key));

  openvpn::SSLLib::CryptoAPI::HMACContext hmac(openvpn::CryptoAlgs::SHA1, key, sizeof(key));

  const uint8_t *ipsum = reinterpret_cast<const uint8_t*>(ipsumlorem);

  hmac.update(ipsum, std::strlen(ipsumlorem));
  hmac.update(ipsum, std::strlen(ipsumlorem));

  uint8_t hash[20];

  ASSERT_EQ(hmac.final(hash), 20);

  /* Google test does not seem to have a good memory equality test macro */
  ASSERT_EQ(std::memcmp(hash, goodhash, sizeof(goodhash)), 0);

  hmac.reset();

  /* Do this again to ensure that reset works */
  hmac.update(ipsum, std::strlen(ipsumlorem));
  hmac.update(ipsum, std::strlen(ipsumlorem));
  ASSERT_EQ(hmac.final(hash), 20);

  /* Google test does not seem to have a good memory equality test macro */
  ASSERT_EQ(std::memcmp(hash, goodhash, sizeof(goodhash)), 0);

  /* Overwrite the key to ensure that the memory is no referenced by internal
   * structs of the hmac */
  std::memset(key, 0x55, sizeof(key));

  hmac.reset();

  /* Do this again to ensure that reset works */
  hmac.update(ipsum, std::strlen(ipsumlorem));
  hmac.update(ipsum, std::strlen(ipsumlorem));
  ASSERT_EQ(hmac.final(hash), 20);

  /* Google test does not seem to have a good memory equality test macro */
  ASSERT_EQ(std::memcmp(hash, goodhash, sizeof(goodhash)), 0);
}
