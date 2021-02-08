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

#ifndef OPENVPN_TRANSPORT_GREMLIN_H
#define OPENVPN_TRANSPORT_GREMLIN_H

#include <memory>
#include <deque>
#include <vector>
#include <utility>
#include <sstream>

#include <openvpn/common/rc.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/number.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/time/asiotimer.hpp>
#include <openvpn/random/mtrandapi.hpp>

namespace openvpn {
  namespace Gremlin {

    OPENVPN_EXCEPTION(gremlin_error);

    struct DelayedQueue : public RC<thread_unsafe_refcount>
    {
    public:
      typedef RCPtr<DelayedQueue> Ptr;

      DelayedQueue(openvpn_io::io_context& io_context,
		   const unsigned int delay_ms)
	: dur(Time::Duration::milliseconds(delay_ms)),
	  next_event(io_context)
      {
      }

      template <class F>
      void queue(F&& func_arg)
      {
	const bool empty = events.empty();
	events.emplace_back(new Event<F>(Time::now() + dur, std::move(func_arg)));
	if (empty)
	  set_timer();
      }

      size_t size() const
      {
	return events.size();
      }

      void stop()
      {
	next_event.cancel();
      }

    private:
      struct EventBase
      {
	virtual void call() = 0;
	virtual const Time& fire_time() = 0;
	virtual ~EventBase() {}
      };

      template <class F>
      struct Event : public EventBase
      {
      public:
	Event(Time fire_arg, F&& func_arg)
	  : fire(fire_arg),
	    func(std::move(func_arg))
	{
	}

	virtual void call()
	{
	  func();
	}

	virtual const Time& fire_time()
	{
	  return fire;
	}

      private:
	Time fire;
	F func;
      };

      void set_timer()
      {
	if (events.empty())
	  return;
	EventBase& ev = *events.front();
	next_event.expires_at(ev.fire_time());
	next_event.async_wait([self=Ptr(this)](const openvpn_io::error_code& error)
			      {
				if (!error)
				  {
				    EventBase& ev = *self->events.front();
				    ev.call();
				    self->events.pop_front();
				    self->set_timer();
				  }
			      });
      }

      Time::Duration dur;
      AsioTimer next_event;
      std::deque<std::unique_ptr<EventBase>> events;
    };

    class Config : public RC<thread_unsafe_refcount>
    {
    public:
      typedef RCPtr<Config> Ptr;

      Config(const std::string& config_str)
      {
	const std::vector<std::string> parms = string::split(config_str, ',');
	if (parms.size() < 4)
	  throw gremlin_error("need 4 comma-separated values for send_delay_ms, recv_delay_ms, send_drop_prob, recv_drop_prob");
	if (!parse_number(string::trim_copy(parms[0]), send_delay_ms))
	  throw gremlin_error("send_delay_ms");
	if (!parse_number(string::trim_copy(parms[1]), recv_delay_ms))
	  throw gremlin_error("recv_delay_ms");
	if (!parse_number(string::trim_copy(parms[2]), send_drop_probability))
	  throw gremlin_error("send_drop_probability");
	if (!parse_number(string::trim_copy(parms[3]), recv_drop_probability))
	  throw gremlin_error("recv_drop_probability");
      }

      std::string to_string() const
      {
	std::ostringstream os;
	os << '[' << send_delay_ms << ',' << recv_delay_ms << ',' << send_drop_probability << ',' << recv_drop_probability << ']';
	return os.str();
      }

      unsigned int send_delay_ms = 0;
      unsigned int recv_delay_ms = 0;
      unsigned int send_drop_probability = 0;
      unsigned int recv_drop_probability = 0;
    };

    class SendRecvQueue
    {
    public:
      SendRecvQueue(openvpn_io::io_context& io_context,
		    const Config::Ptr& conf_arg,
		    const bool tcp_arg)
	: conf(conf_arg),
	  send(new DelayedQueue(io_context, conf->send_delay_ms)),
	  recv(new DelayedQueue(io_context, conf->recv_delay_ms)),
	  tcp(tcp_arg)
      {
      }

      template <class F>
      void send_queue(F&& func_arg)
      {
	if (tcp || flip(conf->send_drop_probability))
	  send->queue(std::move(func_arg));
      }

      template <class F>
      void recv_queue(F&& func_arg)
      {
	if (tcp || flip(conf->recv_drop_probability))
	  recv->queue(std::move(func_arg));
      }

      size_t send_size() const
      {
	return send->size();
      }

      size_t recv_size() const
      {
	return recv->size();
      }

      void stop()
      {
	send->stop();
	recv->stop();
      }

    private:
      bool flip(const unsigned int prob)
      {
	if (prob)
	  return ri.randrange(prob) != 0;
	else
	  return true;
      }

      Config::Ptr conf;
      MTRand ri;
      DelayedQueue::Ptr send;
      DelayedQueue::Ptr recv;
      bool tcp;
    };
  }
}

#endif
