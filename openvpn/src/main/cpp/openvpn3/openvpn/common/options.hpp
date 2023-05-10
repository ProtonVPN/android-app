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

// General-purpose options parser, used to parse the OpenVPN configuration
// file as well as the server-pushed options list.  Note that these classes
// don't get into the interpretation or typing of options -- they only care
// about parsing the options into lists of strings, and then presenting the
// complete configuration file as a list of options.
//
// The parser understands the general grammar of OpenVPN configuration
// files including:
//
// 1. option/argument parsing, quoting, escaping, and comments,
// 2. inline directives such as
//      <ca>
//      ...
//      </ca>
// 3. and meta-directives such as those used by OpenVPN Access Server such as:
//    # OVPN_ACCESS_SERVER_USERNAME=test
//
// The basic organization of the parser is as follows:
//
//   Option -- a list of strings, where the first string is the
//     option/directive name, and subsequent strings are arguments.
//
//   OptionList -- a list of Options that also contains a map for
//     optimal lookup of specific options

#ifndef OPENVPN_COMMON_OPTIONS_H
#define OPENVPN_COMMON_OPTIONS_H

#include <string>
#include <sstream>
#include <vector>
#include <algorithm>   // for std::sort, std::min
#include <utility>     // for std::move
#include <type_traits> // for std::is_nothrow_move_constructible
#include <unordered_map>
#include <cstdint> // for std::uint64_t

#include <openvpn/common/rc.hpp>
#include <openvpn/common/size.hpp>
#include <openvpn/common/number.hpp>
#include <openvpn/common/hexstr.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/split.hpp>
#include <openvpn/common/splitlines.hpp>
#include <openvpn/common/unicode.hpp>
#include <openvpn/common/option_error.hpp>

namespace openvpn {

class Option
{
  public:
    OPENVPN_UNTAGGED_EXCEPTION(RejectedException);
    enum
    {
        MULTILINE = 0x8000000,
    };

    // Validate string by size and multiline status.
    // OR max_len with MULTILINE to allow multiline string.
    // Return values:
    enum validate_status
    {
        STATUS_GOOD,
        STATUS_MULTILINE,
        STATUS_LENGTH,
    };

    // Options for render methods
    enum render_flags
    {
        RENDER_TRUNC_64 = (1 << 0), // truncate option after 64 chars
        RENDER_PASS_FMT = (1 << 1), // pass \r\n\t
        RENDER_NUMBER = (1 << 2),   // number lines
        RENDER_BRACKET = (1 << 3),  // quote options using []
        RENDER_UNUSED = (1 << 4),   // only show unused options
    };

    Option()
    {
        static_assert(std::is_nothrow_move_constructible<Option>::value, "class Option not noexcept move constructable");
    }

    template <typename T, typename... Args>
    Option(T first, Args... args)
    {
        reserve(1 + sizeof...(args));
        from_list(std::move(first), std::forward<Args>(args)...);
    }

    static validate_status validate(const std::string &str, const size_t max_len)
    {
        const size_t pos = str.find_first_of("\r\n");
        const size_t len = max_len & ((size_t)MULTILINE - 1); // NOTE -- use smallest flag value here
        if (pos != std::string::npos && !(max_len & MULTILINE))
            return STATUS_MULTILINE;
        else if (len > 0 && Unicode::utf8_length(str) > len)
            return STATUS_LENGTH;
        else
            return STATUS_GOOD;
    }

    static const char *validate_status_description(const validate_status status)
    {
        switch (status)
        {
        case STATUS_GOOD:
            return "good";
        case STATUS_MULTILINE:
            return "multiline";
        case STATUS_LENGTH:
            return "too long";
        default:
            return "unknown";
        }
    }

    void min_args(const size_t n) const
    {
        const size_t s = data.size();
        if (s < n)
            OPENVPN_THROW(option_error, err_ref() << " must have at least " << (n - 1) << " arguments");
    }

    void exact_args(const size_t n) const
    {
        const size_t s = data.size();
        if (s != n)
            OPENVPN_THROW(option_error, err_ref() << " must have exactly " << n << " arguments");
    }

    void validate_arg(const size_t index, const size_t max_len) const
    {
        if (max_len > 0 && index < data.size())
        {
            const validate_status status = validate(data[index], max_len);
            if (status != STATUS_GOOD)
                OPENVPN_THROW(option_error, err_ref() << " is " << validate_status_description(status));
        }
    }

    bool is_multiline() const
    {
        if (data.size() == 2)
        {
            const std::string &str = data[1];
            const size_t pos = str.find_first_of("\r\n");
            return pos != std::string::npos;
        }
        else
            return false;
    }

    static void validate_string(const std::string &name, const std::string &str, const size_t max_len)
    {
        const validate_status status = validate(str, max_len);
        if (status != STATUS_GOOD)
            OPENVPN_THROW(option_error, name << " is " << validate_status_description(status));
    }

    std::string printable_directive() const
    {
        try
        {
            if (data.size() > 0)
                return Unicode::utf8_printable(data[0], 32);
            else
                return "";
        }
        catch (const std::exception &)
        {
            return "[DIRECTIVE]";
        }
    }

    const std::string &get(const size_t index, const size_t max_len) const
    {
        min_args(index + 1);
        validate_arg(index, max_len);
        return data[index];
    }

