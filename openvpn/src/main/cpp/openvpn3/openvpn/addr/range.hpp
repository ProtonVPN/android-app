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

#ifndef OPENVPN_ADDR_RANGE_H
#define OPENVPN_ADDR_RANGE_H

#include <cstddef>
#include <string>
#include <sstream>

#include <openvpn/addr/ip.hpp>

namespace openvpn::IP {

// Denote a range of IP addresses with a start and extent,
// where A represents an address class.
// A should be a network address class such as IP::Addr, IPv4::Addr, or IPv6::Addr.

/**
    @brief designed to represent and manage a range of IP addresses.
    @details designed to represent and manage a range of IP addresses. This class is particularly
    useful for networking applications that need to work with blocks of IP addresses.

    The purpose of this code is to provide a flexible and efficient way to handle ranges of IP
    addresses. It allows users to create, manipulate, and iterate over these ranges. The class
    is templated, which means it can work with different types of IP addresses (IPv4 or IPv6) as
    long as they conform to the expected interface.

    The main inputs for this class are a starting address (of type ADDR) and an extent (the number
    of addresses in the range). These are typically provided when constructing a RangeType object.
    The class also has a default constructor that creates an empty range.

    As for outputs, the class provides various methods to access information about the range. For
    example, you can get the starting address, the extent of the range, check if the range is
    defined (non-empty), and convert the range to a string representation.

    The RangeType class achieves its purpose through a combination of data storage and methods for
    manipulation and access. It stores the start address and the extent of the range as private
    member variables. The class then provides public methods to interact with this data in useful
    ways.

    One of the key features of this class is its iterator functionality. The Iterator inner class
    allows users to easily traverse the range of IP addresses. This is particularly useful for
    operations that need to process each address in the range individually.

    An important piece of logic in this class is the pull_front method. This method allows you to
    remove a specified number of addresses from the front of the range and return them as a new
    RangeType object. This can be useful for dividing a large range into smaller chunks.

    The class also includes methods for converting the range to a string representation, which can
    be helpful for debugging or displaying the range to users.

    @tparam ADDR The address type
*/
template <typename ADDR>
class RangeType
{
  public:
    class Iterator;

    /**
     * @brief Default constructor for RangeType.
     */
    RangeType();

    /**
     * @brief Constructor for RangeType with specified start address and extent.
     * @param start The starting address of the range.
     * @param extent The number of addresses in the range.
     */
    RangeType(const ADDR &start, const std::size_t extent);

    /**
     * @brief Get an iterator pointing to the beginning of the range.
     * @return Iterator to the first address in the range.
     */
    Iterator begin() const;

    /**
     * @brief Get an iterator pointing to the end of the range.
     * @return Iterator to one past the last address in the range.
     */
    Iterator end() const;

    /**
     * @brief Get an iterator for the range.
     * @return Iterator to the first address in the range.
     * @note This method is equivalent to begin().
     */
    Iterator iterator() const;

    /**
     * @brief Check if the range is defined (non-empty).
     * @return true if the range is defined, false otherwise.
     */
    bool defined() const;

    /**
     * @brief Get the starting address of the range.
     * @return Constant reference to the starting address.
     */
    const ADDR &start() const;

    /**
     * @brief Get the extent (size) of the range.
     * @return The number of addresses in the range.
     */
    std::size_t extent() const;

    /**
     * @brief Remove and return a new range from the front of this range.
     * @param extent The number of addresses to remove from the front.
     * @return A new RangeType object containing the removed addresses.
     * @note This operation modifies the original range.
     */
    RangeType pull_front(std::size_t extent);

    /**
     * @brief Convert the range to a string representation.
     * @return A string describing the range.
     */
    std::string to_string() const;

  private:
    ADDR start_;
    std::size_t extent_;
};

/**
    @brief allow easy navigation through a series of IP addresses
    @details This iterator class is created to allow easy navigation through a series of IP
    addresses defined by a RangeType object. It provides methods to check if there are more
    addresses, retrieve the current address, move to the next address, and compare iterators.

    The iterator doesn't take any direct inputs from the user. Instead, it's initialized with
    a RangeType object, which provides the starting address and the number of addresses in the
    range. This initialization happens through the private constructor, which is only
    accessible to the RangeType class due to the friend declaration.

    The main outputs of this iterator are the IP addresses it traverses. Users can access the
    current address using the addr() method or the * operator. The iterator also indicates
    whether there are more addresses to visit through the more() method.

    @tparam ADDR The address type
*/
template <typename ADDR>
class RangeType<ADDR>::Iterator
{
    friend class RangeType;

  public:
    /**
     * @brief Check if there are more elements in the range.
     * @return true if there are more elements, false otherwise.
     */
    bool more() const;

    /**
     * @brief Get the current address in the range.
     * @return A const reference to the current address.
     */
    const ADDR &addr() const;

    /**
     * @brief Move to the next address in the range.
     * @note This function advances the iterator to the next element if available.
     */
    void next();

    /**
     * @brief Prefix increment operator.
     * @return A reference to the iterator after incrementing.
     * @note This function advances the iterator to the next element if available.
     */
    Iterator &operator++();

