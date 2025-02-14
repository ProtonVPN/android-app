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

// Server-side code to fragment an oversized options buffer
// into multiple buffers using the push-continuation option.

#pragma once

#include <string>
#include <vector>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/lex.hpp>
#include <openvpn/buffer/bufstr.hpp>
#include <openvpn/options/pushlex.hpp>

namespace openvpn {

// Fragment a long PUSH_REPLY/PUSH_UPDATE buffer into
// multiple buffers using the push-continuation option.
class PushContinuationFragment : public std::vector<BufferPtr>
{
  public:
    // maximum allowable fragment size (excluding null termination
    // that will be appended by push_reply() in servproto.hpp).
    static constexpr size_t FRAGMENT_SIZE = 1023;

    OPENVPN_EXCEPTION(push_continuation_fragment_error);

    static bool should_fragment(const ConstBuffer &buf)
    {
        return buf.size() > FRAGMENT_SIZE;
    }

    // prefix should be PUSH_REPLY or PUSH_UPDATE
    PushContinuationFragment(const ConstBuffer &buf, const std::string &prefix)
    {
        // size of ",push-continuation n"
        const size_t push_continuation_len = 20;

        // loop over options
        bool did_continuation = false;
        PushLex lex(buf, true);
        while (lex.defined())
        {
            // get escaped opt
            const std::string escaped_opt = lex.next();

            // create first buffer on loop startup
            if (empty())
                append_new_buffer(prefix);

            // ready to finalize this outbut buffer and move on to next?
            // (the +1 is for escaped_opt comma)
            if (back()->size() + escaped_opt.size() + push_continuation_len + 1 > FRAGMENT_SIZE)
            {
                did_continuation = true;
                append_push_continuation(*back(), false);
                append_new_buffer(prefix);
            }

            back()->push_back(',');
            buf_append_string(*back(), escaped_opt);
        }

        // push final push-continuation
        if (!empty() && did_continuation)
            append_push_continuation(*back(), true);
    }

    // prefix should be PUSH_REPLY or PUSH_UPDATE
    static BufferPtr defragment(const std::vector<BufferPtr> &bv,
                                const std::string &prefix)
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
        auto ret = BufferAllocatedRc::Create(total_size, 0);
        buf_append_string(*ret, prefix);

        // terminators
        static const char pc1[] = ",push-continuation 1";
        static const char pc2[] = ",push-continuation 2";

        // build return buffer
        const std::string prefix_comma = prefix + ',';
        const size_t size = bv.size();
        for (size_t i = 0; i < size; ++i)
        {
            const Buffer &buf = *bv[i];
            const char *pc = (i == size - 1) ? pc1 : pc2;
            if (string::starts_with(buf, prefix_comma) && string::ends_with(buf, pc))
            {
                Buffer b = buf;
                b.advance(prefix.size());  // advance past prefix
                b.set_size(b.size() - 20); // truncate ",push-continuation n"
                ret->append(b);
            }
            else
                throw push_continuation_fragment_error("badly formatted fragments");
        }
        return ret;
    }

  private:
    // create a new PUSH_REPLY/PUSH_UPDATE buffer
    void append_new_buffer(const std::string &prefix)
    {
        // include extra byte for null termination
        auto bp = BufferAllocatedRc::Create(FRAGMENT_SIZE + 1, 0);
        buf_append_string(*bp, prefix);
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
