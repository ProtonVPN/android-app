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


#include "test_common.hpp"

#include <openvpn/common/userpass.hpp>

using namespace openvpn;

const std::string optname = "auth";
const std::string user_simple(
    "auth username\n");
const std::string user_tag(
    "<auth>\n"
    "username\n"
    "</auth>\n");
const std::string user_file(
    "auth " UNITTEST_SOURCE_DIR "/userpass/user.txt\n");
const std::vector<std::string> user_only{
    user_simple,
    user_tag,
};
const std::string userpass_tag(
    "<auth>\n"
    "username\n"
    "password\n"
    "</auth>\n");
const std::string userpass_file(
    "auth " UNITTEST_SOURCE_DIR "/userpass/userpass.txt\n");
const std::vector<std::string> user_pass{
    userpass_tag,
    userpass_file,
};
const std::vector<std::string> onearg{
    user_simple,
    user_tag,
    user_file,
    userpass_file};
const std::vector<std::string> overflow_files{
    UNITTEST_SOURCE_DIR "/userpass/useroverflow.txt",
    UNITTEST_SOURCE_DIR "/userpass/passoverflow.txt",
};

const std::vector<unsigned int> flag_combos_missing_okay{
    0,
    UserPass::OPT_OPTIONAL,
    UserPass::OPT_OPTIONAL | UserPass::USERNAME_REQUIRED,
    UserPass::OPT_OPTIONAL | UserPass::USERNAME_REQUIRED | UserPass::PASSWORD_REQUIRED,
};
const std::vector<unsigned int> flag_combos_noargs_okay{
    0,
    UserPass::OPT_OPTIONAL,
};
const std::vector<unsigned int> flag_combos_required{
    UserPass::OPT_REQUIRED,
    // FIXME?
    UserPass::OPT_REQUIRED | UserPass::OPT_OPTIONAL,
    UserPass::OPT_REQUIRED | UserPass::USERNAME_REQUIRED,
    UserPass::OPT_REQUIRED | UserPass::USERNAME_REQUIRED | UserPass::PASSWORD_REQUIRED,
};
const std::vector<unsigned int> flag_combos_pw_not_required{
    0,
    UserPass::OPT_REQUIRED,
    UserPass::OPT_OPTIONAL,
    UserPass::USERNAME_REQUIRED,
    UserPass::OPT_OPTIONAL | UserPass::USERNAME_REQUIRED,
    UserPass::OPT_REQUIRED | UserPass::USERNAME_REQUIRED,
};
const std::vector<unsigned int> flag_combos_pw_required{
    UserPass::OPT_OPTIONAL | UserPass::PASSWORD_REQUIRED,
    UserPass::OPT_REQUIRED | UserPass::PASSWORD_REQUIRED,
    UserPass::OPT_OPTIONAL | UserPass::USERNAME_REQUIRED | UserPass::PASSWORD_REQUIRED,
    UserPass::OPT_REQUIRED | UserPass::USERNAME_REQUIRED | UserPass::PASSWORD_REQUIRED,
};
const std::vector<unsigned int> flag_combos_nofile{
    0,
    UserPass::OPT_REQUIRED,
    UserPass::OPT_OPTIONAL,
    UserPass::USERNAME_REQUIRED,
    UserPass::OPT_OPTIONAL | UserPass::USERNAME_REQUIRED,
    UserPass::OPT_REQUIRED | UserPass::USERNAME_REQUIRED,
    UserPass::OPT_OPTIONAL | UserPass::PASSWORD_REQUIRED,
    UserPass::OPT_REQUIRED | UserPass::PASSWORD_REQUIRED,
    UserPass::OPT_OPTIONAL | UserPass::USERNAME_REQUIRED | UserPass::PASSWORD_REQUIRED,
    UserPass::OPT_REQUIRED | UserPass::USERNAME_REQUIRED | UserPass::PASSWORD_REQUIRED,
};

