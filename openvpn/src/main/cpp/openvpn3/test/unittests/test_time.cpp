#include "test_common.h"

#include <openvpn/time/time.hpp>
#include <openvpn/random/mtrandapi.hpp>

#include <openvpn/time/skew.hpp>
#include <openvpn/common/format.hpp>

using namespace openvpn;
using namespace openvpn;

int my_abs(const int value)
{
    if (value >= 0)
        return value;
    else
        return -value;
}

class Mean
{
  public:
    void add(const int value)
    {
        sum_ += value;
        ++count_;
    }

    int mean() const
    {
        return sum_ / count_;
    }

    void check_mean_range(const std::string &title, const int low, const int hi) const
    {
        const int m = mean();
        ASSERT_TRUE(m > low && m < hi) << title << ' ' << to_string() << " outside of range=(" << low << ',' << hi << ')';
    }

    int count() const
    {
        return count_;
    }

    std::string to_string() const
    {
        return printfmt("[mean=%s count=%s]", mean(), count());
    }

  private:
    int count_ = 0;
    int sum_ = 0;
};

struct MeanDev
{
    Mean mean;
    Mean dev;

    std::string to_string() const
    {
        return mean.to_string() + " dev=" + dev.to_string();
    }
};

void test_skew(const Time::Duration &dur,
               const unsigned int skew_factor,
               MeanDev &md,
               const bool verbose,
               RandomAPI &prng)
{
    const Time::Duration after = TimeSkew::skew(dur, skew_factor, prng);
    md.mean.add(after.to_binary_ms());
    md.dev.add(my_abs(int(dur.to_binary_ms()) - int(after.to_binary_ms())));
    if (verbose)
        OPENVPN_LOG("BEFORE=" << dur.to_binary_ms() << " AFTER=" << after.to_binary_ms());
}

TEST(time, timeskew)
{
    MTRand::Ptr prng(new MTRand());
    MeanDev md;
    for (int i = 0; i < 10000; ++i)
    {
        test_skew(Time::Duration::seconds(10), TimeSkew::PCT_25, md, false, *prng);
    }
    // OPENVPN_LOG(md.to_string());
    md.mean.check_mean_range("mean", 10100, 10300);
    md.dev.check_mean_range("dev", 1250, 1350);
}

TEST(time, test1)
{
    Time::reset_base();

    const Time until = Time::now() + Time::Duration::seconds(1);

    Time::base_type last_sec = 0;
    Time::type last_frac = 0;

    while (true)
    {
        const Time t = Time::now();
        if (t >= until)
            break;
        const Time::base_type sec = t.seconds_since_epoch();
        const Time::type frac = t.fractional_binary_ms();
        if (sec != last_sec || frac != last_frac)
        {
            // std::cout << sec << ' ' << frac << std::endl;
            last_sec = sec;
            last_frac = frac;
        }
    }
}

static void sub(const Time &t1, const Time &t2, bool large)
{
    const Time::Duration d = t1 - t2;
    // std::cout << "T-T " << t1.raw() << " - " << t2.raw() << " = " << d.raw() << std::endl;
    if (large)
        ASSERT_GE(d.raw(), 100000u);
    else
        ASSERT_EQ(d.raw(), 0u);
}

static void sub(const Time::Duration &d1, const Time::Duration &d2)
{
    const Time::Duration d = d1 - d2;
    // std::cout << "D-D " << d1.raw() << " - " << d2.raw() << " = " << d.raw() << std::endl;
    Time::Duration x = d1;
    x -= d2;
    ASSERT_EQ(x, d) << "D-D INCONSISTENCY DETECTED";
}

static void add(const Time &t1, const Time::Duration &d1)
{
    const Time t = t1 + d1;
    // std::cout << "T+D " << t1.raw() << " + " << d1.raw() << " = " << t.raw() << std::endl;
    Time x = t1;
    x += d1;
    ASSERT_EQ(x, t) << "T+D INCONSISTENCY DETECTED";
}

static void add(const Time::Duration &d1, const Time::Duration &d2)
{
    const Time::Duration d = d1 + d2;
    // std::cout << "D+D " << d1.raw() << " + " << d2.raw() << " = " << d.raw() << std::endl;
    Time::Duration x = d1;
    x += d2;
    ASSERT_EQ(x, d) << "D+D INCONSISTENCY DETECTED";
}

TEST(time, timeaddsub)
{
    {
        const Time now = Time::now();
        const Time inf = Time::infinite();
        sub(now, now, false);
        sub(inf, now, true);
        sub(now, inf, false);
        sub(inf, inf, false);
    }
    {
        const Time::Duration sec = Time::Duration::seconds(1);
        const Time::Duration inf = Time::Duration::infinite();
        sub(sec, sec);
        sub(inf, sec);
        sub(sec, inf);
        sub(inf, inf);
    }
    {
        const Time tf = Time::now();
        const Time ti = Time::infinite();
        const Time::Duration df = Time::Duration::seconds(1);
        const Time::Duration di = Time::Duration::infinite();
        add(tf, df);
        add(tf, di);
        add(ti, df);
        add(ti, di);
    }
    {
        const Time::Duration sec = Time::Duration::seconds(1);
        const Time::Duration inf = Time::Duration::infinite();
        add(sec, sec);
        add(inf, sec);
        add(sec, inf);
        add(inf, inf);
    }
}
