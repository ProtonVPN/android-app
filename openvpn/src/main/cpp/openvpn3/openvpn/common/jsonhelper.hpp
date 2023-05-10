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

#pragma once

#include <string>
#include <cstring>
#include <cstdint>
#include <utility>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/string.hpp>
#include <openvpn/common/number.hpp>
#include <openvpn/common/file.hpp>
#include <openvpn/common/jsonlib.hpp>
#include <openvpn/common/jsonhelperfmt.hpp>
#include <openvpn/buffer/bufstr.hpp>
#include <openvpn/buffer/bufstream.hpp>

#ifndef HAVE_JSON
#error no JSON library available
#endif

namespace openvpn {
namespace json {

OPENVPN_EXCEPTION(json_parse);

template <typename TITLE>
inline Json::Value parse(const std::string &str, const TITLE &title)
{
#ifdef OPENVPN_JSON
    return Json::Value::parse(str, StringTempl::to_string(title));
#else
    Json::CharReaderBuilder builder;
    builder["collectComments"] = false;
    Json::Value root;
    std::string errors;
    std::istringstream instr(str);

    if (!Json::parseFromStream(builder, instr, &root, &errors))
        throw json_parse(StringTempl::to_string(title) + " : " + errors);
    return root;
#endif
}

inline Json::Value parse(const std::string &str)
{
    return parse(str, "json");
}

inline Json::Value parse_from_file(const std::string &fn)
{
    return parse(read_text_utf8(fn), fn);
}

template <typename BUFFER, typename TITLE>
inline Json::Value parse_from_buffer(const BUFFER &buf, const TITLE &title)
{
#ifdef OPENVPN_JSON
    return Json::Value::parse(buf, StringTempl::to_string(title));
#else
    Json::CharReaderBuilder builder;
    builder["collectComments"] = false;
    Json::Value root;
    std::string errors;

    std::unique_ptr<Json::CharReader> reader(builder.newCharReader());
    if (!reader->parse(reinterpret_cast<const char *>(buf.c_data()), reinterpret_cast<const char *>(buf.c_data()) + buf.size(), &root, &errors))
        throw json_parse(StringTempl::to_string(title) + " : " + errors);
    return root;
#endif
}

#ifdef OPENVPN_JSON_INTERNAL
template <typename NAME, typename TITLE>
inline const Json::Value &cast(const Json::ValueType target_type,
                               const Json::Value &value,
                               const NAME &name,
                               const bool optional,
                               const TITLE &title)
{
    if (value.isNull())
    {
        if (optional)
            return value;
        throw json_parse(Json::Value::type_string(target_type) + " cast " + fmt_name(name, title) + " is null");
    }
    if (!value.isConvertibleTo(target_type))
        throw json_parse(Json::Value::type_string(target_type) + " cast " + fmt_name(name, title) + " is of incorrect type (" + value.type_string() + ')');
    return value;
}

template <typename NAME>
inline const Json::Value &cast(const Json::ValueType target_type,
                               const Json::Value &value,
                               const NAME &name,
                               const bool optional)
{
    return cast(target_type, value, name, optional, nullptr);
}
#endif

template <typename T, typename NAME>
inline void from_vector(Json::Value &root, const T &vec, const NAME &name)
{
    Json::Value array(Json::arrayValue);
    for (auto &e : vec)
        array.append(e.to_json());
    if (array.size())
        root[name] = array;
}

template <typename TITLE>
inline void assert_dict(const Json::Value &obj, const TITLE &title)
{
    if (!obj.isObject())
        throw json_parse(fmt_name_cast(title) + " is not a JSON dictionary");
}

template <typename TITLE>
inline bool is_dict(const Json::Value &obj, const TITLE &title)
{
    if (obj.isNull())
        return false;
    assert_dict(obj, title);
    return true;
}

template <typename NAME>
inline bool exists(const Json::Value &root, const NAME &name)
{
    if (!root.isObject())
        return false;
    return !root[name].isNull();
}

template <typename NAME>
inline bool string_exists(const Json::Value &root, const NAME &name)
{
    if (!root.isObject())
        return false;
    return root[name].isString();
}

template <typename T, typename NAME, typename TITLE>
inline void to_vector(const Json::Value &root, T &vec, const NAME &name, const TITLE &title)
{
    const Json::Value &array = root[name];
    if (array.isNull())
        return;
    if (!array.isArray())
        throw json_parse("array " + fmt_name(name, title) + " is of incorrect type");
    for (unsigned int i = 0; i < array.size(); ++i)
    {
        vec.emplace_back();
        vec.back().from_json(array[i], fmt_name(name, title));
    }
}

template <typename NAME, typename TITLE>
inline std::string get_string(const Json::Value &root,
                              const NAME &name,
                              const TITLE &title)
{
    const Json::Value &value = root[name];
    if (value.isNull())
        throw json_parse("string " + fmt_name(name, title) + " is missing");
    if (!value.isString())
        throw json_parse("string " + fmt_name(name, title) + " is of incorrect type");
    return value.asString();
}

template <typename NAME>
inline std::string get_string(const Json::Value &root, const NAME &name)
{
    return get_string(root, name, nullptr);
}

#ifdef OPENVPN_JSON_INTERNAL
template <typename NAME, typename TITLE>
inline const std::string &get_string_ref(const Json::Value &root,
                                         const NAME &name,
                                         const TITLE &title)
{
    const Json::Value &value = root[name];
    if (value.isNull())
        throw json_parse("string " + fmt_name(name, title) + " is missing");
    if (!value.isString())
        throw json_parse("string " + fmt_name(name, title) + " is of incorrect type");
    return value.asStringRef();
}

template <typename NAME>
inline const std::string &get_string_ref(const Json::Value &root, const NAME &name)
{
    return get_string_ref(root, name, nullptr);
}

template <typename NAME, typename TITLE>
inline const std::string *get_string_ptr(const Json::Value &root,
                                         const NAME &name,
                                         const TITLE &title)
{
    const Json::Value &value = root[name];
    if (value.isNull())
        return nullptr;
    if (!value.isString())
        throw json_parse("string " + fmt_name(name, title) + " is of incorrect type");
    return value.asStringPtr();
}

template <typename NAME>
inline const std::string *get_string_ptr(const Json::Value &root, const NAME &name)
{
    return get_string_ptr(root, name, nullptr);
}
#endif

template <typename NAME, typename TITLE>
inline std::string get_string_optional(const Json::Value &root,
                                       const NAME &name,
                                       const std::string &default_value,
                                       const TITLE &title)
{
    const Json::Value &value = root[name];
    if (value.isNull())
        return default_value;
    if (!value.isString())
        throw json_parse("string " + fmt_name(name, title) + " is of incorrect type");
    return value.asString();
}

template <typename NAME>
inline std::string get_string_optional(const Json::Value &root,
                                       const NAME &name,
                                       const std::string &default_value)
{
    return get_string_optional(root, name, default_value, nullptr);
}

template <typename TITLE>
inline std::string get_string_from_array(const Json::Value &root,
                                         const Json::ArrayIndex index,
                                         const TITLE &title)
{
    const Json::Value &value = root[index];
    if (value.isNull())
        throw json_parse("string " + fmt_name(index, title) + " is missing");
    if (!value.isString())
        throw json_parse("string " + fmt_name(index, title) + " is of incorrect type");
    return value.asString();
}

inline std::string get_string_from_array(const Json::Value &root,
                                         const Json::ArrayIndex index)
{
    return get_string_from_array(root, index, nullptr);
}

template <typename NAME, typename TITLE>
inline int get_int(const Json::Value &root,
                   const NAME &name,
                   const TITLE &title)
{
    const Json::Value &value = root[name];
    if (value.isNull())
        throw json_parse("int " + fmt_name(name, title) + " is missing");
    if (!value.isInt())
        throw json_parse("int " + fmt_name(name, title) + " is of incorrect type");
    return value.asInt();
}

template <typename NAME>
inline int get_int(const Json::Value &root, const NAME &name)
{
    return get_int(root, name, nullptr);
}

template <typename NAME, typename TITLE>
inline int get_int_optional(const Json::Value &root,
                            const NAME &name,
                            const int default_value,
                            const TITLE &title)
{
    const Json::Value &value = root[name];
    if (value.isNull())
        return default_value;
    if (!value.isInt())
        throw json_parse("int " + fmt_name(name, title) + " is of incorrect type");
    return value.asInt();
}

template <typename NAME>
inline int get_int_optional(const Json::Value &root,
                            const NAME &name,
                            const int default_value)
{
    return get_int_optional(root, name, default_value, nullptr);
}

template <typename NAME, typename TITLE>
inline unsigned int get_uint(const Json::Value &root,
                             const NAME &name,
                             const TITLE &title)
{
    const Json::Value &value = root[name];
    if (value.isNull())
        throw json_parse("uint " + fmt_name(name, title) + " is missing");
    if (!value.isUInt())
        throw json_parse("uint " + fmt_name(name, title) + " is of incorrect type");
    return value.asUInt();
}

template <typename NAME>
inline unsigned int get_uint(const Json::Value &root, const NAME &name)
{
    return get_uint(root, name, nullptr);
}

template <typename NAME, typename TITLE>
inline unsigned int get_uint_optional(const Json::Value &root,
                                      const NAME &name,
                                      const unsigned int default_value,
                                      const TITLE &title)
{
    const Json::Value &value = root[name];
    if (value.isNull())
        return default_value;
    if (!value.isUInt())
        throw json_parse("uint " + fmt_name(name, title) + " is of incorrect type");
    return value.asUInt();
}

template <typename NAME>
inline unsigned int get_uint_optional(const Json::Value &root,
                                      const NAME &name,
                                      const unsigned int default_value)
{
    return get_uint_optional(root, name, default_value, nullptr);
}

template <typename NAME, typename TITLE>
inline unsigned int get_uint_via_string(const Json::Value &root,
                                        const NAME &name,
                                        const TITLE &title)
{
    const Json::Value &value = root[name];
    if (value.isNull())
        throw json_parse("uint-via-string " + fmt_name(name, title) + " is missing");
    if (!value.isString())
        throw json_parse("uint-via-string " + fmt_name(name, title) + " is of incorrect type");

    unsigned int ret;
    if (!parse_number(value.asString(), ret))
        throw json_parse("uint-via-string " + fmt_name(name, title) + " failed to parse");
    return ret;
}

template <typename NAME>
inline unsigned int get_uint_via_string(const Json::Value &root,
                                        const NAME &name)
{
    return get_uint_via_string(root, name, nullptr);
}

template <typename NAME, typename TITLE>
inline unsigned int get_uint_optional_via_string(const Json::Value &root,
                                                 const NAME &name,
                                                 const unsigned int default_value,
                                                 const TITLE &title)
{
    const Json::Value &value = root[name];
    if (value.isNull())
        return default_value;
    if (!value.isString())
        throw json_parse("uint-via-string " + fmt_name(name, title) + " is of incorrect type");

    unsigned int ret;
    if (!parse_number(value.asString(), ret))
        throw json_parse("uint-via-string " + fmt_name(name, title) + " failed to parse");
    return ret;
}

template <typename NAME>
inline unsigned int get_uint_optional_via_string(const Json::Value &root,
                                                 const NAME &name,
                                                 const unsigned int default_value)
{
    return get_uint_optional_via_string(root, name, default_value, nullptr);
}

template <typename NAME, typename TITLE>
inline std::uint64_t get_uint64(const Json::Value &root,
                                const NAME &name,
                                const TITLE &title)
{
    const Json::Value &value = root[name];
    if (value.isNull())
        throw json_parse("uint64 " + fmt_name(name, title) + " is missing");
    if (!value.isUInt64())
        throw json_parse("uint64 " + fmt_name(name, title) + " is of incorrect type");
    return value.asUInt64();
}

template <typename NAME>
inline std::uint64_t get_uint64(const Json::Value &root, const NAME &name)
{
    return get_uint64(root, name, nullptr);
}

template <typename NAME, typename TITLE>
inline std::uint64_t get_uint64_optional(const Json::Value &root,
                                         const NAME &name,
                                         const std::uint64_t default_value,
                                         const TITLE &title)
{
    const Json::Value &value = root[name];
    if (value.isNull())
        return default_value;
    if (!value.isUInt64())
        throw json_parse("uint64 " + fmt_name(name, title) + " is of incorrect type");
    return value.asUInt64();
}

template <typename NAME, typename TITLE>
inline std::int64_t get_int64_optional(const Json::Value &root,
                                       const NAME &name,
                                       const std::uint64_t default_value,
                                       const TITLE &title)
{
    const Json::Value &value = root[name];
    if (value.isNull())
        return default_value;
    if (!value.isInt64())
        throw json_parse("int64 " + fmt_name(name, title) + " is of incorrect type");
    return value.asInt64();
}

template <typename NAME>
inline std::uint64_t get_uint64_optional(const Json::Value &root,
                                         const NAME &name,
                                         const std::uint64_t default_value)
{
    return get_uint64_optional(root, name, default_value, nullptr);
}

/*
 * The get_integer_optional function are used to select the right
 * method based on the default_value parameter
 */

template <typename NAME, typename TITLE>
std::uint64_t get_integer_optional(const Json::Value &root,
                                   const NAME &name,
                                   const std::uint64_t default_value,
                                   const TITLE &title)
{
    return get_uint64_optional(root, name, default_value, title);
}

template <typename NAME, typename TITLE>
std::int64_t get_integer_optional(const Json::Value &root,
                                  const NAME &name,
                                  const std::int64_t default_value,
                                  const TITLE &title)
{
    return get_int64_optional(root, name, default_value, title);
}

template <typename NAME, typename TITLE>
inline unsigned int get_integer_optional(const Json::Value &root,
                                         const NAME &name,
                                         const unsigned int default_value,
                                         const TITLE &title)
{
    return get_uint_optional(root, name, default_value, title);
}

template <typename NAME, typename TITLE>
inline int get_integer_optional(const Json::Value &root,
                                const NAME &name,
                                const int default_value,
                                const TITLE &title)
{
    return get_int_optional(root, name, default_value, title);
}

template <typename NAME, typename TITLE>
inline std::uint64_t get_uint64_via_string(const Json::Value &root,
                                           const NAME &name,
                                           const TITLE &title)
{
    const Json::Value &value = root[name];
    if (value.isNull())
        throw json_parse("uint64-via-string " + fmt_name(name, title) + " is missing");
    if (!value.isString())
        throw json_parse("uint64-via-string " + fmt_name(name, title) + " is of incorrect type");

    std::uint64_t ret;
    if (!parse_number(value.asString(), ret))
        throw json_parse("uint64-via-string " + fmt_name(name, title) + " failed to parse");
    return ret;
}

template <typename NAME>
inline std::uint64_t get_uint64_via_string(const Json::Value &root,
                                           const NAME &name)
{
    return get_uint64_via_string(root, name, nullptr);
}

template <typename NAME, typename TITLE>
inline std::uint64_t get_uint64_optional_via_string(const Json::Value &root,
                                                    const NAME &name,
                                                    const std::uint64_t default_value,
                                                    const TITLE &title)
{
    const Json::Value &value = root[name];
    if (value.isNull())
        return default_value;
    if (!value.isString())
        throw json_parse("uint64-via-string " + fmt_name(name, title) + " is of incorrect type");

    std::uint64_t ret;
    if (!parse_number(value.asString(), ret))
        throw json_parse("uint64-via-string " + fmt_name(name, title) + " failed to parse");
    return ret;
}

template <typename NAME>
inline std::uint64_t get_uint64_optional_via_string(const Json::Value &root,
                                                    const NAME &name,
                                                    const std::uint64_t default_value)
{
    return get_uint64_optional_via_string(root, name, default_value, nullptr);
}
template <typename NAME, typename TITLE>
inline bool get_bool(const Json::Value &root,
                     const NAME &name,
                     const TITLE &title)
{
    const Json::Value &value = root[name];
    if (value.isNull())
        throw json_parse("bool " + fmt_name(name, title) + " is missing");
    if (!value.isBool())
        throw json_parse("bool " + fmt_name(name, title) + " is of incorrect type");
    return value.asBool();
}

template <typename NAME>
inline bool get_bool(const Json::Value &root, const NAME &name)
{
    return get_bool(root, name, nullptr);
}

template <typename NAME>
inline bool get_bool_optional(const Json::Value &root,
                              const NAME &name,
                              const bool default_value = false)
{
    const Json::Value &jv = root[name];
    if (jv.isConvertibleTo(Json::booleanValue))
        return jv.asBool();
    else
        return default_value;
}

template <typename NAME>
inline int get_bool_tristate(const Json::Value &root,
                             const NAME &name)
{
    const Json::Value &jv = root[name];
    if (jv.isConvertibleTo(Json::booleanValue))
        return jv.asBool() ? 1 : 0;
    else
        return -1;
}

template <typename NAME, typename TITLE>
inline const Json::Value &get_dict(const Json::Value &root,
                                   const NAME &name,
                                   const bool optional,
                                   const TITLE &title)
{
    const Json::Value &value = root[name];
    if (value.isNull())
    {
        if (optional)
            return value;
        throw json_parse("dictionary " + fmt_name(name, title) + " is missing");
    }
    if (!value.isObject())
        throw json_parse("dictionary " + fmt_name(name, title) + " is of incorrect type");
    return value;
}

template <typename NAME>
inline const Json::Value &get_dict(const Json::Value &root,
                                   const NAME &name,
                                   const bool optional)
{
    return get_dict(root, name, optional, nullptr);
}

template <typename TITLE>
inline const Json::Value &cast_dict(const Json::Value &value,
                                    const bool optional,
                                    const TITLE &title)
{
    if (value.isNull())
    {
        if (optional)
            return value;
        throw json_parse("dictionary cast " + fmt_name_cast(title) + " is null");
    }
    if (!value.isObject())
        throw json_parse("dictionary cast " + fmt_name_cast(title) + " is of incorrect type");
    return value;
}

inline const Json::Value &cast_dict(const Json::Value &value,
                                    const bool optional)
{
    return cast_dict(value, optional, nullptr);
}

template <typename NAME, typename TITLE>
inline const Json::Value &get_array(const Json::Value &root,
                                    const NAME &name,
                                    const bool optional,
                                    const TITLE &title)
{
    const Json::Value &value = root[name];
    if (value.isNull())
    {
        if (optional)
            return value;
        throw json_parse("array " + fmt_name(name, title) + " is missing");
    }
    if (!value.isArray())
        throw json_parse("array " + fmt_name(name, title) + " is of incorrect type");
    return value;
}

template <typename NAME>
inline const Json::Value &get_array(const Json::Value &root,
                                    const NAME &name,
                                    const bool optional)
{
    return get_array(root, name, optional, nullptr);
}

template <typename TITLE>
inline const Json::Value &cast_array(const Json::Value &value,
                                     const bool optional,
                                     const TITLE &title)
{
    if (value.isNull())
    {
        if (optional)
            return value;
        throw json_parse("array cast " + fmt_name_cast(title) + " is null");
    }
    if (!value.isArray())
        throw json_parse("array cast " + fmt_name_cast(title) + " is of incorrect type");
    return value;
}

inline const Json::Value &cast_array(const Json::Value &value,
                                     const bool optional)
{
    return cast_array(value, optional, nullptr);
}

template <typename NAME, typename TITLE>
inline void to_string(const Json::Value &root,
                      std::string &dest,
                      const NAME &name,
                      const TITLE &title)
{
    dest = get_string(root, name, title);
}

template <typename NAME, typename TITLE>
inline void to_string_optional(const Json::Value &root,
                               std::string &dest,
                               const NAME &name,
                               const std::string &default_value,
                               const TITLE &title)
{
    dest = get_string_optional(root, name, default_value, title);
}

template <typename NAME, typename TITLE>
inline void to_int(const Json::Value &root,
                   int &dest,
                   const NAME &name,
                   const TITLE &title)
{
    dest = get_int(root, name, title);
}

template <typename NAME, typename TITLE>
inline void to_uint(const Json::Value &root,
                    unsigned int &dest,
                    const NAME &name,
                    const TITLE &title)
{
    dest = get_uint(root, name, title);
}

template <typename NAME, typename TITLE>
inline void to_uint_optional(const Json::Value &root,
                             unsigned int &dest,
                             const NAME &name,
                             const unsigned int default_value,
                             const TITLE &title)
{
    dest = get_uint_optional(root, name, default_value, title);
}

template <typename NAME, typename TITLE>
inline void to_uint64(const Json::Value &root,
                      std::uint64_t &dest,
                      const NAME &name,
                      const TITLE &title)
{
    dest = get_uint64(root, name, title);
}

template <typename NAME, typename TITLE>
inline void to_bool(const Json::Value &root,
                    bool &dest,
                    const NAME &name,
                    const TITLE &title)
{
    dest = get_bool(root, name, title);
}

inline void format_compact(const Json::Value &root, Buffer &buf)
{
#ifdef OPENVPN_JSON
    root.toCompactString(buf);
#else
    Json::StreamWriterBuilder json_builder;
    json_builder.settings_["indentation"] = "";
    BufferStreamOut os(buf);
    std::unique_ptr<Json::StreamWriter> sw(json_builder.newStreamWriter());
    sw->write(root, &os);
#endif
}

inline std::string format_compact(const Json::Value &root,
                                  const size_t size_hint = 256)
{
    BufferPtr bp = new BufferAllocated(size_hint, BufferAllocated::GROW);
    format_compact(root, *bp);
    return buf_to_string(*bp);
}

inline void format(const Json::Value &root, Buffer &buf)
{
#ifdef OPENVPN_JSON
    root.toStyledString(buf);
#else
    Json::StreamWriterBuilder json_builder;
    json_builder.settings_["indentation"] = "  ";
    BufferStreamOut os(buf);
    std::unique_ptr<Json::StreamWriter> sw(json_builder.newStreamWriter());
    sw->write(root, &os);
#endif
}

inline std::string format(const Json::Value &root)
{
    return root.toStyledString();
}

inline std::string error(const Json::Value &root)
{
    const Json::Value &je = root["error"];
    if (je.isString())
        return je.asString();
    else
        return std::string();
}

// Guarantee that json object jr is a dictionary.
// Do this by encapsulating jr in a dictionary
// { "result": jr } if it is not already one.
inline Json::Value dict_result(Json::Value jr)
{
    if (jr.isObject())
        return jr;
    else
    {
        Json::Value jret(Json::objectValue);
        jret["result"] = std::move(jr);
        return jret;
    }
}
} // namespace json
} // namespace openvpn
