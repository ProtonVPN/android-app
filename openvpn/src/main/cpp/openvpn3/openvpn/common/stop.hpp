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

#ifndef OPENVPN_COMMON_STOP_H
#define OPENVPN_COMMON_STOP_H

#include <vector>
#include <functional>
#include <utility>
#include <mutex>

namespace openvpn {
  class Stop
  {
  public:
    class Scope
    {
      friend Stop;

    public:
      Scope(Stop* stop_arg, std::function<void()>&& method_arg)
	: stop(stop_arg),
	  method(std::move(method_arg)),
	  index(-1)
      {
	if (stop)
	  {
	    std::lock_guard<std::recursive_mutex> lock(stop->mutex);
	    if (stop->stop_called)
	      {
		// stop already called, call method immediately
		method();
	      }
	    else
	      {
		index = stop->scopes.size();
		stop->scopes.push_back(this);
	      }
	  }
      }

      ~Scope()
      {
	if (stop)
	  {
	    std::lock_guard<std::recursive_mutex> lock(stop->mutex);
	    if (index >= 0 && index < stop->scopes.size() && stop->scopes[index] == this)
	      {
		stop->scopes[index] = nullptr;
		stop->prune();
	      }
	  }
      }

    private:
      Scope(const Scope&) = delete;
      Scope& operator=(const Scope&) = delete;

      Stop *const stop;
      const std::function<void()> method;
      int index;
    };

    Stop()
    {
    }

    void stop()
    {
      std::lock_guard<std::recursive_mutex> lock(mutex);
      stop_called = true;
      while (scopes.size())
	{
	  Scope* scope = scopes.back();
	  scopes.pop_back();
	  if (scope)
	    {
	      scope->index = -1;
	      scope->method();
	    }
	}
    }

  private:
    Stop(const Stop&) = delete;
    Stop& operator=(const Stop&) = delete;

    void prune()
    {
      while (scopes.size() && !scopes.back())
	scopes.pop_back();
    }

    std::recursive_mutex mutex;
    std::vector<Scope*> scopes;
    bool stop_called = false;
  };

}

#endif
