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

// A state machine to skip extraneous HTML in an
// HTTP CONNECT proxy response.

// Matches on typical HTML blocks:
//
// <!doctype html> ... </html>
// <html> ... </html>
//
// Notes:
// 1. Case insensitive
// 2. </html> can be followed by CR/LF chars

#ifndef OPENVPN_HTTP_HTMLSKIP_H
#define OPENVPN_HTTP_HTMLSKIP_H

#include <openvpn/buffer/buffer.hpp>

namespace openvpn {
  namespace HTTP {

    class HTMLSkip
    {
    public:
      enum Status {
	PENDING,
	MATCH,
	NOMATCH,
      };

      HTMLSkip()
	: state(INITIAL),
	  residual(64, 0),
	  bytes(0)
      {}

      Status add(unsigned char c)
      {
	bool retain = false;

	++bytes;
	switch (state)
	  {
	  case INITIAL:
	    retain = true;
	    if (c == '<')
	      state = O_OPEN;
	    else
	      state = FAIL;
	    break;
	  case O_OPEN:
	    retain = true;
	    if (c == '!')
	      state = O_BANG;
	    else if (c == 'h' || c == 'H')
	      state = O_HTML_H;
	    else
	      state = FAIL;
	    break;
	  case O_BANG:
	    retain = true;
	    if (c == 'd' || c == 'D')
	      state = O_DOCTYPE_D;
	    else
	      state = FAIL;
	    break;
	  case O_DOCTYPE_D:
	    retain = true;
	    if (c == 'o' || c == 'O')
	      state = O_DOCTYPE_O;
	    else
	      state = FAIL;
	    break;
	  case O_DOCTYPE_O:
	    retain = true;
	    if (c == 'c' || c == 'C')
	      state = O_DOCTYPE_C;
	    else
	      state = FAIL;
	    break;
	  case O_DOCTYPE_C:
	    retain = true;
	    if (c == 't' || c == 'T')
	      state = O_DOCTYPE_T;
	    else
	      state = FAIL;
	    break;
	  case O_DOCTYPE_T:
	    retain = true;
	    if (c == 'y' || c == 'Y')
	      state = O_DOCTYPE_Y;
	    else
	      state = FAIL;
	    break;
	  case O_DOCTYPE_Y:
	    retain = true;
	    if (c == 'p' || c == 'P')
	      state = O_DOCTYPE_P;
	    else
	      state = FAIL;
	    break;
	  case O_DOCTYPE_P:
	    retain = true;
	    if (c == 'e' || c == 'E')
	      state = O_DOCTYPE_SPACE;
	    else
	      state = FAIL;
	    break;
	  case O_DOCTYPE_SPACE:
	    retain = true;
	    if (c == ' ' || c == '\t' || c == '\r' || c == '\n')
	      break;
	    else if (c == 'h' || c == 'H')
	      state = O_HTML_H;
	    else
	      state = FAIL;
	    break;
	  case O_HTML_H:
	    retain = true;
	    if (c == 't' || c == 'T')
	      state = O_HTML_T;
	    else
	      state = FAIL;
	    break;
	  case O_HTML_T:
	    retain = true;
	    if (c == 'm' || c == 'M')
	      state = O_HTML_M;
	    else
	      state = FAIL;
	    break;
	  case O_HTML_M:
	    if (c == 'l' || c == 'L')
	      {
		state = CONTENT;
		residual.reset_content();
	      }
	    else
	      {
		state = FAIL;
		retain = true;
	      }
	    break;
	  case CONTENT:
	    if (c == '<')
	      state = C_OPEN;
	    break;
	  case C_OPEN:
	    if (c == '/')
	      state = C_SLASH;
	    else
	      state = CONTENT;
	    break;
	  case C_SLASH:
	    if (c == 'h' || c == 'H')
	      state = C_HTML_H;
	    else
	      state = CONTENT;
	    break;
	  case C_HTML_H:
	    if (c == 't' || c == 'T')
	      state = C_HTML_T;
	    else
	      state = CONTENT;
	    break;
	  case C_HTML_T:
	    if (c == 'm' || c == 'M')
	      state = C_HTML_M;
	    else
	      state = CONTENT;
	    break;
	  case C_HTML_M:
	    if (c == 'l' || c == 'L')
	      state = C_HTML_L;
	    else
	      state = CONTENT;
	    break;
	  case C_HTML_L:
	    if (c == '>')
	      state = C_CRLF;
	    else
	      state = CONTENT;
	    break;
	  case C_CRLF:
	    if (!(c == '\n' || c == '\r'))
	      {
		residual.reset_content();
		residual.push_back(c);
		state = DONE;
	      }
	    break;
	  default:
	    retain = true;
	    break;
	  }

	if (retain)
	  residual.push_back(c);

	switch (state)
	  {
	  case DONE:
	    return MATCH;
	  case FAIL:
	    return NOMATCH;
	  default:
	    return PENDING;
	  }
      }

      void get_residual(BufferAllocated& buf) const
      {
	if (residual.size() <= buf.offset())
	  {
	    buf.prepend(residual.c_data(), residual.size());
	  }
	else
	  {
	    BufferAllocated newbuf(residual.size() + buf.size(), 0);
	    newbuf.write(residual.c_data(), residual.size());
	    newbuf.write(buf.c_data(), buf.size());
	    buf.move(newbuf);
	  }
      }

      unsigned long n_bytes() const { return bytes; }

    private:
      enum State {
	DONE,
	FAIL,
	INITIAL,
	O_OPEN,
	O_BANG,
	O_DOCTYPE_D,
	O_DOCTYPE_O,
	O_DOCTYPE_C,
	O_DOCTYPE_T,
	O_DOCTYPE_Y,
	O_DOCTYPE_P,
	O_DOCTYPE_SPACE,
	O_HTML_H,
	O_HTML_T,
	O_HTML_M,
	CONTENT,
	C_OPEN,
	C_SLASH,
	C_HTML_H,
	C_HTML_T,
	C_HTML_M,
	C_HTML_L,
	C_CRLF,
      };

      State state;
      BufferAllocated residual;
      unsigned long bytes;
    };

  }
}

#endif
