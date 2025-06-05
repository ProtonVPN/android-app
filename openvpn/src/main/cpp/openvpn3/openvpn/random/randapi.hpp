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

/**
 * @file   randapi.hpp
 * @brief  Implementation of the base classes for random number generators
 */

#pragma once

#include <cstdint>
#include <limits>
#include <string>
#include <type_traits>

#include <openvpn/common/size.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/random/randistrib.hpp>

namespace openvpn {

/**
 * @class RandomAPI
 * @brief Abstract base class for random number generators
 *
 * This class cannot be inherited from directly, use \ref StrongRandomAPI
 * or \ref WeakRandomAPI to implement random number generators.
 */
class RandomAPI : public RC<thread_unsafe_refcount>
{
  public:
    /**
     * @typedef RCPtr<RandomAPI> Ptr
     * @brief Smart pointer type for managing the ownership of RandomAPI objects
     */
    typedef RCPtr<RandomAPI> Ptr;

    /**
     * @brief   Get the name of the random number generation algorithm
     * @return  The name of the algorithm
     */
    virtual std::string name() const = 0;

    /**
     * @brief   Fill a buffer with random bytes
     * @param   buf  Pointer to the buffer
     * @param   size Number of bytes to generate
     */
    virtual void rand_bytes(unsigned char *buf, size_t size) = 0;

    /**
     * @brief   Fill a buffer with random bytes without throwing exceptions
     * @param   buf  Pointer to the buffer
     * @param   size Number of bytes to generate
     * @return  true on success
     * @return  false on failure
     */
    virtual bool rand_bytes_noexcept(unsigned char *buf, size_t size) = 0;

    /**
     * @brief   Fill a data object with random bytes
     * @tparam  T    Type of the object
     * @param   obj  Reference to the object to be filled
     */
    template <typename T>
    void rand_fill(T &obj)
    {
        rand_bytes(reinterpret_cast<unsigned char *>(&obj), sizeof(T));
    }

    /**
     * @brief   Create a data object filled with random bytes
     * @tparam  T    Type of the object
     * @return  The generated object
     */
    template <typename T>
    T rand_get()
    {
        T ret;
        rand_fill(ret);
        return ret;
    }

    /**
     * @brief   Create a data object filled with random bytes, always >= 0 for signed types
     * @tparam  T    Type of the object
     * @return  The generated data object
     */
    template <typename T>
    T rand_get_positive()
    {
        T ret = rand_get<T>();
        if constexpr (std::is_signed_v<T>)
        {
            // maps (T:min, -1) to (0, T:max) which is fine for random generation
            ret &= std::numeric_limits<T>::max();
        }
        return ret;
    }

    /**
     * @brief   Return a uniformly distributed random number in the range [0, end)
     * @tparam  T    Type of the object
     * @param   end  The upper bound (exclusive)
     * @return  The generated random number
     */
    template <typename T>
    T randrange(const T end)
    {
        return rand_get_positive<T>() % end;
    }

    /**
     * @brief   Return a uniformly distributed random number in the range [start, end]
     * @tparam  T      Type of the object
     * @param   start  The lower bound
     * @param   end    The upper bound
     * @return  The generated random number
     */
    template <typename T>
    T randrange(const T start, const T end)
    {
        if (start >= end)
            return start;
        else
            return start + rand_get_positive<T>() % (end - start + 1);
    }

    /**
     * @brief   Return a uniformly distributed random number in the range [0, end)
     * @param   end  The upper bound (exclusive)
     * @return  The generated random number
     *
     * If end==0 or end==1, will always return 0.
     * This version is strictly 32-bit only and optimizes by avoiding
     * integer division.
     */
    std::uint32_t randrange32(const std::uint32_t end)
    {
        std::uint32_t r;
        rand_fill(r);
        return rand32_distribute(r, end);
    }

    /**
     * @brief   Return a uniformly distributed random number in the range [start, end]
     * @param   start  The lower bound
     * @param   end    The upper bound
     * @return  The generated random number
     *
     * This version is strictly 32-bit only and optimizes by avoiding
     * integer division.
     */
    std::uint32_t randrange32(const std::uint32_t start, const std::uint32_t end)
    {
        if (start >= end)
            return start;
        else
            return start + randrange32(end - start + 1);
    }

    /**
     * @brief   Return a random byte
     * @return  The generated random byte
     */
    std::uint8_t randbyte()
    {
        std::uint8_t byte;
        rand_fill(byte);
        return byte;
    }

    /**
     * @brief   Return a random boolean
     * @return  The generated random boolean
     */
    bool randbool()
    {
        return bool(randbyte() & 1);
    }

    /**
     * @name UniformRandomBitGenerator members
     *
     * These members implement the C++11 UniformRandomBitGenerator requirements
     * so that RandomAPI instances can be used with std::shuffle (and others).
     * See https://en.cppreference.com/w/cpp/named_req/UniformRandomBitGenerator
     */
    ///@{
    typedef unsigned int result_type;
    static constexpr result_type min()
    {
        return result_type(0);
    }
    static constexpr result_type max()
    {
        return ~result_type(0);
    }
    result_type operator()()
    {
        return rand_get<result_type>();
    }
    ///@}

  private:
    friend class StrongRandomAPI;
    friend class WeakRandomAPI;
    RandomAPI() = default;
};

/**
 * @class StrongRandomAPI
 * @brief Abstract base class for cryptographically strong random number generators
 *
 * Inherit from this class if your random number generator produces cryptographically
 * strong random numbers.
 */
class StrongRandomAPI : public RandomAPI
{
  public:
    /**
     * @typedef RCPtr<StrongRandomAPI> Ptr
     * @brief Smart pointer type for managing the ownership of StrongRandomAPI objects
     */
    typedef RCPtr<StrongRandomAPI> Ptr;
};

/**
 * @class WeakRandomAPI
 * @brief Abstract base class for pseudo random number generators
 *
 * Inherit from this class if your random number generator produces pseudo random numbers
 * which are deterministic and should not be used for operations requiring true randomness.
 */
class WeakRandomAPI : public RandomAPI
{
  public:
    /**
     * @typedef RCPtr<WeakRandomAPI> Ptr
     * @brief Smart pointer type for managing the ownership of WeakRandomAPI objects
     */
    typedef RCPtr<WeakRandomAPI> Ptr;
};

} // namespace openvpn