    std::string get_optional(const size_t index, const size_t max_len) const
    {
        validate_arg(index, max_len);
        if (index < data.size())
            return data[index];
        else
            return "";
    }

    std::string get_default(const size_t index, const size_t max_len, const std::string &default_value) const
    {
        validate_arg(index, max_len);
        if (index < data.size())
            return data[index];
        else
            return default_value;
    }

    const std::string *get_ptr(const size_t index, const size_t max_len) const
    {
        validate_arg(index, max_len);
        if (index < data.size())
            return &data[index];
        else
            return nullptr;
    }

    template <typename T>
    T get_num(const size_t idx) const
    {
        typedef typename std::remove_const<T>::type T_nonconst;
        T_nonconst n(0); // we shouldn't need to initialize here, but some compilers complain "may be used uninitialized in this function"
        const std::string &numstr = get(idx, 64);
        if (numstr.length() >= 2 && numstr[0] == '0' && numstr[1] == 'x')
        {
            if (!parse_hex_number(numstr.substr(2), n))
                OPENVPN_THROW(option_error, err_ref() << '[' << idx << "] expecting a hex number");
        }
        else if (!parse_number<T_nonconst>(numstr, n))
            OPENVPN_THROW(option_error, err_ref() << '[' << idx << "] must be a number");
        return n;
    }

    template <typename T>
    T get_num(const size_t idx, const T default_value) const
    {
        if (size() > idx)
            return get_num<T>(idx);
        else
            return default_value;
    }

    template <typename T>
    T get_num(const size_t idx, const T default_value, const T min_value, const T max_value) const
    {
        const T ret = get_num<T>(idx, default_value);
        if (ret != default_value && (ret < min_value || ret > max_value))
            range_error(idx, min_value, max_value);
        return ret;
    }

    template <typename T>
    T get_num(const size_t idx, const T min_value, const T max_value) const
    {
        const T ret = get_num<T>(idx);
        if (ret < min_value || ret > max_value)
            range_error(idx, min_value, max_value);
        return ret;
    }

    std::string render(const unsigned int flags) const
    {
        std::ostringstream out;
        size_t max_len_flags = (flags & RENDER_TRUNC_64) ? 64 : 0;
        if (flags & RENDER_PASS_FMT)
            max_len_flags |= Unicode::UTF8_PASS_FMT;
        bool first = true;
        for (std::vector<std::string>::const_iterator i = data.begin(); i != data.end(); ++i)
        {
            if (!first)
                out << ' ';
            if (flags & RENDER_BRACKET)
                out << '[';
            out << Unicode::utf8_printable(*i, max_len_flags);
            if (flags & RENDER_BRACKET)
                out << ']';
            first = false;
        }
        return out.str();
    }

    static void escape_string(std::ostream &out, const std::string &term, const bool must_quote)
    {
        if (must_quote)
            out << '\"';
        for (std::string::const_iterator j = term.begin(); j != term.end(); ++j)
        {
            const char c = *j;
            if (c == '\"' || c == '\\')
                out << '\\';
            out << c;
        }
        if (must_quote)
            out << '\"';
    }

    // Render the option args into a string format such that it could be parsed back to
    // the equivalent option args.
    std::string escape(const bool csv) const
    {
        std::ostringstream out;
        bool more = false;
        for (std::vector<std::string>::const_iterator i = data.begin(); i != data.end(); ++i)
        {
            const std::string &term = *i;
            const bool must_quote = must_quote_string(term, csv);
            if (more)
                out << ' ';
            escape_string(out, term, must_quote);
            more = true;
        }
        return out.str();
    }

    void clear()
    {
        data.clear();
        touched_ = touchedState::NOT_TOUCHED;
        warn_only_if_unknown_ = false;
        meta_ = false;
    }

    // delegate to data
    size_t size() const
    {
        return data.size();
    }
    bool empty() const
    {
        return data.empty();
    }
    void push_back(const std::string &item)
    {
        data.push_back(item);
    }
    void push_back(std::string &&item)
    {
        data.push_back(std::move(item));
    }
    void reserve(const size_t n)
    {
        data.reserve(n);
    }
    void resize(const size_t n)
    {
        data.resize(n);
    }

    // raw references to data
    const std::string &ref(const size_t i) const
    {
        return data[i];
    }
    std::string &ref(const size_t i)
    {
        return data[i];
    }

    // equality
    bool operator==(const Option &other) const
    {
        return data == other.data;
    }
    bool operator!=(const Option &other) const
    {
        return data != other.data;
    }

    // remove first n elements
    void remove_first(const size_t n_elements)
    {
        const size_t n = std::min(data.size(), n_elements);
        if (n)
            data.erase(data.begin(), data.begin() + n);
    }

    /**
     * indicate that this option was processed
     *
     * @param lightly an option of the same name has been used
     */
    void touch(bool lightly = false) const
    {
        // Note that we violate constness here, which is done
        // because the touched bit is considered to be option metadata.
        if (lightly)
        {
            if (touched_ != touchedState::TOUCHED)
                touched_ = touchedState::OPTION_OF_SAME_NAME_TOUCHED;
        }
        else
        {
            touched_ = touchedState::TOUCHED;
        }
    }

    void enableWarnOnly()
    {
        warn_only_if_unknown_ = true;
    }

    bool warnonlyunknown() const
    {
        return warn_only_if_unknown_;
    }

