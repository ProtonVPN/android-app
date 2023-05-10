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

// Server-side code to fragment an oversized options buffer
// into multiple buffers using the push-continuation option.

#pragma once

#include <string>
#include <vector>

#include <openvpn/common/lex.hpp>
#include <openvpn/buffer/bufstr.hpp>

namespace openvpn {

// Fragment a long PUSH_REPLY buffer into multiple
// buffers using the push-continuation option.
class PushContinuationFragment : public std::vector<BufferPtr>
{
  public:
    // maximum allowable fragment size (excluding null termination
    // that will be appended by push_reply() in servproto.hpp).
    static constexpr size_t FRAGMENT_SIZE = 1023;

    OPENVPN_EXCEPTION(push_continuation_fragment_error);

    static bool should_fragment(const Buffer &buf)
    {
        return buf.size() > FRAGMENT_SIZE;
    }

    PushContinuationFragment(const Buffer &buf)
    {
        // size of ",push-continuation n"
        const size_t push_continuation_len = 20;

        // loop over options
        bool did_continuation = false;
        Lex lex(buf);
        while (lex.defined())
        {
            // get escaped opt
            const std::string escaped_opt = lex.next();

            // create first buffer on loop startup
            if (empty())
                append_new_buffer();

            // ready to finalize this outbut buffer and move on to next?
            // (the +1 is for escaped_opt comma)
            if (back()->size() + escaped_opt.size() + push_continuation_len + 1 > FRAGMENT_SIZE)
            {
                did_continuation = true;
                append_push_continuation(*back(), false);
                append_new_buffer();
            }

            back()->push_back(',');
            buf_append_string(*back(), escaped_opt);
        }

        // push final push-continuation
        if (!empty() && did_continuation)
            append_push_continuation(*back(), true);
    }

    static BufferPtr defragment(const std::vector<BufferPtr> &bv)
    {
        // exit cases where no need to defrag
        if (bv.empty())
            return BufferPtr();
        if (bv.size() == 1)
            return bv[0];

        // compute length
        size_t total_size = 0;
        for (const auto &e : bv)
            total_size += e->size();

        // allocate return buffer
        BufferPtr ret(new BufferAllocated(total_size, 0));
        buf_append_string(*ret, "PUSH_REPLY");

        // terminators
        static const char pc1[] = ",push-continuation 1";
        static const char pc2[] = ",push-continuation 2";

        // build return buffer
        const size_t size = bv.size();
        for (size_t i = 0; i < size; ++i)
        {
            const Buffer &buf = *bv[i];
            const char *pc = (i == size - 1) ? pc1 : pc2;
            if (string::starts_with(buf, "PUSH_REPLY,") && string::ends_with(buf, pc))
            {
                Buffer b = buf;
                b.advance(10);             // advance past "PUSH_REPLY"
                b.set_size(b.size() - 20); // truncate ",push-continuation n"
                ret->append(b);
            }
            else
                throw push_continuation_fragment_error("badly formatted fragments");
        }
        return ret;
    }

  private:
    class Lex
    {
      public:
        Lex(const Buffer &buf)
            : buf_(buf)
        {
            if (!string::starts_with(buf_, "PUSH_REPLY,"))
                throw push_continuation_fragment_error("not a valid PUSH_REPLY message");
            buf_.advance(11);
        }

        bool defined()
        {
            return !buf_.empty();
        }

        std::string next()
        {
            StandardLex lex;
            std::string ret;
            while (defined())
            {
                const char c = buf_.pop_front();
                lex.put(c);
                if (lex.get() == ',' && !(lex.in_quote() || lex.in_backslash()))
                    return ret;
                ret += c;
            }
            return ret;
        }

      private:
        Buffer buf_;
    };

    // create a new PUSH_REPLY buffer
    void append_new_buffer()
    {
        // include extra byte for null termination
        BufferPtr bp = new BufferAllocated(FRAGMENT_SIZE + 1, 0);
        buf_append_string(*bp, "PUSH_REPLY");
        push_back(std::move(bp));
    }

    // append a push-continuation directive to buffer
    static void append_push_continuation(Buffer &buf, bool end)
    {
        buf_append_string(buf, ",push-continuation ");
        buf.push_back(end ? '1' : '2');
    }
};
} // namespace openvpn
