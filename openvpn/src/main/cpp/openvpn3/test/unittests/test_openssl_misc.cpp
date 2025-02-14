#include "test_common.hpp"
#include <iostream>

#include <openvpn/openssl/sign/verify.hpp>
#include <openvpn/openssl/sign/pkcs7verify.hpp>
#include <openvpn/ssl/sslchoose.hpp>

using namespace openvpn;

constexpr const char *broken_pkcs7 = "-----BEGIN PKCS7-----\n"
                                     "MAsGCSqGSIb3DQEHAg==\n"
                                     "-----END PKCS7-----\n";

constexpr const char *unit_test_ca = "-----BEGIN CERTIFICATE-----\n"
                                     "MIIBuTCCAUCgAwIBAgIUTLtjSBzx53qZRvZ6Ur7D9kgoOHkwCgYIKoZIzj0EAwIw\n"
                                     "EzERMA8GA1UEAwwIdW5pdHRlc3QwIBcNMjMxMTIxMDk1NDQ3WhgPMjA3ODA4MjQw\n"
                                     "OTU0NDdaMBMxETAPBgNVBAMMCHVuaXR0ZXN0MHYwEAYHKoZIzj0CAQYFK4EEACID\n"
                                     "YgAEHYB2hn2xx3f4lClXDtdi36P19pMZA+kI1Dkv/Vn10vBZ/j9oa+P99T8duz/e\n"
                                     "QlPeHpesNJO4fX8iEDj6+vMeWejOT7jAQ4MmG5EZjpcBKxCfwFooEvzu8bVujUcu\n"
                                     "wTQEo1MwUTAdBgNVHQ4EFgQUPcgBEVXjF5vYfDsInoE3dF6UfQswHwYDVR0jBBgw\n"
                                     "FoAUPcgBEVXjF5vYfDsInoE3dF6UfQswDwYDVR0TAQH/BAUwAwEB/zAKBggqhkjO\n"
                                     "PQQDAgNnADBkAjBLPAGrQAyinigqiu0RomoV8TVaknVLFSq6H6A8jgvzfsFCUK1O\n"
                                     "dvNZhFPM6idKB+oCME2JLOBANCSV8o7aJzq7SYHKwPyb1J4JFlwKe/0Jpv7oh9b1\n"
                                     "IJbuaM9Z/VSKbrIXGg==\n"
                                     "-----END CERTIFICATE-----\n";


constexpr const char *unit_test_ca_key [[maybe_unused]] = "-----BEGIN PRIVATE KEY-----\n"
                                                          "MIG2AgEAMBAGByqGSM49AgEGBSuBBAAiBIGeMIGbAgEBBDCJ92tBE1WmpBkPwgcN\n"
                                                          "5xJ93tVilpsS5hi22V/VIGpCwKplSzTdB61TkB5RRWuQMAuhZANiAAQdgHaGfbHH\n"
                                                          "d/iUKVcO12Lfo/X2kxkD6QjUOS/9WfXS8Fn+P2hr4/31Px27P95CU94el6w0k7h9\n"
                                                          "fyIQOPr68x5Z6M5PuMBDgyYbkRmOlwErEJ/AWigS/O7xtW6NRy7BNAQ=\n"
                                                          "-----END PRIVATE KEY-----\n";

/* first stanza of a German children's song for Saint Martin */
constexpr const char *laterne = "Laterne, Laterne, Sonne, Mond und Sterne,\n"
                                "brenne auf mein Licht,\n"
                                "brenne auf mein Licht,\n"
                                "aber du, meine Liebe Laterne, nicht.\n";


