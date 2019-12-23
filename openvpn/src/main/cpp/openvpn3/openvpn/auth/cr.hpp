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

// Encapsulate the state of a static or dynamic authentication challenge.

#ifndef OPENVPN_AUTH_CR_H
#define OPENVPN_AUTH_CR_H

#include <string>
#include <sstream>
#include <vector>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/base64.hpp>
#include <openvpn/common/split.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/common/string.hpp>

// Static Challenge response:
//   SCRV1:<BASE64_PASSWORD>:<BASE64_RESPONSE>
//
// Dynamic Challenge:
//   CRV1:<FLAGS>:<STATE_ID>:<BASE64_USERNAME>:<CHALLENGE_TEXT>
//   FLAGS is a comma-separated list of options:
//     E -- echo
//     R -- response required
//
// Dynamic Challenge response:
//   Username: [username decoded from username_base64]
//   Password: CRV1::<STATE_ID>::<RESPONSE_TEXT>

namespace openvpn {
  class ChallengeResponse : public RC<thread_unsafe_refcount> {
  public:
    typedef RCPtr<ChallengeResponse> Ptr;

    OPENVPN_SIMPLE_EXCEPTION(dynamic_challenge_parse_error);
    OPENVPN_SIMPLE_EXCEPTION(static_challenge_parse_error);

    ChallengeResponse()
      : echo(false), response_required(false)
    {
    }

    explicit ChallengeResponse(const std::string& cookie)
      : echo(false), response_required(false)
    {
      init(cookie);
    }

    ChallengeResponse(const std::string& cookie, const std::string& user)
      : echo(false), response_required(false)
    {
      if (!is_dynamic(cookie) && cookie.find_first_of(':') == std::string::npos)
	{
	  state_id = cookie;
	  username = user;
	}
      else
	init(cookie);
    }

    void init(const std::string& cookie)
    {
      typedef std::vector<std::string> StringList;
      StringList sl;
      sl.reserve(5);
      Split::by_char_void<StringList, NullLex, Split::NullLimit>(sl, cookie, ':', 0, 4);
      if (sl.size() != 5)
	throw dynamic_challenge_parse_error();
      if (sl[0] != "CRV1")
	throw dynamic_challenge_parse_error();

      // parse options
      {
	StringList opt;
	opt.reserve(2);
	Split::by_char_void<StringList, NullLex, Split::NullLimit>(opt, sl[1], ',');
	for (StringList::const_iterator i = opt.begin(); i != opt.end(); ++i)
	  {
	    if (*i == "E")
	      echo = true;
	    else if (*i == "R")
	      response_required = true;
	  }
      }

      // save state ID
      state_id = sl[2];

      // save username
      try {
	username = base64->decode(sl[3]);
      }
      catch (const Base64::base64_decode_error&)
	{
	  throw dynamic_challenge_parse_error();
	}

      // save challenge
      challenge_text = sl[4];
    }

    static bool is_dynamic(const std::string& s)
    {
      return string::starts_with(s, "CRV1:");
    }

    static bool is_static(const std::string& s)
    {
      return string::starts_with(s, "SCRV1:");
    }

    static void validate_dynamic(const std::string& cookie)
    {
      ChallengeResponse cr(cookie);
    }

    std::string construct_dynamic_password(const std::string& response) const
    {
      std::ostringstream os;
      os << "CRV1::" << state_id << "::" << response;
      return os.str();
    }

    static std::string construct_static_password(const std::string& password,
						 const std::string& response)
    {
      std::ostringstream os;
      os << "SCRV1:" << base64->encode(password) << ':' << base64->encode(response);
      return os.str();
    }

    static void parse_static_cookie(const std::string& cookie,
				    std::string& password,
				    std::string& response)
    {
      typedef std::vector<std::string> StringList;
      StringList sl;
      sl.reserve(3);
      Split::by_char_void<StringList, NullLex, Split::NullLimit>(sl, cookie, ':');
      if (sl.size() != 3)
	throw static_challenge_parse_error();
      if (sl[0] != "SCRV1")
	throw static_challenge_parse_error();

      // get password
      try {
	password = base64->decode(sl[1]);
      }
      catch (const Base64::base64_decode_error&)
	{
	  throw static_challenge_parse_error();
	}

      // get response
      try {
	response = base64->decode(sl[2]);
      }
      catch (const Base64::base64_decode_error&)
	{
	  throw static_challenge_parse_error();
	}
    }

    static std::string generate_dynamic_challenge(const std::string& session_token,
						  const std::string& username,
						  const std::string& challenge,
						  const bool echo,
						  const bool response_required)
    {
      std::ostringstream os;
      bool comma = false;
      os << "CRV1:";
      if (echo)
	{
	  if (comma)
	    os << ",";
	  os << "E";
	  comma = true;
	}
      if (response_required)
	{
	  if (comma)
	    os << ",";
	  os << "R";
	  comma = true;
	}
      os << ':' << session_token;
      os << ':' << base64->encode(username);
      os << ':' << challenge;
      return os.str();
    }

    const std::string& get_state_id() const { return state_id; }
    const std::string& get_username() const { return username; }
    bool get_echo() const { return echo; }
    bool get_response_required() const { return response_required; }
    const std::string& get_challenge_text() const { return challenge_text; }

  private:
    bool echo;
    bool response_required;
    std::string state_id;
    std::string username;
    std::string challenge_text;
  };
}

#endif
