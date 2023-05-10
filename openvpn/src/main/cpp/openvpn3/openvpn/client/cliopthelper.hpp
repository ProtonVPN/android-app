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

// A preliminary parser for OpenVPN client configuration files.

#ifndef OPENVPN_CLIENT_CLIOPTHELPER_H
#define OPENVPN_CLIENT_CLIOPTHELPER_H

#include <vector>
#include <string>
#include <sstream>
#include <utility>

#ifdef HAVE_CONFIG_JSONCPP
#include "json/json.h"
#endif /* HAVE_CONFIG_JSONCPP */

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/options.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/split.hpp>
#include <openvpn/common/splitlines.hpp>
#include <openvpn/common/userpass.hpp>
#include <openvpn/client/remotelist.hpp>
#include <openvpn/client/cliconstants.hpp>
#include <openvpn/ssl/peerinfo.hpp>
#include <openvpn/ssl/proto.hpp>
#include <openvpn/ssl/proto_context_options.hpp>
#include <openvpn/ssl/sslchoose.hpp>

namespace openvpn {
class ParseClientConfig
{
  public:
    struct ServerEntry
    {
        std::string server;
        std::string friendlyName;
    };

    struct ServerList : public std::vector<ServerEntry>
    {
    };

    struct RemoteItem
    {
        std::string host;
        std::string port;
        std::string proto;
    };

    ParseClientConfig()
    {
        reset_pod();
    }

