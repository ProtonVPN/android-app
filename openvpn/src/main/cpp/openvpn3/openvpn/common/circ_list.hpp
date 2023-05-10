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
//    If not, see <http://www.gnu.org/licenses/>.

// A general-purpose circular list collection class.
// Used by the OpenVPN anti-replay logic.

#ifndef OPENVPN_COMMON_CIRC_LIST_H
#define OPENVPN_COMMON_CIRC_LIST_H

#include <vector>

#include <openvpn/common/size.hpp>
#include <openvpn/common/exception.hpp>

namespace openvpn {

template <typename T>
class CircList
{
  public:
    OPENVPN_SIMPLE_EXCEPTION(circ_list_reset);
    OPENVPN_SIMPLE_EXCEPTION(circ_list_index);
    OPENVPN_SIMPLE_EXCEPTION(circ_list_const_index);
    OPENVPN_SIMPLE_EXCEPTION(circ_list_push);

    CircList()
    {
        init(0);
    }

    explicit CircList(const size_t capacity)
    {
        init(capacity);
    }

    void init(const size_t capacity)
    {
        if (capacity)
        {
            data_.reserve(capacity);
            capacity_ = capacity;
            reset();
        }
        else
        {
            head_ = capacity_ = 0;
            data_.clear();
        }
    }

    void reset()
    {
        if (capacity_)
        {
            head_ = capacity_ - 1;
            data_.clear();
        }
        else
            throw circ_list_reset();
    }

    size_t size() const
    {
        return data_.size();
    }

    bool defined() const
    {
        return capacity_ > 0;
    }

    void push(const T &item)
    {
        if (++head_ >= capacity_)
            head_ = 0;
        if (head_ < data_.size())
            data_[head_] = item;
        else if (head_ == data_.size() && data_.size() < capacity_)
            data_.push_back(item);
        else
            throw circ_list_push(); // could occur if object isn't properly initialized
    }

    T &operator[](const size_t index)
    {
        if (index >= data_.size())
            throw circ_list_index();
        else if (index <= head_)
            return data_[head_ - index];
        else
            return data_[head_ + capacity_ - index];
    }

    const T &operator[](const size_t index) const
    {
        if (index >= data_.size())
            throw circ_list_const_index();
        else if (index <= head_)
            return data_[head_ - index];
        else
            return data_[head_ + capacity_ - index];
    }

  private:
    size_t capacity_;
    size_t head_;
    std::vector<T> data_;
};

} // namespace openvpn

#endif // OPENVPN_COMMON_CIRC_LIST_H
