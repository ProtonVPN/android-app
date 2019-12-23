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

// Wrap the Apple SSL API as defined in <Security/SecureTransport.h>
// so that it can be used as the SSL layer by the OpenVPN core.
// NOTE: not used in production code.

// Note that the Apple SSL API is missing some functionality (as of
// Mac OS X 10.8) that makes it difficult to use as a drop in replacement
// for OpenSSL or MbedTLS.  The biggest issue is that the API doesn't
// allow an SSL context to be built out of PEM-based certificates and
// keys.  It requires an "Identity" in the Keychain that was imported
// by the user as a PKCS#12 file.

#ifndef OPENVPN_APPLECRYPTO_SSL_SSLCTX_H
#define OPENVPN_APPLECRYPTO_SSL_SSLCTX_H

#include <string>

#include <Security/SecImportExport.h>
#include <Security/SecItem.h>
#include <Security/SecureTransport.h>
#include <Security/SecKey.h>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/mode.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/frame/frame.hpp>
#include <openvpn/frame/memq_stream.hpp>
#include <openvpn/pki/epkibase.hpp>
#include <openvpn/apple/cf/cfsec.hpp>
#include <openvpn/apple/cf/error.hpp>
#include <openvpn/ssl/tlsver.hpp>
#include <openvpn/ssl/sslconsts.hpp>
#include <openvpn/ssl/sslapi.hpp>

// An SSL Context is essentially a configuration that can be used
// to generate an arbitrary number of actual SSL connections objects.

// AppleSSLContext is an SSL Context implementation that uses the
// Mac/iOS SSL library as a backend.

namespace openvpn {

  // Represents an SSL configuration that can be used
  // to instantiate actual SSL sessions.
  class AppleSSLContext : public SSLFactoryAPI
  {
  public:
    typedef RCPtr<AppleSSLContext> Ptr;

    enum {
      MAX_CIPHERTEXT_IN = 64
    };

    // The data needed to construct an AppleSSLContext.
    class Config : public SSLConfigAPI
    {
      friend class AppleSSLContext;

    public:
      typedef RCPtr<Config> Ptr;

      Config() {}

      void load_identity(const std::string& subject_match)
      {
	identity = load_identity_(subject_match);
	if (!identity())
	  OPENVPN_THROW(ssl_context_error, "AppleSSLContext: identity '" << subject_match << "' undefined");
      }

      virtual SSLFactoryAPI::Ptr new_factory()
      {
	return SSLFactoryAPI::Ptr(new AppleSSLContext(this));
      }

      virtual void set_mode(const Mode& mode_arg)
      {
	mode = mode_arg;
      }

      virtual const Mode& get_mode() const
      {
	return mode;
      }

      virtual void set_frame(const Frame::Ptr& frame_arg)
      {
	frame = frame_arg;
      }

      virtual void load(const OptionList& opt, const unsigned int lflags)
      {
	// client/server
	if (lflags & LF_PARSE_MODE)
	  mode = opt.exists("client") ? Mode(Mode::CLIENT) : Mode(Mode::SERVER);

	// identity
	{
	  const std::string& subject_match = opt.get("identity", 1, 256);
	  load_identity(subject_match);
	}
      }

      virtual void set_external_pki_callback(ExternalPKIBase* external_pki_arg)
      {
	not_implemented("set_external_pki_callback");
      }

      virtual void set_private_key_password(const std::string& pwd)
      {
	return not_implemented("set_private_key_password");
      }

      virtual void load_ca(const std::string& ca_txt, bool strict)
      {
	return not_implemented("load_ca");
      }

      virtual void load_crl(const std::string& crl_txt)
      {
	return not_implemented("load_crl");
      }

      virtual void load_cert(const std::string& cert_txt)
      {
	return not_implemented("load_cert");
      }

      virtual void load_cert(const std::string& cert_txt, const std::string& extra_certs_txt)
      {
	return not_implemented("load_cert");
      }

      virtual void load_private_key(const std::string& key_txt)
      {
	return not_implemented("load_private_key");
      }

      virtual void load_dh(const std::string& dh_txt)
      {
	return not_implemented("load_dh");
      }

      virtual void set_debug_level(const int debug_level)
      {
	return not_implemented("set_debug_level");
      }

      virtual void set_flags(const unsigned int flags_arg)
      {
	return not_implemented("set_flags");
      }

      virtual void set_ns_cert_type(const NSCert::Type ns_cert_type_arg)
      {
	return not_implemented("set_ns_cert_type");
      }

      virtual void set_remote_cert_tls(const KUParse::TLSWebType wt)
      {
	return not_implemented("set_remote_cert_tls");
      }

