#include <iostream>
#include "test_common.hpp"

#include <openvpn/random/randapi.hpp>

using namespace openvpn;

template <typename IntegralT>
class IntegralMin : public WeakRandomAPI
{
  public:
    OPENVPN_EXCEPTION(s_min_error);

    typedef RCPtr<IntegralMin> Ptr;

    // Random algorithm name
    std::string name() const override
    {
        return "IntegralMin";
    }

    // Fill buffer with minimum value
    void rand_bytes(unsigned char *buf, size_t size) override
    {
        if (!rand_bytes_noexcept(buf, size))
            throw s_min_error("rand_bytes failed");
    }

    // Like rand_bytes, but don't throw exception.
    // Return true on successs, false on fail.
    bool rand_bytes_noexcept(unsigned char *buf, size_t size) override
    {
        if (size < sizeof(IntegralT))
            return false;
        IntegralT *int_ptr = reinterpret_cast<IntegralT *>(buf);
        *int_ptr = std::numeric_limits<IntegralT>::min();
        return true;
    }

    IntegralT get_result()
    {
        return rand_get_positive<IntegralT>();
    }
};

template <typename IntegralT>
void randapi_signed_min_test(const std::string &test_name)
{
    IntegralMin<IntegralT> s_min;

    IntegralT result = s_min.get_result();

    EXPECT_EQ(result, 0) << "fails for \"" << test_name << "\" test";
}

#define RANDAPI_SIGNED_MIN_TEST(test)         \
    do                                        \
    {                                         \
        randapi_signed_min_test<test>(#test); \
    } while (0)


TEST(misc, randapi_signed_min)
{
    RANDAPI_SIGNED_MIN_TEST(signed char);
    RANDAPI_SIGNED_MIN_TEST(unsigned char);
    RANDAPI_SIGNED_MIN_TEST(int32_t);
    RANDAPI_SIGNED_MIN_TEST(uint32_t);
    RANDAPI_SIGNED_MIN_TEST(int64_t);
    RANDAPI_SIGNED_MIN_TEST(uint64_t);
}
