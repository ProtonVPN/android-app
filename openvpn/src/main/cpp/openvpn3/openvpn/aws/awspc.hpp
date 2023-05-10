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

// Get AWS info such as instanceId, region, and privateIp.
// Also optionally call AWSPC API with product code to get
// number of licensed concurrent connections.

#pragma once

#include <string>
#include <utility>

#include <openvpn/aws/awscreds.hpp>
#include <openvpn/ws/httpcliset.hpp>
#include <openvpn/common/jsonhelper.hpp>
#include <openvpn/common/hexstr.hpp>
#include <openvpn/common/enumdir.hpp>
#include <openvpn/common/file.hpp>
#include <openvpn/random/devurand.hpp>
#include <openvpn/frame/frame_init.hpp>
#include <openvpn/openssl/sign/verify.hpp>
#include <openvpn/openssl/sign/pkcs7verify.hpp>
#include <openvpn/ssl/sslchoose.hpp>

namespace openvpn {
namespace AWS {

class PCQuery : public RC<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<PCQuery> Ptr;

    OPENVPN_EXCEPTION(awspc_query_error);

    struct Info
    {
        std::string instanceId;
        std::string region;
        std::string az;
        std::string privateIp;

        Creds creds;

        int concurrentConnections = -1;
        std::string error;

        bool is_error() const
        {
            return !error.empty();
        }

        bool instance_data_defined() const
        {
            return !instanceId.empty() && !region.empty() && !privateIp.empty();
        }

        // example: [instanceId=i-ae91d23e region=us-east-1 privateIp=10.0.0.218 concurrentConnections=10]
        std::string to_string() const
        {
            std::string ret = "[instanceId=" + instanceId + " region=" + region;
            if (!privateIp.empty())
                ret += " privateIp=" + privateIp;
            if (concurrentConnections >= 0)
                ret += " concurrentConnections=" + std::to_string(concurrentConnections);
            if (!error.empty())
                ret += " error='" + error + '\'';
            ret += ']';
            return ret;
        }
    };

    PCQuery(WS::ClientSet::Ptr cs_arg,
            const bool lookup_product_code_arg,
            const int debug_level_arg)
        : cs(std::move(cs_arg)),
          rng(new DevURand()),
          frame(frame_init_simple(1024)),
          lookup_product_code(lookup_product_code_arg),
          debug_level(debug_level_arg)
    {
    }

    PCQuery(WS::ClientSet::Ptr cs_arg,
            const std::string &role_for_credentials_arg,
            const std::string &certs_dir_arg)
        : cs(std::move(cs_arg)),
          rng(new DevURand()),
          frame(frame_init_simple(1024)),
          lookup_product_code(false),
          debug_level(0),
          role_for_credentials(role_for_credentials_arg),
          certs_dir(certs_dir_arg)
    {
    }

    void start(std::function<void(Info info)> completion_arg)
    {
        // make sure we are not in a pending state
        if (pending)
            throw awspc_query_error("request pending");
        pending = true;

        // save completion method
        completion = std::move(completion_arg);

        // init return object
        info = Info();

        try
        {
            // make HTTP context
            WS::Client::Config::Ptr http_config(new WS::Client::Config());
            http_config->frame = frame;
            http_config->connect_timeout = 15;
            http_config->general_timeout = 30;

            // make transaction set for initial local query
            WS::ClientSet::TransactionSet::Ptr ts = new WS::ClientSet::TransactionSet;
            ts->host.host = "169.254.169.254";
            ts->host.port = "80";
            ts->http_config = http_config;
            ts->max_retries = 3;
            ts->debug_level = debug_level;

            // transaction #1
            {
                std::unique_ptr<WS::ClientSet::Transaction> t(new WS::ClientSet::Transaction);
                t->req.method = "GET";
                t->req.uri = "/latest/dynamic/instance-identity/document";
                ts->transactions.push_back(std::move(t));
            }

            // transaction #2
            {
                std::unique_ptr<WS::ClientSet::Transaction> t(new WS::ClientSet::Transaction);
                t->req.method = "GET";
                t->req.uri = "/latest/dynamic/instance-identity/pkcs7";
                ts->transactions.push_back(std::move(t));
            }

            // transaction #3
            if (lookup_product_code)
            {
                std::unique_ptr<WS::ClientSet::Transaction> t(new WS::ClientSet::Transaction);
                t->req.method = "GET";
                t->req.uri = "/latest/meta-data/product-codes";
                ts->transactions.push_back(std::move(t));
            }

            // transaction #4
            if (!role_for_credentials.empty())
            {
                std::unique_ptr<WS::ClientSet::Transaction> t(new WS::ClientSet::Transaction);
                t->req.method = "GET";
                t->req.uri = "/latest/meta-data/iam/security-credentials/" + role_for_credentials;
                ts->transactions.push_back(std::move(t));
            }

            // completion handler
            ts->completion = [self = Ptr(this)](WS::ClientSet::TransactionSet &ts)
            {
                self->local_query_complete(ts);
            };

            // do the request
            cs->new_request(ts);
        }
        catch (const std::exception &e)
        {
            done(e.what());
        }
    }

