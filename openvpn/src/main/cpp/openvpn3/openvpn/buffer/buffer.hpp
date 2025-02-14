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

// These templates define the fundamental data buffer classes used by the
// OpenVPN core.  Normally OpenVPN uses buffers of unsigned chars, but the
// templatization of the classes would allow buffers of other types to
// be defined.
//
// Fundamentally a buffer is an object with 4 fields:
//
// 1. a pointer to underlying data array
// 2. the capacity of the underlying data array
// 3. an offset into the data array
// 4. the size of the referenced data within the array
//
// The BufferType template is the lowest-level buffer class template.  It refers
// to a buffer but without any notion of ownership of the underlying data.
//
// The BufferAllocatedType template is a higher-level template that inherits
// from BufferType but which asserts ownership over the resources of the buffer --
// for example, it will free the underlying buffer in its destructor.
//
// Since most of the time, we want our buffers to be made out of unsigned chars,
// some typedefs at the end of the file define common instantations for the
// BufferType and BufferAllocatedType templates.
//
// Buffer            : a simple buffer of unsigned char without ownership semantics
// ConstBuffer       : like buffer but where the data pointed to by the buffer is read-only
// BufferAllocated   : an allocated Buffer with ownership semantics
// BufferPtr         : a smart, reference-counted pointer to a BufferAllocatedRc

#pragma once

#include <string>
#include <cstdlib> // defines std::abort()
#include <cstring>
#include <algorithm>
#include <type_traits> // for std::is_nothrow_move_constructible_v, std::remove_const, std::enable_if_t, and std::is_const_v

#ifndef OPENVPN_NO_IO
#include <openvpn/io/io.hpp>
#endif

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/buffer/bufclamp.hpp>
#include <openvpn/common/make_rc.hpp>

#ifdef OPENVPN_BUFFER_ABORT
#define OPENVPN_BUFFER_THROW(exc) \
    {                             \
        std::abort();             \
    }
#else
#define OPENVPN_BUFFER_THROW(exc)                    \
    {                                                \
        throw BufferException(BufferException::exc); \
    }
#endif

namespace openvpn {
//  ===============================================================================================
//  special-purpose exception class for Buffer classes
//  ===============================================================================================

/**
    @brief report various types of exceptions or errors that may occur when working with buffers
    @details defines a C++ class called BufferException which is derived from the standard
    std::exception class. The purpose of this class is to provide a way to handle and report
    various types of exceptions or errors that may occur when working with buffers (data
    containers) in the OpenVPN application.

    The BufferException class does not take any direct input. Instead, it is designed to be
    instantiated and thrown (raised) when certain exceptional conditions arise during buffer
    operations. The class defines an enumeration Status that lists various types of buffer-related
    exceptions, such as buffer_full, buffer_underflow, buffer_overflow, buffer_offset, and others.

    When a BufferException is thrown, it can be caught and handled by the calling code. The output
    produced by this class is an error message that describes the specific type of exception that
    occurred. This error message can be obtained by calling the what() member function, which
    returns a C-style string (const char*) containing the error description.

    The class achieves its purpose through the following logic and algorithm:

    The BufferException class has two constructors: one that takes only a Status value, and another
    that takes both a Status value and an additional error message string. The what() member
    function is overridden from the base std::exception class. It returns the error message
    associated with the exception. If an additional error message string was provided during
    construction, the what() function returns a combination of the status string (e.g.,
    "buffer_full") and the additional message. If no additional message was provided, the what()
    function returns only the status string. The status_string() private static member function
    is used to convert the Status enumeration value to a corresponding string representation
    (e.g., "buffer_full" for buffer_full).

    The important logic flow in this code is the conversion of the Status enumeration value to
    a human-readable string representation, which is then used to construct the error message
    returned by the what() function. This error message can be used by the calling code to
    understand the nature of the exception and take appropriate action.

    It's important to note that this code does not handle any data transformations or perform
    any complex algorithms. Its sole purpose is to provide a mechanism for reporting and
    handling buffer-related exceptions in a consistent and descriptive manner.
*/
class BufferException : public std::exception
{
  public:
    enum Status
    {
        buffer_full,
        buffer_headroom,
        buffer_underflow,
        buffer_overflow,
        buffer_offset,
        buffer_index,
        buffer_const_index,
        buffer_push_front_headroom,
        buffer_no_reset_impl,
        buffer_pop_back,
        buffer_set_size,
        buffer_range,
    };

    explicit BufferException(Status status)
        : status_(status)
    {
    }

    BufferException(Status status, const std::string &msg)
        : status_(status),
          msg_(std::string(status_string(status)) + " : " + msg)
    {
    }

    const char *what() const noexcept override
    {
        if (!msg_.empty())
            return msg_.c_str();
        else
            return status_string(status_);
    }

    Status status() const
    {
        return status_;
    }
    virtual ~BufferException() noexcept = default;

  private:
    static const char *status_string(const Status status)
    {
        switch (status)
        {
        case buffer_full:
            return "buffer_full";
        case buffer_headroom:
            return "buffer_headroom";
        case buffer_underflow:
            return "buffer_underflow";
        case buffer_overflow:
            return "buffer_overflow";
        case buffer_offset:
            return "buffer_offset";
        case buffer_index:
            return "buffer_index";
        case buffer_const_index:
            return "buffer_const_index";
        case buffer_push_front_headroom:
            return "buffer_push_front_headroom";
        case buffer_no_reset_impl:
            return "buffer_no_reset_impl";
        case buffer_pop_back:
            return "buffer_pop_back";
        case buffer_set_size:
            return "buffer_set_size";
        case buffer_range:
            return "buffer_range";
        default:
            return "buffer_???";
        }
    }

    Status status_;
    std::string msg_;
};

//  ===============================================================================================
//  ===============================================================================================

template <typename T>
class BufferAllocatedType;

template <typename T>
class BufferType;

//  ===============================================================================================
//  class ConstBufferType
//  ===============================================================================================
/**
   @brief Immutable buffer with double ended access and adjustable free space at both ends.
   @details This template implements a buffer with double ended access and adjustable free space at
            both ends. It's generalized for type \p T but is only really fully functional with
            various types that can represent strings, as it does have string interoperation helpers.
            It is particularly useful for use as a building block in implementing protocols such
            as wire protocols for a network.

   Data layout:
   @verbatim

    data_ ->|--------------|------------------ Buffered Data --------------------|--------------|
            ^-- offset_ ---^                                                     ^              ^
            ^              ^----- size_ -----------------------------------------^              ^
            ^                                                                                   ^
            ^-- capacity_ ----------------------------------------------------------------------^

   @endverbatim
*/

template <typename T>
class ConstBufferType
{
  public:
    typedef T value_type;
    typedef T *type;
    typedef const T *const_type;
    typedef typename std::remove_const_t<T> NCT;

    /**
     * @brief Default constructor for ConstBufferType.
     */
    ConstBufferType();

