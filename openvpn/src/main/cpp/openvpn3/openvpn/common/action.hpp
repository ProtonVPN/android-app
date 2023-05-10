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


#ifndef OPENVPN_COMMON_ACTION_H
#define OPENVPN_COMMON_ACTION_H

#include <vector>
#include <string>
#include <ostream>
#include <sstream>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/destruct.hpp>
#include <openvpn/common/jsonlib.hpp>

namespace openvpn {

struct Action : public RC<thread_unsafe_refcount>
{
    typedef RCPtr<Action> Ptr;

    virtual void execute(std::ostream &os) = 0;
    virtual std::string to_string() const = 0;
#ifdef HAVE_JSON
    virtual Json::Value to_json() const
    {
        throw Exception("Action::to_json() virtual method not implemented");
    }
#endif
    virtual ~Action()
    {
    }
};

class ActionList : public std::vector<Action::Ptr>, public DestructorBase
{
  public:
    typedef RCPtr<ActionList> Ptr;

    ActionList()
    {
        reserve(16);
    }

    void add(Action *action)
    {
        if (action)
            emplace_back(action);
    }

    void add(const Action::Ptr &action)
    {
        if (action)
            push_back(action);
    }

    void add(const ActionList &other)
    {
        insert(end(), other.begin(), other.end());
    }

    bool exists(const Action::Ptr &action) const
    {
        if (action)
        {
            const std::string cmp = action->to_string();
            for (auto &a : *this)
            {
                if (a->to_string() == cmp)
                    return true;
            }
        }
        return false;
    }

    virtual void execute(std::ostream &os)
    {
        Iter i(size(), reverse_);
        while (i())
        {
            if (is_halt())
                return;
            try
            {
                (*this)[i.index()]->execute(os);
            }
            catch (const std::exception &e)
            {
                os << "action exception: " << e.what() << std::endl;
            }
        }
    }

    void execute_log()
    {
        std::ostringstream os;
        execute(os);
        OPENVPN_LOG_STRING(os.str());
    }

    std::string to_string() const
    {
        std::string ret;
        Iter i(size(), reverse_);
        while (i())
        {
            ret += (*this)[i.index()]->to_string();
            ret += '\n';
        }
        return ret;
    }

    void enable_destroy(const bool state)
    {
        enable_destroy_ = state;
    }

    void halt()
    {
        halt_ = true;
    }

    virtual void destroy(std::ostream &os) override // defined by DestructorBase
    {
        if (enable_destroy_)
        {
            execute(os);
            enable_destroy_ = false;
        }
    }

    bool is_halt() const
    {
        return halt_;
    }

  protected:
    class Iter
    {
      public:
        Iter(size_t size, const bool reverse)
        {
            if (reverse)
            {
                idx = size;
                end = -1;
                delta = -1;
            }
            else
            {
                idx = -1;
                end = size;
                delta = 1;
            }
        }

        bool operator()()
        {
            return (idx += delta) != end;
        }

        size_t index() const
        {
            return idx;
        }

      private:
        size_t idx;
        size_t end;
        size_t delta;
    };

    bool reverse_ = false;
    bool enable_destroy_ = false;
    volatile bool halt_ = false;
};

struct ActionListReversed : public ActionList
{
    ActionListReversed()
    {
        reverse_ = true;
    }
};

struct ActionListFactory : public RC<thread_unsafe_refcount>
{
    typedef RCPtr<ActionListFactory> Ptr;

    virtual ActionList::Ptr new_action_list() = 0;
};
} // namespace openvpn

#endif
