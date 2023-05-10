//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2019 OpenVPN Inc.
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

#include "test_common.h"

using namespace openvpn;

#ifdef USE_MBEDTLS

#include <openvpn/auth/authcert.hpp>
#include <openvpn/mbedtls/ssl/sslctx.hpp>

static const std::string ca_str = "\n\
-----BEGIN CERTIFICATE-----\n\
MIIDSjCCAjKgAwIBAgIUfLhJAQO17QauTyTEDO518zSf4y0wDQYJKoZIhvcNAQEL\n\
BQAwFTETMBEGA1UEAwwKUEctTVQtVGVzdDAgFw0yMjA5MjEyMTI1NThaGA8yMTIy\n\
MDgyODIxMjU1OFowFTETMBEGA1UEAwwKUEctTVQtVGVzdDCCASIwDQYJKoZIhvcN\n\
AQEBBQADggEPADCCAQoCggEBAL1nDTIADdH18KgZwwgFHH4uj7No3Qj1n983qna9\n\
l+Ha4fQbnr3eoB8QrAzU+L5VlsPVeck2hReRx3He4T/ogm/uOTAvgTT72z4qpXS7\n\
ys5ya28/G54Q6R3G8Flo7i02SaooQE0u/1k7fCUhO8p8URMUNI1eklVUgqZUdUWF\n\
pDv8JZzpNX0KW5Q/yJF2wtTWbv0vObvwBHRHL0xhTNhgh7XCZtKoPGZIEvey0tBp\n\
72mm3wDvgpuutdyL85NfkvLM6rr8s3nFaKphFSdy5edpzjCWPN47lEJj/G/B2nRQ\n\
o5zXEJJJ6AzZO/5rSMy2IO4cex1jYZM9Lu/IvscS7BW9IyUCAwEAAaOBjzCBjDAd\n\
BgNVHQ4EFgQURShYDUrq+7fvSbEsQ/FwzLVI70kwUAYDVR0jBEkwR4AURShYDUrq\n\
+7fvSbEsQ/FwzLVI70mhGaQXMBUxEzARBgNVBAMMClBHLU1ULVRlc3SCFHy4SQED\n\
te0Grk8kxAzudfM0n+MtMAwGA1UdEwQFMAMBAf8wCwYDVR0PBAQDAgEGMA0GCSqG\n\
SIb3DQEBCwUAA4IBAQCEnCQvOfC8FoNgpGHPuBXKDgMDRmubU+hvibGCtOQGXU+o\n\
f3jjxoLsn+qgop8FsyjS86yOH3mx6Y4nSTI/8nmHFHwSflJbnaMv2qBhsGr0Wrwd\n\
wDhQ7W3H6KZFjZX9w8dFSTy1kuJn/U5xoZQj9ovztirmE7S5jP8oXsitY82L+a80\n\
2J7/+yCi0TJrXa2DLLK+UjqCU3NilnwV3GsNuj2Wgnfa+4/mIccIVyD55Jn7Vxpn\n\
Iglk8X4JMDg5O5MMXtiUIkmUuAjrE9kP1LlX3q7tRYH0cyLpDUjl/+ENFafjcaOq\n\
Cq6cUgLYAFN4Ihhmz2WasKJIIhJ7ZZVDN/HRDJnI\n\
-----END CERTIFICATE-----\n\
";

static const std::string cert_str = "\n\
-----BEGIN CERTIFICATE-----\n\
MIIDYjCCAkqgAwIBAgIBATANBgkqhkiG9w0BAQsFADAVMRMwEQYDVQQDDApQRy1N\n\
VC1UZXN0MCAXDTIyMDkyMTIxMjU1OFoYDzIxMjIwODI4MjEyNTU4WjAWMRQwEgYD\n\
VQQDDAt0ZXN0LXNlcnZlcjCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEB\n\
ALvFb9swVPS8H2yTNfao5Cf7EhEkrlKIlLgQFDmsWZAxw8SKML7WCjdBLyw7K8CF\n\
f3st1vmLG0LUULHAJo0MdeMMgdDDU15Q4gf1F9/vl1Qnko4/zDxI1o9sKJRmTdYS\n\
ntkC4DWL+Y4EAO5e3x+Ae7N1knjQKfKomkvsfVvpjBFhELqYHoqSfHvxri5088aB\n\
36+NIue1D+c6l/OaG5HS87MJLqNd4qC4EWQX4vZYnILxGRI/1wENUxtpk+jCCNVd\n\
iXJG1qsAxrlQzEz0rn0cponoVVbTfXVN5KVTU3VDpm0TYRF7qKnOEmBMBqNWPtGo\n\
feQ3LtHnB77Lw0HwwZwwI9kCAwEAAaOBuTCBtjAJBgNVHRMEAjAAMB0GA1UdDgQW\n\
BBTY2utk9nPH3a2fAofge/OG5eRrujBQBgNVHSMESTBHgBRFKFgNSur7t+9JsSxD\n\
8XDMtUjvSaEZpBcwFTETMBEGA1UEAwwKUEctTVQtVGVzdIIUfLhJAQO17QauTyTE\n\
DO518zSf4y0wEwYDVR0lBAwwCgYIKwYBBQUHAwEwCwYDVR0PBAQDAgWgMBYGA1Ud\n\
EQQPMA2CC3Rlc3Qtc2VydmVyMA0GCSqGSIb3DQEBCwUAA4IBAQCrW1dkuTNSD9ly\n\
8htodCiDJbqIA87ui7Ecc/WNbsj7iBbfUYYffgeme3aaNSvJl0BQd/czcCOUpdLB\n\
UEF8BltqvQxFGKDYCTf0UYSp8vOoeSahI65HjJ/J5vgdrO3YnwBKsPkO/XlDViDa\n\
7Ai9v64jxf2MXJ4YleIQujvhpJ/slu1sRuIyjVNp+un9n+9cB1UxjGv7g3EtUAYR\n\
WJ3ZwKTXg6CKh2FwkWAKH85s1JRxrsAXUFqMV5t2+OBjGxiUi8e+ioEqxDmdVSj6\n\
maEDfbDAJAchP61YODqHEGiEXKCdiGF47a+aV/WGjiuS+htFg5qWnk2RPC64PNax\n\
UTrzK+hr\n\
-----END CERTIFICATE-----\n\
";