    /**
     * @brief Constructs a ConstBufferType from a void pointer and size.
     * @param data Pointer to the data.
     * @param size Size of the data in bytes.
     * @param filled Indicates whether the buffer is filled or not.
     * @note The term filled indicates that the memory pointed to contains desired data and
     *       that the size of the buffer should be set equal to its capacity, as opposed to
     *       this being a buffer of available memory with capacity 'size' and size zero. See
     *       layout diagram for more insight.
     */
    ConstBufferType(void *data, const size_t size, const bool filled);

    /**
     * @brief Constructs a ConstBufferType from a const void pointer and size.
     *        This constructor is disabled when T is already const.
     * @param data Pointer to the const data.
     * @param size Size of the data in bytes.
     * @param filled Indicates whether the buffer is filled or not.
     */
    template <typename U = T,
              typename std::enable_if_t<!std::is_const_v<U>, int> = 0>
    ConstBufferType(const void *data, const size_t size, const bool filled);

    /**
     * @brief Constructs a ConstBufferType from a pointer to T and size.
     * @param data Pointer to the data.
     * @param size Size of the data in bytes.
     * @param filled Indicates whether the buffer is filled or not.
     */
    ConstBufferType(T *data, const size_t size, const bool filled);

    /**
     * @brief Constructs a ConstBufferType from a const pointer to T and size.
     *        This constructor is disabled when T is already const.
     * @param data Pointer to the const data.
     * @param size Size of the data in bytes.
     * @param filled Indicates whether the buffer is filled or not.
     */
    template <typename U = T,
              typename std::enable_if_t<!std::is_const_v<U>, int> = 0>
    ConstBufferType(const U *data, const size_t size, const bool filled);

    /**
     * @brief Needed because this class has virtual member functions and is
     *        intended as a base class.
     */
    virtual ~ConstBufferType() = default;

    /**
     * @brief Const indexing operator for ConstBufferType.
     * @param index Index of the element to access.
     * @return Const reference to the element at the specified index.
     */
    const auto &operator[](const size_t index) const;

    /**
     * @brief Non-const indexing operator for ConstBufferType.
     * @param index Index of the element to access.
     * @return Non-const reference to the element at the specified index.
     */
    auto &operator[](const size_t index);

    /**
     * @brief Initializes the headroom (offset) of the buffer.
     * @param headroom The desired headroom value.
     */
    void init_headroom(const size_t headroom);

    /**
     * @brief Resets the offset of the buffer.
     * @param offset The new offset value.
     */
    void reset_offset(const size_t offset);

    /**
     * @brief Resets the size of the buffer to zero.
     */
    void reset_size();

    /**
     * @brief Resets the content of the buffer.
     */
    void reset_content();

    /**
     * @brief Returns a const pointer to the null-terminated string representation of the buffer.
     * @return Const pointer to the null-terminated string.
     */
    const T *c_str() const;

    /**
     * @brief Returns the length of the buffer.
     * @return The length of the buffer in elements.
     */
    size_t length() const;

    /**
     * @brief Returns a const pointer to the start of the buffer.
     * @return Const pointer to the start of the buffer.
     */
    const T *c_data() const;

    /**
     * @brief Returns a const pointer to the end of the buffer.
     * @return Const pointer to the end of the buffer.
     */
    const T *c_data_end() const;

    /**
     * @brief Returns a const pointer to the start of the raw data in the buffer.
     * @return Const pointer to the start of the raw data.
     */
    const T *c_data_raw() const;

    /**
     * @brief Returns the capacity (raw size) of the allocated buffer in T objects.
     * @return The capacity of the buffer in T objects.
     */
    size_t capacity() const;

    /**
     * @brief Returns the current offset (headroom) into the buffer.
     * @return The offset into the buffer.
     */
    size_t offset() const;

    /**
     * @brief Returns true if the buffer is not empty.
     * @return True if the buffer is not empty, false otherwise.
     */
    bool defined() const;

    /**
     * @brief Returns true if the data memory is defined (allocated).
     * @return True if the data memory is defined, false otherwise.
     */
    bool allocated() const;

    /**
     * @brief Returns true if the buffer is empty.
     * @return True if the buffer is empty, false otherwise.
     */
    bool empty() const;

    /**
     * @brief Returns the size of the buffer in T objects.
     * @return The size of the buffer in T objects.
     */
    size_t size() const;

    /**
     * @brief Removes and returns the last element from the buffer.
     * @return The last element of the buffer.
     */
    T pop_back();

    /**
     * @brief Removes and returns the first element from the buffer.
     * @return The first element of the buffer.
     */
    T pop_front();

    /**
     * @brief Returns the first element of the buffer.
     * @return The first element of the buffer.
     */
    T front() const;

    /**
     * @brief Returns the last element of the buffer.
     * @return The last element of the buffer.
     */
    T back() const;

    /**
     * @brief Advances the buffer by the specified delta.
     * @param delta The amount to advance the buffer.
     */
    void advance(const size_t delta);

    /**
     * @brief Returns true if the buffer contains a null character.
     * @return True if the buffer contains a null character, false otherwise.
     */
    bool contains_null() const;

    /**
     * @brief Returns true if the buffer is zeroed (all elements are zero).
     * @return True if the buffer is zeroed, false otherwise.
     */
    bool is_zeroed() const;

#ifndef OPENVPN_NO_IO
    /**
     * @brief Return an openvpn_io::const_buffer object used by asio write methods.
     * @return A const_buffer object representing the underlying buffer data.
     */
    openvpn_io::const_buffer const_buffer() const;

    /**
     * @brief Return a clamped version of const_buffer().
     * @details This function returns a const_buffer object that represents the underlying
     *          buffer data, but with a size clamped to a certain limit. This can be useful
     *          when you want to ensure that the buffer size does not exceed a specific
     *          value.
     * @return A const_buffer object representing the underlying buffer data, with
     *         the size clamped to a certain limit.
     */
    openvpn_io::const_buffer const_buffer_clamp() const;

    /**
     * @brief Return a const_buffer object with a specified size limit.
     * @details This function returns a const_buffer object that represents the underlying
     *          buffer data, but with a size limited to the specified `limit` value. This
     *          can be useful when you want to ensure that the buffer size does not exceed
     *          a specific value.
     * @param limit The maximum size of the returned const_buffer object.
     * @return A const_buffer object representing the underlying buffer data, with
     *         the size limited to the specified `limit` value.
     */
    openvpn_io::const_buffer const_buffer_limit(const size_t limit) const;
#endif

    /**
     * @brief Read data from the buffer into the specified memory location.
     * @param data Pointer to the memory location where the data will be read.
     * @param size Number of bytes to read from the buffer.
     */
    void read(NCT *data, const size_t size);

    /**
     * @brief Read data from the buffer into the specified memory location.
     * @param data Pointer to the memory location where the data will be read.
     * @param size Number of bytes to read from the buffer.
     */
    void read(void *data, const size_t size);

    /**
     * @brief Allocate memory and read data from the buffer into the allocated memory.
     * @param size Number of bytes to read from the buffer.
     * @return Pointer to the allocated memory containing the read data.
     */
    auto *read_alloc(const size_t size);

