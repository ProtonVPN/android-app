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
//

/**
 * This class implements the parsing and generating of app custom control
 * channel messages
 */

#pragma once

#include <vector>
#include <string>
#include <utility>
#include <openvpn/common/string.hpp>
#include <openvpn/common/unicode.hpp>
#include <openvpn/common/base64.hpp>
#include <openvpn/buffer/bufstr.hpp>
#include <openvpn/common/number.hpp>

namespace openvpn {

struct AppControlMessageConfig
{
    //! Supports sending/receiving messages as base64 encoded binary
    bool encoding_base64 = false;

    /** supports sending/receiving messages that are safe to be transmitted as
     * text in an OpenVPN control message */
    bool encoding_text = false;

    //! support sending binary as is as part of the ACC control channel message (not implemented yet)
    bool encoding_binary = false;

    //! List of supported protocols
    std::vector<std::string> supported_protocols;

    //! Maximum size of each individual message/message fragment
    int max_msg_size = 0;

    bool operator==(const AppControlMessageConfig &other) const
    {
        return (encoding_base64 == other.encoding_base64) && (encoding_text == other.encoding_text) && (encoding_binary == other.encoding_binary) && (supported_protocols == other.supported_protocols) && (max_msg_size == other.max_msg_size);
    }

    void parse_flags(const std::string &flags)
    {
        for (const auto &flag : string::split(flags, ':'))
        {
            if (flag == "A")
                encoding_text = true;
            else if (flag == "B")
                encoding_binary = true;
            else if (flag == "6")
                encoding_base64 = true;
        }
    }

    bool supports_protocol(const std::string &protocol)
    {
        return (std::find(supported_protocols.begin(), supported_protocols.end(), protocol) != std::end(supported_protocols));
    }
    /**
      @brief Format a protocol string and a message into a properly packed series of message fragments
      @param protocol The requested ACC protocol
      @param message The raw message, which may be transformed during formatting
      @return std::vector<std::string> The resulting container of message fragments
      @see AppControlMessageReceiver::receive_message
      @see AppControlMessageReceiver::get_msg

      Format a protocol string and a message into a properly packed series of message fragments. If the message is not
      a UTF-8 legal sequence, it will be encoded into some form that can represent the data in the message. Once it's
      received the AppControlMessageReceiver methods receive_message and get_msg can be used to reverse this process.
    */
    std::vector<std::string> format_message(const std::string &protocol, const std::string &message)
    {
        if (!supports_protocol(protocol))
            throw std::invalid_argument("protocol [" + protocol + "] is not supported by peer");

        std::string format;

        /* 2 for the encoding and potential 'F', and ','; 4 for the commas; 5 for the message size itself */
        /* Example: ACC,muppets,41,A,{ "me": "pig", "msg": "I am Miss Piggy" })  */
        const std::size_t header_size = std::strlen("ACC,") + 2 + protocol.size() + 4 + 5;
        auto max_fragment_size = max_msg_size - header_size;


        /* check if the message would be able to pass through the message
         * sanitisation of normal control channel receive logic */
        const std::string sanitised_msg = Unicode::utf8_printable(message, Unicode::UTF8_FILTER);
        if (sanitised_msg == message && encoding_text)
            format = "A";
        else if (encoding_base64)
        {
            format = "6";
            max_fragment_size = max_fragment_size * 6 / 8 - 1;
        }
        else
        {
            throw std::invalid_argument("no encoding available to encode app custom control message");
        }


        std::vector<std::string> control_messages;

        for (std::size_t i = 0; i < message.size(); i += max_fragment_size)
        {
            std::string fragment = message.substr(i, max_fragment_size);
            const bool lastfragment = (i + max_fragment_size) >= message.size();

            if (format == "6")
            {
                fragment = base64->encode(fragment);
            }

            std::stringstream control_msg{};
            control_msg << "ACC," << protocol << ",";
            control_msg << std::to_string(fragment.size()) << ",";
            control_msg << format;
            if (!lastfragment)
                control_msg << "F";
            control_msg << "," << fragment;

            control_messages.emplace_back(control_msg.str());
        }
        return control_messages;
    }

    std::string str() const
    {
        if (supported_protocols.empty())
        {
            return {"no supported protocols"};
        }

        std::stringstream out;
        out << "protocols " << string::join(supported_protocols, " ") << ", ";
        out << "msg_size " << max_msg_size << ", ";
        out << "encoding";
        if (encoding_binary)
            out << " binary";
        if (encoding_text)
            out << " ascii";
        if (encoding_base64)
            out << " base64";

        return out.str();
    }
};

OPENVPN_EXCEPTION(parse_acc_message);

class AppControlMessageReceiver
{
  private:
    BufferAllocated recvbuf;
    std::string recvprotocol;

  public:
    /**
     * Receives and assembles a custom control channel message. If the message
     * is complete it will return true to signal that the complete message
     * can be retrieved via the \c get_message method
     */
    bool receive_message(const std::string &msg)
    {
        if (!recvbuf.defined())
            recvbuf.reset(256, BufAllocFlags::GROW);
        // msg includes ACC, prefix
        auto parts = string::split(msg, ',', 4);
        if (parts.size() != 5 || parts[0] != "ACC")
        {
            throw parse_acc_message{"Discarding malformed custom app control message"};
        }


        auto protocol = std::move(parts[1]);
        auto length_str = std::move(parts[2]);
        auto flags = std::move(parts[3]);
        auto message = std::move(parts[4]);

        bool base64Encoding = false;
        bool textEncoding = false;
        bool fragment = false;

        size_t length = 0;
        if (!parse_number(length_str, length) || length != message.length())
        {
            throw parse_acc_message{"Discarding malformed custom app control message"};
        }

        for (char const &c : flags)
        {
            switch (c)
            {
            case '6':
                base64Encoding = true;
                break;
            case 'A':
                textEncoding = true;
                break;
            case 'F':
                fragment = true;
                break;
            default:
                throw parse_acc_message{"Discarding malformed custom app control message. "
                                        "Unknown flag '"
                                        + std::to_string(c) + "' in message found"};
            }
        }
        // exactly just one encoding has to be present. So ensure that the sum of the bools as 0/1 is exactly 1
        if (textEncoding + base64Encoding != 1)
        {
            throw parse_acc_message{"Discarding malformed custom app control message. "
                                    "Unknown or no encoding flag in message found"};
        }

        if (base64Encoding)
            message = base64->decode(message);

        if (!recvbuf.empty() && recvprotocol != protocol)
        {
            throw parse_acc_message{"custom app control framing error: message with different "
                                    "protocol and previous fragmented message not finished"};
        }

        recvbuf.write(message.data(), message.size());
        recvprotocol = protocol;
        return !fragment;
    }

    std::pair<std::string, std::string> get_message()
    {
        auto ret = buf_to_string(recvbuf);
        recvbuf.clear();
        return {recvprotocol, ret};
    }
};



} // namespace openvpn