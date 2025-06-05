//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2022- OpenVPN Inc.
//
//    SPDX-License-Identifier: MPL-2.0 OR AGPL-3.0-only WITH openvpn3-openssl-exception
//


/**
 * @file
 * @brief Support deferred server-side state creation when client connects
 *
 * Creating OpenVPN protocol tracking state upon receipt of an initial client HARD_RESET
 * packet invites the bad actor to flood the server with connection requests maintaining
 * anonymity by spoofing the client's source address.  Not only does this invite
 * resource exhaustion, but, because of reliability layer retries, it creates an
 * amplification attack as the server retries its un-acknowledged HARD_RESET replies to
 * the spoofed address.
 *
 * This solution treats the server's 64-bit protocol session ID ("Psid or psid") as a
 * cookie that allows the server to defer state creation.  It is ported here to openvpn3
 * from original work in OpenVPN.  Unlike the randomly created server psid generated in
 * psid.hpp for the server's HARD_RESET reply, this approach derives the server psid via
 * an HMAC of information from the incoming client OpenVPN HARD_RESET control message
 * (i.e., the psid cookie).  This allows the server to verify the client as it returns
 * the server psid in it's second packet, only then creating protocol state.
 *
 * Not only does this prevent the resource exhaustion, but it has the happy consequence
 * of avoiding the amplification attack.  Since no state is created on the first packet,
 * there is no reliability layer; and, hence, no retries of the server's HARD_RESET
 * reply.
 */

#pragma once


#include <openvpn/buffer/buffer.hpp> // includes rc.hpp
#include <openvpn/ssl/psid.hpp>

namespace openvpn {

/**
 * @brief Interface to communicate the server's address semantics
 *
 * The server implementation must derive a concrete class from this abstract one.
 * This encapsulates the server implementation's knowledge of the address semantics it
 * needs to return the HARD_RESET packet to the client.  Further, in support of the
 * psid calculation, this class also needs to supply this component with a
 * reproducably hashable memory slab that represents the client address.
 */
class PsidCookieAddrInfoBase
{
  public:
    virtual const unsigned char *get_abstract_cli_addrport(size_t &slab_size) const = 0;

    virtual const void *get_impl_info() const = 0;

    virtual ~PsidCookieAddrInfoBase() = default;
};

/**
 * @brief Interface to provide access to the server's transport capability
 *
 * The server implementation must derive a concrete class from this abstract one.  The
 * server implementation is presumed to own the transport and must implement the
 * member function to send the
 */
class PsidCookieTransportBase : public RC<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<PsidCookieTransportBase> Ptr;

    virtual bool psid_cookie_send_const(Buffer &send_buf, const PsidCookieAddrInfoBase &pcaib) = 0;

    virtual ~PsidCookieTransportBase() = default;
};

/**
 * @brief Interface to integrate this component into the server implementation
 */
class PsidCookie : public RC<thread_unsafe_refcount>
{
  public:
    typedef RCPtr<PsidCookie> Ptr;

    /**
     * @brief Values returned by the intercept() function
     *
     * These are status values depending upon the action that intercept() took in
     * handling client's 1st and 2nd packets.  Early drop indicates that the packet was
     * dropped before determining whether the packet was client's 1st or 2nd.
     */
    enum class Intercept
    {
        DECLINE_HANDLING,
        EARLY_DROP,
        DROP_1ST,
        HANDLE_1ST,
        DROP_2ND,
        HANDLE_2ND,
    };

    /**
     * @brief  Called when a potential new client session packet is received
     *
     * Called by the server implementation when it recieves a packet for which it has no
     * state information.  Such a packet is potentially a client HARD_RESET or a 2nd
     * client packet returning the psid cookie.
     *
     * @param pkt_buf  The packet received by the server implementation.
     * @param pcaib The address information as contained in an instance of the class
     *  that the server implementation derived from the PsidCookieAddrInfoBase class
     * @return Intercept  Status of the packet handling
     */
    virtual Intercept intercept(ConstBuffer &pkt_buf, const PsidCookieAddrInfoBase &pcaib) = 0;

    /**
     * @brief Get the cookie psid from client's 2nd packet
     *
     * This provides the server's psid (a.k.a, the cookie_psid) as returned by the
     * client in it's 2nd packet.  It may only be called after intercept() returns
     * HANDLE_2ND, indicating a valid psid cookie. Further, it may only be called once
     * as it invalidates the internal data source after it sets the return value.
     *
     * @return ProtoSessionID
     */
    virtual ProtoSessionID get_cookie_psid() = 0;

    // The PsidCookie server implementation owns the transport detail for sending the psid cookie packet that the class implementing this interface creates.    The intercept() method will call the derived class' psid_cookie_send_const() function above.

    /**
     * @brief Give this component the transport needed to send the server's HARD_RESET
     *
     * The server implementation must call this method before the intercept() function
     * is asked to handle a packet
     *
     * @param pctb  The transport capability as provided by the server implementation's
     *  object derived from the PsidCookieTransportBase class
     */
    virtual void provide_psid_cookie_transport(PsidCookieTransportBase::Ptr pctb) = 0;

    virtual ~PsidCookie() = default;
};

} // namespace openvpn
