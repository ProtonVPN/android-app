#include <iostream>
#include "test_common.hpp"

/* if initproces.hpp is not included, mingw/windows compilation fails with
 * weird stack struct creation related errors in OpenSSL */
#include <openvpn/init/initprocess.hpp>
#include <openvpn/time/asiotimer.hpp>

#include <client/ovpncli.hpp>
#include <openvpn/client/cliopt.hpp>

using namespace openvpn;

/* The config parser checks for valid certificates, provide valid ones */
std::string dummysecp256cert = "-----BEGIN CERTIFICATE-----\n"
                               "MIIBETCBuAIJAImY2B4ODlQuMAoGCCqGSM49BAMCMBExDzANBgNVBAMMBnNlcnZl\n"
                               "cjAeFw0yMjA4MzAxNTA3NDJaFw0zMjA4MjcxNTA3NDJaMBExDzANBgNVBAMMBnNl\n"
                               "cnZlcjBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABDwU0GWKxTxYXP/L448OlaQr\n"
                               "fhF2p83eg/55LJB7Aiq7xckQImGa3w2heo01hFQXQ/4mK3wsLZr7ZZl7IDC4hhMw\n"
                               "CgYIKoZIzj0EAwIDSAAwRQIhAKDmwivsD4qjRtbaXmUNc3src6oFOCus32ZRZw0p\n"
                               "Oz9zAiBZ47YdsJ985ID5COg1+nCKk+0d7jWjICbPcODHyzH4fg==\n"
                               "-----END CERTIFICATE-----\n";

std::string dummysecp256key = "-----BEGIN PRIVATE KEY-----\n"
                              "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgbzZUYL0jZM05vB2O\n"
                              "kIKcA1OxSKw9ZVQ8UnlUCf6l/8ChRANCAAQ8FNBlisU8WFz/y+OPDpWkK34RdqfN\n"
                              "3oP+eSyQewIqu8XJECJhmt8NoXqNNYRUF0P+Jit8LC2a+2WZeyAwuIYT\n"
                              "-----END PRIVATE KEY-----\n";

std::string certconfig = "<ca>\n"
                         + dummysecp256cert + "</ca>\n"
                         + "<cert>\n"
                         + dummysecp256cert + "</cert>\n"
                         + "<key>\n" + dummysecp256key
                         + "</key>\n";

std::string minimalConfig = certconfig + "\n"
                            + "client\n"
                              "remote wooden.box\n";

class ValidConfigs : public testing::TestWithParam<std::string>
{
};
typedef std::pair<std::string, std::string> config_error;
class InvalidConfigs : public testing::TestWithParam<config_error>
{
};

void load_client_config(const std::string &config_content)
{
    OptionList options;
    ClientOptions::Config config;
    config.clientconf.dco = true;
    config.proto_context_options.reset(new ProtoContextCompressionOptions());

    ClientAPI::OpenVPNClientHelper client_helper;
    ParseClientConfig conf = ParseClientConfig::parse(config_content);

    auto parsed_config = ParseClientConfig::parse(config_content, nullptr, options);

    ClientOptions cliopt(options, config);
}

TEST_P(ValidConfigs, valid_config)
{
    load_client_config(GetParam());
}

TEST_P(InvalidConfigs, config_throws_option_error)
{
    OVPN_EXPECT_THROW(
        load_client_config(GetParam().first),
        option_error,
        GetParam().second);
}

TEST(config, missingRequiredOption)
{
    ParseClientConfig conf = ParseClientConfig::parse("mode server");
    EXPECT_EQ(conf.error(), true);
    EXPECT_TRUE(conf.message().find("option_error: remote option not specified") != std::string::npos);
}