    // was this option processed?
    bool touched() const
    {
        return touched_ == touchedState::TOUCHED;
    }

    // was an option of the same name (or this option see \c touched)
    // touched
    bool touched_lightly() const
    {
        return touched_ == touchedState::OPTION_OF_SAME_NAME_TOUCHED;
    }


    // refer to the option when constructing an error message
    std::string err_ref() const
    {
        std::string ret = "option";
        if (data.size())
        {
            ret += " '";
            ret += printable_directive();
            ret += '\'';
        }
        return ret;
    }

    /**
     * Marks this option as parsed from a meta options like
     * # OVPN_ACCESS_SERVER_USERNAME=username
     */
    void set_meta(bool value = true)
    {
        meta_ = value;
    }

    /**
     * This option has been parsed from a meta options like
     * # OVPN_ACCESS_SERVER_USERNAME=username
     * @return
     */
    bool meta() const
    {
        return meta_;
    }

  private:
    void from_list(std::string arg)
    {
        push_back(std::move(arg));
    }

    void from_list(const char *arg)
    {
        push_back(std::string(arg));
    }

    void from_list(std::vector<std::string> arg)
    {
        data.insert(data.end(), arg.begin(), arg.end());
    }

    template <typename T, typename... Args>
    void from_list(T first, Args... args)
    {
        from_list(std::move(first));
        from_list(std::forward<Args>(args)...);
    }

    template <typename T>
    void range_error(const size_t idx, const T min_value, const T max_value) const
    {
        OPENVPN_THROW(option_error, err_ref() << '[' << idx << "] must be in the range [" << min_value << ',' << max_value << ']');
    }

    bool must_quote_string(const std::string &str, const bool csv) const
    {
        for (const auto c : str)
        {
            if (string::is_space(c))
                return true;
            if (csv && c == ',')
                return true;
        }
        return false;
    }

    /** Indicates that this option was used/consumed */
    enum class touchedState
    {
        /* Option was never used */
        NOT_TOUCHED,
        /** Indicates that another option with the same name
         * was considered. Ie, the option was not used because
         * another option with same overrode it */
        OPTION_OF_SAME_NAME_TOUCHED,
        /** Option has be used */
        TOUCHED
    };
    volatile mutable touchedState touched_ = touchedState::NOT_TOUCHED;

    bool warn_only_if_unknown_ = false;
    bool meta_ = false;
    std::vector<std::string> data;
};

class OptionList : public std::vector<Option>, public RCCopyable<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<OptionList> Ptr;
    typedef std::vector<unsigned int> IndexList;
    typedef std::unordered_map<std::string, IndexList> IndexMap;
    typedef std::pair<std::string, IndexList> IndexPair;

    static bool is_comment(const char c)
    {
        return c == '#' || c == ';';
    }

    // standard lex filter that doesn't understand end-of-line comments
    typedef StandardLex Lex;

    // special lex filter that recognizes end-of-line comments
    class LexComment
    {
      public:
        LexComment()
            : in_quote_(false), in_comment(false), backslash(false), ch(-1)
        {
        }

        void put(char c)
        {
            if (in_comment)
            {
                ch = -1;
            }
            else if (backslash)
            {
                ch = c;
                backslash = false;
            }
            else if (c == '\\')
            {
                backslash = true;
                ch = -1;
            }
            else if (c == '\"')
            {
                in_quote_ = !in_quote_;
                ch = -1;
            }
            else if (is_comment(c) && !in_quote_)
            {
                in_comment = true;
                ch = -1;
            }
            else
            {
                ch = c;
            }
        }

        bool available() const
        {
            return ch != -1;
        }
        int get() const
        {
            return ch;
        }
        void reset()
        {
            ch = -1;
        }

        bool in_quote() const
        {
            return in_quote_;
        }

      private:
        bool in_quote_;
        bool in_comment;
        bool backslash;
        int ch;
    };

    class Limits
    {
      public:
        Limits(const std::string &error_message,
               const std::uint64_t max_bytes_arg,
               const size_t extra_bytes_per_opt_arg,
               const size_t extra_bytes_per_term_arg,
               const size_t max_line_len_arg,
               const size_t max_directive_len_arg)
            : bytes(0),
              max_bytes(max_bytes_arg),
              extra_bytes_per_opt(extra_bytes_per_opt_arg),
              extra_bytes_per_term(extra_bytes_per_term_arg),
              max_line_len(max_line_len_arg),
              max_directive_len(max_directive_len_arg),
              err(error_message)
        {
        }

        void add_bytes(const size_t n)
        {
            bytes += n;
            check_overflow();
        }

        void add_string(const std::string &str)
        {
            bytes += str.length();
            check_overflow();
        }

        void add_term()
        {
            bytes += extra_bytes_per_term;
            check_overflow();
        }

        void add_opt()
        {
            bytes += extra_bytes_per_opt;
            check_overflow();
        }

        size_t get_max_line_len() const
        {
            return max_line_len;
        }

        std::uint64_t get_bytes() const
        {
            return bytes;
        }

        void validate_directive(const Option &opt)
        {
            opt.validate_arg(0, max_directive_len);
        }

      private:
        void check_overflow()
        {
            if (bytes >= max_bytes)
                error();
        }

        void error()
        {
            throw option_error(err);
        }

