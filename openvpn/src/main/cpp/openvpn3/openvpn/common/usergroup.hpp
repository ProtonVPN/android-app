//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
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

#ifndef OPENVPN_COMMON_USERGROUP_H
#define OPENVPN_COMMON_USERGROUP_H

#include <pwd.h>
#include <grp.h>
#include <unistd.h>
#include <sys/types.h>
#include <errno.h>

#include <string>

#include <openvpn/common/platform.hpp>

#ifdef OPENVPN_PLATFORM_LINUX
#include <sys/prctl.h>
#endif

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/to_string.hpp>
#include <openvpn/common/strerror.hpp>

namespace openvpn {
  // NOTE: -- SetUserGroup object does not own passwd and group
  // objects, therefore *pw and *gr can change under us.
  class SetUserGroup
  {
  public:
    OPENVPN_EXCEPTION(user_group_err);

    SetUserGroup(const std::string& user, const std::string& group, const bool strict)
      : SetUserGroup(user.empty() ? nullptr : user.c_str(),
		     group.empty() ? nullptr : group.c_str(),
		     strict)
    {
    }

    SetUserGroup(const char *user, const char *group, const bool strict)
      : pw(nullptr),
	gr(nullptr)
    {
      if (user)
	{
	  pw = ::getpwnam(user);
	  if (!pw && strict)
	    OPENVPN_THROW(user_group_err, "user lookup failed for '" << user << '\'');
	  user_name = user;
	}
      if (group)
	{
	  gr = ::getgrnam(group);
	  if (!gr && strict)
	    OPENVPN_THROW(user_group_err, "group lookup failed for '" << group << '\'');
	  group_name = group;
	}
    }

    virtual ~SetUserGroup() {}

    const std::string& user() const
    {
      return user_name;
    }

    const std::string& group() const
    {
      return group_name;
    }

    virtual void pre_thread() const
    {
    }

    virtual void post_thread() const
    {
    }

    virtual void activate() const
    {
      do_setgid_setgroups();
      do_setuid();
      retain_core_dumps();
    }

    void chown(const std::string& fn) const
    {
      if (pw && gr)
	{
	  const int status = ::chown(fn.c_str(), uid(), gid());
	  if (status < 0)
	    {
	      const int eno = errno;
	      OPENVPN_THROW(user_group_err, "chown " << user_name << '.' << group_name << ' ' << fn << " : " << strerror_str(eno));
	    }
	}
    }

    void chown(const int fd, const std::string& title) const
    {
      if (pw && gr)
	{
	  const int status = ::fchown(fd, uid(), gid());
	  if (status < 0)
	    {
	      const int eno = errno;
	      OPENVPN_THROW(user_group_err, "chown " << user_name << '.' << group_name << ' ' << title << " : " << strerror_str(eno));
	    }
	}
    }

    void invalidate()
    {
      pw = nullptr;
      gr = nullptr;
    }

    uid_t uid() const
    {
      if (pw)
	return pw->pw_uid;
      else
	return -1;
    }

    gid_t gid() const
    {
      if (gr)
	return gr->gr_gid;
      else
	return -1;
    }

    bool uid_defined() const
    {
      return bool(pw);
    }

    bool gid_defined() const
    {
      return bool(gr);
    }

    bool defined() const
    {
      return uid_defined() && gid_defined();
    }

  protected:
    void do_setgid_setgroups() const
    {
      if (gr)
	{
	  if (::setgid(gr->gr_gid))
	    {
	      const int eno = errno;
	      OPENVPN_THROW(user_group_err, "setgid failed for group '" << group_name << "': " << strerror_str(eno));
	    }
	  gid_t gr_list[1];
	  gr_list[0] = gr->gr_gid;
	  if (::setgroups(1, gr_list))
	    {
	      const int eno = errno;
	      OPENVPN_THROW(user_group_err, "setgroups failed for group '" << group_name << "': " << strerror_str(eno));
	    }
	  OPENVPN_LOG("GID set to '" << group_name << '\'');
	}
    }

    void do_setuid() const
    {
      if (pw)
	{
	  if (::setuid(pw->pw_uid))
	    {
	      const int eno = errno;
	      OPENVPN_THROW(user_group_err, "setuid failed for user '" << user_name << "': " << strerror_str(eno));
	    }
	  OPENVPN_LOG("UID set to '" << user_name << '\'');
	}
    }

    void retain_core_dumps() const
    {
#ifdef OPENVPN_PLATFORM_LINUX
      // retain core dumpability after setgid/setuid
      if (gr || pw)
	{
	  if (::prctl(PR_SET_DUMPABLE, 1))
	    {
	      const int eno = errno;
	      OPENVPN_THROW(user_group_err, "SetUserGroup prctl PR_SET_DUMPABLE fail: " << strerror_str(eno));
	    }
	}
#endif
    }

    std::string user_name;
    std::string group_name;

    struct passwd *pw;
    struct group *gr;
  };
}

#endif
