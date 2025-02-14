//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012- OpenVPN Inc.
//
//    SPDX-License-Identifier: MPL-2.0 OR AGPL-3.0-only WITH openvpn3-openssl-exception
//

// A basic reference-counting garbage collection scheme based
// on intrusive pointers, where the reference count is embedded in
// the object via inheritance.  Simply inherit from RC to create an
// object that can be tracked with an RCPtr.
//
// We use tend to use RCPtr (or RCWeakPtr) rather than the other
// smart pointer classes (std or boost) for flexibility and
// performance.
//
// Smart pointers have two basic attributes that determine
// their performance.  Either of these attributes, when required,
// will degrade the performance of the smart pointer:
//
// 1. whether the smart pointer is thread-safe, i.e. it uses an
//    atomic reference counter
// 2. whether the smart pointer can be referrred to via a
//    weak reference
//
// In keeping with the oft-stated C++ motto of only paying for
// what you use, both attributes can be independently controlled.
//
// * thread-unsafe/not-weak-referenceable -- class Foo : public RC<thread_unsafe_refcount>
// * thread-safe/not-weak-referenceable   -- class Foo : public RC<thread_safe_refcount>
// * thread-unsafe/weak-referenceable     -- class Foo : public RCWeak<thread_unsafe_refcount>
// * thread-safe/weak-referenceable       -- class Foo : public RCWeak<thread_safe_refcount>
//
// Thread-safe reference counting can be significantly more expensive
// because an atomic object must be used for the reference count.
// Therefore, thread-safe reference counting should only be used for
// objects that have visibility across multiple threads.
//
// In addition, having an object be weak-referenceable also
// imposes a cost, so it should be avoided unless necessary.
//
// For clarity and as a general convention in the OpenVPN code,
// any object that inherits from RC should also declare a Ptr
// typedef that defines the smart pointer type that should be used to
// track the object, e.g.:
//
// class Foo : public RC<thread_unsafe_refcount> {
// public:
//   typedef RCPtr<Foo> Ptr;        // strong pointer
//   typedef RCWeakPtr<Foo> WPtr;   // weak pointer
// };
//
// This allows a smart-pointer to Foo to be referred to
// as Foo::Ptr or Foo::WPtr.
//
// Note that RC/RCWeak fully supports virtual inheritance.  For
// example, consider the diamond inheritance pattern below, where
// both A and B objects contain their own reference count, but C
// inherits from both A and B.  To prevent C objects from
// having two separate reference counts, it is necessary to
// virtually inherit from RC.
//
// class A : public virtual RC<thread_unsafe_refcount> {}
// class B : public virtual RC<thread_unsafe_refcount> {}
// class C : public A, public B {}

#ifndef OPENVPN_COMMON_RC_H
#define OPENVPN_COMMON_RC_H

#include <atomic>
#include <utility>

#include <openvpn/common/olong.hpp>

#ifdef OPENVPN_RC_DEBUG
#include <iostream>
#include <openvpn/common/demangle.hpp>
#endif

namespace openvpn {

/**
  @brief The smart pointer class
  @tparam T an RC enabled type

  Defines a template class called RCPtr that implements a smart pointer for
  reference counted objects.

  RCPtr is a template class, meaning it can be instantiated for any type T that
  supports reference counting. It keeps track of a pointer to an object of type T,
  and handles incrementing and decrementing the reference count automatically.

  The purpose of RCPtr is to automate reference counting for any reference-counted
  object (any class that inherits from RC). It provides a safe way to have multiple
  pointers to an object without worrying about memory leaks or double-frees.

  RCPtr has a pointer member variable px that holds a pointer to the T object it
  references. It overloads operators like * and -> to dereference the pointer and
  access the referenced object.

  The key methods are the constructors and destructor. The constructors increment
  the reference count, and the destructor decrements it. This ensures the object
  will stay allocated as long as any RCPtr points to it, and be freed when the
  last RCPtr is destructed.

  Copy and move constructors increment the refcount before assigning px, while
  move assignment operators decrement the old refcount after reassigning px.

  RCPtr is a smart pointer class that automates reference counting for any
  reference-counted type T, allowing multiple pointer ownership without leaks or
  double-frees.
*/
template <typename T>
class RCPtr
{
  public:
    typedef T element_type;

    RCPtr() noexcept;
    RCPtr(T *p, const bool add_ref = true) noexcept;
    RCPtr(const RCPtr &rhs) noexcept;
    RCPtr(RCPtr &&rhs) noexcept;
    template <typename U>
    RCPtr(const RCPtr<U> &rhs) noexcept;
    ~RCPtr();

    RCPtr &operator=(const RCPtr &rhs) noexcept;
    RCPtr &operator=(RCPtr &&rhs) noexcept;

    void reset() noexcept;
    void reset(T *rhs) noexcept;
    void swap(RCPtr &rhs) noexcept;

    T *get() const noexcept;
    T &operator*() const noexcept;
    T *operator->() const noexcept;

    explicit operator bool() const noexcept;
    bool operator==(const RCPtr &rhs) const;
    bool operator!=(const RCPtr &rhs) const;

    RCPtr<T> move_strong() noexcept;
    template <typename U>
    RCPtr<U> static_pointer_cast() const noexcept;
    template <typename U>
    RCPtr<U> dynamic_pointer_cast() const noexcept;

