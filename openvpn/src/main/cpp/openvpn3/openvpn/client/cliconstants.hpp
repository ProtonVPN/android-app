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

#ifndef OPENVPN_CLIENT_CLICONSTANTS_H
#define OPENVPN_CLIENT_CLICONSTANTS_H

// Various sanity checks for different limits on OpenVPN clients

namespace openvpn {
  namespace ProfileParseLimits {
    enum {
      MAX_PROFILE_SIZE=262144, // maximum size of an OpenVPN configuration file
      MAX_PUSH_SIZE=262144,    // maximum size of aggregate data that can be pushed to a client
      MAX_LINE_SIZE=512,       // maximum size of an OpenVPN configuration file line
      MAX_DIRECTIVE_SIZE=64,   // maximum number of chars in an OpenVPN directive
      OPT_OVERHEAD=64,         // bytes overhead of one option/directive, for accounting purposes
      TERM_OVERHEAD=16,        // bytes overhead of one argument in an option, for accounting purposes
      MAX_SERVER_LIST_SIZE=4096, // maximum server list size, i.e. "setenv SERVER ..."
    };
  }
}

#endif
