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

#ifndef OPENVPN_SSL_MSSPARMS_H
#define OPENVPN_SSL_MSSPARMS_H

#include <openvpn/common/options.hpp>
#include <openvpn/common/number.hpp>

namespace openvpn {
struct MSSParms
{
    enum
    {
        MSSFIX_DEFAULT = 1492,
    };

    void parse(const OptionList &opt, bool nothrow = false)
    {
        const Option *o = opt.get_ptr("mssfix");
        if (o)
        {
            const std::string *val = o->get_ptr(1, 16);
            if (val == nullptr)
            {
                if (nothrow)
                {
                    OPENVPN_LOG("Missing mssfix value, mssfix functionality disabled");
                    mssfix_default = false;
                    return;
                }
                else
                    throw option_error("mssfix must have a value");
            }

            const bool status = parse_number_validate<decltype(mssfix)>(*val,
                                                                        16,
                                                                        576,
                                                                        65535,
                                                                        &mssfix);
            if (!status)
            {
                if (nothrow)
                {
                    // no need to warn if mssfix is actually 0
                    if (*val != "0")
                    {
                        OPENVPN_LOG("Invalid mssfix value " << *val << ", mssfix functionality disabled");
                        mssfix_default = false;
                    }
                }
                else
                    throw option_error("mssfix: parse/range issue");
            }
            else
            {
                mssfix_default = false;
            }
            mtu = (o->get_optional(2, 16) == "mtu");
            fixed = (o->get_optional(2, 16) == "fixed");
        }
    }

    unsigned int mssfix = 0; // standard OpenVPN mssfix parm
    bool mtu = false;        // include overhead from IP and TCP/UDP encapsulation
    bool fixed = false;      // use mssfix value without any encapsulation adjustments
    bool mssfix_default = true;
};

struct MSSCtrlParms
{
    MSSCtrlParms(const OptionList &opt)
    {
        mssfix_ctrl = opt.get_num<decltype(mssfix_ctrl)>("mssfix-ctrl", 1, 1250, 256, 65535);
    }

    unsigned int mssfix_ctrl;
};
} // namespace openvpn

#endif
