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

#ifndef OPENVPN_BUFFER_BUFSTREAM_H
#define OPENVPN_BUFFER_BUFSTREAM_H

#include <streambuf>

#include <openvpn/buffer/buffer.hpp>

namespace openvpn {

class BufferStream : public std::streambuf
{
  public:
    BufferStream(Buffer &buffer)
        : buf(buffer)
    {
    }

  protected:
#if 0 // not implemented yet
    // input
    std::streamsize showmanyc() override;
    std::streamsize xsgetn(char* s, std::streamsize n) override;
    int underflow() override;
    int uflow() override;
    int pbackfail(int c = EOF) override;
#endif

    // output
    std::streamsize xsputn(const char *s, std::streamsize n) override
    {
        buf.write((unsigned char *)s, (size_t)n);
        return n;
    }

    int overflow(int c = EOF) override
    {
        if (c != EOF)
        {
            unsigned char uc = (unsigned char)c;
            buf.push_back(uc);
        }
        return c;
    }

  private:
    Buffer &buf;
};

class BufferStreamOut : public std::ostream
{
  public:
    BufferStreamOut(Buffer &buffer)
        : std::ostream(new BufferStream(buffer))
    {
    }

    ~BufferStreamOut()
    {
        delete rdbuf();
    }
};

} // namespace openvpn

#endif
