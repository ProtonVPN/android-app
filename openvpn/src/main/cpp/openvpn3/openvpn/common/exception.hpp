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

// Basic exception handling.  Allow exception classes for specific errors
// to be easily defined, and allow exceptions to be thrown with a consise
// syntax that allows stringstream concatenation using <<

#ifndef OPENVPN_COMMON_EXCEPTION_H
#define OPENVPN_COMMON_EXCEPTION_H

#include <string>
#include <sstream>
#include <exception>
#include <utility>

#include <openvpn/common/stringize.hpp> // for OPENVPN_STRINGIZE
#include <openvpn/common/string.hpp>

#ifdef OPENVPN_DEBUG_EXCEPTION
// well-known preprocessor hack to get __FILE__:__LINE__ rendered as a string
#define OPENVPN_FILE_LINE "/" __FILE__ ":" OPENVPN_STRINGIZE(__LINE__)
#else
#define OPENVPN_FILE_LINE
#endif

namespace openvpn {

// string exception class, where the exception is described by a std::string
class Exception : public std::exception
{
  public:
    explicit Exception(const std::string &err) noexcept
        : err_(err)
    {
    }
    explicit Exception(std::string &&err) noexcept
        : err_(std::move(err))
    {
    }
    const char *what() const noexcept override
    {
        return err_.c_str();
    }
    const std::string &err() const noexcept
    {
        return err_;
    }
    virtual ~Exception() noexcept = default;

    void add_label(const std::string &label)
    {
        err_ = label + ": " + err_;
    }

    void remove_label(const std::string &label)
    {
        const std::string head = label + ": ";
        if (string::starts_with(err_, head))
            err_ = err_.substr(head.length());
    }

  private:
    std::string err_;
};

// define a simple custom exception class with no extra info
#define OPENVPN_SIMPLE_EXCEPTION(C)                \
    class C : public std::exception                \
    {                                              \
      public:                                      \
        const char *what() const noexcept override \
        {                                          \
            return #C OPENVPN_FILE_LINE;           \
        }                                          \
    }

// define a simple custom exception class with no extra info that inherits from a custom base
#define OPENVPN_SIMPLE_EXCEPTION_INHERIT(B, C)     \
    class C : public B                             \
    {                                              \
      public:                                      \
        C() : B(#C OPENVPN_FILE_LINE)              \
        {                                          \
        }                                          \
        const char *what() const noexcept override \
        {                                          \
            return #C OPENVPN_FILE_LINE;           \
        }                                          \
    }

// define a custom exception class that allows extra info
#define OPENVPN_EXCEPTION(C)                                                           \
    class C : public openvpn::Exception                                                \
    {                                                                                  \
      public:                                                                          \
        C() : openvpn::Exception(#C OPENVPN_FILE_LINE)                                 \
        {                                                                              \
        }                                                                              \
        C(const std::string err) : openvpn::Exception(#C OPENVPN_FILE_LINE ": " + err) \
        {                                                                              \
        }                                                                              \
    }

// define a custom exception class that allows extra info with error code
#define OPENVPN_EXCEPTION_WITH_CODE(C, DEFAULT_CODE, ...)                               \
    enum C##_##code : unsigned int{__VA_ARGS__};                                        \
    class C : public openvpn::Exception                                                 \
    {                                                                                   \
      public:                                                                           \
        C() : openvpn::Exception(#C OPENVPN_FILE_LINE)                                  \
        {                                                                               \
            add_label(#DEFAULT_CODE);                                                   \
        }                                                                               \
        C(const std::string &err) : openvpn::Exception(#C OPENVPN_FILE_LINE ": " + err) \
        {                                                                               \
            add_label(#DEFAULT_CODE);                                                   \
        }                                                                               \
        option_error(C##_##code code, const std::string &err)                           \
            : openvpn::Exception(#C OPENVPN_FILE_LINE ": " + err)                       \
        {                                                                               \
            add_label(code2string(code));                                               \
        }                                                                               \
        static std::string code2string(C##_##code code);                                \
    }
// define a custom exception class that allows extra info, but does not emit a tag
#define OPENVPN_UNTAGGED_EXCEPTION(C)                      \
    class C : public openvpn::Exception                    \
    {                                                      \
      public:                                              \
        C(const std::string err) : openvpn::Exception(err) \
        {                                                  \
        }                                                  \
    }

// define a custom exception class that allows extra info, and inherits from a custom base
#define OPENVPN_EXCEPTION_INHERIT(B, C)                               \
    class C : public B                                                \
    {                                                                 \
      public:                                                         \
        C() : B(#C OPENVPN_FILE_LINE)                                 \
        {                                                             \
        }                                                             \
        C(const std::string err) : B(#C OPENVPN_FILE_LINE ": " + err) \
        {                                                             \
        }                                                             \
    }

// define a custom exception class that allows extra info, and inherits from a custom base,
// but does not emit a tag
#define OPENVPN_UNTAGGED_EXCEPTION_INHERIT(B, C) \
    class C : public B                           \
    {                                            \
      public:                                    \
        using B::B;                              \
    }

// throw an Exception with stringstream concatenation allowed
#define OPENVPN_THROW_EXCEPTION(stuff)             \
    do                                             \
    {                                              \
        std::ostringstream _ovpn_exc;              \
        _ovpn_exc << stuff;                        \
        throw openvpn::Exception(_ovpn_exc.str()); \
    } while (0)

// throw an OPENVPN_EXCEPTION class with stringstream concatenation allowed
#define OPENVPN_THROW(exc, stuff)     \
    do                                \
    {                                 \
        std::ostringstream _ovpn_exc; \
        _ovpn_exc << stuff;           \
        throw exc(_ovpn_exc.str());   \
    } while (0)

#define OPENVPN_THROW_ARG1(exc, arg, stuff) \
    do                                      \
    {                                       \
        std::ostringstream _ovpn_exc;       \
        _ovpn_exc << stuff;                 \
        throw exc(arg, _ovpn_exc.str());    \
    } while (0)

// properly rethrow an exception that might be derived from Exception
inline void throw_ref(const std::exception &e)
{
    const Exception *ex = dynamic_cast<const Exception *>(&e);
    if (ex)
        throw *ex;
    else
        throw e;
}

} // namespace openvpn

#endif // OPENVPN_COMMON_EXCEPTION_H
