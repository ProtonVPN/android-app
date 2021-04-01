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

// This code implements an OpenSSL BIO object for datagrams based on the
// MemQ buffer queue object.

#ifndef OPENVPN_OPENSSL_BIO_BIO_MEMQ_DGRAM_H
#define OPENVPN_OPENSSL_BIO_BIO_MEMQ_DGRAM_H

#include <cstring> // for std::strlen

#include <openssl/err.h>
#include <openssl/bio.h>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/frame/frame.hpp>
#include <openvpn/frame/memq_dgram.hpp>

namespace openvpn {
  namespace bmq_dgram {

    class MemQ : public MemQDgram {
    public:
      MemQ()
      {
	mtu = 0;
	query_mtu_return = 0;
	std::memset(&next_timeout, 0, sizeof(next_timeout));
      }

      void set_mtu(long mtu) { query_mtu_return = mtu; }
      const struct timeval *get_next_timeout(void) const { return &next_timeout; }

      long ctrl (BIO *b, int cmd, long num, void *ptr)
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
	    ret = (long)(BIO_get_shutdown (b));
	    break;
	  case BIO_CTRL_SET_CLOSE:
	    BIO_set_shutdown (b, (int)num);
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
	  case BIO_CTRL_DGRAM_QUERY_MTU:
	    ret = mtu = query_mtu_return;
	    break;
	  case BIO_CTRL_DGRAM_GET_MTU:
	    ret = mtu;
	    break;
	  case BIO_CTRL_DGRAM_SET_MTU:
	    ret = mtu = num;
	    break;
	  case BIO_CTRL_DGRAM_SET_NEXT_TIMEOUT:
	    std::memcpy(&next_timeout, ptr, sizeof(struct timeval));
	    break;
	  default:
	    //OPENVPN_LOG("*** MemQ-dgram unimplemented ctrl method=" << cmd);
	    ret = 0;
	    break;
	  }
	return (ret);
      }

    private:
      long mtu;
      long query_mtu_return;
      bool return_eof_on_empty;
      struct timeval next_timeout;
    };

    namespace bio_memq_internal {
      static int memq_method_type=0;
      static BIO_METHOD* memq_method = nullptr;

      inline int memq_new (BIO *b)
      {
	MemQ *bmq = new(std::nothrow) MemQ();
	if (!bmq)
	  return 0;
	BIO_set_shutdown(b, 1);
	BIO_set_init(b, 1);
	b->return_eof_on_empty = false;
	BIO_set_data(b, (void *)bmq);
	return 1;
      }

      inline int memq_free (BIO *b)
      {
	if (b == nullptr)
	  return (0);
	if (BIO_get_shutdown (b))
	  {
	    MemQ *bmq = (MemQ*) (BIO_get_data (b));
	    if (BIO_get_init (b) && (bmq != nullptr))
	      {
		delete bmq;
		BIO_set_data (b, nullptr);
	      }
	  }
	return 1;
      }

      inline int memq_write (BIO *b, const char *in, int len)
      {
	MemQ *bmq = (MemQ*) (BIO_get_data (b));
	if (in)
	  {
	    BIO_clear_retry_flags (b);
	    try {
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

      inline int memq_read (BIO *b, char *out, int size)
      {
	MemQ *bmq = (MemQ*) (BIO_get_data (b));
	int ret = -1;
	BIO_clear_retry_flags (b);
	if (!bmq->empty())
	  {
	    try {
	      ret = (int)bmq->read((unsigned char *)out, (size_t)size);
	    }
	    catch (...)
	      {
		BIOerr(BIO_F_MEM_READ, BIO_R_INVALID_ARGUMENT);
		return -1;
	      }
	  }
	else
	  {
	    ret = b->num;
	    if (ret != 0)
	      BIO_set_retry_read (b);
	  }
	return ret;
      }

      inline long memq_ctrl (BIO *b, int cmd, long arg1, void *arg2)
      {
	MemQ *bmq = (MemQ*) (BIO_get_data (b));
	return bmq->ctrl(b, cmd, arg1, arg2);
      }

      inline int memq_puts (BIO *b, const char *str)
      {
	const int len = std::strlen (str);
	const int ret = memq_write (b, str, len);
	return ret;
      }

      inline void create_bio_method ()
      {
	if (!memq_method_type)
          memq_method_type = BIO_get_new_index ();

	memq_method = BIO_meth_new (memq_method_type, "datagram memory queue");
	BIO_meth_set_write (memq_method, memq_write);
	BIO_meth_set_read (memq_read);
	BIO_meth_set_puts (memq_puts);
	BIO_meth_set_create (memq_new);
	BIO_meth_set_destroy (memq_destroy);
	BIO_meth_set_gets (nullptr);
	BIO_meth_set_ctrl (memq_method, memq_ctrl);
      }

      inline void free_bio_method()
      {
	BIO_meth_free (memq_method);
      }
    } // namespace bio_memq_internal

    inline BIO_METHOD *BIO_s_memq(void)
    {
      // TODO: call free in some cleanup
      bio_memq_internal::create_bio_method ();
      return bio_memq_internal::memq_method;
    }

    inline MemQ *memq_from_bio(BIO *b)
    {
      if (BIO_method_type (b) == bio_memq_internal::memq_method_type)
	return (MemQ *)(BIO_get_data (b));
      else
	return nullptr;
    }

    inline const MemQ *const_memq_from_bio(const BIO *b)
    {
      if (BIO_method_type (b) == bio_memq_internal::memq_method_type)
        return (const MemQ *)(BIO_get_data (const_cast<BIO*>(b)));
      else
	return nullptr;
    }

    MemQ()
    {
      mtu = 0;
      query_mtu_return = 0;
      std::memset(&next_timeout, 0, sizeof(next_timeout));

      bio_memq_internal::create_bio_method ();
    }

    ~MemQ() { bio_memq_internal::free_bio_method(); }
  } // namespace bmq_dgram
} // namespace openvpn

#endif // OPENVPN_OPENSSL_BIO_BIO_MEMQ_DGRAM_H