        std::uint64_t bytes;
        const std::uint64_t max_bytes;
        const size_t extra_bytes_per_opt;
        const size_t extra_bytes_per_term;
        const size_t max_line_len;
        const size_t max_directive_len;
        const std::string err;
    };

    // Used by extend() to optionally control which options are copied.
    struct FilterBase : public RC<thread_unsafe_refcount>
    {
        typedef RCPtr<FilterBase> Ptr;
        virtual bool filter(const Option &opt) = 0;
    };

    class KeyValue : public RC<thread_unsafe_refcount>
    {
      public:
        typedef RCPtr<KeyValue> Ptr;

        KeyValue()
            : key_priority(0)
        {
        }
        KeyValue(const std::string &key_arg, const std::string &value_arg, const int key_priority_arg = 0)
            : key(key_arg), value(value_arg), key_priority(key_priority_arg)
        {
        }

        size_t combined_length() const
        {
            return key.length() + value.length();
        }

        Option convert_to_option(Limits *lim) const
        {
            bool newline_present = false;
            Option opt;
            const std::string unesc_value = unescape(value, newline_present);
            opt.push_back(key);
            if (newline_present || singular_arg(key))
                opt.push_back(unesc_value);
            else if (unesc_value != "NOARGS")
                Split::by_space_void<Option, Lex, SpaceMatch, Limits>(opt, unesc_value, lim);
            return opt;
        }

        void split_priority()
        {
            // look for usage such as: remote.7
            const size_t dp = key.find_last_of(".");
            if (dp != std::string::npos)
            {
                const size_t tp = dp + 1;
                if (tp < key.length())
                {
                    const char *tail = key.c_str() + tp;
                    try
                    {
                        key_priority = parse_number_throw<int>(tail, "option priority");
                        key = key.substr(0, dp);
                    }
                    catch (const number_parse_exception &)
                    {
                        ;
                    }
                }
            }
        }

        static bool compare(const Ptr &a, const Ptr &b)
        {
            const int cmp = a->key.compare(b->key);
            if (cmp < 0)
                return true;
            else if (cmp > 0)
                return false;
            else
                return a->key_priority < b->key_priority;
        }

        std::string key;
        std::string value;
        int key_priority;

      private:
        static std::string unescape(const std::string &value, bool &newline_present)
        {
            std::string ret;
            ret.reserve(value.length());

            bool bs = false;
            for (size_t i = 0; i < value.length(); ++i)
            {
                const char c = value[i];
                if (bs)
                {
                    if (c == 'n')
                    {
                        ret += '\n';
                        newline_present = true;
                    }
                    else if (c == '\\')
                        ret += '\\';
                    else
                    {
                        ret += '\\';
                        ret += c;
                    }
                    bs = false;
                }
                else
                {
                    if (c == '\\')
                        bs = true;
                    else
                        ret += c;
                }
            }
            if (bs)
                ret += '\\';
            return ret;
        }

        static bool singular_arg(const std::string &key)
        {
            bool upper = false;
            bool lower = false;
            for (size_t i = 0; i < key.length(); ++i)
            {
                const char c = key[i];
                if (c >= 'a' && c <= 'z')
                    lower = true;
                else if (c >= 'A' && c <= 'Z')
                    upper = true;
            }
            return upper && !lower;
        }
    };

    struct KeyValueList : public std::vector<KeyValue::Ptr>
    {
        void preprocess()
        {
            split_priority();
            sort();
        }

        void split_priority()
        {
            for (iterator i = begin(); i != end(); ++i)
            {
                KeyValue &kv = **i;
                kv.split_priority();
            }
        }

        void sort()
        {
            std::sort(begin(), end(), KeyValue::compare);
        }
    };

    OptionList()
    {
    }

    template <typename T, typename... Args>
    explicit OptionList(T first, Args... args)
    {
        reserve(1 + sizeof...(args));
        from_list(std::move(first), std::forward<Args>(args)...);
        update_map();
    }

    static OptionList parse_from_csv_static(const std::string &str, Limits *lim)
    {
        OptionList ret;
        ret.parse_from_csv(str, lim);
        ret.update_map();
        return ret;
    }

    static OptionList parse_from_csv_static_nomap(const std::string &str, Limits *lim)
    {
        OptionList ret;
        ret.parse_from_csv(str, lim);
        return ret;
    }

    static OptionList parse_from_config_static(const std::string &str, Limits *lim)
    {
        OptionList ret;
        ret.parse_from_config(str, lim);
        ret.update_map();
        return ret;
    }

    static OptionList::Ptr parse_from_config_static_ptr(const std::string &str, Limits *lim)
    {
        OptionList::Ptr ret = new OptionList();
        ret->parse_from_config(str, lim);
        ret->update_map();
        return ret;
    }

    static OptionList parse_from_argv_static(const std::vector<std::string> &argv)
    {
        OptionList ret;
        ret.parse_from_argv(argv);
        ret.update_map();
        return ret;
    }

    void clear()
    {
        std::vector<Option>::clear();
        map_.clear();
    }

    // caller should call update_map() after this function
    void parse_from_csv(const std::string &str, Limits *lim)
    {
        if (lim)
            lim->add_string(str);
        std::vector<std::string> list = Split::by_char<std::vector<std::string>, Lex, Limits>(str, ',', 0, ~0, lim);
        for (std::vector<std::string>::const_iterator i = list.begin(); i != list.end(); ++i)
        {
            const Option opt = Split::by_space<Option, Lex, SpaceMatch, Limits>(*i, lim);
            if (opt.size())
            {
                if (lim)
                {
                    lim->add_opt();
                    lim->validate_directive(opt);
                }
                push_back(std::move(opt));
            }
        }
    }

