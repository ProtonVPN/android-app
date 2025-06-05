#ifndef TEST_GENERATORS_HPP
#define TEST_GENERATORS_HPP
#ifdef __GNUC__
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wconversion" // turn off warning for rapidcheck
#endif
#include <rapidcheck/gtest.h>
#ifdef __GNUC__
#pragma GCC diagnostic pop
#endif

#include <algorithm>
#include <utility>
#include <string>
#include <tuple>
#include <array>
#include <string_view>
#include <bitset>
#include <sstream>
#include <vector>

#include "openvpn/addr/ip.hpp"
#include "openvpn/tun/builder/capture.hpp"

namespace rc {

/**
 * @brief Generates an array of booleans that contains at least one \c false.
 * @details This template function generates an array of booleans of size \p N
 *          such that at least one of the elements in the array is \c false.
 * @tparam N The size of the array to be generated.
 * @return A generator that produces array where at least one boolean value is \c false.
 *
 * Example usage:
 * @code
 * auto falseBoolean = *atLeastOneFalse<5>();
 * @endcode
 */
template <size_t N>
auto atLeastOneFalse() -> Gen<std::array<bool, N>>
{
    static_assert(N > 0, "N must be greater than 0");

    return gen::suchThat(
        gen::container<std::array<bool, N>>(gen::arbitrary<bool>()),
        [](const auto &booleans)
        {
            return std::any_of(booleans.begin(), booleans.end(), [](const bool b)
                               { return !b; });
        });
}

/**
 * @brief Generates an array of validity flags for component testing
 *
 * @tparam N Number of validity flags to generate
 * @param all_valid If @c true, generates all flags as valid (@c true).
 *                  If @c false, generates flags with at least one invalid (@c false) value.
 *
 * @return Generator producing an array of @p N boolean flags.
 *         When all_valid is @c true : returns array with all elements set to @c true
 *         When all_valid is @c false : returns array with at least one element set to @c false
 *
 * Example usage:
 * @code
 *   // Generate flags for testing URL components (all valid)
 *   auto [scheme_is_valid, authority_is_valid] = *generateValidityFlags<2>(true);
 *
 *   // Generate flags with at least one invalid component
 *   auto [scheme_is_valid, authority_is_valid] = *generateValidityFlags<2>(false);
 * @endcode
 */
template <size_t N>
auto generateValidityFlags(const bool all_valid = true) -> Gen<std::array<bool, N>>
{
    if (all_valid)
    {
        return gen::container<std::array<bool, N>>(gen::just(true));
    }
    return atLeastOneFalse<N>();
}

/**
 * @brief Generates a valid or invalid IPv4 octet value.
 * @details This function generates a value that represents an IPv4 octet. If \p valid is \c true,
 * it generates a value within the valid range of 0 to 255. If \p valid is \c false, it generates
 * values outside this range to represent an invalid octet.
 * @param valid A boolean flag indicating whether to generate a valid or invalid octet value. Default is \c true.
 * @return A generator producing either valid IPv4 octet value (0-255) or invalid value.
 *
 * Example usage:
 * @code
 * auto octet = *IPv4Octet();
 * @endcode
 */
inline auto IPv4Octet(const bool valid = true) -> Gen<int>
{
    static constexpr int min_ipv4_octet = 0;
    static constexpr int max_ipv4_octet = 255;

    if (valid)
    {
        return gen::inRange(min_ipv4_octet, max_ipv4_octet + 1);
    }
    return gen::suchThat(gen::arbitrary<int>(), [](const auto &i)
                         { return i < min_ipv4_octet || i > max_ipv4_octet; });
}

/**
 * @brief Generates a random IPv4 address.
 * @details This function generates a random IPv4 address. The validity of the octets
 * can be controlled by the \p valid parameter. If \p valid is \c true, all four
 * octets will be valid. Otherwise, at least one octet will be invalid.
 * The resulting IPv4 address is formatted as \c X.X.X.X where \c X is a number between
 * 0 and 255 (or an invalid value if \p valid is \c false).
 * @param valid A boolean flag indicating whether the generated address should be valid.
 *              Defaults to \c true.
 * @return A generator producing either valid on invalid IPv4 address.
 *
 * Example usage:
 * @code
 * auto address = *IPv4Address();
 * @endcode
 */
inline auto IPv4Address(const bool valid = true) -> Gen<std::string>
{
    static constexpr int octets_number = 4;
    static constexpr std::array<bool, octets_number> all_true = {true, true, true, true};
    const auto octet_validity = valid ? all_true : *atLeastOneFalse<octets_number>().as("first,second,third,fourth octet valid");

    return gen::map(
        gen::tuple(IPv4Octet(octet_validity[0]),
                   IPv4Octet(octet_validity[1]),
                   IPv4Octet(octet_validity[2]),
                   IPv4Octet(octet_validity[3])),
        [](const auto &octets)
        {
            return std::to_string(std::get<0>(octets)) + "." + std::to_string(std::get<1>(octets)) + "."
                   + std::to_string(std::get<2>(octets)) + "." + std::to_string(std::get<3>(octets));
        });
}

/**
 * @brief Generates a random printable ASCII character code.
 * @details This function generates an integer value representing printable ASCII character,
 * ranging from 32 (space) to 126 (tilde) without 37 (percent sign)
 * @return A generator producing printable ASCII character code.
 * @warning It does not generate percent sign (%)
 *
 * Example usage:
 * @code
 * auto code = *asciiPrintableCode();
 * @endcode
 */
inline auto asciiPrintableCode() -> Gen<int>
{
    static constexpr int ASCII_range_start_code = 32;  // ASCII code for space character
    static constexpr int ASCII_range_end_code = 127;   // ASCII code for DEL (not included)
    static constexpr int ASCII_percent_sign_code = 37; // ASCII code for percent sign

    // Due to IPv6 Scoped Address Architecture (RFC 4007) anything after '%' is not part of IPv6 address but zone_id
    // Therefore generating '% breaks assumption on validity of generated IPv6 addresses
    return gen::distinctFrom(gen::inRange(ASCII_range_start_code, ASCII_range_end_code), ASCII_percent_sign_code);
}

/**
 * @brief Generates a valid or invalid hexadecimal character.
 * @details This function generates a single hexadecimal character. If \p valid is \c true,
 *          it generates a value within the valid ranges of \c 0-9, \c A-F, and \c a-f.
 *          If \p valid is \c false, it generates a character outside of these ranges to represent
 *          an invalid hexadecimal character.
 * @param valid A boolean flag indicating whether to generate a valid or invalid hexadecimal character.
 *              Default is \c true.
 * @return A generator producing either valid or invalid hexadecimal character.
 *
 * Example usage:
 * @code
 * auto hex_generator = *hexChar();
 * @endcode
 */
inline auto hexChar(const bool valid = true) -> Gen<std::string>
{
    if (valid)
    {
        const auto alphapositives = gen::elementOf(std::string("abcdefABCDEF123456789"));

        static constexpr int probability_weight_of_0 = 23;
        static constexpr int probability_weight_of_alphapositives = 1;

        // "0" should be generated <probability_weight_of_0> times more often
        return gen::map(gen::weightedOneOf<char>({{probability_weight_of_0, gen::just<char>('0')},
                                                  {probability_weight_of_alphapositives, alphapositives}}),
                        [](const auto &c)
                        {
                            return std::string{static_cast<char>(c)};
                        });
    }

    // Generate invalid hexadecimal characters
    return gen::map(gen::suchThat(asciiPrintableCode(), [](const auto &c)
                                  { return isxdigit(c) == 0; }),
                    [](const auto &c)
                    {
                        return std::string{static_cast<char>(c)};
                    });
}

/**
 * @brief Generates a hextet value of an IPv6 address.
 * @details This function generates a hextet (4 characters) value of an IPv6 address,
 * which may consist of valid or invalid hexadecimal characters based on the \p valid parameter.
 * @param valid A boolean indicating whether the generated hextet should only contain valid hexadecimal characters.
 *              If set to \c true, all characters will be valid. If set to \c false, at least one character will be invalid.
 *              Default is \c true.
 * @return A generator producing either valid or invalid hextet value.
 *
 * Example usage:
 * @code
 * auto hextet = *IPv6HextetValue();
 * @endcode
 */
inline auto IPv6HextetValue(const bool valid = true) -> Gen<std::string>
{
    static constexpr int hexchars_number = 4;
    static constexpr std::array<bool, hexchars_number> all_true = {true, true, true, true};
    const auto hexchar_validity = valid ? all_true : *atLeastOneFalse<hexchars_number>().as("first,second,third,fourth hexchar in hextet valid");

    return gen::map(
        gen::tuple(hexChar(hexchar_validity[0]),
                   hexChar(hexchar_validity[1]),
                   hexChar(hexchar_validity[2]),
                   hexChar(hexchar_validity[3])),
        [](const auto &hexchars)
        {
            const auto &[first_hexchar, second_hexchar, third_hexchar, fourth_hexchar] = hexchars;
            return first_hexchar + second_hexchar + third_hexchar + fourth_hexchar;
        });
}

/**
 * @brief Removes leading zeros from a hextet (IPv6 segment).
 * @details This function trims all leading zeros from a given hextet string.
 *          If the hextet only contains zeros (or is empty), it returns "0".
 * @param hextet The input string representing a hextet (e.g., "0001", "0ABC").
 * @return A string without leading zeros.
 */
inline std::string removeLeadingZerosFromHextet(const std::string &hextet)
{
    const auto first_nonzero = hextet.find_first_not_of('0');
    return first_nonzero == std::string::npos ? "0" : hextet.substr(first_nonzero);
}

/**
 * @brief Removes leading zeros from a vector of hextets.
 * @details This function modifies a vector of IPv6 hextets by removing leading zeros
 *          from each hextet using the `removeLeadingZerosFromHextet` transformation.
 *          The operation is performed in-place.
 * @param hextets A reference to a vector of strings representing IPv6 hextets.
 *                Each hextet will be stripped of its leading zeros.
 */
inline void removeLeadingZerosFromHextets(std::vector<std::string> &hextets)
{
    std::transform(hextets.begin(), hextets.end(), hextets.begin(), removeLeadingZerosFromHextet);
}

/**
 * @brief Replaces the longest sequence of consecutive "0" strings in a vector with "::".
 * @details This function finds the longest contiguous sequence of "0" strings
 *          in the provided `hextets` vector, replaces it with a single "::",
 *          and removes the other "0" strings in the sequence.
 *          If there are multiple sequences of the same length, it acts on the first one found.
 * @param hextets A vector of strings representing the hextets (subdivisions) of an IPv6 address.
 */
inline void replaceSequenceOfZerosWithDoubleColon(std::vector<std::string> &hextets)
{
    for (auto longest_zero_sequence = hextets.size(); longest_zero_sequence > 1; --longest_zero_sequence)
    {
        auto position = std::search_n(hextets.begin(), hextets.end(), longest_zero_sequence, std::string{"0"});
        if (position != hextets.end())
        {
            const auto it = hextets.insert(position, "::");
            hextets.erase(it + 1, std::next(it + 1, longest_zero_sequence));
            return;
        }
    }
}

/**
 * @brief Converts a vector of hextets to an IPv6 address string with colons.
 * @details This function takes a vector of strings representing hextets (parts of an IPv6 address)
 *          and constructs a colon-separated IPv6 address. The function ensures that colons are
 *          correctly placed between the hextets, and it avoids adding redundant colons around "::"
 *          which represents a compressed sequence of zeroes in an IPv6 address.
 *
 * @param hextets A vector of strings, where each string represents a hextet or "::".
 * @return A string representation of the IPv6 address with colons separating the hextets.
 */
inline std::string stringifyHextetsToAddressWithColons(const std::vector<std::string> &hextets)
{
    std::string result;
    for (size_t i = 0; i < hextets.size(); ++i)
    {
        if (i > 0 && hextets[i] != "::" && hextets[i - 1] != "::")
        {
            result += ":";
        }
        result += hextets[i];
    }
    return result;
}

/**
 * @brief Compress an IPv6 address by simplifying its representation.
 * @details This function takes a list of hextets (hexadecimal segments of an IPv6 address),
 *          removes leading zeros from each hextet, replaces the largest contiguous sequence
 *          of zeroed hextets with a double colon (::), and converts the simplified hextets
 *          back into a string representation of the IPv6 address.
 * @param hextets A vector of strings, where each string represents one hextet of an IPv6 address.
 * @return A string containing the compressed IPv6 address.
 */
inline std::string compressIPv6Address(std::vector<std::string> hextets)
{
    removeLeadingZerosFromHextets(hextets);
    replaceSequenceOfZerosWithDoubleColon(hextets);
    return stringifyHextetsToAddressWithColons(hextets);
}

/**
 * @brief Generates a random IPv6 address.
 * @details This function generates a random IPv6 address. The validity of the hextets
 * can be controlled by the \p valid parameter. If \p valid is \c true, all eight
 * hextets will be valid. Otherwise, at least one hextet will be invalid.
 * The resulting IPv6 address is formatted as \c X:X:X:X:X:X:X:X where \c X is a hextet (4 hex chars)
 * within the valid ranges of \c 0-9, \c A-F, and \c a-f.
 * @details This function generates either a valid or an invalid IPv6 address.
 * @param valid A boolean flag indicating whether the generated IPv6 address should be valid.
 *              Defaults to \c true.
 * @return A generator that produces a valid or invalid IPv6 address.
 *
 * Example usage:
 * @code
 * auto address = *IPv6Address();
 * @endcode
 */
inline auto IPv6Address(const bool valid = true) -> Gen<std::string>
{
    static constexpr int number_of_hextets = 8;
    static constexpr std::array<bool, number_of_hextets> all_true = {true, true, true, true, true, true, true, true};
    const auto hextet_validity = valid ? all_true : *atLeastOneFalse<number_of_hextets>().as("first,second,third,fourth,fifth,sixth,seventh,eighth hextet valid");

    auto convert_hextets_to_compressed_address = [](std::string first_hextet,
                                                    std::string second_hextet,
                                                    std::string third_hextet,
                                                    std::string fourth_hextet,
                                                    std::string fifth_hextet,
                                                    std::string sixth_hextet,
                                                    std::string seventh_hextet,
                                                    std::string eighth_hextet)
    {
        return compressIPv6Address({std::move(first_hextet),
                                    std::move(second_hextet),
                                    std::move(third_hextet),
                                    std::move(fourth_hextet),
                                    std::move(fifth_hextet),
                                    std::move(sixth_hextet),
                                    std::move(seventh_hextet),
                                    std::move(eighth_hextet)});
    };

    if (valid)
    {
        return gen::apply(convert_hextets_to_compressed_address,
                          IPv6HextetValue(hextet_validity[0]),
                          IPv6HextetValue(hextet_validity[1]),
                          IPv6HextetValue(hextet_validity[2]),
                          IPv6HextetValue(hextet_validity[3]),
                          IPv6HextetValue(hextet_validity[4]),
                          IPv6HextetValue(hextet_validity[5]),
                          IPv6HextetValue(hextet_validity[6]),
                          IPv6HextetValue(hextet_validity[7]));
    }
    return gen::map(gen::tuple(
                        IPv6HextetValue(hextet_validity[0]),
                        IPv6HextetValue(hextet_validity[1]),
                        IPv6HextetValue(hextet_validity[2]),
                        IPv6HextetValue(hextet_validity[3]),
                        IPv6HextetValue(hextet_validity[4]),
                        IPv6HextetValue(hextet_validity[5]),
                        IPv6HextetValue(hextet_validity[6]),
                        IPv6HextetValue(hextet_validity[7])),
                    [](const auto &hextets)
                    {
                        const auto [first_hextet, second_hextet, third_hextet, fourth_hextet, fifth_hextet, sixth_hextet, seventh_hextet, eighth_hextet] = hextets;
                        return first_hextet + ":" + second_hextet + ":" + third_hextet + ":" + fourth_hextet + ":" + fifth_hextet + ":" + sixth_hextet + ":" + seventh_hextet + ":" + eighth_hextet;
                    });
}

using RedirectGatewayFlagsValues = openvpn::RedirectGatewayFlags::Flags;

/**
 * @brief Template specialization for generating arbitrary RedirectGatewayFlagsValues.
 * @details This struct specializes the Arbitrary template for the RedirectGatewayFlagsValues enum.
 * It generates a set of flags by selecting a subset of bit positions to set.
 */
template <>
struct Arbitrary<RedirectGatewayFlagsValues>
{
    /**
     * @brief Generates an arbitrary RedirectGatewayFlagsValues.
     * @details This function generates an arbitrary value for RedirectGatewayFlagsValues
     * by selecting a subset of bit positions (from 0 to number_of_flags) and setting
     * the corresponding bits in the result.
     * @return A generator that produces RedirectGatewayFlagsValues with random sets of flags.
     */
    static Gen<RedirectGatewayFlagsValues> arbitrary()
    {
        static constexpr int number_of_flags = 9;
        return gen::map(
            gen::container<std::vector<int>>(gen::inRange(0, number_of_flags + 1)),
            [](const auto &bit_positions)
            {
                auto flags = static_cast<RedirectGatewayFlagsValues>(0);
                for (const auto &pos : bit_positions)
                {
                    flags = static_cast<RedirectGatewayFlagsValues>(flags | (1 << pos));
                }
                return flags;
            });
    }
};

/**
 * @brief Specialization of Arbitrary for the RouteBase type.
 * @details This struct specializes the Arbitrary template for
 *          the `openvpn::TunBuilderCapture::RouteBase` type. It provides
 *          an `arbitrary` function that produces a generator for this type.
 */
template <>
struct Arbitrary<openvpn::TunBuilderCapture::RouteBase>
{
    /**
     * @brief Generates a value of type RouteBase.
     * @details This function uses `gen::just` to generate a default
     *          instance of `openvpn::TunBuilderCapture::RouteBase`.
     * @return A generator (`Gen`) that produces a `RouteBase` object.
     */
    static auto arbitrary() -> Gen<openvpn::TunBuilderCapture::RouteBase>
    {
        return gen::just(openvpn::TunBuilderCapture::RouteBase{});
    }
};

/**
 * @brief Specialization of the Arbitrary template for TunBuilderCapture::Route.
 * @details Provides a mechanism to generate arbitrary instances of
 *          TunBuilderCapture::Route using a predefined generator.
 *          This is useful for testing purposes.
 * @return A generator that always produces a default instance of
 *         openvpn::TunBuilderCapture::Route.
 */
template <>
struct Arbitrary<openvpn::TunBuilderCapture::Route>
{
    /**
     * @brief Generates an arbitrary instance of TunBuilderCapture::Route.
     * @details This method always returns a generator that produces a
     *          default-constructed TunBuilderCapture::Route.
     * @return A generator of type Gen<openvpn::TunBuilderCapture::Route>.
     */
    static auto arbitrary() -> Gen<openvpn::TunBuilderCapture::Route>
    {
        return gen::just(openvpn::TunBuilderCapture::Route{});
    }
};

/**
 * @brief Specialization of the Arbitrary struct for RouteAddress.
 * @details Provides a generation function for openvpn::TunBuilderCapture::RouteAddress
 *          objects used in property-based testing.
 */
template <>
struct Arbitrary<openvpn::TunBuilderCapture::RouteAddress>
{
    /**
     * @brief Generates an arbitrary RouteAddress instance.
     * @details Uses a generator to produce a default-initialized
     *          openvpn::TunBuilderCapture::RouteAddress object.
     * @return A generator that returns a default RouteAddress instance.
     */
    static auto arbitrary() -> Gen<openvpn::TunBuilderCapture::RouteAddress>
    {
        return gen::just(openvpn::TunBuilderCapture::RouteAddress{});
    }
};

/**
 * @brief Alias representing a route-based variant type.
 * @details This alias holds one of three possible route-related types:
 * `openvpn::TunBuilderCapture::Route`, `openvpn::TunBuilderCapture::RouteAddress`,
 * or `openvpn::TunBuilderCapture::RouteBase`.
 */
using RouteBased = std::variant<openvpn::TunBuilderCapture::Route,
                                openvpn::TunBuilderCapture::RouteAddress,
                                openvpn::TunBuilderCapture::RouteBase>;

/**
 * @brief Specialization of Arbitrary for creating arbitrary std::variant values.
 * @details This struct provides a static method to generate an arbitrary instance of a `std::variant`
 * containing one of the specified types. It leverages the `gen::oneOf` function to select one of the
 * provided types and creates a `std::variant` containing that type.
 *
 * @tparam T The first type in the `std::variant`.
 * @tparam Ts The remaining types in the `std::variant`.
 */
template <typename T, typename... Ts>
struct Arbitrary<std::variant<T, Ts...>>
{
    /**
     * @brief Generates an arbitrary `std::variant` containing one of the specified types.
     * @details This function selects one of the types in the `std::variant` and wraps its generated value
     * in the `std::variant` type using `gen::cast`.
     *
     * @return A generator object (`Gen<std::variant<T, Ts...>>`) that produces `std::variant` instances
     * containing one of the specified types.
     */
    static auto arbitrary() -> Gen<std::variant<T, Ts...>>
    {
        return gen::oneOf(
            gen::cast<std::variant<T, Ts...>>(gen::arbitrary<T>()),
            gen::cast<std::variant<T, Ts...>>(gen::arbitrary<Ts>())...);
    }
};

static const std::string ALPHA_CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
static const std::string DIGITS = "1234567890";

/**
 * @brief Generates alphabetic or non-alphabetic characters.
 * @details Creates a generator that produces either valid alphabetic characters (A-Z, a-z)
 *          or non-alphabetic characters, depending on the input parameter. When generating
 *          valid alphabetic characters, it selects from a predefined set of characters.
 *          For invalid characters, it uses a character generator with a predicate that
 *          ensures the character is not alphabetic.
 *
 * @param valid If @c true (default), generates valid alphabetic characters.
 *              If @c false, generates non-alphabetic characters.
 *
 * @return Gen<char> A generator that produces characters according to the specified criteria:
 *         - When valid=true: returns a generator for alphabetic characters (A-Z, a-z)
 *         - When valid=false: returns a generator for non-alphabetic characters
 */
inline auto alpha(const bool valid = true) -> Gen<char>
{
    if (valid)
    {
        return gen::elementOf(std::string_view{ALPHA_CHARACTERS});
    }
    return gen::suchThat(gen::character<char>(),
                         [](const char character)
                         { return !std::isalpha(character); });
}

/**
 * @brief Generates characters based on an allowed character set.
 * @details Creates a generator that either produces characters from a specified set of allowed characters,
 *          or characters that are specifically not in that set, depending on the @c valid parameter.
 *          When @c valid is @c true, it generates only characters from the allowed set.
 *          When @c valid is @c false, it generates only characters that are not in the allowed set.
 *
 * @param allowed_chars A string_view containing the set of allowed characters
 * @param valid        Boolean flag to determine whether to generate characters from the allowed set (@c true)
 *                     or characters not in the allowed set (@c false). Defaults to @c true.
 *
 * @return Gen<char>   A character generator that produces either valid or invalid characters
 *                     based on the @p allowed_chars set
 */
inline auto from_allowed_chars(const std::string_view &allowed_chars, const bool valid = true) -> Gen<char>
{
    if (valid)
    {
        return gen::elementOf(allowed_chars);
    }
    return gen::suchThat(gen::character<char>(),
                         [allowed_chars](const char character)
                         { return allowed_chars.find(character) == std::string::npos; });
}

/**
 * @brief Generates strings based on allowed characters.
 * @details Creates a generator that produces strings either containing only allowed characters
 *          or containing at least one character not in the allowed set.
 *          When @c valid is @c true, it generates strings using only allowed characters.
 *          When @c valid is @c false, it generates strings that contain at least one
 *          character not present in @p allowed_chars.
 *
 * @param allowed_chars A string_view containing the set of allowed characters
 * @param valid Boolean flag to control generation mode:
 *              - @c true : generate strings using only allowed characters
 *              - @c false : generate strings containing at least one invalid character
 *              (defaults to @c true)
 *
 * @return A generator that produces @c std::string values according to the
 *         specified constraints
 */
inline auto string_from_allowed_chars(const std::string_view &allowed_chars, const bool valid = true) -> Gen<std::string>
{
    if (valid)
    {
        return gen::container<std::string>(from_allowed_chars(allowed_chars));
    }
    return gen::suchThat(gen::string<std::string>(), [allowed_chars](const auto &string)
                         { return std::any_of(string.begin(), string.end(), [allowed_chars](const auto &character)
                                              { return allowed_chars.find(character) == std::string::npos; }); });
}

/**
 * @brief Generates a port number value.
 * @details Creates a generator that produces either valid or invalid port numbers.
 * If @c valid is @c true, generates numbers in the valid port range [0, 65535].
 * If @c valid is @c false, generates numbers outside the valid port range.
 *
 * @param valid Boolean flag to determine if valid (@c true) or invalid (@c false) port numbers should be generated.
 *              Defaults to @c true.
 * @return Gen<int> A generator that produces port numbers according to the @p valid parameter.
 */
inline auto port(const bool valid = true) -> Gen<int>
{
    static constexpr int port_upper_bound = 65535;
    static constexpr int port_lower_bound = 0;
    if (valid)
        return gen::inRange(port_lower_bound, port_upper_bound + 1);
    return gen::suchThat<int>(
        [](const auto port_number)
        { return port_number < port_lower_bound || port_number > port_upper_bound; });
}

/**
 * @brief Calculates the valid IP prefix range for a given IP address.
 * @details This function takes an IPv4 address as a string and calculates the minimum
 *          and maximum valid prefix lengths. It converts the address to its integer
 *          representation and determines the smallest prefix that can correctly
 *          represent the network containing this address based on the position of
 *          the least significant '1' bit.
 *
 *          For example:
 *          - For 192.168.1.0, the range would be {24, 32}
 *          - For 10.0.0.0, the range would be {7, 32}
 *
 * @param ipAddress An IPv4 address in dotted decimal notation (e.g., "192.168.1.0")
 * @return A tuple containing:
 *         - First element: The minimum valid prefix length (between 0 and 32)
 *         - Second element: The maximum valid prefix length (always 32)
 */
inline auto calculateIPPrefixRange(const std::string &ipAddress) -> std::tuple<int, int>
{
    std::array<unsigned int, 4> addressParts{};
    std::istringstream addressStream(ipAddress);
    std::string part;
    size_t partIndex = 0;

    while (std::getline(addressStream, part, '.'))
    {
        addressParts[partIndex++] = std::stoi(part);
    }

    const uint32_t addressInteger = (addressParts[0] << 24) | (addressParts[1] << 16) | (addressParts[2] << 8) | addressParts[3];

    auto minimumPrefix = 32;
    for (auto bitPosition = 0; bitPosition < 32; ++bitPosition)
    {
        if (addressInteger & (1U << bitPosition))
        {
            minimumPrefix = 32 - bitPosition;
            break;
        }
    }

    return {minimumPrefix, openvpn::IPv4::Addr::SIZE};
}
} // namespace rc
#endif // TEST_GENERATORS_HPP
