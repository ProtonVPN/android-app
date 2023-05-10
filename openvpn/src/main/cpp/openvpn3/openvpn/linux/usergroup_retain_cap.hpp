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

// Drop root privileges but retain one or more Linux capabilities

#pragma once

#include <sys/capability.h>

#include <string>
#include <vector>
#include <utility>
#include <initializer_list>

#include <openvpn/common/numeric_cast.hpp>
#include <openvpn/common/usergroup.hpp>

#ifndef OPENVPN_PLATFORM_LINUX
#error SetUserGroupRetainCap requires Linux
#endif

namespace openvpn {

class SetUserGroupRetainCap : public SetUserGroup
{
  public:
    SetUserGroupRetainCap(const std::string &user,
                          const std::string &group,
                          const bool strict,
                          std::initializer_list<cap_value_t> retain_caps_arg)
        : SetUserGroup(user, group, strict),
          retain_caps(retain_caps_arg)
    {
        grab_root();
    }

    SetUserGroupRetainCap(const char *user,
                          const char *group,
                          const bool strict,
                          std::initializer_list<cap_value_t> retain_caps_arg)
        : SetUserGroup(user, group, strict),
          retain_caps(retain_caps_arg)
    {
        grab_root();
    }

    // call first in all threads before user/group downgrade
    virtual void pre_thread() const override
    {
        if (!pw)
            return;

        // create a capabilities object
        Capabilities cap("pre_thread");

        // set retained capabilities + setuid/setgid
        cap.set_flag_with_setuid_setgid(retain_caps);

        // commit it to kernel
        cap.set_proc();

        // retain the capabilities across identity change
        if (::prctl(PR_SET_KEEPCAPS, 1L))
        {
            const int eno = errno;
            OPENVPN_THROW(user_group_err, "SetUserGroupRetainCap prctl PR_SET_KEEPCAPS fail: " << strerror_str(eno));
        }
    }

    // call once after pre_thread() called in each thread
    virtual void activate() const override
    {
        if (!pw)
        {
            SetUserGroup::activate();
            return;
        }

        // set GID/Groups
        do_setgid_setgroups();

        // drop extra privileges (aside from capabilities)
        if (::setresuid(pw->pw_uid, pw->pw_uid, pw->pw_uid))
        {
            const int eno = errno;
            OPENVPN_THROW(user_group_err, "SetUserGroupRetainCap setresuid user fail: " << strerror_str(eno));
        }

        // retain core dumps after UID/GID downgrade
        retain_core_dumps();

        // logging
        {
            Capabilities cap("logging");
            cap.set_flag(retain_caps);
            OPENVPN_LOG("UID [" << cap.to_string() << "] set to '" << user_name << '\'');
        }
    }

    // call in all threads after activate()
    virtual void post_thread() const override
    {
        if (!pw)
            return;

        // create a capabilities object
        Capabilities cap("post_thread");

        // set retained capabilities
        cap.set_flag(retain_caps);

        // commit it to kernel
        cap.set_proc();
    }

  private:
    class Capabilities
    {
      public:
        Capabilities(std::string title_arg)
            : capabilities(::cap_init()),
              title(std::move(title_arg))
        {
        }

        ~Capabilities()
        {
            ::cap_free(capabilities);
        }

        void set_flag(const std::vector<cap_value_t> &caps)
        {
            if (::cap_set_flag(capabilities, CAP_PERMITTED, numeric_cast<int>(caps.size()), caps.data(), CAP_SET)
                || ::cap_set_flag(capabilities, CAP_EFFECTIVE, numeric_cast<int>(caps.size()), caps.data(), CAP_SET))
            {
                const int eno = errno;
                OPENVPN_THROW(user_group_err, "SetUserGroupRetainCap::Capabilities: cap_set_flag " << title << " fail: " << strerror_str(eno));
            }
        }

        void set_flag_with_setuid_setgid(std::vector<cap_value_t> caps)
        {
            caps.push_back(CAP_SETUID);
            caps.push_back(CAP_SETGID);
            set_flag(caps);
        }

        void set_proc()
        {
            if (::cap_set_proc(capabilities))
            {
                const int eno = errno;
                OPENVPN_THROW(user_group_err, "SetUserGroupRetainCap::Capabilities: cap_set_proc " << title << " fail: " << strerror_str(eno));
            }
        }

        std::string to_string() const
        {
            char *txt = ::cap_to_text(capabilities, nullptr);
            std::string ret(txt);
            ::cap_free(txt);
            return ret;
        }

      private:
        Capabilities(const Capabilities &) = delete;
        Capabilities &operator=(const Capabilities &) = delete;

        const cap_t capabilities;
        const std::string title;
    };

    void grab_root()
    {
        // get full root privileges
        if (::setresuid(0, 0, 0))
        {
            const int eno = errno;
            OPENVPN_THROW(user_group_err, "SetUserGroupRetainCap setresuid root fail: " << strerror_str(eno));
        }
    }

    const std::vector<cap_value_t> retain_caps;
};

} // namespace openvpn
