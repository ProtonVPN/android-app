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

#pragma once

#include <type_traits>

namespace openvpn {

/**
    @brief CRTP type designed to allow creation of strong types based on intrinsics
    @tparam BaseT The final type name
    @tparam T The value type
    @details The IntrinsicType template struct is designed to encapsulate a value of type T
    and provide a set of operations for manipulating this value. The struct uses the
    Curiously Recurring Template Pattern (CRTP) to enable static polymorphism, where BaseT
    is the derived type that inherits from IntrinsicType.

    This enables an arithmetic type to be essentially used as a base type for a new strong
    type, which can be used to enforce type safety and prevent accidental misuse of the
    underlying value. The struct provides a set of operators for bitwise operations, and
    could be extended to provide additional operators as needed.
*/
template <typename BaseT, typename T>
    requires std::is_arithmetic_v<T>
struct IntrinsicType
{
    using value_type = T;

    /**
     * @brief Constructs an IntrinsicType object
     * @param v The value to initialize the object with
     * @note Default constructs the value_type if no parameter is provided
     */
    constexpr explicit IntrinsicType(value_type v = value_type()) noexcept
        : mValue(v) {};

    /**
     * @brief Assignment operator from value_type
     * @param v The value to assign
     * @return Reference to the modified object
     */
    BaseT &operator=(value_type v)
    {
        mValue = v;
        return CrtpBase();
    }

    /**
     * @brief Assignment operator from BaseT
     * @param arg The object to assign from
     * @return Reference to the modified object
     */
    BaseT &operator=(BaseT arg) noexcept
    {
        mValue = arg.mValue;
        return CrtpBase();
    }

    /**
        @brief Add assignment operator
        @param arg The object to add
        @return Reference to the modified object
    */
    constexpr BaseT operator+=(BaseT arg) noexcept
    {
        mValue += arg.mValue;
        return CrtpBase();
    }

    /**
        @brief Subtract assignment operator
        @param arg The object to subtract
        @return Reference to the modified object
    */
    constexpr BaseT operator-=(BaseT arg) noexcept
    {
        mValue -= arg.mValue;
        return CrtpBase();
    }

    /**
        @brief Multiply assignment operator
        @param arg The object to multiply
        @return Reference to the modified object
    */
    constexpr BaseT operator*=(BaseT arg) noexcept
    {
        mValue *= arg.mValue;
        return CrtpBase();
    }

    /**
        @brief Divide assignment operator
        @param arg The object to divide
        @return Reference to the modified object
    */
    constexpr BaseT operator/=(BaseT arg) noexcept
    {
        mValue /= arg.mValue;
        return CrtpBase();
    }

    /**
     * @brief Bitwise OR assignment operator
     * @param arg The object to OR with
     * @return Reference to the modified object
     * @note This operator is marked constexpr and noexcept, and is enabled only for
     *       integral types
     */
    constexpr BaseT &operator|=(BaseT arg) noexcept
        requires std::is_integral_v<T>
    {
        mValue |= arg.mValue;
        return CrtpBase();
    }

    /**
     * @brief Bitwise AND assignment operator
     * @param arg The object to AND with
     * @return Reference to the modified object
     * @note This operator is marked constexpr and noexcept, and is enabled only for
     *       integral types
     */
    constexpr BaseT &operator&=(BaseT arg) noexcept
        requires std::is_integral_v<T>
    {
        mValue &= arg.mValue;
        return CrtpBase();
    }

    /**
     * @brief Getter for the underlying value
     * @return The stored value
     */
    constexpr value_type get() const noexcept
    {
        return mValue;
    }

    /**
     * @brief Conversion operator to value_type
     * @return The stored value
     */
    constexpr operator value_type() const noexcept
    {
        return mValue;
    }

  private:
    /**
     * @brief Helper function to perform CRTP cast
     * @return Reference to the derived type
     */
    BaseT &CrtpBase() noexcept
    {
        return static_cast<BaseT &>(*this);
    }

    /**
     * @brief Const version of helper function to perform CRTP cast
     * @return Const reference to the derived type
     */
    const BaseT &CrtpBase() const noexcept
    {
        return static_cast<const BaseT &>(*this);
    }

