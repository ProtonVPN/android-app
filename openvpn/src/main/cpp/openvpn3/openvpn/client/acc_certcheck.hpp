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

#include <string>
#include <memory>
#include <optional>
#include <stdexcept>

#include <openvpn/buffer/buffer.hpp>
#include <openvpn/ssl/sslchoose.hpp>
#include <openvpn/ssl/sslapi.hpp>

namespace openvpn {

/**
  @brief The SslApiBuilder struct is used to initialize and configure an SSL/TLS API in OpenVPN.
  @class SslApiBuilder

  It takes in a configuration pointer for the SSL library and uses that to initialize an SSL connection
  object. It does not directly produce any outputs, but allows accessing the initialized SSLAPI server
  object via the get() method.

  Important transforms are using the SSLAPI config to initialize the SSLAPI object correctly. This
  handles the low-level details of configuring SSL securely via the sslctx abstraction layer.
*/
struct SslApiBuilder
{
    /**
      @brief Construct a new SslApiBuilder object
      @param cfg configuration that should be installed
    */
    SslApiBuilder(SSLLib::SSLAPI::Config::Ptr cfg)
        : mConfig(std::move(cfg)),
          mFactory(mConfig->new_factory()),
          mServer(mFactory->ssl()) {};

    SslApiBuilder(const SslApiBuilder &) = delete;
    SslApiBuilder(SslApiBuilder &&) noexcept = delete;
    SslApiBuilder &operator=(const SslApiBuilder &) = delete;
    SslApiBuilder &operator=(SslApiBuilder &&) = delete;

  public: // API
    /**
      @brief get a reference to the encapsulated ssl object
      @return openvpn::SSLAPI& a reference to the ready-to-use ssl object
    */
    openvpn::SSLAPI &get()
    {
        return *mServer;
    }

  private:                                // Data
    SSLLib::SSLAPI::Config::Ptr mConfig;  ///< Configuration for this SSL server
    openvpn::SSLFactoryAPI::Ptr mFactory; ///< Factory from the SSL configuration
    openvpn::SSLAPI::Ptr mServer;         ///< Server created from the factory - depends on mConfig and mFactory
};
/**
  @brief defines a class that handles SSL/TLS handshaking
  @class AccHandshaker

  Defines a class that handles SSL/TLS handshaking for device authentication.

  It takes in a configuration pointer for the SSL library and uses that to initialize an SSL connection
  object. The main methods are the constructor which takes the SSL config pointer and initializes the internal
  SSL object using that config. The process_msg method takes in a message string, passes it into the SSL object
  to continue the handshake, and returns any response message the SSL object generates during the handshake.
  This allows incrementally processing the handshake protocol messages. The reset method reinitializes the SSL
  object if the config changes.

  Internally it contains a unique pointer to a SslApiBuilder object. The SslApiBuilder initializes the lower
  level SSL objects like the SSL context, factory, and server instance using the provided configuration. So the
  AccHandshaker gives a simple interface to perform an SSL handshake using an SSL configuration. It handles
  setting up the SSL objects correctly, feeding the handshake messages into the SSL library, and getting any
  responses back out. This allows verifying possession of the correct certificates and keys.
*/
struct AccHandshaker
{
    using MsgT = std::optional<std::string>;
    AccHandshaker() = default;
    AccHandshaker(SSLLib::SSLAPI::Config::Ptr cfg);
    MsgT process_msg(const MsgT &msg);
    std::string details();
    void reset(SSLLib::SSLAPI::Config::Ptr cfg);

  private:
    std::unique_ptr<SslApiBuilder> mSslApi;
};
/**
  @brief Construct a new AccHandshaker object
  @param cfg an initialized confiog object type Config::Ptr
*/
inline AccHandshaker::AccHandshaker(SSLLib::SSLAPI::Config::Ptr cfg)
    : mSslApi(new SslApiBuilder(std::move(cfg)))
{
    mSslApi->get().start_handshake();
}
/**
  @brief Incrementally process the CLIENT HELLO / SERVER HELLO exchange
  @param msg optional cipher text from the TLS peer
  @return optional<string> reply for the given msg text if any
  @exception std::exception derived type with more information regarding the problem

  The function will stop returning reply data when it's done handshaking. A handshake failure may result in
  an exception derived from std::exception being thrown.
*/
inline AccHandshaker::MsgT AccHandshaker::process_msg(const MsgT &msg)
{
    if (!mSslApi)
        throw std::runtime_error("AccHandshaker::process_msg: not configured");

    MsgT ret = std::nullopt;
    auto &api = mSslApi->get();
    if (msg)
    {
        api.write_ciphertext(BufferAllocatedRc::Create(reinterpret_cast<const unsigned char *>(msg->c_str()),
                                                       msg->size(),
                                                       BufAllocFlags::NO_FLAGS));

        // Won't handshake without this even though there is no data available.
        uint8_t cleartext[8];
        api.read_cleartext(cleartext, sizeof(cleartext));
    }

    if (api.read_ciphertext_ready())
    {
        auto reply = api.read_ciphertext();
        ret = {reinterpret_cast<const char *>(reply->c_data()),
               reinterpret_cast<const char *>(reply->c_data_end())};
    }

    return ret;
}
/**
  @brief returns ssl_handshake_details() if the SSLAPI is available
  @return std::string containing SSLAPI details
  @exception std::exception derived type with more information regarding the problem
*/
inline std::string AccHandshaker::details()
{
    if (!mSslApi)
        throw std::runtime_error("AccHandshaker::details: not configured");

    return mSslApi->get().ssl_handshake_details();
}
/**
  @brief Re-init the handshaker
  @param cfg configuration that should be installed
  @exception throws an object derived from std::exception if there is a problem with the init process

  Rebuilds the SSLAPI object with the specified configuration and begins the handshake process. Data
  exchange for the actual handshake is done via calls to process_msg.
*/
inline void AccHandshaker::reset(SSLLib::SSLAPI::Config::Ptr cfg)
{
    mSslApi.reset(new SslApiBuilder(std::move(cfg)));
    mSslApi->get().start_handshake();
}

} // namespace openvpn
