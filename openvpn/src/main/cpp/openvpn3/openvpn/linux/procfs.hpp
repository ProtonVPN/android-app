// Private Gateway
// Copyright (C) 2012-2017 OpenVPN Technologies, Inc.
// All rights reserved

#ifndef OPENVPN_LINUX_PROCFS_H
#define OPENVPN_LINUX_PROCFS_H

#include <string>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/file.hpp>
#include <openvpn/common/sleep.hpp>
#include <openvpn/common/stat.hpp>
#include <openvpn/common/format.hpp>
#include <openvpn/common/action.hpp>
#include <openvpn/common/stop.hpp>

namespace openvpn {

  class ProcFS : public Action
  {
  public:
    OPENVPN_EXCEPTION(procfs_error);

    ProcFS(std::string fn_arg, std::string text_arg)
      : fn(std::move(fn_arg)),
	text(std::move(text_arg))
    {
    }

    virtual void execute(std::ostream& os) override
    {
      os << to_string() << std::endl;
      try {
	write_sys(fn, text);
      }
      catch (const std::exception& e)
	{
	  os << "ProcFS exception: " << e.what() << std::endl;
	}
    }

    virtual std::string to_string() const override
    {
      return to_string(fn, text);
    }

    static std::string to_string(const std::string& fn, const std::string& text)
    {
      return "ProcFS: " + fn + " -> " + string::trim_crlf_copy(text);
    }

    static void write_sys(const std::string& fn, const std::string& text, Stop* async_stop=nullptr)
    {
      //OPENVPN_LOG(to_string(fn, text));

      const unsigned int n_retries = 200;
      const unsigned int milliseconds_per_retry = 100;
      volatile bool stop = false;

      // allow asynchronous stop
      Stop::Scope stop_scope(async_stop, [&stop]() {
	  stop = true;
	});

      for (unsigned int i = 0; i < n_retries && !stop; ++i)
	{
	  if (file_exists(fn))
	    {
	      write_string(fn, text);
	      return;
	    }
	  sleep_milliseconds(milliseconds_per_retry);
	}
      if (stop)
	OPENVPN_THROW(procfs_error, "file " << fn << " : aborting write attempt due to stop signal");
      else
	OPENVPN_THROW(procfs_error, "file " << fn << " failed to exist within " << (n_retries * milliseconds_per_retry / 1000) << " seconds");
    }

  private:
    std::string fn;
    std::string text;
  };

  class IPv4ReversePathFilter : public ProcFS
  {
  public:
    IPv4ReversePathFilter(const std::string& dev, const unsigned int value)
      : ProcFS(key_fn(dev), openvpn::to_string(value))
    {
      OPENVPN_LOG("IPv4ReversePathFilter " << dev << " -> " << value);
    }

    static void write(const std::string& dev, const unsigned int value, Stop* stop=nullptr)
    {
      ProcFS::write_sys(key_fn(dev), openvpn::to_string(value), stop);
    }

  private:
    static std::string key_fn(const std::string& dev)
    {
      return printfmt("/proc/sys/net/ipv4/conf/%s/rp_filter", dev);
    }
  };
}

#endif