  private:
    value_type mValue;
};

/**
 * @brief Equality comparison operator
 * @param lhs The left operand of the operation
 * @param rhs The right operand of the operation
 * @return true if the objects are equal, false otherwise
 */
template <typename BaseT, typename T>
    requires std::is_arithmetic_v<T>
constexpr bool operator==(IntrinsicType<BaseT, T> lhs, IntrinsicType<BaseT, T> rhs) noexcept
{
    return lhs.get() == rhs.get();
}

/**
 * @brief Equality comparison operator
 * @param lhs The left operand of the operation
 * @param rhs The right operand of the operation
 * @return returns the result of the comparison
 */
template <typename BaseT, typename T>
    requires std::is_arithmetic_v<T>
constexpr auto operator<=>(IntrinsicType<BaseT, T> lhs, IntrinsicType<BaseT, T> rhs) noexcept
{
    return lhs.get() <=> rhs.get();
}

/**
 * @brief Addition operator
 * @param lhs The left operand of the operation
 * @param rhs The right operand of the operation
 * @return Sum of the two objects
 */
template <typename BaseT, typename T>
    requires std::is_arithmetic_v<T>
constexpr BaseT operator+(IntrinsicType<BaseT, T> lhs, IntrinsicType<BaseT, T> rhs) noexcept
{
    return BaseT(lhs.get() + rhs.get());
}

/**
 * @brief Subtraction operator
 * @param lhs The left operand of the operation
 * @param rhs The right operand of the operation
 * @return Difference of the two objects
 */
template <typename BaseT, typename T>
    requires std::is_arithmetic_v<T>
constexpr BaseT operator-(IntrinsicType<BaseT, T> lhs, IntrinsicType<BaseT, T> rhs) noexcept
{
    return BaseT(lhs.get() - rhs.get());
}

/**
 * @brief Multiplication operator
 * @param lhs The left operand of the operation
 * @param rhs The right operand of the operation
 * @return Product of the two objects
 */
template <typename BaseT, typename T>
    requires std::is_arithmetic_v<T>
constexpr BaseT operator*(IntrinsicType<BaseT, T> lhs, IntrinsicType<BaseT, T> rhs) noexcept
{
    return BaseT(lhs.get() * rhs.get());
}

/**
 * @brief Division operator
 * @param lhs The left operand of the operation
 * @param rhs The right operand of the operation
 * @return Quotient of the two objects
 */
template <typename BaseT, typename T>
    requires std::is_arithmetic_v<T>
constexpr BaseT operator/(IntrinsicType<BaseT, T> lhs, IntrinsicType<BaseT, T> rhs) noexcept
{
    return BaseT(lhs.get() / rhs.get());
}

/**
 * @brief Performs bitwise NOT operation on an IntrinsicType
 * @tparam BaseT The base type of the IntrinsicType
 * @tparam T The underlying value type of the IntrinsicType
 * @param t The IntrinsicType object to perform NOT operation on
 * @return A new BaseT object containing the result of the bitwise NOT operation
 * @note This operator is marked constexpr and noexcept, and is enabled only for
 *       integral types
 */
template <typename BaseT, typename T>
    requires std::is_integral_v<T>
constexpr BaseT operator~(IntrinsicType<BaseT, T> t) noexcept
{
    return BaseT{~T(t)};
}

/**
 * @brief Performs bitwise OR operation between two IntrinsicType objects
 * @tparam BaseT The base type of the IntrinsicType
 * @tparam T The underlying value type of the IntrinsicType
 * @param l The left operand of the OR operation
 * @param r The right operand of the OR operation
 * @return A new BaseT object containing the result of the bitwise OR operation
 * @note This operator is marked constexpr and noexcept, and is enabled only for
 *       integral types
 */
template <typename BaseT, typename T>
    requires std::is_integral_v<T>
constexpr BaseT operator|(IntrinsicType<BaseT, T> l, IntrinsicType<BaseT, T> r) noexcept
{
    return BaseT{T(l) | T(r)};
}

/**
 * @brief Performs bitwise AND operation between two IntrinsicType objects
 * @tparam BaseT The base type of the IntrinsicType
 * @tparam T The underlying value type of the IntrinsicType
 * @param l The left operand of the AND operation
 * @param r The right operand of the AND operation
 * @return A new BaseT object containing the result of the bitwise AND operation
 * @note This operator is marked constexpr and noexcept, and is enabled only for
 *       integral types
 */
template <typename BaseT, typename T>
    requires std::is_integral_v<T>
constexpr BaseT operator&(IntrinsicType<BaseT, T> l, IntrinsicType<BaseT, T> r) noexcept
{
    return BaseT{T(l) & T(r)};
}

} // namespace openvpn