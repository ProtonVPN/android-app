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
        virtual void transport_start()
        {
        }
        virtual void stop()
        {
        }
        virtual bool transport_send_const(const Buffer &buf)
        {
            return false;
        }
        virtual bool transport_send(BufferAllocated &buf)
        {
            return false;
        }
        virtual bool transport_send_queue_empty()
        {
            return false;
        }
        virtual bool transport_has_send_queue()
        {
            return false;
        }
        virtual unsigned int transport_send_queue_size()
        {
            return 0;
        }
        virtual void transport_stop_requeueing()
        {
        }
        virtual void reset_align_adjust(const size_t align_adjust)
        {
        }
        virtual void transport_reparent(TransportClientParent *parent)
        {
        }

        virtual IP::Addr server_endpoint_addr() const
        {
            return endpoint_;
        }

        virtual Protocol transport_protocol() const
        {
            return protocol_;
        }

        virtual void server_endpoint_info(std::string &host, std::string &port, std::string &proto, std::string &ip_addr) const
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
        virtual void transport_recv(BufferAllocated &buf)
        {
        }
        virtual void transport_needs_send()
        {
        }

        virtual void transport_error(const Error::Type fatal_err, const std::string &err_text)
        {
            OPENVPN_LOG("TransportRelayFactory: Transport Error in null parent: " << Error::name(fatal_err) << " : " << err_text);
        }

        virtual void proxy_error(const Error::Type fatal_err, const std::string &err_text)
        {
            OPENVPN_LOG("TransportRelayFactory: Proxy Error in null parent: " << Error::name(fatal_err) << " : " << err_text);
        }

        // Return true if we are transporting OpenVPN protocol
        virtual bool transport_is_openvpn_protocol()
        {
            return is_openvpn_protocol;
        }

        // progress notifications
        virtual void transport_pre_resolve()
        {
        }
        virtual void transport_wait_proxy()
        {
        }
        virtual void transport_wait()
        {
        }
        virtual void transport_connecting()
        {
        }

        // Return true if keepalive parameter(s) are enabled.
        virtual bool is_keepalive_enabled() const
        {
            return false;
        }

        // Disable keepalive for rest of session, but fetch
        // the keepalive parameters (in seconds).
        virtual void disable_keepalive(unsigned int &keepalive_ping, unsigned int &keepalive_timeout)
        {
            keepalive_ping = 0;
            keepalive_timeout = 0;
        }

        bool is_openvpn_protocol;
    };

    virtual TransportClient::Ptr new_transport_client_obj(openvpn_io::io_context &io_context,
                                                          TransportClientParent *parent) override
    {
        // io_context MUST stay consistent
        if (&io_context != &io_context_)
            throw Exception("TransportRelayFactory: inconsistent io_context");

        transport_->transport_reparent(parent);
        return transport_;
    }

    virtual bool is_relay() override
    {
        return true;
    }

    openvpn_io::io_context &io_context_;                 // only used to verify consistency
    TransportClient::Ptr transport_;                     // the persisted transport
    std::unique_ptr<TransportClientParent> null_parent_; // placeholder for TransportClient parent before reparenting
};
} // namespace openvpn

#endif
