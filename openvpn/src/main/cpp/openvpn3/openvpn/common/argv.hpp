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

#ifndef OPENVPN_COMMON_ARGV_H
#define OPENVPN_COMMON_ARGV_H

#include <cstring> // memcpy

#include <string>
#include <vector>

namespace openvpn {

class Argv : public std::vector<std::string>
{
  public:
    Argv(const size_t capacity = 16)
    {
        reserve(capacity);
    }

    std::string to_string() const
    {
        std::string ret;
        bool first = true;
        for (const auto &s : *this)
        {
            if (!first)
                ret += ' ';
            ret += s;
            first = false;
        }
        return ret;
    }
};

class ArgvWrapper
{
  public:
    explicit ArgvWrapper(const std::vector<std::string> &argv)
    {
        size_t i;
        argc = argv.size();
        cargv = new char *[argc + 1];
        for (i = 0; i < argc; ++i)
            cargv[i] = string_alloc(argv[i]);
        cargv[i] = nullptr;
    }

    ArgvWrapper(ArgvWrapper &&rhs) noexcept
    {
        argc = rhs.argc;
        cargv = rhs.cargv;
        rhs.argc = 0;
        rhs.cargv = nullptr;
    }

    ArgvWrapper &operator=(ArgvWrapper &&rhs) noexcept
    {
        del();
        argc = rhs.argc;
        cargv = rhs.cargv;
        rhs.argc = 0;
        rhs.cargv = nullptr;
        return *this;
    }

    ~ArgvWrapper()
    {
        del();
    }

    char *const *c_argv() const noexcept
    {
        return cargv;
    }

    char **c_argv() noexcept
    {
        return cargv;
    }

    size_t c_argc() const noexcept
    {
        return argc;
    }

  private:
    ArgvWrapper(const ArgvWrapper &) = delete;
    ArgvWrapper &operator=(const ArgvWrapper &) = delete;

    static char *string_alloc(const std::string &s)
    {
        const char *sdata = s.c_str();
        const size_t slen = s.length();
        char *ret = new char[slen + 1];
        std::memcpy(ret, sdata, slen);
        ret[slen] = '\0';
        return ret;
    }

    void del()
    {
        for (size_t i = 0; i < argc; ++i)
            delete[] cargv[i];
        delete[] cargv;
    }

    size_t argc;
    char **cargv;
};

} // namespace openvpn

#endif
