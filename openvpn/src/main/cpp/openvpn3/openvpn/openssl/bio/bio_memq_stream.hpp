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

// This code implements an OpenSSL BIO object for streams based on the
// MemQ buffer queue object.

#pragma once

#include <cstring> // for std::strlen and others

#include <openssl/err.h>
#include <openssl/bio.h>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/frame/frame.hpp>
#include <openvpn/frame/memq_stream.hpp>

#include <openvpn/openssl/compat.hpp>

namespace openvpn {
namespace bmq_stream {

class MemQ : public MemQStream
{
  public:
    MemQ()
    {
    }

    long ctrl(BIO *b, int cmd, long num, void *ptr)
    {
        long ret = 1;

        switch (cmd)
        {
        case BIO_CTRL_RESET:
            clear();
            break;
        case BIO_CTRL_EOF:
            ret = (long)empty();
            break;
        case BIO_C_SET_BUF_MEM_EOF_RETURN:
            return_eof_on_empty = (num == 0);
            break;
        case BIO_CTRL_GET_CLOSE:
            ret = BIO_get_shutdown(b);
            break;
        case BIO_CTRL_SET_CLOSE:
            BIO_set_shutdown(b, (int)num);
            break;
        case BIO_CTRL_WPENDING:
            ret = 0L;
            break;
        case BIO_CTRL_PENDING:
            ret = (long)pending();
            break;
        case BIO_CTRL_DUP:
        case BIO_CTRL_FLUSH:
            ret = 1;
            break;
        default:
            // OPENVPN_LOG("*** MemQ-stream unimplemented ctrl method=" << cmd);
            ret = 0;
            break;
        }
        return (ret);
    }

    bool return_eof_on_empty = false;
};

class bio_memq_internal
{
  public:
    static int memq_method_type;
    static BIO_METHOD *memq_method;


    static inline int memq_new(BIO *b)
    {
        MemQ *bmq = new (std::nothrow) MemQ();
        if (!bmq)
            return 0;
        BIO_set_shutdown(b, 1);
        BIO_set_init(b, 1);
        BIO_set_data(b, (void *)bmq);
        return 1;
    }

    static inline int memq_free(BIO *b)
    {
        if (b == nullptr)
            return (0);
        if (BIO_get_shutdown(b))
        {
            MemQ *bmq = (MemQ *)(BIO_get_data(b));
            if (BIO_get_init(b) && (bmq != nullptr))
            {
                delete bmq;
                BIO_set_data(b, nullptr);
            }
        }
        return 1;
    }

    static inline int memq_write(BIO *b, const char *in, int len)
    {
        MemQ *bmq = (MemQ *)(BIO_get_data(b));
        if (in)
        {
            BIO_clear_retry_flags(b);
            try
            {
                if (len)
                    bmq->write((const unsigned char *)in, (size_t)len);
                return len;
            }
            catch (...)
            {
                BIOerr(BIO_F_MEM_WRITE, BIO_R_INVALID_ARGUMENT);
                return -1;
            }
        }
        else
        {
            BIOerr(BIO_F_MEM_WRITE, BIO_R_NULL_PARAMETER);
            return -1;
        }
    }

    static inline int memq_read(BIO *b, char *out, int size)
    {
        MemQ *bmq = (MemQ *)(BIO_get_data(b));
        int ret = -1;
        BIO_clear_retry_flags(b);
        if (!bmq->empty())
        {
            try
            {
                ret = (int)bmq->read((unsigned char *)out, (size_t)size);
            }
            catch (...)
            {
                BIOerr(memq_method_type, BIO_R_INVALID_ARGUMENT);
                return -1;
            }
        }
        else
        {
            if (!bmq->return_eof_on_empty)
                BIO_set_retry_read(b);
        }
        return ret;
    }

    static inline long memq_ctrl(BIO *b, int cmd, long arg1, void *arg2)
    {
        MemQ *bmq = (MemQ *)(BIO_get_data(b));
        return bmq->ctrl(b, cmd, arg1, arg2);
    }

    static inline int memq_puts(BIO *b, const char *str)
    {
        const int len = std::strlen(str);
        const int ret = memq_write(b, str, len);
        return ret;
    }

    static inline void init_static()
    {
        memq_method_type = BIO_get_new_index();
        memq_method = BIO_meth_new(memq_method_type, "stream memory queue");
        BIO_meth_set_write(memq_method, memq_write);
        BIO_meth_set_read(memq_method, memq_read);
        BIO_meth_set_puts(memq_method, memq_puts);
        BIO_meth_set_create(memq_method, memq_new);
        BIO_meth_set_destroy(memq_method, memq_free);
        BIO_meth_set_gets(memq_method, nullptr);
        BIO_meth_set_ctrl(memq_method, memq_ctrl);
    }

    static inline void free_bio_method()
    {
        BIO_meth_free(memq_method);
        memq_method = nullptr;
    }
}; // class bio_memq_internal

#if defined(OPENVPN_NO_EXTERN)
int bio_memq_internal::memq_method_type = -1;
BIO_METHOD *bio_memq_internal::memq_method = nullptr;
#endif

inline void init_static()
{
    bio_memq_internal::init_static();
}

inline BIO_METHOD *BIO_s_memq(void)
{
    return (bio_memq_internal::memq_method);
}

inline MemQ *memq_from_bio(BIO *b)
{
    if (BIO_method_type(b) == bio_memq_internal::memq_method_type)
        return (MemQ *)(BIO_get_data(b));
    else
        return nullptr;
}

inline const MemQ *const_memq_from_bio(const BIO *b)
{
    if (BIO_method_type(b) == bio_memq_internal::memq_method_type)
        return (const MemQ *)(BIO_get_data(const_cast<BIO *>(b)));
    else
        return nullptr;
}
} // namespace bmq_stream
} // namespace openvpn