    ParseClientConfig(const OptionList &options)
    {
        try
        {
            // reset POD types
            reset_pod();

            // limits
            const size_t max_server_list_size = ProfileParseLimits::MAX_SERVER_LIST_SIZE;

            // setenv UV_x
            PeerInfo::Set::Ptr peer_info_uv(new PeerInfo::Set);

            // process setenv directives
            {
                const OptionList::IndexList *se = options.get_index_ptr("setenv");
                if (se)
                {
                    for (OptionList::IndexList::const_iterator i = se->begin(); i != se->end(); ++i)
                    {
                        const Option &o = options[*i];
                        o.touch();
                        const std::string arg1 = o.get_optional(1, 256);

                        // server-locked profiles not supported
                        if (arg1 == "GENERIC_CONFIG")
                        {
                            error_ = true;
                            message_ = "ERR_PROFILE_SERVER_LOCKED_UNSUPPORTED: server locked profiles are currently unsupported";
                            return;
                        }
                        else if (arg1 == "ALLOW_PASSWORD_SAVE")
                            allowPasswordSave_ = parse_bool(o, "setenv ALLOW_PASSWORD_SAVE", 2);
                        else if (arg1 == "CLIENT_CERT")
                            clientCertEnabled_ = parse_bool(o, "setenv CLIENT_CERT", 2);
                        else if (arg1 == "USERNAME")
                            userlockedUsername_ = o.get(2, 256);
                        else if (arg1 == "FRIENDLY_NAME")
                            friendlyName_ = o.get(2, 256);
                        else if (arg1 == "SERVER")
                        {
                            const std::string &serv = o.get(2, 256);
                            std::vector<std::string> slist = Split::by_char<std::vector<std::string>, NullLex, Split::NullLimit>(serv, '/', 0, 1);
                            ServerEntry se;
                            if (slist.size() == 1)
                            {
                                se.server = slist[0];
                                se.friendlyName = slist[0];
                            }
                            else if (slist.size() == 2)
                            {
                                se.server = slist[0];
                                se.friendlyName = slist[1];
                            }
                            if (!se.server.empty() && !se.friendlyName.empty() && serverList_.size() < max_server_list_size)
                                serverList_.push_back(std::move(se));
                        }
                        else if (arg1 == "PUSH_PEER_INFO")
                            pushPeerInfo_ = true;
                        else if (string::starts_with(arg1, "UV_") && arg1.length() >= 4 && string::is_word(arg1))
                        {
                            const std::string value = o.get_optional(2, 256);
                            if (string::is_printable(value))
                                peer_info_uv->emplace_back(arg1, value);
                        }
                    }
                }
            }

            // Alternative to "setenv CLIENT_CERT 0".  Note that as of OpenVPN 2.3, this option
            // is only supported server-side, so this extends its meaning into the client realm.
            if (options.exists("client-cert-not-required"))
                clientCertEnabled_ = false;

            // userlocked username
            {
                const Option *o = options.get_ptr("USERNAME");
                if (o)
                    userlockedUsername_ = o->get(1, 256);
            }

            // userlocked username/password via <auth-user-pass>
            std::vector<std::string> user_pass;
            const bool auth_user_pass = parse_auth_user_pass(options, &user_pass);
            if (auth_user_pass && user_pass.size() >= 1)
            {
                userlockedUsername_ = user_pass[0];
                if (user_pass.size() >= 2)
                {
                    hasEmbeddedPassword_ = true;
                    embeddedPassword_ = user_pass[1];
                }
            }

            // External PKI
            externalPki_ = (clientCertEnabled_ && is_external_pki(options));

            // allow password save
            {
                const Option *o = options.get_ptr("allow-password-save");
                if (o)
                    allowPasswordSave_ = parse_bool(*o, "allow-password-save", 1);
            }

            // autologin
            {
                autologin_ = is_autologin(options, auth_user_pass, user_pass);
                if (autologin_)
                    allowPasswordSave_ = false; // saving passwords is incompatible with autologin
            }

            // static challenge
            {
                const Option *o = options.get_ptr("static-challenge");
                if (o)
                {
                    staticChallenge_ = o->get(1, 256);
                    if (o->get_optional(2, 16) == "1")
                        staticChallengeEcho_ = true;
                }
            }

            // validate remote list - don't randomize it at this point
            RandomAPI::Ptr no_rng;
            remoteList.reset(new RemoteList(options, "", 0, nullptr, no_rng));
            {
                if (remoteList->defined())
                {
                    const RemoteList::Item::Ptr ri = remoteList->get_item(0);
                    firstRemoteListItem_.host = ri->server_host;
                    firstRemoteListItem_.port = ri->server_port;
                    if (ri->transport_protocol.is_udp())
                        firstRemoteListItem_.proto = "udp";
                    else if (ri->transport_protocol.is_tcp())
                        firstRemoteListItem_.proto = "tcp-client";
                }
            }

            // determine if private key is encrypted
            if (!externalPki_)
            {
                const Option *o = options.get_ptr("key");
                if (o)
                {
                    const std::string &key_txt = o->get(1, Option::MULTILINE);
                    privateKeyPasswordRequired_ = (key_txt.find("-----BEGIN RSA PRIVATE KEY-----\nProc-Type: 4,ENCRYPTED\n") != std::string::npos
                                                   || key_txt.find("-----BEGIN EC PRIVATE KEY-----\nProc-Type: 4,ENCRYPTED\n") != std::string::npos
                                                   || key_txt.find("-----BEGIN ENCRYPTED PRIVATE KEY-----") != std::string::npos);
                }
            }

            // profile name
            {
                const Option *o = options.get_ptr("PROFILE");
                if (o)
                {
                    // take PROFILE substring up to '/'
                    const std::string &pn = o->get(1, 256);
                    const size_t slashpos = pn.find('/');
                    if (slashpos != std::string::npos)
                        profileName_ = pn.substr(0, slashpos);
                    else
                        profileName_ = pn;
                }
                else
                {
                    if (remoteList)
                        profileName_ = remoteList->get_item(0)->server_host;
                }

                // windows-driver
                {
                    const Option *o = options.get_ptr("windows-driver");
                    if (o)
                    {
                        windowsDriver_ = o->get(1, 256);
                    }
                }
            }

            // friendly name
            {
                const Option *o = options.get_ptr("FRIENDLY_NAME");
                if (o)
                    friendlyName_ = o->get(1, 256);
            }

            // server list
            {
                const Option *o = options.get_ptr("HOST_LIST");
                if (o)
                {
                    SplitLines in(o->get(1, 4096 | Option::MULTILINE), 0);
                    while (in(true))
                    {
                        ServerEntry se;
                        se.server = in.line_ref();
                        se.friendlyName = se.server;
                        Option::validate_string("HOST_LIST server", se.server, 256);
                        Option::validate_string("HOST_LIST friendly name", se.friendlyName, 256);
                        if (!se.server.empty() && !se.friendlyName.empty() && serverList_.size() < max_server_list_size)
                            serverList_.push_back(std::move(se));
                    }
                }
            }

            // push-peer-info
            {
                if (options.exists("push-peer-info"))
                    pushPeerInfo_ = true;
                if (pushPeerInfo_)
                    peerInfoUV_ = peer_info_uv;
            }

            // dev name
            {
                const Option *o = options.get_ptr("dev");
                if (o)
                {
                    dev = o->get(1, 256);
                }
            }

            // protocol configuration
            {
                protoConfig.reset(new ProtoContext::Config());
                protoConfig->tls_auth_factory.reset(new CryptoOvpnHMACFactory<SSLLib::CryptoAPI>());
                protoConfig->tls_crypt_factory.reset(new CryptoTLSCryptFactory<SSLLib::CryptoAPI>());
                protoConfig->load(options, ProtoContextOptions(), -1, false);
            }

            unsigned int lflags = SSLConfigAPI::LF_PARSE_MODE;

            // ssl lib configuration
            try
            {
                sslConfig.reset(new SSLLib::SSLAPI::Config());
                sslConfig->load(options, lflags);
            }
            catch (...)
            {
                sslConfig.reset();
            }
        }
        catch (const option_error &e)
        {
            error_ = true;
            message_ = Unicode::utf8_printable<std::string>(std::string("ERR_PROFILE_OPTION: ") + e.what(), 256);
        }
        catch (const std::exception &e)
        {
            error_ = true;
            message_ = Unicode::utf8_printable<std::string>(std::string("ERR_PROFILE_GENERIC: ") + e.what(), 256);
        }
    }