    void stop()
    {
        if (cs)
            cs->stop();
    }

  private:
    void done(std::string error)
    {
        pending = false;
        info.error = std::move(error);
        if (completion)
            completion(std::move(info));
    }

    void local_query_complete(WS::ClientSet::TransactionSet &lts)
    {
        try
        {
            // get transactions and check that they succeeded
            WS::ClientSet::Transaction &ident_trans = *lts.transactions.at(0);
            if (!ident_trans.request_status_success())
            {
                done("could not fetch AWS identity document: " + ident_trans.format_status(lts));
                return;
            }

            WS::ClientSet::Transaction &sig_trans = *lts.transactions.at(1);
            if (!sig_trans.request_status_success())
            {
                done("could not fetch AWS identity document signature: " + sig_trans.format_status(lts));
                return;
            }

            // get identity document and signature
            const std::string ident = ident_trans.content_in.to_string();
            const std::string sig = "-----BEGIN PKCS7-----\n"
                                    + sig_trans.content_in.to_string()
                                    + "\n-----END PKCS7-----\n";

            if (debug_level >= 3)
            {
                OPENVPN_LOG("IDENT\n"
                            << ident);
                OPENVPN_LOG("SIG\n"
                            << sig);
            }

            // verify signature on identity document
            {
                std::list<OpenSSLPKI::X509> certs;
                if (certs_dir.empty())
                    certs.emplace_back(awscert(), "AWS Cert");
                else
                {
                    enum_dir(certs_dir, [&certs, certs_dir = certs_dir](const std::string &file)
                             { certs.emplace_back(read_text(certs_dir + "/" + file), "AWS Cert"); });
                }
                OpenSSLSign::verify_pkcs7(certs, sig, ident);
            }

            // parse the identity document (JSON)
            {
                const std::string title = "identity-document";
                const Json::Value root = json::parse(ident, title);
                info.region = json::get_string(root, "region", title);
                info.az = json::get_string(root, "availabilityZone", title);
                info.instanceId = json::get_string(root, "instanceId", title);
                info.privateIp = json::get_string(root, "privateIp", title);
            }

            if (lookup_product_code)
            {
                WS::ClientSet::Transaction &pc_trans = *lts.transactions.at(2);
                if (pc_trans.request_status_success())
                {
                    const std::string pc = pc_trans.content_in.to_string();
                    queue_pc_validation(pc);
                }
                else
                    done("could not fetch AWS product code: " + pc_trans.format_status(lts));
            }

            if (!role_for_credentials.empty())
            {
                WS::ClientSet::Transaction &cred_trans = *lts.transactions.at(lookup_product_code ? 3 : 2);
                if (cred_trans.request_status_success())
                {
                    const std::string creds = cred_trans.content_in.to_string();
                    const Json::Value root = json::parse(creds);
                    info.creds.access_key = json::get_string(root, "AccessKeyId");
                    info.creds.secret_key = json::get_string(root, "SecretAccessKey");
                    info.creds.token = json::get_string(root, "Token");
                    done("");
                }
                else
                    done("could not fetch role credentials: " + cred_trans.format_status(lts));
            }
            else
                done("");
        }
        catch (const std::exception &e)
        {
            done(e.what());
        }
    }

