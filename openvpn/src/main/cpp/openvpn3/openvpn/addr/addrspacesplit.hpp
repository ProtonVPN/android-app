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

// Invert a route list.  Used to support excluded routes on platforms that
// don't support them natively.

#pragma once

#include <openvpn/common/exception.hpp>
#include <openvpn/addr/route.hpp>

namespace openvpn::IP {
class AddressSpaceSplitter : public RouteList
{
  public:
    OPENVPN_EXCEPTION(address_space_splitter);

    AddressSpaceSplitter() = default;

    // NOTE: when passing AddressSpaceSplitter to this constructor, make sure
    // to static_cast it to RouteList& so as to avoid matching the
    // default copy constructor.
    explicit AddressSpaceSplitter(const RouteList &in)
        : AddressSpaceSplitter(in, in.version_mask())
    {
    }

    AddressSpaceSplitter(const RouteList &in, const Addr::VersionMask vermask)
    {
        in.verify_canonical();
        if (vermask & Addr::V4_MASK)
            descend(in, Route(Addr::from_zero(Addr::V4), 0));
        if (vermask & Addr::V6_MASK)
            descend(in, Route(Addr::from_zero(Addr::V6), 0));
    }

  private:
    enum class Type
    {
        EQUAL,
        SUBROUTE,
        LEAF,
    };
    /**
     * This method constructs a non-overlapping list of routes spanning the address
     * space in \p route.  The routes are constructed in a way that each
     * route in the returned list is smaller or equal to each route in
     * parameter \p in.
     *
     * @param in Existing routes
     * @param route The route we currently are looking at and split if it does
     *	      not meet the requirements
     */
    void descend(const RouteList &in, const Route &route)
    {
        switch (find(in, route))
        {
        case Type::SUBROUTE:
            {
                Route r1, r2;
                if (route.split(r1, r2))
                {
                    descend(in, r1);
                    descend(in, r2);
                }
                else
                    push_back(route);
                break;
            }
        case Type::EQUAL:
        case Type::LEAF:
            push_back(route);
            break;
        }
    }

    static Type find(const RouteList &in, const Route &route)
    {
        Type type = Type::LEAF;
        for (const auto &r : in)
        {
            if (route == r)
                type = Type::EQUAL;
            else if (route.contains(r))
                return Type::SUBROUTE;
        }
        return type;
    }
};
} // namespace openvpn::IP
