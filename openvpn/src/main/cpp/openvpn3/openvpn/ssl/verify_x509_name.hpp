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
//

#pragma once

#include <openvpn/common/options.hpp>

namespace openvpn {

/**
 *  Parses the --verify-x509-name configuration option
 *  and provides the logic to validate an X.509 certificate subject
 *  against such an option.
 */
class VerifyX509Name
{
  public:
    enum Mode
    {
        VERIFY_X509_NONE = 0,
        VERIFY_X509_SUBJECT_DN = 1,
        VERIFY_X509_SUBJECT_RDN = 2,
        VERIFY_X509_SUBJECT_RDN_PREFIX = 3
    };

    VerifyX509Name() = default;

    VerifyX509Name(const OptionList &opt, const std::string &relay_prefix = "")
    {
        init(opt, relay_prefix);
    }

    ~VerifyX509Name() = default;

    void init(const OptionList &opt, const std::string &relay_prefix)
    {
        const Option *o = opt.get_ptr(relay_prefix + "verify-x509-name");
        if (o)
        {
            o->min_args(1);
            verify_value = o->get(1, 256);
            // If the mode flag is not present, we default to subject.
            // For details, see openvpn(8) man page.
            mode = parse_x509_verify_mode(o->get_default(2, 256, "subject"));
        }
    }

    std::string get_mode_str() const
    {
        switch (mode)
        {
        case VERIFY_X509_NONE:
            return "VERIFY_X509_NONE";
        case VERIFY_X509_SUBJECT_DN:
            return "VERIFY_X509_SUBJECT_DN";
        case VERIFY_X509_SUBJECT_RDN:
            return "VERIFY_X509_SUBJECT_RDN";
        case VERIFY_X509_SUBJECT_RDN_PREFIX:
            return "VERIFY_X509_SUBJECT_RDN_PREFIX";
        default:
            return "VERIFY_X509_NONE";
        }
    }

    Mode get_mode() const
    {
        return mode;
    }

    bool verify(const std::string &value) const
    {
        switch (mode)
        {
        case VERIFY_X509_NONE:
            // If no verification is configured, it is always a pass
            return true;

        case VERIFY_X509_SUBJECT_DN:
            // The input value is expected to be a full subject DN
            // where a perfect match is expected
            return verify_value == value;

        case VERIFY_X509_SUBJECT_RDN:
            // The input value is expected to be the certificate
            // Common Name (CN), and a perfect patch is expected
            return verify_value == value;

        case VERIFY_X509_SUBJECT_RDN_PREFIX:
            // The input value contains a prefix of the certificate
            // Common Name (CN), where we only require a perfect match
            // only on the matching prefix
            return value.compare(0, verify_value.length(), verify_value) == 0;
        }
        return false;
    }

  private:
    Mode mode = VERIFY_X509_NONE;
    std::string verify_value;

    static Mode parse_x509_verify_mode(const std::string &type)
    {
        if (type == "subject")
        {
            return VERIFY_X509_SUBJECT_DN;
        }
        else if (type == "name")
        {
            return VERIFY_X509_SUBJECT_RDN;
        }
        else if (type == "name-prefix")
        {
            return VERIFY_X509_SUBJECT_RDN_PREFIX;
        }
        throw option_error("Invalid verify-x509-name type: " + type);
    }

}; // class VerifyX509Name
} // namespace openvpn
