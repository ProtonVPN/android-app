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

  class ClientCreds : public RC<thread_unsafe_refcount> {
  public:
    typedef RCPtr<ClientCreds> Ptr;

    ClientCreds() : allow_cache_password(false),
		    password_save_defined(false),
		    replace_password_with_session_id(false),
		    did_replace_password_with_session_id(false) {}

    void set_username(const std::string& username_arg)
    {
      username = username_arg;
    }

    void set_password(const std::string& password_arg)
    {
      password = password_arg;
      did_replace_password_with_session_id = false;
    }

    void set_http_proxy_username(const std::string& username)
    {
      http_proxy_user = username;
    }

    void set_http_proxy_password(const std::string& password)
    {
      http_proxy_pass = password;
    }

    void set_response(const std::string& response_arg)
    {
      response = response_arg;
    }

    void set_dynamic_challenge_cookie(const std::string& cookie, const std::string& username)
    {
      if (!cookie.empty())
	dynamic_challenge.reset(new ChallengeResponse(cookie, username));
    }

    void set_replace_password_with_session_id(const bool value)
    {
      replace_password_with_session_id = value;
    }

    void enable_password_cache(const bool value)
    {
      allow_cache_password = value;
    }

    bool get_replace_password_with_session_id() const
    {
      return replace_password_with_session_id;
    }

    void set_session_id(const std::string& user, const std::string& sess_id)
    {
      // force Session ID use if dynamic challenge is enabled
      if (dynamic_challenge && !replace_password_with_session_id)
	replace_password_with_session_id = true;

      if (replace_password_with_session_id)
	{
	  if (allow_cache_password && !password_save_defined)
	    {
	      password_save = password;
	      password_save_defined = true;
	    }
	  password = sess_id;
	  response = "";
	  if (dynamic_challenge)
	    {
	      username = dynamic_challenge->get_username();
	      dynamic_challenge.reset();
	    }
	  else if (!user.empty())
	    username = user;
	  did_replace_password_with_session_id = true;
	}
    }

    std::string get_username() const
    {
      if (dynamic_challenge)
	return dynamic_challenge->get_username();
      else
	return username;
    }

    std::string get_password() const
    {
      if (dynamic_challenge)
	return dynamic_challenge->construct_dynamic_password(response);
      else if (response.empty())
	return password;
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
      return did_replace_password_with_session_id;
    }

    // If we have a saved password that is not a session ID,
    // restore it and wipe any existing session ID.
    bool reset_to_cached_password()
    {
      if (password_save_defined)
	{
	  password = password_save;
	  password_save.clear();
	  password_save_defined = false;
	  did_replace_password_with_session_id = false;
	  return true;
	}
      else
	return false;
    }

    void purge_session_id()
    {
      if (!reset_to_cached_password())
	{
	  password.clear();
	  did_replace_password_with_session_id = false;
	}
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
	    ret += "Username";
	  else
	    ret += "UsernameEmpty";
	  ret += '/';
	  if (!password.empty())
	    {
	      if (did_replace_password_with_session_id)
		ret += "SessionID";
	      else
		ret += "Password";
	    }
	  else
	    ret += "PasswordEmpty";
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

    // Password caching
    bool allow_cache_password;
    bool password_save_defined;
    std::string password_save;

    // Response to challenge
    std::string response;

    // Info describing a dynamic challenge
    ChallengeResponse::Ptr dynamic_challenge;

    // If true, on successful connect, we will replace the password
    // with the session ID we receive from the server.
    bool replace_password_with_session_id;

    // true if password has been replaced with Session ID
    bool did_replace_password_with_session_id;
  };

}

#endif
