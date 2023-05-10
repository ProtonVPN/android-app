#include "test_common.h"

#include <openvpn/common/sess_id.hpp>
#include <openvpn/openssl/util/tokenencrypt.hpp>
#include <unordered_map>
#include <openvpn/ssl/sslchoose.hpp>

using namespace openvpn;

TEST(sessid, test1)
{
    SSLLib::RandomAPI rng(false);

    // test 1
    {
        const SessionID64 sid1(rng);
        // std::cout << "SID1: " << sid1 << std::endl;

        const SessionID64 sid2(sid1.to_string());
        ASSERT_TRUE(sid1.defined() && sid2.defined()) << "FAIL sid1 or sid2 is undefined";
        ASSERT_EQ(sid1, sid2);

        const SessionID128 sid3(rng);
        ASSERT_FALSE(sid1.eq_weak(sid3)) << "FAIL sid1 ~== sid3";
        ASSERT_FALSE(sid3.eq_weak(sid1)) << "FAIL sid3 ~== sid1";

        for (int i = 1; i <= 4; ++i)
        {
            // std::cout << "---- " << i << " ----" << std::endl;
            const TokenEncrypt::Key key(rng);
            TokenEncryptDecrypt ted(key);
            const SessionID128 sid3_enc(sid3, ted.encrypt);
            // std::cout << "SID3 (enc): " << sid3_enc << std::endl;
            const SessionID128 sid3_dec(sid3_enc, ted.decrypt);
            // std::cout << "SID3 (dec): " << sid3_dec << std::endl;
        }
    }
}
TEST(sessid, test2)
{
    SSLLib::RandomAPI rng(false);
    {
        const SessionID64 sid1(rng);
        // std::cout << "SID1: " << sid1 << std::endl;
        const SessionID128 sid2(rng);
        // std::cout << "SID2: " << sid2 << std::endl;

        const SessionID128 sid1_exp(sid1);
        // std::cout << "SID1_EXP: " << sid1_exp << std::endl;
        const SessionID64 sid2_trunc(sid2);
        // std::cout << "SID2_TRUNC: " << sid2_trunc << std::endl;
    }
}

TEST(sessid, test3)
{
    const SessionID64 ns;
    ASSERT_FALSE(ns.defined()) << "FAIL default constructed SessionID is defined";
}

TEST(sessid, test4)
{
    SSLLib::RandomAPI rng(false);
    const SessionID128 x;
    const SessionID128 a("YmtN7B2edrDRlefk3vQ_YQ..");
    const SessionID128 b("YmtN7B2edrDRlefk3vQ_YA..");
    const SessionID64 c("YmtN7B2edrA.");
    const SessionID128 d(c);
    /*std::cout << "a: " << a <<
              std::endl;
    std::cout << "b: " << b <<
              std::endl;
    std::cout << "c: " << c <<
              std::endl;
    std::cout << "d: " << d <<
              std::endl; */
    ASSERT_FALSE(a == b) << "test4: wrong, not equal";
    ASSERT_TRUE(a.eq_weak(b)) << "test4/1: wrong, weakly equal";
    ASSERT_TRUE(a.eq_weak(c)) << "test4/2: wrong, weakly equal";
    ASSERT_TRUE(b.eq_weak(c)) << "test4/3: wrong, weakly equal";

    std::unordered_map<SessionID128, std::string> map;
    const std::unordered_map<SessionID128, std::string> &cmap = map;
    map[a] = "hello";
    ASSERT_TRUE(b.find_weak(map, true)) << "test4/1: wrong, weak exists";
    ASSERT_TRUE(d.find_weak(map, true)) << "test4/2: wrong, weak exists";
    ASSERT_FALSE(a.find_weak(map, true)) << "test4/3: wrong, weak doesn't exist";
    ASSERT_TRUE(a.find_weak(map, false)) << "test4/4: wrong, weak exists";
    ASSERT_FALSE(x.find_weak(map, true)) << "test4: wrong, weak doesn't exist";
    const SessionID128 *s1 = d.find_weak(cmap, true);
    ASSERT_TRUE(s1) << "test4: can't find s1";
    // std::cout << "lookup: " << *s1 << ' ' <<
    //            std::endl;
    const SessionID128 *s2 = x.find_weak(cmap, true);
    ASSERT_FALSE(s2) << "test4: shouldn't have found s2";
}

TEST(sessid, speed)
{
    SSLLib::RandomAPI rng(false);

    const SessionID128 sid(rng);
    const TokenEncrypt::Key key(rng);
    TokenEncryptDecrypt ted(key);
    for (size_t i = 0; i < 1000; ++i)
    {
        const SessionID128 sid_enc(sid, ted.encrypt);
        const SessionID128 sid_dec(sid_enc, ted.decrypt);
        ASSERT_EQ(sid, sid_dec);
    }
}

struct SessionID : public SessionID128
{
    SessionID()
    {
        // dump("default");
    }

    SessionID(RandomAPI &rng)
        : SessionID128(rng, true)
    {
        // dump("rng");
    }

    ~SessionID()
    {
        // dump("destruct");
    }

    void dump(const char *prefix) const
    {
        std::cout << prefix << " : " << to_string() << std::endl;
    }
};

class Session
{
  public:
    Session(RandomAPI &rng)
        : sid(rng)
    {
    }

    const SessionID &get_token() const
    {
        return sid;
    }

  private:
    SessionID sid;
};

std::string test(Session *session)
{
    const std::string &nam = "myname";
    const SessionID &sid = session ? session->get_token() : SessionID();
    return "Name: " + nam + " SessID: " + sid.to_string();
}

TEST(sessid, refscope1)
{
    MTRand rng(123456789);
    Session sess(rng);
    EXPECT_EQ("Name: myname SessID: DsiRkfGnT1l1WtMoM59SRA..", test(&sess));
    EXPECT_EQ("Name: myname SessID: AAAAAAAAAAAAAAAAAAAAAA..", test(nullptr));
}

#ifndef ITER
#define ITER 1000
#endif

static void tryit(RandomAPI &rng, TokenEncryptDecrypt &encdec)
{
    std::uint8_t data1[TokenEncrypt::Key::SIZE];
    std::uint8_t data2[TokenEncrypt::Key::SIZE];
    std::uint8_t data3[TokenEncrypt::Key::SIZE];

    rng.rand_bytes(data1, sizeof(data1));
    encdec.encrypt(data2, data1, TokenEncrypt::Key::SIZE);
    encdec.decrypt(data3, data2, TokenEncrypt::Key::SIZE);
    ASSERT_TRUE(::memcmp(data1, data3, TokenEncrypt::Key::SIZE) == 0);
}

TEST(sessid, tokenEncrypt)
{
    RandomAPI::Ptr rng(new SSLLib::RandomAPI(false));
    const TokenEncrypt::Key key(*rng);
    TokenEncryptDecrypt encdec(key);

    for (size_t i = 0; i < ITER; ++i)
        tryit(*rng, encdec);
}