    // caller should call update_map() after this function
    void parse_from_argv(const std::vector<std::string> &argv)
    {
        Option opt;
        for (auto &arg : argv)
        {
            std::string a = arg;
            if (string::starts_with(a, "--"))
            {
                if (!opt.empty())
                {
                    push_back(std::move(opt));
                    opt.clear();
                }
                a = a.substr(2);
            }
            if (!a.empty())
                opt.push_back(a);
        }
        if (!opt.empty())
            push_back(std::move(opt));
    }

    // caller should call update_map() after this function
    void parse_from_peer_info(const std::string &str, Limits *lim)
    {
        if (lim)
            lim->add_string(str);
        SplitLines in(str, 0);
        while (in(true))
        {
            const std::string &line = in.line_ref();
            Option opt;
            opt.reserve(2);
            Split::by_char_void<Option, NullLex, Limits>(opt, line, '=', 0, 1, lim);
            if (opt.size())
            {
                if (lim)
                {
                    lim->add_opt();
                    lim->validate_directive(opt);
                }
                push_back(std::move(opt));
            }
        }
    }

    // caller may want to call list.preprocess() before this function
    // caller should call update_map() after this function
    void parse_from_key_value_list(const KeyValueList &list, Limits *lim)
    {
        for (KeyValueList::const_iterator i = list.begin(); i != list.end(); ++i)
        {
            const KeyValue &kv = **i;
            if (lim)
                lim->add_bytes(kv.combined_length());
            const Option opt = kv.convert_to_option(lim);
            if (lim)
            {
                lim->add_opt();
                lim->validate_directive(opt);
            }
            push_back(std::move(opt));
        }
    }

    static Option parse_option_from_line(const std::string &line, Limits *lim)
    {
        return Split::by_space<Option, LexComment, SpaceMatch, Limits>(line, lim);
    }

    // caller should call update_map() after this function
    void parse_from_config(const std::string &str, Limits *lim)
    {
        if (lim)
            lim->add_string(str);

        SplitLines in(str, lim ? lim->get_max_line_len() : 0);
        int line_num = 0;
        bool in_multiline = false;
        Option multiline;
        while (in(true))
        {
            ++line_num;
            if (in.line_overflow())
                line_too_long(line_num);
            const std::string &line = in.line_ref();
            if (in_multiline)
            {
                if (is_close_tag(line, multiline.ref(0)))
                {
                    if (lim)
                    {
                        lim->add_opt();
                        lim->validate_directive(multiline);
                    }
                    multiline.set_meta(true);
                    push_back(std::move(multiline));
                    multiline.clear();
                    in_multiline = false;
                }
                else
                {
                    std::string &mref = multiline.ref(1);
                    mref += line;
                    mref += '\n';
                }
            }
            else if (!ignore_line(line))
            {
                Option opt = parse_option_from_line(line, lim);
                if (opt.size())
                {
                    if (is_open_tag(opt.ref(0)))
                    {
                        if (opt.size() > 1)
                            extraneous_err(line_num, "option", opt);
                        untag_open_tag(opt.ref(0));
                        opt.push_back("");
                        multiline = opt;
                        in_multiline = true;
                    }
                    else
                    {
                        if (lim)
                        {
                            lim->add_opt();
                            lim->validate_directive(opt);
                        }
                        push_back(std::move(opt));
                    }
                }
            }
        }
        if (in_multiline)
            not_closed_out_err("option", multiline);
    }

    // caller should call update_map() after this function
    void parse_meta_from_config(const std::string &str, const std::string &tag, Limits *lim)
    {
        SplitLines in(str, lim ? lim->get_max_line_len() : 0);
        int line_num = 0;
        bool in_multiline = false;
        Option multiline;
        const std::string prefix = tag + "_";
        while (in(true))
        {
            ++line_num;
            if (in.line_overflow())
                line_too_long(line_num);
            std::string &line = in.line_ref();
            if (string::starts_with(line, "# "))
            {
                line = std::string(line, 2);
                if (in_multiline)
                {
                    if (is_close_meta_tag(line, prefix, multiline.ref(0)))
                    {
                        if (lim)
                        {
                            lim->add_opt();
                            lim->validate_directive(multiline);
                        }
                        multiline.set_meta(true);
                        push_back(std::move(multiline));
                        multiline.clear();
                        in_multiline = false;
                    }
                    else
                    {
                        std::string &mref = multiline.ref(1);
                        mref += line;
                        mref += '\n';
                    }
                }
                else if (string::starts_with(line, prefix))
                {
                    Option opt = Split::by_char<Option, NullLex, Limits>(std::string(line, prefix.length()), '=', 0, 1, lim);
                    if (opt.size())
                    {
                        if (is_open_meta_tag(opt.ref(0)))
                        {
                            if (opt.size() > 1)
                                extraneous_err(line_num, "meta option", opt);
                            untag_open_meta_tag(opt.ref(0));
                            opt.push_back("");
                            multiline = opt;
                            in_multiline = true;
                        }
                        else
                        {
                            if (lim)
                            {
                                lim->add_opt();
                                lim->validate_directive(opt);
                            }
                            opt.set_meta(true);
                            push_back(std::move(opt));
                        }
                    }
                }
            }
        }
        if (in_multiline)
            not_closed_out_err("meta option", multiline);
    }