TEST(UserPass, missing)
{
    for (auto flags : flag_combos_missing_okay)
    {
        std::string user;
        std::string pass;
        std::vector<std::string> userpass;
        OptionList cfg;
        cfg.parse_from_config("otheropt", nullptr);
        cfg.update_map();
        UserPass::parse(cfg, optname, flags, user, pass);
        ASSERT_TRUE(user.empty()) << "flags: " << flags;
        ASSERT_TRUE(pass.empty()) << "flags: " << flags;
        user = "otheruser";
        pass = "otherpass";
        UserPass::parse(cfg, optname, flags, user, pass);
        ASSERT_TRUE(user.empty()) << "flags: " << flags;
        ASSERT_TRUE(pass.empty()) << "flags: " << flags;
        bool ret = UserPass::parse(cfg, optname, flags, &userpass);
        ASSERT_FALSE(ret) << "flags: " << flags;
        ASSERT_EQ(userpass.size(), 0) << "flags: " << flags;
    }
    for (auto flags : flag_combos_required)
    {
        std::string user;
        std::string pass;
        std::vector<std::string> userpass;
        OptionList cfg;
        cfg.parse_from_config("otheropt", nullptr);
        cfg.update_map();
        ASSERT_THROW(
            UserPass::parse(cfg, optname, flags, user, pass),
            UserPass::creds_error)
            << "flags: " << flags;
        ASSERT_TRUE(user.empty()) << "flags: " << flags;
        ASSERT_TRUE(pass.empty()) << "flags: " << flags;
        user = "otheruser";
        pass = "otherpass";
        ASSERT_THROW(
            UserPass::parse(cfg, optname, flags, user, pass),
            UserPass::creds_error)
            << "flags: " << flags;
        ASSERT_TRUE(user.empty()) << "flags: " << flags;
        ASSERT_TRUE(pass.empty()) << "flags: " << flags;
        ASSERT_THROW(
            UserPass::parse(cfg, optname, flags, &userpass),
            UserPass::creds_error)
            << "flags: " << flags;
        ASSERT_EQ(userpass.size(), 0) << "flags: " << flags;
    }
}

TEST(UserPass, noargs)
{
    for (auto flags : flag_combos_noargs_okay)
    {
        std::string user;
        std::string pass;
        std::vector<std::string> userpass;
        OptionList cfg;
        cfg.parse_from_config(optname, nullptr);
        cfg.update_map();
        UserPass::parse(cfg, optname, flags, user, pass);
        ASSERT_TRUE(user.empty()) << "flags: " << flags;
        ASSERT_TRUE(pass.empty()) << "flags: " << flags;
        user = "otheruser";
        pass = "otherpass";
        UserPass::parse(cfg, optname, flags, user, pass);
        ASSERT_TRUE(user.empty()) << "flags: " << flags;
        ASSERT_TRUE(pass.empty()) << "flags: " << flags;
        bool ret = UserPass::parse(cfg, optname, flags, &userpass);
        ASSERT_TRUE(ret) << "flags: " << flags;
        ASSERT_EQ(userpass.size(), 0) << "flags: " << flags;
    }
    for (auto flags : flag_combos_required)
    {
        std::string user;
        std::string pass;
        std::vector<std::string> userpass;
        OptionList cfg;
        cfg.parse_from_config(optname, nullptr);
        cfg.update_map();
        ASSERT_THROW(
            UserPass::parse(cfg, optname, flags, user, pass),
            UserPass::creds_error)
            << "flags: " << flags;
        ASSERT_TRUE(user.empty()) << "flags: " << flags;
        ASSERT_TRUE(pass.empty()) << "flags: " << flags;
        user = "otheruser";
        pass = "otherpass";
        ASSERT_THROW(
            UserPass::parse(cfg, optname, flags, user, pass),
            UserPass::creds_error)
            << "flags: " << flags;
        ASSERT_TRUE(user.empty()) << "flags: " << flags;
        ASSERT_TRUE(pass.empty()) << "flags: " << flags;
        ASSERT_THROW(
            UserPass::parse(cfg, optname, flags, &userpass),
            UserPass::creds_error)
            << "flags: " << flags;
        ASSERT_EQ(userpass.size(), 0) << "flags: " << flags;
    }
}