static const std::string cert64_str = "\n\
-----BEGIN CERTIFICATE-----\n\
MIIDhTCCAm2gAwIBAgIIASNFZ4mrze8wDQYJKoZIhvcNAQELBQAwFTETMBEGA1UE\n\
AwwKUEctTVQtVGVzdDAgFw0yMjA5MjEyMTI1NThaGA8yMTIyMDgyODIxMjU1OFow\n\
JDEiMCAGA1UEAwwZdGVzdC1zZXJ2ZXItNjQtYml0LXNlcmlhbDCCASIwDQYJKoZI\n\
hvcNAQEBBQADggEPADCCAQoCggEBANQv7dyvXDwDCZRseoMPytCtTysh9U74yELD\n\
Crh1vbC0NgDL/rlEfXGTWJd7R0hdTXdHhKkL2QfpXwxr6qNnVyp/WuZkxd6f+Rk8\n\
lIdEZAOSgXH03wySSDbwfMBmDYW1V4mH9ac3mL+SWPfGSBR3PEZDe1XiwOhakutT\n\
j0487TeCuupLUfVCco7imhhYKIl+Vqz4iihucXTF+FS4JLGMuFcwXglWwNZj+Tjn\n\
in/KXVcyvyMn5mQc/I1S6hQ55RAyms3AP7XSB3uZmyS1cWFQvCSMV5w22WrvZ3R0\n\
rJtL+CI5DNRmM1UASmG9L+WSestJTYwgvH4sRtSqBALsLrs+giMCAwEAAaOBxzCB\n\
xDAJBgNVHRMEAjAAMB0GA1UdDgQWBBR1oQ5PQVTk2KTcaZOLkr7UQe4ejDBQBgNV\n\
HSMESTBHgBRFKFgNSur7t+9JsSxD8XDMtUjvSaEZpBcwFTETMBEGA1UEAwwKUEct\n\
TVQtVGVzdIIUfLhJAQO17QauTyTEDO518zSf4y0wEwYDVR0lBAwwCgYIKwYBBQUH\n\
AwEwCwYDVR0PBAQDAgWgMCQGA1UdEQQdMBuCGXRlc3Qtc2VydmVyLTY0LWJpdC1z\n\
ZXJpYWwwDQYJKoZIhvcNAQELBQADggEBAAQlQDEd2hxjXcwaGMQCplrIz3JVeZVE\n\
IiXHd5rqfXSTmJVCjvTOaTN7d9pc98OyPQQc1l6XGqQ4MR/tn8JZ34ooTfS/KaBp\n\
22yTI8OqDRTWvemg92D5saP69hML/SJv02nKvcbIWgbVXk4Q132TTJjKgyQgA7I6\n\
fVleMn4Uk34MetJGOzm4w1AulHI3C4j5FhIB306C6gtFroH7PYFj/WwDHKzwXKNb\n\
vCM9eK5hz+PSFYduNlEvWDTwdO0BuDBT0iyL1y220jlZf0KCFQmRXD2rJazIvsaM\n\
/hJslb5Fn8CR924uLsy9Q2/sTwvuzjl6M3IxRvIgLWABls4GjiNHIO8=\n\
-----END CERTIFICATE-----\n\
";

