//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
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
#include <cstdint>
#include <ostream>
#include <tuple>
#include <utility>

#include <openvpn/common/exception.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/common/base64.hpp>
#include <openvpn/common/socktypes.hpp>
#include <openvpn/common/endian64.hpp>
#include <openvpn/crypto/hashstr.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/random/randapi.hpp>

namespace openvpn {
  namespace WebSocket {

    OPENVPN_EXCEPTION(websocket_error);

    class Receiver;

    inline std::string accept_confirmation(DigestFactory& digest_factory,
					   const std::string& websocket_key)
    {
      static const char guid[] = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
      HashString h(digest_factory, CryptoAlgs::SHA1);
      h.update(websocket_key + guid);
      return h.final_base64();
    }

    class Protocol
    {
    public:
      static constexpr size_t MAX_HEAD = 16;

      enum Opcode
	{
	  Text = 0x1,
	  Binary = 0x2,
	  Close = 0x8,
	  Ping = 0x9,
	  Pong = 0xA,
	};

      static std::string opcode_to_string(const unsigned int opcode)
      {
	switch (opcode)
	  {
	  case Text:
	    return "Text";
	  case Binary:
	    return "Binary";
	  case Close:
	    return "Close";
	  case Ping:
	    return "Ping";
	  case Pong:
	    return "Pong";
	  default:
	    return "WS-OPCODE-" + std::to_string(opcode);
	  }
      }

      union MaskingKey
      {
      public:
	MaskingKey(std::uint32_t mask)
	  : mask32(std::move(mask))
	{
	}

	void xor_buf(Buffer& buf) const
	{
	  const size_t size = buf.size();
	  std::uint8_t* data = buf.data();
	  for (size_t i = 0; i < size; ++i)
	    data[i] ^= mask8[i & 0x3];
	}

	void prepend_mask(Buffer& buf) const
	{
	  buf.prepend(&mask32, sizeof(mask32));
	}

      private:
	std::uint32_t mask32;
	std::uint8_t mask8[4];
      };
    };

    class Status
    {
    public:
      Status()
	: opcode_(0),
	  fin_(false),
	  close_status_code_(0)
      {
      }

      Status(unsigned int opcode,
	     bool fin=true,
	     unsigned int close_status_code=0)
	: opcode_(std::move(opcode)),
	  fin_(std::move(fin)),
	  close_status_code_(std::move(close_status_code))
      {
      }

      Status(const Status& ref,
	     const unsigned int opcode)
	: opcode_(opcode),
	  fin_(ref.fin_),
	  close_status_code_(ref.close_status_code_)
      {
      }

      bool defined() const
      {
	return opcode_ != 0;
      }

      unsigned int opcode() const
      {
	return opcode_;
      }

      bool fin() const
      {
	return fin_;
      }

      unsigned int close_status_code() const
      {
	return close_status_code_;
      }

      bool operator==(const Status& rhs) const
      {
	return std::tie(opcode_, fin_, close_status_code_) == std::tie(rhs.opcode_, fin_, rhs.close_status_code_);
      }

      bool operator!=(const Status& rhs) const
      {
	return std::tie(opcode_, fin_, close_status_code_) != std::tie(rhs.opcode_, fin_, rhs.close_status_code_);
      }

      std::string to_string() const
      {
	std::string ret;

	ret.reserve(64);
	ret += "[op=";
	ret += Protocol::opcode_to_string(opcode_);
	ret += " fin=";
	ret += std::to_string(fin_);
	if (opcode_ == Protocol::Close)
	  {
	    ret += " status=";
	    ret += std::to_string(close_status_code_);
	  }
	ret += ']';
	return ret;
      }

    private:
      friend class Receiver;

      unsigned int opcode_;
      bool fin_;
      unsigned int close_status_code_;
    };

    class Sender
    {
    public:
      Sender(RandomAPI::Ptr cli_rng_arg) // only provide rng on client side
	: cli_rng(std::move(cli_rng_arg))
      {
	if (cli_rng)
	  cli_rng->assert_crypto();
      }