  private:
    T *px; ///< Pointer to the controlled object
};
/**
  @brief Construct a new RCPtr<T>::RCPtr object
  @tparam T an RC enabled type

  The default constructor for the RCPtr class

  This constructor initializes the RCPtr class with no referenced object. It sets the
  internal px pointer to nullptr. The purpose of this default constructor is to allow
  creating an RCPtr instance that doesn't yet reference anything. This is useful when
  you need to declare an RCPtr variable but don't have an object to assign it to yet.
*/
template <typename T>
RCPtr<T>::RCPtr() noexcept
    : px(nullptr){};
/**
  @brief Construct a new RCPtr<T>::RCPtr object
  @tparam T an RC enabled type
  @param p pointer to an RC enabled object
  @param add_ref bool that determines whether the RC of p is incremented

  The RCPtr constructor taking a pointer and bool

  This constructor initializes an RCPtr instance to point to a provided object pointer p.

  It takes two inputs:
    - p - a pointer to an object of type T that inherits from RC (reference counted).
    - add_ref - a bool indicating if the reference count of p should be incremented.

  It does not return anything directly. Its purpose is to construct an RCPtr instance.

  The key logic is:
    - The px member is assigned the provided pointer p.
    - If add_ref is true, and px is non-null, the reference count of px is incremented
    via openvpn::intrusive_ptr_add_ref().

  This achieves the goal of constructing an RCPtr that points to the provided object pointer
  p. If add_ref is true, it will also increment the ref count of p, indicating that RCPtr
  now owns a count on that object.
*/
template <typename T>
RCPtr<T>::RCPtr(T *p, const bool add_ref) noexcept
    : px(p)
{
    if (px && add_ref)
        intrusive_ptr_add_ref(px);
}
/**
  @brief Copy constructor for RCPtr
  @tparam T an RC enabled type
  @param rhs the RCPtr to be copied
 */
template <typename T>
RCPtr<T>::RCPtr(const RCPtr &rhs) noexcept
    : px(rhs.px)
{
    if (px)
        intrusive_ptr_add_ref(px);
}
/**
  @brief Construct a new RCPtr object via move
  @tparam T an RC enabled type
  @param rhs object from which to move
*/
template <typename T>
RCPtr<T>::RCPtr(RCPtr &&rhs) noexcept
    : px(rhs.px)
{
    rhs.px = nullptr;
}
/**
  @brief Construct a new RCPtr<T>::RCPtr object to type T and make it track an object of type U
  @tparam T an RC enabled type
  @tparam U an RC enabled type
  @param rhs "RCPtr<U>" pointing to the object the new RCPtr<T> will reference as well

  This achieves the goal of creating an RCPtr<T> that points to the same object as the "RCPtr<U>".
*/
template <typename T>
template <typename U>
RCPtr<T>::RCPtr(const RCPtr<U> &rhs) noexcept
    : px(rhs.get())
{
    if (px)
        intrusive_ptr_add_ref(px);
}
/**
  @brief Destroy the RCPtr<T>::RCPtr object
  @tparam T an RC enabled type

  This achieves the goal of reducing the refcount when the RCPtr is destructed, possibly deleting
  the object if no other RCPtrs reference it anymore. The key data transformation is decrementing
  the refcount via openvpn::intrusive_ptr_release().
*/
template <typename T>
RCPtr<T>::~RCPtr()
{
    if (px)
        intrusive_ptr_release(px);
}
/**
  @brief Assigns an existing RCPtr<T> to point to a different T
  @tparam T an RC enabled type
  @param rhs other RCPtr<T>
  @return reference to this

  Assigns an existing RCPtr<T> to point to a different T, which is already controlled by another
  RCPtr<T>. Reduces the refcount on the current T.
*/
template <typename T>
RCPtr<T> &RCPtr<T>::operator=(const RCPtr &rhs) noexcept
{
    // notice that RCPtr(rhs) is a temp built from rhs, which will decrement old T when scope ends
    RCPtr(rhs).swap(*this);
    return *this;
}
/**
  @brief Assigns an existing RCPtr<T> to point to a different T using move
  @tparam T an RC enabled type
  @param rhs other RCPtr<T>
  @return reference to this

  Assigns an existing RCPtr<T> to point to a different T using move, by stealing the guts of another
  RCPtr.
*/
template <typename T>
RCPtr<T> &RCPtr<T>::operator=(RCPtr &&rhs) noexcept
{
    RCPtr(std::move(rhs)).swap(*this);
    return *this;
}
/**
  @brief Points this RCPtr<T> to nullptr safely
  @tparam T an RC enabled type
*/
template <typename T>
void RCPtr<T>::reset() noexcept
{
    RCPtr().swap(*this);
}
/**
  @brief Points this RCPtr to an RC enabled object T
  @note It's critical that the object in question be allocated via new.
  @tparam T an RC enabled type
  @param rhs pointer to the object that will be managed by this pointer
*/
template <typename T>
void RCPtr<T>::reset(T *rhs) noexcept
{
    RCPtr(rhs).swap(*this);
}
/**
  @brief swaps the contents of two RCPtr<T>
  @tparam T an RC enabled type
  @param rhs the other RCPtr<T>
*/
template <typename T>
void RCPtr<T>::swap(RCPtr &rhs) noexcept
{
    std::swap(px, rhs.px);
}
/**
  @brief Returns the raw pointer to the object T, or nullptr.
  @tparam T an RC enabled type
  @return T* pointer we are tracking, or nullptr.
*/
template <typename T>
T *RCPtr<T>::get() const noexcept
{
    return px;
}
/**
  @brief Operator returns a ref to the pointed to T
  @tparam T an RC enabled type
  @return T& reference to the object this RCPtr points to

  Operator returns a ref to the pointed to T, or if the RCPtr does not point
  to a valid T, undefined behavior due to dereference of invalid pointer. This
  is identical to the behavior of a C ptr or the STL smart pointers.
*/
template <typename T>
T &RCPtr<T>::operator*() const noexcept
{
    return *px;
}
/**
  @brief Returns the raw pointer to the object T, or nullptr.
  @tparam T an RC enabled type
  @return T* pointer we are tracking, or nullptr.
*/
template <typename T>
T *RCPtr<T>::operator->() const noexcept
{
    return px;
}
/**
  @brief Evaluates to true if the internal pointer is not equal to nullptr
  @tparam T an RC enabled type
  @return true if internal pointer is not equal to nullptr
  @return false if internal pointer is equal to nullptr
*/
template <typename T>
RCPtr<T>::operator bool() const noexcept
{
    return px != nullptr;
}
/**
  @brief Evaluates to true if the two RCPtr<T> point to the same object.
  @note Does not check equality of the pointed two object, rather checks for identity.
  @tparam T an RC enabled type
  @param rhs other RCPtr<T>
  @return true if *this and rhs point to the same instance or both equal nullptr
  @return false if *this and rhs point to different instances or do not both equal nullptr
*/
template <typename T>
bool RCPtr<T>::operator==(const RCPtr &rhs) const
{
    return px == rhs.px;
}
/**
  @brief Evaluates to true if the two RCPtr<T> point to different objects.
  @tparam T an RC enabled type
  @param rhs other RCPtr<T>
  @return true if *this and rhs point to different instances or do not both equal nullptr
  @return false if *this and rhs point to the same instance or both equal nullptr
*/
template <typename T>
bool RCPtr<T>::operator!=(const RCPtr &rhs) const
{
    return px != rhs.px;
}
/**
  @brief Moves ownership of the internal pointer to the returned RCPtr<T>
  @tparam T an RC enabled type
  @return The new owning RCPtr<T>
*/
template <typename T>
RCPtr<T> RCPtr<T>::move_strong() noexcept
{
    T *p = px;
    px = nullptr;
    return RCPtr<T>(p, false);
}
/**
  @brief Returns a "RCPtr<U>" that points to our T object
  @tparam T an RC enabled type
  @tparam U an RC enabled type
  @return "RCPtr<U>" that points to the same object this points to

  Performs a static_cast from T * to U * and then wraps the cast pointer in a new "RCPtr<U>"
*/
template <typename T>
template <typename U>
RCPtr<U> RCPtr<T>::static_pointer_cast() const noexcept
{
    return RCPtr<U>(static_cast<U *>(px));
}
/**
  @brief Returns a "RCPtr<U>" that points to our T object
  @tparam T an RC enabled type
  @tparam U an RC enabled type
  @return "RCPtr<U>" that points to the same object this points to, or nullptr

  Performs a dynamic_cast from T * to U * and then wraps the cast pointer in a new "RCPtr<U>",
  or if the dynamic_cast fails the result will equal nullptr cast to U * in a new "RCPtr<U>".
*/
template <typename T>
template <typename U>
RCPtr<U> RCPtr<T>::dynamic_pointer_cast() const noexcept
{
    return RCPtr<U>(dynamic_cast<U *>(px));
}
/**
  @brief implements a weak pointer for reference counted objects.
  @tparam T RCWeak enabled type

  RCWeakPtr takes a template parameter T which is the type of the object it will hold a weak pointer
  to. T must be a reference counted type. The purpose of RCWeakPtr is to hold a non-owning pointer to
  a reference counted object that can be converted to a strong owning pointer if the object still
  exists. It allows having a pointer to an object without affecting its reference count.

  RCWeakPtr contains a member variable controller which holds a pointer to the reference count
  controller object of the T object it points to. This allows it to query the reference count and
  check if the object still exists.

  The class provides methods to initialize the weak pointer from a strong pointer or raw pointer to a
  T object. This sets the controller to point to the T object's reference count controller.

  It also provides methods to reset the pointer, check if it has expired (if the object was deleted),
  get a strong owning pointer via lock() if the object still exists, and get the reference count.

  The key benefit of RCWeakPtr is being able to hold a non-owning pointer to a reference counted object
  without affecting its lifetime. It allows referencing the object without incrementing the reference
  count and can check if the object was deleted.
*/
template <typename T>
class RCWeakPtr
{
    typedef RCPtr<T> Strong;

