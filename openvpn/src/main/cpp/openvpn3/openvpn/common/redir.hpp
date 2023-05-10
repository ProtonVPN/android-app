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

#ifndef OPENVPN_COMMON_REDIR_H
#define OPENVPN_COMMON_REDIR_H

#include <fcntl.h>
#include <unistd.h>
#include <cstring>
#include <sys/types.h>
#include <sys/stat.h>
#include <errno.h>

#include <string>
#include <utility>
#include <memory>
#include <algorithm>

#include <openvpn/io/io.hpp>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/scoped_fd.hpp>
#include <openvpn/common/tempfile.hpp>
#include <openvpn/common/pipe.hpp>
#include <openvpn/common/strerror.hpp>

namespace openvpn {

struct RedirectBase
{
    OPENVPN_EXCEPTION(redirect_std_err);
    virtual void redirect() = 0;
    virtual void close() = 0;
    virtual ~RedirectBase()
    {
    }
};

struct RedirectStdFD : public RedirectBase
{
    virtual void redirect() noexcept override
    {
        // stdin
        if (in.defined())
        {
            ::dup2(in(), 0);
            if (in() <= 2)
                in.release();
        }

        // stdout
        if (out.defined())
        {
            ::dup2(out(), 1);
            if (!err.defined() && combine_out_err)
                ::dup2(out(), 2);
            if (out() <= 2)
                out.release();
        }

        // stderr
        if (err.defined())
        {
            ::dup2(err(), 2);
            if (err() <= 2)
                err.release();
        }

        close();
    }

    virtual void close() override
    {
        in.close();
        out.close();
        err.close();
    }

    ScopedFD in;
    ScopedFD out;
    ScopedFD err;
    bool combine_out_err = false;
};

class RedirectNull : public RedirectStdFD
{
  public:
    RedirectNull()
    {
        // open /dev/null for stdin
        in.reset(::open("/dev/null", O_RDONLY, 0));
        if (!in.defined())
        {
            const int eno = errno;
            OPENVPN_THROW(redirect_std_err, "RedirectNull: error opening /dev/null for input : " << strerror_str(eno));
        }

        // open /dev/null for stdout
        out.reset(::open("/dev/null", O_RDWR, 0));
        if (!out.defined())
        {
            const int eno = errno;
            OPENVPN_THROW(redirect_std_err, "RedirectNull: error opening /dev/null for output : " << strerror_str(eno));
        }
        combine_out_err = true;
    }
};

class RedirectStd : public RedirectStdFD
{
  public:
    // flags shortcuts
    static constexpr int FLAGS_OVERWRITE = O_CREAT | O_WRONLY | O_TRUNC;
    static constexpr int FLAGS_APPEND = O_CREAT | O_WRONLY | O_APPEND;
    static constexpr int FLAGS_MUST_NOT_EXIST = O_CREAT | O_WRONLY | O_EXCL;

    // mode shortcuts
    static constexpr mode_t MODE_ALL = 0777;
    static constexpr mode_t MODE_USER_GROUP = S_IRUSR | S_IWUSR | S_IRGRP | S_IWGRP;
    static constexpr mode_t MODE_USER = S_IRUSR | S_IWUSR;

    RedirectStd(const std::string &in_fn,
                const std::string &out_fn,
                const int out_flags = FLAGS_OVERWRITE,
                const mode_t out_mode = MODE_ALL,
                const bool combine_out_err_arg = true)
    {
        if (!in_fn.empty())
            open_input(in_fn);
        open_output(out_fn, out_flags, out_mode);
        combine_out_err = combine_out_err_arg;
    }

  protected:
    RedirectStd()
    {
    }

    void open_input(const std::string &fn)
    {
        // open input file for stdin
        in.reset(::open(fn.c_str(), O_RDONLY, 0));
        if (!in.defined())
        {
            const int eno = errno;
            OPENVPN_THROW(redirect_std_err, "error opening input file: " << fn << " : " << strerror_str(eno));
        }
    }