// created used openssl smime -in laterne --binary -sign -signer unittest.pem -inkey unittest.pem  -outform pem
constexpr const char *laterne_sig = "-----BEGIN PKCS7-----\n"
                                    "MIIDmAYJKoZIhvcNAQcCoIIDiTCCA4UCAQExDzANBglghkgBZQMEAgEFADALBgkq\n"
                                    "hkiG9w0BBwGgggG9MIIBuTCCAUCgAwIBAgIUTLtjSBzx53qZRvZ6Ur7D9kgoOHkw\n"
                                    "CgYIKoZIzj0EAwIwEzERMA8GA1UEAwwIdW5pdHRlc3QwIBcNMjMxMTIxMDk1NDQ3\n"
                                    "WhgPMjA3ODA4MjQwOTU0NDdaMBMxETAPBgNVBAMMCHVuaXR0ZXN0MHYwEAYHKoZI\n"
                                    "zj0CAQYFK4EEACIDYgAEHYB2hn2xx3f4lClXDtdi36P19pMZA+kI1Dkv/Vn10vBZ\n"
                                    "/j9oa+P99T8duz/eQlPeHpesNJO4fX8iEDj6+vMeWejOT7jAQ4MmG5EZjpcBKxCf\n"
                                    "wFooEvzu8bVujUcuwTQEo1MwUTAdBgNVHQ4EFgQUPcgBEVXjF5vYfDsInoE3dF6U\n"
                                    "fQswHwYDVR0jBBgwFoAUPcgBEVXjF5vYfDsInoE3dF6UfQswDwYDVR0TAQH/BAUw\n"
                                    "AwEB/zAKBggqhkjOPQQDAgNnADBkAjBLPAGrQAyinigqiu0RomoV8TVaknVLFSq6\n"
                                    "H6A8jgvzfsFCUK1OdvNZhFPM6idKB+oCME2JLOBANCSV8o7aJzq7SYHKwPyb1J4J\n"
                                    "FlwKe/0Jpv7oh9b1IJbuaM9Z/VSKbrIXGjGCAZ8wggGbAgEBMCswEzERMA8GA1UE\n"
                                    "AwwIdW5pdHRlc3QCFEy7Y0gc8ed6mUb2elK+w/ZIKDh5MA0GCWCGSAFlAwQCAQUA\n"
                                    "oIHkMBgGCSqGSIb3DQEJAzELBgkqhkiG9w0BBwEwHAYJKoZIhvcNAQkFMQ8XDTIz\n"
                                    "MTEyMTExNTYxOFowLwYJKoZIhvcNAQkEMSIEIL6nbAP3MXDvmWwGIpts8nUoOyHn\n"
                                    "aDA9IjR3QooF/IYvMHkGCSqGSIb3DQEJDzFsMGowCwYJYIZIAWUDBAEqMAsGCWCG\n"
                                    "SAFlAwQBFjALBglghkgBZQMEAQIwCgYIKoZIhvcNAwcwDgYIKoZIhvcNAwICAgCA\n"
                                    "MA0GCCqGSIb3DQMCAgFAMAcGBSsOAwIHMA0GCCqGSIb3DQMCAgEoMAoGCCqGSM49\n"
                                    "BAMCBGcwZQIwGjRweguw3AXhfSBu4czIiOk/kdncLIAzz0S78YURt5wYlbHSnMuO\n"
                                    "YSNyVn97Uc+UAjEA6+tj2o1i42yiF5WNMp/92QtfCV7TZE3ssiLxqst2aqlIY29H\n"
                                    "G9j5hdY2ZRkhUCHL\n"
                                    "-----END PKCS7-----";

