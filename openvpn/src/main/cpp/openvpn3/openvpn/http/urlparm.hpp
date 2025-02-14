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

#ifndef OPENVPN_HTTP_URLPARM_H
#define OPENVPN_HTTP_URLPARM_H

#include <string>
#include <sstream>
#include <vector>

#include <openvpn/common/number.hpp>
#include <openvpn/http/urlencode.hpp>
#include <openvpn/http/webexcept.hpp>
#include <openvpn/common/string.hpp>

namespace openvpn::URL {
OPENVPN_EXCEPTION(url_parameter_error);

struct Parm
{
    Parm()
    {
    }

    Parm(const std::string &name_arg, const std::string &value_arg)
        : name(name_arg), value(value_arg)
    {
    }

    std::string to_string() const
    {
        std::ostringstream out;
        out << name << '=' << value;
        return out.str();
    }

    std::string name;
    std::string value;
};

class ParmList : public std::vector<Parm>
{
  public:
    ParmList(const std::string &uri)
    {
        try
        {
            const std::vector<std::string> req_parms = string::split(uri, '?', 1);
            request_ = req_parms[0];
            if (req_parms.size() == 2)
            {
                const std::vector<std::string> kv_list = string::split(req_parms[1], '&');
                for (auto &kvstr : kv_list)
                {
                    const std::vector<std::string> kv = string::split(kvstr, '=', 1);
                    Parm p;
                    p.name = decode(kv[0]);
                    if (kv.size() == 2)
                        p.value = decode(kv[1]);
                    push_back(std::move(p));
                }
            }
        }
        catch (const std::exception &e)
        {
            throw HTTP::WebException(HTTP::Status::BadRequest, e.what());
        }
    }

    const Parm *get(const std::string &key) const
    {
        for (auto &p : *this)
        {
            if (key == p.name)
                return &p;
        }
        return nullptr;
    }

    std::string get_value(const std::string &key) const
    {
        const Parm *p = get(key);
        if (p)
            return p->value;
        else
            return "";
    }

    const std::string &get_value_required(const std::string &key) const
    {
        const Parm *p = get(key);
        if (p)
            return p->value;
        else
            throw url_parameter_error(key + " : not found");
    }

    template <typename T>
    T get_num(const std::string &name, const std::string &short_name, const T default_value) const
    {
        const Parm *p = get(name);
        if (!p && !short_name.empty())
            p = get(short_name);
        if (p)
            return parse_number_throw<T>(p->value, name);
        else
            return default_value;
    }

    template <typename T>
    T get_num_required(const std::string &name, const std::string &short_name) const
    {
        const Parm *p = get(name);
        if (!p && !short_name.empty())
            p = get(short_name);
        if (!p)
            throw url_parameter_error(name + " : not found");
        return parse_number_throw<T>(p->value, name);
    }

    bool get_bool(const std::string &name, const std::string &short_name, const bool default_value) const
    {
        const Parm *p = get(name);
        if (!p && !short_name.empty())
            p = get(short_name);
        if (p)
        {
            if (p->value == "0")
                return false;
            else if (p->value == "1")
                return true;
            else
                throw url_parameter_error(name + ": parameter must be 0 or 1");
        }
        else
            return default_value;
    }

    std::string get_string(const std::string &name, const std::string &short_name) const
    {
        const Parm *p = get(name);
        if (!p && !short_name.empty())
            p = get(short_name);
        if (p)
            return p->value;
        else
            return "";
    }

    std::string get_string_required(const std::string &name, const std::string &short_name) const
    {
        const Parm *p = get(name);
        if (!p && !short_name.empty())
            p = get(short_name);
        if (!p)
            throw url_parameter_error(name + " : not found");
        return p->value;
    }

    std::string to_string() const
    {
        std::ostringstream out;
        for (size_t i = 0; i < size(); ++i)
            out << '[' << i << "] " << (*this)[i].to_string() << std::endl;
        return out.str();
    }

    std::string request(const bool remove_leading_slash) const
    {
        std::string ret = request_;
        if (remove_leading_slash)
        {
            if (ret.length() > 0 && ret[0] == '/')
                ret = ret.substr(1);
            else
                throw HTTP::WebException(HTTP::Status::BadRequest, "URI missing leading slash");
        }
        if (ret.empty())
            throw HTTP::WebException(HTTP::Status::BadRequest, "URI resource is empty");
        return ret;
    }

    const std::string &request() const
    {
        return request_;
    }

  private:
    std::string request_;
};

} // namespace openvpn::URL

#endif