    void queue_pc_validation(const std::string &pc)
    {
        if (debug_level >= 3)
            OPENVPN_LOG("PRODUCT CODE: " << pc);

        // SSL flags
        unsigned int ssl_flags = SSLConst::ENABLE_CLIENT_SNI;
        if (debug_level >= 1)
            ssl_flags |= SSLConst::LOG_VERIFY_STATUS;

        // make SSL context using awspc_web_cert() as our CA bundle
        SSLLib::SSLAPI::Config::Ptr ssl(new SSLLib::SSLAPI::Config);
        ssl->set_mode(Mode(Mode::CLIENT));
        ssl->load_ca(awspc_web_cert(), false);
        ssl->set_local_cert_enabled(false);
        ssl->set_tls_version_min(TLSVersion::Type::V1_2);
        ssl->set_remote_cert_tls(KUParse::TLS_WEB_SERVER);
        ssl->set_flags(ssl_flags);
        ssl->set_frame(frame);
        ssl->set_rng(rng);

        // make HTTP context
        WS::Client::Config::Ptr hc(new WS::Client::Config());
        hc->frame = frame;
        hc->ssl_factory = ssl->new_factory();
        hc->user_agent = "PG";
        hc->connect_timeout = 30;
        hc->general_timeout = 60;

        // make host list
        WS::ClientSet::HostRetry::Ptr hr(new WS::ClientSet::HostRetry(
            "awspc1.openvpn.net",
            "awspc2.openvpn.net"));

        // make transaction set
        WS::ClientSet::TransactionSet::Ptr ts = new WS::ClientSet::TransactionSet;
        ts->host.host = hr->next_host();
        ts->host.port = "443";
        ts->http_config = hc;
        ts->error_recovery = hr;
        ts->max_retries = 5;
        ts->retry_duration = Time::Duration::seconds(5);
        ts->debug_level = debug_level;

        // transaction #1
        {
            std::unique_ptr<WS::ClientSet::Transaction> t(new WS::ClientSet::Transaction);
            t->req.uri = "/prod/AwsPC";
            t->req.method = "POST";
            t->ci.type = "application/json";
            t->randomize_resolver_results = true;

            Json::Value root(Json::objectValue);
            root["region"] = Json::Value(info.region);
            root["identityIp"] = Json::Value(info.privateIp);
            root["host"] = Json::Value(openvpn_io::ip::host_name());
            root["instanceId"] = Json::Value(info.instanceId);
            root["productCode"] = Json::Value(pc);
            root["nonce"] = Json::Value(nonce());
            const std::string jreq = root.toStyledString();
            t->content_out.push_back(buf_from_string(jreq));
            awspc_req = std::move(root);

            ts->transactions.push_back(std::move(t));

            if (debug_level >= 3)
                OPENVPN_LOG("AWSPC REQ\n"
                            << jreq);
        }

        // completion handler
        ts->completion = [self = Ptr(this)](WS::ClientSet::TransactionSet &ts)
        {
            self->awspc_query_complete(ts);
        };

        // do the request
        cs->new_request(ts);
    }