// created used openssl smime -in laterne --binary -sign -signer unittest.pem -inkey unittest.pem  -outform pem -nodetach
constexpr const char *laterne_signd = "-----BEGIN PKCS7-----\n"
                                      "MIIEGwYJKoZIhvcNAQcCoIIEDDCCBAgCAQExDzANBglghkgBZQMEAgEFADCBjAYJ\n"
                                      "KoZIhvcNAQcBoH8EfUxhdGVybmUsIExhdGVybmUsIFNvbm5lLCBNb25kIHVuZCBT\n"
                                      "dGVybmUsCmJyZW5uZSBhdWYgbWVpbiBMaWNodCwKYnJlbm5lIGF1ZiBtZWluIExp\n"
                                      "Y2h0LAphYmVyIGR1LCBtZWluZSBMaWViZSBMYXRlcm5lLCBuaWNodC4KoIIBvTCC\n"
                                      "AbkwggFAoAMCAQICFEy7Y0gc8ed6mUb2elK+w/ZIKDh5MAoGCCqGSM49BAMCMBMx\n"
                                      "ETAPBgNVBAMMCHVuaXR0ZXN0MCAXDTIzMTEyMTA5NTQ0N1oYDzIwNzgwODI0MDk1\n"
                                      "NDQ3WjATMREwDwYDVQQDDAh1bml0dGVzdDB2MBAGByqGSM49AgEGBSuBBAAiA2IA\n"
                                      "BB2AdoZ9scd3+JQpVw7XYt+j9faTGQPpCNQ5L/1Z9dLwWf4/aGvj/fU/Hbs/3kJT\n"
                                      "3h6XrDSTuH1/IhA4+vrzHlnozk+4wEODJhuRGY6XASsQn8BaKBL87vG1bo1HLsE0\n"
                                      "BKNTMFEwHQYDVR0OBBYEFD3IARFV4xeb2Hw7CJ6BN3RelH0LMB8GA1UdIwQYMBaA\n"
                                      "FD3IARFV4xeb2Hw7CJ6BN3RelH0LMA8GA1UdEwEB/wQFMAMBAf8wCgYIKoZIzj0E\n"
                                      "AwIDZwAwZAIwSzwBq0AMop4oKortEaJqFfE1WpJ1SxUquh+gPI4L837BQlCtTnbz\n"
                                      "WYRTzOonSgfqAjBNiSzgQDQklfKO2ic6u0mBysD8m9SeCRZcCnv9Cab+6IfW9SCW\n"
                                      "7mjPWf1Uim6yFxoxggGgMIIBnAIBATArMBMxETAPBgNVBAMMCHVuaXR0ZXN0AhRM\n"
                                      "u2NIHPHneplG9npSvsP2SCg4eTANBglghkgBZQMEAgEFAKCB5DAYBgkqhkiG9w0B\n"
                                      "CQMxCwYJKoZIhvcNAQcBMBwGCSqGSIb3DQEJBTEPFw0yMzExMjExMTU5MzZaMC8G\n"
                                      "CSqGSIb3DQEJBDEiBCC+p2wD9zFw75lsBiKbbPJ1KDsh52gwPSI0d0KKBfyGLzB5\n"
                                      "BgkqhkiG9w0BCQ8xbDBqMAsGCWCGSAFlAwQBKjALBglghkgBZQMEARYwCwYJYIZI\n"
                                      "AWUDBAECMAoGCCqGSIb3DQMHMA4GCCqGSIb3DQMCAgIAgDANBggqhkiG9w0DAgIB\n"
                                      "QDAHBgUrDgMCBzANBggqhkiG9w0DAgIBKDAKBggqhkjOPQQDAgRoMGYCMQD7s/oo\n"
                                      "MspfBQyDQ3RbEmJnub3d0JQjVFJjpSuQZuFfR4tn061LM0txurggtkCLU3MCMQCl\n"
                                      "rkK0zZs3G7UH2W4XXmzBQsfYGooTSyt5hASTo14xnA8GssngKQcztjxQ19nIic0=\n"
                                      "-----END PKCS7-----";


TEST(OpenSSL, verify_broken_pkcs7)
{
    std::list<OpenSSLPKI::X509> certs;

    certs.emplace_back(unit_test_ca, "unit test certificate");

    const std::string ident = "nothing to see here";

    OVPN_EXPECT_THROW(
        OpenSSLSign::verify_pkcs7(certs, broken_pkcs7, ident),
        OpenSSLException,
        "OpenSSLSign::verify_pkcs7: verification failed");
}

TEST(OpenSSL, verify_valid_pkcs7)
{
    std::list<OpenSSLPKI::X509> certs;

    certs.emplace_back(unit_test_ca, "unit test certificate");

    EXPECT_NO_THROW(OpenSSLSign::verify_pkcs7(certs, laterne_sig, laterne));
}

TEST(OpenSSL, verify_nodetach_pkcs7)
{
    std::list<OpenSSLPKI::X509> certs;

    certs.emplace_back(unit_test_ca, "unit test certificate");

    EXPECT_NO_THROW(OpenSSLSign::verify_pkcs7(certs, laterne_signd, laterne));
}