      virtual void set_tls_remote(const std::string& tls_remote_arg)
      {
	return not_implemented("set_tls_remote");
      }

      virtual void set_tls_version_min(const TLSVersion::Type tvm)
      {
	return not_implemented("set_tls_version_min");
      }

      virtual void set_local_cert_enabled(const bool v)
      {
	return not_implemented("set_local_cert_enabled");
      }

      virtual void set_enable_renegotiation(const bool v)
      {
	return not_implemented("set_enable_renegotiation");
      }

      virtual void set_force_aes_cbc_ciphersuites(const bool v)
      {
	return not_implemented("set_force_aes_cbc_ciphersuites");
      }

      virtual void set_rng(const RandomAPI::Ptr& rng_arg)
      {
	return not_implemented("set_rng");
      }

    private:
      void not_implemented(const char *funcname)
      {
	OPENVPN_LOG("AppleSSL: " << funcname << " not implemented");
      }

      Mode mode;
      CF::Array identity; // as returned by load_identity
      Frame::Ptr frame;
    };

    // Represents an actual SSL session.
    // Normally instantiated by AppleSSLContext::ssl().
    class SSL : public SSLAPI
    {
      friend class AppleSSLContext;

    public:
      typedef RCPtr<SSL> Ptr;

      virtual void start_handshake()
      {
	SSLHandshake(ssl);
      }

      virtual ssize_t write_cleartext_unbuffered(const void *data, const size_t size)
      {
	size_t actual = 0;
	const OSStatus status = SSLWrite(ssl, data, size, &actual);
	if (status < 0)
	  {
	    if (status == errSSLWouldBlock)
	      return SSLConst::SHOULD_RETRY;
	    else
	      throw CFException("AppleSSLContext::SSL::write_cleartext failed", status);
	  }
	else
	  return actual;
      }

      virtual ssize_t read_cleartext(void *data, const size_t capacity)
      {
	if (!overflow)
	  {
	    size_t actual = 0;
	    const OSStatus status = SSLRead(ssl, data, capacity, &actual);
	    if (status < 0)
	      {
		if (status == errSSLWouldBlock)
		  return SSLConst::SHOULD_RETRY;
		else
		  throw CFException("AppleSSLContext::SSL::read_cleartext failed", status);
	      }
	    else
	      return actual;
	  }
	else
	  throw ssl_ciphertext_in_overflow();
      }

      virtual bool read_cleartext_ready() const
      {
	// fixme: need to detect data buffered at SSL layer
	return !ct_in.empty();
      }

      virtual void write_ciphertext(const BufferPtr& buf)
      {
	if (ct_in.size() < MAX_CIPHERTEXT_IN)
	  ct_in.write_buf(buf);
	else
	  overflow = true;
      }

      virtual bool read_ciphertext_ready() const
      {
	return !ct_out.empty();
      }

      virtual BufferPtr read_ciphertext()
      {
	return ct_out.read_buf();
      }

      virtual std::string ssl_handshake_details() const // fixme -- code me
      {
	return "[AppleSSL not implemented]";
      }

      virtual const AuthCert::Ptr& auth_cert() const
      {
	OPENVPN_THROW(ssl_context_error, "AppleSSL::SSL: auth_cert() not implemented");
      }

      ~SSL()
      {
	ssl_erase();
      }

