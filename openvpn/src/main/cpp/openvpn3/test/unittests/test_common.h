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

#pragma once

/* Globals get defined multiple times, normally we set this to extern
 * in the part of the program that pulls in a subset of the other
 * In the unit tests that does not work. So all unit tests are told not
 * to include them and all includes are included in core_tests.cpp to pull
 * them in
 */
#define OPENVPN_EXTERN extern

/* Without the asio.hpp include we get winsocket.h related errors
 * See also https://stackoverflow.com/questions/9750344/boostasio-winsock-and-winsock-2-compatibility-issue
 */
#include <openvpn/io/io.hpp>
#include "test_helper.hpp"
#include <gtest/gtest.h>
