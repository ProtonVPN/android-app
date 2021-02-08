//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
//    Copyright (C) 2019-2020 David Sommerseth <davids@openvpn.net>
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

#include <iostream>
#include "test_common.h"
#include "openvpn/client/cliconstants.hpp"
#include "openvpn/common/options.hpp"
#include "openvpn/ssl/verify_x509_name.hpp"

using namespace openvpn;

namespace unittests {

OptionList parse_testcfg(std::string& config) {
  OptionList::Limits limits(
      "profile is too large", ProfileParseLimits::MAX_PROFILE_SIZE,
      ProfileParseLimits::OPT_OVERHEAD, ProfileParseLimits::TERM_OVERHEAD,
      ProfileParseLimits::MAX_LINE_SIZE,
      ProfileParseLimits::MAX_DIRECTIVE_SIZE);
  OptionList opts;
  opts.parse_from_config(config, &limits);
  opts.update_map();
  return opts;
}

TEST(VerifyX509Name, config_missing_args) {
  // Missing both needed arguments
  std::string config = "verify-x509-name";
  EXPECT_THROW(VerifyX509Name err_no_args(parse_testcfg(config)), option_error);
}

TEST(VerifyX509Name, config_incorrect_type) {
  // Incorrect type
  std::string config = "verify-x509-name localhost nonsense-arg";
  EXPECT_THROW(VerifyX509Name err_wrong_type(parse_testcfg(config)),
               option_error);
}

TEST(VerifyX509Name, config_correct_default_type) {
  // Missing type argument - defaults to complete subject DN
  std::string config =
      "verify-x509-name \"C=KG, ST=NA, O=OpenVPN-TEST, CN=Test-Server, "
      "emailAddress=me@myhost.mydomain\"";
  VerifyX509Name ok_default_subj(parse_testcfg(config));
}

TEST(VerifyX509Name, config_correct_subject) {
  // Correct - type: subject
  std::string config =
      "verify-x509-name \"C=KG, ST=NA, O=OpenVPN-TEST, CN=Test-Server, "
      "emailAddress=me@myhost.mydomain\" subject";
  VerifyX509Name ok_subj(parse_testcfg(config));
}

TEST(VerifyX509Name, config_correct_name) {
  // Correct - type: name
  std::string config = "verify-x509-name localhost name";
  VerifyX509Name ok_name(parse_testcfg(config));
}

TEST(VerifyX509Name, config_correct_name_prefix) {
  // Correct - type: name-prefix
  std::string config = "verify-x509-name Server- name-prefix";
  VerifyX509Name ok_name_prefix(parse_testcfg(config));
}

TEST(VerifyX509Name, test_subject) {
  std::string config =
      "verify-x509-name \"C=KG, ST=NA, O=OpenVPN-TEST, CN=Test-Server, "
      "emailAddress=me@myhost.mydomain\"";
  VerifyX509Name verify_def(parse_testcfg(config));

  ASSERT_TRUE(verify_def.verify(
      "C=KG, ST=NA, O=OpenVPN-TEST, CN=Test-Server, "
      "emailAddress=me@myhost.mydomain"));
  ASSERT_FALSE(verify_def.verify(
      "C=KG, ST=NA, O=OpenVPN-TEST-FAIL, CN=Wrong-Server, "
      "emailAddress=me@myhost.mydomain"));
  ASSERT_FALSE(verify_def.verify("server-1.example.org"));

  // This is basically the same config as the one above,
  // just with the 'subject' type defined explicitly
  config =
      "verify-x509-name \"C=KG, ST=NA, O=OpenVPN-TEST, CN=Test-Server, "
      "emailAddress=me@myhost.mydomain\" subject";
  VerifyX509Name verify_subj(parse_testcfg(config));

  ASSERT_TRUE(verify_subj.verify(
      "C=KG, ST=NA, O=OpenVPN-TEST, CN=Test-Server, "
      "emailAddress=me@myhost.mydomain"));
  ASSERT_FALSE(verify_subj.verify(
      "C=KG, ST=NA, O=OpenVPN-TEST-FAIL, CN=Wrong-Server, "
      "emailAddress=me@myhost.mydomain"));
  ASSERT_FALSE(verify_subj.verify("server-1.example.org"));
}

TEST(VerifyX509Name, test_name) {
  std::string config = "verify-x509-name server-1.example.org name";
  VerifyX509Name verify(parse_testcfg(config));

  ASSERT_TRUE(verify.verify("server-1.example.org"));
  ASSERT_FALSE(verify.verify("server-2.example.org"));
  ASSERT_FALSE(verify.verify("server"));
}

TEST(VerifyX509Name, test_name_prefix) {
  std::string config = "verify-x509-name server name-prefix";
  VerifyX509Name verify(parse_testcfg(config));

  ASSERT_TRUE(verify.verify("server-1.example.org"));
  ASSERT_TRUE(verify.verify("server-2.sub.example.net"));
  ASSERT_TRUE(verify.verify("server"));
  ASSERT_FALSE(verify.verify("some-other.example.org"));
}

}  // namespace unittests
