#include "test_common.h"

#include <openvpn/common/ostream_containers.hpp>
#include <list>
#include <complex>
#include <deque>

using namespace openvpn;

// simple use case example; cases will mostly look like this
TEST(ostream_container, simple_vector_int)
{
    std::vector<int> vi{2, 4, 6, 8};
    std::ostringstream oss;

    oss << C2os::cast(vi);

    ASSERT_EQ(oss.str(), std::string("[2, 4, 6, 8]"));
}

// TestItem and generic_test() allow short implementation of tests for arbitrary
// conforming containers that hold arbitrary conforming types
template <typename Container>
struct TestItem
{
    TestItem(const Container &&c, const std::string &&s)
        : container(c), expected(s)
    {
    }

    const Container container;
    const std::string expected;
};

template <typename Tests>
void generic_test(const Tests &tests)
{
    for (auto &test : tests)
    {
        std::ostringstream oss;

        oss << C2os::cast(test.container);

        EXPECT_EQ(oss.str(), test.expected);
    }
}

// tests for int/set, string/list, complex/vector, and custom/deque
using ssi = std::set<int>;
const TestItem<ssi> set_int_tests[] = {
    TestItem<ssi>({3, 5, 7, 11}, "[3, 5, 7, 11]"),
    TestItem<ssi>({-3, 5, -7, 0}, "[-7, -3, 0, 5]"),
    TestItem<ssi>({}, "[]"),
};

TEST(ostream_container, set_int)
{
    generic_test(set_int_tests);
}

using sls = std::list<std::string>;
const TestItem<sls> list_string_tests[] = {
    TestItem<sls>({"Alfred", "E.", "Neuman"}, "[Alfred, E., Neuman]"),
    TestItem<sls>({"Institute has", "the finest", "professors"}, "[Institute has, the finest, professors]"),
};

TEST(ostream_container, list_string)
{
    generic_test(list_string_tests);
}

using svc = std::vector<std::complex<double>>;
const TestItem<svc> vector_complex_tests[] = {
    TestItem<svc>({{1.5, 2.0}, {6.4, 7.2}, {8.9, 0.4}}, "[(1.5,2), (6.4,7.2), (8.9,0.4)]"),
    TestItem<svc>({{-5, 0}, {18, 500}, {1e6, 32}}, "[(-5,0), (18,500), (1e+06,32)]"),
};

TEST(ostream_container, vector_complex)
{
    generic_test(vector_complex_tests);
}

struct MyComplex : public std::complex<double>
{
    MyComplex(double re, double im)
        : std::complex<double>(re, im)
    {
    }
};

std::ostream &operator<<(std::ostream &os, const MyComplex &mc)
{
    os << mc.real() << "+i" << mc.imag();
    return os;
}

using sdm = std::deque<MyComplex>;
const TestItem<sdm> deque_custom_tests[] = {
    TestItem<sdm>({{1.5, 2.0}, {6.4, 7.2}, {8.9, 0.4}}, "[1.5+i2, 6.4+i7.2, 8.9+i0.4]"),
    TestItem<sdm>({{-5, 0}, {18, 500}, {1e6, 32}}, "[-5+i0, 18+i500, 1e+06+i32]"),
};

TEST(ostream_container, deque_custom)
{
    generic_test(deque_custom_tests);
}
// end: tests for int/set, string/list, complex/vector, and custom/deque