  public:
    typedef T element_type;

    RCWeakPtr() noexcept;
    RCWeakPtr(const Strong &p) noexcept;
    RCWeakPtr(T *p) noexcept;

    void reset(const Strong &p) noexcept;
    void reset(T *p) noexcept;
    void reset() noexcept;

    void swap(RCWeakPtr &other) noexcept;
    olong use_count() const noexcept;
    bool expired() const noexcept;
    Strong lock() const noexcept;
    Strong move_strong() noexcept;

  private:
    typename T::Controller::Ptr controller; ///< Smart pointer to the T::ControllerF
};
/**
  @brief Construct a new RCWeakPtr<T>::RCWeakPtr object
  @tparam T RCWeak enabled type
*/
template <typename T>
RCWeakPtr<T>::RCWeakPtr() noexcept {};
/**
  @brief Construct a new RCWeakPtr<T>::RCWeakPtr object
  @tparam T RCWeak enabled type
  @param p RCPtr that holds a reference to an RCWeak::Controller
*/
template <typename T>
RCWeakPtr<T>::RCWeakPtr(const Strong &p) noexcept
{
    if (p)
        controller = p->refcount_.controller;
}
/**
  @brief Construct a new RCWeakPtr<T>::RCWeakPtr object
  @tparam T RCWeak enabled type
  @param p raw pointer to T
*/
template <typename T>
RCWeakPtr<T>::RCWeakPtr(T *p) noexcept
{
    if (p)
        controller = p->refcount_.controller;
}
/**
  @brief Reassign this weak ptr to the object referenced by the given strong (RCPtr) pointer
  @tparam T RCWeak enabled type
  @param p Strong pointer to an RCWeak enabled object instance
*/
template <typename T>
void RCWeakPtr<T>::reset(const Strong &p) noexcept
{
    if (p)
        controller = p->refcount_.controller;
    else
        controller.reset();
}
/**
  @brief Reassign this weak pointer to reference the controller within the specified object
  @tparam T RCWeak enabled type
  @param p an instance of an RCWeak enabled type
*/
template <typename T>
void RCWeakPtr<T>::reset(T *p) noexcept
{
    if (p)
        controller = p->refcount_.controller;
    else
        controller.reset();
}
/**
  @brief remove any existing reference
  @tparam T RCWeak enabled type
*/
template <typename T>
void RCWeakPtr<T>::reset() noexcept
{
    controller.reset();
}
/**
  @brief Swaps thing pointed to by *this withthing pointed to by other
  @tparam T RCWeak enabled type
  @param other the RCWeakPtr with which *this is to be swapped
*/
template <typename T>
void RCWeakPtr<T>::swap(RCWeakPtr &other) noexcept
{
    controller.swap(other.controller);
}
/**
  @brief Returns count of references to the object
  @tparam T RCWeak enabled type
  @return olong ref count

  If we point to a controller, we return the object use count for the object the controller
  refers to. Otherwise we return zero.
*/
template <typename T>
olong RCWeakPtr<T>::use_count() const noexcept
{
    if (controller)
        return controller->use_count();
    else
        return 0;
}
/**
  @brief Returns true if the underlying object is already freed.
  @tparam T RCWeak enabled type
  @return true if the object has been freed.
  @return false if the object still exists.
*/
template <typename T>
bool RCWeakPtr<T>::expired() const noexcept
{
    return use_count() == 0;
}
/**
  @brief Tries to upgrade the weak reference to a strong reference and returns that result
  @tparam T RCWeak enabled type
  @return RCWeakPtr<T>::Strong

  If the underlying object has been freed, returns empty Strong ptr, otherwise returns a
  Strong referring to the object.
*/
template <typename T>
typename RCWeakPtr<T>::Strong RCWeakPtr<T>::lock() const noexcept
{
    if (controller)
        return controller->template lock<Strong>();
    else
        return Strong();
}
/**
  @brief Try to move the weak pointer into a strong pointer
  @tparam T RCWeak enabled type
  @return RCWeakPtr<T>::Strong to the weakly referred to T or nullptr if the T is no longer available

  Releases the weak reference and either takes and returns a strong reference if possible
  or nullptr if the lock cannot be accomplished.
*/
template <typename T>
typename RCWeakPtr<T>::Strong RCWeakPtr<T>::move_strong() noexcept
{
    typename T::Controller::Ptr c;
    c.swap(controller);
    if (c)
        return c->template lock<Strong>();
    else
        return Strong();
}

/* We're pretty sure these are false positives. They only occur with very
   specific compiler versions and/or architectures.

   For some reason some gcc versions think that the reference counter goes
   away too soon when destructing certain MultiCompleteType objects. The
   warnings/errors do not tell us why they think that.

   So for now we display the warnings, but do not fail -Werror builds over
   them. So that we can fail them for any other new warnings.
 */
#if !defined(__clang__) && defined(__GNUC__)
#pragma GCC diagnostic push
#if __GNUC__ == 12
#pragma GCC diagnostic warning "-Wuse-after-free"
#endif
#if __GNUC__ == 13 || __GNUC__ == 14
#pragma GCC diagnostic warning "-Wstringop-overflow"
#endif
#endif
/**
  @brief implements a simple reference count for objects.

  The purpose of thread_unsafe_refcount is to keep track of how many references exist to an
  object and automatically delete the object when the reference count reaches zero. It
  provides methods to increment, decrement, and read the current reference count.

  thread_unsafe_refcount contains a member variable rc which holds the current reference count
  number as a type olong. Overall, thread_unsafe_refcount provides simple reference counting
  functionality to track object references in a single-threaded context. It could be used to
  implement basic automatic memory management based on scope and references for objects.
*/
class thread_unsafe_refcount
{
  public:
    thread_unsafe_refcount() noexcept;
    void operator++() noexcept;
    olong operator--() noexcept;
    bool inc_if_nonzero() noexcept;
    olong use_count() const noexcept;

