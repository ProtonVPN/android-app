//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2024- OpenVPN Inc.
//
//    SPDX-License-Identifier: MPL-2.0 OR AGPL-3.0-only WITH openvpn3-openssl-exception
//


#pragma once

#include <openvpn/common/rc.hpp>

namespace openvpn {

/**
 * @brief A class template that enables reference counting for a given type.
 * @details This class inherits from both the TypeT and RcT (Reference Counting)
 *          classes. It provides a convenient way to create reference-counted
 *          objects of TypeT.
 * @tparam TypeT The base type to be reference-counted.
 * @tparam RcT The reference counting class, defaulting to RC<thread_unsafe_refcount>.
 */
template <typename TypeT, typename RcT = RC<thread_unsafe_refcount>>
class RcEnable : public TypeT, public RcT
{
  public:
    using Ptr = RCPtr<RcEnable>; ///< Alias for the pointer type used by the reference counting class.

    /**
     * @brief Creates a new instance of RcEnable with the given arguments.
     * @details This function creates a new RcEnable object using the provided arguments
     *          and returns a smart pointer (Ptr) to the created object.
     * @tparam ArgsT The parameter pack types for the arguments to be forwarded
     *               to the RcEnable constructor.
     * @param args The arguments to be forwarded to the RcEnable constructor.
     * @return A smart pointer (Ptr) to the newly created RcEnable object with intrusive ref count.
     */
    template <typename... ArgsT>
    [[nodiscard]] static Ptr Create(ArgsT &&...args)
    {
        return Ptr(new RcEnable(std::forward<ArgsT>(args)...));
    }

  private:
    /**
     * @brief Private constructor for RcEnable.
     * @details This constructor is used to create a new instance of RcEnable with the
     *          provided arguments. It initializes the base classes TypeT and RcT with
     *          the forwarded arguments.
     * @tparam ArgsT The parameter pack types for the arguments to be forwarded
     *               to the base class constructors.
     * @param args The arguments to be forwarded to the base class constructors.
     * @note This constructor is private and should not be called directly.
     *       Use the Create() function to create instances of RcEnable.
     */
    template <typename... ArgsT>
    RcEnable(ArgsT &&...args)
        : TypeT(std::forward<ArgsT>(args)...),
          RcT(){};
};

/**
 * @brief Helper function to create a reference-counted object with the default thread-unsafe reference counting policy.
 * @tparam TypeT The type of the object to be created.
 * @tparam RcT The RC type that shall be used, defaults to RC<thread_unsafe_refcount>
 * @tparam ArgsT The types of the arguments to be forwarded to the constructor of TypeT.
 * @param args The arguments to be forwarded to the constructor of TypeT.
 * @return A reference-counted object of type TypeT, using the default thread-unsafe reference counting policy.
 * @note This function is a convenience wrapper around make_rc_impl, using the default RC<thread_unsafe_refcount>
 *       as the reference counting policy.
 */
template <typename TypeT, typename RcT = RC<thread_unsafe_refcount>, typename... ArgsT>
auto make_rc(ArgsT &&...args)
{
    return RcEnable<TypeT, RcT>::Create(std::forward<ArgsT>(args)...);
}

} // namespace openvpn