    // Append elements in other to self,
    // caller should call update_map() after this function.
    void extend(const OptionList &other, FilterBase *filt = nullptr)
    {
        reserve(size() + other.size());
        for (const auto &opt : other)
        {
            if (!filt || filt->filter(opt))
            {
                push_back(opt);
                opt.touch();
            }
        }
    }

    // Append elements in other to self,
    // consumes other,
    // caller should call update_map() after this function.
    void extend(OptionList &&other, FilterBase *filt = nullptr)
    {
        reserve(size() + other.size());
        for (auto &opt : other)
        {
            if (!filt || filt->filter(opt))
                push_back(std::move(opt));
        }
    }

    // Append elements in other having given name to self,
    // caller should call update_map() after this function.
    // Return the number of elements processed.
    unsigned int extend(const OptionList &other, const std::string &name)
    {
        IndexMap::const_iterator oi = other.map().find(name);
        unsigned int count = 0;
        if (oi != other.map().end())
            for (IndexList::const_iterator i = oi->second.begin(); i != oi->second.end(); ++i)
            {
                const Option &opt = other[*i];
                push_back(opt);
                opt.touch();
                ++count;
            }
        return count;
    }

    // Append to self only those elements in other that do not exist
    // in self, caller should call update_map() after this function.
    // Caller should also consider calling update_map() before this function,
    // to ensure that lookups on this->map will see up-to-date data.
    void extend_nonexistent(const OptionList &other)
    {
        for (std::vector<Option>::const_iterator i = other.begin(); i != other.end(); ++i)
        {
            const Option &opt = *i;
            if (!opt.empty() && map().find(opt.ref(0)) == map().end())
            {
                push_back(opt);
                opt.touch();
            }
        }
    }

    // Get the last instance of an option, or return nullptr if option
    // doesn't exist.
    const Option *get_ptr(const std::string &name) const
    {
        IndexMap::const_iterator e = map_.find(name);
        if (e != map_.end())
        {
            const size_t size = e->second.size();
            if (size)
            {
                for (const auto &optidx : e->second)
                {
                    (*this)[optidx].touch(true);
                }
                const Option *ret = &((*this)[e->second[size - 1]]);
                ret->touch();
                return ret;
            }
        }
        return nullptr;
    }

    // Get an option, return nullptr if option doesn't exist, or
    // throw an error if more than one instance exists.
    const Option *get_unique_ptr(const std::string &name) const
    {
        IndexMap::const_iterator e = map_.find(name);
        if (e != map_.end() && !e->second.empty())
        {
            if (e->second.size() == 1)
            {
                const Option *ret = &((*this)[e->second[0]]);
                ret->touch();
                return ret;
            }
            else
                OPENVPN_THROW(option_error, "more than one instance of option '" << name << '\'');
        }
        else
            return nullptr;
    }

    // Get an option, throw an error if more than one instance exists and the instances
    // are not exact duplicates of one other.
    const Option *get_consistent(const std::string &name) const
    {
        IndexMap::const_iterator e = map_.find(name);
        if (e != map_.end() && !e->second.empty())
        {
            const Option *first = &((*this)[e->second[0]]);
            first->touch();
            if (e->second.size() >= 2)
            {
                for (size_t i = 1; i < e->second.size(); ++i)
                {
                    const Option *other = &(*this)[e->second[i]];
                    other->touch();
                    if (*other != *first)
                        OPENVPN_THROW(option_error, "more than one instance of option '" << name << "' with inconsistent argument(s)");
                }
            }
            return first;
        }
        else
            return nullptr;
    }

    // Get option, throw error if not found
    // If multiple options of the same name exist, return
    // the last one.
    const Option &get(const std::string &name) const
    {
        const Option *o = get_ptr(name);
        if (o)
            return *o;
        else
            OPENVPN_THROW(option_error, "option '" << name << "' not found");
    }

    // Get the list of options having the same name (by index),
    // throw an exception if option is not found.
    const IndexList &get_index(const std::string &name) const
    {
        IndexMap::const_iterator e = map_.find(name);
        if (e != map_.end() && !e->second.empty())
            return e->second;
        else
            OPENVPN_THROW(option_error, "option '" << name << "' not found");
    }

    // Get the list of options having the same name (by index),
    // return nullptr is option is not found.
    const IndexList *get_index_ptr(const std::string &name) const
    {
        IndexMap::const_iterator e = map_.find(name);
        if (e != map_.end() && !e->second.empty())
            return &e->second;
        else
            return nullptr;
    }

    // Concatenate all one-arg directives of a given name, in index order.
    std::string cat(const std::string &name) const
    {
        std::string ret;
        const OptionList::IndexList *il = get_index_ptr(name);
        if (il)
        {
            size_t size = 0;
            OptionList::IndexList::const_iterator i;
            for (i = il->begin(); i != il->end(); ++i)
            {
                const Option &o = (*this)[*i];
                if (o.size() == 2)
                    size += o.ref(1).length() + 1;
                else
                    OPENVPN_THROW(option_error, "option '" << name << "' (" << o.size() << ") must have exactly one parameter");
            }
            ret.reserve(size);
            for (i = il->begin(); i != il->end(); ++i)
            {
                const Option &o = (*this)[*i];
                if (o.size() >= 2)
                {
                    o.touch();
                    ret += o.ref(1);
                    string::add_trailing(ret, '\n');
                }
            }
        }
        return ret;
    }

