//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2017-2018 OpenVPN Technologies, Inc.
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License Version 3
//    as published by the Free Software Foundation.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program in the COPYING file.
//    If not, see <http://www.gnu.org/licenses/>.

// Wrap the OpenSSL PEM API defined in <openssl/pem.h> so
// that it can be used as part of the crypto layer of the OpenVPN core.

#ifndef OPENVPN_OPENSSL_UTIL_PEM_H
#define OPENVPN_OPENSSL_UTIL_PEM_H

#include <openvpn/openssl/util/error.hpp>

#include <openssl/pem.h>

namespace openvpn {
class OpenSSLPEM
{
  public:
    static bool pem_encode(BufferAllocated &dst,
                           const unsigned char *src,
                           size_t src_len,
                           const std::string &key_name)
    {
        bool ret = false;
        BIO *bio = BIO_new(BIO_s_mem());
        if (!bio)
            return false;

        if (!PEM_write_bio(bio, key_name.c_str(), "", src, src_len))
            goto out;

        BUF_MEM *bptr;
        BIO_get_mem_ptr(bio, &bptr);
        dst.write((unsigned char *)bptr->data, bptr->length);

        ret = true;

    out:
        if (!BIO_free(bio))
            ret = false;

        return ret;
    }

    static bool pem_decode(BufferAllocated &dst,
                           const char *src,
                           size_t src_len,
                           const std::string &key_name)
    {
        bool ret = false;
        BIO *bio;

        if (!(bio = BIO_new_mem_buf(src, src_len)))
            throw OpenSSLException("Cannot open memory BIO for PEM decode");

        char *name_read = NULL;
        char *header_read = NULL;
        uint8_t *data_read = NULL;
        long data_read_len = 0;
        if (!PEM_read_bio(bio,
                          &name_read,
                          &header_read,
                          &data_read,
                          &data_read_len))
        {
            OPENVPN_LOG("PEM decode failed");
            goto out;
        }

        if (key_name.compare(std::string(name_read)))
        {
            OPENVPN_LOG("unexpected PEM name (got '" << name_read << "', expected '" << key_name << "')");
            goto out;
        }

        dst.write(data_read, data_read_len);

        ret = true;
    out:
        OPENSSL_free(name_read);
        OPENSSL_free(header_read);
        OPENSSL_free(data_read);

        if (!BIO_free(bio))
            ret = false;

        return ret;
    }
};
}; // namespace openvpn

#endif /* OPENVPN_OPENSSL_UTIL_PEM_H */
