#include "test_common.h"

#include <iostream>
#include <string>
#include <functional>
#include <openvpn/common/rc.hpp>

using namespace openvpn;

template <typename BASE>
class TestType : public BASE
{
  public:
    typedef RCPtr<TestType> Ptr;
    typedef RCWeakPtr<TestType> WPtr;
    typedef BASE Base;

    TestType(const std::string &name_arg)
        : name(name_arg)
    {
        OPENVPN_LOG(name << "()");
    }

    ~TestType()
    {
        OPENVPN_LOG("~" << name << "()");
    }

    void go(const char *title)
    {
        OPENVPN_LOG(title << ": " << name);
    }

    std::string name;
};

template <typename Base>
class TestParentType : public TestType<Base>
{
  public:
    typedef RCPtr<TestParentType> Ptr;
    typedef RCWeakPtr<TestParentType> WPtr;

    TestParentType(const std::string &name_arg)
        : TestType<Base>(name_arg)
    {
        parent_name = std::string("parent of ") + TestType<Base>::name;
    }

    std::string parent_name;
};

const char *expected_output = "*** TEST1\n"
                              "Test1()\n"
                              "~Test1()\n"
                              "*** TEST2\n"
                              "Test2()\n"
                              "t1a: Test2\n"
                              "t2a: Test2\n"
                              "t1b: Test2\n"
                              "t2b: Test2\n"
                              "tz: Test2\n"
                              "w1z=3 w2z=3\n"
                              "~Test2()\n"
                              "*** TEST3\n"
                              "Test3()\n"
                              "N#3: Test3\n"
                              "NOTIFY #3\n"
                              "N#2: Test3\n"
                              "NOTIFY #2\n"
                              "N#1: Test3\n"
                              "NOTIFY #1\n"
                              "~Test3()\n"
                              "*** TEST4\n"
                              "Test4()\n"
                              "parent of Test4\n"
                              "~Test4()\n";

template <typename Test>
void test()
{
    testLog->startCollecting();
    typedef TestParentType<typename Test::Base> TestParent;

    {
        OPENVPN_LOG("*** TEST1");
        typename Test::Ptr t1 = new Test("Test1");
        typename Test::Ptr t2(t1);
        typename Test::Ptr t3(t2);
    }
    {
        OPENVPN_LOG("*** TEST2");

        typename Test::WPtr w1z;
        typename Test::WPtr w2z;

        {
            typename Test::Ptr t1 = new Test("Test2");
            typename Test::WPtr w1 = t1;
            RCWeakPtr<typename Test::WPtr::element_type> w2 = t1.get();
            w1z.reset(t1);
            w2z.reset(t1.get());

            typename Test::Ptr t1a = w1.lock();
            typename Test::Ptr t2a = w2.lock();

            t1a->go("t1a");
            t2a->go("t2a");

            t1a = w1z.lock();
            t2a = w2z.lock();

            t1a->go("t1b");
            t2a->go("t2b");

            typename Test::WPtr z;
            z.swap(w1);
            typename Test::Ptr tz = z.lock();
            tz->go("tz");

            tz = w1.lock();
            ASSERT_FALSE(tz) << "BUG ALERT #1";

            z.reset();
            tz = z.lock();
            ASSERT_FALSE(tz) << "BUG ALERT #2";

            OPENVPN_LOG("w1z=" << w1z.use_count() << " w2z=" << w2z.use_count());
        }

        typename Test::Ptr x = w1z.lock();
        typename Test::Ptr y = w2z.lock();
        ASSERT_FALSE(x || y || !w1z.expired() || !w2z.expired()) << "BUG ALERT #3";
        w1z = w2z;
    }
    {
        OPENVPN_LOG("*** TEST3");
        typename Test::Ptr t1 = new Test("Test3");
        typename Test::Ptr t2(t1);
        typename Test::Ptr t3(t2);

        t1->rc_release_notify([obj = t1.get()]()
                              {
	obj->go("N#1");
	OPENVPN_LOG("NOTIFY #1"); });
        t2->rc_release_notify([obj = t2.get()]()
                              {
	obj->go("N#2");
	OPENVPN_LOG("NOTIFY #2"); });
        t3->rc_release_notify([obj = t3.get()]()
                              {
	obj->go("N#3");
	OPENVPN_LOG("NOTIFY #3"); });
    }
    {
        OPENVPN_LOG("*** TEST4");
        typename TestParent::Ptr t1 = new TestParent("Test4");
        typename Test::Ptr t2(t1);
        typename TestParent::Ptr t3 = t2.template dynamic_pointer_cast<TestParent>();
        OPENVPN_LOG(t3->parent_name);
    }
    ASSERT_EQ(expected_output, testLog->stopCollecting());
}

TEST(misc, RCthreadUnsafe)
{
    test<TestType<RCWeak<thread_unsafe_refcount>>>();
}

TEST(misc, RCthreadSafe)
{
    test<TestType<RCWeak<thread_safe_refcount>>>();
}