    static ParseClientConfig parse(const std::string &content)
    {
        return parse(content, nullptr);
    }

    static ParseClientConfig parse(const std::string &content, OptionList::KeyValueList *content_list)
    {
        OptionList options;
        return parse(content, content_list, options);
    }

    static ParseClientConfig parse(const std::string &content,
                                   OptionList::KeyValueList *content_list,
                                   OptionList &options)
    {
        try
        {
            OptionList::Limits limits("profile is too large",
                                      ProfileParseLimits::MAX_PROFILE_SIZE,
                                      ProfileParseLimits::OPT_OVERHEAD,
                                      ProfileParseLimits::TERM_OVERHEAD,
                                      ProfileParseLimits::MAX_LINE_SIZE,
                                      ProfileParseLimits::MAX_DIRECTIVE_SIZE);
            options.clear();
            options.parse_from_config(content, &limits);
            options.parse_meta_from_config(content, "OVPN_ACCESS_SERVER", &limits);
            if (content_list)
            {
                content_list->preprocess();
                options.parse_from_key_value_list(*content_list, &limits);
            }
            process_setenv_opt(options);
            options.update_map();

            // add in missing options
            bool added = false;

            // client
            if (!options.exists("client"))
            {
                Option opt;
                opt.push_back("client");
                options.push_back(std::move(opt));
                added = true;
            }

            // dev
            if (!options.exists("dev"))
            {
                Option opt;
                opt.push_back("dev");
                opt.push_back("tun");
                options.push_back(std::move(opt));
                added = true;
            }
            if (added)
                options.update_map();

            return ParseClientConfig(options);
        }
        catch (const std::exception &e)
        {
            ParseClientConfig ret;
            ret.error_ = true;
            ret.message_ = Unicode::utf8_printable<std::string>(std::string("ERR_PROFILE_GENERIC: ") + e.what(), 256);
            return ret;
        }
    }

    // true if error
    bool error() const
    {
        return error_;
    }

    // if error, message given here
    const std::string &message() const
    {
        return message_;
    }

    // this username must be used with profile
    const std::string &userlockedUsername() const
    {
        return userlockedUsername_;
    }

    // profile name of config
    const std::string &profileName() const
    {
        return profileName_;
    }

