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

#ifndef OPENVPN_CRYPTO_HASHSTR_H
#define OPENVPN_CRYPTO_HASHSTR_H

#include <string>

#include <openvpn/buffer/buffer.hpp>
#include <openvpn/common/hexstr.hpp>
#include <openvpn/common/base64.hpp>
#include <openvpn/crypto/digestapi.hpp>

namespace openvpn {
class HashString
{
  public:
    HashString(DigestFactory &digest_factory,
               const CryptoAlgs::Type digest_type)
        : ctx(digest_factory.new_digest(digest_type))
    {
    }

    void update(const std::string &str)
    {
        ctx->update((unsigned char *)str.c_str(), str.length());
    }

    void update(const char *str)
    {
        ctx->update((unsigned char *)str, std::strlen(str));
    }

    void update(const char c)
    {
        ctx->update((unsigned char *)&c, 1);
    }

    void update(const Buffer &buf)
    {
        ctx->update(buf.c_data(), buf.size());
    }

    BufferPtr final()
    {
        BufferPtr ret(new BufferAllocated(ctx->size(), BufferAllocated::ARRAY));
        ctx->final(ret->data());
        return ret;
    }

    void final(Buffer &output)
    {
        const size_t size = ctx->size();
        if (size > output.max_size())
            OPENVPN_BUFFER_THROW(buffer_overflow);
        ctx->final(output.data());
        output.set_size(size);
    }

    std::string final_hex()
    {
        BufferPtr bp = final();
        return render_hex_generic(*bp);
    }

    std::string final_base64()
    {
        BufferPtr bp = final();
        return base64->encode(*bp);
    }

  private:
    DigestInstance::Ptr ctx;
};
} // namespace openvpn

#endif