    /**
     * @brief Dereference operator.
     * @return A const reference to the current address.
     * @note This operator allows accessing the current element without advancing the iterator.
     */
    const ADDR &operator*() const;

    /**
     * @brief Inequality comparison operator.
     * @param rhs The right-hand side iterator to compare with.
     * @return true if the iterators are not equal, false otherwise.
     * @note This operator is typically used in range-based for loops and other iteration contexts.
     */
    bool operator!=(const Iterator &rhs) const;

  private:
    /**
     * @brief Constructor for the Iterator class.
     * @param range The RangeType object to iterate over.
     * @note This constructor is private and can only be called by the RangeType class.
     */
    Iterator(const RangeType &range)
        : addr_(range.start_), remaining_(range.extent_)
    {
    }

    ADDR addr_;
    std::size_t remaining_;
};

using Range = RangeType<IP::Addr>;

/**
    @brief divide a range of IP addresses into smaller, equal-sized partitions
    @details designed to divide a range of IP addresses into smaller, equal-sized partitions. This
    class is useful when you need to split a large range of IP addresses into manageable chunks,
    which can be helpful in various networking scenarios.
    @tparam ADDR The address type
*/
template <typename ADDR>
class RangePartitionType
{
  public:
    /**
     * @brief Constructor for RangePartitionType
     * @param src_range The source range to be partitioned
     * @param n_partitions The number of partitions to create
     * @note This constructor initializes the RangePartitionType with a given range and number of partitions
     */
    RangePartitionType(const RangeType<ADDR> &src_range, const std::size_t n_partitions);

    /**
     * @brief Retrieves the next partition in the range
     * @param[out] r The RangeType object to store the next partition
     * @return true if a next partition exists, false otherwise
     * @note This function is used to iterate through the partitions of the range
     */
    bool next(RangeType<ADDR> &r);

  private:
    RangeType<ADDR> range;
    std::size_t remaining;
};

using RangePartition = RangePartitionType<IP::Addr>;

// ================================================================================================

template <typename ADDR>
inline bool RangeType<ADDR>::Iterator::more() const
{
    return remaining_ > 0;
}

template <typename ADDR>
inline const ADDR &RangeType<ADDR>::Iterator::addr() const
{
    return addr_;
}

template <typename ADDR>
inline void RangeType<ADDR>::Iterator::next()
{
    if (more())
    {
        ++addr_;
        --remaining_;
    }
}

template <typename ADDR>
inline typename RangeType<ADDR>::Iterator &RangeType<ADDR>::Iterator::operator++()
{
    next();
    return *this;
}

template <typename ADDR>
inline const ADDR &RangeType<ADDR>::Iterator::operator*() const
{
    return addr_;
}

template <typename ADDR>
inline bool RangeType<ADDR>::Iterator::operator!=(const Iterator &rhs) const
{
    return remaining_ != rhs.remaining_ || addr_ != rhs.addr_;
}

template <typename ADDR>
inline RangeType<ADDR>::RangeType()
    : extent_(0)
{
}

template <typename ADDR>
inline RangeType<ADDR>::RangeType(const ADDR &start, const std::size_t extent)
    : start_(start), extent_(extent)
{
}

template <typename ADDR>
inline typename RangeType<ADDR>::Iterator RangeType<ADDR>::begin() const
{
    return Iterator(*this);
}

template <typename ADDR>
inline typename RangeType<ADDR>::Iterator RangeType<ADDR>::end() const
{
    RangeType end_range = *this;
    end_range.start_ += static_cast<long>(end_range.extent_);
    end_range.extent_ = 0;
    return Iterator(end_range);
}

template <typename ADDR>
inline typename RangeType<ADDR>::Iterator RangeType<ADDR>::iterator() const
{
    return Iterator(*this);
}

template <typename ADDR>
inline bool RangeType<ADDR>::defined() const
{
    return extent_ > 0;
}

template <typename ADDR>
inline const ADDR &RangeType<ADDR>::start() const
{
    return start_;
}

template <typename ADDR>
inline std::size_t RangeType<ADDR>::extent() const
{
    return extent_;
}

template <typename ADDR>
inline RangeType<ADDR> RangeType<ADDR>::pull_front(std::size_t extent)
{
    if (extent > extent_)
        extent = extent_;
    RangeType ret(start_, extent);
    start_ += extent;
    extent_ -= extent;
    return ret;
}

template <typename ADDR>
inline std::string RangeType<ADDR>::to_string() const
{
    std::ostringstream os;
    os << start_.to_string() << '[' << extent_ << ']';
    return os.str();
}

template <typename ADDR>
inline RangePartitionType<ADDR>::RangePartitionType(const RangeType<ADDR> &src_range, const std::size_t n_partitions)
    : range(src_range),
      remaining(n_partitions)
{
}

template <typename ADDR>
inline bool RangePartitionType<ADDR>::next(RangeType<ADDR> &r)
{
    if (remaining)
    {
        if (remaining > 1)
            r = range.pull_front(range.extent() / remaining);
        else
            r = range;
        --remaining;
        return r.defined();
    }
    else
        return false;
}

} // namespace openvpn::IP

#endif
