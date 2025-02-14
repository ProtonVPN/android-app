//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012- OpenVPN Inc.
//
//    SPDX-License-Identifier: MPL-2.0 OR AGPL-3.0-only WITH openvpn3-openssl-exception
//

#ifndef OPENVPN_RANDOM_RANDBYTESTORE_H
#define OPENVPN_RANDOM_RANDBYTESTORE_H

#include <openvpn/common/size.hpp>

namespace openvpn {

template <typename RAND_TYPE>
class RandomByteStore
{
  public:
    static constexpr size_t SIZE = sizeof(typename RAND_TYPE::result_type);

    unsigned char get_byte(RAND_TYPE &rng)
    {
        if (n_bytes == 0)
        {
            res.rt = rng();
            n_bytes = SIZE;
        }
        unsigned char ret = res.bytes[0];
        res.rt >>= 8;
        --n_bytes;
        return ret;
    }

    template <typename T>
    void fill(T &obj, RAND_TYPE &rng)
    {
        unsigned char *data = reinterpret_cast<unsigned char *>(&obj);
        for (size_t i = 0; i < sizeof(obj); ++i)
            data[i] = get_byte(rng);
    }

  private:
    union Result {
        unsigned char bytes[SIZE];
        typename RAND_TYPE::result_type rt;
    };

    Result res;
    unsigned int n_bytes = 0;
};

} // namespace openvpn
#endif
