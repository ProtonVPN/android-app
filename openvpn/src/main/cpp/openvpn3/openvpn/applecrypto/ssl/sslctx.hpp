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

    enum
    {
        MAX_CIPHERTEXT_IN = 64
    };

    // The data needed to construct an AppleSSLContext.
    class Config : public SSLConfigAPI
    {
        friend class AppleSSLContext;

      public:
        typedef RCPtr<Config> Ptr;

        Config()
        {
        }

        void load_identity(const std::string &subject_match)
        {
            identity = load_identity_(subject_match);
            if (!identity())
                OPENVPN_THROW(ssl_context_error, "AppleSSLContext: identity '" << subject_match << "' undefined");
        }

        SSLFactoryAPI::Ptr new_factory() override
        {
            return SSLFactoryAPI::Ptr(new AppleSSLContext(this));
        }

        void set_mode(const Mode &mode_arg) override
        {
            mode = mode_arg;
        }

        const Mode &get_mode() const override
        {
            return mode;
        }

        void set_frame(const Frame::Ptr &frame_arg) override
        {
            frame = frame_arg;
        }

        void load(const OptionList &opt, const unsigned int lflags) override
        {
            // client/server
            if (lflags & LF_PARSE_MODE)
                mode = opt.exists("client") ? Mode(Mode::CLIENT) : Mode(Mode::SERVER);

            // identity
            {
                const std::string &subject_match = opt.get("identity", 1, 256);
                load_identity(subject_match);
            }
        }

        void set_external_pki_callback(ExternalPKIBase *external_pki_arg, const std::string &alias) override
        {
            not_implemented("set_external_pki_callback");
        }

        void set_private_key_password(const std::string &pwd) override
        {
            return not_implemented("set_private_key_password");
        }

        void load_ca(const std::string &ca_txt, bool strict) override
        {
            return not_implemented("load_ca");
        }

        void load_crl(const std::string &crl_txt) override
        {
            return not_implemented("load_crl");
        }

        void load_cert(const std::string &cert_txt) override
        {
            return not_implemented("load_cert");
        }

        void load_cert(const std::string &cert_txt, const std::string &extra_certs_txt) override
        {
            return not_implemented("load_cert");
        }

        void load_private_key(const std::string &key_txt) override
        {
            return not_implemented("load_private_key");
        }

        void load_dh(const std::string &dh_txt) override
        {
            return not_implemented("load_dh");
        }

        void set_debug_level(const int debug_level) override
        {
            return not_implemented("set_debug_level");
        }

        void set_flags(const unsigned int flags_arg) override
        {
            return not_implemented("set_flags");
        }

        void set_ns_cert_type(const NSCert::Type ns_cert_type_arg) override
        {
            return not_implemented("set_ns_cert_type");
        }

        void set_remote_cert_tls(const KUParse::TLSWebType wt) override
        {
            return not_implemented("set_remote_cert_tls");
        }

        void set_tls_remote(const std::string &tls_remote_arg) override
        {
            return not_implemented("set_tls_remote");
        }

        void set_tls_version_min(const TLSVersion::Type tvm) override
        {
            return not_implemented("set_tls_version_min");
        }

        void set_local_cert_enabled(const bool v) override
        {
            return not_implemented("set_local_cert_enabled");
        }

        void set_enable_renegotiation(const bool v) override
        {
            return not_implemented("set_enable_renegotiation");
        }

        void set_rng(const StrongRandomAPI::Ptr &rng_arg) override
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

        void start_handshake() override
        {
            SSLHandshake(ssl);
        }

        ssize_t write_cleartext_unbuffered(const void *data, const size_t size) override
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

        ssize_t read_cleartext(void *data, const size_t capacity) override
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

        bool read_cleartext_ready() const override
        {
            // fixme: need to detect data buffered at SSL layer
            return !ct_in.empty();
        }

        void write_ciphertext(const BufferPtr &buf) override
        {
            if (ct_in.size() < MAX_CIPHERTEXT_IN)
                ct_in.write_buf(buf);
            else
                overflow = true;
        }

        bool read_ciphertext_ready() const override
        {
            return !ct_out.empty();
        }

        BufferPtr read_ciphertext() override
        {
            return ct_out.read_buf();
        }

        std::string ssl_handshake_details() const override // fixme -- code me
        {
            return "[AppleSSL not implemented]";
        }

        const AuthCert::Ptr &auth_cert() const override
        {
            OPENVPN_THROW(ssl_context_error, "AppleSSL::SSL: auth_cert() not implemented");
        }

        ~SSL()
        {
            ssl_erase();
        }

      private:
        SSL(const AppleSSLContext &ctx)
        {
            ssl_clear();
            try
            {
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
            try
            {
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
            try
            {
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
    SSLAPI::Ptr ssl() override
    {
        return SSL::Ptr(new SSL(*this));
    }

    // like ssl() above but verify hostname against cert CommonName and/or SubjectAltName
    SSLAPI::Ptr ssl(const std::string &hostname) override
    {
        OPENVPN_THROW(ssl_context_error, "AppleSSLContext: ssl session with CommonName and/or SubjectAltName verification not implemented");
    }

    const Mode &mode() const override
    {
        return config_->mode;
    }

  private:
    AppleSSLContext(Config *config)
        : config_(config)
    {
        if (!config_->identity())
            OPENVPN_THROW(ssl_context_error, "AppleSSLContext: identity undefined");
    }

    const Frame::Ptr &frame() const
    {
        return config_->frame;
    }
    const CF::Array &identity() const
    {
        return config_->identity;
    }

    // load an identity from keychain, return as an array that can
    // be passed to SSLSetCertificate
    static CF::Array load_identity_(const std::string &subj_match)
    {
        const CF::String label = CF::string(subj_match);
        const void *keys[] = {kSecClass, kSecMatchSubjectContains, kSecMatchTrustedOnly, kSecReturnRef};
        const void *values[] = {kSecClassIdentity, label(), kCFBooleanTrue, kCFBooleanTrue};
        const CF::Dict query = CF::dict(keys, values, sizeof(keys) / sizeof(keys[0]));
        CF::Generic result;
        const OSStatus s = SecItemCopyMatching(query(), result.mod_ref());
        if (!s && result.defined())
        {
            const void *asrc[] = {result()};
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
