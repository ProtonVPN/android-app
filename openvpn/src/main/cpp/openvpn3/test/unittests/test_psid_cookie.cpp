#include "test_common.hpp"

#include <openvpn/ssl/psid_cookie_impl.hpp>

using namespace openvpn;


TEST(psid_cookie, setup)
{
    PsidCookieImpl::pre_threading_setup();

    ASSERT_TRUE(true);
}

// The following uland_addr46 type is a userland adaptation of an unpublished
// ovpn_addr46 type from James Yonan's kernel work.  The main idea is to create
// a reliably hashable representation of an IP address, be it IPv4 or IPv6
/* Discriminated union for IPv4/v6 addresses that should replace
   ovpn_addr.  The advantage of this approach over ovpn_addr is
   better alignment/packing and potential use as an rhashtable key. */
union uland_addr46 {
    /* IPv4 */
    struct
    {
        /* treat as IPv4-mapped IPv6 addresses */
        uint64_t a4_pre64; /* 0 */
        uint32_t a4_pre32; /* htonl(0xFFFF) */
        struct in_addr a4; /* the IPv4 address */
    };

    /* IPv6 */
    struct in6_addr a6;
    uint64_t a6_64[2];
};

class ClientAddressMock : public PsidCookieAddrInfoBase
{
  public:
    ClientAddressMock(RandomAPI &prng)
    {
        prng.rand_fill(addrport_);
    }
    const unsigned char *get_abstract_cli_addrport(size_t &slab_size) const override
    {
        slab_size = slab_size_;
        return addrport_.c;
    }
    // unused for these tests
    const void *get_impl_info() const override
    {
        return nullptr;
    }

    virtual ~ClientAddressMock() = default;

  private:
    // the detail here is not used; the slab is just randomly filled with data for the
    // hmac; this segment is here to show the motivation for slab_size_
    static constexpr size_t slab_size_ = sizeof(union uland_addr46) + sizeof(std::uint16_t);
    union {
        unsigned char c[slab_size_];
        struct
        {
            union uland_addr46 oaddr46;
            std::uint16_t port;
        } s;
    } addrport_;
};

class PsidCookieTest : public testing::Test
{
    openvpn_io::io_context dummy_io_context;
    Time now;
    ProtoContext::ProtoConfig::Ptr pcfg;
    ServerProto::Factory::Ptr spf;

  protected:
    PsidCookieTest()
        : dummy_io_context(1), pcfg(new ProtoContext::ProtoConfig())
    {
        std::string tls_key_fn = UNITTEST_SOURCE_DIR "/input/psid_cookie_tls.key";
        pcfg->tls_key.parse_from_file(tls_key_fn);
        pcfg->tls_auth_factory.reset(new CryptoOvpnHMACFactory<SSLLib::CryptoAPI>());
        pcfg->set_tls_auth_digest(CryptoAlgs::lookup("SHA256"));
        pcfg->now = &now;
        pcfg->handshake_window = Time::Duration::seconds(60);
        pcfg->key_direction = 0;
        pcfg->rng.reset(new SSLLib::RandomAPI());
        pcfg->prng.reset(new MTRand(2020303));

        spf.reset(new ServerProto::Factory(dummy_io_context, *pcfg));
        spf->proto_context_config = pcfg;

        pcookie_impl.reset(new PsidCookieImpl(spf.get()));
    }

    Time set_clock(Time setting)
    {
        now = setting;
        return setting;
    }

    Time advance_clock(uint64_t binary_ms)
    {
        now += Time::Duration::binary_ms(binary_ms);
        return now;
    }

    void SetUp() override
    {
    }

    void TearDown() override
    {
    }

    std::unique_ptr<PsidCookieImpl> pcookie_impl;
};


TEST_F(PsidCookieTest, check_setup)
{
    PsidCookieImpl *pci_dut = pcookie_impl.get();
    ASSERT_NE(pci_dut, nullptr);

    // check test clock's equivalence to the PsidCookieImpl clock
    Time start(set_clock(Time::now()));
    EXPECT_TRUE(start == *pci_dut->now_);

    // spot check other aspects of successful pci_dut creation
    EXPECT_TRUE(pci_dut->pcfg_.tls_key.defined());
    EXPECT_FALSE(pci_dut->not_tls_auth_mode_);
}

TEST_F(PsidCookieTest, valid_time)
{
    PsidCookieImpl &pci_dut(*pcookie_impl.get());
    ClientAddressMock cli_addr(*pci_dut.pcfg_.prng);
    ProtoSessionID cli_psid;
    ProtoSessionID srv_psid;
    // interval duplicates the computation in calculate_session_id_hmac()
    uint64_t interval = (pci_dut.pcfg_.handshake_window.raw() + 1) / 2;
    bool hmac_ok;

    cli_psid.randomize(*pci_dut.pcfg_.rng);

    set_clock(Time::now());
    srv_psid = pci_dut.calculate_session_id_hmac(cli_psid, cli_addr, 0);

    // server is in the same interval in which it offered the hmac
    hmac_ok = pci_dut.check_session_id_hmac(srv_psid, cli_psid, cli_addr);
    EXPECT_TRUE(hmac_ok);

    advance_clock(interval);
    // server is in the next interval after which it offered the hmac
    hmac_ok = pci_dut.check_session_id_hmac(srv_psid, cli_psid, cli_addr);
    EXPECT_TRUE(hmac_ok);

    advance_clock(interval);
    // server is two intervals after which it offered the hmac
    hmac_ok = pci_dut.check_session_id_hmac(srv_psid, cli_psid, cli_addr);
    EXPECT_FALSE(hmac_ok);
}
