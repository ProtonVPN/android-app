//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2021 OpenVPN Inc.
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
//

#include <string>

#include "test_common.h"
#include "openvpn/ssl/peer_fingerprint.hpp"

using namespace openvpn;

namespace unittests {

std::vector<uint8_t> test_fingerprint = {
  0x44, 0xF5, 0xA6, 0x4D, 0x4A, 0xCB, 0x65, 0xE1, 0x8A, 0x9F, 0x55, 0x89, 0x7F, 0x77, 0xA0, 0x79,
  0xAA, 0xFB, 0xCC, 0xA1, 0x37, 0x2F, 0xD8, 0xB3, 0x47, 0xAA, 0x9D, 0xE3, 0xD0, 0x76, 0xB1, 0x44
};

TEST(PeerFingerprint, parse_config) {
  OptionList cfg;
  cfg.parse_from_config(
    "peer-fingerprint 01:F5:A6:4D:4A:CB:65:E1:8A:9F:55:89:7F:77:A0:79:AA:FB:CC:A1:37:2F:D8:B3:47:AA:9D:E3:D0:76:B1:44\n"
    "<peer-fingerprint>\n"
    "02:F5:A6:4D:4A:CB:65:E1:8A:9F:55:89:7F:77:A0:79:AA:FB:CC:A1:37:2F:D8:B3:47:AA:9D:E3:D0:76:B1:44\n"
    "03:F5:A6:4D:4A:CB:65:E1:8A:9F:55:89:7F:77:A0:79:AA:FB:CC:A1:37:2F:D8:B3:47:AA:9D:E3:D0:76:B1:44 \n"
    "04:F5:A6:4D:4A:CB:65:E1:8A:9F:55:89:7F:77:A0:79:AA:FB:CC:A1:37:2F:D8:B3:47:AA:9D:E3:D0:76:B1:44  \n"
    "</peer-fingerprint>\n"
    "peer-fingerprint 05:F5:A6:4D:4A:CB:65:E1:8A:9F:55:89:7F:77:A0:79:AA:FB:CC:A1:37:2F:D8:B3:47:AA:9D:E3:D0:76:B1:44 \n"
    , nullptr);
  cfg.update_map();

  struct TestPeerFingerprints : public PeerFingerprints
  {
    TestPeerFingerprints(const OptionList& opt, std::size_t fp_size)
      : PeerFingerprints(opt, fp_size) {}
    std::size_t size() { return fingerprints_.size(); }
  };

  TestPeerFingerprints fps(cfg, test_fingerprint.size());
  ASSERT_EQ(fps.size(), 5);

  for (std::size_t i = 1; i <= fps.size(); ++i)
    {
      auto fingerprint = test_fingerprint;
      fingerprint[0] = i;
      PeerFingerprint fp(fingerprint);
      ASSERT_EQ(fps.match(fp), true);
    }
}


TEST(PeerFingerprint, malformed) {
  OptionList cfg;
  cfg.parse_from_config(
    "peer-fingerprint 01:F5:A6:4D:4A:CB:65:E1:8A:9F:55:89:7F:77:A0:79:AA:FB:CC:A1:37:2F:D8:B3:47:AA:9D:E3:D0:76:B1\n"
    , nullptr);
  cfg.update_map();
  EXPECT_THROW(PeerFingerprints(cfg, test_fingerprint.size()), option_error);

  cfg.clear();
  cfg.parse_from_config(
    "peer-fingerprint 01:F5:A6:4D:4A:CB:65:E1:8A:9F:55:89:7F:77:A0:79:AA:FB:CC:A1:37:2F:D8:B3:47:AA:9D:E3:D0:76:B1:55:FF\n"
    , nullptr);
  cfg.update_map();
  EXPECT_THROW(PeerFingerprints(cfg, test_fingerprint.size()), option_error);

  cfg.clear();
  cfg.parse_from_config(
    "peer-fingerprint 101:F5:A6:4D:4A:CB:65:E1:8A:9F:55:89:7F:77:A0:79:AA:FB:CC:A1:37:2F:D8:B3:47:AA:9D:E3:D0:76:B1:55\n"
    , nullptr);
  cfg.update_map();
  EXPECT_THROW(PeerFingerprints(cfg, test_fingerprint.size()), option_error);

  cfg.clear();
  cfg.parse_from_config(
    "peer-fingerprint 11:F5:A6:4D:4A:1CB:65:E1:8A:9F:55:89:7F:77:A0:79:AA:FB:CC:A1:37:2F:D8:B3:47:AA:9D:E3:D0:76:B1:55\n"
    , nullptr);
  cfg.update_map();
  EXPECT_THROW(PeerFingerprints(cfg, test_fingerprint.size()), option_error);

  cfg.clear();
  cfg.parse_from_config(
    "peer-fingerprint 11/F5/A6/4D/4A/CB/65/E1/8A/9F/55/89/7F/77/A0/79/AA/FB/CC/A1/37/2F/D8/B3/47/AA/9D/E3/D0/76/B1/55\n"
    , nullptr);
  cfg.update_map();
  EXPECT_THROW(PeerFingerprints(cfg, test_fingerprint.size()), option_error);

  cfg.clear();
  cfg.parse_from_config(
    "<peer-fingerprint>\n"
    " 02:F5:A6:4D:4A:CB:65:E1:8A:9F:55:89:7F:77:A0:79:AA:FB:CC:A1:37:2F:D8:B3:47:AA:9D:E3:D0:76:B1:44\n"
    "</peer-fingerprint>\n"
    , nullptr);
  cfg.update_map();
  EXPECT_THROW(PeerFingerprints(cfg, test_fingerprint.size()), option_error);
}


TEST(PeerFingerprint, stringify) {
  std::string hex_fp("01:f5:a6:4d:4a:cb:65:e1:8a:9f:55:89:7f:77:a0:79:aa:fb:cc:a1:37:2f:d8:b3:47:aa:9d:e3:d0:76:b1:55");
  PeerFingerprint fp(hex_fp, test_fingerprint.size());
  ASSERT_EQ(fp.str(), hex_fp);
}


TEST(PeerFingerprint, match_empty) {
  PeerFingerprint fp(test_fingerprint);
  PeerFingerprints fps;
  ASSERT_FALSE(fps);
  ASSERT_EQ(fps.match(fp), false);
}


TEST(PeerFingerprint, match) {
  OptionList cfg;
  cfg.parse_from_config(
    "<peer-fingerprint>\n"
    "A4:E5:A7:1D:AA:E3:65:E1:3A:6E:45:89:80:66:A0:79:BB:E3:EC:41:34:2F:08:83:97:AA:91:33:DF:11:31:AA\n"
    "44:F5:A6:4D:4A:CB:65:E1:8A:9F:55:89:7F:77:A0:79:AA:FB:CC:A1:37:2F:D8:B3:47:AA:9D:E3:D0:76:B1:44\n"
    "</peer-fingerprint>\n"
    , nullptr);
  cfg.update_map();

  PeerFingerprint fp(test_fingerprint);
  PeerFingerprints fps(cfg, test_fingerprint.size());
  ASSERT_EQ(fps.match(fp), true);
}

TEST(PeerFingerprint, no_match) {
  OptionList cfg;
  cfg.parse_from_config(
    "peer-fingerprint A4:E5:A7:1D:AA:E3:65:E1:3A:6E:45:89:80:66:A0:79:BB:E3:EC:41:34:2F:08:83:97:AA:91:33:DF:11:31:AA\n"
    , nullptr);
  cfg.update_map();

  PeerFingerprint fp(test_fingerprint);
  PeerFingerprints fps(cfg, test_fingerprint.size());
  ASSERT_EQ(fps.match(fp), false);
}


}  // namespace unittests
