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
#include "openvpn/mbedtls/pki/x509cert.hpp"
#include "openvpn/mbedtls/pki/x509certinfo.hpp"

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

TEST(mbedTLS_x509_get_subject, old_format) {
  MbedTLSPKI::X509Cert x509crt(test_cert, "Embedded Test Server Cert", true);
  std::string expect(
      "/C=US/ST=CA/O=OpenVPN-TEST/CN=server-1.example.net/"
      "emailAddress=example-server@example.net");

  ASSERT_EQ(MbedTLSPKI::x509_get_subject(x509crt.get()), expect);
  ASSERT_EQ(MbedTLSPKI::x509_get_subject(x509crt.get(), false), expect);
}

TEST(mbesTLS_x509_get_subject, new_format) {
  MbedTLSPKI::X509Cert x509crt(test_cert, "Embedded Test Server Cert", true);
  std::string expect(
      "C=US, ST=CA, O=OpenVPN-TEST, CN=server-1.example.net, "
      "emailAddress=example-server@example.net");

  ASSERT_EQ(MbedTLSPKI::x509_get_subject(x509crt.get(), true), expect);
}

TEST(mbedTLS_x509_get_common_name, basic_check) {
  MbedTLSPKI::X509Cert x509crt(test_cert, "Embedded Test Server Cert", true);

  ASSERT_EQ(MbedTLSPKI::x509_get_common_name(x509crt.get()),
            "server-1.example.net");
}

}  // namespace unittests