INSTANTIATE_TEST_SUITE_P(
    optionError,
    InvalidConfigs,
    testing::Values(
        config_error{std::string("remote wooden.box\n") + "mode server"
                         + "\n<ca>\n" + dummysecp256cert + "</ca>\n",
                     "option 'cert' not found"},
        config_error{minimalConfig + "mode", "Only 'mode p2p' supported"},
        config_error{minimalConfig + "mode server", "Only 'mode p2p' supported"},
        config_error{minimalConfig + "key-method 1", "Only 'key-method 2' is supported"},
        config_error{minimalConfig + "fragment", "sorry, 'fragment' directive is not supported"}));

TEST(config, parse_unknown_option)
{
    OVPN_EXPECT_THROW(
        load_client_config(minimalConfig + "bikeshed-color green"),
        ErrorCode,
        "UNKNOWN/UNSUPPORTED OPTIONS");
}

INSTANTIATE_TEST_SUITE_P(
    minimalConfigs,
    ValidConfigs,
    testing::Values(
        /* A duplicate option should not cause our parser to fail */
        minimalConfig + "cipher AES-192-CBC\ncipher AES-256-GCM\n",
        /* Bikeshed colour is ignored should throw no error */
        minimalConfig + "ignore-unknown-option bikeshed-colour bikeshed-color\n"
                        "ignore-unknown-option danish axe phk\n"
                        "bikeshed-colour green",
        minimalConfig + "setenv opt bikeshed-paint silver with sparkling",
        /* warnings, but no errors */
        minimalConfig + "tun-ipv6\n",
        minimalConfig + "opt-verify\n"));

TEST(config, parse_management)
{
    OVPN_EXPECT_THROW(
        load_client_config(minimalConfig + "management-is-blue"),
        ErrorCode,
        "OpenVPN management interface is not supported by this client");

    OVPN_EXPECT_THROW(
        load_client_config(minimalConfig + "management"),
        ErrorCode,
        "OpenVPN management interface is not supported by this client");
}

TEST(config, duplicate_options_sets)
{
    /* Do the whole dance to get a ClientOption object to access the list */
    OptionList options;
    ClientOptions::Config config;
    config.clientconf.dco = false;
    config.proto_context_options = new ProtoContextCompressionOptions();

    ClientAPI::OpenVPNClientHelper client_helper;

    ParseClientConfig conf = ParseClientConfig::parse(minimalConfig);

    auto parsed_config = ParseClientConfig::parse(minimalConfig, nullptr, options);

    ClientOptions cliopt(options, config);

    std::vector<std::unordered_set<std::string>> allsets = {
        cliopt.settings_feature_not_implemented_fatal,
        cliopt.settings_feature_not_implemented_warn,
        cliopt.settings_ignoreSilently,
        cliopt.settings_ignoreWithWarning,
        cliopt.settings_pushonlyoptions,
        cliopt.settings_removedOptions,
        cliopt.settings_serverOnlyOptions,
        cliopt.settings_standalone_options};

    std::unordered_set<std::string> allOptions;

    for (auto set : allsets)
    {
        for (const auto &optname : set)
        {
            /* Use an expection instead of an assert to get the name of the option
             * that is a duplicate */
            if (!(allOptions.find(optname) == allOptions.end()))
                throw std::runtime_error("duplicate element: " + optname);
            allOptions.insert(optname);
        }
    }
}

TEST(config, dco_compatibility)
{
    for (auto optname : ClientOptions::dco_incompatible_opts)
    {
        ClientAPI::Config api_config;

        /* If we just use http-proxy without argument, we will bail out for
         * missing parameter instead */
        if (optname == "http-proxy")
            optname = "proto tcp\nhttp-proxy 1.1.1.1 8080";

        api_config.dco = true;
        api_config.content = minimalConfig + optname;
        ClientAPI::OpenVPNClientHelper client_helper;
        auto eval = client_helper.eval_config(api_config);

        EXPECT_FALSE(eval.dcoCompatible);

        OVPN_EXPECT_THROW(
            load_client_config(minimalConfig + optname),
            option_error,
            "ERR_INVALID_CONFIG: option_error: dco_compatibility: config/options are not compatible with dco");
    }
}

