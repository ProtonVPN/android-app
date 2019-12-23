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
//

#ifndef OPENVPN_SSL_DATALIMIT_H
#define OPENVPN_SSL_DATALIMIT_H

#include <openvpn/common/exception.hpp>

namespace openvpn {
  // Helper for handling keys which can have an upper limit
  // on maximum amount of data encrypted/decrypted, such
  // as Blowfish.
  class DataLimit
  {
  public:
    typedef unsigned int size_type;

    enum Mode {
      Encrypt=0,
      Decrypt=1,
    };

    enum State {
      None=0,
      Green=1,
      Red=2,
    };

    struct Parameters
    {
      size_type encrypt_red_limit = 0;
      size_type decrypt_red_limit = 0;
    };

    DataLimit(const Parameters& p)
      : encrypt(p.encrypt_red_limit),
	decrypt(p.decrypt_red_limit)
    {
    }

    State update_state(const Mode mode, const State newstate)
    {
      return elgible(mode, component(mode).update_state(newstate));
    }

    State add(const Mode mode, const size_type n)
    {
      return elgible(mode, component(mode).add(n));
    }

    bool is_decrypt_green()
    {
      return decrypt.get_state() >= Green;
    }

    static const char *mode_str(const Mode m)
    {
      switch (m)
	{
	case Encrypt:
	  return "Encrypt";
	case Decrypt:
	  return "Decrypt";
	default:
	  return "Mode_???";
	}
    }

    static const char *state_str(const State s)
    {
      switch (s)
	{
	case None:
	  return "None";
	case Green:
	  return "Green";
	case Red:
	  return "Red";
	default:
	  return "State_???";
	}
    }

  private:
    // Don't return Encrypt-Red until Decrypt-Green
    // has been received.  This confirms that the peer
    // is now transmitting on the key ID, making it
    // eligible for renegotiation.
    State elgible(const Mode mode, const State state)
    {
      // Bit positions for Encrypt/Decrypt and Green/Red
      enum {
	EG = 1<<0,
	ER = 1<<1,
	DG = 1<<2,
	DR = 1<<3,
      };
      if (state > None)
	{
	  const unsigned int mask = 1 << ((int(state) - 1) + (int(mode) << 1));
	  if (!(flags & mask))
	    {
	      flags |= mask;
	      if ((mask & (ER|DG)) && ((flags & (ER|DG)) == (ER|DG)))
		return Red;
	      else if (mask & ER)
		return None;
	      else
		return state;
	    }
	}
      return None;
    }

    class Component
    {
    public:
      Component(const size_type red_limit_arg)
	: red_limit(red_limit_arg)
      {
      }

      State add(const size_type n)
      {
	bytes += n;
	return update_state(transition(state));
      }

      State update_state(const State newstate)
      {
	State ret = None;
	if (newstate > state)
	  state = ret = newstate;
	return ret;
      }

      State get_state() const
      {
	return state;
      }

    private:
      State transition(State s) const
      {
	switch (s)
	  {
	  case None:
	    if (bytes)
	      return Green;
	    else
	      return None;
	  case Green:
	    if (red_limit && bytes >= red_limit)
	      return Red;
	    else
	      return None;
	  case Red:
	  default:
	    return None;
	  }
      }

      const size_type red_limit;
      size_type bytes = 0;
      State state = None;
    };

    Component& component(const Mode m)
    {
      switch (m)
	{
	case Encrypt:
	  return encrypt;
	case Decrypt:
	  return decrypt;
	default:
	  throw Exception("DataLimit::Component: unknown mode");
	}
    }

    Component encrypt;
    Component decrypt;
    unsigned int flags = 0;
  };
}

#endif