    // "friendly" name of config
    const std::string &friendlyName() const
    {
        return friendlyName_;
    }

    // true: no creds required, false: username/password required
    bool autologin() const
    {
        return autologin_;
    }

    // profile embedded password via <auth-user-pass>
    bool hasEmbeddedPassword() const
    {
        return hasEmbeddedPassword_;
    }
    const std::string &embeddedPassword() const
    {
        return embeddedPassword_;
    }

    // true: no client cert/key required, false: client cert/key required
    bool clientCertEnabled() const
    {
        return clientCertEnabled_;
    }

    // if true, this is an External PKI profile (no cert or key directives)
    bool externalPki() const
    {
        return externalPki_;
    }

    // static challenge, may be empty, ignored if autologin
    const std::string &staticChallenge() const
    {
        return staticChallenge_;
    }

    // true if static challenge response should be echoed to UI, ignored if autologin
    bool staticChallengeEcho() const
    {
        return staticChallengeEcho_;
    }

    // true if this profile requires a private key password
    bool privateKeyPasswordRequired() const
    {
        return privateKeyPasswordRequired_;
    }

    // true if user is allowed to save authentication password in UI
    bool allowPasswordSave() const
    {
        return allowPasswordSave_;
    }

    // true if "setenv PUSH_PEER_INFO" or "push-peer-info" are defined
    bool pushPeerInfo() const
    {
        return pushPeerInfo_;
    }

    // "setenv UV_x" directives if pushPeerInfo() is true
    const PeerInfo::Set *peerInfoUV() const
    {
        return peerInfoUV_.get();
    }

    // optional list of user-selectable VPN servers
    const ServerList &serverList() const
    {
        return serverList_;
    }

    // return first remote directive in config
    const RemoteItem &firstRemoteListItem() const
    {
        return firstRemoteListItem_;
    }

    const std::string &windowsDriver() const
    {
        return windowsDriver_;
    }

    std::string to_string() const
    {
        std::ostringstream os;
        os << "user=" << userlockedUsername_
           << " pn=" << profileName_
           << " fn=" << friendlyName_
           << " auto=" << autologin_
           << " embed_pw=" << hasEmbeddedPassword_
           << " epki=" << externalPki_
           << " schal=" << staticChallenge_
           << " scecho=" << staticChallengeEcho_;
        return os.str();
    }

    std::string to_string_config() const
    {
        std::ostringstream os;

        os << "client" << std::endl;
        os << "dev " << dev << std::endl;
        os << "dev-type " << protoConfig->layer.dev_type() << std::endl;
        for (size_t i = 0; i < remoteList->size(); i++)
        {
            const RemoteList::Item::Ptr item = remoteList->get_item(i);

            os << "remote " << item->server_host << " " << item->server_port;
            const char *proto = item->transport_protocol.protocol_to_string();
            if (proto)
                os << " " << proto;
            os << std::endl;
        }
        if (protoConfig->tls_crypt_context)
        {
            os << "<tls-crypt>" << std::endl
               << protoConfig->tls_key.render() << "</tls-crypt>"
               << std::endl;
        }
        else if (protoConfig->tls_auth_context)
        {
            os << "<tls-auth>" << std::endl
               << protoConfig->tls_key.render() << "</tls-auth>"
               << std::endl;
            os << "key_direction " << protoConfig->key_direction << std::endl;
        }

        // SSL parameters
        if (sslConfig)
        {
            print_pem(os, "ca", sslConfig->extract_ca());
            print_pem(os, "crl", sslConfig->extract_crl());
            print_pem(os, "key", sslConfig->extract_private_key());
            print_pem(os, "cert", sslConfig->extract_cert());

            std::vector<std::string> extra_certs = sslConfig->extract_extra_certs();
            if (extra_certs.size() > 0)
            {
                os << "<extra-certs>" << std::endl;
                for (auto &cert : extra_certs)
                {
                    os << cert;
                }
                os << "</extra-certs>" << std::endl;
            }
        }

        os << "cipher " << CryptoAlgs::name(protoConfig->dc.cipher(), "none")
           << std::endl;
        os << "auth " << CryptoAlgs::name(protoConfig->dc.digest(), "none")
           << std::endl;
        const char *comp = protoConfig->comp_ctx.method_to_string();
        if (comp)
            os << "compress " << comp << std::endl;
        os << "keepalive " << protoConfig->keepalive_ping.to_seconds() << " "
           << protoConfig->keepalive_timeout.to_seconds() << std::endl;
        os << "tun-mtu " << protoConfig->tun_mtu << std::endl;
        os << "reneg-sec " << protoConfig->renegotiate.to_seconds() << std::endl;

        return os.str();
    }

#ifdef HAVE_CONFIG_JSONCPP

