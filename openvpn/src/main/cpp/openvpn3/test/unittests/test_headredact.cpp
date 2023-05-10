#include "test_common.h"

// #define OPENVPN_HTTP_HEADERS_NO_REDACT

#include <openvpn/http/headredact.hpp>

using namespace openvpn;

static const std::string in1 = "HEADERS: POST /pg HTTP/1.1\r\n"
                               "         Host: 3.91.106.178\r\n"
                               "         User-Agent: japicli\r\n"
                               "         Authorization: Basic cGc6cHJqN1hKQUpuRkRsZ2V5MXZLaVlVcGhL\r\n"
                               "         Content-Type: application/json\r\n"
                               "         Content-Length: 49\r\n"
                               "         Accept-Encoding: lz4\r\n"
                               "         Accept: */*\r\n"
                               "         \r\n";

static const std::string out1 = "HEADERS: POST /pg HTTP/1.1\r\n"
                                "         Host: 3.91.106.178\r\n"
                                "         User-Agent: japicli\r\n"
                                "         Authorization: Basic [REDACTED]\r\n"
                                "         Content-Type: application/json\r\n"
                                "         Content-Length: 49\r\n"
                                "         Accept-Encoding: lz4\r\n"
                                "         Accept: */*\r\n"
                                "         \r\n";

static const std::string in2 = "HEADERS: POST /pg HTTP/1.1\r\n"
                               "         Host: 3.91.106.178\r\n"
                               "         User-Agent: japicli\r\n"
                               "         authorization=basic cGc6cHJqN1hKQUpuRkRsZ2V5MXZLaVlVcGhL\r\n"
                               "         Content-Type: application/json\r\n"
                               "         Content-Length: 49\r\n"
                               "         Accept-Encoding: lz4\r\n"
                               "         Accept: */*\r\n"
                               "         \r\n";

static const std::string out2 = "HEADERS: POST /pg HTTP/1.1\r\n"
                                "         Host: 3.91.106.178\r\n"
                                "         User-Agent: japicli\r\n"
                                "         authorization=basic [REDACTED]\r\n"
                                "         Content-Type: application/json\r\n"
                                "         Content-Length: 49\r\n"
                                "         Accept-Encoding: lz4\r\n"
                                "         Accept: */*\r\n"
                                "         \r\n";

TEST(http, headredact1)
{
    const std::string out = HTTP::headers_redact(in1);
    ASSERT_EQ(out, out1);
}

TEST(http, headredact2)
{
    const std::string out = HTTP::headers_redact(in2);
    ASSERT_EQ(out, out2);
}