      void frame(Buffer& buf, const Status& s) const
      {
	if (s.opcode() == Protocol::Close)
	  {
	    const std::uint16_t cs = htons(s.close_status_code());
	    buf.prepend(&cs, sizeof(cs));
	  }

	const size_t payload_len = buf.size();
	if (cli_rng)
	  {
	    const Protocol::MaskingKey mk(cli_rng->rand_get<std::uint32_t>());
	    mk.xor_buf(buf);
	    mk.prepend_mask(buf);
	  }
	prepend_payload_length(buf, payload_len);

	std::uint8_t head = s.opcode() & 0xF;
	if (s.fin())
	  head |= 0x80;
	buf.prepend(&head, sizeof(head));

	//OPENVPN_LOG("WS SEND HEAD\n" << dump_hex(buf));
      }

    private:
      void prepend_payload_length(Buffer& buf, const size_t len) const
      {
	std::uint8_t len8;

	if (len <= 125)
	  len8 = len;
	else if (len <= 65535)
	  {
	    len8 = 126;
	    const std::uint16_t len16 = htons(len);
	    buf.prepend(&len16, sizeof(len16));
	  }
	else
	  {
	    len8 = 127;
	    const std::uint64_t len64 = Endian::rev64(len);
	    buf.prepend(&len64, sizeof(len64));
	  }

	if (cli_rng)
	  len8 |= 0x80;
	buf.prepend(&len8, sizeof(len8));
      }

      RandomAPI::Ptr cli_rng;
    };

    class Receiver
    {
    public:
      Receiver(const bool is_client_arg)
	: is_client(is_client_arg)
      {
	reset_pod();
      }

      Buffer buf_unframed()
      {
	verify_message_complete();
	if (size > buf.size())
	  throw websocket_error("Receiver::buf_unframed: internal error");
	return Buffer(buf.data(), size, true);
      }

      // return true if message is complete
      bool complete()
      {
	// already complete?
	if (header_complete)
	  return complete_();

	// we need at least 2 bytes before we can do anything
	if (buf.size() < 2)
	  return false;

	// get first 2 bytes of header
	Buffer b(buf.data(), buf.size(), true);
	const std::uint8_t* head = b.read_alloc(2);
	s.opcode_ = head[0] & 0xF;
	s.fin_ = bool(head[0] & 0x80);
	if (head[0] & 0x70)
	  throw websocket_error("Receiver: reserved bits are set");
	if (bool(head[1] & 0x80) == is_client)
	  throw websocket_error("Receiver: bad masking direction");

	// process payload length
	const std::uint8_t pl = head[1] & 0x7f;
	if (pl <= 125)
	  {
	    size = pl;
	  }
	else if (pl == 126)
	  {
	    std::uint16_t len16;
	    if (b.size() < sizeof(len16))
	      return false;
	    b.read(&len16, sizeof(len16));
	    size = ntohs(len16);
	  }
	else // pl == 127
	  {
	    std::uint64_t len64;
	    if (b.size() < sizeof(len64))
	      return false;
	    b.read(&len64, sizeof(len64));
	    size = Endian::rev64(len64);
	  }

	// read mask (server side only)
	if (!is_client)
	  {
	    if (b.size() < sizeof(mask))
	      return false;
	    b.read(&mask, sizeof(mask));
	  }

	buf.advance(b.offset());
	header_complete = true;
	return complete_();
      }

      void add_buf(BufferAllocated&& inbuf)
      {
	if (!buf.allocated())
	  {
	    buf = std::move(inbuf);
	    buf.or_flags(BufferAllocated::GROW);
	  }
	else
	  buf.append(inbuf);
      }

      void reset()
      {
	verify_message_complete();
	s = Status();
	reset_buf();
	reset_pod();
      }

      Status status() const
      {
	verify_message_complete();
	return s;
      }

    private:
      void reset_buf()
      {
	if (buf.allocated())
	  {
	    if (size < buf.size())
	      {
		buf.advance(size);
		buf.realign(0);
	      }
	    else if (size == buf.size())
	      buf.clear();
	    else
	      throw websocket_error("Receiver::reset_buf: bad size");
	  }
      }

      void reset_pod()
      {
	header_complete = false;
	message_complete = false;
	mask = 0;
	size = 0;
      }

      void verify_message_complete() const
      {
	if (!message_complete)
	  throw websocket_error("Receiver: message incomplete");
      }