    std::string to_json_config() const
    {
        std::ostringstream os;

        Json::Value root(Json::objectValue);

        root["mode"] = Json::Value("client");
        root["dev"] = Json::Value(dev);
        root["dev-type"] = Json::Value(protoConfig->layer.dev_type());
        root["remotes"] = Json::Value(Json::arrayValue);
        for (size_t i = 0; i < remoteList->size(); i++)
        {
            const RemoteList::Item::Ptr item = remoteList->get_item(i);

            Json::Value el = Json::Value(Json::objectValue);
            el["address"] = Json::Value(item->server_host);
            el["port"] = Json::Value((Json::UInt)std::stoi(item->server_port));
            if (item->transport_protocol() == Protocol::NONE)
                el["proto"] = Json::Value("adaptive");
            else
                el["proto"] = Json::Value(item->transport_protocol.str());

            root["remotes"].append(el);
        }
        if (protoConfig->tls_crypt_context)
        {
            root["tls_wrap"] = Json::Value(Json::objectValue);
            root["tls_wrap"]["mode"] = Json::Value("tls_crypt");
            root["tls_wrap"]["key"] = Json::Value(protoConfig->tls_key.render());
        }
        else if (protoConfig->tls_auth_context)
        {
            root["tls_wrap"] = Json::Value(Json::objectValue);
            root["tls_wrap"]["mode"] = Json::Value("tls_auth");
            root["tls_wrap"]["key_direction"] = Json::Value((Json::UInt)protoConfig->key_direction);
            root["tls_wrap"]["key"] = Json::Value(protoConfig->tls_key.render());
        }

        // SSL parameters
        if (sslConfig)
        {
            json_pem(root, "ca", sslConfig->extract_ca());
            json_pem(root, "crl", sslConfig->extract_crl());
            json_pem(root, "cert", sslConfig->extract_cert());

            // JSON config is aimed to users, therefore we do not export the raw private
            // key, but only some basic info
            PKType::Type priv_key_type = sslConfig->private_key_type();
            if (priv_key_type != PKType::PK_NONE)
            {
                root["key"] = Json::Value(Json::objectValue);
                root["key"]["type"] = Json::Value(sslConfig->private_key_type_string());
                root["key"]["length"] = Json::Value((Json::UInt)sslConfig->private_key_length());
            }

            std::vector<std::string> extra_certs = sslConfig->extract_extra_certs();
            if (extra_certs.size() > 0)
            {
                root["extra_certs"] = Json::Value(Json::arrayValue);
                for (auto cert = extra_certs.begin(); cert != extra_certs.end(); cert++)
                {
                    if (!cert->empty())
                        root["extra_certs"].append(Json::Value(*cert));
                }
            }
        }

        root["cipher"] = Json::Value(CryptoAlgs::name(protoConfig->dc.cipher(), "none"));
        root["auth"] = Json::Value(CryptoAlgs::name(protoConfig->dc.digest(), "none"));
        if (protoConfig->comp_ctx.type() != CompressContext::NONE)
            root["compression"] = Json::Value(protoConfig->comp_ctx.str());
        root["keepalive"] = Json::Value(Json::objectValue);
        root["keepalive"]["ping"] = Json::Value((Json::UInt)protoConfig->keepalive_ping.to_seconds());
        root["keepalive"]["timeout"] = Json::Value((Json::UInt)protoConfig->keepalive_timeout.to_seconds());
        root["tun_mtu"] = Json::Value((Json::UInt)protoConfig->tun_mtu);
        root["reneg_sec"] = Json::Value((Json::UInt)protoConfig->renegotiate.to_seconds());

        return root.toStyledString();
    }

#endif /* HAVE_CONFIG_JSONCPP */

