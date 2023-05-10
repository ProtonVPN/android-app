#include "test_common.h"
#include <iostream>


#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>

#include <openvpn/common/format.hpp>
#include <openvpn/common/ostream.hpp>

using namespace openvpn;

class MyObj
{
  public:
    MyObj(int v)
        : value(v)
    {
    }

    std::string to_string() const
    {
        return std::to_string(value);
    }

  private:
    int value;
};

OPENVPN_OSTREAM(MyObj, to_string);

const std::string expected = "7\n"
                             "foo\n"
                             "bar\n"
                             "3.141593\n"
                             "3\n"
                             "1\n"
                             "0\n"
                             "pi is not 3 nor is it 7 ; it is 3.14159 ...\n"
                             "pi is 'not' 3 nor is it 7 ; it is 3.141593... (and has 99% less fat!)\n"
                             "the year is 2015 and the weather is \"partly cloudy\"\n"
                             "where am I? is it still 2015?\n"
                             "no, it's 1666... bring out yer dedd?\n"
                             "save 20%!\n"
                             "no wait... save? 99.9999%!\n"
                             "extra argument is here\n"
                             "is the question true or false?\n"
                             "more extra arguments are here\n"
                             "null string ''\n"
                             "nullptr 'nullptr'\n"
                             "foo=bar non const\n"
                             "EX1: bad foo\n"
                             "EX2: this prog is done 4 U\n";

TEST(misc, format)
{
    std::ostringstream os;

    const MyObj seven(7);
    const std::string foo = "foo";
    const char *const bar = "bar";
    const double pi = 3.14159265;
    const int three = 3;
    const std::string weather = "partly cloudy";
    char *nc = const_cast<char *>("non const");

    os << to_string(seven) << std::endl;
    os << to_string(foo) << std::endl;
    os << to_string(bar) << std::endl;
    os << to_string(pi) << std::endl;
    os << to_string(three) << std::endl;
    os << to_string(true) << std::endl;
    os << to_string(false) << std::endl;
    os << prints("pi", "is", std::string("not"), 3, "nor is it", seven, ';', "it", "is", pi, "...") << std::endl;
    os << printfmt("pi is %r %s nor is it %s ; it is %s... (and has %s%% less %s!)", "not", 3, seven, pi, 99, std::string("fat")) << std::endl;
    os << printfmt("the year is %s and the weather is %R", 2015, weather) << std::endl;
    os << printfmt("where am %s? is it still %s?", 'I', 2015) << std::endl;
    os << printfmt("no, it's %s... bring out yer dedd%s", 1666) << std::endl;
    os << printfmt("save 20%%!") << std::endl;
    os << printfmt("no wait... save%s 99.9999%%!") << std::endl;
    os << printfmt("extra argument is here", 1) << std::endl;
    os << printfmt("is the question %s or %s?", true, false) << std::endl;
    os << printfmt("more extra arguments are here", 1, 2, 3, 4) << std::endl;
    os << printfmt("null string '%s'", static_cast<const char *>(nullptr)) << std::endl;
    os << printfmt("nullptr '%s'", nullptr) << std::endl;
    os << printfmt("%s=%s %s", foo, bar, nc) << std::endl;
    try
    {
        const std::string exstr = "bad foo";
        throw Exception(exstr);
    }
    catch (const std::exception &e)
    {
        os << prints("EX1:", e.what()) << std::endl;
    }
    try
    {
        throw Exception(prints("this", "prog", "is", "done", 4, 'U'));
    }
    catch (const std::exception &e)
    {
        os << prints("EX2:", e.what()) << std::endl;
    }
    const std::string actual = os.str();
    ASSERT_EQ(expected, actual);
}

template <typename... Args>
inline std::string pfmt(const std::string &fmt, Args... args)
{
#if 1
    PrintFormatted<std::string> pf(fmt, 256);
#else
    PrintFormatted<std::ostringstream> pf(fmt, 256);
#endif
    pf.process(args...);
    return pf.str();
}

void perf()
{
    const MyObj seven(7);
    // const double pi = 3.14159265;
    long count = 0;
    const std::string weather = "partly cloudy";
    for (long i = 0; i < 1000000; ++i)
    {
        const std::string str = pfmt("the year is %s and the weather is %r", 2015, weather);
        // const std::string str = pfmt("this program is brought to you by the number %s", seven);
        // const std::string str = pfmt("foo %s", 69);
        // const std::string str = pfmt("foo");
        // const std::string str = pfmt("foo %s %s", 69, "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
        // const std::string str = pfmt("pi is %s %s nor is it %s ; it is %s... (and has %s%% less %s!)", "not", 3, seven, pi, 99, std::string("fat"));
        count += str.length();
    }
    std::cout << count << std::endl;
}