    static constexpr bool is_thread_safe();

#ifdef OPENVPN_RC_NOTIFY
    void notify_release() noexcept;
#endif

#ifdef OPENVPN_RC_NOTIFY
    template <typename T>
    class ListHead;
#endif

  private:
    thread_unsafe_refcount(const thread_unsafe_refcount &) = delete;
    thread_unsafe_refcount &operator=(const thread_unsafe_refcount &) = delete;

    olong rc; ///< The reference count, platform efficient integer type
};
/**
  @brief Construct a new thread unsafe refcount::thread unsafe refcount object

  initializes rc to 0 for a new object with no references.
*/
inline thread_unsafe_refcount::thread_unsafe_refcount() noexcept
    : rc(olong(0)) {};
/**
  @brief Increment ref count by 1
*/
inline void thread_unsafe_refcount::operator++() noexcept
{
    ++rc;
}
/**
  @brief Decrement ref count by 1
  @return olong
*/
inline olong thread_unsafe_refcount::operator--() noexcept
{
    return --rc;
}
/**
  @brief Increments refcount by 1 if refcount is not 0, returns true if it incremented refcount
  @return true if the ref count was incremented
  @return false if the ref count was not incremented
*/
inline bool thread_unsafe_refcount::inc_if_nonzero() noexcept
{
    if (rc)
    {
        ++rc;
        return true;
    }
    else
        return false;
}
/**
  @brief Returns the internal use count
  @return olong ref count
*/
inline olong thread_unsafe_refcount::use_count() const noexcept
{
    return rc;
}
/**
  @brief Returns false for this type
  @return false

  This allows a uniform way to check at compile time or runtime and determine if the
  refcount type is thread safe or not. This one is not.
*/
inline constexpr bool thread_unsafe_refcount::is_thread_safe()
{
    return false;
}

#ifdef OPENVPN_RC_NOTIFY
inline void thread_unsafe_refcount::notify_release() noexcept
{
}
#endif

#ifdef OPENVPN_RC_NOTIFY
template <typename T>
class thread_unsafe_refcount::ListHead
{
  public:
    ListHead() noexcept
        : ptr(nullptr)
    {
    }

