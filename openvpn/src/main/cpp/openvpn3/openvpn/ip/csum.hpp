//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2018 OpenVPN Inc.
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

// IP checksum based on Linux kernel implementation

#pragma once

#include <cstdint>

#include <openvpn/common/endian.hpp>
#include <openvpn/common/socktypes.hpp>
#include <openvpn/common/size.hpp>

namespace openvpn {
  namespace IPChecksum {

    inline std::uint16_t fold(std::uint32_t sum)
    {
      sum = (sum >> 16) + (sum & 0xffff);
      sum += (sum >> 16);
      return sum;
    }

    inline std::uint16_t cfold(const std::uint32_t sum)
    {
      return ~fold(sum);
    }

    inline std::uint32_t unfold(const std::uint16_t sum)
    {
      return sum;
    }

    inline std::uint32_t cunfold(const std::uint16_t sum)
    {
      return ~unfold(sum);
    }

    inline std::uint32_t compute(const std::uint8_t *buf, size_t len)
    {
      std::uint32_t result = 0;

      if (!len)
	return 0;

      const bool odd = size_t(buf) & 1;
      if (odd)
	{
#ifdef OPENVPN_LITTLE_ENDIAN
	  result += (*buf << 8);
#else
	  result = *buf;
#endif
	  len--;
	  buf++;
	}

      if (len >= 2)
	{
	  if (size_t(buf) & 2)
	    {
	      result += *(std::uint16_t *)buf;
	      len -= 2;
	      buf += 2;
	    }
	  if (len >= 4)
	    {
	      const uint8_t *end = buf + (len & ~3);
	      std::uint32_t carry = 0;
	      do {
		std::uint32_t w = *(std::uint32_t *)buf;
		buf += 4;
		result += carry;
		result += w;
		carry = (w > result);
	      } while (buf < end);
	      result += carry;
	      result = (result & 0xffff) + (result >> 16);
	    }
	  if (len & 2)
	    {
	      result += *(std::uint16_t *)buf;
	      buf += 2;
	    }
	}
      if (len & 1)
	{
#ifdef OPENVPN_LITTLE_ENDIAN
	  result += *buf;
#else
	  result += (*buf << 8);
#endif
	}
      result = fold(result);
      if (odd)
	result = ((result >> 8) & 0xff) | ((result & 0xff) << 8);
      return result;
    }

    inline std::uint32_t compute(const void *buf, const size_t len)
    {
      return compute((const std::uint8_t *)buf, len);
    }

    inline std::uint32_t partial(const void *buf, const size_t len, const std::uint32_t sum)
    {
      std::uint32_t result = compute(buf, len);

      /* add in old sum, and carry.. */
      result += sum;
      if (sum > result)
	result += 1;
      return result;
    }

    inline std::uint32_t diff16(const std::uint32_t *old,
				const std::uint32_t *new_,
				const std::uint32_t oldsum)
    {
      std::uint32_t diff[8] = { ~old[0], ~old[1], ~old[2], ~old[3],
				new_[0],  new_[1],  new_[2],  new_[3] };
      return partial(diff, sizeof(diff), oldsum);
    }

    inline std::uint32_t diff16(const std::uint8_t *old,
				const std::uint8_t *new_,
				const std::uint32_t oldsum)
    {
      return diff16((const std::uint32_t *)old, (const std::uint32_t *)new_, oldsum);
    }

    inline std::uint32_t diff4(const std::uint32_t old,
			       const std::uint32_t new_,
			       const std::uint32_t oldsum)
    {
      std::uint32_t diff[2] = { ~old, new_ };
      return partial(diff, sizeof(diff), oldsum);
    }

    inline std::uint32_t diff2(const std::uint16_t old,
			       const std::uint16_t new_,
			       const std::uint32_t oldsum)
    {
      std::uint16_t diff[2] = { std::uint16_t(~old), new_ };
      return partial(diff, sizeof(diff), oldsum);
    }

    inline std::uint16_t checksum(const void *data, const size_t size)
    {
      return cfold(compute(data, size));
    }
  }
}