  private:
    static void print_pem(std::ostream &os, std::string label, std::string pem)
    {
        if (pem.empty())
            return;
        os << "<" << label << ">" << std::endl
           << pem << "</" << label << ">" << std::endl;
    }

#ifdef HAVE_CONFIG_JSONCPP

    static void json_pem(Json::Value &obj, std::string key, std::string pem)
    {
        if (pem.empty())
            return;
        obj[key] = Json::Value(pem);
    }

#endif /* HAVE_CONFIG_JSONCPP */

    static bool parse_auth_user_pass(const OptionList &options, std::vector<std::string> *user_pass)
    {
        return UserPass::parse(options, "auth-user-pass", 0, user_pass);
    }

    static void process_setenv_opt(OptionList &options)
    {
        for (OptionList::iterator i = options.begin(); i != options.end(); ++i)
        {
            Option &o = *i;
            if (o.size() >= 3 && o.ref(0) == "setenv" && o.ref(1) == "opt")
            {
                o.remove_first(2);
                o.enableWarnOnly();
            }
        }
    }

    static bool is_autologin(const OptionList &options,
                             const bool auth_user_pass,
                             const std::vector<std::string> &user_pass)
    {
        if (auth_user_pass && user_pass.size() >= 2) // embedded password?
            return true;
        else
        {
            const Option *autologin = options.get_ptr("AUTOLOGIN");
            if (autologin)
                return string::is_true(autologin->get_optional(1, 16));
            else
            {
                bool ret = !auth_user_pass;
                if (ret)
                {
                    // External PKI profiles from AS don't declare auth-user-pass,
                    // and we have no way of knowing if they are autologin unless
                    // we examine their cert, which requires accessing the system-level
                    // cert store on the client.  For now, we are going to assume
                    // that External PKI profiles from the AS are always userlogin,
                    // unless explicitly overriden by AUTOLOGIN above.
                    if (options.exists("EXTERNAL_PKI"))
                        return false;
                }
                return ret;
            }
        }
    }

    static bool is_external_pki(const OptionList &options)
    {
        const Option *epki = options.get_ptr("EXTERNAL_PKI");
        if (epki)
            return string::is_true(epki->get_optional(1, 16));
        else
        {
            const Option *cert = options.get_ptr("cert");
            const Option *key = options.get_ptr("key");
            return !cert || !key;
        }
    }

    void reset_pod()
    {
        error_ = autologin_ = externalPki_ = staticChallengeEcho_ = false;
        privateKeyPasswordRequired_ = hasEmbeddedPassword_ = false;
        pushPeerInfo_ = false;
        allowPasswordSave_ = clientCertEnabled_ = true;
    }

    bool parse_bool(const Option &o, const std::string &title, const size_t index)
    {
        const std::string parm = o.get(index, 16);
        if (parm == "0")
            return false;
        else if (parm == "1")
            return true;
        else
            throw option_error(title + ": parameter must be 0 or 1");
    }

    bool error_;
    std::string message_;
    std::string userlockedUsername_;
    std::string profileName_;
    std::string friendlyName_;
    bool autologin_;
    bool clientCertEnabled_;
    bool externalPki_;
    bool pushPeerInfo_;
    std::string staticChallenge_;
    bool staticChallengeEcho_;
    bool privateKeyPasswordRequired_;
    bool allowPasswordSave_;
    ServerList serverList_;
    bool hasEmbeddedPassword_;
    std::string embeddedPassword_;
    RemoteList::Ptr remoteList;
    RemoteItem firstRemoteListItem_;
    PeerInfo::Set::Ptr peerInfoUV_;
    ProtoContext::Config::Ptr protoConfig;
    SSLLib::SSLAPI::Config::Ptr sslConfig;
    std::string dev;
    std::string windowsDriver_;
};
} // namespace openvpn

#endif