    /**
     * @brief Allocate memory and read data from the buffer into the allocated memory.
     * @param size Number of bytes to read from the buffer.
     * @return Buffer containing the read data.
     */
    auto read_alloc_buf(const size_t size);

    /**
     * @brief Return the maximum allowable size value in T objects given the current offset (without considering resize).
     * @return The maximum allowable size value in T objects.
     */
    size_t max_size() const;

    /**
     * @brief After an external method, operating on the array as a mutable unsigned char buffer, has written data to the array, use this method to set the array length in terms of T objects.
     * @param size The new size of the array in terms of T objects.
     */
    void set_size(const size_t size);

    /**
     * @brief Increment the size of the array (usually used in a similar context to set_size such as after mutable_buffer_append).
     * @param delta The amount to increment the size by.
     */
    void inc_size(const size_t delta);

    /**
     * @brief Get a range of the buffer as a ConstBufferType object.
     * @param offset The starting offset of the range.
     * @param len The length of the range.
     * @return A ConstBufferType object representing the specified range of the buffer.
     */
    ConstBufferType range(size_t offset, size_t len) const;

    /**
     * @brief Get a const pointer to the element at the specified index in the array.
     * @param index The index of the element to retrieve.
     * @return A const pointer to the element at the specified index.
     */
    const T *c_index(const size_t index) const;

    /**
     * @brief Equality operator to compare this buffer with another ConstBufferType object.
     * @param other The ConstBufferType object to compare with.
     * @return true if the buffers are equal, false otherwise.
     */
    bool operator==(const ConstBufferType &other) const;

    /**
     * @brief Inequality operator to compare this buffer with another ConstBufferType object.
     * @param other The ConstBufferType object to compare with.
     * @return true if the buffers are not equal, false otherwise.
     */
    bool operator!=(const ConstBufferType &other) const;

  protected: // mutable implementations are only available to derived classes
    /**
     * @brief Reserve additional memory for the buffer.
     * @param n The amount of additional memory to reserve.
     */
    void reserve(const size_t n);

    /**
     * @brief Get a mutable pointer to the start of the array.
     * @return A mutable pointer to the start of the array.
     */
    T *data();

    /**
     * @brief Get a mutable pointer to the end of the array.
     * @return A mutable pointer to the end of the array.
     */
    T *data_end();

    /**
     * @brief Get a mutable pointer to the start of the raw data.
     * @return A mutable pointer to the start of the raw data.
     */
    T *data_raw();

    /**
     * @brief Return the number of additional T objects that can be added before capacity is reached (without considering resize).
     * @param tailroom (Optional) The amount of additional space to reserve at the end of the buffer.
     * @return The number of additional T objects that can be added.
     */
    size_t remaining(const size_t tailroom = 0) const;

    /**
     * @brief Return the maximum allowable size value in T objects, taking into account the specified tailroom.
     * @param tailroom The amount of additional space to reserve at the end of the buffer.
     * @return The maximum allowable size value in T objects, considering the tailroom.
     */
    size_t max_size_tailroom(const size_t tailroom) const;

    /**
     * @brief Append a T object to the end of the array, resizing the array if necessary.
     * @param value The T object to append.
     */
    void push_back(const T &value);

    /**
     * @brief Append a T object to the array, with possible resize.
     * @param value The T object to be appended to the array.
     */
    void push_front(const T &value);

    /**
     * @brief Place a T object after the last object in the array, with possible resize to contain it.
     *        However, it doesn't actually change the size of the array to reflect the added object.
     *        Useful for maintaining null-terminated strings.
     * @param value The T object to be placed after the last object in the array.
     * @throws Will throw an exception if there is no room for the trailing value and the resize fails.
     */
    void set_trailer(const T &value);

    /**
     * @brief Null-terminate the array.
     * @throws Will throw an exception if there is no room, termination is required, and the resize fails.
     */
    void null_terminate();

    /**
     * @brief Get a mutable index into the array.
     * @param index The index of the element to be accessed.
     * @return A pointer to the element at the specified index.
     */
    T *index(const size_t index);

#ifndef OPENVPN_NO_IO
    /**
     * @brief Return an openvpn_io::mutable_buffer object used by asio read methods, starting from data().
     * @param tailroom The amount of tailroom to reserve in the buffer (default: 0).
     * @return An openvpn_io::mutable_buffer object.
     */
    openvpn_io::mutable_buffer mutable_buffer(const size_t tailroom = 0);

    /**
     * @brief Return an openvpn_io::mutable_buffer object used by asio read methods, starting from data_end().
     * @param tailroom The amount of tailroom to reserve in the buffer (default: 0).
     * @return An openvpn_io::mutable_buffer object.
     */
    openvpn_io::mutable_buffer mutable_buffer_append(const size_t tailroom = 0);

    /**
     * @brief Clamped version of mutable_buffer().
     * @param tailroom The amount of tailroom to reserve in the buffer (default: 0).
     * @return An openvpn_io::mutable_buffer object.
     */
    openvpn_io::mutable_buffer mutable_buffer_clamp(const size_t tailroom = 0);

    /**
     * @brief Clamped version of mutable_buffer_append().
     * @param tailroom The amount of tailroom to reserve in the buffer (default: 0).
     * @return An openvpn_io::mutable_buffer object.
     */
    openvpn_io::mutable_buffer mutable_buffer_append_clamp(const size_t tailroom = 0);
#endif

    /**
     * @brief Realign the buffer with the specified headroom.
     * @param headroom The amount of headroom to reserve in the buffer.
     * @note This is useful for aligning structures within the buffer or for adjusting the
     *       headroom in the buffer. It does one by adjusting the other.
     */
    void realign(size_t headroom);

    /**
     * @brief Write data to the buffer.
     * @param data A pointer to the data to be written.
     * @param size The number of T objects to be written.
     */
    void write(const T *data, const size_t size);

    /**
     * @brief Write data to the buffer.
     * @param data A pointer to the data to be written.
     * @param size The number of bytes to be written.
     */
    void write(const void *data, const size_t size);

    /**
     * @brief Prepend data to the buffer.
     * @param data A pointer to the data to be prepended.
     * @param size The number of T objects to be prepended.
     */
    void prepend(const T *data, const size_t size);

    /**
     * @brief Prepend data to the buffer.
     * @param data A pointer to the data to be prepended.
     * @param size The number of bytes to be prepended.
     */
    void prepend(const void *data, const size_t size);

    /**
     * @brief Allocate space for writing data to the buffer.
     * @param size The number of T objects to allocate space for.
     * @return A pointer to the allocated space in the buffer.
     */
    T *write_alloc(const size_t size);

    /**
     * @brief Allocate space for prepending data to the buffer.
     * @param size The number of T objects to allocate space for.
     * @return A pointer to the allocated space in the buffer.
     * @note This function may move the data in the buffer to make room for the prepended
     *       data. If insufficient space is available, this will throw with the strong
     *       exception guarantee.
     */
    T *prepend_alloc(const size_t size);

