// Test Unicode::is_valid_utf8(), validate_auth_cred(),
// and AuthCreds::is_valid().  Throws exception on failure.
#include "test_common.h"

#include <iostream>
#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>

#include <openvpn/auth/validatecreds.hpp>
#include <openvpn/auth/authcreds.hpp>

using namespace openvpn;

static bool verbose = false;

void validate(const ValidateCreds::Type type, const bool expected_result, const std::string &cred, const bool strict)
{
    if (verbose)
        OPENVPN_LOG("VALIDATE '" << cred << "' expected res=" << expected_result);
    const bool actual_result = ValidateCreds::is_valid(type, cred, strict);
    EXPECT_EQ(actual_result, expected_result);
}

TEST(misc, creds1)
{
    validate(ValidateCreds::USERNAME, true, "foobar", true);
    validate(ValidateCreds::PASSWORD, true, "xxx\nyyy", false);
    validate(ValidateCreds::USERNAME, false, "foo\nbar", true);
    validate(ValidateCreds::USERNAME,
             true,
             "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
             true);
    validate(ValidateCreds::USERNAME,
             false,
             "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
             true);
    validate(ValidateCreds::USERNAME, false, "hello\x07there", true);
    validate(ValidateCreds::USERNAME, true, "Привет", true);
    validate(ValidateCreds::USERNAME, false, "\xFF\xFF\xFF\xFF", true);
}

void validate_creds(std::string username, std::string password, const bool expected_result, const bool strict)
{
    if (verbose)
        OPENVPN_LOG("VALIDATE username='" << username << "' password='" << password << "' expected res=" << expected_result);
    SafeString password_safe(password);
    const AuthCreds ac(std::move(username), std::move(password_safe), "");
    const bool actual_result = ac.is_valid(strict);
    EXPECT_EQ(actual_result, expected_result);
}

TEST(misc, creds2)
{
    validate_creds("foo", "bar", true, true);
    validate_creds("", "bar", false, true);
    validate_creds("foo", "", true, true);
    validate_creds("Привет", "trouble", true, true);
    validate_creds("Привет", "", true, true);
    validate_creds("foo\nbar", "zoo", false, true);
    validate_creds("hello\x07there", "pass", false, true);
    validate_creds("হ্যালো", "హలో", true, true);
    validate_creds("yyy", "\xFF\xFF\xFF\xFF", false, true);
}

void validate_utf8(const std::string &str, const size_t max_len_flags, const bool expected_result)
{
    if (verbose)
        OPENVPN_LOG("VALIDATE UTF8 '" << str << "' expected res=" << expected_result);
    const bool actual_result = Unicode::is_valid_utf8(str, max_len_flags);
    EXPECT_EQ(actual_result, expected_result);
}

TEST(misc, creds3)
{
    validate_utf8("", 0, true);
    validate_utf8("test", 0, true);
    validate_utf8("Привет", 0, true);
    validate_utf8("Привет", 6, true);
    validate_utf8("Привет", 5, false);
    validate_utf8("hello\x07there", 0, true);
    validate_utf8("hello\x07there", Unicode::UTF8_NO_CTRL, false);
    validate_utf8("\xFF\xFF\xFF\xFF", 0, false);
    validate_utf8("hello there", 0, true);
    validate_utf8("hello there", Unicode::UTF8_NO_SPACE, false);
}