    T *load() noexcept
    {
        return ptr;
    }

    void insert(T *item) noexcept
    {
        item->next = ptr;
        ptr = item;
    }

  private:
    ListHead(const ListHead &) = delete;
    ListHead &operator=(const ListHead &) = delete;

    T *ptr;
};
#endif

/**
  @brief Implements a memory fenced ref count
*/
class thread_safe_refcount
{
  public:
    thread_safe_refcount() noexcept;
    void operator++() noexcept;
    olong operator--() noexcept;

    bool inc_if_nonzero() noexcept;
    olong use_count() const noexcept;
    static constexpr bool is_thread_safe();

#ifdef OPENVPN_RC_NOTIFY
    void notify_release() noexcept;
#endif

#ifdef OPENVPN_RC_NOTIFY
    template <typename T>
    class ListHead;
#endif

  private:
    thread_safe_refcount(const thread_safe_refcount &) = delete;
    thread_safe_refcount &operator=(const thread_safe_refcount &) = delete;

    std::atomic<olong> rc;
};
/**
  @brief Construct a new thread safe refcount object
*/
inline thread_safe_refcount::thread_safe_refcount() noexcept
    : rc(olong(0))
{
}
/**
  @brief Atomically increment the refcount by 1
*/
inline void thread_safe_refcount::operator++() noexcept
{
    rc.fetch_add(1, std::memory_order_relaxed);
}
/**
  @brief Atomically decrement the internal counter by 1
  @return olong decremented ref count
*/
inline olong thread_safe_refcount::operator--() noexcept
{
    // http://www.boost.org/doc/libs/1_55_0/doc/html/atomic/usage_examples.html
    const olong ret = rc.fetch_sub(1, std::memory_order_release) - 1;
    if (ret == 0)
        std::atomic_thread_fence(std::memory_order_acquire);
    return ret;
}
/**
  @brief Atomically increments refcount by 1 if refcount is not 0, returns true if it incremented refcount
  @return true if refcount was incremented
  @return false if refcount was not incremented

  If refcount is 0, do nothing and return false. If refcount != 0, increment it and return true.
*/
inline bool thread_safe_refcount::inc_if_nonzero() noexcept
{
    olong previous = rc.load(std::memory_order_relaxed);
    while (true)
    {
        if (!previous)
            break;
        if (rc.compare_exchange_weak(previous, previous + 1, std::memory_order_relaxed))
            break;
    }
    return previous > 0;
}
/**
  @brief Returns the internal use count
  @return olong ref count
*/
inline olong thread_safe_refcount::use_count() const noexcept
{
    return rc.load(std::memory_order_relaxed);
}
/**
  @brief Returns true for this class
  @return true

  This allows a uniform way to check at compile time or runtime and determine if the
  refcount type is thread safe or not. This one is thread safe.
*/
inline constexpr bool thread_safe_refcount::is_thread_safe()
{
    return true;
}

#ifdef OPENVPN_RC_NOTIFY
inline void thread_safe_refcount::notify_release() noexcept
{
}
#endif

#ifdef OPENVPN_RC_NOTIFY
template <typename T>
class thread_safe_refcount::ListHead
{
  public:
    ListHead() noexcept
        : ptr(nullptr)
    {
    }

    T *load() noexcept
    {
        return ptr;
    }

    void insert(T *item) noexcept
    {
        item->next = ptr;
        ptr = item;
    }

  private:
    ListHead(const ListHead &) = delete;
    ListHead &operator=(const ListHead &) = delete;

    T *ptr;
};
#endif


#if !defined(__clang__) && defined(__GNUC__)
#pragma GCC diagnostic pop
#endif

/**
  @brief Reference count base class for objects tracked by RCPtr. Disallows copying and assignment.

  Implements basic reference counting functionality for objects.

  The purpose of RC is to provide a base class that other classes can inherit from to enable
  reference counting and automatic memory management. It takes a template parameter RCImpl which
  specifies the actual reference count implementation class that will be used, either
  thread_safe_refcount or thread_unsafe_refcount.

  RC provides a common base class for enabling reference counting on other classes via inheritance.
  It delegates the actual reference count tracking to the RCImpl implementation specified as a
  template parameter. It also prohibits copy or assignment of the inheriting object.

  The member functions just delegate to the refcount_ object that's injected via template.

  @tparam RCImpl The ref count implementation that will be used - thread_safe_refcount or thread_unsafe_refcount
*/
template <typename RCImpl>
class RC
{
  public:
    typedef RCPtr<RC> Ptr;

    RC() noexcept = default;
    virtual ~RC() = default;
    RC(const RC &) = delete;
    RC &operator=(const RC &) = delete;

    olong use_count() const noexcept;
    static constexpr bool is_thread_safe();

