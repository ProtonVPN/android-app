//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
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
//    If not, see <http://www.gnu.org/licenses/>.
//
//
//    Basic unit test for the openvpn/openssl/pki/x509certinfo.hpp functions
//

#include <string>

#include "test_common.h"
#include "openvpn/openssl/pki/x509.hpp"
#include "openvpn/openssl/pki/x509certinfo.hpp"

using namespace openvpn;

namespace unittests {

std::string test_cert =
    "-----BEGIN CERTIFICATE-----\n"
    "MIIFrjCCA5agAwIBAgIJFXABl4gwlJIEMA0GCSqGSIb3DQEBCwUAMHAxCzAJBgNV\n"
    "BAYTAlVTMQswCQYDVQQIDAJDQTETMBEGA1UEBwwKUGxlYXNhbnRvbjEYMBYGA1UE\n"
    "CgwPT3BlblZQTi1URVNULUNBMSUwIwYJKoZIhvcNAQkBFhZleGFtcGxlLmNhQGV4\n"
    "YW1wbGUubmV0MB4XDTE5MTAwMjEyMzY0OFoXDTI5MDkyOTEyMzY0OFowezELMAkG\n"
    "A1UEBhMCVVMxCzAJBgNVBAgMAkNBMRUwEwYDVQQKDAxPcGVuVlBOLVRFU1QxHTAb\n"
    "BgNVBAMMFHNlcnZlci0xLmV4YW1wbGUubmV0MSkwJwYJKoZIhvcNAQkBFhpleGFt\n"
    "cGxlLXNlcnZlckBleGFtcGxlLm5ldDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCC\n"
    "AQoCggEBAN7KKMU2A7X74KYn+agBvQmW2zr/OFH/PJn6sMk94aDAIWsa7KccuV8W\n"
    "d/69XH2FXK2ygSd9df8kO8mGkDl1w5nC/LUk5V0KNqsZGLyNDRvulSFJ2eQChOOs\n"
    "snOdpf17e/yzs08KY5RME9+HBWx2GpQQWHVsmqvPF+pPJnBctOm8azBOAUZRDBuj\n"
    "TxCWtwLwVjnFHGSATETuvCiTPuDa9sbw5ibCLFz9ge94ptXcXEU6z+GuighQI9rU\n"
    "o8BVFF6DiaWZn3jC5KsA1dX81c+UpDpxwOpG9MXg8RRm8rWwsvC/RvjVYjGDBdra\n"
    "oSuHWPjzH1DIJ31ptjKUPAVdR8ZxAGcCAwEAAaOCAT4wggE6MAkGA1UdEwQCMAAw\n"
    "EQYJYIZIAYb4QgEBBAQDAgZAMDQGCWCGSAGG+EIBDQQnFiVUZXN0IENlcnRpZmlj\n"
    "YXRlIC0gTk9UIEZPUiBQUk9EVUNUSU9OMB0GA1UdDgQWBBRsQDoK1XxrwQdrKmcC\n"
    "7/HfbSrQFTCBogYDVR0jBIGaMIGXgBTa10VpSdTIo1PhrwMuGmGrUMerq6F0pHIw\n"
    "cDELMAkGA1UEBhMCVVMxCzAJBgNVBAgMAkNBMRMwEQYDVQQHDApQbGVhc2FudG9u\n"
    "MRgwFgYDVQQKDA9PcGVuVlBOLVRFU1QtQ0ExJTAjBgkqhkiG9w0BCQEWFmV4YW1w\n"
    "bGUuY2FAZXhhbXBsZS5uZXSCCQDm/rJ9Tz3bEDATBgNVHSUEDDAKBggrBgEFBQcD\n"
    "ATALBgNVHQ8EBAMCBaAwDQYJKoZIhvcNAQELBQADggIBAHl41WvFsh+nscCx+1X2\n"
    "RudmnsuKHFUAktpAOdG3vh+5zh2v3PZYWoS4sgmBl0Zvia4VC/xTrcC2ywJILLAM\n"
    "cda6CUXMF3+kJMi+qlgn6WRn9RsUHLQFa1/y7zNkuo38zkLlJaiKPQfm1YPkUtnr\n"
    "n74W9XBrZ2rWBsqL2XCDayEs1IAjL9zs0F1Bs0MCgf+BccCu7wFL886+Y8mhAkRJ\n"
    "c0aniG/bsawOrrF8JwW2MP/QpPls2BSWmfwJASxX57AbSQ8TmMf289ozTupcBVMC\n"
    "N973ks9n/35cRtW9SHtwpdsb4nvXFZi6DCfyS3PBpHgi/mRuhgWWSLaVr40RnlHI\n"
    "NvW0x7SPJwkbHeWz6PStrZJLjkJ9LuvRQwb2+wH6SjIxQiJ/AMXlSL2USASdLR32\n"
    "eiPUWq5xalTrNQINcnEfVT/ruTInY2vytUaQgFTQvJKp0DJZZHEmkvEQC77IkI7Y\n"
    "ED4Icu9CLCpXN7axV4Ga0iM53kX4MsDt419mmD8NoYJciHzBZHuJ6cD1tAsUUov7\n"
    "NJZQLYfixIs63ZNEgb5gCkKywy40gZ+jaK3ard5LzyRUhgWHXdV7oZU7DkY5yAON\n"
    "63gBg9THgEvcEhG/Ci60y6pB+YpXTiVGkuJvqLdSCn3qota8v+/Fm9ujrlJk1evR\n"
    "fYFKjF0w1F5ftfpCbucSMbqt\n"
    "-----END CERTIFICATE-----\n";

TEST(OpenSSL_X509_get_subject, old_format) {
  OpenSSLPKI::X509 x509crt(test_cert, "Embedded Test Server Cert");
  std::string expect(
      "/C=US/ST=CA/O=OpenVPN-TEST/CN=server-1.example.net/"
      "emailAddress=example-server@example.net");

  ASSERT_EQ(OpenSSLPKI::x509_get_subject(x509crt.obj()), expect);
  ASSERT_EQ(OpenSSLPKI::x509_get_subject(x509crt.obj(), false), expect);
}

TEST(OpenSSL_X509_get_subject, new_format) {
  OpenSSLPKI::X509 x509crt(test_cert, "Embedded Test Server Cert");
  std::string expect(
      "C=US, ST=CA, O=OpenVPN-TEST, CN=server-1.example.net, "
      "emailAddress=example-server@example.net");

  ASSERT_EQ(OpenSSLPKI::x509_get_subject(x509crt.obj(), true), expect);
}

TEST(OpenSSL_X509_get_serial, numeric) {
  OpenSSLPKI::X509 x509crt(test_cert, "Embedded Test Server Cert");
  std::string expect("395452524166311612932");

  ASSERT_EQ(OpenSSLPKI::x509_get_serial(x509crt.obj()), expect);
}

TEST(OpenSSL_X509_get_serial, hexadecimal) {
  OpenSSLPKI::X509 x509crt(test_cert, "Embedded Test Server Cert");
  std::string expect("15:70:01:97:88:30:94:92:04");

  ASSERT_EQ(OpenSSLPKI::x509_get_serial_hex(x509crt.obj()), expect);
}

TEST(OpenSSL_X509_get_field, basic_checks) {
  OpenSSLPKI::X509 x509crt(test_cert, "Embedded Test Server Cert");

  ASSERT_EQ(OpenSSLPKI::x509_get_field(x509crt.obj(), NID_commonName),
            "server-1.example.net");

  ASSERT_EQ(OpenSSLPKI::x509_get_field(x509crt.obj(), NID_stateOrProvinceName),
            "CA");

  ASSERT_EQ(OpenSSLPKI::x509_get_field(x509crt.obj(), NID_localityName), "");

  ASSERT_EQ(OpenSSLPKI::x509_get_field(x509crt.obj(), NID_pkcs9_emailAddress),
            "example-server@example.net");

  ASSERT_EQ(OpenSSLPKI::x509_get_field(x509crt.obj(), NID_organizationName),
            "OpenVPN-TEST");

  ASSERT_EQ(OpenSSLPKI::x509_get_field(x509crt.obj(), NID_countryName), "US");
}

TEST(OpenSSL_X509_get_field, signature) {
    OpenSSLPKI::X509 x509crt(test_cert, "Embedded Test Server Cert");

    ASSERT_EQ(OpenSSLPKI::x509_get_signature_algorithm(x509crt.obj()), "RSA-SHA256");
}

TEST(x509_get_fingerprint, output) {
  OpenSSLPKI::X509 x509crt(test_cert, "Embedded Test Server Cert");
  const std::vector<uint8_t> fingerprint = {
    0x44, 0xF5, 0xA6, 0x4D, 0x4A, 0xCB, 0x65, 0xE1,
    0x8A, 0x9F, 0x55, 0x89, 0x7F, 0x77, 0xA0, 0x79,
    0xAA, 0xFB, 0xCC, 0xA1, 0x37, 0x2F, 0xD8, 0xB3,
    0x47, 0xAA, 0x9D, 0xE3, 0xD0, 0x76, 0xB1, 0x44
  };
  ASSERT_EQ(OpenSSLPKI::x509_get_fingerprint(x509crt.obj()), fingerprint);
}


}  // namespace unittests