TEST(UserPass, user_only)
{
    for (auto flags : flag_combos_pw_not_required)
    {
        for (auto &config_text : user_only)
        {
            std::string user;
            std::string pass;
            std::vector<std::string> userpass;
            OptionList cfg;
            cfg.parse_from_config(config_text, nullptr);
            cfg.update_map();
            UserPass::parse(cfg, optname, flags, user, pass);
            ASSERT_EQ(user, "username") << "config: " << config_text << "flags: " << flags;
            ASSERT_TRUE(pass.empty()) << "config: " << config_text << "flags: " << flags;
            user = "otheruser";
            pass = "otherpass";
            UserPass::parse(cfg, optname, flags, user, pass);
            ASSERT_EQ(user, "username") << "config: " << config_text << "flags: " << flags;
            ASSERT_TRUE(pass.empty()) << "config: " << config_text << "flags: " << flags;
            bool ret = UserPass::parse(cfg, optname, flags, &userpass);
            ASSERT_TRUE(ret) << "config: " << config_text << "flags: " << flags;
            ASSERT_EQ(userpass.size(), 1) << "config: " << config_text << "flags: " << flags;
            ASSERT_EQ(userpass[0], "username") << "config: " << config_text << "flags: " << flags;
        }
        /* filename instead of username */
        {
            std::string user;
            std::string pass;
            std::vector<std::string> userpass;
            OptionList cfg;
            cfg.parse_from_config(userpass_file, nullptr);
            cfg.update_map();
            UserPass::parse(cfg, optname, flags, user, pass);
            ASSERT_EQ(user, UNITTEST_SOURCE_DIR "/userpass/userpass.txt") << "flags: " << flags;
            ASSERT_TRUE(pass.empty()) << "flags: " << flags;
            user = "otheruser";
            pass = "otherpass";
            UserPass::parse(cfg, optname, flags, user, pass);
            ASSERT_EQ(user, UNITTEST_SOURCE_DIR "/userpass/userpass.txt") << "flags: " << flags;
            ASSERT_TRUE(pass.empty()) << "flags: " << flags;
            bool ret = UserPass::parse(cfg, optname, flags, &userpass);
            ASSERT_TRUE(ret) << "flags: " << flags;
            ASSERT_EQ(userpass.size(), 1) << "flags: " << flags;
            ASSERT_EQ(userpass[0], UNITTEST_SOURCE_DIR "/userpass/userpass.txt") << "flags: " << flags;
        }
    }
    for (auto flags : flag_combos_pw_required)
    {
        for (auto &config_text : onearg)
        {
            std::string user;
            std::string pass;
            std::vector<std::string> userpass;
            OptionList cfg;
            cfg.parse_from_config(config_text, nullptr);
            cfg.update_map();
            ASSERT_THROW(
                UserPass::parse(cfg, optname, flags, user, pass),
                UserPass::creds_error)
                << "config: " << config_text << "flags: " << flags;
            user = "otheruser";
            pass = "otherpass";
            ASSERT_THROW(
                UserPass::parse(cfg, optname, flags, user, pass),
                UserPass::creds_error)
                << "config: " << config_text << "flags: " << flags;
            bool ret = UserPass::parse(cfg, optname, flags, &userpass);
            // FIXME?
            ASSERT_TRUE(ret) << "config: " << config_text << "flags: " << flags;
        }
    }
}

