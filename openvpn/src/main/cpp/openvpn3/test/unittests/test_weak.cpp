// test weak smart pointers by having a vector of
// reference objects (Ref/RefType) that weakly point back
// to their parent object (Object).

#include "test_common.h"

#include <vector>

#include <openvpn/common/rc.hpp>

using namespace openvpn;

class StaticCounter
{
  public:
    StaticCounter()
    {
        ++count_;
    }

    ~StaticCounter()
    {
        --count_;
    }

    static int count()
    {
        return count_;
    }

  private:
    static int count_;
};

int StaticCounter::count_ = 0;

// Strategy A -- RefType declared before Object, so use class Template
// so that RefType can be specialized for Object
namespace A {

template <typename PARENT>
class RefType : public RCWeak<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<RefType> Ptr;
    typedef RCWeakPtr<RefType> WPtr;

    RefType(std::string name,
            typename PARENT::Ptr parent)
        : name_(std::move(name)),
          parent_(std::move(parent))
    {
    }

    typename PARENT::Ptr parent() const
    {
        return parent_.lock();
    }

    std::string to_string() const
    {
        auto p = parent();
        if (p)
            return "I am " + name() + " whose parent is " + p->name();
        else
            return "I am " + name() + ", an orphan";
    }

    std::string name() const
    {
        return name_;
    }

  private:
    std::string name_;
    typename PARENT::WPtr parent_;
    StaticCounter sc_;
};

class Object : public RCWeak<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<Object> Ptr;
    typedef RCWeakPtr<Object> WPtr;
    typedef RefType<Object> Ref;

    std::string name() const
    {
        return "Regular Joe";
    }

    std::string to_string() const
    {
        std::string ret;
        for (const auto &e : vec)
        {
            ret += e->to_string();
            ret += '\n';
        }
        return ret;
    }

    std::vector<Ref::Ptr> vec;

  private:
    StaticCounter sc_;
};

typedef Object::Ref Ref;
} // namespace A

// Strategy B -- Ref declared inside Object, so Ref can make use
// of existing Object types (such as Ptr and WPtr)
namespace B {

class Object : public RCWeak<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<Object> Ptr;
    typedef RCWeakPtr<Object> WPtr;

    class Ref : public RCWeak<thread_unsafe_refcount>
    {
      public:
        typedef RCPtr<Ref> Ptr;
        typedef RCWeakPtr<Ref> WPtr;

        Ref(std::string name,
            typename Object::Ptr parent)
            : name_(std::move(name)),
              parent_(std::move(parent))
        {
        }

        Object::Ptr parent() const
        {
            return parent_.lock();
        }

        std::string to_string() const
        {
            auto p = parent();
            if (p)
                return "I am " + name() + " whose parent is " + p->name();
            else
                return "I am " + name() + ", an orphan";
        }

        std::string name() const
        {
            return name_;
        }

      private:
        std::string name_;
        Object::WPtr parent_;
        StaticCounter sc_;
    };

    std::string name() const
    {
        return "Regular Joe";
    }

    std::string to_string() const
    {
        std::string ret;
        for (const auto &e : vec)
        {
            ret += e->to_string();
            ret += '\n';
        }
        return ret;
    }

    std::vector<Ref::Ptr> vec;

  private:
    StaticCounter sc_;
};

typedef Object::Ref Ref;
} // namespace B

template <typename Object, typename Ref>
void test()
{
    ASSERT_EQ(StaticCounter::count(), 0);

    std::string result;

    // create new Ref objects that point back to their parent (Object)
    typename Object::Ptr obj(new Object);
    obj->vec.emplace_back(new Ref("One", obj));
    obj->vec.emplace_back(new Ref("Two", obj));
    obj->vec.emplace_back(new Ref("Three", obj));

    // verify obj
    ASSERT_EQ(obj->vec.size(), 3u);
    ASSERT_EQ(obj->vec.at(0)->to_string(), "I am One whose parent is Regular Joe");
    ASSERT_EQ(obj->vec.at(1)->to_string(), "I am Two whose parent is Regular Joe");
    ASSERT_EQ(obj->vec.at(2)->to_string(), "I am Three whose parent is Regular Joe");

    // make One into orphan
    typename Ref::Ptr the_one = obj->vec[0]; // get One
    obj.reset();                             // free parent
    ASSERT_EQ(the_one->to_string(), "I am One, an orphan");

    // verify no memory leaks
    ASSERT_EQ(StaticCounter::count(), 1);
    the_one.reset();
    ASSERT_EQ(StaticCounter::count(), 0);
}

// strategy A
TEST(misc, weak_a)
{
    test<A::Object, A::Ref>();
}

// strategy B
TEST(misc, weak_b)
{
    test<B::Object, B::Ref>();
}
