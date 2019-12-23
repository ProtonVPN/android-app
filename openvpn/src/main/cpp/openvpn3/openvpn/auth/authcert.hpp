//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2017 OpenVPN Inc.
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

#ifndef OPENVPN_AUTH_AUTHCERT_H
#define OPENVPN_AUTH_AUTHCERT_H

#include <string>
#include <vector>
#include <sstream>
#include <cstring>
#include <memory>
#include <utility>

#include <openvpn/common/rc.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/hexstr.hpp>
#include <openvpn/common/binprefix.hpp>
#include <openvpn/common/to_string.hpp>
#include <openvpn/pki/x509track.hpp>

namespace openvpn {

    class OpenSSLContext;
    class MbedTLSContext;

    struct AuthCert : public RC<thread_unsafe_refcount>
    {
      // AuthCert needs to friend SSL implementation classes
      friend class OpenSSLContext;
      friend class MbedTLSContext;

      typedef RCPtr<AuthCert> Ptr;

      class Fail
      {
      public:
	// ordered by priority
	enum Type {
	  OK=0, // OK MUST be 0
	  OTHER,
	  BAD_CERT_TYPE,
	  EXPIRED,
	  N
	};

	void add_fail(const size_t depth, const Type new_code, const char *reason)
	{
	  if (new_code > code)
	    code = new_code;
	  while (errors.size() <= depth)
	    errors.emplace_back();
	  std::string& err = errors[depth];
	  if (err.empty())
	    err = reason;
	  else if (err.find(reason) == std::string::npos)
	    {
	      err += ", ";
	      err += reason;
	    }
	}

	bool is_fail() const
	{
	  return code != OK;
	}

	Type get_code() const
	{
	  return code;
	}

	std::string to_string(const bool use_prefix) const
	{
	  std::string ret;
	  if (use_prefix)
	    {
	      ret += render_code(code);
	      ret += ": ";
	    }
	  bool notfirst = false;
	  for (size_t i = 0; i < errors.size(); ++i)
	    {
	      if (errors[i].empty())
		continue;
	      if (notfirst)
		ret += ", ";
	      notfirst = true;
	      ret += errors[i];
	      ret += " [";
	      ret += openvpn::to_string(i);
	      ret += ']';
	    }
	  return ret;
	}

	static const char *render_code(const Type code)
	{
	  switch (code)
	    {
	    case OK:
	      return "OK";
	    case OTHER:
	    default:
	      return "CERT_FAIL";
	    case BAD_CERT_TYPE:
	      return "BAD_CERT_TYPE";
	    case EXPIRED:
	      return "EXPIRED";
	    }
	}

      private:
	Type code{OK};                    // highest-valued cert fail code
	std::vector<std::string> errors;  // human-readable cert errors by depth
      };

      AuthCert()
      {
	std::memset(issuer_fp, 0, sizeof(issuer_fp));
	sn = -1;
      }

      bool defined() const
      {
	return sn >= 0;
      }

      bool cn_defined() const
      {
	return !cn.empty();
      }

      template <typename T>
      T issuer_fp_prefix() const
      {
	return bin_prefix<T>(issuer_fp);
      }

      bool operator==(const AuthCert& other) const
      {
	return cn == other.cn && sn == other.sn && !std::memcmp(issuer_fp, other.issuer_fp, sizeof(issuer_fp));
      }

      bool operator!=(const AuthCert& other) const
      {
	return !operator==(other);
      }

      std::string to_string() const
      {
	std::ostringstream os;
	os << "CN=" << cn
	   << " SN=" << sn
	   << " ISSUER_FP=" << issuer_fp_str(false);
	return os.str();
      }

      std::string issuer_fp_str(const bool openssl_fmt) const
      {
	if (openssl_fmt)
	  return render_hex_sep(issuer_fp, sizeof(issuer_fp), ':', true);
	else
	  return render_hex(issuer_fp, sizeof(issuer_fp), false);
      }

      std::string normalize_cn() const // remove trailing "_AUTOLOGIN" from AS certs
      {
	if (string::ends_with(cn, "_AUTOLOGIN"))
	  return cn.substr(0, cn.length() - 10);
	else
	  return cn;
      }

      const std::string& get_cn() const
      {
	return cn;
      }

      long get_sn() const
      {
	return sn;
      }

      const X509Track::Set* x509_track_get() const
      {
	return x509_track.get();
      }

      std::unique_ptr<X509Track::Set> x509_track_take_ownership()
      {
	return std::move(x509_track);
      }

      void add_fail(const size_t depth, const Fail::Type new_code, const char *reason)
      {
	if (!fail)
	  fail.reset(new Fail());
	fail->add_fail(depth, new_code, reason);
      }

      bool is_fail() const
      {
	return fail && fail->is_fail();
      }

      const Fail* get_fail() const
      {
	return fail.get();
      }

    private:
      std::string cn;                // common name
      long sn;                       // serial number
      unsigned char issuer_fp[20];   // issuer cert fingerprint

      std::unique_ptr<Fail> fail;
      std::unique_ptr<X509Track::Set> x509_track;
    };
}

#endif
