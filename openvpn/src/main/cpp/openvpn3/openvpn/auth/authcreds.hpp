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

#ifndef OPENVPN_AUTH_AUTHCREDS
#define OPENVPN_AUTH_AUTHCREDS

#include <utility> // for std::move
#include <string>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/common/options.hpp>
#include <openvpn/common/unicode.hpp>
#include <openvpn/buffer/safestr.hpp>
#include <openvpn/auth/validatecreds.hpp>

namespace openvpn {

class AuthCreds : public RC<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<AuthCreds> Ptr;

    AuthCreds(std::string &&username_arg,
              SafeString &&password_arg,
              const std::string &peer_info_str)
        : username(std::move(username_arg)),
          password(std::move(password_arg))
    {
        peer_info.parse_from_peer_info(peer_info_str, nullptr);
        peer_info.update_map();
    }

    // for unit test
    AuthCreds(std::string username_arg,
              SafeString password_arg,
              OptionList peer_info_arg)
        : username(std::move(username_arg)),
          password(std::move(password_arg)),
          peer_info(std::move(peer_info_arg))
    {
    }

    bool defined() const
    {
        return !username.empty();
    }

    bool is_valid_user_pass(const bool strict) const
    {
        return ValidateCreds::is_valid(ValidateCreds::USERNAME, username, strict)
               && ValidateCreds::is_valid(ValidateCreds::PASSWORD, password, strict);
    }

    bool is_valid(const bool strict) const
    {
        return defined() && is_valid_user_pass(strict);
    }

    void wipe_password()
    {
        password.wipe();
    }

    std::string to_string() const
    {
        std::ostringstream os;
        os << "*** AuthCreds ***" << std::endl;
        os << "user: '" << username << "'" << std::endl;
        if (password.empty())
        {
            os << "pass: (empty)" << std::endl;
        }
        else
        {
            os << "pass: (non-empty)" << std::endl;
        }
        os << "peer info:" << std::endl;
        os << peer_info.render(Option::RENDER_BRACKET | Option::RENDER_NUMBER);
        return os.str();
    }

    std::string username;
    SafeString password;
    OptionList peer_info;
};

} // namespace openvpn

#endif