TEST(config, server_cert_in_eval)
{
    ClientAPI::Config api_config;
    api_config.content = minimalConfig;

    ClientAPI::OpenVPNClientHelper client_helper;
    auto eval = client_helper.eval_config(api_config);

    EXPECT_FALSE(eval.vpnCa.empty());
}


TEST(config, server_options_present_in_error_msg)
{
    std::vector<std::string> server_options = {"server 10.0.0.0 255.255.255.0",
                                               "push \"foo bar\""};

    for (auto &option : server_options)
    {
        auto optname = option.substr(0, option.find(' '));
        auto expected_error_string = "Server only option: " + optname;

        OVPN_EXPECT_THROW(
            load_client_config(minimalConfig + option),
            ErrorCode,
            expected_error_string);
    }
}

TEST(config, unknown_options_present_in_error_msg)
{
    std::vector<std::string> server_options = {"make-a-lot-of-noise", "water-the-plants"};

    for (auto &option : server_options)
    {
        auto optname = option.substr(0, option.find(' '));
        auto expected_error_string = "UNKNOWN/UNSUPPORTED OPTIONS: " + optname;

        OVPN_EXPECT_THROW(
            load_client_config(minimalConfig + option),
            ErrorCode,
            expected_error_string);
    }
}

TEST(config, multiple_option_errors)
{
    std::ostringstream os;
    os << "OpenVPN management interface is not supported by this client: management\n";
    os << "UNKNOWN/UNSUPPORTED OPTIONS: lol,lal";

    OVPN_EXPECT_THROW(
        load_client_config(minimalConfig + "management\nlol\nlal"),
        ErrorCode,
        os.str());
}

INSTANTIATE_TEST_SUITE_P(
    clientPull,
    ValidConfigs,
    testing::Values(
        /* Should not trigger an error, even without --client in place */
        certconfig + "\nremote 1.2.3.4\ntls-client\npull\n",
        /* Should not trigger an error. Redundant options are no problem */
        certconfig + "\nremote 1.2.3.4\ntls-client\npull\nclient\n",
        certconfig + "\nremote 1.2.3.4\npull\nclient\n",
        certconfig + "\nremote 1.2.3.4\nclient\ntls-client\n"));

INSTANTIATE_TEST_SUITE_P(
    clientPull,
    InvalidConfigs,
    testing::Values(
        config_error{certconfig + "\nremote 1.2.3.4\n",
                     "option_error: Neither 'client' nor both 'tls-client' and 'pull' options declared. OpenVPN3 client only supports --client mode."},
        config_error{certconfig + "\nremote 1.2.3.4\ntls-client\n",
                     "option_error: Neither 'client' nor both 'tls-client' and 'pull' options declared. OpenVPN3 client only supports --client mode."},
        config_error{certconfig + "\nremote 1.2.3.4\npull\n",
                     "option_error: Neither 'client' nor both 'tls-client' and 'pull' options declared. OpenVPN3 client only supports --client mode."}));

TEST(config, meta_option_in_content)
{
    OptionList options;
    auto cfg = minimalConfig + "\n# OVPN_ACCESS_SERVER_AAA=BBB";

    OptionList::KeyValueList kvl;
    kvl.push_back(new OptionList::KeyValue("OVPN_ACCESS_SERVER_CCC", "DDD"));

    auto parsed_config = ParseClientConfig::parse(cfg, &kvl, options);

    ClientOptions::Config config;
    config.clientconf.dco = true;
    config.proto_context_options.reset(new ProtoContextCompressionOptions());
    ClientOptions cliopt(options, config);

    auto opt = options.get("AAA");
    ASSERT_TRUE(opt.meta());
    ASSERT_EQ(opt.get(1, 256), "BBB");

    opt = options.get("CCC");
    ASSERT_TRUE(opt.meta());
    ASSERT_EQ(opt.get(1, 256), "DDD");
}