  private:
    template <typename R>
    friend void intrusive_ptr_add_ref(R *rcptr) noexcept;
    template <typename R>
    friend void intrusive_ptr_release(R *rcptr) noexcept;
    RCImpl refcount_;
};
/**
  @brief Delegates call to RCImpl and returns the result
  @tparam RCImpl a suitable ref count implementation
  @return olong ref count
*/
template <typename RCImpl>
olong RC<RCImpl>::use_count() const noexcept
{
    return refcount_.use_count();
}
/**
  @brief Delegates call to RCImpl and returns the result
  @tparam RCImpl a suitable ref count implementation
  @return true if the underlying RCImpl is thread safe
  @return false if the underlying RCImpl is not thread safe
*/
template <typename RCImpl>
constexpr bool RC<RCImpl>::is_thread_safe()
{
    return RCImpl::is_thread_safe();
}

/**
  @brief Reference count base class for objects tracked by RCPtr. Allows copying and assignment.
  @tparam RCImpl The ref count implementation that will be used - thread_safe_refcount or thread_unsafe_refcount

  Implements basic reference counting functionality for objects plus copy/assign.

  The purpose of RCCopyable is to provide a base class that other classes can inherit from to
  enable reference counting and automatic memory management. It takes a template parameter
  RCImpl which specifies the actual reference count implementation class that will be used, either
  thread_safe_refcount or thread_unsafe_refcount.

  RCCopyable provides a common base class for enabling reference counting on other classes via
  inheritance. It delegates the actual reference count tracking to the RCImpl implementation
  specified as a template parameter.

  Since copy and assignment are allowed and this is a reference counted type, some atypical logic
  is implemented to make those operations work properly. This means that while the rest of the
  inheriting object will be copied as per usual, for this object we will give the copy a fresh
  reference count, since the count is bookkeeping metadata. The referencing pointers to the individual
  objects will then take care of properly maintaining the RC of the new and the donor object as
  per usual.

  The member functions just delegate to the refcount_ object that's injected via template.
*/
template <typename RCImpl>
class RCCopyable
{
  public:
    virtual ~RCCopyable() = default;
    RCCopyable() noexcept = default;
    RCCopyable(const RCCopyable &) noexcept;
    RCCopyable(RCCopyable &&) noexcept;
    RCCopyable &operator=(const RCCopyable &) noexcept;
    RCCopyable &operator=(RCCopyable &&) noexcept;
    olong use_count() const noexcept;

  private:
    template <typename R>
    friend void intrusive_ptr_add_ref(R *rcptr) noexcept;
    template <typename R>
    friend void intrusive_ptr_release(R *rcptr) noexcept;
    RCImpl refcount_;
};

/**
  @brief Construct a new RCCopyable object
  @tparam RCImpl RC compatible type to use as ref count

  Copy/move construction/assignment are no-ops because we always want to
  default-construct the refcount from scratch.  We never want to actually copy
  or move the refcount because that would break any referencing smart pointers.

  We make a fresh object (by copy/move/assignment) and we want a new ref count.
*/
template <typename RCImpl>
RCCopyable<RCImpl>::RCCopyable(const RCCopyable &) noexcept {};
/**
  @brief Construct a new RCCopyable object by move
  @tparam RCImpl RC compatible type to use as ref count

  Copy/move construction/assignment are no-ops because we always want to
  default-construct the refcount from scratch.  We never want to actually copy
  or move the refcount because that would break any referencing smart pointers.

  We make a fresh object (by copy/move/assignment) and we want a new ref count.
*/
template <typename RCImpl>
RCCopyable<RCImpl>::RCCopyable(RCCopyable &&) noexcept {};
/**
  @brief Ensures the new ref count is not copied with the rest of the object.
  @tparam RCImpl
  @return RCCopyable<RCImpl>&

  Copy/move construction/assignment are no-ops because we always want to
  default-construct the refcount from scratch.  We never want to actually copy
  or move the refcount because that would break any referencing smart pointers.

  We make a fresh object (by copy/move/assignment) and we want a new ref count.
*/
template <typename RCImpl>
RCCopyable<RCImpl> &RCCopyable<RCImpl>::operator=(const RCCopyable &) noexcept
{
    return *this;
}
/**
  @brief Ensures the new ref count is not moved with the rest of the object.
  @tparam RCImpl
  @return RCCopyable<RCImpl>&

  Copy/move construction/assignment are no-ops because we always want to
  default-construct the refcount from scratch.  We never want to actually copy
  or move the refcount because that would break any referencing smart pointers.

  We make a fresh object (by copy/move/assignment) and we want a new ref count.
*/
template <typename RCImpl>
RCCopyable<RCImpl> &RCCopyable<RCImpl>::operator=(RCCopyable &&) noexcept
{
    return *this;
}
/**
  @brief Returns the use count as reported by defering to the injected ref count type.
  @tparam RCImpl the injected ref count implementation
  @return olong current ref count
*/
template <typename RCImpl>
olong RCCopyable<RCImpl>::use_count() const noexcept
{
    return refcount_.use_count();
}

/**
  @brief Reference count base class for objects tracked by RCPtr. Like RC, but also allows weak
  pointers and release notification callables.
  @tparam RCImpl The ref count implementation that will be used - thread_safe_refcount or thread_unsafe_refcount

  Implements basic reference counting functionality for objects plus weak pointer capability.

  The purpose of RCWeak is to provide a base class that other classes can inherit from to
  enable reference counting and automatic memory management. It takes a template parameter
  RCImpl which specifies the actual reference count implementation class that will be used,
  either thread_safe_refcount or thread_unsafe_refcount.

  RCWeak provides a common base class for enabling reference counting on other classes via
  inheritance. It delegates the actual reference count tracking to the RCImpl implementation
  specified as a template parameter. It also disables copy but enables move.

  Nested classes are used to facilitate the requirements of weak pointers and those classes
  are documented seperately. The member functions just delegate to the refcount_ object
  that's injected via template, with the previously mentionend nested classes changing the
  way ref counts work as required.

  @see RCWeak::Controller for more details
  @see RCWeak::ControllerRef for more details
*/
template <typename RCImpl> // RCImpl = thread_safe_refcount or thread_unsafe_refcount
class RCWeak
{
    template <typename T>
    friend class RCWeakPtr;

#ifdef OPENVPN_RC_NOTIFY
    // Base class of release notification callables
    class NotifyBase;
    // A release notification callable
    template <typename CALLABLE>
    class NotifyItem;
    // Head of a linked-list of release notification callables
    class NotifyListHead;
#endif

