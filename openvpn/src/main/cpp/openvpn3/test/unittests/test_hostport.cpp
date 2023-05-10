#include "test_common.h"

#include <openvpn/common/hostport.hpp>

using namespace openvpn;

static void test(const std::string &str,
                 const std::string &default_port,
                 const bool allow_unix,
                 const std::string &expected_host,
                 const std::string &expected_port,
                 const unsigned int expected_portnum,
                 const bool expected_status)
{
    std::string host;
    std::string port;
    unsigned int port_save = 0;

    const bool status = HostPort::split_host_port(str, host, port, default_port, allow_unix, &port_save);
    if (expected_status)
    {
        if (!status)
            OPENVPN_THROW_EXCEPTION("failed to parse " << str << " default_port=" << default_port << " allow_unix=" << allow_unix);
        if (expected_host != host)
            OPENVPN_THROW_EXCEPTION("inconsistent host " << str << " expected=" << expected_host << " actual=" << host);
        if (expected_port != port)
            OPENVPN_THROW_EXCEPTION("inconsistent port " << str << " expected=" << expected_port << " actual-" << port);
        if (expected_portnum != port_save)
            OPENVPN_THROW_EXCEPTION("inconsistent portnum " << str << " expected=" << expected_portnum << " actual=" << port_save);
    }
    else
    {
        if (status)
            OPENVPN_THROW_EXCEPTION("parse should have failed: " << str);
    }
}

TEST(misc, host_port)
{
    test("foo.bar.gov", "", false, "", "", 0, false);
    test("foo.bar.gov", "1234", false, "foo.bar.gov", "1234", 1234, true);
    test("foo.bar.gov:5678", "1234", false, "foo.bar.gov", "5678", 5678, true);
    test("foo.bar.gov:5678", "", false, "foo.bar.gov", "5678", 5678, true);
    test("[foo.bar.gov]:5678", "555555", false, "foo.bar.gov", "5678", 5678, true);
    test("[foo.bar.gov]", "1234", false, "foo.bar.gov", "1234", 1234, true);
    test("[foo.bar.gov]", "123456", false, "", "", 0, false);
    test("1.2.3.4:5678", "", false, "1.2.3.4", "5678", 5678, true);
    test("[1.2.3.4]:5678", "", false, "1.2.3.4", "5678", 5678, true);
    test("[1.2.3.4]", "5678", false, "1.2.3.4", "5678", 5678, true);
    test("[::0]", "9999", false, "::0", "9999", 9999, true);
    test("[::0]", "", false, "", "", 0, false);
    test("[::0]:9999", "", false, "::0", "9999", 9999, true);
    test("", "", false, "", "", 0, false);
    test(":", "", false, "", "", 0, false);
    test("x:", "", false, "", "", 0, false);
    test(":4", "", false, "", "", 0, false);
    test("[]:1234", "", false, "", "", 0, false);
    test("[fe80::1443:76ff:fe2e:1479]", "4040", false, "fe80::1443:76ff:fe2e:1479", "4040", 4040, true);
    test("[fe80::1443:76ff:fe2e:147a]:8080", "4040", false, "fe80::1443:76ff:fe2e:147a", "8080", 8080, true);
    test("fe80::1443:76ff:fe2e:1477", "4040", false, "fe80::1443:76ff:fe2e:1477", "4040", 4040, true);
    test("[foo]", "", false, "", "", 0, false);
    test("[", "", false, "", "", 0, false);
    test("]", "", false, "", "", 0, false);

    test("/foo/bar", "unix", false, "", "", 0, false);
    test("/foo/bar", "unix", true, "/foo/bar", "unix", 0, true);
    test("/foo/bar:unix", "", true, "/foo/bar", "unix", 0, true);
}