    // Return true if option exists, but raise an exception if multiple
    // instances of the option exist.
    bool exists_unique(const std::string &name) const
    {
        return get_unique_ptr(name) != nullptr;
    }

    // Return true if one or more instances of a given option exist.
    bool exists(const std::string &name) const
    {
        return get_ptr(name) != nullptr;
    }

    // Convenience method that gets a particular argument index within an option,
    // while raising an exception if option doesn't exist or if argument index
    // is out-of-bounds.
    const std::string &get(const std::string &name, size_t index, const size_t max_len) const
    {
        const Option &o = get(name);
        return o.get(index, max_len);
    }

    // Convenience method that gets a particular argument index within an option,
    // while returning the empty string if option doesn't exist, and raising an
    // exception if argument index is out-of-bounds.
    std::string get_optional(const std::string &name, size_t index, const size_t max_len) const
    {
        const Option *o = get_ptr(name);
        if (o)
            return o->get(index, max_len);
        else
            return "";
    }

    // Like get_optional(), but return "" if argument index is out-of-bounds.
    std::string get_optional_relaxed(const std::string &name, size_t index, const size_t max_len) const
    {
        const Option *o = get_ptr(name);
        if (o)
            return o->get_optional(index, max_len);
        else
            return "";
    }

    // Like get_optional(), but return "" if exception is thrown.
    std::string get_optional_noexcept(const std::string &name, size_t index, const size_t max_len) const
    {
        try
        {
            return get_optional(name, index, max_len);
        }
        catch (const std::exception &)
        {
            return "";
        }
    }

    // Return raw C string to option data or nullptr if option doesn't exist.
    const char *get_c_str(const std::string &name, size_t index, const size_t max_len) const
    {
        const Option *o = get_ptr(name);
        if (o)
            return o->get(index, max_len).c_str();
        else
            return nullptr;
    }

    // Convenience method that gets a particular argument index within an option,
    // while returning a default string if option doesn't exist, and raising an
    // exception if argument index is out-of-bounds.
    std::string get_default(const std::string &name,
                            size_t index,
                            const size_t max_len,
                            const std::string &default_value) const
    {
        const Option *o = get_ptr(name);
        if (o)
            return o->get(index, max_len);
        else
            return default_value;
    }

    // Like get_default(), but return default_value if argument index is out-of-bounds.
    std::string get_default_relaxed(const std::string &name,
                                    size_t index,
                                    const size_t max_len,
                                    const std::string &default_value) const
    {
        const Option *o = get_ptr(name);
        if (o)
        {
            const std::string *s = o->get_ptr(index, max_len);
            if (s)
                return *s;
        }
        return default_value;
    }

    template <typename T>
    T get_num(const std::string &name, const size_t idx, const T default_value) const
    {
        typedef typename std::remove_const<T>::type T_nonconst;
        T_nonconst n = default_value;
        const Option *o = get_ptr(name);
        if (o)
            n = o->get_num<T>(idx, default_value);
        return n;
    }

    template <typename T>
    T get_num(const std::string &name,
              const size_t idx,
              const T default_value,
              const T min_value,
              const T max_value) const
    {
        typedef typename std::remove_const<T>::type T_nonconst;
        T_nonconst n = default_value;
        const Option *o = get_ptr(name);
        if (o)
            n = o->get_num<T>(idx, default_value, min_value, max_value);
        return n;
    }

    template <typename T>
    T get_num(const std::string &name, const size_t idx, const T min_value, const T max_value) const
    {
        const Option &o = get(name);
        return o.get_num<T>(idx, min_value, max_value);
    }

    template <typename T>
    T get_num(const std::string &name, const size_t idx) const
    {
        const Option &o = get(name);
        return o.get_num<T>(idx);
    }

    // Touch an option, if it exists.
    void touch(const std::string &name) const
    {
        const Option *o = get_ptr(name);
        if (o)
            o->touch();
    }

    // Render object as a string.
    // flags should be given as Option::render_flags.
    std::string render(const unsigned int flags) const
    {
        std::ostringstream out;
        for (size_t i = 0; i < size(); ++i)
        {
            const Option &o = (*this)[i];
            if (!(flags & Option::RENDER_UNUSED) || !o.touched())
            {
                if (flags & Option::RENDER_NUMBER)
                    out << i << ' ';
                out << o.render(flags) << std::endl;
            }
        }
        return out.str();
    }

    std::string render_csv() const
    {
        std::string ret;
        bool first = true;
        for (auto &e : *this)
        {
            if (!first)
                ret += ',';
            ret += e.escape(true);
            first = false;
        }
        return ret;
    }

    // Render contents of hash map used to locate options after underlying option list
    // has been modified.
    std::string render_map() const
    {
        std::ostringstream out;
        for (IndexMap::const_iterator i = map_.begin(); i != map_.end(); ++i)
        {
            out << i->first << " [";
            for (IndexList::const_iterator j = i->second.begin(); j != i->second.end(); ++j)
                out << ' ' << *j;
            out << " ]" << std::endl;
        }
        return out.str();
    }

