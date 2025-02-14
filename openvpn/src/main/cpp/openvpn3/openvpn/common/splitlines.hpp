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

// General purpose class to split a multi-line string into lines.

#ifndef OPENVPN_COMMON_SPLITLINES_H
#define OPENVPN_COMMON_SPLITLINES_H

#include <utility>

#include <openvpn/common/string.hpp>

namespace openvpn {
template <typename STRING>
class SplitLinesType
{
  public:
    OPENVPN_EXCEPTION(overflow_error);
    OPENVPN_EXCEPTION(moved_error);

    /**
     * Initialises SplitLinesType object with pointer to str
     *
     * @remark Note: string/buffer passed to constructor is not locally stored,
     * so it must remain in scope and not be modified during the lifetime
     * of the SplitLines object.
     *
     * @param str Input string.
     * @param max_line_len_arg If not 0, specifies line length that
     *                         will trigger overflow error.
     */
    SplitLinesType(const STRING &str, const size_t max_line_len_arg = 0)
        : data((const char *)str.c_str()),
          size(str.length()),
          max_line_len(max_line_len_arg)
    {
    }

    /**
     * Read next line so that it can be accessed with line_ref or line_move.
     *
     * If max_line_len is greater zero, read at most max_line_len characters.
     *
     * @param trim If true, remove trailing \\n or \\r\\n
     * @return Returns true if any characters were read.
     */
    bool operator()(const bool trim = true)
    {
        line.clear();
        overflow = false;
        line_valid = true;
        const size_t overflow_index = index + max_line_len;
        while (index < size)
        {
            if (max_line_len && index >= overflow_index)
            {
                overflow = true;
                return true;
            }
            const char c = data[index++];
            line += c;
            if (c == '\n' || index >= size)
            {
                if (trim)
                    string::trim_crlf(line);
                return true;
            }
        }
        line_valid = false;
        return false;
    }

    /**
     * Returns true if max_line_len is greater zero and the current line was
     * longer than max_line_len characters.
     */
    bool line_overflow() const
    {
        return overflow;
    }

    /**
     * Returns reference to current line.
     *
     * Throws an exception if there is no line available currently.
     * Throws an exception if line_overflow() returns true.
     */
    std::string &line_ref()
    {
        validate();
        return line;
    }

    /**
     * Returns const reference to current line.
     *
     * Throws an exception if there is no line available currently.
     * Throws an exception if line_overflow() returns true.
     */
    const std::string &line_ref() const
    {
        validate();
        return line;
    }

    /**
     * Returns the moved current line.
     *
     * Throws an exception if there is no line available currently.
     * Throws an exception if line_overflow() returns true.
     * Further calls to line_ref() or line_moved() will throw an exception until
     * operator() is called again.
     */
    std::string line_move()
    {
        validate();
        line_valid = false;
        return std::move(line);
    }

    enum Status
    {
        S_OKAY, //!< next line was successfully read
        S_EOF,  //!< no further characters are available
        S_ERROR //!< line was longer than allowed
    };

    /**
     * Read the next line and move it into ln.
     *
     * Does not throw an exception on overflow but instead
     * returns S_ERROR. If nothing could be read, returns
     * S_EOF.
     * Since the line is moved into the argument, you can't
     * use line_ref() or line_moved() on the object afterwards.
     * In general calls to operator()+line_ref() and next()
     * are not intended to be mixed.
     *
     * @param ln   string to move the line into.
     * @param trim If true, remove trailing \\n or \\r\\n
     * @return Returns S_OKAY if a line was moved into ln.
     */
    Status next(std::string &ln, const bool trim = true)
    {
        const bool s = (*this)(trim);
        if (!s)
            return S_EOF;
        if (overflow)
            return S_ERROR;
        ln = std::move(line);
        line_valid = false;
        return S_OKAY;
    }

  private:
    void validate()
    {
        if (!line_valid)
            throw moved_error();
        if (overflow)
            throw overflow_error(line);
    }

    const char *data;
    size_t size;
    const size_t max_line_len;
    size_t index = 0;
    std::string line;
    bool line_valid = false;
    bool overflow = false;
};

typedef SplitLinesType<std::string> SplitLines;
} // namespace openvpn

#endif
