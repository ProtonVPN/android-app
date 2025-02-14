#include "test_common.hpp"
#include <iostream>

#include <openvpn/common/file.hpp>
#include <openvpn/pki/cclist.hpp>
#include <openvpn/openssl/pki/x509.hpp>
#include <openvpn/openssl/pki/x509store.hpp>
#include <openvpn/openssl/pki/pkey.hpp>
#include <openvpn/openssl/pki/dh.hpp>
#include <openvpn/openssl/pki/crl.hpp>
#include <openvpn/init/initprocess.hpp>

using namespace openvpn;

typedef CertCRLListTemplate<OpenSSLPKI::X509List, OpenSSLPKI::CRLList> CertCRLList;

template <typename VEC>
VEC move_contents(VEC &src)
{
    VEC v;
    for (auto &e : src)
        v.push_back(std::move(e));
    return v;
}

void move_contents(CertCRLList &dest, CertCRLList &src)
{
    dest.certs = move_contents(src.certs);
    dest.crls = move_contents(src.crls);
}

#define CERTDIR UNITTEST_SOURCE_DIR "/pki"

void test_cert_crl()
{
    std::string cert_crl_txt = read_text(CERTDIR "/certcrl.pem");

    CertCRLList ccl, ccl2;
    ccl.parse_pem(cert_crl_txt, "TEST1");
    CertCRLList ccl1(ccl);
    ccl2 = ccl1;
    ccl2 = ccl1;
    std::string rend2 = ccl2.render_pem();

    CertCRLList ccl3;
    ccl3.parse_pem_file(CERTDIR "/certcrl.pem");
    std::string rend3 = ccl3.render_pem();

    ASSERT_EQ(rend2, rend3);

    CertCRLList ccl4(rend3, "TEST2");
    CertCRLList ccl5(std::move(ccl4));
    ccl2 = ccl5;
    rend2 = ccl2.render_pem();
    ASSERT_EQ(rend2, rend3);

    CertCRLList ccl6(rend3, "TEST3");
    move_contents(ccl2, ccl6);
    rend2 = ccl2.render_pem();
    ASSERT_EQ(rend2, rend3);

    OpenSSLPKI::X509Store xs(ccl2);

    // std::cout << rend2;
}

constexpr const char *testcert = "-----BEGIN CERTIFICATE-----\n"
                                 "MIIBuzCCAUCgAwIBAgIUEwa9vm0C63Cc/kFu8lFmOKFUdccwCgYIKoZIzj0EAwIw\n"
                                 "EzERMA8GA1UEAwwIdW5pdHRlc3QwIBcNMjQwNTE1MTA1NDM3WhgPMjA3OTAyMTYx\n"
                                 "MDU0MzdaMBMxETAPBgNVBAMMCHVuaXR0ZXN0MHYwEAYHKoZIzj0CAQYFK4EEACID\n"
                                 "YgAEEVRoVjNQeYLPBlst7a7vxm6KUT5TL8iDel5I7Vt0CD2saQ+E0oUrMrk/W7uB\n"
                                 "FhbBDbKu+AiXBmudIjQbrx4JiNPD7wouGCY3Up6C6hdDMQAtPqIIWOGj/13OshyY\n"
                                 "EX/eo1MwUTAdBgNVHQ4EFgQUONymlHRoIr+aGp7kss/Yl5gEOHQwHwYDVR0jBBgw\n"
                                 "FoAUONymlHRoIr+aGp7kss/Yl5gEOHQwDwYDVR0TAQH/BAUwAwEB/zAKBggqhkjO\n"
                                 "PQQDAgNpADBmAjEAx4NDBMtTW/4qeSdedxpNH4DCnI5iue+22UNTt/dGWBMzcYF7\n"
                                 "xW53r2QVcCKzoJABAjEA7//UDtN8gZgfiYaCXh9Qwew8DSsn1+B9mY6e3hQQ00nJ\n"
                                 "Qv3xi0OJFoAxAQBG0weY\n"
                                 "-----END CERTIFICATE-----";

void test_output_pem()
{
    BIO *bio_in = ::BIO_new_mem_buf(const_cast<char *>(testcert), static_cast<int>(std::strlen(testcert)));

    ::X509 *cert = ::PEM_read_bio_X509(bio_in, nullptr, nullptr, nullptr);

    ASSERT_TRUE(cert);

    std::string pem_out = OpenSSLPKI::X509_get_pem_encoding(cert);

    EXPECT_EQ(pem_out, testcert);

    ::X509_free(cert);
    ::BIO_free(bio_in);
}

void test_pkey()
{
    std::string pkey_txt = read_text(CERTDIR "/key.pem");

    OpenSSLPKI::PKey pkey, pkey2;
    pkey.parse_pem(pkey_txt, "TEST0", nullptr);
    OpenSSLPKI::PKey pkey1(pkey);
    pkey2 = pkey1;
    pkey2 = pkey1;
    std::string rend2 = pkey2.render_pem();

    OpenSSLPKI::PKey pkey3(pkey_txt, "TEST2", nullptr);
    std::string rend3 = pkey3.render_pem();

    ASSERT_EQ(rend2, rend3);

    OpenSSLPKI::PKey pkey4(rend3, "TEST3", nullptr);
    OpenSSLPKI::PKey pkey5(std::move(pkey4));
    pkey2 = pkey5;
    rend2 = pkey2.render_pem();
    ASSERT_EQ(rend2, rend3);

    // std::cout << rend2;
}

void test_dh()
{
    std::string dh_txt = read_text(CERTDIR "/dh2048.pem");

    OpenSSLPKI::DH dh, dh2;
    dh.parse_pem(dh_txt);
    OpenSSLPKI::DH dh1(dh);
    dh2 = dh1;
    dh2 = dh1;
    std::string rend2 = dh2.render_pem();

    OpenSSLPKI::DH dh3(dh_txt);
    std::string rend3 = dh3.render_pem();

    ASSERT_EQ(rend2, rend3);

    OpenSSLPKI::DH dh4(rend3);
    OpenSSLPKI::DH dh5(std::move(dh4));
    dh2 = dh5;
    rend2 = dh2.render_pem();
    ASSERT_EQ(rend2, rend3);

    // std::cout << rend2;
}

static bool verbose_output = false;

TEST(PKI, crl)
{
    override_logOutput(verbose_output, test_cert_crl);
}

TEST(PKI, pkey)
{
    override_logOutput(verbose_output, test_pkey);
}

TEST(PKI, dh)
{
    override_logOutput(verbose_output, test_dh);
}