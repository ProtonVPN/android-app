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

#ifndef OPENVPN_ADDR_ADDRPAIR_H
#define OPENVPN_ADDR_ADDRPAIR_H

#include <sstream>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/number.hpp>
#include <openvpn/common/split.hpp>
#include <openvpn/addr/ip.hpp>

namespace openvpn {
namespace IP {

// AddrMaskPair is basically an object that combines an IP address (v4 or v6)
// with a netmask or prefix length.
struct AddrMaskPair
{
  public:
    OPENVPN_EXCEPTION(addr_pair_mask_parse_error);

    class StringPair
    {
      public:
        OPENVPN_SIMPLE_EXCEPTION(addr_pair_string_error);

        StringPair()
            : size_(0)
        {
        }

        explicit StringPair(const std::string &s1)
            : size_(1)
        {
            data[0] = s1;
        }

        explicit StringPair(const std::string &s1, const std::string &s2)
            : size_(2)
        {
            data[0] = s1;
            data[1] = s2;
        }

        void push_back(const std::string &s)
        {
            if (size_ < 2)
                data[size_++] = s;
            else
                throw addr_pair_string_error();
        }

        const std::string &operator[](const size_t i) const
        {
            if (i >= 2)
                throw addr_pair_string_error();
            return data[i];
        }

        std::string &operator[](const size_t i)
        {
            if (i >= 2)
                throw addr_pair_string_error();
            return data[i];
        }

        size_t size() const
        {
            return size_;
        }

        std::string render() const
        {
            switch (size_)
            {
            case 1:
                return data[0];
            case 2:
                return data[0] + "/" + data[1];
            default:
                return "";
            }
        }

      private:
        std::string data[2];
        unsigned int size_;
    };

    static AddrMaskPair from_string(const std::string &s1, const std::string &s2, const char *title = nullptr)
    {
        try
        {
            if (s2.empty())
            {
                const StringPair pair = Split::by_char<StringPair, NullLex, Split::NullLimit>(s1, '/');
                return from_string_impl(pair, title);
            }
            else
            {
                const StringPair pair(s1, s2);
                return from_string_impl(pair, title);
            }
        }
        catch (const std::exception &e)
        {
            const StringPair pair(s1, s2);
            error(e, pair.render(), title);
        }
        return AddrMaskPair(); // NOTREACHED
    }

    static AddrMaskPair from_string(const std::string &s, const char *title = nullptr)
    {
        try
        {
            const StringPair pair = Split::by_char<StringPair, NullLex, Split::NullLimit>(s, '/');
            return from_string_impl(pair, title);
        }
        catch (const std::exception &e)
        {
            error(e, s, title);
        }
        return AddrMaskPair(); // NOTREACHED
    }

    static AddrMaskPair from_string(const StringPair &pair, const char *title = nullptr)
    {
        try
        {
            return from_string_impl(pair, title);
        }
        catch (const std::exception &e)
        {
            error(e, pair.render(), title);
        }
        return AddrMaskPair(); // NOTREACHED
    }

    std::string to_string(const bool netmask_form = false) const
    {
        std::ostringstream os;
        if (netmask_form)
            os << addr.to_string() << '/' << netmask.to_string();
        else
            os << addr.to_string() << '/' << netmask.prefix_len();
        return os.str();
    }

    bool is_canonical() const
    {
        return (addr & netmask) == addr;
    }

    Addr::Version version() const
    {
        const Addr::Version v1 = addr.version();
        const Addr::Version v2 = netmask.version();
        if (v1 == v2)
            return v1;
        else
            return Addr::UNSPEC;
    }

    Addr addr;
    Addr netmask;

  private:
    static void error(const std::exception &e, const std::string &s, const char *title)
    {
        if (!title)
            title = "";
        OPENVPN_THROW(addr_pair_mask_parse_error, "AddrMaskPair parse error '" << title << "': " << s << " : " << e.what());
    }

    static AddrMaskPair from_string_impl(const StringPair &pair, const char *title = nullptr)
    {
        AddrMaskPair ret;
        if (pair.size() == 1 || pair.size() == 2)
        {
            ret.addr = Addr::from_string(pair[0], title);
            if (pair.size() == 2 && !pair[1].empty())
            {
                if (is_number(pair[1].c_str()))
                    ret.netmask = Addr::netmask_from_prefix_len(ret.addr.version(),
                                                                parse_number_throw<unsigned int>(pair[1], "prefix length"));
                else
                    ret.netmask = Addr::from_string(pair[1]);
                ret.netmask.prefix_len(); // verify that netmask is ok
            }
            else
                ret.netmask = Addr::from_zero_complement(ret.addr.version());
            ret.addr.verify_version_consistency(ret.netmask);
        }
        else
            throw addr_pair_mask_parse_error("only one or two address terms allowed");
        return ret;
    }
};
OPENVPN_OSTREAM(AddrMaskPair, to_string)
} // namespace IP
} // namespace openvpn

#endif
