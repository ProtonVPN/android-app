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

#include <cassert>
#include <openvpn/common/exception.hpp>

namespace openvpn {

OPENVPN_EXCEPTION_WITH_CODE(option_error, ERR_PROFILE_OPTION, ERR_INVALID_OPTION_DNS, ERR_INVALID_OPTION_CRYPTO, ERR_INVALID_CONFIG, ERR_INVALID_OPTION_PUSHED, ERR_INVALID_OPTION_VAL);

/// \cond KNOWN_WARNINGS
/// error: documented symbol 'std::string openvpn::option_error::code2string' was not declared or defined.

inline std::string option_error::code2string(option_error_code code)
{
    static const char *code_strings[] = {
        "ERR_INVALID_OPTION_DNS",
        "ERR_INVALID_OPTION_CRYPTO",
        "ERR_INVALID_CONFIG",
        "ERR_INVALID_OPTION_PUSHED",
        "ERR_INVALID_OPTION_VAL"};

    assert(code < sizeof(code_strings));
    return code_strings[code];
}

/// \endcond

} // namespace openvpn