    private:
      SSL(const AppleSSLContext& ctx)
      {
	ssl_clear();
	try {
	  OSStatus s;

#ifdef OPENVPN_PLATFORM_IPHONE
	  // init SSL object, select client or server mode
	  if (ctx.mode().is_server())
	    ssl = SSLCreateContext(kCFAllocatorDefault, kSSLServerSide, kSSLStreamType);
	  else if (ctx.mode().is_client())
	    ssl = SSLCreateContext(kCFAllocatorDefault, kSSLClientSide, kSSLStreamType);
	  else
	    OPENVPN_THROW(ssl_context_error, "AppleSSLContext::SSL: unknown client/server mode");
	  if (ssl == nullptr)
	    throw CFException("SSLCreateContext failed");

	  // use TLS v1
	  s = SSLSetProtocolVersionMin(ssl, kTLSProtocol1);
	  if (s)
	    throw CFException("SSLSetProtocolVersionMin failed", s);
#else
	  // init SSL object, select client or server mode
	  if (ctx.mode().is_server())
	    s = SSLNewContext(true, &ssl);
	  else if (ctx.mode().is_client())
	    s = SSLNewContext(false, &ssl);
	  else
	    OPENVPN_THROW(ssl_context_error, "AppleSSLContext::SSL: unknown client/server mode");
	  if (s)
	    throw CFException("SSLNewContext failed", s);

	  // use TLS v1
	  s = SSLSetProtocolVersionEnabled(ssl, kSSLProtocol2, false);
	  if (s)
	    throw CFException("SSLSetProtocolVersionEnabled !S2 failed", s);
	  s = SSLSetProtocolVersionEnabled(ssl, kSSLProtocol3, false);
	  if (s)
	    throw CFException("SSLSetProtocolVersionEnabled !S3 failed", s);
	  s = SSLSetProtocolVersionEnabled(ssl, kTLSProtocol1, true);
	  if (s)
	    throw CFException("SSLSetProtocolVersionEnabled T1 failed", s);
#endif
	  // configure cert, private key, and supporting CAs via identity wrapper
	  s = SSLSetCertificate(ssl, ctx.identity()());
	  if (s)
	    throw CFException("SSLSetCertificate failed", s);

	  // configure ciphertext buffers
	  ct_in.set_frame(ctx.frame());
	  ct_out.set_frame(ctx.frame());

	  // configure the "connection" object to be self
	  s = SSLSetConnection(ssl, this);
	  if (s)
	    throw CFException("SSLSetConnection", s);

	  // configure ciphertext read/write callbacks
	  s = SSLSetIOFuncs(ssl, ct_read_func, ct_write_func);
	  if (s)
	    throw CFException("SSLSetIOFuncs failed", s);
	}
	catch (...)
	  {
	    ssl_erase();
	    throw;
	  }
      }

      static OSStatus ct_read_func(SSLConnectionRef cref, void *data, size_t *length)
      {
	try {
	  SSL *self = (SSL *)cref;
	  const size_t actual = self->ct_in.read((unsigned char *)data, *length);
	  const OSStatus ret = (*length == actual) ? 0 : errSSLWouldBlock;
	  *length = actual;
	  return ret;
	}
	catch (...)
	  {
	    return errSSLInternal;
	  }
      }

      static OSStatus ct_write_func(SSLConnectionRef cref, const void *data, size_t *length)
      {
	try {
	  SSL *self = (SSL *)cref;
	  self->ct_out.write((const unsigned char *)data, *length);
	  return 0;
	}
	catch (...)
	  {
	    return errSSLInternal;
	  }
      }

      void ssl_clear()
      {
	ssl = nullptr;
	overflow = false;
      }

      void ssl_erase()
      {
	if (ssl)
	  {
#ifdef OPENVPN_PLATFORM_IPHONE
	    CFRelease(ssl);
#else
	    SSLDisposeContext(ssl);
#endif
	  }
	ssl_clear();
      }

      SSLContextRef ssl; // underlying SSL connection object
      MemQStream ct_in;  // write ciphertext to here
      MemQStream ct_out; // read ciphertext from here
      bool overflow;
    };

    /////// start of main class implementation

    // create a new SSL instance
    virtual SSLAPI::Ptr ssl()
    {
      return SSL::Ptr(new SSL(*this));
    }

    // like ssl() above but verify hostname against cert CommonName and/or SubjectAltName
    virtual SSLAPI::Ptr ssl(const std::string& hostname)
    {
      OPENVPN_THROW(ssl_context_error, "AppleSSLContext: ssl session with CommonName and/or SubjectAltName verification not implemented");
    }

    virtual const Mode& mode() const
    {
      return config_->mode;
    }

  private:
    AppleSSLContext(Config* config)
      : config_(config)
    {
      if (!config_->identity())
	OPENVPN_THROW(ssl_context_error, "AppleSSLContext: identity undefined");
    }

    const Frame::Ptr& frame() const { return config_->frame; }
    const CF::Array& identity() const { return config_->identity; }

    // load an identity from keychain, return as an array that can
    // be passed to SSLSetCertificate
    static CF::Array load_identity_(const std::string& subj_match)
    {
      const CF::String label = CF::string(subj_match);
      const void *keys[] =   { kSecClass,         kSecMatchSubjectContains, kSecMatchTrustedOnly, kSecReturnRef };
      const void *values[] = { kSecClassIdentity, label(),                  kCFBooleanTrue,       kCFBooleanTrue };
      const CF::Dict query = CF::dict(keys, values, sizeof(keys)/sizeof(keys[0]));
      CF::Generic result;
      const OSStatus s = SecItemCopyMatching(query(), result.mod_ref());
      if (!s && result.defined())
	{
	  const void *asrc[] = { result() };
	  return CF::array(asrc, 1);
	}
      else
	return CF::Array(); // not found
    }

    Config::Ptr config_;
  };

  typedef AppleSSLContext::Ptr AppleSSLContextPtr;

} // namespace openvpn

#endif // OPENVPN_APPLECRYPTO_SSL_SSLCTX_H
