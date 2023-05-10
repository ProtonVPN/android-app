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
    virtual std::streamsize showmanyc();
    virtual std::streamsize xsgetn(char* s, std::streamsize n);
    virtual int underflow();
    virtual int uflow();
    virtual int pbackfail(int c = EOF);
#endif

    // output
    virtual std::streamsize xsputn(const char *s, std::streamsize n)
    {
        buf.write((unsigned char *)s, (size_t)n);
        return n;
    }

    virtual int overflow(int c = EOF)
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