TEST(authcert_mbedtls, ca)
{
    AuthCert ac;
    MbedTLSContext::load_cert_info_into_authcert(ac, ca_str);
    ASSERT_TRUE(ac.defined());
    ASSERT_TRUE(ac.sn_defined());
    ASSERT_EQ(ac.serial_number_as_int64(), -1);
    ASSERT_EQ(ac.to_string(), "CN=PG-MT-Test SN=7c:b8:49:01:03:b5:ed:06:ae:4f:24:c4:0c:ee:75:f3:34:9f:e3:2d ISSUER_FP=1d7dad803066f6d18771fb42b45a21618bb261cc");
}

TEST(authcert_mbedtls, cert)
{
    AuthCert ac;
    MbedTLSContext::load_cert_info_into_authcert(ac, cert_str);
    ASSERT_TRUE(ac.defined());
    ASSERT_TRUE(ac.sn_defined());
    ASSERT_EQ(ac.serial_number_as_int64(), 1);
    ASSERT_EQ(ac.to_string(), "CN=test-server SN=01 ISSUER_FP=d79cbf8db337fdb401d63a3a905a7bf712f693c1");
}

TEST(authcert_mbedtls, cert64)
{
    AuthCert ac;
    MbedTLSContext::load_cert_info_into_authcert(ac, cert64_str);
    ASSERT_TRUE(ac.defined());
    ASSERT_TRUE(ac.sn_defined());
    ASSERT_EQ(ac.serial_number_as_int64(), 81985529216486895);
    ASSERT_EQ(ac.to_string(), "CN=test-server-64-bit-serial SN=01:23:45:67:89:ab:cd:ef ISSUER_FP=c62493563a3c04f6fbd839ef499394400a60ac55");
}

TEST(authcert_mbedtls, sn_0)
{
    AuthCert ac("sn_0", 0);
    ASSERT_TRUE(ac.defined());
    ASSERT_TRUE(ac.sn_defined());
    ASSERT_EQ(ac.serial_number_as_int64(), 0);
    ASSERT_EQ(ac.to_string(), "CN=sn_0 SN=00 ISSUER_FP=0000000000000000000000000000000000000000");
}

TEST(authcert_mbedtls, sn_1)
{
    AuthCert ac("sn_1", 1);
    ASSERT_TRUE(ac.defined());
    ASSERT_TRUE(ac.sn_defined());
    ASSERT_EQ(ac.serial_number_as_int64(), 1);
    ASSERT_EQ(ac.to_string(), "CN=sn_1 SN=01 ISSUER_FP=0000000000000000000000000000000000000000");
}

TEST(authcert_mbedtls, sn_255)
{
    AuthCert ac("sn_255", 255);
    ASSERT_TRUE(ac.defined());
    ASSERT_TRUE(ac.sn_defined());
    ASSERT_EQ(ac.serial_number_as_int64(), 255);
    ASSERT_EQ(ac.to_string(), "CN=sn_255 SN=ff ISSUER_FP=0000000000000000000000000000000000000000");
}

TEST(authcert_mbedtls, sn_256)
{
    AuthCert ac("sn_256", 256);
    ASSERT_TRUE(ac.defined());
    ASSERT_TRUE(ac.sn_defined());
    ASSERT_EQ(ac.serial_number_as_int64(), 256);
    ASSERT_EQ(ac.to_string(), "CN=sn_256 SN=01:00 ISSUER_FP=0000000000000000000000000000000000000000");
}

TEST(authcert_mbedtls, sn_32bit_pre)
{
    AuthCert ac("sn_32bit_pre", 4294967295ll);
    ASSERT_TRUE(ac.defined());
    ASSERT_TRUE(ac.sn_defined());
    ASSERT_EQ(ac.serial_number_as_int64(), 4294967295ll);
    ASSERT_EQ(ac.to_string(), "CN=sn_32bit_pre SN=ff:ff:ff:ff ISSUER_FP=0000000000000000000000000000000000000000");
}

TEST(authcert_mbedtls, sn_32bit_post)
{
    AuthCert ac("sn_32bit_post", 4294967296ll);
    ASSERT_TRUE(ac.defined());
    ASSERT_TRUE(ac.sn_defined());
    ASSERT_EQ(ac.serial_number_as_int64(), 4294967296ll);
    ASSERT_EQ(ac.to_string(), "CN=sn_32bit_post SN=01:00:00:00:00 ISSUER_FP=0000000000000000000000000000000000000000");
}

TEST(authcert_mbedtls, sn_64bit)
{
    AuthCert ac("sn_64bit", 81985529216486895ll);
    ASSERT_TRUE(ac.defined());
    ASSERT_TRUE(ac.sn_defined());
    ASSERT_EQ(ac.serial_number_as_int64(), 81985529216486895ll);
    ASSERT_EQ(ac.to_string(), "CN=sn_64bit SN=01:23:45:67:89:ab:cd:ef ISSUER_FP=0000000000000000000000000000000000000000");
}

TEST(authcert_mbedtls, empty)
{
    AuthCert ac;
    ASSERT_FALSE(ac.defined());
    ASSERT_FALSE(ac.sn_defined());
}

#endif