    void awspc_query_complete(WS::ClientSet::TransactionSet &ats)
    {
        try
        {
            const std::string title = "awspc-reply";

            // get transactions and check that they succeeded
            WS::ClientSet::Transaction &trans = *ats.transactions.at(0);
            if (!trans.request_status_success())
            {
                done("awspc server error: " + trans.format_status(ats));
                return;
            }

            // check content-type
            if (trans.reply.headers.get_value_trim("content-type") != "application/json")
            {
                done("expected application/json reply from awspc server");
                return;
            }

            // parse JSON reply
            const std::string jtxt = trans.content_in.to_string();
            const Json::Value root = json::parse(jtxt, title);
            if (debug_level >= 3)
                OPENVPN_LOG("AWSPC REPLY\n"
                            << root.toStyledString());

            // check for errors
            if (json::exists(root, "errorMessage"))
            {
                const std::string em = json::get_string(root, "errorMessage", title);
                const std::string et = json::get_string_optional(root, "errorType", "unspecified-error", title);
                done(et + " : " + em);
                return;
            }

            // verify consistency of region, instanceId, productCode, and nonce
            if (!awspc_req_verify_consistency(root))
            {
                done("awspc request/reply consistency");
                return;
            }

            // verify reply signature
            {
                const std::string line_to_sign = to_string_sig(root);
                if (debug_level >= 3)
                    OPENVPN_LOG("LINE TO SIGN: " << line_to_sign);
                const std::string sig = json::get_string(root, "signature", title);
                const OpenSSLPKI::X509 cert(awspc_signing_cert(), "awspc-cert");
                OpenSSLSign::verify(cert, sig, line_to_sign, "sha256");
            }

            // get concurrent connections
            info.concurrentConnections = json::get_int(root, "concurrentConnections", title);
            done("");
        }
        catch (const std::exception &e)
        {
            done(e.what());
        }
    }

    bool awspc_req_verify_consistency(const Json::Value &reply,
                                      const std::string &key) const
    {
        return json::get_string(reply, key, "awspc-verify-reply") == json::get_string(awspc_req, key, "awspc-verify-request");
    }

    bool awspc_req_verify_consistency(const Json::Value &reply) const
    {
        return awspc_req_verify_consistency(reply, "region")
               && awspc_req_verify_consistency(reply, "instanceId")
               && awspc_req_verify_consistency(reply, "productCode")
               && awspc_req_verify_consistency(reply, "nonce");
    }

    static std::string to_string_sig(const Json::Value &reply)
    {
        const std::string title = "to-string-sig";
        return json::get_string(reply, "region", title)
               + '/' + json::get_string(reply, "instanceId", title)
               + '/' + json::get_string(reply, "productCode", title)
               + '/' + json::get_string(reply, "nonce", title)
               + '/' + std::to_string(json::get_int(reply, "concurrentConnections", title));
    }

    std::string nonce() const
    {
        unsigned char data[16];
        rng->assert_crypto();
        rng->rand_fill(data);
        return render_hex(data, sizeof(data));
    }

    // The AWS cert for PKCS#7 validation of AWS identity document
    static std::string awscert()
    {
        return std::string(
            "-----BEGIN CERTIFICATE-----\n"
            "MIIC7TCCAq0CCQCWukjZ5V4aZzAJBgcqhkjOOAQDMFwxCzAJBgNVBAYTAlVTMRkw\n"
            "FwYDVQQIExBXYXNoaW5ndG9uIFN0YXRlMRAwDgYDVQQHEwdTZWF0dGxlMSAwHgYD\n"
            "VQQKExdBbWF6b24gV2ViIFNlcnZpY2VzIExMQzAeFw0xMjAxMDUxMjU2MTJaFw0z\n"
            "ODAxMDUxMjU2MTJaMFwxCzAJBgNVBAYTAlVTMRkwFwYDVQQIExBXYXNoaW5ndG9u\n"
            "IFN0YXRlMRAwDgYDVQQHEwdTZWF0dGxlMSAwHgYDVQQKExdBbWF6b24gV2ViIFNl\n"
            "cnZpY2VzIExMQzCCAbcwggEsBgcqhkjOOAQBMIIBHwKBgQCjkvcS2bb1VQ4yt/5e\n"
            "ih5OO6kK/n1Lzllr7D8ZwtQP8fOEpp5E2ng+D6Ud1Z1gYipr58Kj3nssSNpI6bX3\n"
            "VyIQzK7wLclnd/YozqNNmgIyZecN7EglK9ITHJLP+x8FtUpt3QbyYXJdmVMegN6P\n"
            "hviYt5JH/nYl4hh3Pa1HJdskgQIVALVJ3ER11+Ko4tP6nwvHwh6+ERYRAoGBAI1j\n"
            "k+tkqMVHuAFcvAGKocTgsjJem6/5qomzJuKDmbJNu9Qxw3rAotXau8Qe+MBcJl/U\n"
            "hhy1KHVpCGl9fueQ2s6IL0CaO/buycU1CiYQk40KNHCcHfNiZbdlx1E9rpUp7bnF\n"
            "lRa2v1ntMX3caRVDdbtPEWmdxSCYsYFDk4mZrOLBA4GEAAKBgEbmeve5f8LIE/Gf\n"
            "MNmP9CM5eovQOGx5ho8WqD+aTebs+k2tn92BBPqeZqpWRa5P/+jrdKml1qx4llHW\n"
            "MXrs3IgIb6+hUIB+S8dz8/mmO0bpr76RoZVCXYab2CZedFut7qc3WUH9+EUAH5mw\n"
            "vSeDCOUMYQR7R9LINYwouHIziqQYMAkGByqGSM44BAMDLwAwLAIUWXBlk40xTwSw\n"
            "7HX32MxXYruse9ACFBNGmdX2ZBrVNGrN9N2f6ROk0k9K\n"
            "-----END CERTIFICATE-----\n");
    }

