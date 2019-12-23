//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2017 OpenVPN Inc.
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

#ifndef OPENVPN_LOG_LOGPERIOD_H
#define OPENVPN_LOG_LOGPERIOD_H

#include <sys/time.h> // for time_t

#include <string>
#include <sstream>
#include <iomanip> // for setfill and setw

#include <openvpn/common/olong.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/time/time.hpp>
#include <openvpn/time/timestr.hpp>

namespace openvpn {
  class LogPeriod
  {
  public:
    OPENVPN_EXCEPTION(log_period_error);

    enum Period {
      UNDEF,
      DAILY,
      HOURLY,
      BY_MINUTE,
    };

    LogPeriod()
      : start_(0),
	end_(0),
	period_(UNDEF)
    {
    }

    LogPeriod(const Period period, const time_t base)
    {
      period_ = period;
      const olong p = period_sec(period_);
      start_ = period_base(period_, base);
      end_ = start_ + p;
    }

    LogPeriod(const LogPeriod& other, const int index)
    {
      period_ = other.period_;
      const olong p = period_sec(period_);
      start_ = other.start_ + p * olong(index);
      end_ = start_ + p;
    }

    bool is_current(const time_t now) const
    {
      const olong onow = olong(now);
      return onow >= start_ && onow < end_;
    }

    bool defined() const
    {
      return period_ != UNDEF;
    }

    unsigned int expires_in(const time_t now)
    {
      const olong onow = olong(now);
      if (onow < end_)
	return end_ - onow;
      else
	return 0;
    }

    std::string to_string_verbose() const
    {
      return date_time(start_) + " -> " + date_time(end_);
    }

    std::string to_string() const
    {
      std::ostringstream os;
      struct tm lt;
      const time_t time = time_t(start_);
      if (!localtime_r(&time, &lt))
	throw log_period_error("to_string localtime_r");
      os << std::setfill('0');
      os << std::setw(4) << (lt.tm_year + 1900) << '.' << std::setw(2) << (lt.tm_mon + 1) << '.' << std::setw(2) << lt.tm_mday;
      if (period_ == HOURLY || period_ == BY_MINUTE)
	os << '-' << std::setw(2) << lt.tm_hour << ':' << std::setw(2) << lt.tm_min;
      return os.str();
    }

    static Period period_from_string(const std::string& str)
    {
      if (str == "daily")
	return DAILY;
      else if (str == "hourly")
	return HOURLY;
      else if (str == "by_minute")
	return BY_MINUTE;
      else
	throw log_period_error("unknown period: " + str);
    }

  private:
    static olong period_sec(const Period p)
    {
      switch (p)
	{
	case DAILY:
	  return 86400;
	case HOURLY:
	  return 3600;
	case BY_MINUTE:
	  return 60;
	default:
	  throw log_period_error("undefined period");
	}
    }

    static olong period_base(const Period p, const time_t time)
    {
      struct tm lt;
      if (!localtime_r(&time, &lt))
	throw log_period_error("period_base localtime_r");
      switch (p)
	{
	case DAILY:
	  lt.tm_hour = 0;
	  lt.tm_min = 0;
	  lt.tm_sec = 0;
	  break;
	case HOURLY:
	  lt.tm_min = 0;
	  lt.tm_sec = 0;
	  break;
	case BY_MINUTE:
	  lt.tm_sec = 0;
	  break;
	default:
	  throw log_period_error("unknown period");
	}
      const time_t ret = mktime(&lt);
      if (ret == -1)
	throw log_period_error("mktime");
      return olong(ret);
    }

  private:
    olong start_;
    olong end_;
    Period period_;
  };
}

#endif
