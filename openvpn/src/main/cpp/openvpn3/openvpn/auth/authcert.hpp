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

#ifndef OPENVPN_AUTH_AUTHCERT_H
#define OPENVPN_AUTH_AUTHCERT_H

#include <string>
#include <vector>
#include <sstream>
#include <cstring>
#include <cstdint>
#include <memory>
#include <utility>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/hexstr.hpp>
#include <openvpn/common/binprefix.hpp>
#include <openvpn/common/to_string.hpp>
#include <openvpn/common/jsonlib.hpp>
#include <openvpn/pki/x509track.hpp>
#include <openvpn/ssl/sni_metadata.hpp>
#include <openvpn/common/socktypes.hpp> // for ntohl/htonl

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
        enum Type
        {
            OK = 0,  // OK MUST be 0
            EXPIRED, // less severe...
            BAD_CERT_TYPE,
            CERT_FAIL,
            SNI_ERROR, // more severe...
            N
        };

        void add_fail(const size_t depth, const Type new_code, std::string reason)
        {
            if (new_code > code)
                code = new_code;
            while (errors.size() <= depth)
                errors.emplace_back();
            std::string &err = errors[depth];
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
        Type code{OK};                   // highest-valued cert fail code
        std::vector<std::string> errors; // human-readable cert errors by depth
    };

    class Serial
    {
      public:
        OPENVPN_EXCEPTION(serial_number_error);

        Serial()
        {
            std::memset(serial_number, 0xff, sizeof(serial_number));
        }

        Serial(const std::int64_t sn)
        {
            init_from_int64(sn);
        }

        Serial(const std::string &sn_str)
        {
            init_from_string(sn_str);
        }

#ifdef OPENVPN_JSON_INTERNAL
        Serial(const Json::Value &jsn)
        {
            switch (jsn.type())
            {
            case Json::intValue:
            case Json::uintValue:
                init_from_int64(jsn.asInt64());
                break;
            case Json::stringValue:
                init_from_string(jsn.asStringRef());
                break;
            case Json::nullValue:
                throw serial_number_error("JSON serial is missing");
                break;
            default:
                throw serial_number_error("JSON serial is of incorrect type (must be integer or string)");
            }
        }
#endif

        bool defined() const
        {
            for (size_t i = 0; i < 5; ++i)
                if (serial_number32[i] != 0xffffffffu)
                    return true;
            return false;
        }

        std::int64_t as_int64() const
        {
            if (serial_number32[0] != 0
                || serial_number32[1] != 0
                || serial_number32[2] != 0)
            {
                return -1;
            }
            const std::int64_t ret = std::int64_t((std::uint64_t(ntohl(serial_number32[3])) << 32)
                                                  | std::uint64_t(ntohl(serial_number32[4])));
            if (ret < 0)
                return -1;
            return ret;
        }

        bool operator==(const Serial &other) const
        {
            return !std::memcmp(serial_number, other.serial_number, sizeof(serial_number));
        }

        bool operator!=(const Serial &other) const
        {
            return !operator==(other);
        }

        std::string to_string() const
        {
            return to_string(serial_number);
        }

        static std::string to_string(const std::uint8_t *serial_number)
        {
            std::string ret;
            bool leading0 = true;
            for (size_t i = 0; i < size(); ++i)
            {
                const std::uint8_t byte = serial_number[i];
                const bool last = (i == size() - 1);
                if (!byte && leading0 && !last)
                    continue;
                RenderHexByte rhb(byte);
                ret += rhb.char1();
                ret += rhb.char2();
                if (!last)
                    ret += ':';
                leading0 = false;
            }
            return ret;
        }

        const std::uint8_t *number() const
        {
            return serial_number;
        }

        std::uint8_t *number()
        {
            return serial_number;
        }

        static constexpr size_t size()
        {
            return sizeof(serial_number);
        }

      private:
        std::uint8_t parse_hex(const char c)
        {
            const int h = parse_hex_char(c);
            if (h < 0)
                throw Exception(std::string("'") + c + "' is not a hex char");
            return std::uint8_t(h);
        }

        void init_from_int64(const std::int64_t sn)
        {
            if (sn >= 0)
            {
                serial_number32[0] = 0;
                serial_number32[1] = 0;
                serial_number32[2] = 0;
                serial_number32[3] = htonl(std::uint32_t(sn >> 32));
                serial_number32[4] = htonl(std::uint32_t(sn));
            }
            else
                std::memset(serial_number, 0xff, sizeof(serial_number));
        }

        void init_from_string(const std::string &sn_str)
        {
            enum State
            {
                C1,    // character #1 of hex byte
                C2,    // character #2 of hex byte
                C2REQ, // like C2 but character is required
            };

            State state = C2REQ;
            int i = int(sizeof(serial_number) - 1);
            std::memset(serial_number, 0, sizeof(serial_number));

            try
            {
                for (auto ci = sn_str.crbegin(); ci != sn_str.crend(); ++ci)
                {
                    const char c = *ci;
                    switch (state)
                    {
                    case C2:
                        if (c == ':')
                        {
                            state = C2REQ;
                            break;
                        }
                        // fallthrough
                    case C2REQ:
                        if (c == ':')
                            throw Exception("spurious colon");
                        if (i < 0)
                            throw Exception("serial number too large (C2)");
                        serial_number[i] = parse_hex(c);
                        state = C1;
                        break;
                    case C1:
                        if (c == ':') // colon delimiter is optional
                        {
                            state = C2REQ;
                            --i;
                            break;
                        }
                        if (i < 0)
                            throw Exception("serial number too large (C1)");
                        serial_number[i--] |= parse_hex(c) << 4;
                        state = C2;
                        break;
                    default:
                        throw Exception("unknown state");
                    }
                }
                if (state == C2REQ)
                    throw Exception("expected leading serial number hex digit");
            }
            catch (const std::exception &e)
            {
                throw serial_number_error(e.what());
            }
        }

        // certificate serial number in big-endian format
        union {
            std::uint8_t serial_number[20];
            std::uint32_t serial_number32[5];
        };
    };

    AuthCert()
        : defined_(false)
    {
        std::memset(issuer_fp, 0, sizeof(issuer_fp));
    }

    AuthCert(std::string cn_arg, const std::int64_t sn)
        : defined_(true),
          cn(std::move(cn_arg)),
          serial(sn)
    {
        std::memset(issuer_fp, 0, sizeof(issuer_fp));
    }