    /**
     * @brief Reset the buffer with the specified minimum capacity and flags.
     * @param min_capacity The minimum capacity of the buffer.
     * @param flags Flags to control the behavior of the reset operation.
     */
    void reset(const size_t min_capacity, const unsigned int flags);

    /**
     * @brief Reset the buffer with the specified headroom, minimum capacity, and flags.
     * @param headroom The amount of headroom to reserve in the buffer.
     * @param min_capacity The minimum capacity of the buffer.
     * @param flags Flags to control the behavior of the reset operation.
     */
    void reset(const size_t headroom, const size_t min_capacity, const unsigned int flags);

    /**
     * @brief Append data from another buffer to this buffer.
     * @tparam B The type of the other buffer.
     * @param other The other buffer to be appended.
     */
    template <typename B>
    void append(const B &other);

    /**
     * @brief Swap the contents of this buffer with another buffer.
     * @tparam T_ The type of the other buffer.
     * @param other The other buffer to swap with.
     */
    template <typename T_>
    void swap(ConstBufferType<T_> &other);

    /**
     * @brief Throw an exception when the buffer is full.
     * @param newcap The new capacity required for the buffer.
     * @param allocated A flag indicating whether memory was allocated.
     */
    void buffer_full_error(const size_t newcap, const bool allocated) const;

    /**
     * @brief Called when the reset method needs to expand the buffer size.
     * @param min_capacity The minimum capacity required for the buffer.
     * @param flags Flags to control the behavior of the reset operation.
     */
    virtual void reset_impl(const size_t min_capacity, const unsigned int flags);

    /**
     * @brief Derived classes can implement buffer growing semantics by overloading this method.
     *        In the default implementation, buffers are non-growable, so an exception is thrown.
     * @param new_capacity The new capacity required for the buffer.
     * @throws std::exception if the buffer cannot be resized.
     */
    virtual void resize(const size_t new_capacity);

    /**
     * @brief Construct a ConstBufferType object.
     * @param data A pointer to the data buffer.
     * @param offset The offset from data where the T array starts.
     * @param size The number of T objects in the array.
     * @param capacity The maximum number of T objects that can be stored in the buffer.
     */
    ConstBufferType(T *data, const size_t offset, const size_t size, const size_t capacity);

    /**
     * @brief Construct a ConstBufferType object from a const U* pointer.
     *        This constructor is disabled when T is already const.
     * @tparam U The type of the data pointer.
     * @param data A pointer to the data buffer.
     * @param offset The offset from data where the T array starts.
     * @param size The number of T objects in the array.
     * @param capacity The maximum number of T objects that can be stored in the buffer.
     */
    template <typename U = T,
              typename std::enable_if_t<!std::is_const_v<U>, int> = 0>
    ConstBufferType(const U *data, const size_t offset, const size_t size, const size_t capacity);

  private:
    // Even though *data_ is declared as non-const, within ConstBufferType
    // we MUST always treat it as const.  But derived classes may treat it
    // as non-const as long as they passed in non-const data to begin with.
    T *data_;         // pointer to data
    size_t offset_;   // offset from data_ of beginning of T array (to allow for headroom)
    size_t size_;     // number of T objects in array starting at data_ + offset_
    size_t capacity_; // maximum number of array objects of type T for which memory is allocated, starting at data_
};

//  ===============================================================================================
//  class BufferType
//  ===============================================================================================

template <typename T>
class BufferType : public ConstBufferType<T>
{
  private:
    template <typename>
    friend class ConstBufferType;

  public:
    using ConstBufferType<T>::empty;
    using ConstBufferType<T>::capacity;
    using ConstBufferType<T>::offset;
    using ConstBufferType<T>::back;
    using ConstBufferType<T>::init_headroom;
    using ConstBufferType<T>::operator[];
    using ConstBufferType<T>::reserve;
    using ConstBufferType<T>::data;
    using ConstBufferType<T>::data_end;
    using ConstBufferType<T>::data_raw;
    using ConstBufferType<T>::remaining;
    using ConstBufferType<T>::max_size_tailroom;
    using ConstBufferType<T>::push_back;
    using ConstBufferType<T>::push_front;
    using ConstBufferType<T>::set_trailer;
    using ConstBufferType<T>::null_terminate;
    using ConstBufferType<T>::index;
#ifndef OPENVPN_NO_IO
    using ConstBufferType<T>::mutable_buffer;
    using ConstBufferType<T>::mutable_buffer_append;
    using ConstBufferType<T>::mutable_buffer_clamp;
    using ConstBufferType<T>::mutable_buffer_append_clamp;
#endif
    using ConstBufferType<T>::realign;
    using ConstBufferType<T>::write;
    using ConstBufferType<T>::prepend;
    using ConstBufferType<T>::write_alloc;
    using ConstBufferType<T>::prepend_alloc;
    using ConstBufferType<T>::reset;
    using ConstBufferType<T>::append;
    using ConstBufferType<T>::reset_impl;
    using ConstBufferType<T>::resize;
    using ConstBufferType<T>::buffer_full_error;

    /**
     * @brief Default constructor for BufferType.
     */
    BufferType() = default;

    /**
     * @brief Constructor for BufferType that takes a void pointer, size, and a flag indicating if the buffer is filled.
     * @param data A void pointer to the buffer data.
     * @param size The size of the buffer.
     * @param filled A boolean flag indicating if the buffer is filled.
     */
    BufferType(void *data, const size_t size, const bool filled)
        : ConstBufferType<T>(data, size, filled) {};

    /**
     * @brief Constructor for BufferType that takes a T pointer, size, and a flag indicating if the buffer is filled.
     * @param data A pointer to the buffer data of type T.
     * @param size The size of the buffer.
     * @param filled A boolean flag indicating if the buffer is filled.
     */
    BufferType(T *data, const size_t size, const bool filled)
        : ConstBufferType<T>(data, size, filled) {};

    /**
     * @brief Protected constructor for BufferType that takes a T pointer, offset, size, and capacity.
     * @param data A pointer to the buffer data of type T.
     * @param offset The offset of the buffer.
     * @param size The size of the buffer.
     * @param capacity The capacity of the buffer.
     */
  protected:
    BufferType(T *data, const size_t offset, const size_t size, const size_t capacity)
        : ConstBufferType<T>(data, offset, size, capacity) {};
};

//  ===============================================================================================
//  class BufferAllocatedType
//  ===============================================================================================

// Allocation and security for the buffer
struct BufAllocFlags
{
    enum
    {
        CONSTRUCT_ZERO = (1 << 0), ///< if enabled, constructors/init will zero allocated space
        DESTRUCT_ZERO = (1 << 1),  ///< if enabled, destructor will zero data before deletion
        GROW = (1 << 2),           ///< if enabled, buffer will grow (otherwise buffer_full exception will be thrown)
        ARRAY = (1 << 3),          ///< if enabled, use as array
    };
};

template <typename T>
class BufferAllocatedType : public BufferType<T>
{
  private:
    // Friend to all specializations of this template allows access to other.data_
    template <typename>
    friend class BufferAllocatedType;