    void open_output(const std::string &fn,
                     const int flags,
                     const mode_t mode)
    {
        // open output file for stdout/stderr
        out.reset(::open(fn.c_str(),
                         flags,
                         mode));
        if (!out.defined())
        {
            const int eno = errno;
            OPENVPN_THROW(redirect_std_err, "error opening output file: " << fn << " : " << strerror_str(eno));
        }
    }
};

class RedirectTemp : public RedirectStd
{
  public:
    RedirectTemp(const std::string &stdin_fn,
                 TempFile &stdout_temp,
                 const bool combine_out_err_arg)
    {
        open_input(stdin_fn);
        out = std::move(stdout_temp.fd);
        combine_out_err = combine_out_err_arg;
    }

    RedirectTemp(const std::string &stdin_fn,
                 TempFile &stdout_temp,
                 TempFile &stderr_temp)
    {
        open_input(stdin_fn);
        out = std::move(stdout_temp.fd);
        err = std::move(stderr_temp.fd);
    }
};

class RedirectPipe : public RedirectStdFD
{
  public:
    enum
    {
        COMBINE_OUT_ERR = (1 << 0), // capture combined stdout/stderr using a pipe
        ENABLE_IN = (1 << 1),       // make a string -> stdin pipe, otherwise redirect stdin from /dev/null
        IGNORE_IN = (1 << 2),       // don't touch stdin
        IGNORE_OUT = (1 << 3),      // don't touch stdout
        IGNORE_ERR = (1 << 4),      // don't touch stderr
    };

    struct InOut
    {
        std::string in;
        std::string out;
        std::string err;
    };

    RedirectPipe()
    {
    }

    RedirectPipe(RedirectStdFD &remote,
                 const unsigned int flags_arg)
        : flags(flags_arg)
    {
        // stdout
        if (!(flags & IGNORE_OUT))
        {
            int fd[2];
            Pipe::make_pipe(fd);
            out.reset(cloexec(fd[0]));
            remote.out.reset(fd[1]);
        }

        // stderr
        if (!(flags & IGNORE_ERR))
        {
            combine_out_err = remote.combine_out_err = ((flags & (COMBINE_OUT_ERR | IGNORE_OUT)) == COMBINE_OUT_ERR);
            if (!combine_out_err)
            {
                int fd[2];
                Pipe::make_pipe(fd);
                err.reset(cloexec(fd[0]));
                remote.err.reset(fd[1]);
            }
        }

        // stdin
        if (!(flags & IGNORE_IN))
        {
            if (flags & ENABLE_IN)
            {
                int fd[2];
                Pipe::make_pipe(fd);
                in.reset(cloexec(fd[1]));
                remote.in.reset(fd[0]);
            }
            else
            {
                // open /dev/null for stdin
                remote.in.reset(::open("/dev/null", O_RDONLY, 0));
                if (!remote.in.defined())
                {
                    const int eno = errno;
                    OPENVPN_THROW(redirect_std_err, "error opening /dev/null : " << strerror_str(eno));
                }
            }
        }
    }

    void transact(InOut &inout)
    {
        openvpn_io::io_context io_context(1);

        std::unique_ptr<Pipe::SD_OUT> send_in;
        std::unique_ptr<Pipe::SD_IN> recv_out;
        std::unique_ptr<Pipe::SD_IN> recv_err;

        if (!(flags & IGNORE_IN))
            send_in.reset(new Pipe::SD_OUT(io_context, inout.in, in));
        if (!(flags & IGNORE_OUT))
            recv_out.reset(new Pipe::SD_IN(io_context, out));
        if (!(flags & IGNORE_ERR))
            recv_err.reset(new Pipe::SD_IN(io_context, err));

        io_context.run();

        if (recv_out)
            inout.out = recv_out->content();
        if (recv_err)
            inout.err = recv_err->content();
    }

  private:
    // set FD_CLOEXEC to prevent fd from being passed across execs
    static int cloexec(const int fd)
    {
        if (::fcntl(fd, F_SETFD, FD_CLOEXEC) < 0)
        {
            const int eno = errno;
            OPENVPN_THROW(redirect_std_err, "error setting FD_CLOEXEC on pipe : " << strerror_str(eno));
        }
        return fd;
    }

    const unsigned int flags = 0;
};
} // namespace openvpn

#endif