    // The OpenVPN Tech. lambda web cert
    static std::string awspc_web_cert()
    {
        // Go Daddy Root Certificate Authority - G2
        return std::string(
            "-----BEGIN CERTIFICATE-----\n"
            "MIIDxTCCAq2gAwIBAgIBADANBgkqhkiG9w0BAQsFADCBgzELMAkGA1UEBhMCVVMxEDAOBgNVBAgT\n"
            "B0FyaXpvbmExEzARBgNVBAcTClNjb3R0c2RhbGUxGjAYBgNVBAoTEUdvRGFkZHkuY29tLCBJbmMu\n"
            "MTEwLwYDVQQDEyhHbyBEYWRkeSBSb290IENlcnRpZmljYXRlIEF1dGhvcml0eSAtIEcyMB4XDTA5\n"
            "MDkwMTAwMDAwMFoXDTM3MTIzMTIzNTk1OVowgYMxCzAJBgNVBAYTAlVTMRAwDgYDVQQIEwdBcml6\n"
            "b25hMRMwEQYDVQQHEwpTY290dHNkYWxlMRowGAYDVQQKExFHb0RhZGR5LmNvbSwgSW5jLjExMC8G\n"
            "A1UEAxMoR28gRGFkZHkgUm9vdCBDZXJ0aWZpY2F0ZSBBdXRob3JpdHkgLSBHMjCCASIwDQYJKoZI\n"
            "hvcNAQEBBQADggEPADCCAQoCggEBAL9xYgjx+lk09xvJGKP3gElY6SKDE6bFIEMBO4Tx5oVJnyfq\n"
            "9oQbTqC023CYxzIBsQU+B07u9PpPL1kwIuerGVZr4oAH/PMWdYA5UXvl+TW2dE6pjYIT5LY/qQOD\n"
            "+qK+ihVqf94Lw7YZFAXK6sOoBJQ7RnwyDfMAZiLIjWltNowRGLfTshxgtDj6AozO091GB94KPutd\n"
            "fMh8+7ArU6SSYmlRJQVhGkSBjCypQ5Yj36w6gZoOKcUcqeldHraenjAKOc7xiID7S13MMuyFYkMl\n"
            "NAJWJwGRtDtwKj9useiciAF9n9T521NtYJ2/LOdYq7hfRvzOxBsDPAnrSTFcaUaz4EcCAwEAAaNC\n"
            "MEAwDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMCAQYwHQYDVR0OBBYEFDqahQcQZyi27/a9\n"
            "BUFuIMGU2g/eMA0GCSqGSIb3DQEBCwUAA4IBAQCZ21151fmXWWcDYfF+OwYxdS2hII5PZYe096ac\n"
            "vNjpL9DbWu7PdIxztDhC2gV7+AJ1uP2lsdeu9tfeE8tTEH6KRtGX+rcuKxGrkLAngPnon1rpN5+r\n"
            "5N9ss4UXnT3ZJE95kTXWXwTrgIOrmgIttRD02JDHBHNA7XIloKmf7J6raBKZV8aPEjoJpL1E/QYV\n"
            "N8Gb5DKj7Tjo2GTzLH4U/ALqn83/B2gX2yKQOC16jdFU8WnjXzPKej17CuPKf1855eJ1usV2GDPO\n"
            "LPAvTK33sefOT6jEm0pUBsV/fdUID+Ic/n4XuKxe9tQWskMJDE32p2u0mYRlynqI4uJEvlz36hz1\n"
            "-----END CERTIFICATE-----\n");
    }

