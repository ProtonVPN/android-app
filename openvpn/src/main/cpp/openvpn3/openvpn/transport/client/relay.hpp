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

// Create a special transport factory that persists an existing
// transport client.  This is used to preserve the transport
// socket when other client components are restarted after
// a RELAY message is received from the server.

#ifndef OPENVPN_TRANSPORT_CLIENT_RELAY_H
#define OPENVPN_TRANSPORT_CLIENT_RELAY_H

#include <memory>

#include <openvpn/transport/client/transbase.hpp>

namespace openvpn {
class TransportRelayFactory : public TransportClientFactory
{
  public:
    TransportRelayFactory(openvpn_io::io_context &io_context,
                          TransportClient::Ptr transport,
                          TransportClientParent *old_parent)
        : io_context_(io_context),
          transport_(std::move(transport)),
          null_parent_(new NullParent(old_parent))
    {
        // Temporarily point transport to our null parent
        transport_->transport_reparent(null_parent_.get());
    }

    class TransportClientNull : public TransportClient
    {
      public:
        TransportClientNull(TransportClient *old)
            : endpoint_(old->server_endpoint_addr()),
              protocol_(old->transport_protocol())
        {
            old->server_endpoint_info(host_, port_, proto_, ip_addr_);
        }

      private:
        void transport_start() override
        {
        }
        void stop() override
        {
        }
        bool transport_send_const(const Buffer &buf) override
        {
            return false;
        }
        bool transport_send(BufferAllocated &buf) override
        {
            return false;
        }
        bool transport_send_queue_empty() override
        {
            return false;
        }
        bool transport_has_send_queue() override
        {
            return false;
        }
        size_t transport_send_queue_size() override
        {
            return 0;
        }
        void transport_stop_requeueing() override
        {
        }
        void reset_align_adjust(const size_t align_adjust) override
        {
        }
        void transport_reparent(TransportClientParent *parent) override
        {
        }

        IP::Addr server_endpoint_addr() const override
        {
            return endpoint_;
        }

        Protocol transport_protocol() const override
        {
            return protocol_;
        }

        void server_endpoint_info(std::string &host, std::string &port, std::string &proto, std::string &ip_addr) const override
        {
            host = host_;
            port = port_;
            proto = proto_;
            ip_addr = ip_addr_;
        }

        IP::Addr endpoint_;
        Protocol protocol_;
        std::string host_;
        std::string port_;
        std::string proto_;
        std::string ip_addr_;
    };

  private:
    class NullParent : public TransportClientParent
    {
      public:
        NullParent(TransportClientParent *old_parent)
            : is_openvpn_protocol(old_parent->transport_is_openvpn_protocol())
        {
        }

      private:
        void transport_recv(BufferAllocated &buf) override
        {
        }
        void transport_needs_send() override
        {
        }

        void transport_error(const Error::Type fatal_err, const std::string &err_text) override
        {
            OPENVPN_LOG("TransportRelayFactory: Transport Error in null parent: " << Error::name(fatal_err) << " : " << err_text);
        }

        void proxy_error(const Error::Type fatal_err, const std::string &err_text) override
        {
            OPENVPN_LOG("TransportRelayFactory: Proxy Error in null parent: " << Error::name(fatal_err) << " : " << err_text);
        }

        // Return true if we are transporting OpenVPN protocol
        bool transport_is_openvpn_protocol() override
        {
            return is_openvpn_protocol;
        }

        // progress notifications
        void transport_pre_resolve() override
        {
        }
        void transport_wait_proxy() override
        {
        }
        void transport_wait() override
        {
        }
        void transport_connecting() override
        {
        }

        // Return true if keepalive parameter(s) are enabled.
        bool is_keepalive_enabled() const override
        {
            return false;
        }

        // Disable keepalive for rest of session, but fetch
        // the keepalive parameters (in seconds).
        void disable_keepalive(unsigned int &keepalive_ping, unsigned int &keepalive_timeout) override
        {
            keepalive_ping = 0;
            keepalive_timeout = 0;
        }

        bool is_openvpn_protocol;
    };

    TransportClient::Ptr new_transport_client_obj(openvpn_io::io_context &io_context,
                                                  TransportClientParent *parent) override
    {
        // io_context MUST stay consistent
        if (&io_context != &io_context_)
            throw Exception("TransportRelayFactory: inconsistent io_context");

        transport_->transport_reparent(parent);
        return transport_;
    }

    bool is_relay() override
    {
        return true;
    }

    openvpn_io::io_context &io_context_;                 // only used to verify consistency
    TransportClient::Ptr transport_;                     // the persisted transport
    std::unique_ptr<TransportClientParent> null_parent_; // placeholder for TransportClient parent before reparenting
};
} // namespace openvpn

#endif