    struct Controller;
    struct ControllerRef;

  public:
    typedef RCPtr<RCWeak> Ptr;
    typedef RCWeakPtr<RCWeak> WPtr;

    RCWeak() noexcept
        : refcount_(this) {};

    virtual ~RCWeak() = default;
    RCWeak(const RCWeak &) = delete;
    RCWeak &operator=(const RCWeak &) = delete;

#ifdef OPENVPN_RC_NOTIFY
    // Add observers to be called just prior to object deletion,
    // but after refcount has been decremented to 0.  At this
    // point, all weak pointers have expired, and no strong
    // pointers are outstanding.  Callables can access the
    // object by raw pointer but must NOT attempt to create a
    // strong pointer referencing the object.

    template <typename CALLABLE>
    void rc_release_notify(const CALLABLE &c) noexcept
    {
        refcount_.notify.add(c);
    }

    template <typename CALLABLE>
    void rc_release_notify(CALLABLE &&c) noexcept
    {
        refcount_.notify.add(std::move(c));
    }
#endif

  private:
    template <typename R>
    friend void intrusive_ptr_add_ref(R *rcptr) noexcept;
    template <typename R>
    friend void intrusive_ptr_release(R *rcptr) noexcept;

    ControllerRef refcount_; ///< Adapter instance that allows RCPtr to properly interact with RCWeak
};

/**
  @brief Controller structure for our ptr/weakptr implementation
  @tparam RCImpl The ref count implementation

  For weak-referenceable objects, we must detach the refcount from the object and place it
  in Controller.

  This struct implements 3 essential elements:

    - A pointer to the parent (RCWeak enabled) object.
    - A ref count on Controller via RC<RCImpl> inheritance.
    - A ref count inside Controller via the RCImpl member 'rc'.

  They are used as follows.

  Controller::rc is the ref count of the object, and the inherited RCImpl is the ref count of the
  Controller itself. The 'parent' pointer is used to form a Strong (owning) pointer on demand if
  the Controller::rc refcount is greater than zero, or if not a nullptr Strong is returned.

  An RCPtr<> will call intrusive_ptr_add_ref and intrusive_ptr_release helpers which will
  increment and decrement refcount_, which for RCWeak is an instance of RCWeak<>::ControllerRef.
  This ControllerRef instance will delegate those operations to the 'controller' member, which
  is an RCWeak<>::Controller referenced via an RCPtr<> instance inside ControllerRef. This is
  where the composed 'rc' refcount is modified.

  The inherited RCImpl is controlled by the 'Controller::Ptr controller' member in ControllerRef
  via the member refcount_ and by the RCWeakPtr reaching in and manipulating it. Thus the object
  itself has 1 refcount on the controller and each weak ptr also holds a refcount on the controller.

  When the last RCPtr on the object goes out of scope and decrements Controller::rc to zero, the
  object will be destroyed. If the only refcount on the controller is the ControllerRef, then the
  Controller instance will also be destroyed at this time. Otherwise the weak pointers to the
  controller will control that object's lifespan, but no further Strong pointers to the (now
  nonexistant) object will be allowed. This test for zero is what allows 'parent' to dangle
  harmlessly in some cases.
*/
template <typename RCImpl>
struct RCWeak<RCImpl>::Controller : public RC<RCImpl>
{
    typedef RCPtr<Controller> Ptr;

    Controller(RCWeak *parent_arg) noexcept;

    olong use_count() const noexcept;

    template <typename PTR>
    PTR lock() noexcept;

    RCWeak *const parent; ///< Pointer to object, dangles (harmlessly) after rc decrements to 0
    RCImpl rc;            ///< refcount implementation for 'parent' object
};
/**
  @brief Construct a new RCWeak<RCImpl>::Controller object
  @tparam RCImpl The ref count implementation
  @param parent_arg raw pointer to the RCWeak enabled object instance
*/
template <typename RCImpl>
RCWeak<RCImpl>::Controller::Controller(RCWeak *parent_arg) noexcept
    : parent(parent_arg){};
/**
  @brief Get the use count of the underlying ref count implementation
  @tparam RCImpl the ref count implementation
  @return olong ref count
*/
template <typename RCImpl>
olong RCWeak<RCImpl>::Controller::use_count() const noexcept
{
    return rc.use_count();
}
/**
  @brief Used internally to try a to get a pointer to the controlled instance
  @tparam RCImpl the injected ref count implementation
  @tparam PTR explicit return type
  @return PTR pointer type referring to either the weakly ref'd object or nullptr

  Will return the requested pointer to the object if the ref count could be incremented,
  or the specified pointer type set to nullptr
*/
template <typename RCImpl>
template <typename PTR>
PTR RCWeak<RCImpl>::Controller::lock() noexcept
{
    if (rc.inc_if_nonzero())
        return PTR(static_cast<typename PTR::element_type *>(parent), false);
    else
        return PTR();
}

/**
  @brief Adapter object for RCWeak::Controller <---> RCPtr
  @tparam RCImpl the injected ref count implementation

  Serves as an adapter that changes the normal RCPtr/RC interaction such that the embedded controller
  rc member is used as the ref count.
*/
template <typename RCImpl>
struct RCWeak<RCImpl>::ControllerRef
{
    explicit ControllerRef(RCWeak *parent) noexcept;

