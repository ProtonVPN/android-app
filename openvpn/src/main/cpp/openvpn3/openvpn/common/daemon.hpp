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

#ifndef OPENVPN_COMMON_DAEMON_H
#define OPENVPN_COMMON_DAEMON_H

#include <sys/types.h>
#include <unistd.h>

#include <string>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/to_string.hpp>
#include <openvpn/common/file.hpp>
#include <openvpn/common/logrotate.hpp>
#include <openvpn/common/redir.hpp>
#include <openvpn/common/usergroup.hpp>
#include <openvpn/common/logsetup.hpp>

namespace openvpn {

  OPENVPN_EXCEPTION(daemon_err);

  class LogReopen : public LogSetup
  {
  public:
    LogReopen(const std::string& log_fn,
	      const bool combine_out_err)
      : log_fn_(log_fn),
	combine_out_err_(combine_out_err)
    {
    }

    virtual void reopen() override
    {
      try {
	// open redirection log file, but don't redirect yet
	RedirectStd redir(std::string(),
			  log_fn_,
			  RedirectStd::FLAGS_APPEND,
			  RedirectStd::MODE_USER_GROUP,
			  combine_out_err_);

	// now do the redirect
	redir.redirect();
      }
      catch (const std::exception& e)
	{
	  std::cerr << "LogReopen: " << e.what() << std::endl;
	}
    }

  private:
    const std::string log_fn_;
    const bool combine_out_err_;
  };

  inline LogSetup::Ptr log_setup(const std::string& log_fn,
				 const SetUserGroup* user_group,
				 const bool log_append,
				 const int log_versions,
				 const bool stdin_to_dev_null,
				 const bool combine_out_err)
  {
    if (!log_append && log_versions >= 1)
      log_rotate(log_fn, log_versions);
    RedirectStd redir(stdin_to_dev_null ? "/dev/null" : "",
		      log_fn,
		      log_append ? RedirectStd::FLAGS_APPEND : RedirectStd::FLAGS_OVERWRITE,
		      RedirectStd::MODE_USER_GROUP,
		      combine_out_err);
    // if user_group specified, do chown on log file
    try {
      if (user_group && redir.out.defined())
	user_group->chown(redir.out(), log_fn);
    }
    catch (const std::exception&)
      {
      }
    redir.redirect();

    // possibly return a LogReopen object
    if (!log_versions)
      return LogSetup::Ptr(new LogReopen(log_fn, combine_out_err));
    else
      return LogSetup::Ptr();
  }

  inline void daemonize()
  {
    if (daemon(1, 1) < 0)
      throw daemon_err("daemon() failed");
  }

  inline LogSetup::Ptr daemonize(const std::string& log_fn,
				 const SetUserGroup* user_group,
				 const bool log_append,
				 const int log_versions)
  {
    LogSetup::Ptr ret = log_setup(log_fn, user_group, log_append, log_versions, true, true);
    daemonize();
    return ret;
  }

  inline void write_pid(const std::string& fn)
  {
    write_string(fn, to_string(::getpid()) + '\n');
  }

  class WritePid
  {
  public:
    WritePid(const char *pid_fn_arg) // must remain in scope for lifetime of object
      : pid_fn(pid_fn_arg)
    {
      if (pid_fn)
	write_pid(pid_fn);
    }

    ~WritePid()
    {
      if (pid_fn)
	::unlink(pid_fn);
    }

  private:
    WritePid(const WritePid&) = delete;
    WritePid& operator=(const WritePid&) = delete;

    const char *const pid_fn;
  };
}

#endif
