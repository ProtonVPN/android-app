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

#ifndef OPENVPN_AUTH_AUTHCERT_H
#define OPENVPN_AUTH_AUTHCERT_H

#include <string>
#include <vector>
#include <sstream>
#include <cstring>
#include <cstdint>
#include <memory>
#include <utility>

#include <openvpn/common/rc.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/hexstr.hpp>
#include <openvpn/common/binprefix.hpp>
#include <openvpn/common/to_string.hpp>
#include <openvpn/pki/x509track.hpp>
#include <openvpn/ssl/sni_metadata.hpp>

namespace openvpn {

    class OpenSSLContext;
    class MbedTLSContext;

    class AuthCert : public RC<thread_unsafe_refcount>
    {
    public:
      // AuthCert needs to friend SSL implementation classes
      friend class OpenSSLContext;
      friend class MbedTLSContext;

      typedef RCPtr<AuthCert> Ptr;

      class Fail
      {
      public:
	// Ordered by severity.  If many errors are present, the
	// most severe error will be returned by get_code().
	enum Type {
	  OK=0,                // OK MUST be 0
	  EXPIRED,             // less severe...
	  BAD_CERT_TYPE,
	  CERT_FAIL,
	  SNI_ERROR,           // more severe...
	  N
	};

	void add_fail(const size_t depth, const Type new_code, std::string reason)
	{
	  if (new_code > code)
	    code = new_code;
	  while (errors.size() <= depth)
	    errors.emplace_back();
	  std::string& err = errors[depth];
	  if (err.empty())
	    err = std::move(reason);
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

	static std::string render_code(const Type code)
	{
	  switch (code)
	    {
	    case OK:
	      return "OK";
	    case CERT_FAIL:
	    default:
	      return "CERT_FAIL";
	    case BAD_CERT_TYPE:
	      return "BAD_CERT_TYPE";
	    case EXPIRED:
	      return "EXPIRED";
	    case SNI_ERROR:
	      return "SNI_ERROR";
	    }
	}

      private:
	Type code{OK};                    // highest-valued cert fail code
	std::vector<std::string> errors;  // human-readable cert errors by depth
      };

      AuthCert()
      {
      }

      AuthCert(std::string cn_arg, const std::int64_t sn_arg)
	: cn(std::move(cn_arg)),
	  sn(sn_arg)
      {
      }

      bool defined() const
      {
	return sn >= 0;
      }

      bool sni_defined() const
      {
	return !sni.empty();
      }

      bool cn_defined() const
      {
	return !cn.empty();
      }

      bool is_uninitialized() const
      {
	return cn.empty() && sn < 0 && !fail;
      }

      template <typename T>
      T issuer_fp_prefix() const
      {
	return bin_prefix<T>(issuer_fp);
      }

      bool operator==(const AuthCert& other) const
      {
	return sni == other.sni && cn == other.cn && sn == other.sn && !std::memcmp(issuer_fp, other.issuer_fp, sizeof(issuer_fp));
      }

      bool operator!=(const AuthCert& other) const
      {
	return !operator==(other);
      }

      std::string to_string() const
      {
	std::ostringstream os;
	if (!sni.empty())
	  os << "SNI=" << sni << ' ';
	if (sni_metadata)
	  os << "SNI_CN=" << sni_metadata->sni_client_name(*this) << ' ';
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

      // Allow sni_metadata object, if it exists, to generate the client name.
      // Otherwise fall back to normalize_cn().
      std::string sni_client_name() const
      {
	if (sni_metadata)
	  return sni_metadata->sni_client_name(*this);
	else
	  return normalize_cn();
      }

      const std::string& get_sni() const
      {
	return sni;
      }

      const std::string& get_cn() const
      {
	return cn;
      }

      std::int64_t get_sn() const
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

      void add_fail(const size_t depth, const Fail::Type new_code, std::string reason)
      {
	if (!fail)
	  fail.reset(new Fail());
	fail->add_fail(depth, new_code, std::move(reason));
      }

      bool is_fail() const
      {
	return fail && fail->is_fail();
      }

      const Fail* get_fail() const
      {
	return fail.get();
      }

      std::string fail_str() const
      {
	if (fail)
	  return fail->to_string(true);
	else
	  return "OK";
      }

#ifndef UNIT_TEST
    private:
#endif
      std::string sni;               // SNI (server name indication)
      std::string cn;                // common name
      std::int64_t sn = -1;          // serial number

      // issuer cert fingerprint
      unsigned char issuer_fp[20] = {};

      std::unique_ptr<Fail> fail;
      std::unique_ptr<X509Track::Set> x509_track;
      SNI::Metadata::UPtr sni_metadata;
    };
}

#endif