    // Return number of unused options based on the notion that
    // all used options have been touched.
    size_t n_unused(bool ignore_meta = false) const
    {
        size_t n = 0;
        for (std::vector<Option>::const_iterator i = begin(); i != end(); ++i)
        {
            const Option &opt = *i;
            if (!opt.touched() && !(opt.meta() && ignore_meta))
                ++n;
        }
        return n;
    }

    // Return number of unused meta options based on the notion that
    // all used options have been touched.
    size_t meta_unused() const
    {
        size_t n = 0;
        for (std::vector<Option>::const_iterator i = begin(); i != end(); ++i)
        {
            const Option &opt = *i;
            if (opt.meta() && !opt.touched())
                ++n;
        }
        return n;
    }

    void show_unused_options(const char *title = nullptr) const
    {
        // show unused options
        if (n_unused())
        {
            if (!title)
                title = "NOTE: Unused Options";
            OPENVPN_LOG_NTNL(title << std::endl
                                   << render(Option::RENDER_TRUNC_64 | Option::RENDER_NUMBER | Option::RENDER_BRACKET | Option::RENDER_UNUSED));
        }
    }

    // Add item to underlying option list while updating map as well.
    void add_item(const Option &opt)
    {
        if (!opt.empty())
        {
            const size_t i = size();
            push_back(opt);
            map_[opt.ref(0)].push_back((unsigned int)i);
        }
    }

    // Return hash map used to locate options.
    const IndexMap &map() const
    {
        return map_;
    }

    // Rebuild hash map used to locate options after underlying option list
    // has been modified.
    void update_map()
    {
        map_.clear();
        for (size_t i = 0; i < size(); ++i)
        {
            const Option &opt = (*this)[i];
            if (!opt.empty())
                map_[opt.ref(0)].push_back((unsigned int)i);
        }
    }

    // return true if line is blank or a comment
    static bool ignore_line(const std::string &line)
    {
        for (std::string::const_iterator i = line.begin(); i != line.end(); ++i)
        {
            const char c = *i;
            if (!SpaceMatch::is_space(c))
                return is_comment(c);
        }
        return true;
    }

    // multiline tagging

    // return true if string is a tag, e.g. "<ca>"
    static bool is_open_tag(const std::string &str)
    {
        const size_t n = str.length();
        return n >= 3 && str[0] == '<' && str[1] != '/' && str[n - 1] == '>';
    }

    // return true if string is a close tag, e.g. "</ca>"
    static bool is_close_tag(const std::string &str, const std::string &tag)
    {
        const size_t n = str.length();
        return n >= 4 && str[0] == '<' && str[1] == '/' && str.substr(2, n - 3) == tag && str[n - 1] == '>';
    }

    // remove <> chars from open tag
    static void untag_open_tag(std::string &str)
    {
        const size_t n = str.length();
        if (n >= 3)
            str = str.substr(1, n - 2);
    }

    // detect multiline breakout attempt (return true)
    static bool detect_multiline_breakout_nothrow(const std::string &opt, const std::string &tag)
    {
        std::string line;
        for (auto &c : opt)
        {
            if (c == '\n' || c == '\r')
                line.clear();
            else
            {
                line += c;
                if (tag.empty())
                {
                    if (line.length() >= 2
                        && line[0] == '<'
                        && line[1] == '/')
                        return true;
                }
                else if (is_close_tag(line, tag))
                    return true;
            }
        }
        return false;
    }

    // detect multiline breakout attempt
    static void detect_multiline_breakout(const std::string &opt, const std::string &tag)
    {
        if (detect_multiline_breakout_nothrow(opt, tag))
            throw option_error("multiline breakout detected");
    }

  private:
    // multiline tagging (meta)

    // return true if string is a meta tag, e.g. WEB_CA_BUNDLE_START
    static bool is_open_meta_tag(const std::string &str)
    {
        return string::ends_with(str, "_START");
    }

    // return true if string is a tag, e.g. WEB_CA_BUNDLE_STOP
    static bool is_close_meta_tag(const std::string &str, const std::string &prefix, const std::string &tag)
    {
        return prefix + tag + "_STOP" == str;
    }

    // remove trailing "_START" from open tag
    static void untag_open_meta_tag(std::string &str)
    {
        const size_t n = str.length();
        if (n >= 6)
            str = std::string(str, 0, n - 6);
    }

    static void extraneous_err(const int line_num, const char *type, const Option &opt)
    {
        OPENVPN_THROW(option_error, "line " << line_num << ": " << type << " <" << opt.printable_directive() << "> is followed by extraneous text");
    }

    static void not_closed_out_err(const char *type, const Option &opt)
    {
        OPENVPN_THROW(option_error, type << " <" << opt.printable_directive() << "> was not properly closed out");
    }

    static void line_too_long(const int line_num)
    {
        OPENVPN_THROW(option_error, "line " << line_num << " is too long");
    }

    void from_list(Option opt)
    {
        push_back(std::move(opt));
    }

    template <typename T, typename... Args>
    void from_list(T first, Args... args)
    {
        from_list(std::move(first));
        from_list(std::forward<Args>(args)...);
    }

    IndexMap map_;
};

} // namespace openvpn

#endif // OPENVPN_COMMON_OPTIONS_H
