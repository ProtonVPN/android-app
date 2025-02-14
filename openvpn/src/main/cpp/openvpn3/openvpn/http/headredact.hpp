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

#pragma once

#include <string>
#include <regex>

namespace openvpn::HTTP {

inline std::string headers_redact(const std::string &headers)
{
#ifdef OPENVPN_HTTP_HEADERS_NO_REDACT
    return headers;
#else
    // Alternative regex implementation:
    // static const std::regex re(R"((authorization[\s:=]+basic\s+)([^\s]+))", std::regex_constants::ECMAScript | std::regex_constants::icase);
    // return std::regex_replace(headers, re, "$1[REDACTED]");
    std::stringstream result;

    std::istringstream iss(headers);

    for (std::string line; std::getline(iss, line);)
    {
        if (auto authpos = line.find("Authorization: "); authpos != std::string::npos)
        {
            auto auth = line.substr(authpos);
            auto argument = auth.substr(auth.find(' ') + 1);
            std::string authtype;
            if (auto arg1 = argument.find(' '); arg1 != std::string::npos)
            {
                authtype = argument.substr(0, arg1);
            }
            result << line.substr(0, authpos) << "Authorization: " << authtype << " [REDACTED]\r" << std::endl;
        }
        else if ((authpos = line.find("authorization=basic ")) != std::string::npos)
        {
            result << line.substr(0, authpos) << "authorization=basic [REDACTED]\r" << std::endl;
        }
        else
        {
            result << line << std::endl;
        }
    }
    return result.str();
#endif
}

} // namespace openvpn::HTTP
