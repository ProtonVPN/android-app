//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU Affero General Public License Version 3
//    as published by the Free Software Foundation.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU Affero General Public License for more details.
//
//    You should have received a copy of the GNU Affero General Public License
//    along with this program in the COPYING file.
//    If not, see <http://www.gnu.org/licenses/>.

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

  // The smart pointer class
  template <typename T>
  class RCPtr
  {
  public:
    typedef T element_type;

    RCPtr() noexcept
      : px(nullptr)
    {
    }

    RCPtr(T* p, const bool add_ref=true) noexcept
      : px(p)
    {
      if (px && add_ref)
	intrusive_ptr_add_ref(px);
    }

    RCPtr(const RCPtr& rhs) noexcept
      : px(rhs.px)
    {
      if (px)
	intrusive_ptr_add_ref(px);
    }

    RCPtr(RCPtr&& rhs) noexcept
      : px(rhs.px)
    {
      rhs.px = nullptr;
    }

    template <typename U>
    RCPtr(const RCPtr<U>& rhs) noexcept
      : px(rhs.get())
    {
      if (px)
	intrusive_ptr_add_ref(px);
    }

    ~RCPtr()
    {
      if (px)
	intrusive_ptr_release(px);
    }

    RCPtr& operator=(const RCPtr& rhs) noexcept
    {
      RCPtr(rhs).swap(*this);
      return *this;
    }

    RCPtr& operator=(RCPtr&& rhs) noexcept
    {
      RCPtr(std::move(rhs)).swap(*this);
      return *this;
    }

    void reset() noexcept
    {
      RCPtr().swap(*this);
    }

    void reset(T* rhs) noexcept
    {
      RCPtr(rhs).swap(*this);
    }

    void swap(RCPtr& rhs) noexcept
    {
      std::swap(px, rhs.px);
    }

    T* get() const noexcept
    {
      return px;
    }

    T& operator*() const noexcept
    {
      return *px;
    }

    T* operator->() const noexcept
    {
      return px;
    }

    explicit operator bool() const noexcept
    {
      return px != nullptr;
    }

    bool operator==(const RCPtr& rhs) const
    {
      return px == rhs.px;
    }

    bool operator!=(const RCPtr& rhs) const
    {
      return px != rhs.px;
    }

    RCPtr<T> move_strong() noexcept
    {
      T* p = px;
      px = nullptr;
      return RCPtr<T>(p, false);
    }

    template <typename U>
    RCPtr<U> static_pointer_cast() const noexcept
    {
      return RCPtr<U>(static_cast<U*>(px));
    }

    template <typename U>
    RCPtr<U> dynamic_pointer_cast() const noexcept
    {
      return RCPtr<U>(dynamic_cast<U*>(px));
    }

  private:
    T* px;
  };

  template <typename T>
  class RCWeakPtr
  {
    typedef RCPtr<T> Strong;

  public:
    typedef T element_type;

    RCWeakPtr() noexcept {}

    RCWeakPtr(const Strong& p) noexcept
    {
      if (p)
	controller = p->refcount_.controller;
    }

    RCWeakPtr(T* p) noexcept
    {
      if (p)
	controller = p->refcount_.controller;
    }

    void reset(const Strong& p) noexcept
    {
      if (p)
	controller = p->refcount_.controller;
      else
	controller.reset();
    }

    void reset(T* p) noexcept
    {
      if (p)
	controller = p->refcount_.controller;
      else
	controller.reset();
    }

    void reset() noexcept
    {
      controller.reset();
    }

    void swap(RCWeakPtr& other) noexcept
    {
      controller.swap(other.controller);
    }

    olong use_count() const noexcept
    {
      if (controller)
	return controller->use_count();
      else
	return 0;
    }

    bool expired() const noexcept
    {
      return use_count() == 0;
    }

    Strong lock() const noexcept
    {
      if (controller)
	return controller->template lock<Strong>();
      else
	return Strong();
    }

    Strong move_strong() noexcept
    {
      typename T::Controller::Ptr c;
      c.swap(controller);
      if (c)
	return c->template lock<Strong>();
      else
	return Strong();
    }

  private:
    typename T::Controller::Ptr controller;
  };

  class thread_unsafe_refcount
  {
  public:
    thread_unsafe_refcount() noexcept
      : rc(olong(0))
    {
    }

    void operator++() noexcept
    {
      ++rc;
    }

    olong operator--() noexcept
    {
      return --rc;
    }

    bool inc_if_nonzero() noexcept
    {
      if (rc)
	{
	  ++rc;
	  return true;
	}
      else
	return false;
    }

    olong use_count() const noexcept
    {
      return rc;
    }

    static constexpr bool is_thread_safe()
    {
      return false;
    }

#ifdef OPENVPN_RC_NOTIFY
    void notify_release() noexcept
    {
    }
#endif

#ifdef OPENVPN_RC_NOTIFY
    template <typename T>
    class ListHead
    {
    public:
      ListHead() noexcept : ptr(nullptr) {}

      T* load() noexcept
      {
	return ptr;
      }

      void insert(T* item) noexcept
      {
	item->next = ptr;
	ptr = item;
      }

    private:
      ListHead(const ListHead&) = delete;
      ListHead& operator=(const ListHead&) = delete;

      T* ptr;
    };
#endif

  private:
    thread_unsafe_refcount(const thread_unsafe_refcount&) = delete;
    thread_unsafe_refcount& operator=(const thread_unsafe_refcount&) = delete;

    olong rc;
  };

  class thread_safe_refcount
  {
  public:
    thread_safe_refcount() noexcept
      : rc(olong(0))
    {
    }

    void operator++() noexcept
    {
      rc.fetch_add(1, std::memory_order_relaxed);
    }

    olong operator--() noexcept
    {
      // http://www.boost.org/doc/libs/1_55_0/doc/html/atomic/usage_examples.html
      const olong ret = rc.fetch_sub(1, std::memory_order_release) - 1;
      if (ret == 0)
	std::atomic_thread_fence(std::memory_order_acquire);
      return ret;
    }

    // If refcount is 0, do nothing and return false.
    // If refcount != 0, increment it and return true.
    bool inc_if_nonzero() noexcept
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

    olong use_count() const noexcept
    {
      return rc.load(std::memory_order_relaxed);
    }

    static constexpr bool is_thread_safe()
    {
      return true;
    }

#ifdef OPENVPN_RC_NOTIFY
    void notify_release() noexcept
    {
    }
#endif

#ifdef OPENVPN_RC_NOTIFY
    template <typename T>
    class ListHead
    {
    public:
      ListHead() noexcept : ptr(nullptr) {}

      T* load() noexcept
      {
	return ptr.load(std::memory_order_relaxed);
      }

      void insert(T* item) noexcept
      {
	T* previous = ptr.load(std::memory_order_relaxed);
	while (true)
	  {
	    item->next = previous;
	    if (ptr.compare_exchange_weak(previous, item, std::memory_order_relaxed))
	      break;
	  }
      }

    private:
      ListHead(const ListHead&) = delete;
      ListHead& operator=(const ListHead&) = delete;

      std::atomic<T*> ptr;
    };
#endif

  private:
    thread_safe_refcount(const thread_safe_refcount&) = delete;
    thread_safe_refcount& operator=(const thread_safe_refcount&) = delete;

    std::atomic<olong> rc;
  };

  // Reference count base class for objects tracked by RCPtr.
  // Disallows copying and assignment.
  template <typename RCImpl> // RCImpl = thread_safe_refcount or thread_unsafe_refcount
  class RC
  {
  public:
    typedef RCPtr<RC> Ptr;

    RC() noexcept {}
    virtual ~RC() {}

    olong use_count() const noexcept
    {
      return refcount_.use_count();
    }

    static constexpr bool is_thread_safe()
    {
      return RCImpl::is_thread_safe();
    }

  private:
    RC(const RC&) = delete;
    RC& operator=(const RC&) = delete;

    template <typename R> friend void intrusive_ptr_add_ref(R* p) noexcept;
    template <typename R> friend void intrusive_ptr_release(R* p) noexcept;
    RCImpl refcount_;
  };

  // Like RC, but allows object to be copied and assigned.
  template <typename RCImpl> // RCImpl = thread_safe_refcount or thread_unsafe_refcount
  class RCCopyable
  {
  public:
    RCCopyable() noexcept {}
    RCCopyable(const RCCopyable&) noexcept {}
    RCCopyable& operator=(const RCCopyable&) noexcept { return *this; }
    virtual ~RCCopyable() {}

    olong use_count() const noexcept
    {
      return refcount_.use_count();
    }

  private:
    template <typename R> friend void intrusive_ptr_add_ref(R* p) noexcept;
    template <typename R> friend void intrusive_ptr_release(R* p) noexcept;
    RCImpl refcount_;
  };

  // Like RC, but also allows weak pointers and release notification callables
  template <typename RCImpl> // RCImpl = thread_safe_refcount or thread_unsafe_refcount
  class RCWeak
  {
    template<typename T>
    friend class RCWeakPtr;

#ifdef OPENVPN_RC_NOTIFY
    // Base class of release notification callables
    class NotifyBase
    {
    public:
      NotifyBase() noexcept {}
      virtual void call() noexcept = 0;
      virtual ~NotifyBase() {}
      NotifyBase* next;

    private:
      NotifyBase(const NotifyBase&) = delete;
      NotifyBase& operator=(const NotifyBase&) = delete;
    };

    // A release notification callable
    template <typename CALLABLE>
    class NotifyItem : public NotifyBase
    {
    public:
      NotifyItem(const CALLABLE& c) noexcept
	: callable(c)
      {
      }

      NotifyItem(CALLABLE&& c) noexcept
      : callable(std::move(c))
      {
      }

    private:
      virtual void call() noexcept override
      {
	callable();
      }

      CALLABLE callable;
    };

    // Head of a linked-list of release notification callables
    class NotifyListHead
    {
    public:
      NotifyListHead() noexcept {}

      template <typename CALLABLE>
      void add(const CALLABLE& c) noexcept
      {
	NotifyBase* item = new NotifyItem<CALLABLE>(c);
	head.insert(item);
      }

      template <typename CALLABLE>
      void add(CALLABLE&& c) noexcept
      {
	NotifyBase* item = new NotifyItem<CALLABLE>(std::move(c));
	head.insert(item);
      }

      void release() noexcept
      {
	// In thread-safe mode, list traversal is guaranteed to be
	// contention-free because we are not called until refcount
	// reaches zero and after a std::memory_order_acquire fence.
	NotifyBase* nb = head.load();
	while (nb)
	  {
	    NotifyBase* next = nb->next;
	    nb->call();
	    delete nb;
	    nb = next;
	  }
      }

    private:
      NotifyListHead(const NotifyListHead&) = delete;
      NotifyListHead& operator=(const NotifyListHead&) = delete;

      typename RCImpl::template ListHead<NotifyBase> head;
    };
#endif

    // For weak-referenceable objects, we must detach the
    // refcount from the object and place it in Controller.
    struct Controller : public RC<RCImpl>
    {
      typedef RCPtr<Controller> Ptr;

      Controller(RCWeak* parent_arg) noexcept
	: parent(parent_arg)
      {
      }

      olong use_count() const noexcept
      {
	return rc.use_count();
      }

      template <typename PTR>
      PTR lock() noexcept
      {
	if (rc.inc_if_nonzero())
	  return PTR(static_cast<typename PTR::element_type*>(parent), false);
	else
	  return PTR();
      }

      RCWeak *const parent;  // dangles (harmlessly) after rc decrements to 0
      RCImpl rc;             // refcount
    };

    struct ControllerRef
    {
      ControllerRef(RCWeak* parent) noexcept
        : controller(new Controller(parent))
      {
      }

      void operator++() noexcept
      {
	++controller->rc;
      }

      olong operator--() noexcept
      {
	return --controller->rc;
      }

#ifdef OPENVPN_RC_NOTIFY
      void notify_release() noexcept
      {
	notify.release();
      }
#endif

      typename Controller::Ptr controller;  // object containing actual refcount

#ifdef OPENVPN_RC_NOTIFY
      NotifyListHead notify;                // linked list of callables to be notified on object release
#endif
    };

  public:
    typedef RCPtr<RCWeak> Ptr;
    typedef RCWeakPtr<RCWeak> WPtr;

    RCWeak() noexcept
      : refcount_(this)
    {
    }

    virtual ~RCWeak()
    {
    }

#ifdef OPENVPN_RC_NOTIFY
    // Add observers to be called just prior to object deletion,
    // but after refcount has been decremented to 0.  At this
    // point, all weak pointers have expired, and no strong
    // pointers are outstanding.  Callables can access the
    // object by raw pointer but must NOT attempt to create a
    // strong pointer referencing the object.

    template <typename CALLABLE>
    void rc_release_notify(const CALLABLE& c) noexcept
    {
      refcount_.notify.add(c);
    }

    template <typename CALLABLE>
    void rc_release_notify(CALLABLE&& c) noexcept
    {
      refcount_.notify.add(std::move(c));
    }
#endif

  private:
    RCWeak(const RCWeak&) = delete;
    RCWeak& operator=(const RCWeak&) = delete;

    template <typename R> friend void intrusive_ptr_add_ref(R* p) noexcept;
    template <typename R> friend void intrusive_ptr_release(R* p) noexcept;

    ControllerRef refcount_;
  };

  template <typename R>
  inline void intrusive_ptr_add_ref(R *p) noexcept
  {
#ifdef OPENVPN_RC_DEBUG
    std::cout << "ADD REF " << cxx_demangle(typeid(p).name()) << std::endl;
#endif
    ++p->refcount_;
  }

  template <typename R>
  inline void intrusive_ptr_release(R *p) noexcept
  {
    if (--p->refcount_ == 0)
      {
#ifdef OPENVPN_RC_DEBUG
	std::cout << "DEL OBJ " << cxx_demangle(typeid(p).name()) << std::endl;
#endif
#ifdef OPENVPN_RC_NOTIFY
	p->refcount_.notify_release();
#endif
	delete p;
      }
    else
      {
#ifdef OPENVPN_RC_DEBUG
	std::cout << "REL REF " << cxx_demangle(typeid(p).name()) << std::endl;
#endif
      }
  }

} // namespace openvpn

#endif // OPENVPN_COMMON_RC_H