    void operator++() noexcept;
    olong operator--() noexcept;

#ifdef OPENVPN_RC_NOTIFY
    void notify_release() noexcept;
#endif

    typename Controller::Ptr controller; ///< object containing actual refcount

#ifdef OPENVPN_RC_NOTIFY
    NotifyListHead notify; // linked list of callables to be notified on object release
#endif
};
/**
  @brief Construct a new RCWeak<RCImpl>::ControllerRef object
  @tparam RCImpl The ref count implementation
  @param parent raw pointer to RCWeak enabled type

  Creates a ControllerRef and associates that adapter with the specified instance
*/
template <typename RCImpl>
RCWeak<RCImpl>::ControllerRef::ControllerRef(RCWeak *parent) noexcept
    : controller(new Controller(parent)){};
/**
  @brief Increments the ref count
  @tparam RCImpl the injected ref count implementation

  The adapter changes the usual implementation of this operator to increment a ref count 'rc'
  that's inside the controller object for the controlled instance.
*/
template <typename RCImpl>
void RCWeak<RCImpl>::ControllerRef::operator++() noexcept
{
    ++controller->rc;
}
/**
  @brief Decrements the ref count
  @tparam RCImpl the injected ref count implementation

  The adapter changes the usual implementation of this operator to decrement a ref count 'rc'
  that's inside the controller object for the controlled instance.
*/
template <typename RCImpl>
olong RCWeak<RCImpl>::ControllerRef::operator--() noexcept
{
    return --controller->rc;
}

#ifdef OPENVPN_RC_NOTIFY
template <typename RCImpl>
void RCWeak<RCImpl>::ControllerRef::notify_release() noexcept
{
    notify.release();
}
#endif

#ifdef OPENVPN_RC_NOTIFY
// Base class of release notification callables
template <typename RCImpl>
class RCWeak<RCImpl>::NotifyBase
{
  public:
    NotifyBase() noexcept = default;
    virtual void call() noexcept = 0;
    virtual ~NotifyBase() = default;
    NotifyBase *next = nullptr;

  private:
    NotifyBase(const NotifyBase &) = delete;
    NotifyBase &operator=(const NotifyBase &) = delete;
};

// A release notification callable
template <typename RCImpl>
template <typename CALLABLE>
class RCWeak<RCImpl>::NotifyItem : public NotifyBase
{
  public:
    NotifyItem(const CALLABLE &c) noexcept
        : callable(c)
    {
    }

    NotifyItem(CALLABLE &&c) noexcept
        : callable(std::move(c))
    {
    }

  private:
    void call() noexcept override
    {
        callable();
    }

    CALLABLE callable;
};

// Head of a linked-list of release notification callables
template <typename RCImpl>
class RCWeak<RCImpl>::NotifyListHead
{
  public:
    NotifyListHead() noexcept
    {
    }

    template <typename CALLABLE>
    void add(const CALLABLE &c) noexcept
    {
        NotifyBase *item = new NotifyItem<CALLABLE>(c);
        head.insert(item);
    }

    template <typename CALLABLE>
    void add(CALLABLE &&c) noexcept
    {
        NotifyBase *item = new NotifyItem<CALLABLE>(std::move(c));
        head.insert(item);
    }

    void release() noexcept
    {
        // In thread-safe mode, list traversal is guaranteed to be
        // contention-free because we are not called until refcount
        // reaches zero and after a std::memory_order_acquire fence.
        NotifyBase *nb = head.load();
        while (nb)
        {
            NotifyBase *next = nb->next;
            nb->call();
            delete nb;
            nb = next;
        }
    }

  private:
    NotifyListHead(const NotifyListHead &) = delete;
    NotifyListHead &operator=(const NotifyListHead &) = delete;

    typename RCImpl::template ListHead<NotifyBase> head;
};
#endif
/**
  @brief Helper to increment a ref count
  @tparam R type that has an incrementable member refcount_
  @param rcptr pointer to instance of R
  @todo consider removing debug cout

  Helper function template to implement incrementing of a member 'refcount_' of a type R; acts as an
  adapter layer to implement this funtionality as well as some conditionally built debug logging.
*/
template <typename R>
inline void intrusive_ptr_add_ref(R *rcptr) noexcept
{
#ifdef OPENVPN_RC_DEBUG
    std::cout << "ADD REF " << cxx_demangle(typeid(rcptr).name()) << std::endl;
#endif
    ++rcptr->refcount_;
}
/**
  @brief Helper to decrement a ref count
  @tparam R type that has an decrementable member refcount_
  @param rcptr pointer to instance of R
  @todo consider removing debug cout

  Helper function template to implement decrementing of a member 'refcount_' of a type R; acts as an
  adapter layer to implement this funtionality as well as some conditionally built debug logging and
  a conditionally built notify hook.
*/
template <typename R>
inline void intrusive_ptr_release(R *rcptr) noexcept
{
    if (--rcptr->refcount_ == 0)
    {
#ifdef OPENVPN_RC_DEBUG
        std::cout << "DEL OBJ " << cxx_demangle(typeid(rcptr).name()) << std::endl;
#endif
#ifdef OPENVPN_RC_NOTIFY
        rcptr->refcount_.notify_release();
#endif
        delete rcptr;
    }
    else
    {
#ifdef OPENVPN_RC_DEBUG
        std::cout << "REL REF " << cxx_demangle(typeid(rcptr).name()) << std::endl;
#endif
    }
}

} // namespace openvpn

#endif // OPENVPN_COMMON_RC_H
