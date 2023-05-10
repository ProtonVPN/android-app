#include "test_common.h"

#include <openvpn/common/file.hpp>
#include <openvpn/ssl/sslchoose.hpp>
#include <openvpn/crypto/cryptoalgs.hpp>
#include <openvpn/crypto/hashstr.hpp>

using namespace openvpn;

TEST(crypto, hashstr)
{
    const std::string content = read_text_utf8(UNITTEST_SOURCE_DIR "/input/1984.txt");

    DigestFactory::Ptr digest_factory(new CryptoDigestFactory<SSLLib::CryptoAPI>());
    HashString hash(*digest_factory, CryptoAlgs::MD5);
    hash.update(content);
    const std::string actual = hash.final_hex();
    const std::string expected = "2bea7a83bf94971af26372126ebba7e3";
    ASSERT_EQ(actual, expected);
}