  public:
    using BufferType<T>::init_headroom;
    using BufferType<T>::buffer_full_error;
    using BufferType<T>::size;
    using BufferType<T>::capacity;
    using BufferType<T>::offset;
    using BufferType<T>::data_raw;
    using BufferType<T>::c_data_raw;
    using BufferType<T>::data;
    using BufferType<T>::c_data;
    using BufferType<T>::swap;

    /**
     * @brief Default constructor.
     */
    BufferAllocatedType();

    /**
     * @brief Constructs a BufferAllocatedType with the specified capacity and flags.
     * @param capacity The initial capacity of the buffer.
     * @param flags The flags to set for the buffer.
     */
    BufferAllocatedType(const size_t capacity, const unsigned int flags);

    /**
     * @brief Constructs a BufferAllocatedType with the specified data, size, and flags.
     * @param data A pointer to the data to be copied into the buffer.
     * @param size The size of the data to be copied.
     * @param flags The flags to set for the buffer.
     */
    BufferAllocatedType(const T *data, const size_t size, const unsigned int flags);

    /**
     * @brief Copy constructor.
     * @param other The BufferAllocatedType object to copy from.
     */
    BufferAllocatedType(const BufferAllocatedType &other);

    /**
     * @brief Constructs a BufferAllocatedType from a BufferType object with the specified flags.
     * @tparam T_ The template parameter type of the BufferType object.
     * @param other The BufferType object to copy from.
     * @param flags The flags to set for the new BufferAllocatedType object.
     */
    template <typename T_>
    BufferAllocatedType(const BufferType<T_> &other, const unsigned int flags);

    /**
     * @brief Assignment operator.
     * @param other The BufferAllocatedType object to copy from.
     */
    void operator=(const BufferAllocatedType &other);

    /**
     * @brief Initializes the buffer with the specified capacity and flags.
     * @param capacity The initial capacity of the buffer.
     * @param flags The flags to set for the buffer.
     */
    void init(const size_t capacity, const unsigned int flags);

    /**
     * @brief Initializes the buffer with the specified data, size, and flags.
     * @param data A pointer to the data to be copied into the buffer.
     * @param size The size of the data to be copied.
     * @param flags The flags to set for the buffer.
     */
    void init(const T *data, const size_t size, const unsigned int flags);

    /**
     * @brief Reallocates the buffer to the specified new capacity.
     * @param newcap The new capacity for the buffer.
     */
    void realloc(const size_t newcap);

    /**
        @brief Realign the buffer with the specified headroom.
        @param headroom The amount of headroom to reserve in the buffer.
        @return BufferAllocatedType& A reference to the realigned buffer.
        @note May reallocate or throw an exception if the reallocation fails.
        @throws if the buffer is full and the reallocation fails.
    */
    BufferAllocatedType &realign(const size_t headroom);

    /**
     * @brief Resets the buffer with the specified minimum capacity and flags.
     * @param min_capacity The minimum capacity for the buffer.
     * @param flags The flags to set for the buffer.
     */
    void reset(const size_t min_capacity, const unsigned int flags);

    /**
     * @brief Resets the buffer with the specified headroom, minimum capacity, and flags.
     * @param headroom The additional capacity to allocate beyond the minimum capacity.
     * @param min_capacity The minimum capacity for the buffer.
     * @param flags The flags to set for the buffer.
     */
    void reset(const size_t headroom, const size_t min_capacity, const unsigned int flags);

    /**
     * @brief Moves the contents of another BufferAllocatedType object into this object.
     * @tparam T_ The template parameter type of the other BufferAllocatedType object.
     * @tparam R_ The template parameter type of the other BufferAllocatedType object.
     * @param other The other BufferAllocatedType object to move from.
     */
    template <typename T_>
    void move(BufferAllocatedType<T_> &other);

    /**
     * @brief Swaps the contents of this BufferAllocatedType object with another BufferAllocatedType object.
     * @tparam T_ The template parameter type of the other BufferAllocatedType object.
     * @tparam R_ The template parameter type of the other BufferAllocatedType object.
     * @param other The other BufferAllocatedType object to swap with.
     */
    template <typename T_>
    void swap(BufferAllocatedType<T_> &other);

    /**
     * @brief Move constructor.
     * @tparam T_ The template parameter type of the other BufferAllocatedType object.
     * @tparam R_ The template parameter type of the other BufferAllocatedType object.
     * @param other The other BufferAllocatedType object to move from.
     */
    template <typename T_>
    BufferAllocatedType(BufferAllocatedType<T_> &&other) noexcept;

    /**
     * @brief Move assignment operator.
     * @param other The BufferAllocatedType object to move from.
     * @return A reference to this BufferAllocatedType object.
     */
    BufferAllocatedType &operator=(BufferAllocatedType &&other) noexcept;

    /**
     * @brief Clears the contents of the buffer.
     */
    void clear();

    /**
     * @brief Sets the specified flags for the buffer.
     * @param flags The flags to set.
     */
    void or_flags(const unsigned int flags);

    /**
     * @brief Clears the specified flags for the buffer.
     * @param flags The flags to clear.
     */
    void and_flags(const unsigned int flags);

    /**
     * @brief Destructor.
     */
    ~BufferAllocatedType();

    /**
     * @brief Private constructor.
     * @param offset The offset of the buffer.
     * @param size The size of the buffer.
     * @param capacity The capacity of the buffer.
     * @param flags The flags for the buffer.
     */
    BufferAllocatedType(const size_t offset,
                        const size_t size,
                        const size_t capacity,
                        const unsigned int flags);

    /**
     * @brief Resets the buffer implementation with the specified minimum capacity and flags.
     * @param min_capacity The minimum capacity for the buffer.
     * @param flags The flags to set for the buffer.
     */
    void reset_impl(const size_t min_capacity, const unsigned int flags) override;

    /**
     * @brief Resizes the buffer to the specified new capacity.
     * @param new_capacity The new capacity for the buffer.
     */
    void resize(const size_t new_capacity) override;

    /**
     * @brief Reallocates the buffer to the specified new capacity.
     * @param newcap The new capacity for the buffer.
     * @param new_offset The new offset for the buffer.
     */
    void realloc_(const size_t newcap, size_t new_offset);

    /**
     * @brief Frees the data associated with the buffer.
     */
    void free_data();

