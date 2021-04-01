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

// Manage a pool of threads for a multi-threaded server.
//
// To stress test this code, in client after serv->start() add:
//   if (unit == 3 || unit == 5)
//     throw Exception("HIT IT");
// And after "case PThreadBarrier::ERROR:"
//   if (unit & 1)
//     break;

#ifndef OPENVPN_COMMON_RUNCONTEXT_H
#define OPENVPN_COMMON_RUNCONTEXT_H

#include <string>
#include <vector>
#include <thread>
#include <mutex>
#include <memory>
#include <type_traits> // for std::is_nothrow_move_constructible
#include <utility>

#include <openvpn/common/platform.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/size.hpp>
#include <openvpn/common/signal.hpp>
#include <openvpn/common/stop.hpp>
#include <openvpn/common/environ.hpp>
#include <openvpn/common/number.hpp>
#include <openvpn/common/signal_name.hpp>
#include <openvpn/asio/asiosignal.hpp>
#include <openvpn/time/time.hpp>
#include <openvpn/time/asiotimer.hpp>
#include <openvpn/time/timestr.hpp>
#include <openvpn/common/logsetup.hpp>

#ifdef ASIO_HAS_LOCAL_SOCKETS
#include <openvpn/common/scoped_fd.hpp>
#endif

namespace openvpn {

  struct RunContextLogEntry
  {
    RunContextLogEntry(const time_t timestamp_arg, const std::string& text_arg)
      : timestamp(timestamp_arg),
	text(text_arg)
    {
    }

    time_t timestamp;
    std::string text;
  };

  template <typename RC_TYPE>
  struct ServerThreadType : public virtual RC_TYPE
  {
    typedef RCPtr<ServerThreadType> Ptr;
    typedef RCWeakPtr<ServerThreadType> WPtr;

    virtual void thread_safe_stop() = 0;

    virtual void log_notify(const RunContextLogEntry& le)
    {
    }
  };

  typedef ServerThreadType<RCWeak<thread_safe_refcount>> ServerThreadWeakBase;
  typedef ServerThreadType<RC<thread_safe_refcount>> ServerThreadBase;

  struct RunContextBase : public LogBase
  {
    virtual void cancel() = 0;
    virtual std::vector<RunContextLogEntry> add_log_observer(const unsigned int unit) = 0;
    virtual void disable_log_history() = 0;
    virtual Stop* async_stop() = 0;
  };

  template <typename ServerThread, typename Stats>
  class RunContext : public RunContextBase
  {
  public:
    typedef RCPtr<RunContext> Ptr;

    class ThreadContext
    {
    public:
      ThreadContext(RunContext& ctx_arg)
	: ctx(ctx_arg)
      {
	ctx.add_thread();
      }

      ~ThreadContext()
      {
	ctx.remove_thread();
      }

    private:
      RunContext& ctx;
    };

    RunContext()
      : exit_timer(io_context),
	log_context(this),
	log_wrap()
    {
      signals.reset(new ASIOSignals(io_context));
      signal_rearm();
      schedule_debug_exit();
    }

    void set_async_stop(Stop* async_stop)
    {
      async_stop_ = async_stop;
    }

    void set_log_reopen(LogSetup::Ptr lr)
    {
      log_reopen = std::move(lr);
    }

    void set_thread(const unsigned int unit, std::thread* thread)
    {
      while (threadlist.size() <= unit)
	threadlist.push_back(nullptr);
      if (threadlist[unit])
	throw Exception("RunContext::set_thread: overwrite");
      threadlist[unit] = thread;
    }

    // called from worker thread
    void set_server(const unsigned int unit, ServerThread* serv)
    {
      std::lock_guard<std::recursive_mutex> lock(mutex);
      if (halt)
	throw Exception("RunContext::set_server: halting");
      while (servlist.size() <= unit)
	servlist.push_back(nullptr);
      if (servlist[unit])
	throw Exception("RunContext::set_server: overwrite");
      servlist[unit] = serv;
    }

    // called from worker thread
    void clear_server(const unsigned int unit)
    {
      std::lock_guard<std::recursive_mutex> lock(mutex);
      if (unit < servlist.size())
	servlist[unit] = nullptr;

      // remove log observer entry, if present
      auto lu = std::find(log_observers.begin(), log_observers.end(), unit);
      if (lu != log_observers.end())
	log_observers.erase(lu);
    }

    std::vector<typename ServerThread::Ptr> get_servers()
    {
      std::lock_guard<std::recursive_mutex> lock(mutex);
      std::vector<typename ServerThread::Ptr> ret;
      if (halt)
	return ret;
      ret.reserve(servlist.size());
      for (auto sp : servlist)
	ret.emplace_back(sp);
      return ret;
    }

    void enable_log_history()
    {
      std::lock_guard<std::recursive_mutex> lock(mutex);
      if (!log_history)
	log_history.reset(new std::vector<RunContextLogEntry>());
    }

    virtual void disable_log_history() override
    {
      std::lock_guard<std::recursive_mutex> lock(mutex);
      log_history.reset();
    }

    virtual std::vector<RunContextLogEntry> add_log_observer(const unsigned int unit) override
    {
      std::lock_guard<std::recursive_mutex> lock(mutex);
      auto lu = std::find(log_observers.begin(), log_observers.end(), unit);
      if (lu == log_observers.end())
	log_observers.push_back(unit);
      if (log_history)
	return *log_history;
      else
	return std::vector<RunContextLogEntry>();
    }

#ifdef ASIO_HAS_LOCAL_SOCKETS
    void set_exit_socket(ScopedFD& fd)
    {
      exit_sock.reset(new openvpn_io::posix::stream_descriptor(io_context, fd.release()));
      exit_sock->async_read_some(openvpn_io::null_buffers(),
				 [self=Ptr(this)](const openvpn_io::error_code& error, const size_t bytes_recvd)
				 {
				   if (!error)
				     self->cancel();
				 });
    }
#endif