#ifdef UNIT_TEST
    AuthCert(const std::string &cn_arg,
             const std::string &issuer_fp_arg,
             const Serial &serial_arg)
        : defined_(true),
          cn(cn_arg),
          serial(serial_arg)
    {
        parse_issuer_fp(issuer_fp_arg);
    }
#endif

    bool defined() const
    {
        return defined_;
    }

    bool sni_defined() const
    {
        return !sni.empty();
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

    bool sn_defined() const
    {
        return serial.defined();
    }

    std::int64_t serial_number_as_int64() const
    {
        return serial.as_int64();
    }

    const Serial &get_serial() const
    {
        return serial;
    }

    bool operator==(const AuthCert &other) const
    {
        return sni == other.sni
               && cn == other.cn
               && serial == other.serial
               && !std::memcmp(issuer_fp, other.issuer_fp, sizeof(issuer_fp));
    }

    bool operator!=(const AuthCert &other) const
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
        os << "CN=" << cn;
        if (serial.defined())
            os << " SN=" << serial.to_string();
        os << " ISSUER_FP=" << issuer_fp_str(false);
        return os.str();
    }

    // example return for SN=65536: 01:00:00:00:00
    std::string serial_number_str() const
    {
        return serial.to_string();
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

    const std::string &get_sni() const
    {
        return sni;
    }

    const std::string &get_cn() const
    {
        return cn;
    }

    const X509Track::Set *x509_track_get() const
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

    const Fail *get_fail() const
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

#ifdef UNIT_TEST
    void parse_issuer_fp(const std::string &issuer_fp_hex)
    {
        Buffer buf(issuer_fp, sizeof(issuer_fp), false);
        parse_hex(buf, issuer_fp_hex);
        if (buf.size() != sizeof(issuer_fp))
            throw Exception("bad length in issuer_fp hex string");
    }
#endif
    bool defined_;

    std::string sni;            // SNI (server name indication)
    std::string cn;             // common name
    Serial serial;              // certificate serial number
    std::uint8_t issuer_fp[20]; // issuer cert fingerprint

    std::unique_ptr<Fail> fail;
    std::unique_ptr<X509Track::Set> x509_track;
    SNI::Metadata::UPtr sni_metadata;
};
} // namespace openvpn

#endif
