// This test demonstrates an alternative to dynamic_cast
// using std::typeindex that is much faster.

#include "test_common.h"

#include <typeindex>
#include <vector>
#include <utility>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/common/stringtempl2.hpp>

using namespace openvpn;

namespace {
struct Base : public RC<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<Base> Ptr;

    virtual std::string to_string() const = 0;

    std::type_index type() const
    {
        return type_;
    }

  protected:
    Base(std::type_index &&type)
        : type_(std::move(type))
    {
    }

  private:
    const std::type_index type_;
};

template <typename T>
class Wrapper : public Base
{
  public:
    Wrapper(T &&obj_arg)
        : Base(static_type()),
          obj(std::move(obj_arg))
    {
    }

    // this is like dynamic cast on steroids! (~1 ns)
    static const Wrapper *self(const Base *maybe_my_type)
    {
        if (maybe_my_type->type() == static_type())
            return static_cast<const Wrapper *>(maybe_my_type);
        else
            return nullptr;
    }

    virtual std::string to_string() const override
    {
        return "value=" + StringTempl::to_string(obj) + " obj_size=" + std::to_string(sizeof(obj));
    }

    static std::type_index static_type()
    {
        return std::type_index(typeid(Wrapper));
    }

    T obj;
};

struct Vec : public std::vector<Base::Ptr>
{
    std::string to_string() const
    {
        std::string ret;
        for (const auto &e : *this)
        {
            ret += e->to_string();
            ret += '\n';
        }
        return ret;
    }
};

template <typename T>
static Base::Ptr create(T &&obj)
{
    return Base::Ptr(new Wrapper<T>(std::move(obj)));
}

static Vec create_vec()
{
    Vec vec;
    vec.emplace_back(create(1));
    vec.emplace_back(create(2));
    vec.emplace_back(create(3.14159));
    vec.emplace_back(create(std::string("Hello")));
    vec.emplace_back(create(std::string("World!")));
    vec.emplace_back(create(true));
    vec.emplace_back(create(false));
    return vec;
}
} // namespace

// simple test of self()
TEST(typeindex, test)
{
    typedef Wrapper<std::string> StringWrap;

    const Vec vec = create_vec();

    OPENVPN_LOG("CONTENTS...");
    OPENVPN_LOG_STRING(vec.to_string());

    OPENVPN_LOG("STRINGS...");
    for (const auto &e : vec)
    {
        const StringWrap *s = StringWrap::self(e.get());
        if (s)
            OPENVPN_LOG(s->obj);
    }
}

#ifndef HAVE_VALGRIND

// test performance of self() as alternative to dynamic_cast
TEST(typeindex, perf_test_fast)
{
    typedef Wrapper<std::string> StringWrap;

    const size_t N = 100000000;

    const Vec vec = create_vec();
    const size_t size = vec.size();
    size_t n_strings = 0;
    size_t i = 0;
    for (size_t count = 0; count < N; ++count)
    {
        const StringWrap *s = StringWrap::self(vec[i].get());
        if (s)
            ++n_strings;
        if (++i >= size)
            i = 0;
    }
    OPENVPN_LOG("PERF " << n_strings << '/' << N);
    ASSERT_EQ(n_strings, 28571428u);
}

// as a control, test performance of dynamic_cast
TEST(typeindex, perf_test_dynamic)
{
    typedef Wrapper<std::string> StringWrap;

    const size_t N = 100000000;

    const Vec vec = create_vec();
    const size_t size = vec.size();
    size_t n_strings = 0;
    size_t i = 0;
    for (size_t count = 0; count < N; ++count)
    {
        const StringWrap *s = dynamic_cast<const StringWrap *>(vec[i].get());
        if (s)
            ++n_strings;
        if (++i >= size)
            i = 0;
    }
    OPENVPN_LOG("PERF " << n_strings << '/' << N);
    ASSERT_EQ(n_strings, 28571428u);
}

#endif