TEST(UserPass, user_pass)
{
    for (auto flags : flag_combos_nofile)
    {
        for (auto &config_text : user_pass)
        {
            std::string user;
            std::string pass;
            std::vector<std::string> userpass;
            auto flags_try_file = flags | UserPass::TRY_FILE;
            OptionList cfg;
            cfg.parse_from_config(config_text, nullptr);
            cfg.update_map();
            UserPass::parse(cfg, optname, flags_try_file, user, pass);
            ASSERT_EQ(user, "username") << "config: " << config_text << "flags: " << flags;
            ASSERT_EQ(pass, "password") << "config: " << config_text << "flags: " << flags;
            user = "otheruser";
            pass = "otherpass";
            UserPass::parse(cfg, optname, flags_try_file, user, pass);
            ASSERT_EQ(user, "username") << "config: " << config_text << "flags: " << flags;
            ASSERT_EQ(pass, "password") << "config: " << config_text << "flags: " << flags;
            bool ret = UserPass::parse(cfg, optname, flags_try_file, &userpass);
            ASSERT_TRUE(ret) << "config: " << config_text << "flags: " << flags;
            ASSERT_EQ(userpass.size(), 2) << "config: " << config_text << "flags: " << flags;
            ASSERT_EQ(userpass[0], "username") << "config: " << config_text << "flags: " << flags;
            ASSERT_EQ(userpass[1], "password") << "config: " << config_text << "flags: " << flags;
        }
    }
}

TEST(UserPass, parse_file_user_only)
{
    for (auto flags : flag_combos_pw_not_required)
    {
        std::string user;
        std::string pass;
        UserPass::parse_file(UNITTEST_SOURCE_DIR "/userpass/user.txt", flags, user, pass);
        ASSERT_EQ(user, "username") << "flags: " << flags;
        ASSERT_TRUE(pass.empty()) << "flags: " << flags;
        user = "otheruser";
        pass = "otherpass";
        UserPass::parse_file(UNITTEST_SOURCE_DIR "/userpass/user.txt", flags, user, pass);
        ASSERT_EQ(user, "username") << "flags: " << flags;
        ASSERT_TRUE(pass.empty()) << "flags: " << flags;
    }
    for (auto flags : flag_combos_pw_required)
    {
        for (auto &config_text : onearg)
        {
            std::string user;
            std::string pass;
            ASSERT_THROW(
                UserPass::parse_file(UNITTEST_SOURCE_DIR "/userpass/user.txt", flags, user, pass),
                UserPass::creds_error)
                << "config: " << config_text << "flags: " << flags;
            user = "otheruser";
            pass = "otherpass";
            ASSERT_THROW(
                UserPass::parse_file(UNITTEST_SOURCE_DIR "/userpass/user.txt", flags, user, pass),
                UserPass::creds_error)
                << "config: " << config_text << "flags: " << flags;
        }
    }
}

TEST(UserPass, parse_file_user_pass)
{
    for (auto flags : flag_combos_nofile)
    {
        std::string user;
        std::string pass;
        UserPass::parse_file(UNITTEST_SOURCE_DIR "/userpass/userpass.txt", flags, user, pass);
        ASSERT_EQ(user, "username") << "flags: " << flags;
        ASSERT_EQ(pass, "password") << "flags: " << flags;
        user = "otheruser";
        pass = "otherpass";
        UserPass::parse_file(UNITTEST_SOURCE_DIR "/userpass/userpass.txt", flags, user, pass);
        ASSERT_EQ(user, "username") << "flags: " << flags;
        ASSERT_EQ(pass, "password") << "flags: " << flags;
    }
}

TEST(UserPass, parse_file_overflow)
{
    for (auto flags : flag_combos_nofile)
    {
        for (auto &filename : overflow_files)
        {
            std::string user;
            std::string pass;
            ASSERT_ANY_THROW(UserPass::parse_file(filename, flags, user, pass))
                << "file: " << filename << "flags: " << flags;
            user = "otheruser";
            pass = "otherpass";
            ASSERT_ANY_THROW(UserPass::parse_file(filename, flags, user, pass))
                << "file: " << filename << "flags: " << flags;
            auto flags_try_file = flags | UserPass::TRY_FILE;
            std::string config_text = std::string("auth ") + filename;
            std::vector<std::string> userpass;
            OptionList cfg;
            cfg.parse_from_config(config_text, nullptr);
            cfg.update_map();
            ASSERT_ANY_THROW(UserPass::parse(cfg, optname, flags_try_file, &userpass))
                << "file: " << filename << "flags: " << flags;
        }
    }
}