    void set_prefix(const std::string& pre)
    {
      prefix = pre + ": ";
    }

    void run()
    {
      if (!halt)
	io_context.run();
    }

    void join()
    {
      for (size_t i = 0; i < threadlist.size(); ++i)
	{
	  std::thread* t = threadlist[i];
	  if (t)
	    {
	      t->join();
	      delete t;
	      threadlist[i] = nullptr;
	    }
	}
    }

    virtual void log(const std::string& str) override
    {
      time_t now;
      const std::string ts = date_time_store_time_t(now);
      {
	std::lock_guard<std::recursive_mutex> lock(mutex);
	std::cout << ts << ' ' << str << std::flush;

	if (!log_observers.empty() || log_history)
	  {
	    const RunContextLogEntry le(now, str);
	    for (auto &si : log_observers)
	      {
		ServerThread* st = servlist[si];
		if (st)
		  st->log_notify(le);
	      }
	    if (log_history)
	      log_history->emplace_back(now, str);
	  }
      }
    }

    // called from main or worker thread
    virtual void cancel() override
    {
      if (halt)
	return;
      openvpn_io::post(io_context, [self=Ptr(this)]()
        {
	  std::lock_guard<std::recursive_mutex> lock(self->mutex);
	  if (self->halt)
	    return;
	  self->halt = true;

	  // async stop
	  if (self->async_stop_)
	    self->async_stop_->stop();

	  self->exit_timer.cancel();
#ifdef ASIO_HAS_LOCAL_SOCKETS
	  self->exit_sock.reset();
#endif
	  if (self->signals)
	    self->signals->cancel();

	  // stop threads
	  {
	    unsigned int stopped = 0;
	    for (size_t i = 0; i < self->servlist.size(); ++i)
	      {
		ServerThread* serv = self->servlist[i];
		if (serv)
		  {
		    serv->thread_safe_stop();
		    ++stopped;
		  }
		self->servlist[i] = nullptr;
	      }
	    OPENVPN_LOG(self->prefix << "Stopping " << stopped << '/' << self->servlist.size() << " thread(s)");
	  }
	});
    }

    const Log::Context::Wrapper& log_wrapper() { return log_wrap; }

    void set_stats_obj(const typename Stats::Ptr& stats_arg)
    {
      stats = stats_arg;
    }

    virtual Stop* async_stop() override
    {
      return async_stop_;
    }

  private:
    // called from main or worker thread
    void add_thread()
    {
      std::lock_guard<std::recursive_mutex> lock(mutex);
      ++thread_count;
    }

    // called from main or worker thread
    void remove_thread()
    {
      bool last = false;
      {
	std::lock_guard<std::recursive_mutex> lock(mutex);
	last = (--thread_count <= 0);
      }
      if (last)
	cancel();
    }

  protected:
    virtual void signal(const openvpn_io::error_code& error, int signum)
    {
      if (!error && !halt)
	{
	  OPENVPN_LOG("ASIO SIGNAL: " << signal_name(signum));
	  switch (signum)
	    {
	    case SIGINT:
	    case SIGTERM:
	      cancel();
	      break;
#if !defined(OPENVPN_PLATFORM_WIN)
	    case SIGUSR2:
	      if (stats)
		OPENVPN_LOG(stats->dump());
	      signal_rearm();
	      break;
	    case SIGHUP:
	      if (log_reopen)
		log_reopen->reopen();
	      signal_rearm();
	      break;
#endif
	    default:
	      signal_rearm();
	      break;
	    }
	}
    }

  private:
    void signal_rearm()
    {
      signals->register_signals_all([self=Ptr(this)](const openvpn_io::error_code& error, int signal_number)
                                    {
                                      self->signal(error, signal_number);
                                    });
    }

    // debugging feature -- exit in n seconds
    void schedule_debug_exit()
    {
      const std::string exit_in = Environ::find_static("EXIT_IN");
      if (exit_in.empty())
	return;
      const unsigned int n_sec = parse_number_throw<unsigned int>(exit_in, "error parsing EXIT_IN");
      exit_timer.expires_after(Time::Duration::seconds(n_sec));
      exit_timer.async_wait([self=Ptr(this)](const openvpn_io::error_code& error)
                            {
			      if (error || self->halt)
				return;
			      OPENVPN_LOG("DEBUG EXIT");
			      self->cancel();
                            });
    }

    // these vars only used by main thread
    openvpn_io::io_context io_context{1};
    typename Stats::Ptr stats;
    ASIOSignals::Ptr signals;
    AsioTimer exit_timer;
    std::string prefix;
    std::vector<std::thread*> threadlist;
#ifdef ASIO_HAS_LOCAL_SOCKETS
    std::unique_ptr<openvpn_io::posix::stream_descriptor> exit_sock;
#endif

    // main lock
    std::recursive_mutex mutex;

    // servlist and related vars protected by mutex
    std::vector<ServerThread*> servlist;
    int thread_count = 0;

    // stop
    Stop* async_stop_ = nullptr;

    // log observers
    std::vector<unsigned int> log_observers; // unit numbers of log observers
    std::unique_ptr<std::vector<RunContextLogEntry>> log_history;

    // logging
    Log::Context log_context;
    Log::Context::Wrapper log_wrap; // must be constructed after log_context
    LogSetup::Ptr log_reopen;

  protected:
    volatile bool halt = false;
  };

}

#endif