      bool complete_()
      {
	if (message_complete)
	  return true;

	if (header_complete && size <= buf.size())
	  {
	    // un-xor the data on the server side only
	    if (!is_client)
	      {
		Buffer b(buf.data(), size, true);
		const Protocol::MaskingKey mk(mask);
		mk.xor_buf(b);
	      }

	    // get close status code
	    if (s.opcode_ == Protocol::Close && size >= 2)
	      {
		std::uint16_t cs;
		buf.read(&cs, sizeof(cs));
		size -= sizeof(cs);
		s.close_status_code_ = ntohs(cs);
	      }

	    message_complete = true;
	    return true;
	  }
	return false;
      }

      const bool is_client;
      bool header_complete;
      bool message_complete;
      std::uint32_t mask;
      std::uint64_t size;
      Status s;
      BufferAllocated buf;
    };

    namespace Client {

      struct Config : public RC<thread_unsafe_refcount>
      {
	typedef RCPtr<Config> Ptr;

	std::string origin;
	std::string protocol;
	RandomAPI::Ptr rng;
	DigestFactory::Ptr digest_factory;

	// compression
	bool compress = false;
	size_t compress_threshold = 256;
      };

      class PerRequest : public RC<thread_unsafe_refcount>
      {
      private:
	Config::Ptr conf;

      public:
	typedef RCPtr<PerRequest> Ptr;

	PerRequest(Config::Ptr conf_arg)
	  : conf(validate_conf(std::move(conf_arg))),
	    sender(conf->rng),
	    receiver(true)
	{
	}

	void client_headers(std::ostream& os)
	{
	  generate_websocket_key();
	  os << "Sec-WebSocket-Key: " << websocket_key << "\r\n";
	  os << "Sec-WebSocket-Version: 13\r\n";
	  if (!conf->protocol.empty())
	    os << "Sec-WebSocket-Protocol: " << conf->protocol << "\r\n";
	  os << "Connection: Upgrade\r\n";
	  os << "Upgrade: websocket\r\n";
	  if (!conf->origin.empty())
	    os << "Origin: " << conf->origin << "\r\n";
	}

	bool confirm_websocket_key(const std::string& ws_accept) const
	{
	  return ws_accept == accept_confirmation(*conf->digest_factory, websocket_key);
	}

	Sender sender;
	Receiver receiver;

      private:
	static Config::Ptr validate_conf(Config::Ptr conf)
	{
	  if (!conf)
	    throw websocket_error("no config");
	  conf->rng->assert_crypto();
	  if (!conf->digest_factory)
	    throw websocket_error("no digest factory in config");
	  return conf;
	}

	void generate_websocket_key()
	{
	  std::uint8_t data[16];
	  conf->rng->rand_bytes(data, sizeof(data));
	  websocket_key = base64->encode(data, sizeof(data));
	}

	std::string websocket_key;
      };

    }

    namespace Server {

      struct Config : public RC<thread_unsafe_refcount>
      {
	typedef RCPtr<Config> Ptr;

	std::string protocol;
	DigestFactory::Ptr digest_factory;
      };

      class PerRequest : public RC<thread_unsafe_refcount>
      {
      private:
	Config::Ptr conf;

      public:
	typedef RCPtr<PerRequest> Ptr;

	PerRequest(Config::Ptr conf_arg)
	  : conf(validate_conf(std::move(conf_arg))),
	    sender(RandomAPI::Ptr()),
	    receiver(false)
	{
	}

	void set_websocket_key(const std::string& websocket_key)
	{
	  websocket_accept = accept_confirmation(*conf->digest_factory, websocket_key);
	}

	void server_headers(std::ostream& os)
	{
	  os << "Upgrade: websocket\r\n";
	  os << "Connection: Upgrade\r\n";
	  if (!websocket_accept.empty())
	    os << "Sec-WebSocket-Accept: " << websocket_accept << "\r\n";
	  if (!conf->protocol.empty())
	    os << "Sec-WebSocket-Protocol: " << conf->protocol << "\r\n";
	}

	Sender sender;
	Receiver receiver;

      private:
	static Config::Ptr validate_conf(Config::Ptr conf)
	{
	  if (!conf)
	    throw websocket_error("no config");
	  if (!conf->digest_factory)
	    throw websocket_error("no digest factory in config");
	  return conf;
	}

	std::string websocket_accept;
      };

    }

  }
}