  private:
    unsigned int flags_;
};

//  ===============================================================================================
//  ConstBufferType<T> member function definitions
//  ===============================================================================================

template <typename T>
ConstBufferType<T>::ConstBufferType()
{
    static_assert(std::is_nothrow_move_constructible_v<ConstBufferType>,
                  "class ConstBufferType not noexcept move constructable");
    data_ = nullptr;
    offset_ = size_ = capacity_ = 0;
}

template <typename T>
ConstBufferType<T>::ConstBufferType(void *data, const size_t size, const bool filled)
    : ConstBufferType((T *)data, size, filled){};

template <typename T>
template <typename U, typename std::enable_if_t<!std::is_const_v<U>, int>>
ConstBufferType<T>::ConstBufferType(const void *data, const size_t size, const bool filled)
    : ConstBufferType(const_cast<void *>(data), size, filled){};

template <typename T>
ConstBufferType<T>::ConstBufferType(T *data, const size_t size, const bool filled)
    : data_(data),
      offset_(0),
      size_(filled ? size : 0),
      capacity_(size){};

template <typename T>
template <typename U, typename std::enable_if_t<!std::is_const_v<U>, int>>
ConstBufferType<T>::ConstBufferType(const U *data, const size_t size, const bool filled)
    : ConstBufferType(const_cast<U *>(data), size, filled){};

template <typename T>
const auto &ConstBufferType<T>::operator[](const size_t index) const
{
    if (index >= size_)
        OPENVPN_BUFFER_THROW(buffer_const_index);
    return c_data()[index];
}

template <typename T>
auto &ConstBufferType<T>::operator[](const size_t index)
{
    if (index >= size_)
        OPENVPN_BUFFER_THROW(buffer_const_index);
    if constexpr (std::is_same_v<ConstBufferType<T>, decltype(*this)>)
        return c_data()[index];
    else
        return data()[index];
}

template <typename T>
void ConstBufferType<T>::init_headroom(const size_t headroom)
{
    if (headroom > capacity_)
        OPENVPN_BUFFER_THROW(buffer_headroom);
    offset_ = headroom;
    size_ = 0;
}

template <typename T>
void ConstBufferType<T>::reset_offset(const size_t offset)
{
    const size_t size = size_ + offset_ - offset;
    if (offset > capacity_ || size > capacity_ || offset + size > capacity_)
        OPENVPN_BUFFER_THROW(buffer_offset);
    offset_ = offset;
    size_ = size;
}

template <typename T>
void ConstBufferType<T>::reset_size()
{
    size_ = 0;
}

template <typename T>
void ConstBufferType<T>::reset_content()
{
    offset_ = size_ = 0;
}

template <typename T>
const T *ConstBufferType<T>::c_str() const
{
    return c_data();
}

template <typename T>
size_t ConstBufferType<T>::length() const
{
    return size();
}

template <typename T>
const T *ConstBufferType<T>::c_data() const
{
    return data_ + offset_;
}

template <typename T>
const T *ConstBufferType<T>::c_data_end() const
{
    return data_ + offset_ + size_;
}

template <typename T>
const T *ConstBufferType<T>::c_data_raw() const
{
    return data_;
}

template <typename T>
size_t ConstBufferType<T>::capacity() const
{
    return capacity_;
}

template <typename T>
size_t ConstBufferType<T>::offset() const
{
    return offset_;
}

template <typename T>
bool ConstBufferType<T>::defined() const
{
    return size_ > 0;
}

template <typename T>
bool ConstBufferType<T>::allocated() const
{
    return data_ != nullptr;
}

template <typename T>
bool ConstBufferType<T>::empty() const
{
    return !size_;
}

template <typename T>
size_t ConstBufferType<T>::size() const
{
    return size_;
}

template <typename T>
T ConstBufferType<T>::pop_back()
{
    if (!size_)
        OPENVPN_BUFFER_THROW(buffer_pop_back);
    return *(c_data() + (--size_));
}

template <typename T>
T ConstBufferType<T>::pop_front()
{
    T ret = (*this)[0];
    ++offset_;
    --size_;
    return ret;
}

template <typename T>
T ConstBufferType<T>::front() const
{
    return (*this)[0];
}

template <typename T>
T ConstBufferType<T>::back() const
{
    return (*this)[size_ - 1];
}

template <typename T>
void ConstBufferType<T>::advance(const size_t delta)
{
    if (delta > size_)
        OPENVPN_BUFFER_THROW(buffer_overflow);
    offset_ += delta;
    size_ -= delta;
}

template <typename T>
bool ConstBufferType<T>::contains_null() const
{
    const T *end = c_data_end();
    for (const T *p = c_data(); p < end; ++p)
    {
        if (!*p)
            return true;
    }
    return false;
}

template <typename T>
bool ConstBufferType<T>::is_zeroed() const
{
    const T *end = c_data_end();
    for (const T *p = c_data(); p < end; ++p)
    {
        if (*p)
            return false;
    }
    return true;
}

#ifndef OPENVPN_NO_IO

template <typename T>
openvpn_io::const_buffer ConstBufferType<T>::const_buffer() const
{
    return openvpn_io::const_buffer(c_data(), size());
}

template <typename T>
openvpn_io::const_buffer ConstBufferType<T>::const_buffer_clamp() const
{
    return openvpn_io::const_buffer(c_data(), buf_clamp_write(size()));
}

template <typename T>
openvpn_io::const_buffer ConstBufferType<T>::const_buffer_limit(const size_t limit) const
{
    return openvpn_io::const_buffer(c_data(), std::min(buf_clamp_write(size()), limit));
}
#endif

template <typename T>
void ConstBufferType<T>::read(NCT *data, const size_t size)
{
    std::memcpy(data, read_alloc(size), size * sizeof(T));
}

template <typename T>
void ConstBufferType<T>::read(void *data, const size_t size)
{
    read((NCT *)data, size);
}

template <typename T>
auto *ConstBufferType<T>::read_alloc(const size_t size)
{
    if (size <= size_)
    {
        using retT = std::conditional_t<std::is_same_v<decltype(*this), ConstBufferType<T>>, const value_type, value_type>;
        retT *ret;
        if constexpr (std::is_const_v<retT>)
            ret = c_data();
        else
            ret = data();
        offset_ += size;
        size_ -= size;
        return ret;
    }
    else
        OPENVPN_BUFFER_THROW(buffer_underflow);
}

template <typename T>
auto ConstBufferType<T>::read_alloc_buf(const size_t size)
{
    if (size <= size_)
    {
        using retT = std::conditional_t<std::is_same_v<decltype(*this), ConstBufferType<T>>, ConstBufferType<T>, BufferType<T>>;
        retT ret(data_, offset_, size, capacity_);
        offset_ += size;
        size_ -= size;
        return ret;
    }
    else
        OPENVPN_BUFFER_THROW(buffer_underflow);
}

template <typename T>
size_t ConstBufferType<T>::max_size() const
{
    const size_t r = capacity_ - offset_;
    return r <= capacity_ ? r : 0;
}

template <typename T>
void ConstBufferType<T>::set_size(const size_t size)
{
    if (size > max_size())
        OPENVPN_BUFFER_THROW(buffer_set_size);
    size_ = size;
}

template <typename T>
void ConstBufferType<T>::inc_size(const size_t delta)
{
    set_size(size_ + delta);
}

template <typename T>
ConstBufferType<T> ConstBufferType<T>::range(size_t offset, size_t len) const
{
    if (offset + len > size())
    {
        if (offset < size())
            len = size() - offset;
        else
            len = 0;
    }
    return ConstBufferType(c_data(), offset, len, len);
}

template <typename T>
const T *ConstBufferType<T>::c_index(const size_t index) const
{
    if (index >= size_)
        OPENVPN_BUFFER_THROW(buffer_const_index);
    return &c_data()[index];
}

template <typename T>
template <typename T_>
void ConstBufferType<T>::swap(ConstBufferType<T_> &other)
{
    std::swap(other.data_, data_);
    std::swap(other.offset_, offset_);
    std::swap(other.size_, size_);
    std::swap(other.capacity_, capacity_);
}

template <typename T>
bool ConstBufferType<T>::operator==(const ConstBufferType &other) const
{
    if (size_ != other.size_)
        return false;
    return std::memcmp(c_data(), other.c_data(), size_) == 0;
}

template <typename T>
bool ConstBufferType<T>::operator!=(const ConstBufferType &other) const
{
    return !(*this == other);
}

template <typename T>
void ConstBufferType<T>::reserve(const size_t n)
{
    if (n > capacity_)
        resize(n);
}

template <typename T>
T *ConstBufferType<T>::data()
{
    return data_ + offset_;
}

template <typename T>
T *ConstBufferType<T>::data_end()
{
    return data_ + offset_ + size_;
}

template <typename T>
T *ConstBufferType<T>::data_raw()
{
    return data_;
}

template <typename T>
size_t ConstBufferType<T>::remaining(const size_t tailroom) const
{
    const size_t r = capacity_ - (offset_ + size_ + tailroom);
    return r <= capacity_ ? r : 0;
}

template <typename T>
size_t ConstBufferType<T>::max_size_tailroom(const size_t tailroom) const
{
    const size_t r = capacity_ - (offset_ + tailroom);
    return r <= capacity_ ? r : 0;
}

template <typename T>
void ConstBufferType<T>::push_back(const T &value)
{
    if (!remaining())
        resize(offset_ + size_ + 1);
    *(data() + size_++) = value;
}

template <typename T>
void ConstBufferType<T>::push_front(const T &value)
{
    if (!offset_)
        OPENVPN_BUFFER_THROW(buffer_push_front_headroom);
    --offset_;
    ++size_;
    *data() = value;
}

template <typename T>
void ConstBufferType<T>::set_trailer(const T &value)
{
    if (!remaining())
        resize(offset_ + size_ + 1);
    *(data() + size_) = value;
}

template <typename T>
void ConstBufferType<T>::null_terminate()
{
    if (empty() || back())
        push_back(0);
}

template <typename T>
T *ConstBufferType<T>::index(const size_t index)
{
    if (index >= size_)
        OPENVPN_BUFFER_THROW(buffer_index);
    return &data()[index];
}


#ifndef OPENVPN_NO_IO

template <typename T>
openvpn_io::mutable_buffer ConstBufferType<T>::mutable_buffer(const size_t tailroom)
{
    return openvpn_io::mutable_buffer(data(), max_size_tailroom(tailroom));
}

template <typename T>
openvpn_io::mutable_buffer ConstBufferType<T>::mutable_buffer_append(const size_t tailroom)
{
    return openvpn_io::mutable_buffer(data_end(), remaining(tailroom));
}

template <typename T>
openvpn_io::mutable_buffer ConstBufferType<T>::mutable_buffer_clamp(const size_t tailroom)
{
    return openvpn_io::mutable_buffer(data(), buf_clamp_read(max_size_tailroom(tailroom)));
}

template <typename T>
openvpn_io::mutable_buffer ConstBufferType<T>::mutable_buffer_append_clamp(const size_t tailroom)
{
    return openvpn_io::mutable_buffer(data_end(), buf_clamp_read(remaining(tailroom)));
}
#endif

template <typename T>
void ConstBufferType<T>::realign(size_t headroom)
{
    if (headroom != offset_)
    {
        if (headroom + size_ > capacity_)
            OPENVPN_BUFFER_THROW(buffer_headroom);
        std::memmove(data_ + headroom, data_ + offset_, size_);
        offset_ = headroom;
    }
}

template <typename T>
void ConstBufferType<T>::write(const T *data, const size_t size)
{
    std::memcpy(write_alloc(size), data, size * sizeof(T));
}

template <typename T>
void ConstBufferType<T>::write(const void *data, const size_t size)
{
    write((const T *)data, size);
}

template <typename T>
void ConstBufferType<T>::prepend(const T *data, const size_t size)
{
    std::memcpy(prepend_alloc(size), data, size * sizeof(T));
}

template <typename T>
void ConstBufferType<T>::prepend(const void *data, const size_t size)
{
    prepend((const T *)data, size);
}

template <typename T>
T *ConstBufferType<T>::write_alloc(const size_t size)
{
    if (size > remaining())
        resize(offset_ + size_ + size);
    T *ret = data() + size_;
    size_ += size;
    return ret;
}

template <typename T>
T *ConstBufferType<T>::prepend_alloc(const size_t request_size)
{
    if (request_size > offset())
        realign(request_size);

    offset_ -= request_size;
    size_ += request_size;

    return data();
}

template <typename T>
void ConstBufferType<T>::reset(const size_t min_capacity, const unsigned int flags)
{
    if (min_capacity > capacity_)
        reset_impl(min_capacity, flags);
}

template <typename T>
void ConstBufferType<T>::reset(const size_t headroom, const size_t min_capacity, const unsigned int flags)
{
    reset(min_capacity, flags);
    init_headroom(headroom);
}

template <typename T>
template <typename B>
void ConstBufferType<T>::append(const B &other)
{
    write(other.c_data(), other.size());
}

template <typename T>
void ConstBufferType<T>::reset_impl(const size_t min_capacity, const unsigned int flags)
{
    OPENVPN_BUFFER_THROW(buffer_no_reset_impl);
}

template <typename T>
void ConstBufferType<T>::resize(const size_t new_capacity)
{
    if (new_capacity > capacity_)
        buffer_full_error(new_capacity, false);
}

template <typename T>
void ConstBufferType<T>::buffer_full_error(const size_t newcap, const bool allocated) const
{
#ifdef OPENVPN_BUFFER_ABORT
    std::abort();
#else
    throw BufferException(BufferException::buffer_full, "allocated=" + std::to_string(allocated) + " size=" + std::to_string(size_) + " offset=" + std::to_string(offset_) + " capacity=" + std::to_string(capacity_) + " newcap=" + std::to_string(newcap));
#endif
}

template <typename T>
ConstBufferType<T>::ConstBufferType(T *data, const size_t offset, const size_t size, const size_t capacity)
    : data_(data), offset_(offset), size_(size), capacity_(capacity){};

template <typename T>
template <typename U, typename std::enable_if_t<!std::is_const_v<U>, int>>
ConstBufferType<T>::ConstBufferType(const U *data, const size_t offset, const size_t size, const size_t capacity)
    : ConstBufferType(const_cast<U *>(data), offset, size, capacity){};

//  ===============================================================================================
//  BufferAllocatedType<T> member function definitions
//  ===============================================================================================

template <typename T>
BufferAllocatedType<T>::BufferAllocatedType(const size_t offset, const size_t size, const size_t capacity, const unsigned int flags)
    : BufferType<T>(capacity ? new T[capacity] : nullptr, offset, size, capacity), flags_(flags)
{
    if (flags & BufAllocFlags::CONSTRUCT_ZERO)
        std::memset(data_raw(), 0, capacity * sizeof(T));
}

template <typename T>
BufferAllocatedType<T>::BufferAllocatedType()
    : BufferAllocatedType(0, 0, 0, 0)
{
    static_assert(std::is_nothrow_move_constructible_v<BufferAllocatedType>,
                  "class BufferAllocatedType not noexcept move constructable");
}

template <typename T>
BufferAllocatedType<T>::BufferAllocatedType(const size_t capacity, const unsigned int flags)
    : BufferAllocatedType(0, flags & BufAllocFlags::ARRAY ? capacity : 0, capacity, flags){};

template <typename T>
BufferAllocatedType<T>::BufferAllocatedType(const T *data, const size_t size, const unsigned int flags)
    : BufferAllocatedType(0, size, size, flags)
{
    if (size && data)
        std::memcpy(data_raw(), data, size * sizeof(T));
}

template <typename T>
BufferAllocatedType<T>::BufferAllocatedType(const BufferAllocatedType &other)
    : BufferAllocatedType(other.offset(), other.size(), other.capacity(), other.flags_)
{
    if (size())
        std::memcpy(data(), other.c_data(), size() * sizeof(T));
}

template <typename T>
template <typename T_>
BufferAllocatedType<T>::BufferAllocatedType(const BufferType<T_> &other, const unsigned int flags)
    : BufferAllocatedType(other.offset(), other.size(), other.capacity(), flags)
{
    static_assert(sizeof(T) == sizeof(T_), "size inconsistency");
    if (size())
        std::memcpy(data(), other.c_data(), size() * sizeof(T));
}

template <typename T>
void BufferAllocatedType<T>::operator=(const BufferAllocatedType &other)
{
    if (this != &other)
    {
        auto tempBuffer = BufferAllocatedType(other.offset(), other.size(), other.capacity(), other.flags_);
        if (other.size())
            std::memcpy(tempBuffer.data(), other.c_data(), tempBuffer.size() * sizeof(T));
        swap(tempBuffer);
    }
}

template <typename T>
void BufferAllocatedType<T>::init(const size_t capacity, const unsigned int flags)
{
    auto tempBuffer = BufferAllocatedType(capacity, flags);
    swap(tempBuffer);
}

template <typename T>
void BufferAllocatedType<T>::init(const T *data, const size_t size, const unsigned int flags)
{
    auto tempBuffer = BufferAllocatedType(data, size, flags);
    swap(tempBuffer);
}

template <typename T>
void BufferAllocatedType<T>::realloc(const size_t newcap)
{
    if (newcap > capacity())
        realloc_(newcap, offset());
}

template <typename T>
BufferAllocatedType<T> &BufferAllocatedType<T>::realign(const size_t headroom)
{
    if (headroom != offset())
    {
        if (headroom + size() > capacity())
            realloc_(headroom + size(), headroom);
        else
            BufferType<T>::realign(headroom);
    }
    return *this;
}

template <typename T>
void BufferAllocatedType<T>::reset(const size_t min_capacity, const unsigned int flags)
{
    if (min_capacity > capacity())
        init(min_capacity, flags);
}

template <typename T>
void BufferAllocatedType<T>::reset(const size_t headroom, const size_t min_capacity, const unsigned int flags)
{
    reset(min_capacity, flags);
    init_headroom(headroom);
}

template <typename T>
template <typename T_>
void BufferAllocatedType<T>::move(BufferAllocatedType<T_> &other)
{
    auto temp = BufferAllocatedType();
    swap(other);
    other.swap(temp);
}

template <typename T>
template <typename T_>
void BufferAllocatedType<T>::swap(BufferAllocatedType<T_> &other)
{
    BufferType<T>::swap(other);
    std::swap(flags_, other.flags_);
}

template <typename T>
template <typename T_>
BufferAllocatedType<T>::BufferAllocatedType(BufferAllocatedType<T_> &&other) noexcept
    : BufferAllocatedType()
{
    move(other);
}

template <typename T>
BufferAllocatedType<T> &BufferAllocatedType<T>::operator=(BufferAllocatedType &&other) noexcept
{
    if (this != &other)
    {
        move(other);
    }
    return *this;
}

template <typename T>
void BufferAllocatedType<T>::clear()
{
    auto tempBuffer = BufferAllocatedType(0, 0, 0, 0);
    swap(tempBuffer);
}

template <typename T>
void BufferAllocatedType<T>::or_flags(const unsigned int flags)
{
    flags_ |= flags;
}

template <typename T>
void BufferAllocatedType<T>::and_flags(const unsigned int flags)
{
    flags_ &= flags;
}

template <typename T>
BufferAllocatedType<T>::~BufferAllocatedType()
{
    if (data_raw())
        free_data();
}

template <typename T>
void BufferAllocatedType<T>::reset_impl(const size_t min_capacity, const unsigned int flags)
{
    init(min_capacity, flags);
}

template <typename T>
void BufferAllocatedType<T>::resize(const size_t new_capacity)
{
    const size_t newcap = std::max(new_capacity, capacity() * 2);
    if (newcap > capacity())
    {
        if (flags_ & BufAllocFlags::GROW)
            realloc_(newcap, offset());
        else
            buffer_full_error(newcap, true);
    }
}

template <typename T>
void BufferAllocatedType<T>::realloc_(const size_t newcap, size_t new_offset)
{
    auto tempBuffer = BufferAllocatedType(new_offset, size(), newcap, flags_);
    if (size())
        std::memcpy(tempBuffer.data(), c_data(), size() * sizeof(T));
    swap(tempBuffer);
}

template <typename T>
void BufferAllocatedType<T>::free_data()
{
    if (size() && (flags_ & BufAllocFlags::DESTRUCT_ZERO))
        std::memset(data_raw(), 0, capacity() * sizeof(T));
    delete[] data_raw();
}

//  ===============================================================================================
//  specializations of BufferType for unsigned char
//  ===============================================================================================

using Buffer = BufferType<unsigned char>;
using ConstBuffer = ConstBufferType<unsigned char>;
using BufferAllocated = BufferAllocatedType<unsigned char>;
using BufferAllocatedRc = RcEnable<BufferAllocated, RC<thread_unsafe_refcount>>;
using BufferPtr = RCPtr<BufferAllocatedRc>;

//  ===============================================================================================
//  BufferAllocated + RC  with thread-safe refcount
//  ===============================================================================================

using BufferAllocatedTS = RcEnable<BufferAllocated, RC<thread_safe_refcount>>;
using BufferPtrTS = RCPtr<BufferAllocatedTS>;

//  ===============================================================================================
//  cast BufferType<T> to ConstBufferType<T>
//  ===============================================================================================

template <typename T>
inline ConstBufferType<T> &const_buffer_ref(BufferType<T> &src)
{
    return src;
}

template <typename T>
inline const ConstBufferType<T> &const_buffer_ref(const BufferType<T> &src)
{
    return src;
}

} // namespace openvpn