    // The OpenVPN Tech. lambda response signing cert
    static std::string awspc_signing_cert()
    {
        return std::string(
            "-----BEGIN CERTIFICATE-----\n"
            "MIIDSDCCAjCgAwIBAgIQYadxADonNbu3mPeXR0yYVTANBgkqhkiG9w0BAQsFADAW\n"
            "MRQwEgYDVQQDEwtBV1MgUEMgUm9vdDAeFw0xNjAzMDExOTU2NTZaFw0yNjAyMjcx\n"
            "OTU2NTZaMBAxDjAMBgNVBAMTBWF3c3BjMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A\n"
            "MIIBCgKCAQEA0ggZoYroOMwDHKCngVOdUKiF6y65LDWmbAwZVqwVI7WYpvOELV34\n"
            "04ZYtSqPq6IoGFuH6zl0P5rCi674T0oBPSUTmlLwLks+1zrGznboApkr67Mf2dCd\n"
            "snlyaNPuwrjWHJBa6Pi9dv/YMoJgDxOxk9mslAlcl5xOFgXbfSj1pAA0KVzwwbzz\n"
            "dnznJL67wCnuiAeqBxbkyarfOL414tepsI24kHoAddAVDdhWQ2WkhrT/vK2IRdGZ\n"
            "kU5hAAz/qPKkJxebw5uc+cL2TBii2r0Hvg7tEXI9eIEWeoghftsE5YEuaQHP4EVL\n"
            "JU+21OQzz0lT9L2rrvffTR7cF89Nbn2KMQIDAQABo4GXMIGUMAkGA1UdEwQCMAAw\n"
            "HQYDVR0OBBYEFAMy6uiElCGZVP/wwJeqvXL7QHTSMEYGA1UdIwQ/MD2AFLDKS6Dk\n"
            "NtTpQoOPxJi+DRS+GD2CoRqkGDAWMRQwEgYDVQQDEwtBV1MgUEMgUm9vdIIJAOu5\n"
            "NqrIe040MBMGA1UdJQQMMAoGCCsGAQUFBwMCMAsGA1UdDwQEAwIHgDANBgkqhkiG\n"
            "9w0BAQsFAAOCAQEAsFhhC9wwybTS2yTYiStATbxHWqnHJRrbMBpqX8FJweS1MM/j\n"
            "pwr1suTllwTHpqXpqgN6SDzdeG2ZKx8pvJr/dlmD9e+cHguIMTo6TcqPv1MPl3MZ\n"
            "ugOmDPlgmFYwAWBwzujiGR9bgdGfzw+94KK06iO8MrFLtkz9EbeoJol68mi98CEz\n"
            "kmOb2BM6tVzkvB9fIYyNkW66ZJs2gXwb6RZTyE9HMMGR67nWKYo9SxpB6f+6hlyU\n"
            "q7ptxP2Rwmz0u1pRaZdfHmJFOJnPniB7UmMx/t3ftqYWYDXuobr3LVvg7+33WUk0\n"
            "HfSdbAEkzzC82UTHj0xVH/uZZt8ORChRxuIWZQ==\n"
            "-----END CERTIFICATE-----\n");
    }

    WS::ClientSet::Ptr cs;
    RandomAPI::Ptr rng;
    Frame::Ptr frame;
    const bool lookup_product_code;
    const int debug_level;
    std::string role_for_credentials;
    std::string certs_dir;

    std::function<void(Info info)> completion;
    Info info;
    Json::Value awspc_req;
    bool pending = false;
};
} // namespace AWS
} // namespace openvpn
