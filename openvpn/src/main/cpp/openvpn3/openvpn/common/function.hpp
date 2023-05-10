//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2022 OpenVPN Inc.
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

// High-performance functor with move-only semantics.

#ifndef OPENVPN_COMMON_FUNCTION_H
#define OPENVPN_COMMON_FUNCTION_H

#include <cstddef> // for std::size_t
#include <utility> // for std::move
#include <type_traits>
#include <new>

namespace openvpn {
// F -- function type (usually a lambda expression)
// N (default=3) -- max size of functor in void* machine words before we overflow to dynamic allocation
// INTERN_ONLY (default=false) -- if true, throw a static assertion if functor cannot be stored internally
template <typename F, std::size_t N = 3, bool INTERN_ONLY = false>
class Function;

template <typename R, typename... A, std::size_t N, bool INTERN_ONLY>
class Function<R(A...), N, INTERN_ONLY>
{
  public:
    Function() noexcept
    {
        methods = nullptr;
    }

    template <typename T>
    Function(T &&functor) noexcept
    {
        construct(std::move(functor));
    }

    Function(Function &&other) noexcept
    {
        methods = other.methods;
        other.methods = nullptr;
        if (methods)
            methods->move(data, other.data);
    }

    Function &operator=(Function &&other) noexcept
    {
        if (methods)
            methods->destruct(data);
        methods = other.methods;
        other.methods = nullptr;
        if (methods)
            methods->move(data, other.data);
        return *this;
    }

    ~Function()
    {
        if (methods)
            methods->destruct(data);
    }

    template <typename T>
    void reset(T &&functor) noexcept
    {
        if (methods)
            methods->destruct(data);
        construct(std::move(functor));
    }

    void reset() noexcept
    {
        if (methods)
        {
            methods->destruct(data);
            methods = nullptr;
        }
    }

    R operator()(A... args) const
    {
        return methods->invoke(data, std::forward<A>(args)...);
    }

    explicit operator bool() const noexcept
    {
        return methods != nullptr;
    }

  private:
#ifdef _MSC_VER
    template <typename T>
    void construct(T &&functor) noexcept
    {
        constexpr bool is_intern = (sizeof(Intern<T>) <= sizeof(data));
        static_assert(!INTERN_ONLY || is_intern, "Function: Intern<T> doesn't fit in data[] and INTERN_ONLY=true");
        static_assert(sizeof(Extern<T>) <= sizeof(data), "Function: Extern<T> doesn't fit in data[]");

        if (is_intern)
        {
            // store functor internally (in data)
            setup_methods_intern<T>();
            new (data) Intern<T>(std::move(functor));
        }
        else
        {
            // store functor externally (using new)
            setup_methods_extern<T>();
            new (data) Extern<T>(std::move(functor));
        }
    }
#else
    template <typename T>
    static constexpr bool is_intern()
    {
        return sizeof(Intern<T>) <= sizeof(data);
    }

    template <typename T,
              typename std::enable_if<is_intern<T>(), int>::type = 0>
    void construct(T &&functor) noexcept
    {
        // store functor internally (in data)
        setup_methods_intern<T>();
        new (data) Intern<T>(std::move(functor));
    }

    template <typename T,
              typename std::enable_if<!is_intern<T>(), int>::type = 0>
    void construct(T &&functor) noexcept
    {
        static_assert(!INTERN_ONLY, "Function: Intern<T> doesn't fit in data[] and INTERN_ONLY=true");
        static_assert(sizeof(Extern<T>) <= sizeof(data), "Function: Extern<T> doesn't fit in data[]");

        // store functor externally (using new)
        setup_methods_extern<T>();
        new (data) Extern<T>(std::move(functor));
    }
#endif

    struct Methods
    {
        R(*invoke)
        (void *, A &&...);
        void (*move)(void *, void *);
        void (*destruct)(void *);
    };

    template <typename T>
    void setup_methods_intern()
    {
        static const struct Methods m = {
            &Intern<T>::invoke,
            &Intern<T>::move,
            &Intern<T>::destruct,
        };
        methods = &m;
    }

    template <typename T>
    void setup_methods_extern()
    {
        static const struct Methods m = {
            &Extern<T>::invoke,
            &Extern<T>::move,
            &Extern<T>::destruct,
        };
        methods = &m;
    }

    // store functor internally (in data)
    template <typename T>
    class Intern
    {
      public:
        Intern(T &&functor) noexcept
            : functor_(std::move(functor))
        {
        }

        static R invoke(void *ptr, A &&...args)
        {
            Intern *self = reinterpret_cast<Intern *>(ptr);
            return self->functor_(std::forward<A>(args)...);
        }

        static void move(void *dest, void *src)
        {
            Intern *s = reinterpret_cast<Intern *>(src);
            new (dest) Intern(std::move(*s));
        }

        static void destruct(void *ptr)
        {
            Intern *self = reinterpret_cast<Intern *>(ptr);
            self->~Intern();
        }

      private:
        T functor_;
    };

    // store functor externally (using new)
    template <typename T>
    class Extern
    {
      public:
        Extern(T &&functor) noexcept
            : functor_(new T(std::move(functor)))
        {
        }

        static R invoke(void *ptr, A &&...args)
        {
            Extern *self = reinterpret_cast<Extern *>(ptr);
            return (*self->functor_)(std::forward<A>(args)...);
        }

        static void move(void *dest, void *src)
        {
            Extern *d = reinterpret_cast<Extern *>(dest);
            Extern *s = reinterpret_cast<Extern *>(src);
            d->functor_ = s->functor_;
            // no need to set s->functor_=nullptr because parent will not destruct src after move
        }

        static void destruct(void *ptr)
        {
            Extern *self = reinterpret_cast<Extern *>(ptr);
            delete self->functor_;
        }

      private:
        T *functor_;
    };

    const Methods *methods;
    mutable void *data[N];
};
} // namespace openvpn

#endif
