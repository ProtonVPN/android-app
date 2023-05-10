#include "test_common.h"
#include <iostream>

#include <openvpn/common/size.hpp>

#include <openvpn/crypto/static_key.hpp>
#include <openvpn/ssl/sslchoose.hpp>

using namespace openvpn;

const char key_text[] = "-----BEGIN OpenVPN Static key V1-----\n"
                        "bd28e7947597929093371be4cf55fd78\n"
                        "98a70d0feffd389f70ea606635ed0371\n"
                        "57045695a770264ca0b2c251cb5c65fe\n"
                        "447d9b28855cf199bc3d9527e5f88a59\n"
                        "5cd213b5a71b47f11a915a77e3a7aed7\n"
                        "fa901d864150b64eb8d424383e5564dd\n"
                        "23e5b5fa8d16dfe2d37b946e8f22bb58\n"
                        "a5b904062bdcea35007c6825250a1c00\n"
                        "a2a54bd892fa20edbcfe4fe1fa8a786c\n"
                        "5c1102a3b53e294c729b37a24842f9c9\n"
                        "b72018b990aff058bbeeaf18f586cd5c\n"
                        "d70475328caed6d9662937a3c970f253\n"
                        "8495988c6c72c0ef8da720c342ac6405\n"
                        "a61da0fd18ddfd106aeee1736772baad\n"
                        "014703f549480c61080aa963f8b10a4a\n"
                        "f7591ead4710bd0e74c0b37e37c84374\n"
                        "-----END OpenVPN Static key V1-----\n";

TEST(statickey, key1)
{
    // This test only tests if loading a static key works
    OpenVPNStaticKey sk;
    sk.parse(std::string(key_text));
    std::string rend = sk.render();
}

TEST(statickey, key2)
{
    RandomAPI::Ptr rng(new SSLLib::RandomAPI(false));
    const size_t key_len = 16;
    StaticKey sk1;
    sk1.init_from_rng(*rng, key_len);
    const std::string s1 = sk1.render_to_base64();
    StaticKey sk2;
    sk2.parse_from_base64(s1, key_len);
    const std::string s2 = sk2.render_to_base64();
    ASSERT_EQ(s1, s2);
}

class StaticSinkBase : public RC<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<StaticSinkBase> Ptr;

    virtual void init(StaticKey &&key) = 0;
    virtual std::string dump() = 0;
};

class StaticSink : public StaticSinkBase
{
  public:
    virtual void init(StaticKey &&key)
    {
        k = std::move(key);
    }

    virtual std::string dump()
    {
        return k.render_hex();
    }

  private:
    StaticKey k;
};

TEST(statickey, move)
{
    OpenVPNStaticKey sk;
    sk.parse(std::string(key_text));

    StaticSinkBase::Ptr ss(new StaticSink());
    ss->init(sk.slice(OpenVPNStaticKey::CIPHER | OpenVPNStaticKey::ENCRYPT | OpenVPNStaticKey::INVERSE));
    ASSERT_EQ(
        "a2a54bd892fa20edbcfe4fe1fa8a786c5c1102a3b53e294c729b37a24842f9c9b72018b990aff058bbeeaf18f586cd5cd70475328caed6d9662937a3c970f253",
        ss->dump());
}
