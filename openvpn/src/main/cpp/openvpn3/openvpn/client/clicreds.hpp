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

// This class encapsulates the state of authentication credentials
// maintained by an OpenVPN client.  It understands dynamic
// challenge/response cookies, and Session Token IDs (where the
// password in the object is wiped and replaced by a token used
// for further authentications).

#ifndef OPENVPN_CLIENT_CLICREDS_H
#define OPENVPN_CLIENT_CLICREDS_H

#include <string>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/transport/protocol.hpp>
#include <openvpn/auth/cr.hpp>

namespace openvpn {

class ClientCreds : public RC<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<ClientCreds> Ptr;

    ClientCreds() = default;

    void set_username(const std::string &username_arg)
    {
        username = username_arg;
    }

    void set_password(const std::string &password_arg)
    {
        password = password_arg;
        if (!password.empty())
        {
            password_needed_ = true;
        }
    }

    void set_http_proxy_username(const std::string &username)
    {
        http_proxy_user = username;
    }

    void set_http_proxy_password(const std::string &password)
    {
        http_proxy_pass = password;
    }

    void set_response(const std::string &response_arg)
    {
        response = response_arg;
        if (!response.empty())
        {
            need_user_interaction_ = true;
        }
    }

    void set_dynamic_challenge_cookie(const std::string &cookie, const std::string &username)
    {
        if (!cookie.empty())
            dynamic_challenge.reset(new ChallengeResponse(cookie, username));
    }

    void set_session_id(const std::string &user, const std::string &sess_id)
    {
        if (dynamic_challenge)
        {
            session_id_username = dynamic_challenge->get_username();
            // for dynamic challenge we use dynamic password only once
            dynamic_challenge.reset();
        }
        else if (!user.empty())
        {
            session_id_username = user;
        }

        // response is used only once
        response.clear();

        session_id = sess_id;
    }

    std::string get_username() const
    {
        if (dynamic_challenge)
            return dynamic_challenge->get_username();
        else if (!session_id_username.empty())
            return session_id_username;
        else
            return username;
    }

    std::string get_password() const
    {
        if (dynamic_challenge)
            return dynamic_challenge->construct_dynamic_password(response);
        else if (response.empty())
        {
            if (!session_id.empty())
                return session_id;
            else
                return password;
        }
        else
            return ChallengeResponse::construct_static_password(password, response);
    }

    std::string get_http_proxy_username() const
    {
        return http_proxy_user;
    }

    std::string get_http_proxy_password() const
    {
        return http_proxy_pass;
    }

    bool username_defined() const
    {
        return !username.empty();
    }

    bool password_defined() const
    {
        return !password.empty();
    }

    bool http_proxy_username_defined() const
    {
        return !http_proxy_user.empty();
    }

    bool http_proxy_password_defined() const
    {
        return !http_proxy_pass.empty();
    }

    bool session_id_defined() const
    {
        return !session_id.empty();
    }

    void purge_session_id()
    {
        OPENVPN_LOG("Clearing session-id");
        session_id.clear();
        session_id_username.clear();
    }

    void purge_user_pass()
    {
        OPENVPN_LOG("Clearing credentials");
        username.clear();
        password.clear();
    }

    void save_username_for_session_id()
    {
        if (session_id_username.empty())
        {
            session_id_username = username;
        }
    }

    void set_need_user_interaction()
    {
        need_user_interaction_ = true;
    }

    bool need_user_interaction() const
    {
        return need_user_interaction_;
    }

    bool password_needed() const
    {
        return password_needed_;
    }

    std::string auth_info() const
    {
        std::string ret;
        if (dynamic_challenge)
        {
            ret = "DynamicChallenge";
        }
        else if (response.empty())
        {
            if (!username.empty())
            {
                ret += "Username";
            }
            else if (!session_id_username.empty())
            {
                ret += "UsernameSessionId";
            }
            else
            {
                ret += "UsernameEmpty";
            }
            ret += '/';
            if (!session_id.empty())
            {
                ret += "SessionID";
            }
            else if (!password.empty())
            {
                ret += "Password";
            }
            else
            {
                ret += "PasswordEmpty";
            }
        }
        else
        {
            ret = "StaticChallenge";
        }
        return ret;
    }

  private:
    // Standard credentials
    std::string username;
    std::string password;

    // HTTP proxy credentials
    std::string http_proxy_user;
    std::string http_proxy_pass;

    std::string session_id;
    std::string session_id_username;

    // Response to a challenge
    std::string response;

    // Need user interaction to authenticate - such as static/dynamic challenge or SAML
    bool need_user_interaction_ = false;

    // Non-empty password provided
    bool password_needed_ = false;

    // Info describing a dynamic challenge
    ChallengeResponse::Ptr dynamic_challenge;
};

} // namespace openvpn

#endif
